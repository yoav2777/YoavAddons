package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LowballGemTest {
   private static final String UUID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
   private static final long T = 1_700_000_000_000L;
   private static final long MIN = 60_000L;
   private static final TradeItem.Gem FINE_RUBY = new TradeItem.Gem("RUBY", "FINE");
   private static final TradeItem.Gem PERFECT_SAPPHIRE = new TradeItem.Gem("SAPPHIRE", "PERFECT");
   /** How Hypixel names gems in chat: an icon glyph (private-use char) in front. */
   private static final String CHAT_RUBY = " Fine Ruby Gemstone";

   private static List<String> ids(TradeItem.Gem... gems) {
      return Parts.of(List.of(gems), List.of());
   }

   private static List<Parts.Part> parts(TradeItem.Gem... gems) {
      return java.util.Arrays.stream(gems).map(g -> new Parts.Part(Parts.gemId(g), g.bazaarName())).toList();
   }

   private static PlayerTrade buy(long ts, List<TradeItem.Gem> gems) {
      return new PlayerTrade(ts, "Seller", 10_000_000, 0, List.of(), List.of(new TradeItem("HYPERION", UUID, "Hyperion", 1, gems)));
   }

   private static Trade bz(long ts, Trade.Kind kind, String item, int qty, double coins) {
      return new Trade(ts, kind, item, qty, coins, coins / qty, null, "");
   }

   /** "Claimed N coins from selling Qx Item at U each!": coins after the 1.25% tax, unit = the offer's price. */
   private static Trade claim(long ts, String item, int qty, double unit) {
      return new Trade(ts, Trade.Kind.BZ_CLAIM_SOLD, item, qty, unit * qty * (1 - Lowball.BAZAAR_TAX), unit, null, "");
   }

   private static List<Lowball.Line> gemLines(Lowball.Result r) {
      return r.lines().stream().filter(l -> l.kind().equals("GEM")).toList();
   }

   @Test
   void gemNamesMatchWithTheIconGlyphAndColourCodes() {
      assertEquals("fine ruby gemstone", Gems.nameKey(CHAT_RUBY));
      assertEquals("fine ruby gemstone", Gems.nameKey("§9 Fine Ruby Gemstone"));
      assertEquals(Gems.nameKey(FINE_RUBY.bazaarName()), Gems.nameKey(CHAT_RUBY));
   }

   @Test
   void removedIsAMultisetDifference() {
      assertEquals(List.of(FINE_RUBY), Gems.removed(List.of(FINE_RUBY, FINE_RUBY, PERFECT_SAPPHIRE), List.of(PERFECT_SAPPHIRE, FINE_RUBY)));
      assertEquals(List.of(), Gems.removed(List.of(FINE_RUBY), List.of(FINE_RUBY, PERFECT_SAPPHIRE)));
   }

   @Test
   void itemsGemsParseFromTheNbtKeys() {
      List<TradeItem.Gem> g = Gems.parse(Map.of("COMBAT_0", "PERFECT", "COMBAT_0_gem", "SAPPHIRE", "RUBY_0", "FINE"));
      assertEquals(2, g.size());
      assertTrue(g.contains(FINE_RUBY) && g.contains(PERFECT_SAPPHIRE));
   }

   @Test
   void trackerLogsARemovalOnceAndStopsWhenAllGemsAreGone() {
      long now = T + MIN;
      GemTracker t = GemTracker.build(List.of(buy(T, List.of(FINE_RUBY, PERFECT_SAPPHIRE))), List.of(GemLog.start(T - MIN)), now);
      assertTrue(t.tracks(UUID.toUpperCase()));
      assertNull(t.observe(UUID, "Hyperion", ids(FINE_RUBY, PERFECT_SAPPHIRE), now, Parts::name));
      GemLog e = t.observe(UUID, "Hyperion", ids(PERFECT_SAPPHIRE), now + 1000, Parts::name);
      assertEquals(GemLog.Kind.REMOVED, e.kind());
      assertEquals(ids(FINE_RUBY), e.ids());
      // Putting your own gem in and taking it out again is not logged: only gems that came with the item count.
      assertNull(t.observe(UUID, "Hyperion", ids(PERFECT_SAPPHIRE, FINE_RUBY), now + 2000, Parts::name));
      assertNull(t.observe(UUID, "Hyperion", ids(PERFECT_SAPPHIRE), now + 3000, Parts::name));
      assertEquals(ids(PERFECT_SAPPHIRE), t.observe(UUID, "Hyperion", ids(), now + 4000, Parts::name).ids());
      assertTrue(t.isEmpty());
   }

   @Test
   void trackerRebuildsFromTheLog() {
      List<GemLog> log = new ArrayList<>();
      log.add(GemLog.start(T - MIN));
      log.add(new GemLog(T + MIN, GemLog.Kind.REMOVED, UUID, "Hyperion", parts(FINE_RUBY)));
      GemTracker t = GemTracker.build(List.of(buy(T, List.of(FINE_RUBY, PERFECT_SAPPHIRE))), log, T + 2 * MIN);
      assertNull(t.observe(UUID, "Hyperion", ids(PERFECT_SAPPHIRE), T + 3 * MIN, Parts::name));
      assertEquals(ids(PERFECT_SAPPHIRE), t.observe(UUID, "Hyperion", ids(), T + 4 * MIN, Parts::name).ids());
   }

   @Test
   void trackerTakesTheBaseFromTheInventoryWhenTheTradeWindowShowedNoGems() {
      GemTracker t = GemTracker.build(List.of(buy(T, List.of())), List.of(), T + MIN);
      assertEquals(GemLog.Kind.BASE, t.observe(UUID, "Hyperion", ids(FINE_RUBY), T + MIN, Parts::name).kind());
      assertEquals(GemLog.Kind.REMOVED, t.observe(UUID, "Hyperion", ids(), T + 2 * MIN, Parts::name).kind());
      // Too late for a base: gems seen later may be the player's own.
      assertTrue(GemTracker.build(List.of(buy(T, List.of())), List.of(), T + GemTracker.BASE_GRACE_MS + 1).isEmpty());
   }

   @Test
   void trackerForgetsItemsTradedAway() {
      PlayerTrade sold = new PlayerTrade(T + MIN, "Buyer", 0, 20_000_000, List.of(new TradeItem("HYPERION", UUID, "Hyperion", 1, List.of(FINE_RUBY))), List.of());
      assertTrue(GemTracker.build(List.of(buy(T, List.of(FINE_RUBY)), sold), List.of(), T + 2 * MIN).isEmpty());
   }

   @Test
   void removedGemSoldOnTheBazaarCountsInTheLowball() {
      long removedAt = T + 3 * 86_400_000L; // days after the trade: only the removal time matters
      List<GemLog> log = List.of(
         GemLog.start(T - MIN), new GemLog(removedAt, GemLog.Kind.REMOVED, UUID, "Hyperion", parts(FINE_RUBY))
      );
      List<Trade> events = List.of(
         bz(T + MIN, Trade.Kind.BZ_INSTA_SELL, CHAT_RUBY, 1, 9_999), // before the removal: the player's own gem
         bz(removedAt + 5 * MIN, Trade.Kind.BZ_INSTA_SELL, CHAT_RUBY, 3, 30_000)
      );
      Lowball.Result r = Lowball.analyze(List.of(buy(T, List.of(FINE_RUBY, PERFECT_SAPPHIRE))), List.of(), events, log, removedAt + 10 * MIN).get(0);
      List<Lowball.Line> gems = gemLines(r);
      assertEquals(1, gems.size());
      assertEquals(Lowball.Status.SOLD, gems.get(0).status());
      assertEquals("1x Fine Ruby Gemstone", gems.get(0).label());
      assertEquals(10_000, gems.get(0).net(), 0.01);
      assertEquals(0, gems.get(0).cost(), 0.01);
      assertEquals(10_000, gems.get(0).profit(), 0.01);
      assertEquals(10_000, r.gained(), 0.01);
   }

   @Test
   void sellOfferIsSoldWhenClaimedAndListedUntilThen() {
      long removedAt = T + MIN;
      List<GemLog> log = List.of(GemLog.start(T - MIN), new GemLog(removedAt, GemLog.Kind.REMOVED, UUID, "Hyperion", parts(FINE_RUBY)));
      PlayerTrade trade = buy(T, List.of(FINE_RUBY));
      List<Trade> setupOnly = List.of(bz(removedAt + MIN, Trade.Kind.BZ_SELL_OFFER_SETUP, CHAT_RUBY, 1, 12_000));
      Lowball.Line listed = gemLines(Lowball.analyze(List.of(trade), List.of(), setupOnly, log, removedAt + 2 * MIN).get(0)).get(0);
      assertEquals(Lowball.Status.LISTED, listed.status());
      assertEquals(12_000 * (1 - Lowball.BAZAAR_TAX), listed.net(), 0.01);

      List<Trade> claimed = List.of(setupOnly.get(0), claim(removedAt + 3 * 86_400_000L, CHAT_RUBY, 1, 12_000));
      Lowball.Line sold = gemLines(Lowball.analyze(List.of(trade), List.of(), claimed, log, removedAt + 4 * 86_400_000L).get(0)).get(0);
      assertEquals(Lowball.Status.SOLD, sold.status());
      assertEquals(11_850, sold.net(), 0.01);
   }

   @Test
   void aClaimOnlyCountsForTheOfferWithItsPrice() {
      long removedAt = T + MIN;
      List<GemLog> log = List.of(GemLog.start(T - MIN), new GemLog(removedAt, GemLog.Kind.REMOVED, UUID, "Hyperion", parts(FINE_RUBY)));
      List<Trade> events = List.of(
         bz(removedAt + MIN, Trade.Kind.BZ_SELL_OFFER_SETUP, CHAT_RUBY, 1, 12_000), // the removed gem
         bz(removedAt + 2 * MIN, Trade.Kind.BZ_SELL_OFFER_SETUP, CHAT_RUBY, 1, 15_000), // another ruby, other offer
         claim(removedAt + 3 * MIN, CHAT_RUBY, 1, 15_000) // only the second offer filled
      );
      Lowball.Line line = gemLines(Lowball.analyze(List.of(buy(T, List.of(FINE_RUBY))), List.of(), events, log, removedAt + 4 * MIN).get(0)).get(0);
      assertEquals(Lowball.Status.LISTED, line.status());
      assertEquals(12_000 * (1 - Lowball.BAZAAR_TAX), line.net(), 0.01);
   }

   @Test
   void removedButUnsoldGemShowsAsHeldForADayThenDrops() {
      long removedAt = T + MIN;
      List<GemLog> log = List.of(GemLog.start(T - MIN), new GemLog(removedAt, GemLog.Kind.REMOVED, UUID, "Hyperion", parts(FINE_RUBY)));
      PlayerTrade trade = buy(T, List.of(FINE_RUBY));
      Lowball.Line held = gemLines(Lowball.analyze(List.of(trade), List.of(), List.of(), log, removedAt + MIN).get(0)).get(0);
      assertEquals(Lowball.Status.HELD, held.status());
      assertTrue(gemLines(Lowball.analyze(List.of(trade), List.of(), List.of(), log, removedAt + Lowball.GEM_WINDOW_MS + 1).get(0)).isEmpty());
   }

   @Test
   void gemSalesWithoutARemovalDoNotCountOnceWatching() {
      List<GemLog> log = List.of(GemLog.start(T - MIN));
      List<Trade> events = List.of(bz(T + MIN, Trade.Kind.BZ_INSTA_SELL, CHAT_RUBY, 1, 10_000));
      Lowball.Result r = Lowball.analyze(List.of(buy(T, List.of(FINE_RUBY))), List.of(), events, log, T + 2 * MIN).get(0);
      assertTrue(gemLines(r).isEmpty());
   }

   @Test
   void tradesFromBeforeWatchingStillGuessFromTheTradeTime() {
      List<Trade> events = List.of(bz(T + MIN, Trade.Kind.BZ_INSTA_SELL, CHAT_RUBY, 1, 10_000));
      Lowball.Result r = Lowball.analyze(List.of(buy(T, List.of(FINE_RUBY))), List.of(), events, List.of(GemLog.start(T + 5 * MIN)), T + 10 * MIN).get(0);
      List<Lowball.Line> gems = gemLines(r);
      assertEquals(1, gems.size());
      assertEquals(Lowball.Status.SOLD, gems.get(0).status());
      assertEquals(10_000, gems.get(0).net(), 0.01);
   }

   @Test
   void newestLowballComesFirstInItsDay() {
      List<PlayerTrade> trades = List.of(buy(T, List.of()), buy(T + MIN, List.of()));
      List<Lowball.Day> days = Lowball.byDay(trades, Lowball.analyze(trades, List.of(), List.of(), List.of(), T + 2 * MIN));
      assertEquals(T + MIN, days.get(0).lowballs().get(0).trade().ts());
   }

   @Test
   void partsComeFromDrillPetAndSkinKeys() {
      assertEquals(
         List.of("AMBER_POLISHED_DRILL_ENGINE", "GOBLIN_OMELETTE_SPICY", "PET_ITEM_TIER_BOOST", "PET_SKIN_DRAGON_NEON_BLUE"),
         Parts.ids(Map.of("drill_part_engine", "amber_polished_drill_engine", "drill_part_upgrade_module", "goblin_omelette_spicy",
            "petInfo.heldItem", "PET_ITEM_TIER_BOOST", "petInfo.skin", "DRAGON_NEON_BLUE", "dye_item", "none"))
      );
      assertTrue(Parts.sameName("Spicy Goblin Omelette", Parts.name("GOBLIN_OMELETTE_SPICY")));
      assertTrue(Parts.sameName("Amber-polished Drill Engine", Parts.name("AMBER_POLISHED_DRILL_ENGINE")));
      assertTrue(Parts.sameName(CHAT_RUBY, Parts.name("FINE_RUBY_GEM")));
      assertTrue(Parts.isGem("FINE_RUBY_GEM") && !Parts.isGem("AMBER_POLISHED_DRILL_ENGINE"));
   }

   @Test
   void drillPartTakenOffAndSoldOnTheAhCountsWithoutCostShare() {
      String engine = "AMBER_POLISHED_DRILL_ENGINE";
      long removedAt = T + MIN;
      PlayerTrade trade = new PlayerTrade(
         T, "Seller", 10_000_000, 0, List.of(), List.of(new TradeItem("DIVAN_DRILL", UUID, "Divan's Drill", 1, List.of(), List.of(engine)))
      );
      GemTracker t = GemTracker.build(List.of(trade), List.of(GemLog.start(T - MIN)), removedAt);
      GemLog e = t.observe(UUID, "Divan's Drill", List.of(), removedAt, id -> "Amber-polished Drill Engine");
      assertEquals(List.of(engine), e.ids());
      List<Trade> events = List.of(
         new Trade(removedAt + MIN, Trade.Kind.AH_LISTED, "Amber-polished Drill Engine", 1, Double.NaN, Double.NaN, "BIN", ""),
         new Trade(removedAt + 60 * MIN, Trade.Kind.AH_COLLECTED, "Amber-polished Drill Engine", 1, 9_800_000, 9_800_000, "Buyer", "")
      );
      Lowball.Result r = Lowball.analyze(List.of(trade), List.of(), events, List.of(GemLog.start(T - MIN), e), removedAt + 2 * 60 * MIN).get(0);
      Lowball.Line part = r.lines().stream().filter(l -> l.kind().equals("PART")).findFirst().orElseThrow();
      assertEquals(Lowball.Status.SOLD, part.status());
      assertEquals("AH", part.via());
      assertTrue(part.net() > 9_000_000);
      assertEquals(0, part.cost(), 0.01);
      assertEquals(part.net(), part.profit(), 0.01);
   }

   @Test
   void oldGemsOnlyLogLinesStillParse() {
      GemLog e = GemLog.parse("{\"ts\":1,\"kind\":\"REMOVED\",\"uuid\":\"u\",\"name\":\"H\",\"gems\":[{\"type\":\"RUBY\",\"quality\":\"FINE\"}]}").orElseThrow();
      assertEquals(List.of(new Parts.Part("FINE_RUBY_GEM", "Fine Ruby Gemstone")), e.parts());
   }

   @Test
   void gemLogRoundTripsThroughJson() {
      GemLog e = new GemLog(T, GemLog.Kind.REMOVED, UUID, "Hyperion ✪", parts(FINE_RUBY, PERFECT_SAPPHIRE));
      assertEquals(e, GemLog.parse(e.toJson()).orElseThrow());
      assertEquals(T, GemLog.watchSince(List.of(e, GemLog.start(T), GemLog.start(T + 1))));
      assertTrue(GemLog.parse("not json").isEmpty());
   }
}
