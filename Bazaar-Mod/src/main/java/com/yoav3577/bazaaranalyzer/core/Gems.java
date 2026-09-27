package com.yoav3577.bazaaranalyzer.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

public final class Gems {
   private static final Set<String> QUALITIES = Set.of("ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT");
   private static final Set<String> TYPES = Set.of(
      "JASPER", "RUBY", "AMETHYST", "SAPPHIRE", "AMBER", "TOPAZ", "JADE", "ONYX", "OPAL", "CITRINE", "PERIDOT", "AQUAMARINE"
   );

   private Gems() {
   }

   /**
    * Name key for matching gem names: Hypixel puts an icon glyph (private-use char) and colour codes in front of gem
    * names ("<glyph> Fine Ruby Gemstone"), so only letters/digits/spaces count, lower-cased.
    */
   public static String nameKey(String name) {
      if (name == null) {
         return "";
      } else {
         String plain = name.replaceAll("\u00a7.", "");
         StringBuilder sb = new StringBuilder(plain.length());

         for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? Character.toLowerCase(c) : ' ');
         }

         return sb.toString().strip().replaceAll("\\s+", " ");
      }
   }

   /** The entries of {@code had} missing from {@code now} (counted per entry: a multiset difference). */
   public static <T> List<T> removed(List<T> had, List<T> now) {
      List<T> left = new ArrayList<>(now);
      List<T> out = new ArrayList<>();

      for (T g : had) {
         if (!left.remove(g)) {
            out.add(g);
         }
      }

      return out;
   }

   /** {@code had} minus {@code gone} (one per entry). */
   public static <T> List<T> minus(List<T> had, List<T> gone) {
      List<T> out = new ArrayList<>(had);

      for (T g : gone) {
         out.remove(g);
      }

      return out;
   }

   public static boolean isQuality(String s) {
      return s != null && QUALITIES.contains(s.toUpperCase(Locale.ROOT));
   }

   public static List<TradeItem.Gem> parse(Map<String, String> flat) {
      List<TradeItem.Gem> out = new ArrayList<>();

      for (Entry<String, String> e : new TreeMap<>(flat).entrySet()) {
         String key = e.getKey();
         if (!key.endsWith("_gem") && !key.equals("unlocked_slots")) {
            String quality = e.getValue() == null ? "" : e.getValue().toUpperCase(Locale.ROOT);
            if (QUALITIES.contains(quality)) {
               String type = flat.get(key + "_gem");
               if (type == null) {
                  int us = key.indexOf('_');
                  type = us > 0 ? key.substring(0, us) : null;
               }

               if (type != null) {
                  type = type.toUpperCase(Locale.ROOT);
                  if (TYPES.contains(type)) {
                     out.add(new TradeItem.Gem(type, quality));
                  }
               }
            }
         }
      }

      return out;
   }
}
