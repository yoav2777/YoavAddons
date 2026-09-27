package com.yoav3577.bazaaranalyzer.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.yoav3577.bazaaranalyzer.core.craft.CraftEngine;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.Map.Entry;

public final class ModPricer {
   private static final String[] MASTER_STARS = new String[]{
      "FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"
   };
   private static final Map<String, String> COUNTED = new LinkedHashMap<>();
   /** Enchant levels up to this one are made by combining two of the level below. */
   static final int MAX_COMBINED = 5;
   /**
    * Enchants whose presence, not level, sets the price (the owner's valuations; Wither Goggles with Hecatomb 40M, without
    * 27M). Tested and left out: Giant Killer / Titan Killer presence (worse: most weapons have GK; TK narrowed a Gauntlet
    * to 2 sales and a Chimera 4 Claymore to Chimera 5 ones).
    */
   static final Set<String> PRESENCE = Set.of("hecatomb");
   /** Enchants that level up with use (or Silex): a high level is no rare book. */
   private static final Set<String> USE_LEVELED = Set.of("champion", "compact", "cultivating", "expertise", "hecatomb", "toxophilite", "efficiency");

   private ModPricer() {
   }

   public static List<Modifier> price(ItemMods item, PriceBook bazaar) {
      return price(item, bazaar, id -> !Double.isNaN(bazaar.price(id)));
   }

   /** listed = the id is a Bazaar product (with or without orders right now). */
   public static List<Modifier> price(ItemMods item, PriceBook bazaar, Predicate<String> listed) {
      List<Modifier> out = new ArrayList<>();
      if (item.petTier() != null) {
         out.add(new Modifier(Modifier.Group.IDENTITY, "Pet rarity " + item.petTier(), Double.NaN, List.of(Filter.rarity(item.petTier()))));
      }

      for (Entry<String, Integer> e : item.enchants().entrySet()) {
         double v = enchantValue(e.getKey(), e.getValue(), bazaar);
         boolean rare = Double.isNaN(v) && e.getValue() > MAX_COMBINED && !USE_LEVELED.contains(e.getKey())
            && !listed.test("ENCHANTMENT_" + e.getKey().toUpperCase(Locale.ROOT) + "_" + e.getValue())
            && listedBelow(e.getKey(), e.getValue(), listed);
         out.add(
            new Modifier(
               rare ? Modifier.Group.MUST_MATCH : Modifier.Group.ENCHANT,
               pretty(e.getKey()) + " " + e.getValue(),
               v,
               List.of(e.getKey().startsWith("ultimate_") ? Filter.enchantExact(e.getKey(), e.getValue()) : Filter.enchant(e.getKey(), e.getValue()))
            )
         );
         if (PRESENCE.contains(e.getKey())) {
            out.add(new Modifier(Modifier.Group.MUST_MATCH, pretty(e.getKey()) + " (any level)", Double.NaN, List.of(Filter.enchant(e.getKey(), 1))));
         }
      }

      if (item.stars() > 5) {
         int masters = Math.min(item.stars() - 5, MASTER_STARS.length);
         double[] prices = new double[masters];

         for (int i = 0; i < masters; i++) {
            prices[i] = bazaar.price(MASTER_STARS[i]);
         }

         out.add(new Modifier(Modifier.Group.STARS, "Master stars x" + masters, sum(prices), List.of(Filter.stars(item.stars()))));
      }

      if (item.recombobulated()) {
         out.add(new Modifier(Modifier.Group.RECOMB, "Recombobulated", bazaar.price("RECOMBOBULATOR_3000"), List.of(Filter.recombobulated())));
      }

      List<TradeItem.Gem> gems = item.gems();
      if (!gems.isEmpty()) {
         double[] prices = new double[gems.size()];

         for (int i = 0; i < prices.length; i++) {
            prices[i] = bazaar.price(gems.get(i).quality() + "_" + gems.get(i).type() + "_GEM");
         }

         List<Filter> slots = new ArrayList<>();

         for (Entry<String, String> e : new TreeMap<>(item.gemsFlat()).entrySet()) {
            if (!e.getKey().endsWith("_gem") && Gems.isQuality(e.getValue())) {
               slots.add(Filter.nbtValue(e.getKey(), e.getValue().toUpperCase(Locale.ROOT)));
            }
         }

         out.add(new Modifier(Modifier.Group.GEMS, "Gemstones x" + gems.size(), sum(prices), slots));
      }

      if (!item.scrolls().isEmpty()) {
         double[] prices = new double[item.scrolls().size()];

         for (int i = 0; i < prices.length; i++) {
            prices[i] = bazaar.price(item.scrolls().get(i));
         }

         out.add(
            new Modifier(
               Modifier.Group.SCROLLS,
               "Ability scrolls x" + prices.length,
               sum(prices),
               List.of(Filter.nbtValue("ability_scroll", String.join(" ", item.scrolls().stream().sorted().toList())))
            )
         );
      }

      if (item.powerScroll() != null) {
         out.add(
            new Modifier(
               Modifier.Group.POWER_SCROLL,
               pretty(item.powerScroll()),
               bazaar.price(item.powerScroll()),
               List.of(Filter.nbtValue("power_ability_scroll", item.powerScroll()))
            )
         );
      }

      if (item.hotPotatoBooks() > 0) {
         int normal = Math.min(item.hotPotatoBooks(), 10);
         int fuming = Math.max(0, item.hotPotatoBooks() - 10);
         double[] prices = new double[normal + fuming];

         for (int i = 0; i < normal; i++) {
            prices[i] = bazaar.price("HOT_POTATO_BOOK");
         }

         for (int i = 0; i < fuming; i++) {
            prices[normal + i] = bazaar.price("FUMING_POTATO_BOOK");
         }

         out.add(
            new Modifier(
               Modifier.Group.POTATO_BOOKS, "Potato books x" + item.hotPotatoBooks(), sum(prices), List.of(Filter.hotPotatoBooks(item.hotPotatoBooks()))
            )
         );
      }

      for (Entry<String, String> ex : COUNTED.entrySet()) {
         int n = item.counts().getOrDefault(ex.getKey(), 0);
         if (n > 0) {
            double unit = bazaar.price(ex.getValue());
            out.add(
               new Modifier(
                  Modifier.Group.OTHER,
                  pretty(ex.getValue()) + " x" + n,
                  Double.isNaN(unit) ? Double.NaN : unit * n,
                  List.of(Filter.nbtAtLeast(ex.getKey(), n))
               )
            );
         }
      }

      return out;
   }

