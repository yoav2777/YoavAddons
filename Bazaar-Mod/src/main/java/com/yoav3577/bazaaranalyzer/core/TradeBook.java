package com.yoav3577.bazaaranalyzer.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class TradeBook {
   private final int max;
   private final ArrayDeque<Trade> trades = new ArrayDeque<>();

   public TradeBook(int max) {
      this.max = max;
   }

   public synchronized void add(Trade t) {
      this.trades.addLast(t);

      while (this.trades.size() > this.max) {
         this.trades.removeFirst();
      }
   }

   /**
    * Loads past trades from capture files (oldest file first) without parsing more than the book keeps:
    * lines are read newest-first until {@code max} trades are found. The history goes in front of anything
    * already in the book, so live trades captured while this ran stay the newest.
    */
   public int loadCaptureTail(List<List<String>> filesOldestFirst) {
      ArrayDeque<Trade> older = new ArrayDeque<>();

      for (int f = filesOldestFirst.size() - 1; f >= 0 && older.size() < this.max; f--) {
         List<String> lines = filesOldestFirst.get(f);

         for (int i = lines.size() - 1; i >= 0 && older.size() < this.max; i--) {
            parseRecord(lines.get(i)).ifPresent(older::addFirst);
         }
      }

      synchronized (this) {
         Iterator<Trade> it = older.descendingIterator();

         while (it.hasNext() && this.trades.size() < this.max) {
            this.trades.addFirst(it.next());
         }
      }

      return older.size();
   }

   private static Optional<Trade> parseRecord(String l) {
      int tab = l.indexOf('\t');
      if (tab <= 0) {
         return Optional.empty();
      }

      try {
         return TradeParser.parse(Long.parseLong(l.substring(0, tab)), l.substring(tab + 1));
      } catch (NumberFormatException var3) {
         return Optional.empty();
      }
   }

   public synchronized int loadCaptureLines(Iterable<String> lines) {
      int added = 0;

      for (String l : lines) {
         int tab = l.indexOf('\t');
         if (tab > 0) {
            long ts;
            try {
               ts = Long.parseLong(l.substring(0, tab));
            } catch (NumberFormatException var9) {
               continue;
            }

            Optional<Trade> t = TradeParser.parse(ts, l.substring(tab + 1));
            if (t.isPresent()) {
               this.add(t.get());
               added++;
            }
         }
      }

      return added;
   }

   public synchronized boolean full() {
      return this.trades.size() >= this.max;
   }

   public synchronized int size() {
      return this.trades.size();
   }

   public synchronized List<Trade> snapshot() {
      return new ArrayList<>(this.trades);
   }

   public synchronized List<Trade> latest(int limit) {
      List<Trade> out = new ArrayList<>(Math.min(limit, this.trades.size()));
      Iterator<Trade> it = this.trades.descendingIterator();

      while (it.hasNext() && out.size() < limit) {
         out.add(it.next());
      }

      return out;
   }

   public synchronized TradeBook.Summary summary() {
      double spent = 0.0;
      double earned = 0.0;
      double claimFees = 0.0;
      double listingFees = 0.0;
      int ahSales = 0;
      int other = 0;
      Map<String, String> lastListing = new HashMap<>();
      // earned - spent per day over the whole book (the site's "Net per day" chart; its trade list is only the latest few hundred)
      Map<String, Double> days = new TreeMap<>();

      for (Trade t : this.trades) {
         switch (t.kind()) {
            case BZ_INSTA_BUY:
            case BZ_CLAIM_BOUGHT:
            case AH_PURCHASED:
               spent += t.coins();
               days.merge(Days.key(t.ts()), -t.coins(), Double::sum);
               break;
            case BZ_INSTA_SELL:
            case BZ_CLAIM_SOLD:
               earned += t.coins();
               days.merge(Days.key(t.ts()), t.coins(), Double::sum);
               break;
            case AH_LISTED:
               lastListing.put(t.item(), t.other());
               break;
            case AH_COLLECTED:
               earned += t.coins();
               days.merge(Days.key(t.ts()), t.coins(), Double::sum);
               ahSales++;
               double price = AhFees.priceFromCollected(t.coins());
               claimFees += price - t.coins();
               if (!"Auction".equals(lastListing.get(t.item()))) {
                  listingFees += AhFees.listingFee(price);
               }
               break;
            case OTHER:
               other++;
         }
      }

      return new TradeBook.Summary(spent, earned, claimFees, listingFees, ahSales, other, days);
   }

   public synchronized String toJson(int limit) {
      return this.toJson(limit, List.of());
   }

   public synchronized String toJson(int limit, List<AhLedger.Listing> ledger) {
      Map<Trade, String> extra = new IdentityHashMap<>();

      for (AhLedger.Listing l : ledger) {
         extra.put(
            l.listed(),
            "\"price\":"
               + Json.num(l.price())
               + ",\"state\":\""
               + l.state()
               + "\",\"closer\":"
               + (l.closer() == null ? "null" : String.valueOf(l.closer().ts()))
               + ",\"soldTs\":"
               + (l.soldTs() == 0L ? "null" : String.valueOf(l.soldTs()))
         );
         if (l.closer() != null) {
            extra.put(l.closer(), "\"listed\":" + l.listedTs() + ",\"price\":" + Json.num(l.price()));
         }
      }

      StringBuilder sb = new StringBuilder("{\"ok\":true,\"total\":")
         .append(this.trades.size())
         .append(",\"summary\":")
         .append(this.summary().toJson())
         .append(",\"trades\":[");
      boolean first = true;

      for (Trade t : this.latest(limit)) {
         if (!first) {
            sb.append(',');
         }

         sb.append(t.toJson(extra.get(t)));
         first = false;
      }

      return sb.append("]}").toString();
   }

   public record Summary(double spent, double earned, double claimFees, double listingFees, int ahSales, int unrecognized, Map<String, Double> days) {
      public double net() {
         return this.earned - this.spent - this.listingFees;
      }

      public String toJson() {
         return "{\"spent\":"
            + Json.num(this.spent)
            + ",\"earned\":"
            + Json.num(this.earned)
            + ",\"claimFees\":"
            + Json.num(this.claimFees)
            + ",\"listingFees\":"
            + Json.num(this.listingFees)
            + ",\"net\":"
            + Json.num(this.net())
            + ",\"ahSales\":"
            + this.ahSales
            + ",\"unrecognized\":"
            + this.unrecognized
            + ",\"days\":{"
            + this.days.entrySet().stream().map(e -> "\"" + e.getKey() + "\":" + Json.num(e.getValue())).collect(Collectors.joining(","))
            + "}}";
      }
   }
}
