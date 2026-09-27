package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.AhLink;
import com.yoav3577.bazaaranalyzer.core.Appraiser;
import com.yoav3577.bazaaranalyzer.core.ItemMods;
import com.yoav3577.bazaaranalyzer.core.TradeWindow;
import com.yoav3577.bazaaranalyzer.core.Worth;
import com.yoav3577.bazaaranalyzer.core.craft.CraftQuote;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Values the other player's side of the open trade (Appraisal / core.Appraiser) on a background thread; TradeAhUi draws the result. */
final class TradeWorth {
   private static final long HISTORY_TTL_MS = 600000L;
   // Valuation thread only.
   private static final Map<String, TradeWorth.Cached> CACHE = new HashMap<>();
   private static final Object LOCK = new Object();
   private static int gen;
   private static volatile TradeWorth.View view;
   // Client thread only.
   private static List<TradeWorth.Input> lastInputs;

   private TradeWorth() {
   }

   /** What to draw, or null (no items on their side). */
   static TradeWorth.View view() {
      return view;
   }

   /** Client thread, after the trade window changed: values their side again when their items changed. */
   static void update(List<Slot> slots) {
      try {
         if (!ModSettings.get().tradeButton()) {
            clear();
            return;
         }

         List<TradeWorth.Input> inputs = new ArrayList<>();

         for (int idx : TradeWindow.theirSlotIndexes()) {
            if (idx < slots.size()) {
               ItemStack stack = slots.get(idx).getItem();
               TradeWindow.RawStack raw = ItemReader.read(stack);
               ItemMods m = raw == null || Worth.isCoins(raw.name(), raw.lore()) ? null : ItemReader.mods(stack);
               if (m != null) {
                  inputs.add(new TradeWorth.Input(m, stack.getCount(), ItemReader.tag(stack)));
               }
            }
         }

         if (!inputs.equals(lastInputs)) {
            lastInputs = inputs;
            int g;
            synchronized (LOCK) {
               g = ++gen;
               view = inputs.isEmpty() ? null : new TradeWorth.View(true, 0.0, 0, List.of(), List.of(), List.of());
            }

            if (!inputs.isEmpty()) {
               Appraisal.EXEC.execute(() -> run(g, inputs));
            }
         }
      } catch (RuntimeException e) {
         BazaarClient.LOG.warn("Could not read the trade items for their worth", e);
      }
   }

   /** Client thread, while no trade window is open. */
   static void clear() {
      if (lastInputs != null) {
         lastInputs = null;
         synchronized (LOCK) {
            gen++;
            view = null;
         }
      }
   }

   private static boolean current(int g) {
      synchronized (LOCK) {
         return gen == g;
      }
   }

   private static void publish(int g, TradeWorth.View v) {
      synchronized (LOCK) {
         if (gen == g) {
            view = v;
         }
      }
   }

   private static void run(int g, List<TradeWorth.Input> inputs) {
      try {
         PriceData.refreshBazaar();
      } catch (IOException e) {
         // Without Bazaar prices the items still get AH prices; Bazaar items show as failed.
      }

      double total = 0.0;
      int failed = 0;
      List<TradeWorth.Line> lines = new ArrayList<>();
      // AH tag -> its inflation warning (null = checked, not inflated), so each item type is checked once.
      Map<String, String> inflation = new LinkedHashMap<>();
      List<String> craft = new ArrayList<>();

      for (TradeWorth.Input in : inputs) {
         if (!current(g)) {
            return;
         }

         String name = in.mods().name() + (in.count() > 1 ? " x" + in.count() : "");
         TradeWorth.Line line;
         try {
            line = AhLink.isAhItem(in.mods(), id -> PriceData.products().contains(id))
               ? ahItem(in, name, inflation, craft)
               : bazaarItem(in, name);
         } catch (IOException | RuntimeException e) {
            if (e instanceof RuntimeException) {
               BazaarClient.LOG.warn("Could not value {} in the trade", name, e);
            }

            line = failed(name, reason(e));
         }

         if (Double.isNaN(line.value())) {
            failed++;
         } else {
            total += line.value();
         }

         lines.add(line);
         publish(g, new TradeWorth.View(true, total, failed, List.copyOf(lines), warnings(inflation), List.copyOf(craft)));
      }

      publish(g, new TradeWorth.View(false, total, failed, List.copyOf(lines), warnings(inflation), List.copyOf(craft)));
   }

