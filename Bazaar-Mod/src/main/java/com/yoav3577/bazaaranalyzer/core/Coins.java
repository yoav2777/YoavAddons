package com.yoav3577.bazaaranalyzer.core;

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
}
