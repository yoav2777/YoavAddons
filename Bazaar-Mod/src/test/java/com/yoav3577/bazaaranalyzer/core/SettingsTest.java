package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsTest {
   @Test
   void graphPrefsReadsTheOldFileAndRoundTrips() {
      GraphPrefs old = GraphPrefs.fromJson("{\"width\":500,\"height\":300,\"range\":\"week\"}");
      assertEquals(new GraphPrefs(500, 300, "week", true, GraphPrefs.Lines.BOTH, true, 94), old);

      GraphPrefs p = new GraphPrefs(640, 320, "year", false, GraphPrefs.Lines.SELL, false, 55);
      assertEquals(p, GraphPrefs.fromJson(p.toJson()));
      assertEquals(GraphPrefs.DEFAULT, GraphPrefs.fromJson(null));
      assertEquals(GraphPrefs.DEFAULT, GraphPrefs.fromJson("not json"));
      assertEquals(GraphPrefs.DEFAULT, GraphPrefs.fromJson("[1,2]"));
   }

   @Test
   void graphPrefsClampsBadValues() {
      GraphPrefs p = GraphPrefs.fromJson("{\"width\":10,\"height\":99999,\"range\":\"decade\",\"lines\":\"nope\",\"grid\":\"yes\",\"opacity\":3}");
      assertEquals(GraphPrefs.MIN_W, p.width());
      assertEquals(GraphPrefs.MAX_H, p.height());
      assertEquals("day", p.range());
      assertEquals(GraphPrefs.Lines.BOTH, p.lines());
      assertTrue(p.grid());
      assertEquals(GraphPrefs.MIN_OPACITY, p.opacity());
      assertEquals(0xFF123456, p.withOpacity(100).panelColor(0x80123456));
      assertEquals(0x80123456, p.withOpacity(50).panelColor(0xFF123456));
   }

   @Test
   void modPrefsDefaultsRoundTripAndValidation() {
      assertEquals(ModPrefs.DEFAULT, ModPrefs.fromJson(null));
      ModPrefs p = new ModPrefs(false, ModPrefs.Corner.BOTTOM_RIGHT, 12, 40, false, false, 47835, false, 48);
      assertEquals(p, ModPrefs.fromJson(p.toJson()));
      ModPrefs bad = ModPrefs.fromJson("{\"corner\":\"middle\",\"offsetX\":-5,\"offsetY\":9999,\"sitePort\":80}");
      assertEquals(ModPrefs.Corner.TOP_LEFT, bad.corner());
      assertEquals(0, bad.offsetX());
      assertEquals(ModPrefs.MAX_OFFSET, bad.offsetY());
      assertEquals(0, bad.sitePort());
      assertEquals(336, ModPrefs.fromJson("{\"sitePort\":47835}").fillHours());
      assertEquals(1, ModPrefs.DEFAULT.withFill(true, 0).fillHours());
      assertEquals(336, ModPrefs.fromJson("{\"fillHours\":9999}").fillHours()); // old default: past Hypixel's 14 days
      ModPrefs dbg = ModPrefs.DEFAULT.withPriceDebug(true).withCancelLink(false);
      assertTrue(dbg.priceDebug());
      assertEquals(dbg, ModPrefs.fromJson(dbg.toJson()));
      assertFalse(ModPrefs.fromJson("{\"fillHours\":5}").priceDebug());
   }

   @Test
   void buttonPositionForEachCorner() {
      ModPrefs p = ModPrefs.DEFAULT;
      assertArrayEquals(new int[]{6, 6}, p.withPosition(ModPrefs.Corner.TOP_LEFT, 6, 6).buttonXY(400, 300, 100, 16));
      assertArrayEquals(new int[]{294, 6}, p.withPosition(ModPrefs.Corner.TOP_RIGHT, 6, 6).buttonXY(400, 300, 100, 16));
      assertArrayEquals(new int[]{6, 278}, p.withPosition(ModPrefs.Corner.BOTTOM_LEFT, 6, 6).buttonXY(400, 300, 100, 16));
      assertArrayEquals(new int[]{294, 278}, p.withPosition(ModPrefs.Corner.BOTTOM_RIGHT, 6, 6).buttonXY(400, 300, 100, 16));
      // A big offset on a small screen still keeps the button on screen.
      assertArrayEquals(new int[]{0, 0}, p.withPosition(ModPrefs.Corner.BOTTOM_RIGHT, 200, 200).buttonXY(250, 150, 100, 16));
   }

   @Test
   void dropSnapsToTheNearestCorner() {
      ModPrefs p = ModPrefs.DEFAULT.placedAt(280, 270, 100, 16, 400, 300);
      assertEquals(ModPrefs.Corner.BOTTOM_RIGHT, p.corner());
      assertEquals(20, p.offsetX());
      assertEquals(14, p.offsetY());
      assertArrayEquals(new int[]{280, 270}, p.buttonXY(400, 300, 100, 16));

      ModPrefs q = ModPrefs.DEFAULT.placedAt(-50, 10, 100, 16, 400, 300);
      assertEquals(ModPrefs.Corner.TOP_LEFT, q.corner());
      assertEquals(0, q.offsetX());
      assertEquals(10, q.offsetY());
      assertTrue(q.tradeButton());
   }

   @Test
   void dropFarFromACornerLandsWhereItWasDropped() {
      // 1920x1080 at GUI scale 2 = 960x540; offsets over 200 used to be clamped, so the button jumped away from the drop point.
      ModPrefs p = ModPrefs.DEFAULT.placedAt(250, 10, 150, 16, 960, 540);
      assertEquals(ModPrefs.Corner.TOP_LEFT, p.corner());
      assertArrayEquals(new int[]{250, 10}, p.buttonXY(960, 540, 150, 16));
      assertArrayEquals(new int[]{240, 262}, ModPrefs.DEFAULT.placedAt(240, 262, 150, 16, 960, 540).buttonXY(960, 540, 150, 16));
      // It survives a save + load.
      assertArrayEquals(new int[]{250, 10}, ModPrefs.fromJson(p.toJson()).buttonXY(960, 540, 150, 16));
   }

   @Test
   void linkSettingsReadsTheOldFileAndRoundTrips() {
      LinkSettings old = LinkSettings.fromJson("{\"minEnchantValue\":5000000,\"maxEnchants\":2,\"minSharePercent\":25.0000}");
      assertEquals(5e6, old.minEnchantValue());
      assertEquals(2, old.maxEnchants());
      assertEquals(0.25, old.minShare(), 1e-9);
      assertTrue(old.petRarity());

      LinkSettings s = new LinkSettings(2e8, 5, 0.33, false);
      LinkSettings back = LinkSettings.fromJson(s.toJson());
      assertEquals(s.minEnchantValue(), back.minEnchantValue());
      assertEquals(s.maxEnchants(), back.maxEnchants());
      assertEquals(s.minShare(), back.minShare(), 1e-6);
      assertFalse(back.petRarity());
      assertEquals(LinkSettings.DEFAULT, LinkSettings.fromJson("{\"maxEnchants\":\"many\"}"));
   }

   @Test
   void enchantStepsAndSliderMath() {
      assertEquals(4, LinkSettings.nearestEnchantStep(1e7));
      assertEquals(4, LinkSettings.nearestEnchantStep(1.2e7));
      assertEquals(0, LinkSettings.nearestEnchantStep(0));
      assertEquals(LinkSettings.ENCHANT_STEPS.length - 1, LinkSettings.nearestEnchantStep(1e12));

      assertEquals(0.5, Cfg.toSlider(60, 20, 100));
      assertEquals(0.0, Cfg.toSlider(-5, 20, 100));
      assertEquals(1.0, Cfg.toSlider(500, 20, 100));
      assertEquals(60, Cfg.fromSlider(0.5, 20, 100, 5));
      assertEquals(85, Cfg.fromSlider(0.81, 20, 100, 5));
      assertEquals(100, Cfg.fromSlider(2.0, 20, 100, 5));
      assertEquals(20, Cfg.fromSlider(Double.NaN, 20, 100, 5));
      assertEquals(1000, Cfg.fromSlider(1.0, 300, 1000, 10));
      for (int v = 300; v <= 1000; v += 10) {
         assertEquals(v, Cfg.fromSlider(Cfg.toSlider(v, 300, 1000), 300, 1000, 10));
      }
   }
}
