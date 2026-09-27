package com.yoav3577.bazaaranalyzer.core;

public final class AhFees {
   private AhFees() {
   }

   public static double listingRate(double price) {
      return price >= 1.0E8 ? 0.025 : (price >= 1.0E7 ? 0.02 : 0.01);
   }

   public static double listingFee(double price) {
      return price * listingRate(price);
   }

   /**
    * Hypixel's auction duration fee (paid on top of the listing fee): 20 coins for 1 h ... 55,200 for 14 days,
    * 200 x hours - 12,000 from 84 h on. Unknown duration (startMs 0) = 0.
    * ponytail: straight lines between the wiki's preset points (1/6/12/24/48 h); re-check if Hypixel changes the fees.
    */
   public static double durationFee(long startMs, long endMs) {
      if (startMs <= 0L || endMs <= startMs) {
         return 0.0;
      }

      double h = Math.min(336.0, Math.max(1.0, Math.round((endMs - startMs) / 3600000.0)));
      if (h >= 84.0) {
         return 200.0 * h - 12000.0;
      }

      double[][] pts = {{1, 20}, {6, 45}, {12, 100}, {24, 350}, {48, 1200}, {84, 4800}};
      int i = 1;
      while (h > pts[i][0]) {
         i++;
      }

      return pts[i - 1][1] + (h - pts[i - 1][0]) * (pts[i][1] - pts[i - 1][1]) / (pts[i][0] - pts[i - 1][0]);
   }

   public static double claimFee(double price) {
      return price > 1000000.0 ? Math.min(price * 0.01, price - 1000000.0) : 0.0;
   }

   public static double priceFromCollected(double collected) {
      return collected > 1000000.0 ? collected / 0.99 : collected;
   }
}
