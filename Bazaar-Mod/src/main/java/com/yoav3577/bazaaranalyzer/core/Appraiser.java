package com.yoav3577.bazaaranalyzer.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What an Auction House item is worth. Picked by backtests on ~8100 real sales of 94 item types (Coflnet, 2026-09;
 * notes/price-prediction.md): median error 6.3% (recent sales) and ~10% (1-2 sales + history + anchor).
 * <ol>
 * <li>Recent sales (Coflnet keeps ~7 days of single sales) that meet the item's important modifiers (the website link's
 * filters): each price moved by ALPHA of the difference in modifier cost to this item, then a median weighted by
 * recency (half-life 1 h) and closeness in modifier cost; stat trackers (Midas bid, kills) move it by their coins per unit
 * among the sales. Capped at ANCHOR_CAP x the anchor, except for tracked items (the clean BIN doesn't know the tracker).</li>
 * <li>1-2 of those: the same, never above the anchor.</li>
 * <li>None: Coflnet's daily history for the same filters over 30 days, over 90 days when the
 * 30 days hold fewer than MIN_SALES sales; never above the anchor.</li>
 * <li>Nothing sold: the anchor = clean lowest BIN + ANCHOR_ALPHA x modifier cost; without one (pets) the cheapest listing meeting
 * the filters, moved to this item's modifier cost.</li>
 * </ol>
 * A live BIN listing of the same item (modifier cost within CLOSE_LISTING) caps the result: it can't sell above that.
 * So does the easy craft cost (capByCraft): nobody pays more than it costs to buy the parts and craft it.
 */
public final class Appraiser {
   /** Share of a modifier's cost that a sale price moves by (tested 0.5-0.9: 0.6-0.8 best). */
   public static final double ALPHA = 0.7;
   /** A sale's weight halves every hour (tested 0.5 h-3 days on 8100 sales of 94 items: 1 h best; prices move within hours). */
   public static final long HALF_LIFE_MS = 3600_000L;
   /** ...but at least the age of the 3rd newest comparable sale (tested 1-8: 3 best; 34 items 5.3% -> 5.0%). */
   public static final int K_NEWEST = 3;
   /** Closeness in modifier cost, as a share of the item's value. */
   public static final double WIDTH = 0.10;
   /** Closeness of stat trackers (Midas winning bid, Final Destination kills...), in log units. */
   public static final double TRACKER_SCALE = 0.5;
   /** Share of the modifier cost in the anchor (clean BIN + mods): 0.9 beat 0.7 on sales priced without recent comps. */
   public static final double ANCHOR_ALPHA = 0.9;
   public static final double ANCHOR_CAP = 1.5;
   public static final double BOGUS_CLEAN = 0.5;
   public static final double CLOSE_LISTING = 0.05;
   public static final int MIN_SALES = 3;
   public static final List<String> TRACKERS = List.of("winning_bid", "eman_kills", "additional_coins");
   public static final String TIER_BOOST = "PET_ITEM_TIER_BOOST";
   private static final long DAY_MS = 86_400_000L;
   private static final long HISTORY_HALF_LIFE_MS = 2 * DAY_MS;
   private static final Pattern LEVEL = Pattern.compile("\\[Lvl (\\d+)]");

   private Appraiser() {
   }

   public enum Source {
      RECENT("recent sales"),
      FEW("1-2 recent sales"),
      MONTH("30-day history"),
      QUARTER("90-day history"),
      ANCHOR("clean lowest BIN + mods"),
      LISTING("lowest BIN with the filters"),
      NONE("no data");

      public final String label;

      Source(String label) {
         this.label = label;
      }
   }

   /** One item (the one being valued, a sold auction or a BIN listing) as the estimate compares them. */
   /** auction = a sale that ended on a bid (not a BIN): 1.4% of sales, priced all over (1-coin pets, bid-up armor). */
   public record Item(
      double price, long ts, double mods, Integer petLevel, String petTier, boolean tierBoost, boolean shiny, Map<String, Double> trackers, boolean auction
   ) {
      public Item {
         trackers = trackers == null ? Map.of() : Map.copyOf(trackers);
      }

      public Item(double price, long ts, double mods, Integer petLevel, String petTier, boolean tierBoost, boolean shiny, Map<String, Double> trackers) {
         this(price, ts, mods, petLevel, petTier, tierBoost, shiny, trackers, false);
      }

      public boolean sameKind(Item o) {
         return this.shiny == o.shiny && this.tierBoost == o.tierBoost && (this.petTier == null || this.petTier.equals(o.petTier));
      }
   }

   /** One day of Coflnet's /item/price/{tag}/history/{month|full}. */
   public record Day(long ts, double min, double avg, double volume) {
   }

   /**
    * value NaN = no estimate. recent/monthEst/quarterEst/anchor/listingCap/lowestBin are NaN when not known; month and
    * quarter are the sale counts (-1 = not looked at); craftCapped = value is the easy craft cost (capByCraft).
    */
   public record Result(
      double value, Source source, int comps, double recent, int month, double monthEst, int quarter, double quarterEst, double anchor,
      double lowestBin, double listingCap, boolean capped, boolean craftCapped
   ) {
      public Result(
         double value, Source source, int comps, double recent, int month, double monthEst, int quarter, double quarterEst, double anchor,
         double lowestBin, double listingCap, boolean capped
      ) {
         this(value, source, comps, recent, month, monthEst, quarter, quarterEst, anchor, lowestBin, listingCap, capped, false);
      }
   }

   // ---------------------------------------------------------------- items

   /** A Coflnet sold row (/auctions/tag/{TAG}/sold) or active BIN row (/active/bin); mods = CraftEngine.modValue(tag, rowNbt(row)). */
   public static Item ofRow(JsonObject row, double mods, boolean sold) {
      double count = Math.max(1.0, CoflFilters.num(CoflFilters.str(row, "count")));
      double price = CoflFilters.num(CoflFilters.str(row, sold ? "highestBidAmount" : "startingBid")) / count;
      JsonObject flat = flat(row);
      String tag = CoflFilters.str(row, "tag");
      boolean pet = tag != null && tag.startsWith("PET_");
      return new Item(
         price, time(CoflFilters.str(row, sold ? "end" : "start")), mods, level(CoflFilters.str(row, "itemName")),
         pet ? upper(CoflFilters.str(flat, "tier")) : null, TIER_BOOST.equals(CoflFilters.str(flat, "heldItem")), truthy(CoflFilters.str(flat, "is_shiny")),
         trackers(flat), sold && "false".equalsIgnoreCase(CoflFilters.str(row, "bin"))
      );
   }

   /** The item being valued: its hover name and its raw SkyBlock NBT (custom_data as JSON, petInfo as a JSON string). */
   public static Item target(String name, JsonObject nbt, double mods) {
      JsonObject pet = petInfo(nbt);
      return new Item(
         Double.NaN, 0L, mods, level(name), pet == null ? null : upper(CoflFilters.str(pet, "tier")),
         pet != null && TIER_BOOST.equals(CoflFilters.str(pet, "heldItem")), nbt != null && truthy(CoflFilters.str(nbt, "is_shiny")), trackers(nbt)
      );
   }

   /** A Coflnet row's nbt in the shape the craft engine reads: its flat nbt + "enchantments": {type: level}. */
   public static JsonObject rowNbt(JsonObject row) {
      JsonObject n = flat(row).deepCopy();
      JsonObject en = new JsonObject();
      if (row.get("enchantments") instanceof JsonArray a) {
         for (JsonElement e : a) {
            if (e instanceof JsonObject o && CoflFilters.str(o, "type") != null) {
               en.add(CoflFilters.str(o, "type"), o.get("level"));
            }
         }
      }

      n.add("enchantments", en);
      return n;
   }

   /** Pet level from a name like "[Lvl 100] Golden Dragon", else null. */
   public static Integer level(String name) {
      Matcher m = name == null ? null : LEVEL.matcher(name);
      return m != null && m.find() ? Integer.parseInt(m.group(1)) : null;
   }

   /** The pet-level filter for comparable pets: +-2 levels near max, else +-15% (at least 3). */
   public static Filter levelBand(int level) {
      int band = level >= 95 ? 2 : Math.max(3, (int) (level * 0.15));
      return Filter.petLevel(Math.max(1, level - band), level + band);
   }

   /** Coflnet history rows -> days (bad rows skipped). */
   public static List<Day> days(JsonArray rows) {
      List<Day> out = new ArrayList<>();
      for (JsonElement e : rows) {
         if (e instanceof JsonObject o) {
            long ts = time(CoflFilters.str(o, "time"));
            double avg = CoflFilters.num(CoflFilters.str(o, "avg"));
            double vol = CoflFilters.num(CoflFilters.str(o, "volume"));
            if (ts > 0 && avg > 0 && vol > 0) {
               out.add(new Day(ts, CoflFilters.num(CoflFilters.str(o, "min")), avg, vol));
            }
         }
      }

      return out;
   }

   /** Number of sales in the days from `from` on. */
   public static int volume(List<Day> days, long from) {
      double v = 0;
      for (Day d : days) {
         if (d.ts() >= from) {
            v += d.volume();
         }
      }

      return (int) Math.round(v);
   }

   /** The last 90 days: the 30-day history plus the older days of the full history (one entry per day, the 30-day one wins). */
   public static List<Day> quarter(List<Day> month, List<Day> full, long now) {
      Map<Long, Day> byDay = new LinkedHashMap<>();
      for (Day d : full) {
         if (d.ts() >= now - 90 * DAY_MS) {
            byDay.put(d.ts() / DAY_MS, d);
         }
      }

      for (Day d : month) {
         byDay.put(d.ts() / DAY_MS, d);
      }

      return new ArrayList<>(byDay.values());
   }

   // ---------------------------------------------------------------- estimate

   /**
    * t = the item; sold = recent sales meeting its filters (CoflFilters.matches); month = 30-day history for the same
    * filters; quarter = 90-day history (null when not fetched: only needed when month holds fewer than MIN_SALES);
    * listings = active BINs meeting the filters; clean = the clean lowest BIN (NaN if unknown).
    */
   public static Result estimate(Item t, List<Item> sold, List<Day> month, List<Day> quarter, List<Item> listings, double clean, long now) {
      List<Item> comps = sold.stream().filter(c -> c.price() > 0 && t.sameKind(c)).toList();
      List<Item> bins = comps.stream().filter(c -> !c.auction()).toList();
      if (bins.size() >= MIN_SALES) {
         comps = bins;   // auctions only when BIN sales are too few
      }
      if (bogusClean(clean, comps)) {
         clean = Double.NaN;
      }

      double anchor = ok(clean) ? clean + ANCHOR_ALPHA * t.mods() : Double.NaN;
      double recent = recent(t, comps, clean, now, MIN_SALES);
      double few = ok(recent) ? Double.NaN : recent(t, comps, clean, now, 1);
      int nMonth = month == null ? -1 : volume(month, now - 30 * DAY_MS);
      double monthEst = month == null ? Double.NaN : history(month, now - 30 * DAY_MS, now);
      int nQuarter = quarter == null ? -1 : volume(quarter, now - 90 * DAY_MS);
      double quarterEst = quarter == null ? Double.NaN : history(quarter, now - 90 * DAY_MS, now);
      double value;
      Source source;
      if (ok(recent)) {
         value = ok(anchor) && t.trackers().isEmpty() ? Math.min(recent, ANCHOR_CAP * anchor) : recent;
         source = Source.RECENT;
      } else if (ok(few)) {
         // 1-2 sales know this item better than the history ("at least" filters) or the anchor, but can be one-offs:
         // never above the anchor (thin-market backtest: 15.6% -> 10.3% median error, no more big overestimates)
         value = ok(anchor) && t.trackers().isEmpty() ? Math.min(few, anchor) : few;
         recent = few;
         source = Source.FEW;
      } else if (nMonth >= MIN_SALES && ok(monthEst)) {
         value = ok(anchor) ? Math.min(monthEst, anchor) : monthEst;
         source = Source.MONTH;
      } else if (nQuarter >= MIN_SALES && ok(quarterEst)) {
         value = ok(anchor) ? Math.min(quarterEst, anchor) : quarterEst;
         source = Source.QUARTER;
      } else if (ok(anchor)) {
         value = anchor;
         source = Source.ANCHOR;
      } else {
         value = Double.NaN;
         source = Source.NONE;
      }

      double lowest = Double.NaN;
      double cap = Double.NaN;
      double scale = ok(value) ? value : ok(clean) ? clean + t.mods() : Double.NaN;
      for (Item l : listings) {
         if (l.price() > 0) {
            lowest = ok(lowest) ? Math.min(lowest, l.price()) : l.price();
            double d = l.mods() - t.mods();
            if (t.sameKind(l) && ok(scale) && Math.abs(d) <= CLOSE_LISTING * scale) {
               double adj = l.price() - ALPHA * d;
               cap = ok(cap) ? Math.min(cap, adj) : adj;
            }
         }
      }

      boolean capped = ok(cap) && ok(value) && cap < value;
      if (capped) {
         value = cap;
      } else if (!ok(value)) {
         for (Item l : listings) {
            double adj = l.price() - ALPHA * (l.mods() - t.mods());
            if (l.price() > 0 && t.sameKind(l) && adj > 0 && (!ok(value) || adj < value)) {
               value = adj;
               source = Source.LISTING;
            }
         }
      }

      return new Result(value, source, comps.size(), recent, nMonth, monthEst, nQuarter, quarterEst, anchor, lowest, cap, capped);
   }

   /**
    * The estimate, never above the easy craft cost (buy the parts, craft it). complete = every craft part has a price:
    * with parts missing the craft costs more than the quote, so it caps nothing.
    */
   public static Result capByCraft(Result r, double easy, boolean complete) {
      if (r == null || !complete || !ok(easy) || !ok(r.value()) || r.value() <= easy) {
         return r;
      }

      return new Result(
         easy, r.source(), r.comps(), r.recent(), r.month(), r.monthEst(), r.quarter(), r.quarterEst(), r.anchor(), r.lowestBin(), r.listingCap(), r.capped(), true
      );
   }

   /**
    * A clean lowest BIN under BOGUS_CLEAN x the lower quartile of what the comparable sales are worth without their
    * modifiers is a mispriced listing (sniped in seconds; Aspect of the Dragon: 100k vs 1.6M sales), not an anchor.
    */
   static boolean bogusClean(double clean, List<Item> comps) {
      if (!ok(clean) || comps.size() < MIN_SALES) {
         return false;
      }

      List<Double> bare = comps.stream().map(c -> c.price() - ALPHA * c.mods()).sorted().toList();
      return clean < BOGUS_CLEAN * bare.get(bare.size() / 4);
   }

   /** Weighted median of the comparable sales moved to this item's modifier cost; NaN with fewer than MIN_SALES. */
   static double recent(Item t, List<Item> comps, double clean, long now, int minSales) {
      if (comps.size() < minSales) {
         return Double.NaN;
      }

      double base = (ok(clean) ? clean : median(comps.stream().map(Item::price).toList())) + t.mods();
      Map<String, Double> slopes = new HashMap<>();
      for (String k : TRACKERS) {
         slopes.put(k, trackerSlope(comps, k));
      }

      // the half-life stretches to the K_NEWEST-th newest sale: a sale a day is no reason to trust only the last one
      List<Long> ages = comps.stream().map(c -> Math.max(0L, now - c.ts())).sorted().toList();
      double halfLife = Math.max(HALF_LIFE_MS, ages.get(Math.min(K_NEWEST, ages.size()) - 1));
      List<double[]> vw = new ArrayList<>();
      for (Item c : comps) {
         double d = c.mods() - t.mods();
         double w = 1.0 / (1.0 + Math.abs(d) / (WIDTH * base)) * Math.pow(0.5, Math.max(0L, now - c.ts()) / halfLife) * trackerSim(t, c);
         double v = c.price() - ALPHA * d;
         for (String k : TRACKERS) {
            v += slopes.get(k) * (tracker(t, k) - tracker(c, k));
         }
         vw.add(new double[]{v, w});
      }

      return weightedMedian(vw);
   }

   /** Weighted median of each day's (min + avg) / 2 (min floored at avg / 2 against troll sales), weight = volume x recency. */
   static double history(List<Day> days, long from, long now) {
      List<double[]> vw = new ArrayList<>();
      for (Day d : days) {
         if (d.ts() >= from) {
            double min = Math.max(d.min(), d.avg() / 2);
            vw.add(new double[]{(min + d.avg()) / 2, d.volume() * Math.pow(0.5, Math.max(0L, now - d.ts()) / (double) HISTORY_HALF_LIFE_MS)});
         }
      }

      return vw.isEmpty() ? Double.NaN : weightedMedian(vw);
   }

   /**
    * Coins per tracker unit (a kill, a coin of Midas bid) among the comps: median of the pairwise slopes (Theil-Sen,
    * so a few odd sales don't tilt it), floored at 0; 0 when fewer than MIN_SALES comps carry the tracker.
    * ponytail: O(n^2) pairs, n = one week of one item's sales (a few hundred at most).
    */
   static double trackerSlope(List<Item> comps, String k) {
      List<Item> with = comps.stream().filter(c -> c.trackers().containsKey(k)).toList();
      if (with.size() < MIN_SALES) {
         return 0.0;
      }

      List<Double> slopes = new ArrayList<>();
      for (int i = 0; i < with.size(); i++) {
         for (int j = i + 1; j < with.size(); j++) {
            double dx = tracker(with.get(j), k) - tracker(with.get(i), k);
            if (dx != 0) {
               slopes.add((with.get(j).price() - ALPHA * with.get(j).mods() - with.get(i).price() + ALPHA * with.get(i).mods()) / dx);
            }
         }
      }

      return slopes.isEmpty() ? 0.0 : Math.max(0.0, median(slopes));
   }

   private static double tracker(Item i, String k) {
      return i.trackers().getOrDefault(k, 0.0);
   }

   private static double trackerSim(Item a, Item b) {
      double w = 1.0;
      for (String k : TRACKERS) {
         Double x = a.trackers().get(k);
         Double y = b.trackers().get(k);
         if (x != null || y != null) {
            w *= 1.0 / (1.0 + Math.abs(Math.log1p(x == null ? 0 : x) - Math.log1p(y == null ? 0 : y)) / TRACKER_SCALE);
         }
      }

      return w;
   }

   static double weightedMedian(List<double[]> valueWeight) {
      List<double[]> s = new ArrayList<>(valueWeight);
      s.sort(Comparator.comparingDouble(x -> x[0]));
      double total = 0;
      for (double[] x : s) {
         total += x[1];
      }

      if (!(total > 0)) {
         return median(s.stream().map(x -> x[0]).toList());
      }

      double acc = 0;
      for (double[] x : s) {
         acc += x[1];
         if (acc >= total / 2) {
            return x[0];
         }
      }

      return s.get(s.size() - 1)[0];
   }

   static double median(List<Double> v) {
      return Worth.median(v);
   }

   // ---------------------------------------------------------------- json bits

   private static JsonObject flat(JsonObject row) {
      for (String k : new String[]{"flattenedNbt", "flatNbt"}) {
         if (row.get(k) instanceof JsonObject o) {
            return o;
         }
      }

      return new JsonObject();
   }

   private static JsonObject petInfo(JsonObject nbt) {
      JsonElement p = nbt == null ? null : nbt.get("petInfo");
      if (p != null && p.isJsonPrimitive()) {
         try {
            p = JsonParser.parseString(p.getAsString());
         } catch (RuntimeException e) {
            return null;
         }
      }

      return p instanceof JsonObject o ? o : null;
   }

   private static Map<String, Double> trackers(JsonObject o) {
      Map<String, Double> out = new HashMap<>();
      if (o != null) {
         for (String k : TRACKERS) {
            String v = CoflFilters.str(o, k);
            if (v != null && CoflFilters.num(v) > 0) {
               out.put(k, CoflFilters.num(v));
            }
         }
      }

      return out;
   }

   /** Coflnet times are UTC without a zone ("2026-09-25T23:05:17"); 0 when missing or bad. */
   static long time(String s) {
      if (s == null) {
         return 0L;
      }

      try {
         return LocalDateTime.parse(s.length() > 19 ? s.substring(0, 19) : s).toInstant(ZoneOffset.UTC).toEpochMilli();
      } catch (RuntimeException e) {
         return 0L;
      }
   }

   private static String upper(String s) {
      return s == null ? null : s.toUpperCase(Locale.ROOT);
   }

   private static boolean truthy(String s) {
      return s != null && !s.isEmpty() && !s.equals("0") && !s.equalsIgnoreCase("false");
   }

   private static boolean ok(double v) {
      return Double.isFinite(v) && v > 0;
   }
}
