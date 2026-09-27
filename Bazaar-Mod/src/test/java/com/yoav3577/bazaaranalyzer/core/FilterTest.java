package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FilterTest {
   @Test
   void serialisesTheShapeTheSiteParses() {
      assertEquals("{\"kind\":\"enchant\",\"type\":\"sharpness\",\"min\":7,\"max\":null}", Filter.enchant("sharpness", 7).toJson());
      assertEquals("{\"kind\":\"stars\",\"min\":7,\"max\":null}", Filter.stars(7).toJson());
      assertEquals("{\"kind\":\"rarity\",\"value\":\"EPIC\"}", Filter.rarity("epic").toJson());
      assertEquals("{\"kind\":\"recomb\",\"value\":true}", Filter.recombobulated().toJson());
      assertEquals("{\"kind\":\"nbt\",\"key\":\"COMBAT_0\",\"mode\":\"value\",\"value\":\"PERFECT\"}", Filter.nbtValue("COMBAT_0", "PERFECT").toJson());
      assertEquals(
         "{\"kind\":\"nbt\",\"key\":\"art_of_war_count\",\"mode\":\"range\",\"min\":1,\"max\":null}",
         Filter.nbtAtLeast("art_of_war_count", 1).toJson()
      );
   }

   @Test
   void escapesStrings() {
      assertEquals("{\"kind\":\"nbt\",\"key\":\"a\\\"b\",\"mode\":\"value\",\"value\":\"x\\\\y\"}", Filter.nbtValue("a\"b", "x\\y").toJson());
   }
}
