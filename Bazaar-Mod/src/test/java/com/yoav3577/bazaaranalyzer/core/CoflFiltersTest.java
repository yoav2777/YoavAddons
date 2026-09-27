package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoflFiltersTest {
   // A sold row as /auctions/tag/HYPERION/sold returns it (trimmed from notes/samples/coflnet_sold_HYPERION_first3.json).
   private static final JsonObject SOLD = JsonParser.parseString(
         """
         {"tag":"HYPERION","itemName":"Heroic Hyperion ✪✪✪✪✪","highestBidAmount":1148000000,"end":"2026-09-21T18:17:15",
          "bin":true,"count":1,"tier":"MYTHIC",
          "enchantments":[{"type":"ultimate_wise","level":5},{"type":"sharpness","level":6}],
          "flattenedNbt":{"rarity_upgrades":"1","hpc":"10","power_ability_scroll":"SAPPHIRE_POWER_SCROLL","upgrade_level":"5",
             "uid":"add2f62333ff","ability_scroll":"IMPLOSION_SCROLL SHADOW_WARP_SCROLL WITHER_SHIELD_SCROLL","JASPER_0":"FINE"}}
         """
      )
      .getAsJsonObject();

   private static JsonObject row(String json) {
      return JsonParser.parseString(json).getAsJsonObject();
   }

   @Test
   void paramsFollowTheSiteMapping() {
      List<Filter> filters = List.of(
         Filter.enchant("ultimate_wise", 5),
         Filter.enchant("sharpness", 6),
         Filter.enchant("giant_killer", 6),
         Filter.stars(7),
         Filter.rarity("mythic"),
         Filter.recombobulated(),
         Filter.hotPotatoBooks(15),
         Filter.nbtValue("ability_scroll", "WITHER_SHIELD_SCROLL IMPLOSION_SCROLL SHADOW_WARP_SCROLL"),
         Filter.nbtValue("power_ability_scroll", "SAPPHIRE_POWER_SCROLL"),
         Filter.nbtValue("JASPER_0", "fine"),
         Filter.nbtValue("COMBAT_0", "PERFECT"),
         Filter.nbtAtLeast("art_of_war_count", 1),
         Filter.nbtAtLeast("wood_singularity_count", 1),
         Filter.nbtAtLeast("farming_for_dummies_count", 5),
         Filter.nbtAtLeast("tuned_transmission", 2)
      );
      Map<String, String> want = new LinkedHashMap<>();
      want.put("Enchantment", "ultimate_wise");
      want.put("EnchantLvl", ">4");
      want.put("SecondEnchantment", "sharpness");
      want.put("SecondEnchantLvl", ">5");
      want.put("Stars", ">6");
      want.put("Rarity", "MYTHIC");
      want.put("Recombobulated", "true");
      want.put("HotPotatoCount", ">14");
      want.put("AbilityScroll", "IMPLOSION_SCROLL SHADOW_WARP_SCROLL WITHER_SHIELD_SCROLL");
      want.put("PowerAbilityScroll", "SAPPHIRE_POWER_SCROLL");
      want.put("jasper0Gem", "FINE");
      want.put("combat0Gem", "PERFECT");
      want.put("ArtOfTheWar", "yes");
      want.put("WoodSingularity", "yes");
      assertEquals(List.copyOf(want.entrySet()), List.copyOf(CoflFilters.params(filters).entrySet()));
   }

   @Test
   void queryIsFormEncoded() {
      assertEquals("", CoflFilters.query(List.of()));
      assertEquals("?Enchantment=ultimate_wise&EnchantLvl=%3E4&Stars=%3E6", CoflFilters.query(List.of(Filter.enchant("ultimate_wise", 5), Filter.stars(7))));
      assertEquals("?AbilityScroll=A_SCROLL+B_SCROLL", CoflFilters.query(List.of(Filter.nbtValue("ability_scroll", "B_SCROLL A_SCROLL"))));
   }

   @Test
   void ultimatesMatchTheExactLevelAndWidenWhenTooFewSell() {
      assertTrue(CoflFilters.matches(SOLD, List.of(Filter.enchantExact("ultimate_wise", 5))));
      assertFalse(CoflFilters.matches(SOLD, List.of(Filter.enchantExact("ultimate_wise", 4))));   // a 5 is no comp for a 4
      assertTrue(CoflFilters.matches(SOLD, CoflFilters.relaxed(List.of(Filter.enchantExact("ultimate_wise", 4)))));
      assertEquals("?Enchantment=ultimate_wise&EnchantLvl=4", CoflFilters.query(List.of(Filter.enchantExact("ultimate_wise", 4))));
      assertEquals("{\"kind\":\"enchant\",\"type\":\"ultimate_chimera\",\"min\":4,\"max\":4}", Filter.enchantExact("ultimate_chimera", 4).toJson());
   }

   @Test
   void matchesSoldRowLikeTheSite() {
      assertTrue(CoflFilters.matches(SOLD, List.of()));
      assertTrue(CoflFilters.matches(SOLD, List.of(Filter.enchant("ultimate_wise", 5), Filter.enchant("sharpness", 5))));
      assertFalse(CoflFilters.matches(SOLD, List.of(Filter.enchant("ultimate_wise", 6))));
      assertFalse(CoflFilters.matches(SOLD, List.of(Filter.enchant("giant_killer", 1))));
      assertTrue(CoflFilters.matches(SOLD, List.of(Filter.stars(5))));
      assertFalse(CoflFilters.matches(SOLD, List.of(Filter.stars(6))));
      assertTrue(CoflFilters.matches(SOLD, List.of(Filter.rarity("mythic"))));
      assertFalse(CoflFilters.matches(SOLD, List.of(Filter.rarity("LEGENDARY"))));
      assertTrue(CoflFilters.matches(SOLD, List.of(Filter.recombobulated())));
      assertTrue(CoflFilters.matches(SOLD, List.of(Filter.hotPotatoBooks(10))));
      assertFalse(CoflFilters.matches(SOLD, List.of(Filter.hotPotatoBooks(11))));
      assertTrue(CoflFilters.matches(SOLD, List.of(Filter.nbtValue("ability_scroll", "wither_shield_scroll implosion_scroll  shadow_warp_scroll"))));
      assertFalse(CoflFilters.matches(SOLD, List.of(Filter.nbtValue("ability_scroll", "IMPLOSION_SCROLL"))));
      assertTrue(CoflFilters.matches(SOLD, List.of(Filter.nbtValue("JASPER_0", "FINE"))));
      assertFalse(CoflFilters.matches(SOLD, List.of(Filter.nbtValue("JASPER_0", "PERFECT"))));
      assertFalse(CoflFilters.matches(SOLD, List.of(Filter.nbtAtLeast("art_of_war_count", 1))));
      assertFalse(CoflFilters.matches(SOLD, List.of(new Filter("reforge", null, null, null, "Heroic", null))));
   }

   @Test
   void activeBinRowsUseFlatNbt() {
      JsonObject recombed = row("{\"itemName\":\"Hyperion\",\"startingBid\":600000000,\"tier\":\"MYTHIC\",\"enchantments\":[],\"flatNbt\":{\"rarity_upgrades\":\"1\"}}");
      JsonObject clean = row("{\"itemName\":\"Hyperion\",\"startingBid\":504000000,\"tier\":\"LEGENDARY\",\"enchantments\":[],\"flatNbt\":{\"uid\":\"x\"}}");
      assertTrue(CoflFilters.matches(recombed, List.of(Filter.recombobulated())));
      assertFalse(CoflFilters.matches(clean, List.of(Filter.recombobulated())));
   }

   @Test
   void starsComeFromNbtOrTheName() {
      assertEquals(5, CoflFilters.stars(SOLD));
      assertEquals(6, CoflFilters.stars(row("{\"itemName\":\"Ancient Necron's Chestplate ✪✪✪✪✪➊\",\"flattenedNbt\":{}}")));
      assertEquals(8, CoflFilters.stars(row("{\"itemName\":\"Hyperion ✪✪✪✪✪➌\"}")));
      assertEquals(7, CoflFilters.stars(row("{\"itemName\":\"x\",\"flattenedNbt\":{\"upgrade_level\":\"0\",\"dungeon_item_level\":\"7\"}}")));
      assertEquals(0, CoflFilters.stars(row("{}")));
   }
}
