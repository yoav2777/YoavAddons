package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModPricerTest {
   private static final Map<String, Double> PRICES = Map.of(
      "ENCHANTMENT_SHARPNESS_5", 100.0,
      "RECOMBOBULATOR_3000", 5_000_000.0,
      "FIRST_MASTER_STAR", 10.0,
      "SECOND_MASTER_STAR", 20.0,
      "HOT_POTATO_BOOK", 1.0,
      "FUMING_POTATO_BOOK", 3.0,
      "PERFECT_SAPPHIRE_GEM", 7.0
   );
   private static final PriceBook BOOK = id -> PRICES.getOrDefault(id, Double.NaN);

   private static Modifier find(List<Modifier> mods, Modifier.Group g) {
      return mods.stream().filter(m -> m.group() == g).findFirst().orElseThrow();
   }

   @Test
   void enchantValueFallsBackToDoublingLowerLevels() {
      assertEquals(100.0, ModPricer.enchantValue("sharpness", 5, BOOK));
      assertTrue(Double.isNaN(ModPricer.enchantValue("sharpness", 7, BOOK)));   // 6+ is never two of the level below
      assertTrue(Double.isNaN(ModPricer.enchantValue("unknown", 3, BOOK)));
   }

   @Test
   void pricesEachModifierGroup() {
      Map<String, Integer> enchants = new LinkedHashMap<>();
      enchants.put("sharpness", 5);
      ItemMods item = new ItemMods(
         "HYPERION", "Hyperion", enchants, "withered", 7, true, 12,
         Map.of("SAPPHIRE_0", "PERFECT"), null, null, Map.of("art_of_war_count", 1), null, null, null
      );
      List<Modifier> mods = ModPricer.price(item, BOOK);

      assertEquals(100.0, find(mods, Modifier.Group.ENCHANT).value());
      assertEquals(30.0, find(mods, Modifier.Group.STARS).value());
      assertEquals(5_000_000.0, find(mods, Modifier.Group.RECOMB).value());
      assertEquals(10 * 1.0 + 2 * 3.0, find(mods, Modifier.Group.POTATO_BOOKS).value());
      assertEquals(7.0, find(mods, Modifier.Group.GEMS).value());
      assertTrue(Double.isNaN(find(mods, Modifier.Group.OTHER).value()), "art of war has no price in this book");
      assertEquals("The Art Of War x1", find(mods, Modifier.Group.OTHER).label());
   }

   @Test
   void aLevelTheBazaarDoesNotSellIsARareEnchantAlwaysMatched() {
      // Ender Slayer 7 isn't a Bazaar product (Atomsplits with it sold for 145M, with 6 for 83M); 6 costs 156k
      PriceBook book = id -> id.equals("ENCHANTMENT_ENDER_SLAYER_6") ? 156_000.0 : id.equals("ENCHANTMENT_ENDER_SLAYER_4") ? 20_000.0 : Double.NaN;
      Map<String, Integer> enchants = new LinkedHashMap<>();
      enchants.put("ender_slayer", 7);
      enchants.put("champion", 10);   // levels with use: no rare book
      enchants.put("smite", 7);       // a Bazaar product without orders right now
      ItemMods item = new ItemMods("ATOMSPLIT_KATANA", "Atomsplit Katana", enchants, null, 0, false, 0, Map.of(), null, null, Map.of(), null, null, null);
      List<Modifier> mods = ModPricer.price(item, book, id -> id.startsWith("ENCHANTMENT_SMITE_") || !id.endsWith("_7") && !Double.isNaN(book.price(id)));
      List<Modifier> rare = mods.stream().filter(m -> m.group() == Modifier.Group.MUST_MATCH).toList();
      assertEquals(1, rare.size());
      assertEquals("Ender Slayer 7", rare.get(0).label());
      List<Modifier> chosen = ModSelector.select(mods, 50e6, LinkSettings.DEFAULT);
      assertTrue(chosen.contains(rare.get(0)));
   }

   @Test
   void enderSlayer7IsLevel6PlusAnEndStoneIdol() {
      PriceBook book = id -> id.equals("ENCHANTMENT_ENDER_SLAYER_6") ? 156_000.0 : id.equals("ENDSTONE_IDOL") ? 53_000_000.0 : Double.NaN;
      assertEquals(53_156_000.0, ModPricer.enchantValue("ender_slayer", 7, book), 1);
   }

   @Test
   void hecatombAtAnyLevelIsAlwaysMatched() {
      // Wither Goggles: with Hecatomb 40M, without 27M; its level barely matters (the owner's valuations)
      ItemMods item = new ItemMods("WITHER_GOGGLES", "Wither Goggles", Map.of("hecatomb", 9), null, 5, true, 15, Map.of(), null, null, Map.of(), null, null, null);
      List<Modifier> chosen = ModSelector.select(ModPricer.price(item, BOOK), 5e6, LinkSettings.DEFAULT);
      assertTrue(chosen.stream().anyMatch(m -> m.group() == Modifier.Group.MUST_MATCH && m.filters().equals(List.of(Filter.enchant("hecatomb", 1)))));
   }

   @Test
   void selectKeepsIdentityTopEnchantsAndBigModifiers() {
      LinkSettings s = new LinkSettings(10.0, 1, 0.1);
      List<Modifier> all = List.of(
         new Modifier(Modifier.Group.IDENTITY, "Pet rarity EPIC", Double.NaN, List.of(Filter.rarity("EPIC"))),
         new Modifier(Modifier.Group.ENCHANT, "A", 50.0, List.of(Filter.enchant("a", 1))),
         new Modifier(Modifier.Group.ENCHANT, "B", 80.0, List.of(Filter.enchant("b", 1))),
         new Modifier(Modifier.Group.ENCHANT, "C", 5.0, List.of(Filter.enchant("c", 1))),
         new Modifier(Modifier.Group.RECOMB, "Recomb", 200.0, List.of(Filter.recombobulated())),
         new Modifier(Modifier.Group.POTATO_BOOKS, "HPB", 10.0, List.of(Filter.hotPotatoBooks(10)))
      );
      // total = base 665 + 50 + 80 + 5 + 200 + 10 = 1010, so non-enchant modifiers must be worth more than 101
      List<String> chosen = ModSelector.select(all, 665.0, s).stream().map(Modifier::label).toList();
      assertEquals(List.of("Pet rarity EPIC", "B", "Recomb"), chosen);
   }

   @Test
   void settingsAreClamped() {
      LinkSettings s = new LinkSettings(-5, 99, 2.0);
      assertEquals(0.0, s.minEnchantValue());
      assertEquals(20, s.maxEnchants());
      assertEquals(1.0, s.minShare());
   }
}
