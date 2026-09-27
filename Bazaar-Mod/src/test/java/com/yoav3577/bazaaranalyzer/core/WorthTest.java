package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorthTest {
   private static final long DAY = 86400000L;
   private static final long NOW = 1_790_000_000_000L;

   @Test
   void normalPriceIsTheMedianDailyLowOfTheMonthWithoutTheLastTwoDays() {
      List<Worth.Day> days = new ArrayList<>();
      days.add(new Worth.Day(NOW - 40 * DAY, 1));
      for (int d = 29; d >= 3; d--) {
         days.add(new Worth.Day(NOW - d * DAY, d % 2 == 0 ? 600 : 650));
      }

      days.add(new Worth.Day(NOW - 2 * DAY + 1000, 5_000));
      days.add(new Worth.Day(NOW - DAY, 5_000));
      days.add(new Worth.Day(NOW, 5_000));
      // 27 days in the window: 14 at 650 (odd d) and 13 at 600.
      assertEquals(650.0, Worth.normalPrice(days, NOW));
      assertTrue(Double.isNaN(Worth.normalPrice(days.subList(0, 5), NOW)));
   }

   @Test
   void inflationWarning() {
      assertEquals(
         "⚠ Hyperion looks inflated: lowest BIN 30% above its 30-day normal (812M vs 624M)",
         Worth.inflation("Hyperion", 812_000_000, 624_000_000)
      );
      assertEquals("⚠ X looks inflated: lowest BIN 25% above its 30-day normal (125 vs 100)", Worth.inflation("X", 125, 100));
      assertNull(Worth.inflation("X", 124, 100));
      assertNull(Worth.inflation("X", 500, Double.NaN));
      assertNull(Worth.inflation("X", Double.NaN, 100));
   }

   @Test
   void craftBeatsTheWorth() {
      assertTrue(Worth.craftBeats(new double[]{480, 495}, 520));
      assertTrue(Worth.craftBeats(new double[]{Double.NaN, 500}, 520));
      assertFalse(Worth.craftBeats(new double[]{Double.NaN, 530}, 520));
      assertFalse(Worth.craftBeats(new double[]{480, 495}, Double.NaN));
      assertFalse(Worth.craftBeats(null, 520));
      assertFalse(Worth.craftBeats(new double[]{1}, 520));
      assertEquals(
         List.of("Hyperion:", "  Craft from scratch: 480M", "  Craft the easy way: -", "  Worth: 520M"),
         Worth.craftLines("Hyperion", new double[]{480e6, Double.NaN}, 520e6, List.of())
      );
      assertEquals(
         "  Not priced: A, B, C, D, ... (craft costs more)",
         Worth.craftLines("X", new double[]{1, 2}, 3, List.of("A", "B", "C", "D", "E")).get(4)
      );
   }

   @Test
   void textHelpers() {
      assertEquals("1.23B", Worth.coins(1.234e9));
      assertEquals("812M", Worth.coins(812e6));
      assertEquals("12.3M", Worth.coins(12.34e6));
      assertEquals("950k", Worth.coins(950e3));
      assertEquals("12.3k", Worth.coins(12_345));
      assertEquals("999", Worth.coins(999));
      assertEquals("-", Worth.coins(Double.NaN));
      assertEquals("Hyperion: 1.23B", Worth.line("Hyperion", 1.234e9, null));
      assertEquals("Hyperion: 505M (30-day history)", Worth.line("Hyperion", 505e6, "30-day history"));
      assertEquals("Hyperion: failed (HTTP 429)", Worth.failedLine("Hyperion", "HTTP 429"));
      assertTrue(Worth.isCoins("100M coins", List.of()));
      assertTrue(Worth.isCoins("Gold", List.of("Lump-sum amount")));
      assertFalse(Worth.isCoins("Hyperion", List.of("Some lore")));
   }
}
