package com.yoav3577.bazaaranalyzer.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public final class Lowball {
   /** A removed gem counts when it is sold (insta-sell or sell offer set up) on the Bazaar within this time of the removal. */
   public static final long GEM_WINDOW_MS = 86400000L;
   /** A removal is seen up to a second after it happens (inventory scan), so sales this much earlier still count. */
   static final long GEM_SLACK_MS = 60000L;
   static final long BAZAAR_WINDOW_MS = 604800000L;
   static final double BAZAAR_TAX = 0.0125;

   private Lowball() {
   }

   static double netOf(AhAuction a, double price) {
      double listing = a.bin() ? AhFees.listingFee(price) : 0.05 * a.startingBid();
      return price - AhFees.claimFee(price) - listing - AhFees.durationFee(a.startTs(), a.endTs());
   }

   public static List<Lowball.Result> analyze(List<PlayerTrade> trades, List<AhAuction> auctions, List<Trade> events) {
      return analyze(trades, auctions, events, List.of(), System.currentTimeMillis());
   }

   public static List<Lowball.Result> analyze(List<PlayerTrade> trades, List<AhAuction> auctions, List<Trade> events, List<GemLog> gemLog, long now) {
      return new Lowball.Run(trades, auctions, events, gemLog, now).run();
   }

   public static String responseJson(String playerName, String playerUuid, boolean refreshing, int ahAuctions, String ahError, List<Lowball.Day> days) {
      StringBuilder sb = new StringBuilder("{\"ok\":true,\"player\":{\"name\":")
         .append(Json.str(playerName))
         .append(",\"uuid\":")
         .append(Json.str(playerUuid))
         .append("},\"refreshing\":")
         .append(refreshing)
         .append(",\"ahAuctions\":")
         .append(ahAuctions)
         .append(",\"ahError\":")
         .append(Json.str(ahError != null && !ahError.isEmpty() ? ahError : null))
         .append(",\"days\":[");

      for (int i = 0; i < days.size(); i++) {
         if (i > 0) {
            sb.append(',');
         }

         sb.append(days.get(i).toJson());
      }

      return sb.append("]}").toString();
   }

   public static List<Lowball.Day> byDay(List<PlayerTrade> trades, List<Lowball.Result> results) {
      Map<String, List<PlayerTrade>> tradesByDay = new HashMap<>();

      for (PlayerTrade t : trades) {
         tradesByDay.computeIfAbsent(t.day(), k -> new ArrayList<>()).add(t);
      }

      Map<String, List<Lowball.Result>> resultsByDay = new HashMap<>();

      for (Lowball.Result r : results) {
         resultsByDay.computeIfAbsent(r.trade().day(), k -> new ArrayList<>()).add(r);
      }

      Set<String> days = new HashSet<>(tradesByDay.keySet());
      days.addAll(resultsByDay.keySet());
      List<String> sorted = new ArrayList<>(days);
      sorted.sort(Comparator.reverseOrder());
      List<Lowball.Day> out = new ArrayList<>();

      for (String d : sorted) {
         List<PlayerTrade> ts = tradesByDay.getOrDefault(d, List.of());
         List<Lowball.Result> rs = resultsByDay.getOrDefault(d, List.of())
            .stream()
            .sorted(Comparator.comparingLong((Lowball.Result r) -> r.trade().ts()).reversed())
            .toList();
         double given = 0.0;
         double gained = 0.0;
         double pending = 0.0;

         for (PlayerTrade t : ts) {
            given += t.givenCoins();
         }

         for (Lowball.Result r : rs) {
            gained += r.gained();
            pending += r.pending();
         }

         out.add(new Lowball.Day(d, given, gained, pending, ts.stream().sorted(Comparator.comparingLong(PlayerTrade::ts).reversed()).toList(), rs));
      }

      return out;
   }

   public record Day(String day, double givenCoins, double gained, double pending, List<PlayerTrade> trades, List<Lowball.Result> lowballs) {
      public String toJson() {
         StringBuilder sb = new StringBuilder("{\"day\":\"")
            .append(this.day)
            .append("\",\"givenCoins\":")
            .append(Json.num(this.givenCoins))
            .append(",\"gained\":")
            .append(Json.num(this.gained))
            .append(",\"pending\":")
            .append(Json.num(this.pending))
            .append(",\"trades\":[");

         for (int i = 0; i < this.trades.size(); i++) {
            if (i > 0) {
               sb.append(',');
            }

            sb.append(this.trades.get(i).toJson());
         }

         sb.append("],\"lowballs\":[");

         for (int i = 0; i < this.lowballs.size(); i++) {
            if (i > 0) {
               sb.append(',');
            }

            sb.append(this.lowballs.get(i).toJson());
         }

         return sb.append("]}").toString();
      }
   }

   public record Line(String kind, String label, Lowball.Status status, double net, double cost, double profit, String via, String note) {
      String toJson() {
         return "{\"kind\":\""
            + this.kind
            + "\",\"label\":"
            + Json.str(this.label)
            + ",\"status\":\""
            + this.status
            + "\",\"net\":"
            + Json.num(this.net)
            + ",\"cost\":"
            + Json.num(this.cost)
            + ",\"profit\":"
            + Json.num(this.profit)
            + ",\"via\":"
            + Json.str(this.via)
            + ",\"note\":"
            + Json.str(this.note)
            + "}";
      }
   }

   public record Result(PlayerTrade trade, double cost, List<Lowball.Line> lines, double gained, double pending, int held) {
      String toJson() {
         StringBuilder sb = new StringBuilder("{\"trade\":")
            .append(this.trade.toJson())
            .append(",\"cost\":")
            .append(Json.num(this.cost))
            .append(",\"gained\":")
            .append(Json.num(this.gained))
            .append(",\"pending\":")
            .append(Json.num(this.pending))
            .append(",\"held\":")
            .append(this.held)
            .append(",\"lines\":[");

         for (int i = 0; i < this.lines.size(); i++) {
            if (i > 0) {
               sb.append(',');
            }

            sb.append(this.lines.get(i).toJson());
         }

         return sb.append("]}").toString();
      }
   }

   private static final class Run {
      private final List<PlayerTrade> ordered;
      private final List<AhAuction> auctions;
      private final List<Trade> events;
      private final List<AhLedger.Listing> ledger;
      private final Set<String> usedAuctions = new HashSet<>();
      private final Set<AhLedger.Listing> usedListings = Collections.newSetFromMap(new IdentityHashMap<>());
      private final double[] bazaarLeft;
      private final Map<String, Integer> giveLeft = new HashMap<>();
      private final long now;
      private final long gemWatchSince;
      private final Map<PlayerTrade, List<GemLog>> removals = new IdentityHashMap<>();

      Run(List<PlayerTrade> trades, List<AhAuction> auctions, List<Trade> events, List<GemLog> gemLog, long now) {
         this.ordered = new ArrayList<>(trades);
         this.ordered.sort(Comparator.comparingLong(PlayerTrade::ts));
         this.auctions = auctions;
         this.events = new ArrayList<>(events);
         this.events.sort(Comparator.comparingLong(Trade::ts));
         this.ledger = AhLedger.build(this.events, auctions);
         this.bazaarLeft = new double[this.events.size()];

         for (int i = 0; i < this.events.size(); i++) {
            this.bazaarLeft[i] = this.events.get(i).qty();
         }

         this.now = now;
         this.gemWatchSince = GemLog.watchSince(gemLog);
         List<GemLog> gone = new ArrayList<>();

         for (GemLog e : gemLog) {
            if (e.kind() == GemLog.Kind.REMOVED && e.uuid() != null) {
               gone.add(e);
            }
         }

         gone.sort(Comparator.comparingLong(GemLog::ts));

         for (GemLog e : gone) {
            PlayerTrade owner = this.ownerOf(e);
            if (owner != null) {
               this.removals.computeIfAbsent(owner, k -> new ArrayList<>()).add(e);
            }
         }
      }

      /** The latest lowball at or before the removal that bought the item (uuid). */
      private PlayerTrade ownerOf(GemLog removal) {
         for (int i = this.ordered.size() - 1; i >= 0; i--) {
            PlayerTrade t = this.ordered.get(i);
            if (t.ts() <= removal.ts() && isLowball(t)) {
               for (TradeItem item : t.received()) {
                  if (removal.uuid().equalsIgnoreCase(item.uuid())) {
                     return t;
                  }
               }
            }
         }

         return null;
      }

      private static boolean isLowball(PlayerTrade t) {
         return !t.received().isEmpty() && t.givenCoins() > 0.0;
      }

      List<Lowball.Result> run() {
         List<Lowball.Result> out = new ArrayList<>();

         for (PlayerTrade t : this.ordered) {
            if (isLowball(t)) {
               List<Lowball.Run.Part> parts = new ArrayList<>();

               for (TradeItem item : t.received()) {
                  parts.addAll(this.resolve(item, t));
               }

               // A trade from before gem watching whose item still carried parts when watching began is watched too:
               // its logged removals count, not the old guess.
               parts.addAll(t.ts() < this.gemWatchSince && !this.removals.containsKey(t) ? this.gemsGuessed(t) : this.partsRemoved(t));

               double totalNet = 0.0;

               // Removed parts (gems, drill parts...) carry no cost share: the coins paid are split over the items only,
               // a part's profit is its proceeds.
               for (Lowball.Run.Part p : parts) {
                  if (p.kind().equals("ITEM")) {
                     totalNet += Math.max(0.0, p.net());
                  }
               }

               List<Lowball.Line> lines = new ArrayList<>();
               double gained = 0.0;
               double pending = 0.0;
               int held = 0;

               for (Lowball.Run.Part p : parts) {
                  double cost = totalNet > 0.0 && p.net() > 0.0 && p.kind().equals("ITEM") ? t.givenCoins() * p.net() / totalNet : 0.0;
                  double profit = p.net() > 0.0 ? p.net() - cost : 0.0;
                  if (p.status() == Lowball.Status.SOLD) {
                     gained += profit;
                  } else if (p.status() == Lowball.Status.LISTED) {
                     pending += profit;
                  } else {
                     held++;
                  }

                  lines.add(new Lowball.Line(p.kind(), p.label(), p.status(), Math.max(0.0, p.net()), cost, profit, p.via(), p.note()));
               }

               out.add(new Lowball.Result(t, t.givenCoins(), lines, gained, pending, held));
            }
         }

         return out;
      }

      private List<Lowball.Run.Part> resolve(TradeItem item, PlayerTrade purchase) {
         List<Lowball.Run.Match> sold = new ArrayList<>();
         List<Lowball.Run.Match> listed = new ArrayList<>();
         offer(this.resale(item, purchase), sold, listed);
         if (item.uid() != null) {
            Lowball.Run.Match exact = this.viaCoflnet(item, purchase);
            offer(exact, sold, listed);
            if (exact == null) {
               offer(this.viaChat(item, purchase), sold, listed);
            }
         } else {
            offer(this.viaChat(item, purchase), sold, listed);
            offer(this.viaBazaar(item, purchase), sold, listed);
         }

         Lowball.Run.Match pick = null;
         if (!sold.isEmpty()) {
            pick = Collections.min(sold, Comparator.comparingLong(Lowball.Run.Match::ts));
         } else if (!listed.isEmpty()) {
            pick = listed.get(0);
         }

         if (pick == null) {
            return List.of(new Lowball.Run.Part("ITEM", label(item, item.count()), Lowball.Status.HELD, 0.0, null, heldNote(item)));
         } else {
            pick.commit().run();
            List<Lowball.Run.Part> parts = new ArrayList<>();
            parts.add(new Lowball.Run.Part("ITEM", label(item, pick.units()), pick.status(), pick.net(), pick.via(), pick.note()));
            if (pick.units() < item.count()) {
               parts.add(new Lowball.Run.Part("ITEM", label(item, item.count() - pick.units()), Lowball.Status.HELD, 0.0, null, "the rest is not sold yet"));
            }

            return parts;
         }
      }

      private static void offer(Lowball.Run.Match m, List<Lowball.Run.Match> sold, List<Lowball.Run.Match> listed) {
         if (m != null) {
            (m.status() == Lowball.Status.SOLD ? sold : listed).add(m);
         }
      }

      private static String label(TradeItem item, int units) {
         return units > 1 ? units + "x " + item.name() : item.name();
      }

      private static String heldNote(TradeItem item) {
         return item.uid() != null ? "not found in your auctions or trades yet" : "not listed, traded or sold yet";
      }

      private static boolean sameItem(TradeItem mine, TradeItem given) {
         return mine.uuid() != null ? mine.uuid().equals(given.uuid()) : given.uuid() == null && AhLedger.key(mine.name()).equals(AhLedger.key(given.name()));
      }

      private Lowball.Run.Match resale(TradeItem item, PlayerTrade purchase) {
         for (PlayerTrade r : this.ordered) {
            if (r.ts() > purchase.ts() && !(r.receivedCoins() <= 0.0) && !r.given().isEmpty()) {
               int totalUnits = 0;

               for (TradeItem g : r.given()) {
                  totalUnits += Math.max(1, g.count());
               }

               for (int gi = 0; gi < r.given().size(); gi++) {
                  TradeItem g = r.given().get(gi);
                  if (sameItem(item, g)) {
                     String k = r.ts() + ":" + gi;
                     int left = this.giveLeft.getOrDefault(k, g.count());
                     if (left > 0) {
                        int units = Math.min(item.count(), left);
                        double net = r.receivedCoins() * units / totalUnits;
                        return new Lowball.Run.Match(
                           Lowball.Status.SOLD, net, r.ts(), "TRADE", "sold to " + r.partner(), units, () -> this.giveLeft.put(k, left - units)
                        );
                     }
                  }
               }
            }
         }

         return null;
      }

      private Lowball.Run.Match viaCoflnet(TradeItem item, PlayerTrade purchase) {
         AhAuction a = this.find(item.uid(), purchase.ts());
         if (a == null) {
            return null;
         } else {
            Runnable commit = () -> this.usedAuctions.add(a.auctionId());
            return a.sold()
               ? new Lowball.Run.Match(Lowball.Status.SOLD, Lowball.netOf(a, a.soldFor()), a.endTs(), "AH", null, item.count(), commit)
               : new Lowball.Run.Match(Lowball.Status.LISTED, Lowball.netOf(a, a.startingBid()), 0L, "AH", null, item.count(), commit);
         }
      }

      private AhAuction find(String uid, long tradeTs) {
         AhAuction best = null;

         for (AhAuction a : this.auctions) {
            if (uid.equalsIgnoreCase(a.uid()) && !this.usedAuctions.contains(a.auctionId())) {
               if (a.sold() && a.endTs() >= tradeTs) {
                  if (best == null || !best.sold() || a.endTs() < best.endTs()) {
                     best = a;
                  }
               } else if (a.active() && best == null) {
                  best = a;
               }
            }
         }

         return best;
      }

      private Lowball.Run.Match viaChat(TradeItem item, PlayerTrade purchase) {
         String key = AhLedger.key(item.name());
         List<AhLedger.Listing> skipped = new ArrayList<>();
         AhLedger.Listing chosen = null;

         for (AhLedger.Listing l : this.ledger) {
            if (!this.usedListings.contains(l) && l.listedTs() > purchase.ts() && key.equals(AhLedger.key(l.name()))) {
               if (l.state() != AhLedger.State.CANCELED && l.state() != AhLedger.State.EXPIRED) {
                  chosen = l;
                  break;
               }

               skipped.add(l);
            }
         }

         if (chosen == null) {
            return null;
         } else {
            AhLedger.Listing listing = chosen;
            Runnable commit = () -> {
               this.usedListings.add(listing);
               this.usedListings.addAll(skipped);
            };
            double net = listing.net();
            if (listing.sold()) {
               long ts = listing.soldTs() > 0L ? listing.soldTs() : listing.listedTs();
               String note = listing.state() == AhLedger.State.COLLECTED ? "coins collected" : "sold, coins not collected yet";
               return new Lowball.Run.Match(Lowball.Status.SOLD, Double.isNaN(net) ? 0.0 : net, ts, "AH", note, item.count(), commit);
            } else {
               return new Lowball.Run.Match(
                  Lowball.Status.LISTED, Double.isNaN(net) ? 0.0 : net, 0L, "AH", Double.isNaN(net) ? "price not known yet" : null, item.count(), commit
               );
            }
         }
      }

      private Lowball.Run.Match viaBazaar(TradeItem item, PlayerTrade purchase) {
         String key = AhLedger.key(item.name());
         List<double[]> take = new ArrayList<>();
         double want = item.count();
         double coins = 0.0;
         double taken = 0.0;
         long first = Long.MAX_VALUE;

         for (int i = 0; i < this.events.size() && want > 0.0; i++) {
            Trade e = this.events.get(i);
            if (isBazaarSale(e)
               && e.item() != null
               && !(this.bazaarLeft[i] <= 0.0)
               && e.qty() > 0
               && key.equals(AhLedger.key(e.item()))
               && e.ts() >= purchase.ts()
               && e.ts() <= purchase.ts() + BAZAAR_WINDOW_MS) {
               double units = Math.min(want, this.bazaarLeft[i]);
               take.add(new double[]{i, units});
               coins += e.coins() * units / e.qty();
               want -= units;
               taken += units;
               first = Math.min(first, e.ts());
            }
         }

         if (take.isEmpty()) {
            return null;
         } else {
            Runnable commit = () -> {
               for (double[] t : take) {
                  this.bazaarLeft[(int)t[0]] = this.bazaarLeft[(int)t[0]] - t[1];
               }
            };
            return new Lowball.Run.Match(Lowball.Status.SOLD, coins, first, "BAZAAR", null, (int)Math.round(taken), commit);
         }
      }

      /** Parts taken off a bought item (gem log) and sold or listed on the Bazaar or the AH soon after. */
      private List<Lowball.Run.Part> partsRemoved(PlayerTrade t) {
         Map<String, Lowball.Run.GemSale> byPart = new LinkedHashMap<>();

         for (GemLog e : this.removals.getOrDefault(t, List.of())) {
            for (Parts.Part p : e.parts()) {
               String name = p.name() != null ? p.name() : Parts.name(p.id());
               Lowball.Run.GemSale sale = byPart.computeIfAbsent(p.id(), k -> new Lowball.Run.GemSale(Parts.isGem(p.id())));
               long from = e.ts() - GEM_SLACK_MS;
               long to = e.ts() + GEM_WINDOW_MS;
               this.sellGem(name, 1.0, from, to, sale);
               if (sale.pendingWant > 0.0) {
                  this.listAh(name, from, to, sale);
               }

               if (sale.pendingWant > 0.0 && this.now <= to) {
                  sale.held += sale.pendingWant;
               }

               sale.pendingWant = 0.0;
               sale.name = sale.name == null ? name : sale.name;
            }
         }

         List<Lowball.Run.Part> parts = new ArrayList<>();

         for (Lowball.Run.GemSale sale : byPart.values()) {
            sale.addParts(sale.name, "removed, sold on the Bazaar", parts);
         }

         return parts;
      }

      /** The part put up on the AH (chat log, enriched from Coflnet) in [from, to]: sold or still listed. */
      private void listAh(String name, long from, long to, Lowball.Run.GemSale sale) {
         for (AhLedger.Listing l : this.ledger) {
            if (sale.pendingWant <= 0.0) {
               return;
            }

            if (!this.usedListings.contains(l)
               && l.listedTs() >= from
               && l.listedTs() <= to
               && l.state() != AhLedger.State.CANCELED
               && l.state() != AhLedger.State.EXPIRED
               && Parts.sameName(name, l.name())) {
               this.usedListings.add(l);
               sale.pendingWant--;
               double net = Double.isNaN(l.net()) ? 0.0 : l.net();
               if (l.sold()) {
                  sale.ahSold++;
                  sale.ahSoldCoins += net;
               } else {
                  sale.ahListed++;
                  sale.ahListedCoins += net;
               }
            }
         }
      }

      /** Trades from before gem watching: gems that were on the items and sold on the Bazaar within a day of the trade. */
      private List<Lowball.Run.Part> gemsGuessed(PlayerTrade t) {
         Map<String, Integer> wanted = new LinkedHashMap<>();

         for (TradeItem item : t.received()) {
            for (TradeItem.Gem g : item.gems()) {
               wanted.merge(g.bazaarName(), Math.max(1, item.count()), Integer::sum);
            }
         }

         List<Lowball.Run.Part> parts = new ArrayList<>();

         for (Entry<String, Integer> w : wanted.entrySet()) {
            Lowball.Run.GemSale sale = new Lowball.Run.GemSale(true);
            this.sellGem(w.getKey(), w.getValue(), t.ts(), t.ts() + GEM_WINDOW_MS, sale);
            sale.addParts(w.getKey(), "sold on the Bazaar within a day of the trade", parts);
         }

         return parts;
      }

      /**
       * Takes up to {@code want} units of the gem/part from Bazaar sales in [from, to]: insta-sells count as sold; a sell offer
       * set up in the window is sold as far as later claims cover it, the rest is listed. Unmatched units stay in pendingWant.
       */
      private void sellGem(String bazaarName, double want, long from, long to, Lowball.Run.GemSale sale) {

         for (int i = 0; i < this.events.size() && want > 0.0; i++) {
            Trade e = this.events.get(i);
            if (e.ts() >= from && e.ts() <= to && this.bazaarLeft[i] > 0.0 && e.qty() > 0 && Parts.sameName(bazaarName, e.item())) {
               if (e.kind() == Trade.Kind.BZ_INSTA_SELL) {
                  double take = Math.min(want, this.bazaarLeft[i]);
                  this.bazaarLeft[i] -= take;
                  want -= take;
                  sale.sold += take;
                  sale.soldCoins += e.coins() * take / e.qty();
               } else if (e.kind() == Trade.Kind.BZ_SELL_OFFER_SETUP) {
                  double take = Math.min(want, this.bazaarLeft[i]);
                  this.bazaarLeft[i] -= take;
                  want -= take;
                  double unclaimed = take;

                  for (int j = i + 1; j < this.events.size() && unclaimed > 0.0; j++) {
                     Trade c = this.events.get(j);
                     if (c.kind() == Trade.Kind.BZ_CLAIM_SOLD
                        && this.bazaarLeft[j] > 0.0
                        && c.qty() > 0
                        && samePrice(c.unit(), e.unit())
                        && Parts.sameName(bazaarName, c.item())) {
                        double got = Math.min(unclaimed, this.bazaarLeft[j]);
                        this.bazaarLeft[j] -= got;
                        unclaimed -= got;
                        sale.sold += got;
                        sale.soldCoins += c.coins() * got / c.qty();
                     }
                  }

                  sale.listed += unclaimed;
                  sale.listedCoins += e.coins() / e.qty() * unclaimed * (1.0 - BAZAAR_TAX);
               }
            }
         }

         sale.pendingWant += want;
      }

      /**
       * A claim belongs to the offer with its "at X each" price (the setup's total / qty, rounded in chat).
       * ponytail: two offers of one item within 1% of each other can still swap claims; harmless for profit.
       */
      static boolean samePrice(double claimUnit, double offerUnit) {
         return Math.abs(claimUnit - offerUnit) <= offerUnit * 0.01 + 0.1;
      }

      private static boolean isBazaarSale(Trade e) {
         return e.kind() == Trade.Kind.BZ_INSTA_SELL || e.kind() == Trade.Kind.BZ_CLAIM_SOLD;
      }

      private record Match(Lowball.Status status, double net, long ts, String via, String note, int units, Runnable commit) {
      }

      private record Part(String kind, String label, Lowball.Status status, double net, String via, String note) {
      }

      private static final class GemSale {
         final String kind;
         String name;
         double sold;
         double soldCoins;
         double listed;
         double listedCoins;
         int ahSold;
         double ahSoldCoins;
         int ahListed;
         double ahListedCoins;
         double held;
         double pendingWant;

         GemSale(boolean gem) {
            this.kind = gem ? "GEM" : "PART";
         }

         void addParts(String name, String soldNote, List<Lowball.Run.Part> out) {
            this.add(out, this.sold, name, Lowball.Status.SOLD, this.soldCoins, "BAZAAR", soldNote);
            this.add(out, this.listed, name, Lowball.Status.LISTED, this.listedCoins, "BAZAAR", "sell offer not claimed yet");
            this.add(out, this.ahSold, name, Lowball.Status.SOLD, this.ahSoldCoins, "AH", "removed, sold on the AH");
            this.add(out, this.ahListed, name, Lowball.Status.LISTED, this.ahListedCoins, "AH", "removed, listed on the AH");
            this.add(out, this.held, name, Lowball.Status.HELD, 0.0, null, "removed, not sold yet");
         }

         private void add(List<Lowball.Run.Part> out, double units, String name, Lowball.Status status, double net, String via, String note) {
            if (units > 0.0) {
               out.add(new Lowball.Run.Part(this.kind, Math.round(units) + "x " + name, status, net, via, note));
            }
         }
      }
   }

   public static enum Status {
      SOLD,
      LISTED,
      HELD;
   }
}
