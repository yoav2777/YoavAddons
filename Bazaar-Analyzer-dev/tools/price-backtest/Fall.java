package com.yoav3577.bazaaranalyzer.core;

import com.google.gson.*;
import com.yoav3577.bazaaranalyzer.core.craft.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/**
 * The thin-market path: for sales with a dumped 30-day history (probe-hm-<uuid>.json, the mod's own filters), predict
 * as if fewer than 3 recent sales were known, from the history days before the sale's day and the clean anchor.
 */
public class Fall {
   record Case(String tag, double price, double clean, double mods, List<Appraiser.Day> days, long ts, int monthN, List<Appraiser.Item> last) {}

   public static void main(String[] a) throws Exception {
      Path d = Path.of(a[0]);
      String bz = Files.readString(d.resolve("bazaar.json"));
      JsonObject lbins = JsonParser.parseString(Files.readString(d.resolve("lowestbins.json"))).getAsJsonObject();
      CraftCtx ctx = CraftCtx.of(bz, Files.readString(d.resolve("lowestbins.json")), Files.readString(d.resolve("items.json")), Files.readString(Path.of(a[1])));
      JsonObject products = JsonParser.parseString(bz).getAsJsonObject().getAsJsonObject("products");
      PriceBook book = id -> {
         JsonObject p = products.getAsJsonObject(id);
         if (p == null) return Double.NaN;
         JsonArray bs = p.getAsJsonArray("buy_summary");
         return bs != null && !bs.isEmpty() ? bs.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble() : Double.NaN;
      };
      Set<String> known = new HashSet<>();
      for (String k : products.keySet()) if (k.startsWith("ENCHANTMENT_")) known.add(k.substring(12, k.lastIndexOf('_')).toLowerCase(Locale.ROOT));
      Map<String, String> probeOf = new HashMap<>();
      try (var ds = Files.newDirectoryStream(d, "probe-hm-*.json")) {
         for (Path p : ds) probeOf.put(p.getFileName().toString().replace("probe-hm-", "").replace(".json", ""), Files.readString(p));
      }
      List<Case> cases = new ArrayList<>();
      try (var ds = Files.newDirectoryStream(d, "sold-*.json")) {
         for (Path p : ds) {
            String tag = p.getFileName().toString().replace("sold-", "").replace(".json", "");
            JsonArray all = JsonParser.parseString(Files.readString(p)).getAsJsonArray();
            double cleanT = lbins.has(tag) ? lbins.get(tag).getAsDouble() : Double.NaN;
            for (JsonElement e : all) {
               JsonObject r = e.getAsJsonObject();
               String h = probeOf.get(CoflFilters.str(r, "uuid"));
               if (h == null) continue;
               JsonElement he = JsonParser.parseString(h);
               if (!he.isJsonArray()) continue;
               double mods = CraftEngine.modValue(tag, Appraiser.rowNbt(r), ctx);
               Appraiser.Item it = Appraiser.ofRow(r, mods, true);
               long dayStart = it.ts() / 86400_000L * 86400_000L;
               List<Appraiser.Day> days = Appraiser.days(he.getAsJsonArray()).stream().filter(x -> x.ts() < dayStart).toList();
               double clean = lbins.has(tag) ? lbins.get(tag).getAsDouble() : Double.NaN;
               List<Filter> f = JavaBacktest.filtersOf(new JavaBacktest.Row(r, Appraiser.rowNbt(r), mods, it, it.ts(), it.price(), tag), tag, book, known, cleanT);
               List<Appraiser.Item> comps = new ArrayList<>();
               for (JsonElement ce : all) {
                  JsonObject cr = ce.getAsJsonObject();
                  Appraiser.Item ci = Appraiser.ofRow(cr, 0, true);
                  if (ci.ts() < it.ts() && ci.ts() >= it.ts() - 7 * 86400_000L && CoflFilters.matches(cr, f) && it.sameKind(ci)) {
                     comps.add(Appraiser.ofRow(cr, CraftEngine.modValue(tag, Appraiser.rowNbt(cr), ctx), true));
                  }
               }
               comps.sort(Comparator.comparingLong(Appraiser.Item::ts).reversed());
               cases.add(new Case(tag, it.price(), clean, mods, days, it.ts(), Appraiser.volume(days, it.ts() - 30 * 86400_000L), comps.subList(0, Math.min(2, comps.size()))));
            }
         }
      }
      System.out.println("cases " + cases.size() + " with >=3 history sales " + cases.stream().filter(c -> c.monthN() >= 3).count()
         + " with clean " + cases.stream().filter(c -> c.clean() > 0).count());
      Map<String, Function<Case, Double>> methods = new LinkedHashMap<>();
      methods.put("current (month, capped at anchor; else anchor)", c -> {
         Appraiser.Item t = new Appraiser.Item(Double.NaN, 0, c.mods(), null, null, false, false, Map.of());
         return Appraiser.estimate(t, List.of(), c.days(), null, List.of(), c.clean(), c.ts()).value();
      });
      methods.put("anchor only", c -> c.clean() > 0 ? c.clean() + Appraiser.ALPHA * c.mods() : Double.NaN);
      methods.put("month only", c -> c.monthN() >= 3 ? Appraiser.history(c.days(), c.ts() - 30 * 86400_000L, c.ts()) : Double.NaN);
      for (double cap : new double[]{1.0, 1.25, 1.5, 2.0}) {
         methods.put("month capped at " + cap + " x anchor", c -> {
            double m = c.monthN() >= 3 ? Appraiser.history(c.days(), c.ts() - 30 * 86400_000L, c.ts()) : Double.NaN;
            double an = c.clean() > 0 ? c.clean() + Appraiser.ALPHA * c.mods() : Double.NaN;
            return m > 0 ? (an > 0 ? Math.min(m, cap * an) : m) : an;
         });
      }
      methods.put("month avg only (no min blend)", c -> {
         if (c.monthN() < 3) return Double.NaN;
         List<double[]> vw = new ArrayList<>();
         for (Appraiser.Day x : c.days()) vw.add(new double[]{x.avg(), x.volume() * Math.pow(0.5, (c.ts() - x.ts()) / (2.0 * 86400_000L))});
         return Appraiser.weightedMedian(vw);
      });
      methods.put("month min only", c -> {
         if (c.monthN() < 3) return Double.NaN;
         List<double[]> vw = new ArrayList<>();
         for (Appraiser.Day x : c.days()) vw.add(new double[]{Math.max(x.min(), x.avg() / 2), x.volume() * Math.pow(0.5, (c.ts() - x.ts()) / (2.0 * 86400_000L))});
         return Appraiser.weightedMedian(vw);
      });
      methods.put("month, only last 7 days", c -> {
         int n = Appraiser.volume(c.days(), c.ts() - 7 * 86400_000L);
         return n >= 3 ? Appraiser.history(c.days(), c.ts() - 7 * 86400_000L, c.ts()) : Double.NaN;
      });
      Function<Case, Double> month = c -> c.monthN() >= 3 ? Appraiser.history(c.days(), c.ts() - 30 * 86400_000L, c.ts()) : Double.NaN;
      for (double al : new double[]{0.9, 1.0}) {
         methods.put("anchor only, alpha " + al, c -> c.clean() > 0 ? c.clean() + al * c.mods() : Double.NaN);
      }
      for (double w : new double[]{0.3, 0.5, 0.7}) {
         for (double al : new double[]{0.7, 0.9}) {
            methods.put("blend " + w + " x month + anchor(alpha " + al + ")", c -> {
               double m = month.apply(c), an = c.clean() > 0 ? c.clean() + al * c.mods() : Double.NaN;
               return m > 0 && an > 0 ? w * m + (1 - w) * an : m > 0 ? m : an;
            });
         }
      }
      methods.put("geo mean month x anchor(0.9)", c -> {
         double m = month.apply(c), an = c.clean() > 0 ? c.clean() + 0.9 * c.mods() : Double.NaN;
         return m > 0 && an > 0 ? Math.sqrt(m * an) : m > 0 ? m : an;
      });
      methods.put("1-2 latest sales (moved to mods) only", c -> {
         if (c.last().isEmpty()) return Double.NaN;
         return c.last().stream().mapToDouble(x -> x.price() - Appraiser.ALPHA * (x.mods() - c.mods())).average().orElse(Double.NaN);
      });
      methods.put("latest sales, else blend 0.5 (0.9)", c -> {
         if (!c.last().isEmpty()) return c.last().stream().mapToDouble(x -> x.price() - Appraiser.ALPHA * (x.mods() - c.mods())).average().orElse(Double.NaN);
         double m = month.apply(c), an = c.clean() > 0 ? c.clean() + 0.9 * c.mods() : Double.NaN;
         return m > 0 && an > 0 ? 0.5 * m + 0.5 * an : m > 0 ? m : an;
      });
      methods.put("mean of (latest sales, month, anchor 0.9) present", c -> {
         List<Double> v = new ArrayList<>();
         if (!c.last().isEmpty()) v.add(c.last().stream().mapToDouble(x -> x.price() - Appraiser.ALPHA * (x.mods() - c.mods())).average().orElse(Double.NaN));
         double m = month.apply(c), an = c.clean() > 0 ? c.clean() + 0.9 * c.mods() : Double.NaN;
         if (m > 0) v.add(m);
         if (an > 0) v.add(an);
         return v.isEmpty() ? Double.NaN : v.stream().mapToDouble(x -> x).average().orElse(Double.NaN);
      });
      methods.clear();
      methods.put("current", c -> {
         Appraiser.Item t = new Appraiser.Item(Double.NaN, 0, c.mods(), null, null, false, false, Map.of());
         return Appraiser.estimate(t, List.of(), c.days(), null, List.of(), c.clean(), c.ts()).value();
      });
      for (double al : new double[]{0.9, 1.0}) {
         methods.put("current, anchor alpha " + al, c -> {
            double m = month.apply(c), an = c.clean() > 0 ? c.clean() + al * c.mods() : Double.NaN;
            return m > 0 ? (an > 0 ? Math.min(m, an) : m) : an;
         });
      }
      for (int n : new int[]{1, 2}) {
         for (double cap : new double[]{1.0, 1.25, 1.5}) {
            for (double al : new double[]{0.7, 0.9}) {
               methods.put("<=" + n + " latest, capped " + cap + "x anchor(" + al + "), else current(" + al + ")", c -> {
                  double an = c.clean() > 0 ? c.clean() + al * c.mods() : Double.NaN;
                  double m = month.apply(c);
                  double cur = m > 0 ? (an > 0 ? Math.min(m, an) : m) : an;
                  if (c.last().isEmpty()) return cur;
                  double l = c.last().subList(0, Math.min(n, c.last().size())).stream().mapToDouble(x -> x.price() - Appraiser.ALPHA * (x.mods() - c.mods())).average().orElse(Double.NaN);
                  return an > 0 ? Math.min(l, cap * an) : l;
               });
            }
         }
      }
      methods.put("mean(latest, current(0.9))", c -> {
         double an = c.clean() > 0 ? c.clean() + 0.9 * c.mods() : Double.NaN;
         double m = month.apply(c);
         double cur = m > 0 ? (an > 0 ? Math.min(m, an) : m) : an;
         if (c.last().isEmpty()) return cur;
         double l = c.last().stream().mapToDouble(x -> x.price() - Appraiser.ALPHA * (x.mods() - c.mods())).average().orElse(Double.NaN);
         return cur > 0 ? (l + cur) / 2 : l;
      });
      System.out.println("cases with 1-2 latest sales: " + cases.stream().filter(c -> !c.last().isEmpty()).count());
      for (var m : methods.entrySet()) {
         List<Double> errs = new ArrayList<>();
         for (Case c : cases) {
            double v = m.getValue().apply(c);
            if (v > 0) errs.add((v - c.price()) / c.price());
         }
         report(m.getKey(), errs, cases.size());
      }
   }

   static void report(String label, List<Double> e, int total) {
      if (e.isEmpty()) { System.out.println(label + ": none"); return; }
      List<Double> abs = e.stream().map(Math::abs).sorted().toList();
      List<Double> b = e.stream().sorted().toList();
      System.out.printf(Locale.ROOT, "%-48s n=%4d/%d MdAPE=%5.1f%% bias=%+5.1f%% over20=%4.1f%% under20=%4.1f%%%n", label, e.size(), total,
         abs.get(abs.size() / 2) * 100, b.get(b.size() / 2) * 100, e.stream().filter(x -> x > 0.2).count() * 100.0 / e.size(), e.stream().filter(x -> x < -0.2).count() * 100.0 / e.size());
   }
}
