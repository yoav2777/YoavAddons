package com.yoav3577.bazaaranalyzer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yoav3577.bazaaranalyzer.core.AhLink;
import com.yoav3577.bazaaranalyzer.core.Appraiser;
import com.yoav3577.bazaaranalyzer.core.CoflFilters;
import com.yoav3577.bazaaranalyzer.core.Filter;
import com.yoav3577.bazaaranalyzer.core.ItemMods;
import com.yoav3577.bazaaranalyzer.core.ModPricer;
import com.yoav3577.bazaaranalyzer.core.ModSelector;
import com.yoav3577.bazaaranalyzer.core.Modifier;
import com.yoav3577.bazaaranalyzer.core.PriceBook;
import com.yoav3577.bazaaranalyzer.core.craft.CraftCtx;
import com.yoav3577.bazaaranalyzer.core.craft.CraftEngine;
import com.yoav3577.bazaaranalyzer.core.craft.CraftQuote;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.nbt.CompoundTag;

/**
 * Values one item for the trade window worth and the hover debug (core.Appraiser does the math): the website link's
 * filters, Coflnet's recent sales / 30- and 90-day history / BIN listings, modifier costs from the craft engine.
 * Everything runs on EXEC, one item at a time, so the caches need no locks.
 */
final class Appraisal {
   static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "bazaaranalyzer-worth");
      t.setDaemon(true);
      return t;
   });
   private static final String COFL = "https://sky.coflnet.com/api";
   private static final long SOLD_TTL_MS = 120_000L;
   private static final long BIN_TTL_MS = 60_000L;
   private static final long HISTORY_TTL_MS = 600_000L;
   private static final long FULL_TTL_MS = 1_800_000L;
   private static final int SOLD_PAGE = 500;
   private static final int MAX_SOLD_PAGES = 3;
   // EXEC only.
   private static final Map<String, Cached> CACHE = new HashMap<>();
   /** Modifier cost per auction uuid (sold rows and listings don't change). */
   private static final Map<String, Double> MODS = new LinkedHashMap<>(4096, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Double> e) {
         return this.size() > 20000;
      }
   };

   private Appraisal() {
   }

   /**
    * What an AH item is worth. value NaN = could not price it (error says why); craft = its craft price with these
    * modifiers (null when unknown); filters = what "comparable" meant (the website link's filters + a pet level band).
    */
   record Report(String tag, Appraiser.Result result, List<Filter> filters, List<String> filterLabels, double mods, double clean, CraftQuote craft, String error) {
      double value() {
         return this.result == null ? Double.NaN : this.result.value();
      }
   }

   static boolean isBazaar(ItemMods m) {
      return !AhLink.isAhItem(m, id -> PriceData.products().contains(id));
   }

   /** Insta-sell price of one Bazaar item, NaN when unknown. */
   static double bazaarUnit(ItemMods m) {
      String product = AhLink.tag(m);
      PriceData.Live lv = product == null ? null : PriceData.live(product);
      double unit = lv == null ? Double.NaN : lv.instaSell();
      return Double.isFinite(unit) && unit > 0.0 ? unit : Double.NaN;
   }

   static Report ahItem(ItemMods m, CompoundTag nbt) throws IOException {
      String tag = AhLink.tag(m) != null ? AhLink.tag(m) : tagByName(m.name());
      if (tag == null) {
         throw new IOException("no Auction House match");
      }

      long now = System.currentTimeMillis();
      CraftCtx ctx = CraftPrices.ctx();
      JsonObject raw = NbtJson.of(nbt);
      boolean pet = tag.startsWith("PET_");
      // clean lowest BIN: meaningless for pets (any tier and level); their listings stand in (Appraiser.Source.LISTING)
      double clean = pet ? Double.NaN : ctx != null ? ctx.lowestBinOf(tag) : Double.NaN;
      if (!pet && Double.isNaN(clean)) {
         clean = cached("lbin|" + tag, BIN_TTL_MS, () -> PriceData.lowestBin(tag));
      }

      PriceBook book = id -> {
         PriceData.Live lv = PriceData.live(id);
         return lv == null ? Double.NaN : lv.instaBuy();
      };
      List<Modifier> chosen = ModSelector.select(ModSelector.keepKnownEnchants(ModPricer.price(m, book, id -> PriceData.live(id) != null), PriceData.knownEnchants()), clean, AhSettings.get());
      List<Filter> filters = new ArrayList<>();
      List<String> labels = new ArrayList<>();
      for (Modifier x : chosen) {
         filters.addAll(x.filters());
         labels.add(x.label());
      }

      Integer level = Appraiser.level(m.name());
      if (pet && level != null) {
         Filter band = Appraiser.levelBand(level);
         filters.add(band);
         labels.add("Level " + band.value());
      }

      double mods = ctx == null ? 0.0 : CraftEngine.modValue(tag, raw, ctx);
      Appraiser.Item target = Appraiser.target(m.name(), raw, mods);
      String q = CoflFilters.query(filters);
      String error = null;

      List<Appraiser.Item> sold = new ArrayList<>();
      List<JsonObject> fetched = new ArrayList<>();
      try {
         int same = 0;
         for (int page = 0; page < MAX_SOLD_PAGES; page++) {
            int p = page;
            JsonArray rows = cached("sold|" + tag + "|" + p, SOLD_TTL_MS, () -> array(COFL + "/auctions/tag/" + enc(tag) + "/sold?page=" + p + "&pageSize=" + SOLD_PAGE));
            for (JsonElement el : rows) {
               if (el instanceof JsonObject row) {
                  fetched.add(row);
               }

               if (el instanceof JsonObject row && CoflFilters.matches(row, filters)) {
                  Appraiser.Item it = Appraiser.ofRow(row, modsOf(tag, row, ctx), true);
                  sold.add(it);
                  same += target.sameKind(it) ? 1 : 0;
               }
            }

            if (same >= Appraiser.MIN_SALES || rows.size() < SOLD_PAGE) {
               break;
            }
         }

         List<Filter> wide = CoflFilters.relaxed(filters);
         if (same < Appraiser.MIN_SALES && !wide.equals(filters)) {
            // too few sales at the exact ultimate level (a Chimera 4 Claymore): the higher levels, moved by their mods cost
            sold.clear();
            for (JsonObject row : fetched) {
               if (CoflFilters.matches(row, wide)) {
                  sold.add(Appraiser.ofRow(row, modsOf(tag, row, ctx), true));
               }
            }
         }
      } catch (IOException | RuntimeException e) {
         error = reason(e);
      }

      List<Appraiser.Item> listings = new ArrayList<>();
      try {
         for (JsonElement el : cached("bin|" + tag + q, BIN_TTL_MS, () -> array(COFL + "/auctions/tag/" + enc(tag) + "/active/bin" + q))) {
            if (el instanceof JsonObject row && CoflFilters.matches(row, filters)) {
               listings.add(Appraiser.ofRow(row, modsOf(tag, row, ctx), false));
            }
         }
      } catch (IOException | RuntimeException e) {
         error = error == null ? reason(e) : error;
      }

      List<Appraiser.Day> month = null;
      try {
         month = Appraiser.days(cached("month|" + tag + q, HISTORY_TTL_MS, () -> array(COFL + "/item/price/" + enc(tag) + "/history/month" + q)));
      } catch (IOException | RuntimeException e) {
         // no history: recent sales / the anchor still work
      }

      Appraiser.Result res = Appraiser.estimate(target, sold, month, null, listings, clean, now);
      if (res.source() != Appraiser.Source.RECENT && res.source() != Appraiser.Source.FEW && res.source() != Appraiser.Source.MONTH && month != null) {
         // fewer than 3 sales in 30 days: look at 90 (Coflnet's full history lags a few weeks; the month fills the rest)
         try {
            List<Appraiser.Day> full = Appraiser.days(cached("full|" + tag + q, FULL_TTL_MS, () -> array(COFL + "/item/price/" + enc(tag) + "/history/full" + q)));
            res = Appraiser.estimate(target, sold, month, Appraiser.quarter(month, full, now), listings, clean, now);
         } catch (IOException | RuntimeException e) {
            // keep the estimate without it
         }
      }

      CraftQuote craft = null;
      try {
         craft = CraftPrices.quote(tag, raw);
      } catch (RuntimeException e) {
         BazaarClient.LOG.warn("Craft quote failed for {}", tag, e);
      }

      return new Report(tag, res, List.copyOf(filters), List.copyOf(labels), mods, clean, craft, error);
   }

   private static double modsOf(String tag, JsonObject row, CraftCtx ctx) {
      if (ctx == null) {
         return 0.0;
      }

      String uuid = CoflFilters.str(row, "uuid");
      Double v = uuid == null ? null : MODS.get(uuid);
      if (v == null) {
         v = CraftEngine.modValue(tag, Appraiser.rowNbt(row), ctx);
         if (uuid != null) {
            MODS.put(uuid, v);
         }
      }

      return v;
   }

   /** Items without a fixed AH tag (potions, shards): the first Coflnet search hit for the name, like the website link. */
   private static String tagByName(String name) throws IOException {
      return cached("name|" + name, HISTORY_TTL_MS, () -> {
         List<PriceData.Choice> hits = PriceData.searchAh(PriceData.searchName(name));
         return hits.isEmpty() ? null : hits.get(0).id();
      });
   }

   private static JsonArray array(String url) throws IOException {
      try {
         return JsonParser.parseString(Http.get(url, 15000)).getAsJsonArray();
      } catch (IOException e) {
         if (e.getMessage() != null && e.getMessage().startsWith("HTTP 204")) {
            return new JsonArray();
         }

         throw e;
      }
   }

   @SuppressWarnings("unchecked")
   private static <T> T cached(String key, long ttlMs, Fetch<T> fetch) throws IOException {
      long now = System.currentTimeMillis();
      Cached c = CACHE.get(key);
      if (c != null && now - c.at() < ttlMs) {
         return (T) c.value();
      }

      T v = fetch.get();
      CACHE.values().removeIf(x -> now - x.at() >= FULL_TTL_MS);
      CACHE.put(key, new Cached(now, v));
      return v;
   }

   static String reason(Exception e) {
      String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      int cut = msg.indexOf(" for http");
      return cut > 0 ? msg.substring(0, cut) : msg;
   }

   private static String enc(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
   }

   private record Cached(long at, Object value) {
   }

   @FunctionalInterface
   private interface Fetch<T> {
      T get() throws IOException;
   }
}