   /** Some lower level of the enchant is a Bazaar product (a real enchant, not a typo or a use-leveled one). */
   private static boolean listedBelow(String name, int level, Predicate<String> listed) {
      for (int l = level - 1; l >= 1; l--) {
         if (listed.test("ENCHANTMENT_" + name.toUpperCase(Locale.ROOT) + "_" + l)) {
            return true;
         }
      }

      return false;
   }

   /**
    * A missing level up to MAX_COMBINED is two of the level below (anvil); above it (6+: special drops like Ender Slayer 7,
    * 60M+ vs 156k for level 6) doubling is meaningless, so it has no price.
    */
   static double enchantValue(String name, int level, PriceBook bazaar) {
      String base = "ENCHANTMENT_" + name.toUpperCase(Locale.ROOT) + "_";
      double exact = bazaar.price(base + level);
      String up = CraftEngine.UPGRADE_ITEM.get(name.toUpperCase(Locale.ROOT) + "_" + level);
      if (!Double.isNaN(exact)) {
         return exact;
      } else if (up != null && !Double.isNaN(bazaar.price(base + (level - 1)) + bazaar.price(up))) {
         return bazaar.price(base + (level - 1)) + bazaar.price(up);
      } else if (level > MAX_COMBINED) {
         return Double.NaN;
      } else {
         for (int l = level - 1; l >= Math.max(1, level - 4); l--) {
            double lower = bazaar.price(base + l);
            if (!Double.isNaN(lower)) {
               return lower * Math.pow(2.0, level - l);
            }
         }

         return Double.NaN;
      }
   }

   private static double sum(double[] prices) {
      double total = 0.0;
      boolean any = false;

      for (double p : prices) {
         if (!Double.isNaN(p)) {
            total += p;
            any = true;
         }
      }

      return any ? total : Double.NaN;
   }

   static String pretty(String snake) {
      StringBuilder sb = new StringBuilder();

      for (String w : snake.split("_")) {
         if (!w.isEmpty()) {
            if (sb.length() > 0) {
               sb.append(' ');
            }

            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
         }
      }

      return sb.toString();
   }

   static {
      COUNTED.put("art_of_war_count", "THE_ART_OF_WAR");
      COUNTED.put("wood_singularity_count", "WOOD_SINGULARITY");
      COUNTED.put("farming_for_dummies_count", "FARMING_FOR_DUMMIES");
      COUNTED.put("tuned_transmission", "TRANSMISSION_TUNER");
   }
}
