package com.yoav3577.bazaaranalyzer.core;

public final class Json {
   private Json() {
   }

   public static String str(String s) {
      if (s == null) {
         return "null";
      } else {
         StringBuilder sb = new StringBuilder(s.length() + 2).append('"');

         for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
               case '\t':
                  sb.append("\\t");
                  break;
               case '\n':
                  sb.append("\\n");
                  break;
               case '\r':
                  sb.append("\\r");
                  break;
               case '"':
                  sb.append("\\\"");
                  break;
               case '\\':
                  sb.append("\\\\");
                  break;
               default:
                  if (c < ' ') {
                     sb.append(String.format("\\u%04x", (int) c));
                  } else {
                     sb.append(c);
                  }
            }
         }

         return sb.append('"').toString();
      }
   }

   public static String num(double v) {
      if (!Double.isNaN(v) && !Double.isInfinite(v)) {
         return v == Math.rint(v) && Math.abs(v) < 1.0E15 ? Long.toString((long)v) : Double.toString(v);
      } else {
         return "null";
      }
   }
}
