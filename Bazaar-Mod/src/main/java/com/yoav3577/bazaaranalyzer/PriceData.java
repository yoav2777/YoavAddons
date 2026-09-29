package com.yoav3577.bazaaranalyzer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PriceData {
   static final Set<String> GENERIC_IDS = Set.of("PET", "RUNE", "ENCHANTED_BOOK", "POTION", "ATTRIBUTE_SHARD");
   private static final String COFL = "https://sky.coflnet.com/api";
   private static final ExecutorService EXEC = Executors.newFixedThreadPool(2, r -> {
      Thread t = new Thread(r, "bazaaranalyzer-prices");
      t.setDaemon(true);
      return t;
   });
   private static final long LIVE_MAX_AGE_MS = 20000L;
   private static volatile List<String> products = List.of();
   private static volatile Map<String, PriceData.Live> live = Map.of();
   private static volatile long loadedAt;
   /** The raw bazaar response of the last refresh (the craft engine parses it itself), or null. */
   static volatile String bazaarJson;
   private static volatile boolean loading;
   private static final long DAY_MS = 86400000L;
   private static volatile Set<String> knownEnchants;
   private static final long RETRY_AFTER_MS = 30000L;
   private static volatile long bazaarFailedAt;
   private static volatile long enchantsFailedAt;

   private PriceData() {
   }

   static void async(Runnable r) {
      EXEC.execute(r);
   }

   static List<String> products() {
      return products;
   }

   static PriceData.Live live(String id) {
      return live.get(id);
   }

   static synchronized void refreshBazaar() throws IOException {
      if (System.currentTimeMillis() - loadedAt >= LIVE_MAX_AGE_MS) {
         // Back off after a failure so waiting callers don't each start another 15 s request.
         if (System.currentTimeMillis() - bazaarFailedAt < RETRY_AFTER_MS) {
            throw new IOException("Bazaar API failed recently, retrying soon");
         }
         loading = true;

         try {
            String json = Http.get("https://api.hypixel.net/v2/skyblock/bazaar", 15000);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject prods = root.getAsJsonObject("products");
            Map<String, PriceData.Live> map = new HashMap<>();

            for (Entry<String, JsonElement> e : prods.entrySet()) {
               JsonObject p = e.getValue().getAsJsonObject();
               map.put(e.getKey(), new PriceData.Live(first(p.getAsJsonArray("buy_summary")), first(p.getAsJsonArray("sell_summary"))));
            }

            List<String> ids = new ArrayList<>(map.keySet());
            ids.sort(Comparator.naturalOrder());
            live = map;
            products = ids;
            bazaarJson = json;
            loadedAt = System.currentTimeMillis();
         } catch (IOException | RuntimeException e) {
            bazaarFailedAt = System.currentTimeMillis();
            throw e;
         } finally {
            loading = false;
         }
      }
   }

   static boolean loading() {
      return loading;
   }

   private static double first(JsonArray a) {
      return a != null && !a.isEmpty() ? a.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble() : Double.NaN;
   }

   static List<PriceData.Point> history(PriceData.Kind kind, String id, String range) throws IOException {
      return kind == PriceData.Kind.AH ? ahHistory(id, range) : bazaarHistory(id, range);
   }

   private static String iso(long ms) {
      return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").format(LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC));
   }

   private static List<PriceData.Point> bazaarHistory(String id, String range) throws IOException {
      long now = System.currentTimeMillis();

      String path = switch (range) {
         case "month" -> "/history?start=" + iso(now - 31 * DAY_MS) + "&end=" + iso(now);
         case "year" -> "/history?start=" + iso(now - 366 * DAY_MS) + "&end=" + iso(now);
         default -> "/history/" + range;
      };
      JsonArray arr = JsonParser.parseString(Http.get(COFL + "/bazaar/" + enc(id) + path, 15000)).getAsJsonArray();
      List<PriceData.Point> out = new ArrayList<>(arr.size());

      for (JsonElement el : arr) {
         JsonObject o = el.getAsJsonObject();
         double buy = o.has("buy") ? o.get("buy").getAsDouble() : 0.0;
         double sell = o.has("sell") ? o.get("sell").getAsDouble() : 0.0;
         long ts = Http.parseUtc(o.has("timestamp") ? o.get("timestamp").getAsString() : null);
         if (buy > 0.0 && sell > 0.0 && ts > 0L) {
            out.add(new PriceData.Point(ts, buy, sell, 0.0));
         }
      }

      out.sort(Comparator.comparingLong(PriceData.Point::ts));
      return out;
   }

   private static List<PriceData.Point> ahHistory(String tag, String range) throws IOException {
      boolean year = range.equals("year");
      JsonArray arr = JsonParser.parseString(Http.get(COFL + "/item/price/" + enc(tag) + "/history/" + (year ? "full" : range), 15000))
         .getAsJsonArray();
      long since = year ? System.currentTimeMillis() - 366 * DAY_MS : 0L;
      List<PriceData.Point> out = new ArrayList<>(arr.size());

      for (JsonElement el : arr) {
         JsonObject o = el.getAsJsonObject();
         double min = o.has("min") ? o.get("min").getAsDouble() : 0.0;
         double avg = o.has("avg") ? o.get("avg").getAsDouble() : 0.0;
         double volume = o.has("volume") ? o.get("volume").getAsDouble() : 0.0;
         long ts = Http.parseUtc(o.has("time") ? o.get("time").getAsString() : null);
         if (min > 0.0 && avg > 0.0 && ts > since) {
            out.add(new PriceData.Point(ts, min, avg, volume));
         }
      }

      out.sort(Comparator.comparingLong(PriceData.Point::ts));
      return out;
   }

   static Set<String> knownEnchants() {
      Set<String> cached = knownEnchants;
      if (cached != null) {
         return cached;
      } else if (System.currentTimeMillis() - enchantsFailedAt < RETRY_AFTER_MS) {
         return Set.of();
      } else {
         try {
            Set<String> names = new HashSet<>();

            for (JsonElement el : JsonParser.parseString(Http.get(COFL + "/filter/options", 20000)).getAsJsonArray()) {
               JsonObject o = el.getAsJsonObject();
               if (o.has("name") && "Enchantment".equals(o.get("name").getAsString()) && o.has("options")) {
                  for (JsonElement opt : o.getAsJsonArray("options")) {
                     names.add(opt.getAsString());
                  }
               }
            }

            names.remove("Any");
            names.remove("None");
            knownEnchants = names;
            return names;
         } catch (RuntimeException | IOException var7) {
            enchantsFailedAt = System.currentTimeMillis();
            return Set.of();
         }
      }
   }

   static double lowestBin(String tag) {
      try {
         JsonObject o = JsonParser.parseString(Http.get(COFL + "/item/price/" + enc(tag) + "/current", 8000)).getAsJsonObject();
         return o.has("buy") && !o.get("buy").isJsonNull() ? o.get("buy").getAsDouble() : Double.NaN;
      } catch (RuntimeException | IOException var2) {
         return Double.NaN;
      }
   }

   static List<PriceData.Choice> searchAh(String query) throws IOException {
      JsonArray arr = JsonParser.parseString(Http.get(COFL + "/item/search/" + enc(query.trim()), 10000)).getAsJsonArray();
      List<PriceData.Choice> hits = new ArrayList<>();

      for (JsonElement el : arr) {
         if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            String id = o.has("id") && !o.get("id").isJsonNull() ? o.get("id").getAsString() : null;
            String name = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : null;
            String type = o.has("type") && !o.get("type").isJsonNull() ? o.get("type").getAsString() : "item";
            if (id != null && name != null && "item".equals(type)) {
               hits.add(new PriceData.Choice(PriceData.Kind.AH, id, name));
            }
         }
      }

      String q = query.trim().toLowerCase(Locale.ROOT);
      hits.sort(Comparator.<PriceData.Choice>comparingInt(c -> relevance(c.name().toLowerCase(Locale.ROOT), q)).thenComparingInt(c -> c.name().length()));
      return new ArrayList<>(hits.subList(0, Math.min(6, hits.size())));
   }

   private static int relevance(String name, String q) {
      if (name.startsWith(q)) {
         return 0;
      } else if (name.contains(q)) {
         return 1;
      } else {
         for (String token : q.split(" ")) {
            if (!token.isEmpty() && !name.contains(token)) {
               return 3;
            }
         }

         return 2;
      }
   }

   private static String enc(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
   }

   static String pretty(String id) {
      StringBuilder sb = new StringBuilder();
      // the Bazaar renamed the ultimate Duplex to Reiterate, the game still says Duplex
      id = id.replace("ENCHANTMENT_ULTIMATE_REITERATE_", "ENCHANTMENT_ULTIMATE_DUPLEX_");

      for (String w : id.split("_")) {
         if (!w.isEmpty()) {
            if (sb.length() > 0) {
               sb.append(' ');
            }

            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
         }
      }

      return sb.toString();
   }

   static String searchName(String display) {
      String s = display == null ? "" : display.replaceAll("\\[Lvl \\d+]\\s*", "");
      return s.replaceAll("[^\\p{L}\\p{N}' ]", " ").replaceAll("\\s+", " ").strip();
   }

   record Choice(PriceData.Kind kind, String id, String name) {
   }

   static enum Kind {
      BAZAAR,
      AH;
   }

   record Live(double instaBuy, double instaSell) {
   }

   record Point(long ts, double buy, double sell, double volume) {
   }
}
