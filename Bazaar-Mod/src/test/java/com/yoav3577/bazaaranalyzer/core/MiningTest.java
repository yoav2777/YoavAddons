package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MiningTest {
   private static final Map<String, Double> PRICES = Map.of(
      "MITHRIL_ORE", 10.0, "ENCHANTED_MITHRIL", 1760.0, "REFINED_MITHRIL", 300000.0,
      "ROUGH_RUBY_GEM", 2.0, "FLAWED_RUBY_GEM", 240.0, "TREASURITE", 5000.0
   );

   private static double price(String id) {
      return PRICES.getOrDefault(id, Double.NaN);
   }

   @Test
   void parsesSackHover() {
      String hover = "§aAdded items:\n  §a+1,234 §fMithril §7(Mining Sack)\n  §a+5 §a❤ Flawed Ruby Gemstone §7(Gemstone Sack, Large Gemstone Sack)\n"
         + "\n§eThis message can be disabled in the settings.";
      Map<String, Long> c = SackMessage.changes(List.of(hover, "Removed items:\n  -160 Mithril (Mining Sack)"));
      assertEquals(1074L, c.get("Mithril"));
      assertEquals(5L, c.get("❤ Flawed Ruby Gemstone"));
      assertEquals(2, c.size());
      assertTrue(SackMessage.isSacks("§6[Sacks] §r§a+1,239 §r§eitems§r§e. §r§8(Last 30s.)"));
      assertFalse(SackMessage.isSacks("Sacks are cool"));
   }

   @Test
   void namesMatchWithGlyphsAndColours() {
      ItemNames n = ItemNames.base();
      assertEquals("FLAWED_RUBY_GEM", n.id("§a❤ Flawed Ruby Gemstone"));
      assertEquals("MITHRIL_ORE", n.id("Mithril"));
      n.addId("TREASURITE");
      assertEquals("TREASURITE", n.id("Treasurite"));
      assertEquals("Treasurite", n.name("TREASURITE"));
      n.tier("TREASURITE", "RARE");
      assertEquals("§9", n.color("TREASURITE"));
      assertEquals("§f", n.color("MITHRIL_ORE"));
      assertEquals("Mystery Thing", n.name("Mystery Thing"));
   }

   @Test
   void familiesAndRatios() {
      assertEquals(160L, MiningItems.byId("ENCHANTED_MITHRIL").raw());
      assertEquals(25600L, MiningItems.byId("REFINED_MITHRIL").raw());
      assertEquals(2560L, MiningItems.byId("REFINED_TITANIUM").raw());
      assertEquals(576L * 576L, MiningItems.byId("CONCENTRATED_STONE").raw());
      assertEquals(6400L, MiningItems.byId("FINE_RUBY_GEM").raw());
      MiningItems.Family ruby = MiningItems.family("ROUGH_RUBY_GEM");
      assertEquals("FINE_RUBY_GEM", ruby.form(MiningItems.Tier.SUPER).id());
      assertEquals("ENCHANTED_GLACITE", MiningItems.family("GLACITE").form(MiningItems.Tier.SUPER).id());
   }

   @Test
   void sessionSumsFamiliesAndPricesByTier() {
      MiningSession s = new MiningSession();
      s.add("MITHRIL_ORE", 320);
      s.add("ENCHANTED_MITHRIL", 1);
      s.add("FLAWED_RUBY_GEM", 2);
      s.add("TREASURITE", 3);
      s.add("Mystery Thing", 4);
      assertEquals(480L, s.totals().get("MITHRIL_ORE"));
      assertEquals(160L, s.totals().get("ROUGH_RUBY_GEM"));

      List<MiningSession.Line> normal = s.lines(MiningItems.Tier.NORMAL, MiningTest::price, k -> k);
      assertEquals(4800.0 + 320.0 + 15000.0, MiningSession.total(normal), 1e-6);

      List<MiningSession.Line> ench = s.lines(MiningItems.Tier.ENCHANTED, MiningTest::price, k -> k);
      MiningSession.Line mithril = ench.stream().filter(l -> l.key().equals("MITHRIL_ORE")).findFirst().orElseThrow();
      assertEquals("Enchanted Mithril", mithril.name());
      assertEquals("ENCHANTED_MITHRIL", mithril.id());
      assertEquals(3.0, mithril.count(), 1e-9);
      assertEquals(5280.0, mithril.value(), 1e-6);
      assertEquals("TREASURITE", ench.get(0).key());
      assertTrue(Double.isNaN(ench.get(ench.size() - 1).value()));

      // Fine ruby is not priced: falls back to Flawed.
      List<MiningSession.Line> sup = s.lines(MiningItems.Tier.SUPER, MiningTest::price, k -> k);
      MiningSession.Line r = sup.stream().filter(l -> l.key().equals("ROUGH_RUBY_GEM")).findFirst().orElseThrow();
      assertEquals("Fine Ruby Gemstone", r.name());
      assertEquals(480.0, r.value(), 1e-6);
   }

   @Test
   void removalsNeverGoBelowZero() {
      MiningSession s = new MiningSession();
      s.add("MITHRIL_ORE", 100);
      s.add("MITHRIL_ORE", -160);
      assertTrue(s.totals().isEmpty());
      s.add("MITHRIL_ORE", 160);
      s.add("MITHRIL_ORE", -160);
      s.add("ENCHANTED_MITHRIL", 1);
      assertEquals(160L, s.totals().get("MITHRIL_ORE"));
   }

   @Test
   void timerRunsOnlyWhileMining() {
      MiningSession s = new MiningSession();
      long t = 1_000_000L;
      s.tick(t, false, 60000);
      assertFalse(s.started());
      for (int i = 1; i <= 330; i++) {
         s.tick(t + i * 1000L, i <= 300, 60000);
      }

      assertEquals(329000L, s.activeMs()); // a 30 s break still going counts
      for (int i = 331; i <= 600; i++) {
         s.tick(t + i * 1000L, false, 60000);
      }

      assertEquals(299000L, s.activeMs()); // the break got too long: it does not count
      for (int i = 601; i <= 640; i++) {
         s.tick(t + i * 1000L, i > 610, 60000);
      }

      assertEquals(299000L + 30000L, s.activeMs()); // back to mining for 30 s
      assertFalse(s.paused(t + 650000L, 60000));
      assertTrue(s.paused(t + 701000L, 60000));
      assertEquals(60.0E6, MiningSession.perHour(10.0E6, 600000L), 1e-6);
      assertTrue(Double.isNaN(MiningSession.perHour(5, 5000L)));
   }

   @Test
   void prefsRoundTripAndClamp() {
      MiningPrefs p = MiningPrefs.DEFAULT.withTier(MiningItems.Tier.SUPER).withShow(MiningPrefs.Show.HOURLY).withBox(120, 80, 500).withHideSackChat(true);
      assertEquals(200, p.scale());
      assertEquals(p, MiningPrefs.fromJson(p.toJson()));
      assertEquals(MiningPrefs.DEFAULT, MiningPrefs.fromJson("garbage"));
      assertEquals(MiningItems.Tier.ENCHANTED, MiningPrefs.fromJson("{\"tier\":\"nope\"}").tier());
      int[] xy = p.place(200, 100, 100, 50);
      assertEquals(100, xy[0]);
      assertEquals(50, xy[1]);
   }

   @Test
   void formats() {
      assertEquals("950", Coins.shortFmt(950));
      assertEquals("12.3k", Coins.shortFmt(12345));
      assertEquals("1.25M", Coins.shortFmt(1250000));
      assertEquals("60M", Coins.shortFmt(60000000));
      assertEquals("1,234", Coins.count(1234));
      assertEquals("1.5", Coins.count(1.5));
      assertEquals("12m 5s", Coins.time(725000));
      assertEquals("1h 03m", Coins.time(3780000));
      assertEquals("Dwarven Mines", SackMessage.area(List.of("§b§lInfo", " §r§b§lArea: §r§7Dwarven Mines")));
      assertTrue(SackMessage.isMining("Crystal Hollows"));
      assertFalse(SackMessage.isMining("Hub"));
   }
}
