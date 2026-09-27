package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AhLinkTest {
   private static ItemMods book(Map<String, Integer> enchants) {
      return new ItemMods("ENCHANTED_BOOK", "Enchanted Book", enchants, null, 0, false, 0, null, null, null, null, null, null, null);
   }

   private static ItemMods pet(String type) {
      return new ItemMods("PET", "[Lvl 1] Ghoul", null, null, 0, false, 0, null, null, null, null, type, "EPIC", null);
   }

   @Test
   void tagsForSpecialItems() {
      assertEquals("PET_GHOUL", AhLink.tag(pet("ghoul")));
      assertNull(AhLink.tag(pet(null)));
      assertEquals("RUNE_ENDERSNAKE", AhLink.tag(new ItemMods("RUNE", "r", null, null, 0, false, 0, null, null, null, null, null, null, "ENDERSNAKE")));
      assertEquals("ENCHANTMENT_ULTIMATE_WISE_5", AhLink.tag(book(Map.of("ultimate_wise", 5))));
      assertNull(AhLink.tag(ItemMods.plain("POTION", "Potion")));
      assertEquals("HYPERION", AhLink.tag(ItemMods.plain("HYPERION", "Hyperion")));
      assertNull(AhLink.tag(ItemMods.plain(null, "Nothing")));
   }

   @Test
   void bazaarProductsAreNotAhItems() {
      Set<String> bazaar = Set.of("ENCHANTED_DIAMOND", "ENCHANTMENT_SHARPNESS_5");
      assertFalse(AhLink.isAhItem(ItemMods.plain("ENCHANTED_DIAMOND", "Enchanted Diamond"), bazaar::contains));
      assertTrue(AhLink.isAhItem(ItemMods.plain("HYPERION", "Hyperion"), bazaar::contains));
      assertFalse(AhLink.isAhItem(book(Map.of("sharpness", 5)), bazaar::contains));
      assertTrue(AhLink.isAhItem(book(Map.of("sharpness", 7)), bazaar::contains));
      assertTrue(AhLink.isAhItem(pet("GHOUL"), bazaar::contains));
      assertFalse(AhLink.isAhItem(ItemMods.plain(null, "x"), bazaar::contains));
   }

   @Test
   void urlEncodesTagAndFilters() {
      assertEquals("http://127.0.0.1:47831/#/ah/item/HYPERION", AhLink.url("http://127.0.0.1:47831", "HYPERION", List.of()));
      Modifier stars = new Modifier(Modifier.Group.STARS, "Master stars x2", 1.0, List.of(Filter.stars(7)));
      assertEquals(
         "http://127.0.0.1:47831/#/ah/item/HYPERION?f=%5B%7B%22kind%22%3A%22stars%22%2C%22min%22%3A7%2C%22max%22%3Anull%7D%5D",
         AhLink.url("http://127.0.0.1:47831/", "HYPERION", List.of(stars))
      );
   }
}
