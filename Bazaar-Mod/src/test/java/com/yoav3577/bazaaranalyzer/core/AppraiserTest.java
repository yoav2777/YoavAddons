package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppraiserTest {
   private static final long H = 3600_000L;
   private static final long DAY = 24 * H;
   private static final long NOW = 1_790_000_000_000L;

   private static Appraiser.Item sale(double price, long agoMs, double mods) {
      return new Appraiser.Item(price, NOW - agoMs, mods, null, null, false, false, Map.of());
   }

   private static Appraiser.Item target(double mods) {
      return new Appraiser.Item(Double.NaN, 0, mods, null, null, false, false, Map.of());
   }

   @Test
   void recentSalesAreMovedToTheItemsModifierCost() {
      // clean items sell at 500M; this one has 100M of modifiers -> 500M + 0.7 * 100M
      List<Appraiser.Item> sold = List.of(sale(500e6, H, 0), sale(500e6, 2 * H, 0), sale(500e6, 3 * H, 0));
      Appraiser.Result r = Appraiser.estimate(target(100e6), sold, List.of(), null, List.of(), 480e6, NOW);
      assertEquals(Appraiser.Source.RECENT, r.source());
      assertEquals(570e6, r.value(), 1);
      assertEquals(3, r.comps());
   }

   @Test
   void newestAndClosestSalesWeighMost() {
      // an old sale at 900M and three fresh ones near 600M: the fresh ones win
      List<Appraiser.Item> sold = List.of(sale(900e6, 5 * DAY, 0), sale(600e6, H, 0), sale(610e6, 2 * H, 0), sale(590e6, 3 * H, 0));
      assertEquals(600e6, Appraiser.estimate(target(0), sold, List.of(), null, List.of(), 500e6, NOW).value(), 1);
   }

   @Test
   void cappedAtOneAndAHalfTimesTheCleanAnchor() {
      List<Appraiser.Item> sold = List.of(sale(1.8e9, H, 0), sale(1.8e9, H, 0), sale(1.8e9, H, 0));
      Appraiser.Result r = Appraiser.estimate(target(0), sold, List.of(), null, List.of(), 1e9, NOW);
      assertEquals(1.5e9, r.value(), 1);
   }

   @Test
   void aLowestBinFarUnderTheSalesIsAMispricedListingNotAnAnchor() {
      // Aspect of the Dragon: lowest BIN list said 100k while clean ones sold for 1.6M
      List<Appraiser.Item> sold = List.of(sale(1.6e6, H, 0), sale(1.6e6, H, 0), sale(1.6e6, H, 0));
      Appraiser.Result r = Appraiser.estimate(target(0), sold, List.of(), null, List.of(), 1e5, NOW);
      assertEquals(1.6e6, r.value(), 1);
      assertTrue(Double.isNaN(r.anchor()));
   }

   @Test
   void fewSalesFallBackToTheMonthThenTheQuarterNeverAboveTheAnchor() {
      List<Appraiser.Day> month = List.of(new Appraiser.Day(NOW - 2 * DAY, 400e6, 600e6, 2), new Appraiser.Day(NOW - DAY, 400e6, 600e6, 2));
      Appraiser.Result r = Appraiser.estimate(target(0), List.of(), month, null, List.of(), 900e6, NOW);
      assertEquals(Appraiser.Source.MONTH, r.source());
      assertEquals(4, r.month());
      assertEquals(500e6, r.value(), 1);   // (min + avg) / 2

      List<Appraiser.Day> thin = List.of(new Appraiser.Day(NOW - DAY, 900e6, 1100e6, 1));
      List<Appraiser.Day> quarter = List.of(new Appraiser.Day(NOW - 60 * DAY, 800e6, 1000e6, 5), thin.get(0));
      r = Appraiser.estimate(target(0), List.of(), thin, quarter, List.of(), 700e6, NOW);
      assertEquals(Appraiser.Source.QUARTER, r.source());
      assertEquals(6, r.quarter());
      assertEquals(700e6, r.value(), 1);   // 1B from the history, but the anchor (clean BIN, no mods) is 700M
   }

   @Test
   void oneOrTwoSalesBeatTheHistoryButNeverGoAboveTheAnchor() {
      List<Appraiser.Day> month = List.of(new Appraiser.Day(NOW - DAY, 400e6, 600e6, 5));
      Appraiser.Result r = Appraiser.estimate(target(0), List.of(sale(800e6, H, 0)), month, null, List.of(), 900e6, NOW);
      assertEquals(Appraiser.Source.FEW, r.source());
      assertEquals(800e6, r.value(), 1);
      r = Appraiser.estimate(target(0), List.of(sale(1e9, H, 0), sale(1.1e9, 2 * H, 0)), month, null, List.of(), 900e6, NOW);
      assertEquals(900e6, r.value(), 1);   // the anchor: clean BIN, no mods
   }

   @Test
   void auctionSalesOnlyCountWhenBinSalesAreTooFew() {
      Appraiser.Item bid = new Appraiser.Item(1e6, NOW - H / 2, 0, null, null, false, false, Map.of(), true);   // a 1M bid, newest
      List<Appraiser.Item> sold = List.of(sale(100e6, H, 0), sale(100e6, H, 0), sale(100e6, H, 0), bid, bid, bid, bid);
      assertEquals(100e6, Appraiser.estimate(target(0), sold, List.of(), null, List.of(), Double.NaN, NOW).value(), 1);
      assertEquals(1e6, Appraiser.estimate(target(0), List.of(bid, bid, bid), List.of(), null, List.of(), Double.NaN, NOW).value(), 1);
   }

   @Test
   void noSalesAtAllUsesTheAnchor() {
      Appraiser.Result r = Appraiser.estimate(target(10e6), List.of(), List.of(), List.of(), List.of(), 100e6, NOW);
      assertEquals(Appraiser.Source.ANCHOR, r.source());
      assertEquals(109e6, r.value(), 1);   // clean + 90% of the mods
      assertEquals(Appraiser.Source.NONE, Appraiser.estimate(target(0), List.of(), List.of(), List.of(), List.of(), Double.NaN, NOW).source());
      // no clean price either (pets): the cheapest listing meeting the filters, moved to this item's modifier cost
      Appraiser.Item l1 = new Appraiser.Item(300e6, NOW, 20e6, null, null, false, false, Map.of());
      Appraiser.Item l2 = new Appraiser.Item(290e6, NOW, 0, null, null, false, false, Map.of());
      r = Appraiser.estimate(target(0), List.of(), List.of(), List.of(), List.of(l1, l2), Double.NaN, NOW);
      assertEquals(Appraiser.Source.LISTING, r.source());
      assertEquals(286e6, r.value(), 1);
   }

   @Test
   void aCloseCheaperListingCapsTheValueAFarOneDoesNot() {
      List<Appraiser.Item> sold = List.of(sale(600e6, H, 0), sale(600e6, H, 0), sale(600e6, H, 0));
      Appraiser.Item same = new Appraiser.Item(550e6, NOW, 1e6, null, null, false, false, Map.of());
      Appraiser.Item bare = new Appraiser.Item(400e6, NOW, -200e6, null, null, false, false, Map.of());
      Appraiser.Result r = Appraiser.estimate(target(0), sold, List.of(), null, List.of(same, bare), 500e6, NOW);
      assertTrue(r.capped());
      assertEquals(550e6 - 0.7e6, r.value(), 1);
      assertEquals(400e6, r.lowestBin(), 1);
      assertFalse(Appraiser.estimate(target(0), sold, List.of(), null, List.of(bare), 500e6, NOW).capped());
   }

   @Test
   void petsOnlyCompareWithTheSameTierAndTierBoostAndShinyWithShiny() {
      Appraiser.Item legendary = new Appraiser.Item(Double.NaN, 0, 0, 100, "LEGENDARY", false, false, Map.of());
      List<Appraiser.Item> sold = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
         sold.add(new Appraiser.Item(460e6, NOW - H, 0, 100, "LEGENDARY", false, false, Map.of()));
         sold.add(new Appraiser.Item(355e6, NOW - H, 0, 100, "EPIC", true, false, Map.of()));
      }

      Appraiser.Result r = Appraiser.estimate(legendary, sold, List.of(), null, List.of(), Double.NaN, NOW);
      assertEquals(3, r.comps());
      assertEquals(460e6, r.value(), 1);
      Appraiser.Item boosted = new Appraiser.Item(Double.NaN, 0, 0, 100, "EPIC", true, false, Map.of());
      assertEquals(355e6, Appraiser.estimate(boosted, sold, List.of(), null, List.of(), Double.NaN, NOW).value(), 1);
   }

   @Test
   void statTrackersPickSimilarItems() {
      // Midas: the winning bid decides the price
      List<Appraiser.Item> sold = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
         sold.add(new Appraiser.Item(12e6, NOW - H, 0, null, null, false, false, Map.of("winning_bid", 1e6)));
         sold.add(new Appraiser.Item(50e6, NOW - H, 0, null, null, false, false, Map.of("winning_bid", 50e6)));
      }

      // 38M more for 49M more bid: every sale moves by that slope to 48M bid
      double slope = 38e6 / 49e6;
      Appraiser.Item rich = new Appraiser.Item(Double.NaN, 0, 0, null, null, false, false, Map.of("winning_bid", 48e6));
      assertEquals(slope, Appraiser.trackerSlope(sold, "winning_bid"), 1e-9);
      assertEquals(50e6 - 2e6 * slope, Appraiser.estimate(rich, sold, List.of(), null, List.of(), Double.NaN, NOW).value(), 1);
      // the clean lowest BIN knows nothing of the bid: no 1.5 x anchor cap (would be 15M) for tracked items
      assertEquals(50e6 - 2e6 * slope, Appraiser.estimate(rich, sold, List.of(), null, List.of(), 10e6, NOW).value(), 1);
   }

   @Test
   void readsCoflnetRowsAndTheItemsOwnNbt() {
      JsonObject row = JsonParser.parseString("""
         {"tag":"PET_ENDER_DRAGON","itemName":"[Lvl 100] Ender Dragon","highestBidAmount":710000000,"count":1,"end":"2026-09-25T23:05:17",
          "enchantments":[{"type":"sharpness","level":6}],
          "flattenedNbt":{"type":"ENDER_DRAGON","tier":"EPIC","heldItem":"PET_ITEM_TIER_BOOST","exp":"62074041.3"}}
         """).getAsJsonObject();
      Appraiser.Item it = Appraiser.ofRow(row, 5e6, true);
      assertEquals(710e6, it.price(), 1);
      assertEquals(100, it.petLevel());
      assertEquals("EPIC", it.petTier());
      assertTrue(it.tierBoost());
      assertEquals(Appraiser.time("2026-09-25T23:05:17"), it.ts());
      assertEquals(6, Appraiser.rowNbt(row).getAsJsonObject("enchantments").get("sharpness").getAsInt());

      JsonObject nbt = JsonParser.parseString("{\"id\":\"PET\",\"petInfo\":\"{\\\"type\\\":\\\"ENDER_DRAGON\\\",\\\"tier\\\":\\\"LEGENDARY\\\"}\"}").getAsJsonObject();
      Appraiser.Item t = Appraiser.target("[Lvl 87] Ender Dragon", nbt, 0);
      assertEquals(87, t.petLevel());
      assertEquals("LEGENDARY", t.petTier());
      assertFalse(t.tierBoost());
      assertNull(Appraiser.target("Hyperion", new JsonObject(), 0).petTier());
   }

   @Test
   void historyDaysAndTheQuarterWindow() {
      JsonArray rows = JsonParser.parseString("""
         [{"min":470,"max":2e9,"avg":1e9,"volume":95,"time":"2026-09-25T00:00:00"},{"min":5e8,"max":6e8,"avg":5.5e8,"volume":0,"time":"2026-09-24T00:00:00"}]
         """).getAsJsonArray();
      List<Appraiser.Day> days = Appraiser.days(rows);
      assertEquals(1, days.size());   // no-sale day dropped
      long now = Appraiser.time("2026-09-25T12:00:00");
      assertEquals(95, Appraiser.volume(days, now - 30 * DAY));
      assertEquals(750e6, Appraiser.history(days, now - 30 * DAY, now), 1);   // troll 470 min floored at avg / 2
      List<Appraiser.Day> full = List.of(new Appraiser.Day(now - 80 * DAY, 1, 2, 3), new Appraiser.Day(now - 200 * DAY, 1, 2, 3));
      assertEquals(2, Appraiser.quarter(days, full, now).size());
   }

   @Test
   void petLevelFilter() {
      assertEquals("98-102", Appraiser.levelBand(100).value());
      assertEquals("43-57", Appraiser.levelBand(50).value());
      assertEquals("1-5", Appraiser.levelBand(2).value());
      assertEquals("?PetLevel=98-102", CoflFilters.query(List.of(Appraiser.levelBand(100))));
      JsonObject row = new JsonObject();
      row.addProperty("itemName", "[Lvl 99] Golden Dragon");
      assertTrue(CoflFilters.matches(row, List.of(Appraiser.levelBand(100))));
      assertFalse(CoflFilters.matches(row, List.of(Appraiser.levelBand(50))));
   }
}
