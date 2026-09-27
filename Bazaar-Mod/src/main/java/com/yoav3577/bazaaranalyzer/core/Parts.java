package com.yoav3577.bazaaranalyzer.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Things that can be taken off an item and sold on their own, as market ids: gems (FINE_RUBY_GEM), drill parts,
 * pet held item + pet skin, item skin, dye, fishing rod parts. The lowball tracker follows them off bought items.
 */
public final class Parts {
   /** Flattened NBT keys (see ItemReader.parts) whose value is a removable item id. */
   public static final List<String> KEYS = List.of(
      "drill_part_engine", "drill_part_fuel_tank", "drill_part_upgrade_module", "petInfo.heldItem", "petInfo.skin", "skin", "dye_item", "hook", "line", "sinker"
   );

   private Parts() {
   }

   public record Part(String id, String name) {
   }

   /** Market ids of the non-gem parts in a flattened NBT map (keys from KEYS). */
   public static List<String> ids(Map<String, String> flat) {
      List<String> out = new ArrayList<>();

      for (String k : KEYS) {
         String v = flat.get(k);
         if (v != null && !v.isBlank() && !v.equalsIgnoreCase("none")) {
            String id = v.strip().toUpperCase(Locale.ROOT);
            out.add(k.equals("petInfo.skin") ? "PET_SKIN_" + id : id);
         }
      }

      return out;
   }

   public static String gemId(TradeItem.Gem g) {
      return g.quality() + "_" + g.type() + "_GEM";
   }

   /** Every removable thing on the item: its gems + its other parts. */
   public static List<String> of(List<TradeItem.Gem> gems, List<String> parts) {
      List<String> out = new ArrayList<>();

      for (TradeItem.Gem g : gems) {
         out.add(gemId(g));
      }

      out.addAll(parts);
      return out;
   }

   public static boolean isGem(String id) {
      String[] w = id.split("_");
      return w.length == 3 && w[2].equals("GEM") && Gems.isQuality(w[0]);
   }

   /** A readable name when the real one (from the item in the inventory) is not known. */
   public static String name(String id) {
      if (isGem(id)) {
         String[] w = id.split("_");
         return new TradeItem.Gem(w[1], w[0]).bazaarName();
      } else if (id.startsWith("PET_SKIN_")) {
         return ModPricer.pretty(id.substring(9)) + " Skin";
      } else {
         return ModPricer.pretty(id.startsWith("PET_ITEM_") ? id.substring(9) : id);
      }
   }

   /** Same item name, ignoring icon glyphs, colour codes, punctuation and word order ("Goblin Omelette Spicy" = "Spicy Goblin Omelette"). */
   public static boolean sameName(String a, String b) {
      return words(a).equals(words(b));
   }

   private static List<String> words(String name) {
      String key = Gems.nameKey(name);
      if (key.isEmpty()) {
         return List.of();
      } else {
         String[] w = key.split(" ");
         Arrays.sort(w);
         return List.of(w);
      }
   }
}
