package com.yoav3577.bazaaranalyzer.core.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Port of craft/tests/core.test.mjs (+ the core parts of fixes-r1.test.mjs). */
class CraftCoreTest {
   private static final CraftCtx CTX = Fx.ctx();

   // the reference's method for trades / Kat upgrades is 'other'; the engine shows them as craft
   private static String m(JsonElement e) {
      return e.getAsString().equals("other") ? "craft" : e.getAsString();
   }

   private static CraftCore.Px price(String id, boolean easy, CraftCtx ctx) {
      return CraftCore.priceItem(id, easy, ctx);
   }

   @Test
   void nextCtxReusesParsedRecipesAndItemsFromTheSameStrings() {
      String items = Fx.read("hypixel-items.json");
      String recipes = Fx.read("recipes.json");
      CraftCtx first = CraftCtx.of(null, null, items, recipes);
      CraftCtx next = CraftCtx.of(null, null, items, recipes, first);
      assertTrue(first.recipes == next.recipes && first.items == next.items);
      assertFalse(CraftCtx.of(null, null, new String(items), recipes, first).items == first.items);
      assertFalse(next.recipes.isEmpty() || next.items.isEmpty());
   }

   @Test
   void everyFixtureRootMatchesTheReferenceInBothModes() {
      JsonObject roots = Fx.EXP.getAsJsonObject("roots");

      for (String id : roots.keySet()) {
         JsonObject exp = roots.getAsJsonObject(id);
         String[] p = id.split(";");
         CraftCore.Clean c = p.length > 1
            ? CraftCore.clean("PET_" + p[0], CraftCore.PET_TIERS.get(Integer.parseInt(p[1])), CTX)
            : CraftCore.clean(id, null, CTX);
         if (exp.get("fromScratch").isJsonNull()) {
            assertFalse(c.craftable(), id + " has no recipe");
            continue;
         }

         assertTrue(c.craftable(), id);
         Fx.near(Fx.d(exp.getAsJsonObject("fromScratch").get("unitCost")), c.fromScratch(), id + " fromScratch");
         Fx.near(Fx.d(exp.getAsJsonObject("easy").get("unitCost")), c.easy(), id + " easy");
         assertTrue(c.fromScratch() <= c.easy() + 0.01, id + " buy orders never cost more than insta-buy");
         assertEquals(Set.of(), c.missing(), id);
      }
   }

   @Test
   void rootIsCraftedEvenWhenBuyingItIsCheaper() {
      double lbin = Fx.LBRAW.get("HYPERION").getAsDouble();
      assertTrue(lbin < CraftCore.clean("HYPERION", null, CTX).fromScratch(), "fixture premise");
      assertEquals("lowestBin", price("HYPERION", false, CTX).method(), "as an ingredient it is bought");
   }

   @Test
   void priceItemMatchesTheReferenceAtKeyNodes() {
      JsonObject items = Fx.EXP.getAsJsonObject("items");

      for (String id : items.keySet()) {
         for (String mode : List.of("fromScratch", "easy")) {
            JsonObject e = items.getAsJsonObject(id).getAsJsonObject(mode);
            CraftCore.Px got = price(id, mode.equals("easy"), CTX);
            Fx.near(Fx.d(e.get("unitCost")), got.unit(), id + " " + mode);
            assertEquals(m(e.get("method")), got.method(), id + " " + mode + " method");
            if (e.has("flagged")) {
               assertEquals(e.get("flagged").getAsBoolean(), got.fallback(), id + " easy fallback flag");
            }
         }
      }
   }

   @Test
   void npcCoinShopsCountAsBuyingInEasyMode() {
      for (JsonElement el : Fx.EXP.getAsJsonArray("easyNpcDiff")) {
         JsonObject d = el.getAsJsonObject();
         CraftCore.Px z = price(d.get("id").getAsString(), true, CTX);
         Fx.near(d.get("withNpc").getAsDouble(), z.unit(), d.get("id").getAsString());
         assertEquals("npc", z.method(), d.get("id").getAsString());
      }
   }