   private static List<String> warnings(Map<String, String> inflation) {
      return inflation.values().stream().filter(Objects::nonNull).toList();
   }

   private static TradeWorth.Line bazaarItem(TradeWorth.Input in, String name) {
      double unit = Appraisal.bazaarUnit(in.mods());
      if (Double.isNaN(unit)) {
         return failed(name, "no Bazaar price");
      }

      double v = unit * in.count();
      return new TradeWorth.Line(Worth.line(name, v, null), v);
   }

   private static TradeWorth.Line ahItem(TradeWorth.Input in, String name, Map<String, String> inflation, List<String> craft) throws IOException {
      Appraisal.Report r = Appraisal.ahItem(in.mods(), in.nbt());
      String tag = r.tag();
      // Pets and runes share one price history across rarities / levels, so their history can't say "inflated".
      if (!inflation.containsKey(tag) && !tag.startsWith("PET_") && !tag.startsWith("RUNE_")) {
         inflation.put(tag, null);
         try {
            Double normal = cached(
               "normal|" + tag,
               HISTORY_TTL_MS,
               () -> Worth.normalPrice(
                  PriceData.history(PriceData.Kind.AH, tag, "month").stream().map(p -> new Worth.Day(p.ts(), p.buy())).toList(), System.currentTimeMillis()
               )
            );
            inflation.put(tag, Worth.inflation(PriceData.pretty(tag), r.clean(), normal));
         } catch (IOException | RuntimeException e) {
            // No history, no inflation check.
         }
      }

      double unit = r.value();
      if (Double.isNaN(unit)) {
         return failed(name, r.error() != null ? r.error() : "no price found");
      }

      CraftQuote q = r.craft();
      if (q != null && Worth.craftBeats(new double[]{q.fromScratch(), q.easy()}, unit)) {
         craft.addAll(Worth.craftLines(name, new double[]{q.fromScratch(), q.easy()}, unit, q.missing()));
      }

      double v = unit * in.count();
      String note = r.error() != null ? "incomplete: " + r.error() : r.result().source() == Appraiser.Source.RECENT ? null : r.result().source().label;
      return new TradeWorth.Line(Worth.line(name, v, note), v);
   }

   @SuppressWarnings("unchecked")
   private static <T> T cached(String key, long ttlMs, TradeWorth.Fetch<T> fetch) throws IOException {
      long now = System.currentTimeMillis();
      TradeWorth.Cached c = CACHE.get(key);
      if (c != null && now - c.at() < ttlMs) {
         return (T)c.value();
      } else {
         T v = fetch.get();
         CACHE.values().removeIf(x -> now - x.at() >= HISTORY_TTL_MS);
         CACHE.put(key, new TradeWorth.Cached(now, v));
         return v;
      }
   }

   private static TradeWorth.Line failed(String name, String reason) {
      return new TradeWorth.Line(Worth.failedLine(name, reason), Double.NaN);
   }

   private static String reason(Exception e) {
      return Appraisal.reason(e);
   }

   /** One hover line; value is NaN when the item could not be priced. */
   record Line(String text, double value) {
   }

   /** loading = still valuing; total = sum of the priced items; craft = hover lines for the "!" (empty = no "!"). */
   record View(boolean loading, double total, int failed, List<TradeWorth.Line> lines, List<String> warnings, List<String> craft) {
   }

   private record Input(ItemMods mods, int count, CompoundTag nbt) {
   }

   private record Cached(long at, Object value) {
   }

   @FunctionalInterface
   private interface Fetch<T> {
      T get() throws IOException;
   }
}
