package com.yoav3577.bazaaranalyzer.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Lenient config-file reading: a missing, broken or wrongly typed key falls back to its default instead of failing the whole file. */
public final class Cfg {
   private Cfg() {
   }

   /** Parses a JSON object; anything else (null, garbage, an array) gives an empty object. */
   public static JsonObject object(String json) {
      try {
         JsonElement e = json == null ? null : JsonParser.parseString(json);
         return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
      } catch (RuntimeException e) {
         return new JsonObject();
      }
   }

   public static double num(JsonObject o, String key, double def) {
      try {
         double v = o.has(key) ? o.get(key).getAsDouble() : def;
         return Double.isFinite(v) ? v : def;
      } catch (RuntimeException e) {
         return def;
      }
   }

   public static int integer(JsonObject o, String key, int def) {
      double v = num(o, key, def);
      return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(v)));
   }

   public static boolean bool(JsonObject o, String key, boolean def) {
      try {
         return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isBoolean() ? o.get(key).getAsBoolean() : def;
      } catch (RuntimeException e) {
         return def;
      }
   }

   public static String str(JsonObject o, String key, String def) {
      try {
         return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
      } catch (RuntimeException e) {
         return def;
      }
   }

   public static int clamp(int v, int lo, int hi) {
      return Math.max(lo, Math.min(hi, v));
   }

   /** Slider position 0..1 of v on min..max (v is clamped first). */
   public static double toSlider(int v, int min, int max) {
      return max <= min ? 0.0 : (clamp(v, min, max) - min) / (double) (max - min);
   }

   /** The value at slider position f (0..1) on min..max, rounded to a multiple of step counted from min. */
   public static int fromSlider(double f, int min, int max, int step) {
      if (!Double.isFinite(f)) {
         f = 0.0;
      }

      f = Math.max(0.0, Math.min(1.0, f));
      int steps = Math.max(1, step);
      return clamp(min + (int) Math.round(f * (max - min) / steps) * steps, min, max);
   }
}
