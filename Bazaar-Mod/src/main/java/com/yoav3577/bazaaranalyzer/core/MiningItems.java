package com.yoav3577.bazaaranalyzer.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mining items that compact into each other (Mithril -> Enchanted Mithril -> Refined Mithril, Rough -> Flawed -> Fine
 * Gemstone...). Every form counts in "raw" units (one of the first form), so a sack gain of any form adds up into one
 * family, and the worth can be priced as any form: Normal (the raw item), Enchanted (the first compacted form) or Super
 * (the next one: enchanted blocks, Concentrated Stone, Fine gems, Refined ores). Ratios from the NEU item repo.
 */
public final class MiningItems {
   public enum Tier {
      NORMAL("Normal"),
      ENCHANTED("Enchanted"),
      SUPER("Super compacted");

      public final String label;

      Tier(String label) {
         this.label = label;
      }
   }

   /** One form of a family: its bazaar id, its name and how many raw items it is made of. */
   public record Form(String id, String name, long raw) {
   }

   /** Forms from raw up; forms past index 2 (Flawless gems) only count gains, they are never the priced form. */
   public record Family(String id, List<Form> forms) {
      /** The form a tier prices / shows (the highest the family has, up to the tier). */
      public Form form(Tier t) {
         return this.forms.get(Math.min(t.ordinal(), Math.min(2, this.forms.size() - 1)));
      }
   }

   /** An item id as a family form: which family and how many raw items one of it is. */
   public record Hit(Family family, long raw) {
   }

   public static final List<String> GEMS = List.of(
      "RUBY", "AMETHYST", "JADE", "SAPPHIRE", "AMBER", "TOPAZ", "JASPER", "OPAL", "ONYX", "AQUAMARINE", "CITRINE", "PERIDOT"
   );
   private static final List<Family> FAMILIES = new ArrayList<>();
   private static final Map<String, Hit> BY_ID = new HashMap<>();

