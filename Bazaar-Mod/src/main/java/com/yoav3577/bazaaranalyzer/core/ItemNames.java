package com.yoav3577.bazaaranalyzer.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Item name <-> bazaar id, for the names in the sack messages. Starts with the mining families (MiningItems); the Hypixel
 * item list and the bazaar ids add the rest (they never replace a name already known).
 */
public final class ItemNames {
   private final Map<String, String> ids = new HashMap<>();
   private final Map<String, String> names = new HashMap<>();
   private final Map<String, String> colors = new HashMap<>();

   public static ItemNames base() {
      ItemNames n = new ItemNames();
      for (MiningItems.Family f : MiningItems.families()) {
         for (MiningItems.Form form : f.forms()) {
            n.add(form.id(), form.name());
         }
      }

      return n;
   }

   /** Adds a spelling; a name already known keeps its id, an id keeps the first name it got. */
   public void add(String id, String name) {
      if (id == null || name == null) {
         return;
      }

      String k = Gems.nameKey(name);
      if (!k.isEmpty()) {
         this.ids.putIfAbsent(k, id);
      }

      this.names.putIfAbsent(id, name.replaceAll("§.", "").strip());
   }

   /** A bazaar id named the way the bazaar id reads (ENCHANTED_MITHRIL -> Enchanted Mithril). */
   public void addId(String id) {
      this.add(id, pretty(id));
   }

   /** Remembers the item's rarity (the item list's "tier": COMMON, RARE...) for its colour. */
   public void tier(String id, String tier) {
      String c = tier == null ? null : switch (tier) {
         case "UNCOMMON" -> "§a";
         case "RARE" -> "§9";
         case "EPIC" -> "§5";
         case "LEGENDARY" -> "§6";
         case "MYTHIC" -> "§d";
         case "SPECIAL", "VERY_SPECIAL" -> "§c";
         default -> null;
      };
      if (c != null) {
         this.colors.put(id, c);
      }
   }

   /** The colour code of the item's rarity (white when not known). */
   public String color(String id) {
      return this.colors.getOrDefault(id, "§f");
   }

   public String id(String name) {
      return this.ids.get(Gems.nameKey(name));
   }

   /** The id's name, or the key itself when it is a name already (unknown items are kept by name). */
   public String name(String key) {
      String n = this.names.get(key);
      return n != null ? n : key.equals(key.toUpperCase(Locale.ROOT)) && key.contains("_") ? pretty(key) : key;
   }

   public static String pretty(String id) {
      StringBuilder sb = new StringBuilder();
      for (String w : id.replaceAll(":\\d+$", "").split("_")) {
         if (!w.isEmpty()) {
            if (sb.length() > 0) {
               sb.append(' ');
            }

            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
         }
      }

      return sb.toString();
   }
}
