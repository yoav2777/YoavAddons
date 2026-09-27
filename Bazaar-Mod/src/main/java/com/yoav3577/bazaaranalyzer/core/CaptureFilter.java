package com.yoav3577.bazaaranalyzer.core;

import java.util.List;

public final class CaptureFilter {
   private static final List<String> ANCHORS = List.of(
      "[Bazaar]",
      "[Auction]",
      "You purchased",
      "You collected",
      "You claimed",
      "You cancelled",
      "You canceled",
      "Auction started",
      "BIN Auction started",
      "Your auction",
      "Your Buy Order",
      "Your Sell Offer",
      "Buy Order",
      "Sell Offer",
      "Claimed ",
      "Cancelled ",
      "Bazaar",
      "Auction",
      "Trade",
      "Trading"
   );

   private CaptureFilter() {
   }

   public static boolean interesting(String plainLine) {
      if (plainLine == null) {
         return false;
      } else {
         String line = plainLine.strip();
         if (!line.isEmpty() && line.length() <= 1000) {
            for (String a : ANCHORS) {
               if (line.startsWith(a)) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      }
   }

   public static String oneLine(String s) {
      return s.replace("\r", "").replace("\n", "\\n").strip();
   }
}
