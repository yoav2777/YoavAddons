package com.yoav3577.bazaaranalyzer.core;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.function.Predicate;

public final class AhLink {
   private AhLink() {
   }

   private static String bookProduct(ItemMods item) {
      if (item.enchants().size() != 1) {
         return null;
      } else {
         Entry<String, Integer> e = item.enchants().entrySet().iterator().next();
         return "ENCHANTMENT_" + e.getKey().toUpperCase(Locale.ROOT) + "_" + e.getValue();
      }
   }

   public static boolean isAhItem(ItemMods item, Predicate<String> isBazaarProduct) {
      String id = item.id();
      if (id == null) {
         return false;
      } else {
         return switch (id) {
            case "PET", "RUNE" -> true;
            case "ENCHANTED_BOOK" -> {
               String p = bookProduct(item);
               yield p == null || !isBazaarProduct.test(p);
            }
            default -> !isBazaarProduct.test(id);
         };
      }
   }

   public static String tag(ItemMods item) {
      String id = item.id();
      if (id == null) {
         return null;
      } else {
         return switch (id) {
            case "PET" -> item.petType() == null ? null : "PET_" + item.petType().toUpperCase(Locale.ROOT);
            case "RUNE" -> item.rune() == null ? null : "RUNE_" + item.rune().toUpperCase(Locale.ROOT);
            case "ENCHANTED_BOOK" -> bookProduct(item);
            case "POTION", "ATTRIBUTE_SHARD" -> null;
            default -> id;
         };
      }
   }

   public static String url(String siteBase, String tag, List<Modifier> chosen) {
      StringBuilder filters = new StringBuilder("[");
      boolean any = false;

      for (Modifier m : chosen) {
         for (Filter f : m.filters()) {
            if (any) {
               filters.append(',');
            }

            filters.append(f.toJson());
            any = true;
         }
      }

      filters.append(']');
      String base = siteBase.endsWith("/") ? siteBase : siteBase + "/";
      String url = base + "#/ah/item/" + encode(tag);
      return any ? url + "?f=" + encode(filters.toString()) : url;
   }

   static String encode(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
   }
}
