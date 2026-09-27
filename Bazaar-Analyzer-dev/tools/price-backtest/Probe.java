package com.yoav3577.bazaaranalyzer.core;

import com.google.gson.*;
import com.yoav3577.bazaaranalyzer.core.craft.*;
import java.nio.file.*;
import java.util.*;

/** Prints data-tags "url" lines: the 30-day history with the mod's filters for a sample of recent sales (4 per item). */
public class Probe {
   public static void main(String[] a) throws Exception {
      Path d = Path.of(a[0]);
      int per = Integer.parseInt(a[2]);
      String bz = Files.readString(d.resolve("bazaar.json"));
      JsonObject products = JsonParser.parseString(bz).getAsJsonObject().getAsJsonObject("products");
      JsonObject lbins = JsonParser.parseString(Files.readString(d.resolve("lowestbins.json"))).getAsJsonObject();
      CraftCtx ctx = CraftCtx.of(bz, Files.readString(d.resolve("lowestbins.json")), Files.readString(d.resolve("items.json")), Files.readString(Path.of(a[1])));
      PriceBook book = id -> {
         JsonObject p = products.getAsJsonObject(id);
         if (p == null) return Double.NaN;
         JsonArray bs = p.getAsJsonArray("buy_summary");
         return bs != null && !bs.isEmpty() ? bs.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble() : Double.NaN;
      };
      Set<String> known = new HashSet<>();
      for (String k : products.keySet()) if (k.startsWith("ENCHANTMENT_")) known.add(k.substring(12, k.lastIndexOf('_')).toLowerCase(Locale.ROOT));
      List<Path> files = new ArrayList<>();
      Files.newDirectoryStream(d, "sold-*.json").forEach(files::add);
      files.sort(Comparator.naturalOrder());
      for (Path p : files) {
         String tag = p.getFileName().toString().replace("sold-", "").replace(".json", "");
         JsonArray arr = JsonParser.parseString(Files.readString(p)).getAsJsonArray();
         if (arr.size() < 10) continue;
         List<JavaBacktest.Row> rows = new ArrayList<>();
         for (JsonElement e : arr) {
            JsonObject r = e.getAsJsonObject();
            JsonObject nbt = Appraiser.rowNbt(r);
            Appraiser.Item it = Appraiser.ofRow(r, 0, true);
            rows.add(new JavaBacktest.Row(r, nbt, 0, it, it.ts(), it.price(), tag));
         }
         rows.sort(Comparator.comparingLong(JavaBacktest.Row::ts));
         long tmax = rows.get(rows.size() - 1).ts();
         List<JavaBacktest.Row> tl = rows.stream().filter(r -> r.ts() >= tmax - 36 * 3600_000L).toList();
         double clean = lbins.has(tag) ? lbins.get(tag).getAsDouble() : Double.NaN;
         Set<String> seen = new HashSet<>();
         for (int i = 0; i < tl.size() && seen.size() < per; i += Math.max(1, tl.size() / per)) {
            JavaBacktest.Row s = tl.get(i);
            String q = CoflFilters.query(JavaBacktest.filtersOf(s, tag, book, known, clean));
            if (seen.add(q)) {
               System.out.println("url hm-" + CoflFilters.str(s.row(), "uuid") + " /item/price/" + tag + "/history/month" + q);
            }
         }
      }
   }
}
