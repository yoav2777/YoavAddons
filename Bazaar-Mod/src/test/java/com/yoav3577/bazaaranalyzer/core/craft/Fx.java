package com.yoav3577.bazaaranalyzer.core.craft;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Test data: the site's craft fixtures (Bazaar-Analyzer-dev/craft/fixtures, recorded 2026-09-21) and expected.json
 * from fixtures/reference.mjs, an implementation of the spec that is independent of the engine.
 */
final class Fx {
   static final Path DIR = Path.of("../Bazaar-Analyzer-dev/craft/fixtures");
   static final JsonObject EXP = json("expected.json");
   static final JsonObject LBRAW = json("lowestbins.json");

   private Fx() {
   }

   static String read(String name) {
      try {
         return Files.readString(DIR.resolve(name));
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   static JsonObject json(String name) {
      return JsonParser.parseString(read(name)).getAsJsonObject();
   }

   /** Offline ctx over the fixture snapshots (bulk recipes file as the site ships it). */
   static CraftCtx ctx() {
      return CraftCtx.of(read("bazaar.json"), read("lowestbins.json"), read("hypixel-items.json"), read("recipes.json"));
   }

   /** Small hand-made ctx. recipes / lowestBins in JSON with ' for "; bz = id, buyOrder, instaBuy (null = empty side). */
   static CraftCtx synth(String recipes, String lowestBins, Object... bz) {
      JsonObject products = new JsonObject();

      for (int i = 0; i < bz.length; i += 3) {
         JsonObject p = new JsonObject();
         p.add("sell_summary", side(bz[i + 1]));
         p.add("buy_summary", side(bz[i + 2]));
         products.add((String) bz[i], p);
      }

      JsonObject bazaar = new JsonObject();
      bazaar.add("products", products);
      return CraftCtx.of(bazaar.toString(), lowestBins.replace('\'', '"'), null, recipes.replace('\'', '"'));
   }

   private static JsonArray side(Object price) {
      JsonArray a = new JsonArray();
      if (price != null) {
         JsonObject o = new JsonObject();
         if (price instanceof Number n) {
            o.addProperty("pricePerUnit", n);
         } else {
            o.addProperty("pricePerUnit", price.toString());
         }

         a.add(o);
      }

      return a;
   }

   static JsonObject listing(String key) {
      return json("listings/" + key + ".json");
   }

   /**
    * A Coflnet row as the raw NBT the mod reads in game: nbtData.data, plus the keys Coflnet only has in its flat NBT,
    * enchantments as {name: level}, and the reforge as `modifier` (sold rows only have it in the item name).
    */
   static JsonObject rawOf(JsonObject row) {
      JsonObject raw = row.has("nbtData") ? row.getAsJsonObject("nbtData").getAsJsonObject("data").deepCopy() : new JsonObject();
      Map<String, JsonElement> have = CraftMods.flat(raw);
      JsonObject flat = row.has("flatNbt") ? row.getAsJsonObject("flatNbt") : row.getAsJsonObject("flattenedNbt");

      for (Map.Entry<String, JsonElement> e : flat.entrySet()) {
         if (!have.containsKey(e.getKey())) {
            raw.add(e.getKey(), e.getValue());
         }
      }

      JsonArray ench = row.getAsJsonArray("enchantments");
      if (ench != null && !ench.isEmpty()) {
         JsonObject en = new JsonObject();

         for (JsonElement e : ench) {
            en.add(e.getAsJsonObject().get("type").getAsString(), e.getAsJsonObject().get("level"));
         }

         raw.add("enchantments", en);
      }

      String rf = row.has("reforge") ? row.get("reforge").getAsString() : row.get("itemName").getAsString().split(" ")[0];
      if (!rf.equalsIgnoreCase("none")) {
         raw.addProperty("modifier", rf.toLowerCase(Locale.ROOT));
      }

      return raw;
   }

   static JsonObject nbt(String json) {
      return JsonParser.parseString(json.replace('\'', '"')).getAsJsonObject();
   }

   /** JSON number or null -> double (NaN). */
   static double d(JsonElement e) {
      return e == null || e.isJsonNull() ? Double.NaN : e.getAsDouble();
   }

   /** Coins: 1e-6 relative + 0.01 absolute; NaN only equals NaN. */
   static void near(double want, double got, String msg) {
      boolean ok = Double.isNaN(want) ? Double.isNaN(got) : Math.abs(want - got) <= Math.max(Math.abs(want) * 1e-6, 0.01);
      assertTrue(ok, msg + ": got " + got + ", expected " + want);
   }
}
