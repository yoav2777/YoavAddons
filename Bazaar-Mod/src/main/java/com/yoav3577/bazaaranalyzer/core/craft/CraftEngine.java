package com.yoav3577.bazaaranalyzer.core.craft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Craft price of one auction item, the site's "CRAFT PRICE" (port of craftModifiers.js listingCraftCost). */
public final class CraftEngine {
   /** Enchant levels made by applying an item to the level below (the owner: Ender Slayer VII needs an End Stone Idol). */
   public static final Map<String, String> UPGRADE_ITEM = Map.of("ENDER_SLAYER_7", "ENDSTONE_IDOL");

   private CraftEngine() {
   }

   /**
    * tag = the AH item tag (e.g. "HYPERION", "PET_ENDER_DRAGON"); nbt = the item's raw SkyBlock NBT as JSON (the
    * custom_data compound: id, enchantments{}, gems{}, modifier, upgrade_level, petInfo (JSON string), drill_part_engine, ...).
    * Each total = clean item (forced through its cheapest recipe; no recipe = the clean item bought at market) + every
    * priced modifier. Thread-safe (one call at a time per ctx); pure CPU once the ctx is built.
    */
   public static CraftQuote listing(String tag, JsonObject nbt, CraftCtx ctx) {
      synchronized (ctx) {
         tag = tag == null ? "" : tag.toUpperCase(Locale.ROOT);
         Set<String> missing = new LinkedHashSet<>();
         if (ctx.itemsNote != null) {
            missing.add(ctx.itemsNote);
         }

         Map<String, JsonElement> f = CraftMods.flat(nbt);
         boolean pet = CraftMods.isPet(tag, f);
         String tier = pet ? CraftMods.petTier(f) : null;
         String baseId = tier != null ? tag.substring(4) + ";" + CraftCore.PET_TIERS.indexOf(tier) : tag;
         CraftCore.Clean clean = CraftCore.clean(tag, tier, ctx);
         missing.addAll(clean.missing());
         List<CraftMods.Mod> mods = CraftMods.list(tag, nbt, ctx);
         double[] totals = new double[2];

         for (int mode = 0; mode < 2; mode++) {
            boolean easy = mode == 1;
            double base = easy ? clean.easy() : clean.fromScratch();
            if (!clean.craftable()) {
               // no recipe (e.g. a pet tier without a Kat upgrade): the clean item bought at market, else modifiers only
               double unit = CraftCore.priceItem(baseId, easy, ctx).unit();
               base = Double.isNaN(unit) ? 0 : unit;
               if (Double.isNaN(unit)) {
                  missing.add(baseId + " (no recipe, no market price)");
               }
            } else if (Double.isNaN(base)) {
               missing.add(baseId + " (clean craft)");
            }

            double sum = 0;

            for (CraftMods.Line l : CraftMods.price(mods, easy, ctx)) {
               if (!Double.isNaN(l.total())) {
                  sum += l.total();
               } else {
                  missing.add(l.id() != null ? l.id() : l.label());
               }
            }

            totals[mode] = base + sum;
         }

         return new CraftQuote(totals[0], totals[1], List.copyOf(missing));
      }
   }

   /**
    * From-scratch cost of the item's modifiers only (no clean item, no reforge): what core.Appraiser compares items by.
    * Unpriced modifiers count 0. Same nbt shapes as listing(), plus Coflnet's flat nbt (see core.Appraiser.rowNbt).
    */
   public static double modValue(String tag, JsonObject nbt, CraftCtx ctx) {
      return modCosts(tag, nbt, ctx).values().stream().mapToDouble(Double::doubleValue).sum();
   }

   /** The modifier cost split by kind (enchant, stars, potatoBook, gem, recomb, abilityScroll, ...). */
   public static Map<String, Double> modCosts(String tag, JsonObject nbt, CraftCtx ctx) {
      synchronized (ctx) {
         JsonObject n = nbt == null ? new JsonObject() : nbt.deepCopy();
         n.remove("modifier");
         Map<String, Double> out = new LinkedHashMap<>();
         for (CraftMods.Line l : CraftMods.price(CraftMods.list(tag == null ? "" : tag.toUpperCase(Locale.ROOT), n, ctx), false, ctx)) {
            if (!Double.isNaN(l.total())) {
               out.merge(l.kind(), l.total(), Double::sum);
            }
         }

         return out;
      }
   }
}