   static {
      chain("MITHRIL_ORE", "Mithril", "ENCHANTED_MITHRIL", "Enchanted Mithril", 160, "REFINED_MITHRIL", "Refined Mithril", 160);
      chain("TITANIUM_ORE", "Titanium", "ENCHANTED_TITANIUM", "Enchanted Titanium", 160, "REFINED_TITANIUM", "Refined Titanium", 16);
      chain("HARD_STONE", "Hard Stone", "ENCHANTED_HARD_STONE", "Enchanted Hard Stone", 576, "CONCENTRATED_STONE", "Concentrated Stone", 576);
      chain("GLACITE", "Glacite", "ENCHANTED_GLACITE", "Enchanted Glacite", 160);
      chain("UMBER", "Umber", "ENCHANTED_UMBER", "Enchanted Umber", 160, "REFINED_UMBER", "Refined Umber", 160);
      chain("TUNGSTEN", "Tungsten", "ENCHANTED_TUNGSTEN", "Enchanted Tungsten", 160, "REFINED_TUNGSTEN", "Refined Tungsten", 160);
      chain("SULPHUR_ORE", "Sulphur", "ENCHANTED_SULPHUR", "Enchanted Sulphur", 160, "ENCHANTED_SULPHUR_CUBE", "Enchanted Sulphur Cube", 160);
      chain("COBBLESTONE", "Cobblestone", "ENCHANTED_COBBLESTONE", "Enchanted Cobblestone", 160);
      chain("COAL", "Coal", "ENCHANTED_COAL", "Enchanted Coal", 160, "ENCHANTED_COAL_BLOCK", "Enchanted Coal Block", 160);
      chain("IRON_INGOT", "Iron Ingot", "ENCHANTED_IRON", "Enchanted Iron", 160, "ENCHANTED_IRON_BLOCK", "Enchanted Iron Block", 160);
      chain("GOLD_INGOT", "Gold Ingot", "ENCHANTED_GOLD", "Enchanted Gold", 160, "ENCHANTED_GOLD_BLOCK", "Enchanted Gold Block", 160);
      chain("DIAMOND", "Diamond", "ENCHANTED_DIAMOND", "Enchanted Diamond", 160, "ENCHANTED_DIAMOND_BLOCK", "Enchanted Diamond Block", 160);
      chain("EMERALD", "Emerald", "ENCHANTED_EMERALD", "Enchanted Emerald", 160, "ENCHANTED_EMERALD_BLOCK", "Enchanted Emerald Block", 160);
      chain("INK_SACK:4", "Lapis Lazuli", "ENCHANTED_LAPIS_LAZULI", "Enchanted Lapis Lazuli", 160,
         "ENCHANTED_LAPIS_LAZULI_BLOCK", "Enchanted Lapis Block", 160);
      chain("REDSTONE", "Redstone", "ENCHANTED_REDSTONE", "Enchanted Redstone", 160, "ENCHANTED_REDSTONE_BLOCK", "Enchanted Redstone Block", 160);
      chain("QUARTZ", "Nether Quartz", "ENCHANTED_QUARTZ", "Enchanted Quartz", 160, "ENCHANTED_QUARTZ_BLOCK", "Enchanted Quartz Block", 160);
      chain("OBSIDIAN", "Obsidian", "ENCHANTED_OBSIDIAN", "Enchanted Obsidian", 160);
      chain("NETHERRACK", "Netherrack", "ENCHANTED_NETHERRACK", "Enchanted Netherrack", 160);
      chain("ENDER_STONE", "End Stone", "ENCHANTED_ENDSTONE", "Enchanted End Stone", 160);
      chain("GLOWSTONE_DUST", "Glowstone Dust", "ENCHANTED_GLOWSTONE_DUST", "Enchanted Glowstone Dust", 160,
         "ENCHANTED_GLOWSTONE", "Enchanted Glowstone", 160);
      chain("SAND", "Sand", "ENCHANTED_SAND", "Enchanted Sand", 160);
      chain("SAND:1", "Red Sand", "ENCHANTED_RED_SAND", "Enchanted Red Sand", 160, "ENCHANTED_RED_SAND_CUBE", "Enchanted Red Sand Cube", 160);
      chain("MYCEL", "Mycelium", "ENCHANTED_MYCELIUM", "Enchanted Mycelium", 160, "ENCHANTED_MYCELIUM_CUBE", "Enchanted Mycelium Cube", 160);
      chain("ICE", "Ice", "ENCHANTED_ICE", "Enchanted Ice", 160, "ENCHANTED_PACKED_ICE", "Enchanted Packed Ice", 160);
      chain("FLINT", "Flint", "ENCHANTED_FLINT", "Enchanted Flint", 160);
      for (String g : GEMS) {
         String n = g.charAt(0) + g.substring(1).toLowerCase(Locale.ROOT);
         chain("ROUGH_" + g + "_GEM", "Rough " + n + " Gemstone", "FLAWED_" + g + "_GEM", "Flawed " + n + " Gemstone", 80,
            "FINE_" + g + "_GEM", "Fine " + n + " Gemstone", 80, "FLAWLESS_" + g + "_GEM", "Flawless " + n + " Gemstone", 80);
      }
   }

   private MiningItems() {
   }

   /** raw id, raw name, then (id, name, how many of the form before) per compacted form. */
   private static void chain(Object... a) {
      List<Form> forms = new ArrayList<>();
      forms.add(new Form((String) a[0], (String) a[1], 1));
      long raw = 1;
      for (int i = 2; i + 2 < a.length; i += 3) {
         raw *= ((Number) a[i + 2]).longValue();
         forms.add(new Form((String) a[i], (String) a[i + 1], raw));
      }

      Family f = new Family((String) a[0], List.copyOf(forms));
      FAMILIES.add(f);
      for (Form form : f.forms) {
         BY_ID.put(form.id, new Hit(f, form.raw));
      }
   }

   public static List<Family> families() {
      return List.copyOf(FAMILIES);
   }

   /** The family form this bazaar id is, or null when it is not a compacting mining item. */
   public static Hit byId(String id) {
      return id == null ? null : BY_ID.get(id);
   }

   /** The family whose raw form is this id, or null. */
   public static Family family(String rawId) {
      Hit h = byId(rawId);
      return h != null && h.raw == 1 ? h.family : null;
   }
}
