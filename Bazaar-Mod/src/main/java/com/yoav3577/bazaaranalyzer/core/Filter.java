package com.yoav3577.bazaaranalyzer.core;

import java.util.Locale;

public record Filter(String kind, String type, String key, String mode, String value, Integer min) {
   public static Filter enchant(String type, int minLevel) {
      return new Filter("enchant", type, null, null, null, minLevel);
   }

   /** Exactly this level (ultimates: a Chimera 5 is no comparable sale for a Chimera 4). */
   public static Filter enchantExact(String type, int level) {
      return new Filter("enchant", type, null, "exact", null, level);
   }

   public static Filter stars(int min) {
      return new Filter("stars", null, null, null, null, min);
   }

   public static Filter rarity(String rarity) {
      return new Filter("rarity", null, null, null, rarity.toUpperCase(Locale.ROOT), null);
   }

   public static Filter recombobulated() {
      return new Filter("recomb", null, null, null, "true", null);
   }

   public static Filter hotPotatoBooks(int min) {
      return new Filter("hpc", null, null, null, null, min);
   }

   public static Filter nbtValue(String key, String value) {
      return new Filter("nbt", null, key, "value", value, null);
   }

   /** Pets from level lo to hi (Coflnet PetLevel=lo-hi); only used for valuing, not for website links. */
   public static Filter petLevel(int lo, int hi) {
      return new Filter("petlevel", null, null, "range", lo + "-" + hi, lo);
   }

   public static Filter nbtAtLeast(String key, int min) {
      return new Filter("nbt", null, key, "range", null, min);
   }

   public String toJson() {
      StringBuilder sb = new StringBuilder("{\"kind\":").append(Json.str(this.kind));
      if (this.type != null) {
         sb.append(",\"type\":").append(Json.str(this.type));
      }

      if (this.key != null) {
         sb.append(",\"key\":").append(Json.str(this.key));
      }

      if (this.mode != null && !"exact".equals(this.mode)) {
         sb.append(",\"mode\":").append(Json.str(this.mode));
      }

      if ("recomb".equals(this.kind)) {
         sb.append(",\"value\":true");
      } else if (this.value != null) {
         sb.append(",\"value\":").append(Json.str(this.value));
      }

      if (this.min != null) {
         sb.append(",\"min\":").append(this.min).append(",\"max\":").append("exact".equals(this.mode) ? this.min.toString() : "null");
      }

      return sb.append('}').toString();
   }
}
