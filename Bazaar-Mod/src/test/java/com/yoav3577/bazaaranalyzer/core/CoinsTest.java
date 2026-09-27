package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoinsTest {
   @Test
   void parsesPlainAndGroupedNumbers() {
      assertEquals(1234.0, Coins.parse("1234"));
      assertEquals(1234567.5, Coins.parse("1,234,567.5 coins"));
   }

   @Test
   void parsesSuffixes() {
      assertEquals(10_000_000.0, Coins.parse("10m"));
      assertEquals(2_500.0, Coins.parse("2.5k"));
      assertEquals(1_000_000_000.0, Coins.parse("1B"));
      assertEquals(3_000_000.0, Coins.parse("Lump-sum amount: 3 M"));
   }

   @Test
   void returnsNaNWhenThereIsNoNumber() {
      assertTrue(Double.isNaN(Coins.parse(null)));
      assertTrue(Double.isNaN(Coins.parse("no coins here")));
   }
}
