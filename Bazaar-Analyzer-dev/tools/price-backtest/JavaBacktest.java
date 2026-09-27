package com.yoav3577.bazaaranalyzer.core;

import com.google.gson.*;
import com.yoav3577.bazaaranalyzer.core.craft.*;
import java.nio.file.*;
import java.util.*;

/** Replays the dumped sales through the mod's own pipeline (ModPricer -> ModSelector -> CoflFilters -> Appraiser). */
public class JavaBacktest {
   /** The Bazaar products (listed ids, with or without orders); empty = decide by price. */
   public static JsonObject BOOKS = new JsonObject();
   public record Row(JsonObject row, JsonObject nbt, double mods, Appraiser.Item item, long ts, double price, String tag) {}

   public static void main(String[] a) throws Exception {
      Path d = Path.of(a[0]);
      String bz = Files.readString(d.resolve("bazaar.json"));
      JsonObject products = JsonParser.parseString(bz).getAsJsonObject().getAsJsonObject("products");
      BOOKS = products;
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
      List<double[]> errs = new ArrayList<>();
      Map<String, List<Double>> per = new TreeMap<>();
      Map<Appraiser.Source, Integer> sources = new EnumMap<>(Appraiser.Source.class);
      for (Path p : Files.newDirectoryStream(d, "sold-*.json")) {
         String tag = p.getFileName().toString().replace("sold-", "").replace(".json", "");
         JsonArray arr = JsonParser.parseString(Files.readString(p)).getAsJsonArray();
         if (arr.size() < 30) continue;
         List<Row> rows = new ArrayList<>();
         for (JsonElement e : arr) {
            JsonObject r = e.getAsJsonObject();
            JsonObject nbt = Appraiser.rowNbt(r);
            double mv = CraftEngine.modValue(tag, nbt, ctx);
            Appraiser.Item it = Appraiser.ofRow(r, mv, true);
            rows.add(new Row(r, nbt, mv, it, it.ts(), it.price(), tag));
         }
         rows.sort(Comparator.comparingLong(Row::ts));
         long tmax = rows.get(rows.size() - 1).ts();
         List<Row> tl = rows.stream().filter(r -> r.ts() >= tmax - 36 * 3600_000L).toList();
         int step = Math.max(1, tl.size() / 60);
         double clean = lbins.has(tag) ? lbins.get(tag).getAsDouble() : Double.NaN;
         for (int i = 0; i < tl.size(); i += step) {
            Row s = tl.get(i);
            List<Filter> filters = filtersOf(s, tag, book, known, clean);
            List<Appraiser.Item> comps = new ArrayList<>();
            for (Row c : rows) {
               if (c.ts() < s.ts() && c.ts() >= s.ts() - 7 * 86400_000L && CoflFilters.matches(c.row(), filters)) comps.add(c.item());
            }
            List<Filter> wide = CoflFilters.relaxed(filters);
            if (comps.size() < Appraiser.MIN_SALES && !wide.equals(filters)) {
               comps.clear();
               for (Row c : rows) {
                  if (c.ts() < s.ts() && c.ts() >= s.ts() - 7 * 86400_000L && CoflFilters.matches(c.row(), wide)) comps.add(c.item());
               }
            }
            Appraiser.Item target = new Appraiser.Item(Double.NaN, 0, s.mods(), s.item().petLevel(), s.item().petTier(), s.item().tierBoost(), s.item().shiny(), s.item().trackers());
            Appraiser.Result res = Appraiser.estimate(target, comps, List.of(), null, List.of(), clean, s.ts());
            if (!(res.value() > 0)) continue;
            double e = (res.value() - s.price()) / s.price();
            errs.add(new double[]{Math.abs(e), e});
            per.computeIfAbsent(tag, k -> new ArrayList<>()).add(e);
            sources.merge(res.source(), 1, Integer::sum);
         }
      }
      report("all", errs);
      System.out.println("sources " + sources);
      StringBuilder sb = new StringBuilder();
      for (var e : per.entrySet()) {
         List<Double> abs = e.getValue().stream().map(Math::abs).sorted().toList();
         List<Double> v = e.getValue().stream().sorted().toList();
         sb.append(String.format(Locale.ROOT, "%s:%.0f/%+.0f ", e.getKey().substring(0, Math.min(12, e.getKey().length())), abs.get(abs.size() / 2) * 100, v.get(v.size() / 2) * 100));
      }
      System.out.println(sb);
   }

   public static List<Filter> filtersOf(Row s, String tag, PriceBook book, Set<String> known, double clean) {
      JsonObject n = s.nbt();
      Map<String, Integer> ench = new LinkedHashMap<>();
      for (var e : n.getAsJsonObject("enchantments").entrySet()) ench.put(e.getKey(), e.getValue().getAsInt());
      Map<String, String> gems = new HashMap<>();
      for (var e : n.entrySet()) if (e.getKey().matches("[A-Z]+_\\d+(_gem)?|unlocked_slots") && e.getValue().isJsonPrimitive()) gems.put(e.getKey(), e.getValue().getAsString());
      List<String> scrolls = n.has("ability_scroll") ? Arrays.asList(n.get("ability_scroll").getAsString().split(" ")) : List.of();
      Map<String, Integer> counts = new LinkedHashMap<>();
      for (String k : new String[]{"art_of_war_count", "wood_singularity_count", "farming_for_dummies_count", "tuned_transmission"})
         if (n.has(k)) counts.put(k, (int) CoflFilters.num(n.get(k).getAsString()));
      int stars = (int) CoflFilters.num(n.has("upgrade_level") ? n.get("upgrade_level").getAsString() : n.has("dungeon_item_level") ? n.get("dungeon_item_level").getAsString() : "0");
      boolean pet = tag.startsWith("PET_");
      String petTier = pet ? CoflFilters.str(s.row(), "tier") : null;   // display tier, as ItemReader now gives
      ItemMods m = new ItemMods(tag, CoflFilters.str(s.row(), "itemName"), ench, null, stars, "1".equals(CoflFilters.str(n, "rarity_upgrades")),
         (int) CoflFilters.num(CoflFilters.str(n, "hpc")), gems, scrolls, CoflFilters.str(n, "power_ability_scroll"), counts, pet ? tag.substring(4) : null, petTier, null);
      List<Filter> f = new ArrayList<>(ModSelector.select(ModSelector.keepKnownEnchants(ModPricer.price(m, book, BOOKS.size() > 0 ? BOOKS::has : id -> !Double.isNaN(book.price(id))), known), clean, LinkSettings.DEFAULT)
         .stream().flatMap(x -> x.filters().stream()).toList());
      if (s.item().petLevel() != null && pet) f.add(Appraiser.levelBand(s.item().petLevel()));
      return f;
   }

   static void report(String label, List<double[]> errs) {
      List<Double> abs = errs.stream().map(x -> x[0]).sorted().toList();
      List<Double> b = errs.stream().map(x -> x[1]).sorted().toList();
      System.out.printf(Locale.ROOT, "%s n=%d MdAPE=%.1f%% bias=%+.1f%% over20=%.1f%% under20=%.1f%%%n", label, abs.size(), abs.get(abs.size() / 2) * 100, b.get(b.size() / 2) * 100,
         b.stream().filter(x -> x > 0.2).count() * 100.0 / b.size(), b.stream().filter(x -> x < -0.2).count() * 100.0 / b.size());
   }
}
