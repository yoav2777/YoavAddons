package com.yoav3577.bazaaranalyzer.core.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Port of craft/tests/modifiers.test.mjs (+ the modifier parts of fixes-r1.test.mjs), fed raw NBT like the mod. */
class CraftModsTest {
   private static final CraftCtx CTX = Fx.ctx();

   private static List<String> listingKeys() {
      try (Stream<Path> s = Files.list(Fx.DIR.resolve("listings"))) {
         return s.map(p -> p.getFileName().toString().replace(".json", "")).sorted().toList();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   private static List<CraftMods.Line> lines(String tag, JsonObject nbt, boolean easy, CraftCtx ctx) {
      return CraftMods.price(CraftMods.list(tag, nbt, ctx), easy, ctx);
   }

   private static List<String> partIds(String tag, JsonObject nbt) {
      List<String> out = new ArrayList<>();
      for (CraftMods.Mod m : CraftMods.list(tag, nbt, CTX)) {
         for (CraftMods.Part p : m.parts()) {
            out.add(p.id() + " x" + CraftCore.jsNum(p.qty()));
         }

         if (!Double.isNaN(m.coins())) {
            out.add(m.kind() + " coins " + CraftCore.jsNum(m.coins()));
         }
      }

      return out;
   }

   private static CraftMods.Line enchant(CraftCtx ctx, String type, int level, boolean easy) {
      List<CraftMods.Line> ls = lines("TERMINATOR", Fx.nbt("{'enchantments':{'" + type + "':" + level + "}}"), easy, ctx);
      assertEquals(1, ls.size());
      return ls.get(0);
   }

   @Test
   void listingTotalsMatchTheReferenceForEveryFixture() {
      JsonObject exp = Fx.EXP.getAsJsonObject("listings");

      for (String k : listingKeys()) {
         JsonObject row = Fx.listing(k);
         JsonObject e = exp.getAsJsonObject(k);
         CraftQuote q = CraftEngine.listing(row.get("tag").getAsString(), Fx.rawOf(row), Fx.ctx());
         for (String mode : List.of("fromScratch", "easy")) {
            // no recipe: the reference total is modifiers only, the engine adds the clean item at market
            double base = e.getAsJsonObject("base").get(mode).isJsonNull() ? Fx.d(e.getAsJsonObject("marketBase").get(mode)) : 0;
            Fx.near(Fx.d(e.getAsJsonObject("total").get(mode)) + base, mode.equals("easy") ? q.easy() : q.fromScratch(), k + " " + mode);
         }

         assertEquals(List.of(), q.missing(), k);
         assertTrue(q.fromScratch() <= q.easy() + 1, k);
      }
   }

   @Test
   void modifierLinesMatchTheReference() {
      for (String k : listingKeys()) {
         JsonObject row = Fx.listing(k);
         JsonObject nbt = Fx.rawOf(row);
         for (String mode : List.of("fromScratch", "easy")) {
            List<CraftMods.Line> got = lines(row.get("tag").getAsString(), nbt, mode.equals("easy"), CTX);
            List<JsonObject> ref = new ArrayList<>();
            List<JsonObject> refE = new ArrayList<>();
            for (JsonElement el : Fx.EXP.getAsJsonObject("listings").getAsJsonObject(k).getAsJsonObject("lines").getAsJsonArray(mode)) {
               (el.getAsJsonObject().get("group").getAsString().equals("enchant") ? refE : ref).add(el.getAsJsonObject());
            }

            // non-enchant lines: same (id, qty) multiset, same totals and methods
            List<CraftMods.Line> gotE = new ArrayList<>();
            for (CraftMods.Line l : got) {
               if (l.kind().equals("enchant")) {
                  gotE.add(l);
                  continue;
               }

               JsonObject r = ref.stream()
                  .filter(x -> x.get("id").getAsString().equals(l.id()) && Math.abs(x.get("qty").getAsDouble() - l.qty()) < 1e-9)
                  .findFirst()
                  .orElseThrow(() -> new AssertionError(k + " " + mode + ": line " + l.id() + " x" + l.qty() + " not in the reference"));
               ref.remove(r);
               Fx.near(r.get("total").getAsDouble(), l.total(), k + " " + mode + " " + l.id());
               String rm = r.get("method").getAsString();
               assertEquals(rm.equals("other") ? "craft" : rm, l.method(), k + " " + mode + " " + l.id());
            }

            assertEquals(List.of(), ref.stream().map(r -> r.get("id").getAsString()).toList(), k + " " + mode + ": reference lines not produced");
            // enchant lines (same order): never above the reference (the engine may use the enchanting table)
            assertEquals(refE.size(), gotE.size(), k + " " + mode + " enchant count");
            for (int i = 0; i < refE.size(); i++) {
               double rt = Fx.d(refE.get(i).get("total"));
               assertTrue(gotE.get(i).total() <= rt * (1 + 1e-9) + 0.01, k + " " + mode + " " + refE.get(i).get("label") + ": " + gotE.get(i).total() + " > " + rt);
            }
         }
      }
   }

   @Test
   void keyCasesFromTheSpec() {
      List<String> h = partIds("HYPERION", Fx.rawOf(Fx.listing("hyperion-full")));
      for (String s : List.of("ESSENCE_WITHER x3350", "FIRST_MASTER_STAR x1", "FIFTH_MASTER_STAR x1", "HOT_POTATO_BOOK x10", "FUMING_POTATO_BOOK x5",
            "IMPLOSION_SCROLL x1", "SHADOW_WARP_SCROLL x1", "WITHER_SHIELD_SCROLL x1", "SAPPHIRE_POWER_SCROLL x1", "PERFECT_SAPPHIRE_GEM x1",
            "RECOMBOBULATOR_3000 x1", "ENCHANTMENT_ULTIMATE_WISE_5 x1", "ENCHANTMENT_CHAMPION_1 x1", "gemSlot coins 250000")) {
         assertTrue(h.contains(s), "hyperion-full: " + s + " in " + h);
      }

      List<String> t = partIds("TERMINATOR", Fx.rawOf(Fx.listing("terminator-full")));
      assertTrue(t.contains("OPTICAL_LENS x1") && t.contains("reforge coins 1200000"), "Precise, MYTHIC: " + t);
      assertTrue(t.stream().anyMatch(s -> s.startsWith("ESSENCE_DRAGON x")), "stars + dungeonizing essence: " + t);
      List<String> p = partIds("PET_ENDER_DRAGON", Fx.rawOf(Fx.listing("pet-ender-dragon")));
      assertEquals(List.of("CROCHET_TIGER_PLUSHIE x1"), p, "pet: held item, no reforge");
      List<String> x = partIds("TERMINATOR", Fx.rawOf(Fx.listing("synthetic-extras")));
      for (String s : List.of("RUNE-SOULTWIST-3 x1", "DYE_LAVA x1", "WOOD_SINGULARITY x1", "ETHERWARP_MERGER x1", "ETHERWARP_CONDUIT x1")) {
         assertTrue(x.contains(s), s);
      }

      List<String> d = partIds("DIVAN_CHESTPLATE", Fx.rawOf(Fx.listing("divan-chestplate-gems")));
      assertEquals(5, d.stream().filter(s -> s.startsWith("PERFECT_")).count());
      assertEquals(5, d.stream().filter(s -> s.equals("GEMSTONE_CHAMBER x1")).count());
      // drill: gems as {quality, uuid} objects, 3 parts, Glacial with tier from the items API (MYTHIC + recomb = DIVINE)
      List<String> dr = partIds("DIVAN_DRILL", Fx.rawOf(Fx.listing("divan-drill")));
      for (String s : List.of("SAPPHIRE_POLISHED_DRILL_ENGINE x1", "PERFECTLY_CUT_FUEL_TANK x1", "GOBLIN_OMELETTE_SPICY x1", "FRIGID_HUSK x1",
            "reforge coins 800000", "PERFECT_JADE_GEM x1", "SIL_EX x5", "ENCHANTMENT_COMPACT_1 x1")) {
         assertTrue(dr.contains(s), s + " in " + dr);
      }
   }

   @Test
   void inGameNbtKeys() {
      JsonObject nbt = Fx.nbt("{'id':'HYPERION','hot_potato_count':12,'runes':{'SOULTWIST':3},'modifier':'withered','rarity_upgrades':1,"
         + "'gems':{'COMBAT_0':{'quality':'PERFECT','uuid':'x'},'COMBAT_0_gem':'SAPPHIRE','unlocked_slots':['COMBAT_0']},'ability_scroll':['IMPLOSION_SCROLL']}");
      List<String> ids = partIds("", nbt);   // no tag: the NBT id is used
      for (String s : List.of("HOT_POTATO_BOOK x10", "FUMING_POTATO_BOOK x2", "RUNE-SOULTWIST-3 x1", "WITHER_BLOOD x1", "reforge coins 60000",
            "PERFECT_SAPPHIRE_GEM x1", "gemSlot coins 250000", "IMPLOSION_SCROLL x1")) {
         assertTrue(ids.contains(s), s + " in " + ids);
      }

      assertEquals(List.of("AOTE_STONE x1", "reforge coins 5000000"), partIds("ASPECT_OF_THE_VOID", Fx.nbt("{'modifier':'aote_stone'}")), "stone name = Warped");
      assertEquals(List.of(), lines("HYPERION", Fx.nbt("{'modifier':'heroic'}"), false, CTX), "blacksmith reforge is free");
      Fx.near(4640000, lines("HYPERION", Fx.nbt("{'runes':{'SOULTWIST':3}}"), false, CTX).get(0).total(), "rune from lowest BINs");
   }

   // ---------------------------------------------------------------- review round 1: anvil rule
   @Test
   void anvilCombinesNonUltimatesOnlyUpToTableMaxPlusOne() {
      CraftCtx ctx = Fx.synth("{}", "{}",
         "ENCHANTMENT_CHANCE_3", 50, 100, "ENCHANTMENT_CHANCE_4", 1000.1, 162186.7, "ENCHANTMENT_CHANCE_5", 14559286.5, 19482924.1,
         "ENCHANTMENT_OVERLOAD_4", 5733918, null, "ENCHANTMENT_OVERLOAD_5", 15259860, 24653987,
         "ENCHANTMENT_ULTIMATE_WISE_4", 180000, 300000, "ENCHANTMENT_ULTIMATE_WISE_5", 500000, 700000,
         "ENCHANTMENT_SHARPNESS_5", 1000, 1200, "ENCHANTMENT_SHARPNESS_6", 800000, 900000);
      CraftMods.Line s6 = enchant(ctx, "sharpness", 6, false);
      assertEquals("buyOrder", s6.method());
      Fx.near(800000, s6.total(), "Sharpness 6: never combined above V");
      CraftMods.Line c5 = enchant(ctx, "chance", 5, false);
      assertEquals("ENCHANTMENT_CHANCE_5", c5.id());
      assertEquals("buyOrder", c5.method());
      Fx.near(14559286.5, c5.total(), "Chance 5 (table max III: V can't be anviled)");
      CraftMods.Line c4 = enchant(ctx, "chance", 4, false);
      assertEquals("buyOrder+anvil", c4.method());
      Fx.near(100, c4.total(), "Chance 4 = 2 x Chance 3");
      CraftMods.Line o5 = enchant(ctx, "overload", 5, false);
      assertEquals("ENCHANTMENT_OVERLOAD_5", o5.id());
      Fx.near(15259860, o5.total(), "Overload is not a table enchant: never combined");
      CraftMods.Line u5 = enchant(ctx, "ultimate_wise", 5, false);
      assertEquals("buyOrder+anvil", u5.method());
      Fx.near(360000, u5.total(), "UW5 = 2 x UW4");
   }

   @Test
   void aLowerBookPricedThroughARecipeNeverFeedsTheAnvil() {
      CraftCtx ctx = Fx.synth("{'LIFE_STEAL;2':[['c',1,[['PAPER',6]]]]}", "{}", "PAPER", 10, 12, "ENCHANTMENT_LIFE_STEAL_4", 310003.7, 740992.5);
      CraftMods.Line fs = enchant(ctx, "life_steal", 4, false);
      assertEquals("ENCHANTMENT_LIFE_STEAL_4", fs.id());
      Fx.near(310003.7, fs.total(), "LS4 from scratch");
      CraftMods.Line ez = enchant(ctx, "life_steal", 4, true);
      assertEquals("instaBuy", ez.method());
      Fx.near(740992.5, ez.total(), "LS4 easy");
   }

   @Test
   void easyBuysTheExactBookWhenItCan() {
      CraftCtx ctx = Fx.synth("{}", "{}",
         "ENCHANTMENT_OVERLOAD_4", 5733918, null, "ENCHANTMENT_OVERLOAD_5", 15259860, 24653987,
         "ENCHANTMENT_ULTIMATE_WISE_4", 180000, 300000, "ENCHANTMENT_ULTIMATE_WISE_5", 500000, 700000,
         "ENCHANTMENT_ULTIMATE_CHIMERA_4", null, 400000, "ENCHANTMENT_ULTIMATE_CHIMERA_5", 900000, null);
      CraftMods.Line o5 = enchant(ctx, "overload", 5, true);
      assertEquals("instaBuy", o5.method());
      Fx.near(24653987, o5.total(), "Overload 5 easy");
      CraftMods.Line u5 = enchant(ctx, "ultimate_wise", 5, true);
      assertEquals("instaBuy", u5.method());
      Fx.near(700000, u5.total(), "UW5 easy = its insta-buy, not 2 x UW4");
      // no sell offers: cheaper of its buy-order fallback (900k) and 2 x Chimera 4 insta-buy (800k)
      CraftMods.Line c5 = enchant(ctx, "ultimate_chimera", 5, true);
      assertEquals("instaBuy+anvil", c5.method());
      Fx.near(800000, c5.total(), "Chimera 5 easy");
   }

   @Test
   void enchantsWithoutAnyPriceCountAsZero() {
      CraftCtx ctx = Fx.synth("{}", "{}");
      assertEquals("table", enchant(ctx, "sharpness", 5, false).method());
      assertEquals("noMarket", enchant(ctx, "sharpness", 7, false).method());
      assertEquals(0, enchant(ctx, "sharpness", 7, true).total());
   }

   // ---------------------------------------------------------------- listing
   @Test
   void petTierWithoutARecipeIsBoughtAtItsLowestBin() {
      JsonObject row = Fx.listing("pet-ender-dragon");   // Epic Lvl 80 + Crochet Tiger Plushie
      CraftQuote q = CraftEngine.listing("PET_ENDER_DRAGON", Fx.rawOf(row), CTX);
      CraftCore.Px lb = CraftCore.priceItem("ENDER_DRAGON;3", false, CTX);
      assertEquals("lowestBin", lb.method());
      double held = lines("PET_ENDER_DRAGON", Fx.rawOf(row), false, CTX).get(0).total();
      Fx.near(lb.unit() + held, q.fromScratch(), "fromScratch");
      Fx.near(lb.unit() + held, q.easy(), "easy");
   }

   @Test
   void unpricedPartsAreListedAsMissing() {
      CraftQuote q = CraftEngine.listing("HYPERION", Fx.nbt("{'drill_part_engine':'no_such_engine','gems':{'unlocked_slots':['JADE_7']}}"), CTX);
      assertEquals(List.of("Unlock JADE_7", "NO_SUCH_ENGINE"), q.missing());
      assertTrue(Double.isFinite(q.fromScratch()), "the item itself is still priced");
      CraftQuote none = CraftEngine.listing("NOT_AN_ITEM", new JsonObject(), CraftCtx.of(null, null, null, null));
      assertEquals(0, none.fromScratch());
      assertEquals(List.of("item data unavailable: items API returned no items", "NOT_AN_ITEM (no recipe, no market price)"), none.missing());
   }

   @Test
   void oddNbtNeverThrows() {
      String[] odd = {
         "{}", "{'enchantments':'x'}", "{'enchantments':{'sharpness':'abc','smite':-3,'critical':99}}",
         "{'hpc':'abc','upgrade_level':'99','rarity_upgrades':'yes','unlocked_slots':5,'COMBAT_0':'SHINY','ability_scroll':12,'RUNE_':'','RUNE_X':'NaN','art_of_war_count':'-1'}",
         "{'petInfo':'{bad json','type':'X'}", "{'petInfo':{'type':'X','tier':'NOPE','heldItem':''}}",
         "{'drill_part_engine':'','gems':{'unlocked_slots':['JADE_9','','MINING_x'],'X_0':null},'runes':{'A':{'b':1}}}",
         "{'upgrade_level':10,'dungeon_item_level':5,'dungeon_item':true,'modifier':null}",
      };
      for (String tag : new String[]{"HYPERION", "PET_ENDER_DRAGON", "DIVAN_DRILL", "", null}) {
         for (String s : odd) {
            CraftQuote q = CraftEngine.listing(tag, Fx.nbt(s), CTX);
            assertTrue(q.missing() != null, s);
         }

         CraftEngine.listing(tag, null, CTX);
      }
   }
}