   @Test
   void idFormsResolveToTheSamePrice() {
      CraftCore.Px a = price("INK_SACK-4", false, CTX);
      assertEquals(a.unit(), price("INK_SACK:4", false, CTX).unit());
      assertTrue(a.unit() > 0);
      CraftCore.Px u = price("ENCHANTMENT_ULTIMATE_WISE_4", true, CTX);
      assertEquals(u.unit(), price("ULTIMATE_WISE;4", true, CTX).unit());
      assertTrue(u.unit() > 0);
   }

   @Test
   void lowestBinIsIgnoredForBazaarProducts() {
      for (String id : List.of("GIANT_FRAGMENT_LASER", "WITHER_CATALYST", "ENCHANTED_DIAMOND")) {
         assertTrue(Fx.LBRAW.has(id), "fixture premise");
         assertNotEquals("lowestBin", price(id, false, CTX).method(), id);
         assertNotEquals("lowestBin", price(id, true, CTX).method(), id);
      }
   }

   @Test
   void vanillaCyclesTerminate() {
      for (String id : List.of("IRON_INGOT", "IRON_BLOCK", "DIAMOND_BLOCK", "ENCHANTED_DIAMOND_BLOCK")) {
         double u = price(id, false, CTX).unit();
         assertTrue(Double.isFinite(u) && u > 0, id);
         Fx.near(Fx.d(Fx.EXP.getAsJsonObject("items").getAsJsonObject(id).getAsJsonObject("fromScratch").get("unitCost")), u, id);
      }
   }

   // ---------------------------------------------------------------- hand-computed graphs
   @Test
   void outputCountDividesTheRecipeCost() {
      CraftCtx ctx = Fx.synth("{'X':[['c',4,[['A',2]]]],'TOP':[['c',1,[['X',1]]]]}", "{}", "A", 8, 10);
      CraftCore.Px px = price("X", false, ctx);
      assertEquals(4, px.unit());
      assertEquals("craft", px.method());
      CraftCore.Clean x = CraftCore.clean("X", null, ctx);
      assertEquals(4, x.fromScratch());
      assertEquals(5, x.easy());
      assertEquals(4, CraftCore.clean("TOP", null, ctx).fromScratch());
   }

   @Test
   void memoNeverKeepsAResultThatDependedOnACycleCut() {
      // A <-> B; price B first (B = craft from A = 50), then A (A = buy order 50)
      CraftCtx ctx = Fx.synth("{'A':[['c',1,[['B',1]]]],'B':[['c',1,[['A',1]]]]}", "{}", "A", 50, 60, "B", 100, 110);
      CraftCore.Px b = price("B", false, ctx);
      CraftCore.Px a = price("A", false, ctx);
      assertEquals(50, b.unit());
      assertEquals("craft", b.method());
      assertEquals(50, a.unit());
      assertEquals("buyOrder", a.method());
      // pure cycle with no market: unavailable, no hang
      CraftCtx ctx2 = Fx.synth("{'P':[['c',1,[['Q',1]]]],'Q':[['c',1,[['P',1]]]]}", "{}");
      assertEquals("unavailable", price("P", false, ctx2).method());
      assertTrue(Double.isNaN(CraftCore.clean("P", null, ctx2).fromScratch()));
      // ingot / block pair, ingot bought at 100
      CraftCtx ctx3 = Fx.synth("{'I':[['c',9,[['BL',1]]]],'BL':[['c',1,[['I',9]]]]}", "{}", "I", 100, 120);
      assertEquals(900, price("BL", false, ctx3).unit());
      assertEquals(100, price("I", false, ctx3).unit());
      // root I forced through BL: BL can only be made from I (on the stack) and has no market
      assertTrue(Double.isNaN(CraftCore.clean("I", null, ctx3).fromScratch()));
   }

   @Test
   void coinsAddUpAndPseudoCurrenciesMakeARecipeUnusable() {
      CraftCtx ctx = Fx.synth(
         "{'F':[['f',1,[['A',1],['SKYBLOCK_COIN',1000]],30]],'BITS':[['c',1,[['SKYBLOCK_BIT',100]]]],'MIX':[['c',1,[['SKYBLOCK_COPPER',5]]],['c',1,[['A',2]]]]}",
         "{}", "A", 8, 10
      );
      assertEquals(1008, CraftCore.clean("F", null, ctx).fromScratch());
      assertEquals("unavailable", price("BITS", false, ctx).method());
      CraftCore.Clean bits = CraftCore.clean("BITS", null, ctx);
      assertTrue(Double.isNaN(bits.fromScratch()));
      assertTrue(bits.missing().contains("SKYBLOCK_BIT"));
      assertEquals(16, price("MIX", false, ctx).unit());
      assertEquals(20, CraftCore.clean("MIX", null, ctx).easy());
   }

