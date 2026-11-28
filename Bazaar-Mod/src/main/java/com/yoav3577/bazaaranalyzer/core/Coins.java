package com.yoav3577.bazaaranalyzer.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Coins {
   private static final Pattern AMOUNT = Pattern.compile("(\\d[\\d,]*(?:\\.\\d+)?)\\s*([kKmMbB])?");

   private Coins() {
   }

   public static double parse(String text) {
      if (text == null) {
         return Double.NaN;
      } else {
         Matcher m = AMOUNT.matcher(text);
         if (!m.find()) {
            return Double.NaN;
         } else {
            double v;
            try {
               v = Double.parseDouble(m.group(1).replace(",", ""));
            } catch (NumberFormatException var5) {
               return Double.NaN;
            }

            String suffix = m.group(2);
            if (suffix != null) {
               v *= switch (Character.toLowerCase(suffix.charAt(0))) {
                  case 'k' -> 1000.0;
                  case 'm' -> 1000000.0;
                  default -> 1.0E9;
               };
            }

            return v;
         }
      }
   }

   public static String shortFmt(double v) {
      if (Double.isNaN(v)) {
         return "?";
      }

      double a = Math.abs(v);
      String s;
      if (a < 1000.0) {
         s = String.format(Locale.ROOT, "%.0f", a);
      } else if (a < 1.0E6) {
         s = trim(a / 1000.0) + "k";
      } else if (a < 1.0E9) {
         s = trim(a / 1.0E6) + "M";
      } else {
         s = trim(a / 1.0E9) + "B";
      }

      return v < 0 ? "-" + s : s;
   }

   /** Item counts: whole numbers with commas, a fraction below 100 (1.5 Enchanted Mithril), short above a million. */
   public static String count(double v) {
      if (v >= 1.0E6) {
         return shortFmt(v);
      } else if (v < 100.0 && Math.abs(v - Math.rint(v)) > 0.05) {
         return String.format(Locale.ROOT, "%.1f", v);
      } else {
         return String.format(Locale.ROOT, "%,d", Math.round(v));
      }
   }

   /** "45s", "12m 5s", "1h 03m". */
   public static String time(long ms) {
      long s = ms / 1000L;
      if (s < 60L) {
         return s + "s";
      } else if (s < 3600L) {
         return s / 60L + "m " + s % 60L + "s";
      } else {
         return s / 3600L + "h " + String.format(Locale.ROOT, "%02d", s % 3600L / 60L) + "m";
      }
   }

   private static String trim(double v) {
      String s = v < 10.0 ? String.format(Locale.ROOT, "%.2f", v) : v < 100.0 ? String.format(Locale.ROOT, "%.1f", v) : String.format(Locale.ROOT, "%.0f", v);
      if (s.contains(".")) {
         s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
      }

      return s;
   }
}
