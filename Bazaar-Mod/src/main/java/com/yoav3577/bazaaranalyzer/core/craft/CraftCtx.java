package com.yoav3577.bazaaranalyzer.core.craft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Everything the craft engine prices from (port of craftCore.js createCraftContext + loadPrices + the bulk recipes
 * file, and craftModifiers.js loadModifierData). Build one per data refresh and reuse it: prices are memoized on it.
 * A source that is null or unparseable counts as failed, like a failed download on the site.
 */
public final class CraftCtx {
   /** Bazaar product: NaN = that side of the book is empty. */
   record Bz(double instaBuy, double buyOrder) {
   }

   /** The Hypixel items API fields the modifiers need. */
   record HyItem(String tier, JsonArray upgradeCosts, JsonArray gemSlots, JsonObject conversion) {
   }

   final Map<String, Bz> bazaar = new HashMap<>();
   final Map<String, Double> lowestBin = new HashMap<>();   // Coflnet fallback misses are stored as 0
   final Map<String, List<CraftCore.Recipe>> recipes;   // read-only once built: shared with the next ctx while its JSON is the same
   final Map<String, HyItem> items;
   private final String recipesJson;
   private final String itemsJson;
   final boolean lowestBinFailed;
   final String itemsNote;
   final Map<String, CraftCore.Px> scratchMemo = new HashMap<>();
   Function<String, Double> binFallback;

   private CraftCtx(String bazaarJson, String lowestBinsJson, String itemsJson, String recipesJson, CraftCtx prev) {
      this.recipesJson = recipesJson;
      this.itemsJson = itemsJson;
      if (!each(bazaarJson, "products", (id, p) -> {
         JsonObject o = p.isJsonObject() ? p.getAsJsonObject() : new JsonObject();
         this.bazaar.put(id, new Bz(top(o, "buy_summary"), top(o, "sell_summary")));
      })) {
         this.bazaar.clear();
      }

      if (!each(lowestBinsJson, null, (k, v) -> {
         double n = CraftCore.num(v);
         if (!Double.isNaN(n)) {
            this.lowestBin.put(k, n);
         }
      })) {
         this.lowestBin.clear();
      }

      this.lowestBinFailed = this.lowestBin.isEmpty();
      if (prev != null && prev.recipesJson == recipesJson) {
         this.recipes = prev.recipes;
      } else {
         this.recipes = parseRecipes(recipesJson);
      }

      if (prev != null && prev.itemsJson == itemsJson) {
         this.items = prev.items;
      } else {
         this.items = parseItems(itemsJson);
      }

      this.itemsNote = this.items.isEmpty() ? "item data unavailable: items API returned no items" : null;
   }

   private static Map<String, List<CraftCore.Recipe>> parseRecipes(String recipesJson) {
      Map<String, List<CraftCore.Recipe>> recipes = new HashMap<>();
      if (!each(recipesJson, null, (k, v) -> recipes.put(CraftCore.neuId(k), CraftCore.parseBulk(v)))) {
         recipes.clear();
      }

      return recipes;
   }

   private static Map<String, HyItem> parseItems(String itemsJson) {
      Map<String, HyItem> items = new HashMap<>();
      if (!each(itemsJson, "items", (k, v) -> {
         if (v.isJsonObject() && v.getAsJsonObject().get("id") instanceof JsonElement id && id.isJsonPrimitive()) {
            JsonObject o = v.getAsJsonObject();
            items.put(id.getAsString(), new HyItem(
               o.get("tier") instanceof JsonElement t && t.isJsonPrimitive() ? t.getAsString() : null,
               o.get("upgrade_costs") instanceof JsonArray a ? a : null,
               o.get("gemstone_slots") instanceof JsonArray a ? a : null,
               o.get("dungeon_item_conversion_cost") instanceof JsonObject c ? c : null
            ));
         }
      })) {
         items.clear();
      }

      return items;
   }

