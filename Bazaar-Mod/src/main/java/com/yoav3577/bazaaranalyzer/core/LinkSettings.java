package com.yoav3577.bazaaranalyzer.core;

import java.util.Locale;

/**
 * Which item modifiers become website filters, stored in config/bazaaranalyzer/link-settings.json
 * ({"minEnchantValue":10000000,"maxEnchants":3,"minSharePercent":10.0000,"petRarity":true}; petRarity is new in 0.6).
 */
public record LinkSettings(double minEnchantValue, int maxEnchants, double minShare, boolean petRarity) {
   public static final double MAX_ENCHANT_VALUE = 1.0E12;
   public static final LinkSettings DEFAULT = new LinkSettings(1.0E7, 3, 0.1, true);
   /** The values the settings slider offers for minEnchantValue (a hand-edited file may hold any other value). */
   public static final double[] ENCHANT_STEPS = {0, 1e6, 2e6, 5e6, 1e7, 2e7, 5e7, 1e8, 2e8, 5e8, 1e9, 2e9, 5e9};

   /** Index of the ENCHANT_STEPS entry closest to v (compared on a log scale). */
   public static int nearestEnchantStep(double v) {
      int best = 0;
      double bestD = Double.MAX_VALUE;
      for (int i = 0; i < ENCHANT_STEPS.length; i++) {
         double d = Math.abs(Math.log1p(ENCHANT_STEPS[i]) - Math.log1p(Math.max(0.0, v)));
         if (d < bestD) {
            best = i;
            bestD = d;
         }
      }

      return best;
   }

   public LinkSettings {
      minEnchantValue = Double.isNaN(minEnchantValue) ? 1.0E7 : Math.max(0.0, Math.min(MAX_ENCHANT_VALUE, minEnchantValue));
      maxEnchants = Math.max(0, Math.min(20, maxEnchants));
      minShare = Double.isNaN(minShare) ? 0.1 : Math.max(0.0, Math.min(1.0, minShare));
   }

   public LinkSettings(double minEnchantValue, int maxEnchants, double minShare) {
      this(minEnchantValue, maxEnchants, minShare, true);
   }

   public LinkSettings withMinEnchantValue(double v) {
      return new LinkSettings(v, maxEnchants, minShare, petRarity);
   }

   public LinkSettings withMaxEnchants(int v) {
      return new LinkSettings(minEnchantValue, v, minShare, petRarity);
   }

   public LinkSettings withMinShare(double v) {
      return new LinkSettings(minEnchantValue, maxEnchants, v, petRarity);
   }

   public LinkSettings withPetRarity(boolean v) {
      return new LinkSettings(minEnchantValue, maxEnchants, minShare, v);
   }

   public static LinkSettings fromJson(String json) {
      var o = Cfg.object(json);
      return new LinkSettings(
         Cfg.num(o, "minEnchantValue", DEFAULT.minEnchantValue),
         Cfg.integer(o, "maxEnchants", DEFAULT.maxEnchants),
         Cfg.num(o, "minSharePercent", DEFAULT.minShare * 100.0) / 100.0,
         Cfg.bool(o, "petRarity", DEFAULT.petRarity)
      );
   }

   public String toJson() {
      return String.format(
         Locale.ROOT,
         "{\"minEnchantValue\":%.0f,\"maxEnchants\":%d,\"minSharePercent\":%.4f,\"petRarity\":%b}%n",
         minEnchantValue, maxEnchants, minShare * 100.0, petRarity
      );
   }
}