   @Test
   void missingLeafMakesTheRootUnpricedAndIsListed() {
      CraftCtx ctx = Fx.synth("{'M':[['c',1,[['A',1],['NOWHERE_ITEM',2]]]]}", "{}", "A", 8, 10);
      CraftCore.Clean c = CraftCore.clean("M", null, ctx);
      assertTrue(c.craftable());
      assertTrue(Double.isNaN(c.fromScratch()));
      assertTrue(Double.isNaN(c.easy()));
      assertEquals(Set.of("NOWHERE_ITEM"), c.missing());
      assertFalse(CraftCore.clean("NO_RECIPE_AT_ALL", null, ctx).craftable());
   }

   @Test
   void easyFallsBackToTheFromScratchCostOfAnUnbuyableIngredient() {
      CraftCtx ctx = Fx.synth("{'TOP':[['c',1,[['SUB',1],['A',1]]]],'SUB':[['c',1,[['A',3]]]]}", "{}", "A", 8, 10);
      assertEquals(34, CraftCore.clean("TOP", null, ctx).easy());
      CraftCore.Px z = price("SUB", true, ctx);
      assertEquals(24, z.unit());
      assertTrue(z.fallback());
   }

   @Test
   void thinBooksAndJunkPrices() {
      CraftCtx ctx = Fx.synth("{}", "{'Z':0,'L':-5}", "T", null, 20, "Z", -1, 0, "S", "NaN", "x");
      CraftCore.Px t = price("T", false, ctx);
      assertEquals(20, t.unit());
      assertEquals("instaBuy", t.method());
      for (String id : List.of("Z", "S", "L")) {
         assertEquals("unavailable", price(id, false, ctx).method(), id);
         assertEquals("unavailable", price(id, true, ctx).method(), id);
      }
   }

   @Test
   void weirdIdsNeverThrow() {
      for (String id : new String[]{"", null, "lowercase_id", "???", "A;", ";5", "ENCHANTMENT__", "LOG:", "X".repeat(500), "SKYBLOCK_COIN", "SKYBLOCK_BIT"}) {
         for (boolean easy : new boolean[]{false, true}) {
            CraftCore.Px p = price(id, easy, CTX);
            assertTrue(Double.isNaN(p.unit()) || Double.isFinite(p.unit()));
            CraftCore.clean(id, null, CTX);
         }
      }

      assertEquals("unavailable", price("SKYBLOCK_BIT", false, CTX).method());
   }

   @Test
   void wholeFixtureRecipeSetPricesFast() {
      long t0 = System.nanoTime();
      int priced = 0;
      int n = 0;

      for (String id : CTX.recipes.keySet()) {
         for (boolean easy : new boolean[]{false, true}) {
            double u = price(id, easy, CTX).unit();
            n++;
            priced += Double.isNaN(u) ? 0 : 1;
            assertTrue(Double.isNaN(u) || u >= 0, id);
         }
      }

      assertTrue((System.nanoTime() - t0) / 1e6 < 2000, "slow");
      assertTrue(priced > n * 0.5);
   }

   // ---------------------------------------------------------------- review round 1 fixes
   @Test
   void deadBuyOrderIsIgnored() {
      CraftCtx ctx = Fx.synth("{}", "{}", "ENCHANTMENT_ULTIMATE_REITERATE_5", 2.8, 23374664.6, "ENCHANTMENT_LUCK_6", 10003, 306874);
      CraftCore.Px d = price("ENCHANTMENT_ULTIMATE_REITERATE_5", false, ctx);
      assertEquals("instaBuy", d.method());
      assertEquals(23374664.6, d.unit());
      CraftCore.Px l = price("ENCHANTMENT_LUCK_6", false, ctx);   // 3.3%: a real order, kept
      assertEquals("buyOrder", l.method());
      assertEquals(10003, l.unit());
      CraftCtx edge = Fx.synth("{}", "{}", "DEAD", 0.99, 100, "KEPT", 1.0, 100);
      assertEquals("instaBuy", price("DEAD", false, edge).method(), "below 1%");
      assertEquals("buyOrder", price("KEPT", false, edge).method(), "exactly 1%");
   }