   /**
    * @param hypixelBazaarJson {@code https://api.hypixel.net/v2/skyblock/bazaar}
    * @param lowestBinsJson {@code https://lb.tricked.pro/lowestbins}
    * @param hypixelItemsJson {@code https://api.hypixel.net/v2/resources/skyblock/items}
    * @param recipesJson the site's {@code data/recipes.json} (compact: {@code {"ID":[["c",1,[["IN",8],...]],...]}})
    */
   /** The lowest BIN of an id from the lowest BINs list (lb.tricked.pro), NaN when it has none. */
   public double lowestBinOf(String id) {
      synchronized (this) {
         Double v = id == null ? null : this.lowestBin.get(id);
         return v != null && v > 0 ? v : Double.NaN;
      }
   }

   public static CraftCtx of(String hypixelBazaarJson, String lowestBinsJson, String hypixelItemsJson, String recipesJson) {
      return of(hypixelBazaarJson, lowestBinsJson, hypixelItemsJson, recipesJson, null);
   }

   /** Like {@link #of}, but reuses {@code prev}'s parsed recipes / items when they come from the same (identical) string. */
   public static CraftCtx of(String hypixelBazaarJson, String lowestBinsJson, String hypixelItemsJson, String recipesJson, CraftCtx prev) {
      return new CraftCtx(hypixelBazaarJson, lowestBinsJson, hypixelItemsJson, recipesJson, prev);
   }

   /**
    * Per-id lowest BIN used only when the lowest BINs list failed (the site asks Coflnet
    * {@code https://sky.coflnet.com/api/item/price/{ID}/bin} -> {@code lowest}). Called for at most 20 ids of the
    * item's recipe tree per listing, each id once per ctx; return null for "no BIN", throw to retry next time.
    */
   public CraftCtx binFallback(Function<String, Double> fn) {
      this.binFallback = fn;
      return this;
   }

   // cheapest price of a bazaar summary side: buy_summary[0] = insta-buy, sell_summary[0] = top buy order
   private static double top(JsonObject p, String side) {
      return p.get(side) instanceof JsonArray a && !a.isEmpty() && a.get(0) instanceof JsonObject o ? CraftCore.num(o.get("pricePerUnit")) : Double.NaN;
   }

   // Streams one object/array (the top level, or its `field`) one small value at a time, so a 5 MB response is never
   // held as a whole tree. False = missing or bad JSON (the caller drops what it read, like a failed download).
   private static boolean each(String json, String field, BiConsumer<String, JsonElement> fn) {
      if (json == null) {
         return false;
      }

      try (JsonReader r = new JsonReader(new StringReader(json))) {
         if (field != null) {
            r.beginObject();

            while (r.hasNext() && !r.nextName().equals(field)) {
               r.skipValue();
            }

            if (!r.hasNext()) {
               return false;
            }
         }

         switch (r.peek()) {
            case BEGIN_OBJECT -> {
               r.beginObject();

               while (r.hasNext()) {
                  fn.accept(r.nextName(), JsonParser.parseReader(r));
               }
            }
            case BEGIN_ARRAY -> {
               r.beginArray();

               while (r.hasNext()) {
                  fn.accept(null, JsonParser.parseReader(r));
               }
            }
            default -> {
               return false;
            }
         }

         return true;
      } catch (IOException | RuntimeException e) {
         return false;
      }
   }

   /** Ids reachable from the item (port of loadRecipeTree's BFS, used to pick the ids for the BIN fallback). */
   List<String> treeIds(String root) {
      Set<String> seen = new LinkedHashSet<>(List.of(root));
      List<String> level = List.of(root);

      for (int depth = 0; !level.isEmpty(); depth++) {
         List<String> next = new ArrayList<>();

         for (String id : level) {
            if (depth >= 12) {
               continue;
            }

            for (CraftCore.Recipe r : this.recipes.getOrDefault(id, List.of())) {
               for (CraftCore.Input in : r.inputs()) {
                  if (!seen.contains(in.id()) && !in.id().startsWith("SKYBLOCK_") && seen.size() < 400) {
                     seen.add(in.id());
                     next.add(in.id());
                  }
               }
            }
         }

         level = next;
      }

      return new ArrayList<>(seen);
   }
}
