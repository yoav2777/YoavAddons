package com.yoav3577.bazaaranalyzer.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Trade window worth texts: the inflation check, the craft "!" and the hover lines (the value itself: Appraiser).
 */
public final class Worth {
   public static final double INFLATED_AT = 1.25;
   public static final int MIN_DAYS = 5;
   private static final long DAY_MS = 86400000L;

   private Worth() {
   }

   /** The item type's normal price: median of the daily lowest sales from 30 to 2 days ago; NaN with fewer than MIN_DAYS days. */
   public static double normalPrice(List<Worth.Day> days, long now) {
      List<Double> mins = new ArrayList<>();

      for (Worth.Day d : days) {
         if (d.ts() >= now - 30L * DAY_MS && d.ts() < now - 2L * DAY_MS && ok(d.min())) {
            mins.add(d.min());
         }
      }

      return mins.size() < MIN_DAYS ? Double.NaN : median(mins);
   }

   /** The warning when the clean lowest BIN is INFLATED_AT times the normal price or more, else null. */
   public static String inflation(String name, double lowestBin, double normal) {
      if (ok(lowestBin) && ok(normal) && lowestBin >= INFLATED_AT * normal) {
         long pct = Math.round((lowestBin / normal - 1.0) * 100.0);
         return "⚠ " + name + " looks inflated: lowest BIN " + pct + "% above its 30-day normal (" + coins(lowestBin) + " vs " + coins(normal) + ")";
      } else {
         return null;
      }
   }

   /** True when crafting ({fromScratch, easy}, either may be NaN) is cheaper than what the item is worth. */
   public static boolean craftBeats(double[] quote, double worth) {
      return quote != null && quote.length >= 2 && ok(worth) && (ok(quote[0]) && quote[0] < worth || ok(quote[1]) && quote[1] < worth);
   }

   /** The "!" hover lines; missing = craft parts without a price (the craft then costs more than shown). */
   public static List<String> craftLines(String name, double[] quote, double worth, List<String> missing) {
      List<String> out = new ArrayList<>(List.of(
         name + ":",
         "  Craft from scratch: " + coins(quote[0]),
         "  Craft the easy way: " + coins(quote[1]),
         "  Worth: " + coins(worth)
      ));
      if (missing != null && !missing.isEmpty()) {
         out.add("  Not priced: " + String.join(", ", missing.stream().limit(4).toList()) + (missing.size() > 4 ? ", ..." : "") + " (craft costs more)");
      }

      return out;
   }

   public static String line(String name, double value, String note) {
      return name + ": " + coins(value) + (note == null ? "" : " (" + note + ")");
   }

   public static String failedLine(String name, String reason) {
      return name + ": failed (" + reason + ")";
   }

   /** Same rule as TradeWindow: the coins stack in a trade is not an item. */
   public static boolean isCoins(String name, List<String> lore) {
      if (name != null && name.endsWith(" coins")) {
         return true;
      } else {
         for (String l : lore) {
            if (l.contains("Lump-sum amount")) {
               return true;
            }
         }

         return false;
      }
   }

   /** 1.23B, 812M, 12.3M, 950k, 12.3k, 999. */
   public static String coins(double v) {
      double a = Math.abs(v);
      if (!Double.isFinite(v)) {
         return "-";
      } else if (a >= 1.0E9) {
         return String.format(Locale.ROOT, "%.2fB", v / 1.0E9);
      } else if (a >= 1.0E8) {
         return String.format(Locale.ROOT, "%.0fM", v / 1.0E6);
      } else if (a >= 1.0E6) {
         return String.format(Locale.ROOT, "%.1fM", v / 1.0E6);
      } else if (a >= 1.0E5) {
         return String.format(Locale.ROOT, "%.0fk", v / 1.0E3);
      } else if (a >= 1.0E3) {
         return String.format(Locale.ROOT, "%.1fk", v / 1.0E3);
      } else {
         return String.format(Locale.ROOT, "%.0f", v);
      }
   }

   static double median(List<Double> values) {
      if (values.isEmpty()) {
         return Double.NaN;
      } else {
         List<Double> s = values.stream().sorted().toList();
         int n = s.size();
         return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
      }
   }

   private static boolean ok(double v) {
      return Double.isFinite(v) && v > 0.0;
   }

   /** One day of the Coflnet month history: its lowest sale price. */
   public record Day(long ts, double min) {
   }

}
