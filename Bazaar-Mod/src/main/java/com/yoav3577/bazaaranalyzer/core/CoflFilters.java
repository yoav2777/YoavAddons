package com.yoav3577.bazaaranalyzer.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Website filters as Coflnet sees them, done the way the site does it: params() are the query params for
 * /auctions/tag/{TAG}/active/bin (bundle Xi + BA_nbtParam), matches() is the site's check of one auction row (bundle qi),
 * run on sold rows (flattenedNbt) and again on active BIN rows (flatNbt).
 */
public final class CoflFilters {
   private static final Pattern GEM_SLOT = Pattern.compile("([A-Za-z]+)_(\\d)");
   private static final String STAR = "✪";
   private static final String MASTER_STARS = "➊➋➌➍➎";

   private CoflFilters() {
   }

   /** Query params in filter order. Filters Coflnet can't take (a third enchant, other nbt keys) are left out; matches() still checks them. */
   public static Map<String, String> params(List<Filter> filters) {
      Map<String, String> p = new LinkedHashMap<>();
      int enchants = 0;

      for (Filter f : filters) {
         switch (f.kind()) {
            case "enchant" -> {
               String[] keys = enchants == 0 ? new String[]{"Enchantment", "EnchantLvl"} : enchants == 1 ? new String[]{"SecondEnchantment", "SecondEnchantLvl"} : null;
               enchants++;
               if (keys != null) {
                  put(p, keys[0], f.type());
                  put(p, keys[1], "exact".equals(f.mode()) && f.min() != null ? f.min().toString() : atLeast(f.min()));
               }
            }
            case "stars" -> put(p, "Stars", atLeast(f.min()));
            case "rarity" -> put(p, "Rarity", f.value());
            case "recomb" -> p.put("Recombobulated", "true");
            case "hpc" -> put(p, "HotPotatoCount", atLeast(f.min()));
            case "petlevel" -> put(p, "PetLevel", f.value());
            case "nbt" -> {
               String[] kv = nbtParam(f);
               if (kv != null) {
                  p.put(kv[0], kv[1]);
               }
            }
            default -> {
            }
         }
      }

      return p;
   }

   /** params() as a query string ("?a=b&c=d", form-encoded like the site's URLSearchParams), or "" when there are none. */
   public static String query(List<Filter> filters) {
      StringBuilder sb = new StringBuilder();

      for (Entry<String, String> e : params(filters).entrySet()) {
         sb.append(sb.length() == 0 ? '?' : '&').append(enc(e.getKey())).append('=').append(enc(e.getValue()));
      }

      return sb.toString();
   }

   /** The same filters with exact levels widened to "at least" (the fallback when exact ones match too few sales). */
   public static List<Filter> relaxed(List<Filter> filters) {
      return filters.stream().map(f -> "exact".equals(f.mode()) ? Filter.enchant(f.type(), f.min()) : f).toList();
   }

   /** True when the Coflnet auction row passes every filter. */
   public static boolean matches(JsonObject row, List<Filter> filters) {
      for (Filter f : filters) {
         if (!matches(row, f)) {
            return false;
         }
      }

      return true;
   }

   static boolean matches(JsonObject row, Filter f) {
      JsonObject nbt = nbt(row);
      return switch (f.kind()) {
         case "enchant" -> {
            double level = enchantLevel(row, f.type());
            yield !Double.isNaN(level) && ("exact".equals(f.mode()) ? f.min() == null || level == f.min() : atLeast(level, f));
         }
         case "stars" -> atLeast(stars(row), f);
         case "rarity" -> f.value() != null && f.value().equalsIgnoreCase(str(row, "tier"));
         case "recomb" -> "1".equals(str(nbt, "rarity_upgrades")) == Boolean.parseBoolean(f.value());
         case "hpc" -> atLeast(num(str(nbt, "hpc")), f);
         case "petlevel" -> {
            Integer level = Appraiser.level(str(row, "itemName"));
            String[] lh = f.value() == null ? new String[0] : f.value().split("-");
            yield level != null && lh.length == 2 && level >= num(lh[0]) && level <= num(lh[1]);
         }
         case "nbt" -> {
            String v = str(nbt, f.key());
            yield "range".equals(f.mode()) ? atLeast(num(v), f) : v != null && f.value() != null && sameTokens(v, f.value());
         }
         default -> false;
      };
   }

