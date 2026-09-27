package com.yoav3577.bazaaranalyzer.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class TradeDedupe {
   static final long WINDOW_MS = 30000L;

   private TradeDedupe() {
   }

   public static List<PlayerTrade> merge(List<PlayerTrade> trades) {
      List<PlayerTrade> sorted = new ArrayList<>(trades);
      sorted.sort(Comparator.comparingLong(PlayerTrade::ts));
      List<PlayerTrade> out = new ArrayList<>();

      for (PlayerTrade t : sorted) {
         int same = -1;

         for (int i = out.size() - 1; i >= 0 && t.ts() - out.get(i).ts() <= WINDOW_MS; i--) {
            if (sameTrade(out.get(i), t)) {
               same = i;
               break;
            }
         }

         if (same >= 0) {
            out.set(same, t);
         } else {
            out.add(t);
         }
      }

      return out;
   }

   private static boolean sameTrade(PlayerTrade a, PlayerTrade b) {
      return samePartner(a.partner(), b.partner()) && (share(a.received(), b.received()) || share(a.given(), b.given()));
   }

   private static boolean samePartner(String a, String b) {
      if (a != null && b != null) {
         String x = a.toLowerCase(Locale.ROOT);
         String y = b.toLowerCase(Locale.ROOT);
         return x.startsWith(y) || y.startsWith(x);
      } else {
         return false;
      }
   }

   private static boolean share(List<TradeItem> a, List<TradeItem> b) {
      for (TradeItem x : a) {
         if (x.uuid() != null) {
            for (TradeItem y : b) {
               if (x.uuid().equals(y.uuid())) {
                  return true;
               }
            }
         }
      }

      return false;
   }
}