   @Test
   void neuTradesAreIgnoredForEnchantBooksOnly() {
      CraftCtx ctx = Fx.synth(
         "{'ENDER_SLAYER;6':[['t',1,[['ENCHANTED_EMERALD',50]]],['n',1,[['SKYBLOCK_COIN',1500000]]]],'SOME_ITEM':[['t',1,[['ENCHANTED_EMERALD',2]]]]}",
         "{}", "ENCHANTMENT_ENDER_SLAYER_6", 250000.1, 440024.7, "ENCHANTED_EMERALD", 448, 500, "SOME_ITEM", 100000, 120000
      );
      CraftCore.Px e = price("ENCHANTMENT_ENDER_SLAYER_6", false, ctx);
      assertEquals("buyOrder", e.method());
      assertEquals(250000.1, e.unit());
      CraftCore.Px s = price("SOME_ITEM", false, ctx);
      assertEquals("craft", s.method());
      assertEquals(896, s.unit());
   }

   @Test
   void easyFallbackMayNotLoopBackThroughTheRoot() {
      CraftCtx ctx = Fx.synth("{'A':[['c',1,[['B',1]]]],'B':[['c',1,[['A',1]]]]}", "{}", "A", 10, 12);
      assertTrue(Double.isNaN(CraftCore.clean("A", null, ctx).easy()));
   }

   @Test
   void aTruncatedOrMissingSourceCountsAsFailed() {
      String bz = Fx.read("bazaar.json");
      String lb = Fx.read("lowestbins.json");
      CraftCtx ctx = CraftCtx.of(bz.substring(0, bz.length() / 2), lb.substring(0, lb.length() / 2), "{\"items\":[", null);
      assertTrue(ctx.bazaar.isEmpty() && ctx.lowestBin.isEmpty() && ctx.lowestBinFailed && ctx.items.isEmpty() && ctx.recipes.isEmpty());
      assertEquals(Fx.json("bazaar.json").getAsJsonObject("products").size(), Fx.ctx().bazaar.size(), "the whole file loads");
   }

   @Test
   void lowestBinsDownUsesThePerIdFallbackOncePerId() {
      List<String> asked = new ArrayList<>();
      CraftCtx ctx = CraftCtx.of(Fx.read("bazaar.json"), null, Fx.read("hypixel-items.json"), Fx.read("recipes.json")).binFallback(id -> {
         asked.add(id);
         return Fx.LBRAW.has(id) ? Fx.LBRAW.get(id).getAsDouble() : null;
      });
      assertTrue(ctx.lowestBinFailed);
      CraftCore.Clean c = CraftCore.clean("TERMINATOR", null, ctx);
      assertTrue(!asked.isEmpty() && asked.size() <= 20, "asked " + asked);
      assertEquals(asked.size(), Set.copyOf(asked).size(), "each id once");
      for (String id : asked) {
         assertTrue(CraftCore.bazaarEntry(id, ctx) == null, "never a bazaar id: " + id);
      }

      assertTrue(asked.contains("JUDGEMENT_CORE"));
      assertTrue(Double.isFinite(c.fromScratch()), "priced through the fallback");
      int before = asked.size();
      CraftCore.clean("TERMINATOR", null, ctx);
      assertEquals(before, asked.size(), "no repeated requests");
      // a failing request is not recorded: asked again next time
      Map<String, Integer> calls = new HashMap<>();
      CraftCtx down = CraftCtx.of(Fx.read("bazaar.json"), "", null, Fx.read("recipes.json")).binFallback(id -> {
         calls.merge(id, 1, Integer::sum);
         throw new IllegalStateException("offline");
      });
      CraftCore.clean("TERMINATOR", null, down);
      CraftCore.clean("TERMINATOR", null, down);
      assertTrue(calls.values().stream().allMatch(n -> n == 2), calls.toString());
   }
}