   /** Stars of a row: upgrade_level / dungeon_item_level, else counted from the name (one per ✪, plus the last ➊-➎ master star number). */
   static int stars(JsonObject row) {
      JsonObject nbt = nbt(row);

      for (String k : new String[]{"upgrade_level", "dungeon_item_level"}) {
         String s = str(nbt, k);
         double v = s == null || s.isBlank() ? Double.NaN : parse(s.strip());
         if (Double.isFinite(v) && v > 0.0) {
            return (int) Math.floor(v);
         }
      }

      String name = str(row, "itemName");
      if (name == null) {
         return 0;
      }

      int n = 0;
      int master = 0;
      for (int i = 0; i < name.length(); i++) {
         String c = String.valueOf(name.charAt(i));
         if (c.equals(STAR)) {
            n++;
         } else if (MASTER_STARS.contains(c)) {
            master = MASTER_STARS.indexOf(c) + 1;
         }
      }

      return n + master;
   }

   private static void put(Map<String, String> p, String key, String value) {
      if (value != null) {
         p.put(key, value);
      }
   }

   /** The site's range param for "at least min" (no max): ">min-1". */
   private static String atLeast(Integer min) {
      return min == null ? null : ">" + (min - 1);
   }

   private static boolean atLeast(double v, Filter f) {
      return f.min() == null || v >= f.min();
   }

   private static String[] nbtParam(Filter f) {
      String k = f.key();
      boolean range = "range".equals(f.mode());
      String v = range ? atLeast(f.min()) : f.value();
      if (k == null || v == null || v.isEmpty()) {
         return null;
      } else if (k.equals("ability_scroll")) {
         return new String[]{"AbilityScroll", String.join(" ", tokens(v))};
      } else if (k.equals("power_ability_scroll")) {
         return new String[]{"PowerAbilityScroll", v};
      }

      Matcher g = GEM_SLOT.matcher(k);
      if (g.matches() && !range) {
         return new String[]{g.group(1).toLowerCase(Locale.ROOT) + g.group(2) + "Gem", v.toUpperCase(Locale.ROOT)};
      } else if (k.equals("art_of_war_count")) {
         return new String[]{"ArtOfTheWar", "yes"};
      } else if (k.equals("wood_singularity_count")) {
         return new String[]{"WoodSingularity", "yes"};
      } else {
         return null;
      }
   }

   /** The row's level of that enchant, NaN when it doesn't have it. */
   private static double enchantLevel(JsonObject row, String type) {
      JsonElement arr = row.get("enchantments");
      if (arr != null && arr.isJsonArray()) {
         for (JsonElement el : arr.getAsJsonArray()) {
            if (el.isJsonObject() && type != null && type.equals(str(el.getAsJsonObject(), "type"))) {
               return num(str(el.getAsJsonObject(), "level"));
            }
         }
      }

      return Double.NaN;
   }

   private static boolean sameTokens(String a, String b) {
      return tokens(a.toLowerCase(Locale.ROOT)).equals(tokens(b.toLowerCase(Locale.ROOT)));
   }

   private static List<String> tokens(String s) {
      return Arrays.stream(s.split(" ")).filter(t -> !t.isEmpty()).sorted().toList();
   }

   /** The row's flattened nbt: flattenedNbt on sold rows, flatNbt on active BIN rows. */
   private static JsonObject nbt(JsonObject row) {
      for (String k : new String[]{"flattenedNbt", "flatNbt"}) {
         JsonElement e = row.get(k);
         if (e != null && e.isJsonObject()) {
            return e.getAsJsonObject();
         }
      }

      return new JsonObject();
   }

   public static String str(JsonObject o, String key) {
      JsonElement e = key == null ? null : o.get(key);
      return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
   }

   /** Like the site's Number(v ?? 0) || 0: missing, blank or junk is 0. */
   static double num(String s) {
      if (s == null || s.isBlank()) {
         return 0.0;
      }

      double v = parse(s.strip());
      return Double.isNaN(v) ? 0.0 : v;
   }

   private static double parse(String s) {
      try {
         return Double.parseDouble(s);
      } catch (NumberFormatException e) {
         return Double.NaN;
      }
   }

   private static String enc(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8);
   }
}
