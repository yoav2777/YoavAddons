package com.yoav3577.bazaaranalyzer;

import com.google.gson.JsonObject;
import com.yoav3577.bazaaranalyzer.core.craft.CraftCtx;
import com.yoav3577.bazaaranalyzer.core.craft.CraftEngine;
import com.yoav3577.bazaaranalyzer.core.craft.CraftQuote;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The craft engine's prices for the valuation thread (Appraisal: modifier costs, the craft "!"), with the site's refresh
 * rhythm: Bazaar at most once a minute, lowest BINs every 5 min, Hypixel items once a day, recipes from the jar.
 */
final class CraftPrices {
   private static final long BAZAAR_MS = 60000L;
   private static final long LBIN_MS = 300000L;
   private static final long ITEMS_MS = 86400000L;
   private static CraftCtx ctx;
   private static long builtAt;
   private static String recipes;
   private static String lbins;
   private static long lbinsAt;
   private static String items;
   private static long itemsAt;

   private CraftPrices() {
   }

   /** The current prices, rebuilt when a minute old; null without recipes (every item would look recipe-less). */
   static synchronized CraftCtx ctx() {
      long now = System.currentTimeMillis();
      if (ctx == null || now - builtAt >= BAZAAR_MS) {
         try {
            PriceData.refreshBazaar();
         } catch (IOException e) {
            // keep the last bazaar
         }

         if (recipes == null) {
            try (InputStream in = CraftPrices.class.getResourceAsStream("/assets/bazaaranalyzer/recipes.json")) {
               recipes = in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
               recipes = null;
            }
         }

         if (now - lbinsAt >= LBIN_MS) {
            String s = get("https://lb.tricked.pro/lowestbins");
            if (s != null) {
               lbins = s;
               lbinsAt = now;
            }
         }

         if (now - itemsAt >= ITEMS_MS) {
            String s = get("https://api.hypixel.net/v2/resources/skyblock/items");
            if (s != null) {
               items = s;
               itemsAt = now;
            }
         }

         if (recipes == null) {
            return null;
         }

         ctx = CraftCtx.of(PriceData.bazaarJson, lbins, items, recipes, ctx);
         builtAt = now;
      }

      return ctx;
   }

   /** Craft price of the item with its modifiers (from scratch + the easy way), or null without prices. */
   static CraftQuote quote(String tag, JsonObject nbt) {
      CraftCtx c = ctx();
      return c == null ? null : CraftEngine.listing(tag, nbt, c);
   }

   /** The body, or null when the download failed (then it is tried again at the next rebuild). */
   private static String get(String url) {
      try {
         return Http.get(url, 20000);
      } catch (IOException | RuntimeException e) {
         BazaarClient.LOG.warn("Craft data download failed ({}): {}", url, e.getMessage());
         return null;
      }
   }
}
