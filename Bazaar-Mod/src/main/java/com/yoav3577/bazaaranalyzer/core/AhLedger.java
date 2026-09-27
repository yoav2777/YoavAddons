package com.yoav3577.bazaaranalyzer.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class AhLedger {
   private static final long START_TOLERANCE_MS = 6000L;

   private AhLedger() {
   }

   public static String key(String name) {
      return name == null ? "" : name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
   }

   public static List<AhLedger.Listing> build(List<Trade> events, List<AhAuction> auctions) {
      List<Trade> ordered = new ArrayList<>(events);
      ordered.sort(Comparator.comparingLong(Trade::ts));
      List<AhLedger.Listing> all = new ArrayList<>();
      Map<String, List<AhLedger.Listing>> open = new HashMap<>();

      for (Trade t : ordered) {
         if (t.item() != null) {
            String key = key(t.item());
            switch (t.kind()) {
               case AH_LISTED:
                  AhLedger.Listing l = new AhLedger.Listing(t);
                  l.bin = "BIN".equals(t.other());
                  all.add(l);
                  open.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
                  break;
               case AH_COLLECTED:
                  close(open, key, t, AhLedger.State.COLLECTED, lx -> {
                     lx.soldTs = t.ts();
                     lx.price = AhFees.priceFromCollected(t.coins());
                  });
                  break;
               case AH_CANCELED:
                  close(open, key, t, AhLedger.State.CANCELED, null);
                  break;
               case AH_RETURNED:
                  close(open, key, t, AhLedger.State.EXPIRED, null);
            }
         }
      }

      if (!auctions.isEmpty()) {
         enrich(all, auctions);
      }

      return all;
   }

   private static void close(Map<String, List<AhLedger.Listing>> open, String key, Trade closer, AhLedger.State state, Consumer<AhLedger.Listing> extra) {
      List<AhLedger.Listing> listings = open.get(key);
      if (listings != null && !listings.isEmpty()) {
         AhLedger.Listing l = listings.remove(listings.size() - 1);
         l.state = state;
         l.closer = closer;
         if (extra != null) {
            extra.accept(l);
         }
      }
   }

   private static void enrich(List<AhLedger.Listing> all, List<AhAuction> auctions) {
      Set<String> used = new HashSet<>();

      for (AhLedger.Listing l : all) {
         if (l.state != AhLedger.State.COLLECTED) {
            String key = key(l.name());
            boolean unsold = l.state == AhLedger.State.CANCELED || l.state == AhLedger.State.EXPIRED;
            AhAuction own = null;
            long bestDev = Long.MAX_VALUE;

            for (AhAuction a : auctions) {
               if (!used.contains(a.auctionId()) && !a.sold() && key.equals(key(a.name())) && (!unsold || !a.active())) {
                  long dev = startDeviation(a.endTs(), l.listedTs());
                  if (dev >= 0L && dev < bestDev) {
                     own = a;
                     bestDev = dev;
                  }
               }
            }

            if (own != null) {
               used.add(own.auctionId());
               l.price = own.startingBid();
               l.startingBid = own.startingBid();
               l.bin = own.bin();
               if (!own.active() && l.state == AhLedger.State.ACTIVE) {
                  l.state = AhLedger.State.EXPIRED;
               }
            } else if (!unsold) {
               AhAuction sold = null;

               for (AhAuction ax : auctions) {
                  if (!used.contains(ax.auctionId())
                     && ax.sold()
                     && key.equals(key(ax.name()))
                     && ax.endTs() >= l.listedTs() - START_TOLERANCE_MS
                     && (sold == null || ax.endTs() < sold.endTs())) {
                     sold = ax;
                  }
               }

               if (sold != null) {
                  used.add(sold.auctionId());
                  l.state = AhLedger.State.SOLD;
                  l.soldTs = sold.endTs();
                  l.price = sold.soldFor();
                  l.startingBid = sold.startingBid();
                  l.bin = sold.bin();
               }
            }
         }
      }
   }

   private static long startDeviation(long end, long listedTs) {
      long d = end - listedTs;
      if (d < 55000L) {
         return -1L;
      } else {
         long m = d % 60000L;
         long dev = Math.min(m, 60000L - m);
         return dev <= START_TOLERANCE_MS ? dev : -1L;
      }
   }

   public static final class Listing {
      private final Trade listed;
      private AhLedger.State state = AhLedger.State.ACTIVE;
      private Trade closer;
      private boolean bin;
      private double price = Double.NaN;
      private double startingBid = Double.NaN;
      private long soldTs;

      private Listing(Trade listed) {
         this.listed = listed;
      }

      public Trade listed() {
         return this.listed;
      }

      public String name() {
         return this.listed.item();
      }

      public long listedTs() {
         return this.listed.ts();
      }

      public AhLedger.State state() {
         return this.state;
      }

      public Trade closer() {
         return this.closer;
      }

      public boolean bin() {
         return this.bin;
      }

      public double price() {
         return this.price;
      }

      public long soldTs() {
         return this.soldTs;
      }

      public boolean sold() {
         return this.state == AhLedger.State.SOLD || this.state == AhLedger.State.COLLECTED;
      }

      public double net() {
         if (!Double.isNaN(this.price) && (this.sold() || this.state == AhLedger.State.ACTIVE)) {
            double listingFee = this.bin ? AhFees.listingFee(this.price) : (Double.isNaN(this.startingBid) ? 0.0 : 0.05 * this.startingBid);
            return this.price - AhFees.claimFee(this.price) - listingFee;
         } else {
            return Double.NaN;
         }
      }
   }

   public static enum State {
      ACTIVE,
      SOLD,
      COLLECTED,
      CANCELED,
      EXPIRED;
   }
}
