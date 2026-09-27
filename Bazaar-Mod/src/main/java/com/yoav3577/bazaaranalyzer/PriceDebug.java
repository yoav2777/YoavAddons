package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.Appraiser;
import com.yoav3577.bazaaranalyzer.core.FailureLimiter;
import com.yoav3577.bazaaranalyzer.core.ItemMods;
import com.yoav3577.bazaaranalyzer.core.TradeWindow;
import com.yoav3577.bazaaranalyzer.core.Worth;
import com.yoav3577.bazaaranalyzer.core.craft.CraftQuote;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Settings > Item worth > Debug on hover: how the mod values the hovered item (the same Appraisal as the trade window
 * worth), added to its tooltip. Valued once per item and kept 3 minutes; only the item under the mouse gets valued.
 */
final class PriceDebug {
   private static final long KEEP_MS = 180_000L;
   private static final int MAX_ENTRIES = 300;
   private static final int WRAP = 46;
   private static final FailureLimiter failures = new FailureLimiter(600);
   private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
   private static volatile String wanted;

   private PriceDebug() {
   }

   static void init() {
      ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
         try {
            if (ModSettings.get().priceDebug()) {
               add(stack, lines);
            }
         } catch (RuntimeException e) {
            if (failures.record(e.getClass().getName()) == FailureLimiter.Action.LOG_FULL) {
               BazaarClient.LOG.warn("Price debug tooltip failed", e);
            }
         }
      });
   }

   private static void add(ItemStack stack, List<Component> lines) {
      TradeWindow.RawStack raw = ItemReader.read(stack);
      ItemMods m = raw == null || Worth.isCoins(raw.name(), raw.lore()) ? null : ItemReader.mods(stack);
      if (m == null) {
         return;
      }

      CompoundTag nbt = ItemReader.tag(stack);
      String key = m.id() + "|" + (nbt == null ? "" : nbt.getString("uuid").orElse(Integer.toHexString(nbt.toString().hashCode())));
      long now = System.currentTimeMillis();
      Entry e = CACHE.get(key);
      if (e == null || e.done && now - e.at > KEEP_MS) {
         if (CACHE.size() > MAX_ENTRIES) {
            CACHE.values().removeIf(x -> x.done && now - x.at > KEEP_MS);
            if (CACHE.size() > MAX_ENTRIES) {
               CACHE.clear();
            }
         }

         Entry fresh = new Entry(now);
         CACHE.put(key, fresh);
         e = fresh;
         wanted = key;
         Appraisal.EXEC.execute(() -> compute(key, fresh, m, nbt));
      } else if (!e.done) {
         wanted = key;
      }

      lines.add(Component.literal("Yoav Addons price debug").withStyle(ChatFormatting.DARK_GRAY));
      if (!e.done) {
         lines.add(Component.literal("Valuing...").withStyle(ChatFormatting.GRAY));
      } else {
         int count = stack.getCount();
         lines.addAll(e.lines);
         if (count > 1 && Double.isFinite(e.unit)) {
            lines.add(kv("Stack of " + count, Worth.coins(e.unit * count), ChatFormatting.GOLD));
         }
      }
   }

   /** Valuation thread: fills the entry, or drops it when the mouse moved on to another item before its turn came. */
   private static void compute(String key, Entry e, ItemMods m, CompoundTag nbt) {
      if (!key.equals(wanted)) {
         CACHE.remove(key, e);
         return;
      }

      List<Component> out = new ArrayList<>();
      double unit = Double.NaN;
      try {
         try {
            PriceData.refreshBazaar();
         } catch (IOException ex) {
            // AH items still get valued; Bazaar items show no price
         }

         if (Appraisal.isBazaar(m)) {
            unit = Appraisal.bazaarUnit(m);
            PriceData.Live lv = PriceData.live(com.yoav3577.bazaaranalyzer.core.AhLink.tag(m));
            out.add(kv("Worth", Double.isNaN(unit) ? "no Bazaar price" : Worth.coins(unit) + " (Bazaar insta-sell)", ChatFormatting.GOLD));
            if (lv != null) {
               out.add(kv("Bazaar", "insta-sell " + Worth.coins(lv.instaSell()) + " · insta-buy " + Worth.coins(lv.instaBuy()), ChatFormatting.GRAY));
            }
         } else {
            Appraisal.Report r = Appraisal.ahItem(m, nbt);
            unit = r.value();
            lines(r, out);
         }
      } catch (IOException | RuntimeException ex) {
         if (ex instanceof RuntimeException) {
            BazaarClient.LOG.warn("Price debug could not value {}", m.name(), ex);
         }

         out.add(Component.literal("Could not value it: " + Appraisal.reason(ex)).withStyle(ChatFormatting.RED));
      }

      e.lines = List.copyOf(out);
      e.unit = unit;
      e.at = System.currentTimeMillis();
      e.done = true;
   }

   private static void lines(Appraisal.Report r, List<Component> out) {
      Appraiser.Result res = r.result();
      out.add(kv("Worth", Worth.coins(res.value()) + " (" + res.source().label + (res.capped() ? ", capped by a listing" : "") + ")", ChatFormatting.GOLD));
      out.add(kv("Recent sales", res.comps() > 0 && Double.isFinite(res.recent())
         ? Worth.coins(res.recent()) + " from " + res.comps() + " comparable" : "none comparable", ChatFormatting.GRAY));
      if (res.month() >= 0) {
         out.add(kv("30 days", res.month() + " sales" + (Double.isFinite(res.monthEst()) ? " · " + Worth.coins(res.monthEst()) : ""), ChatFormatting.GRAY));
      }

      if (res.quarter() >= 0) {
         out.add(kv("90 days", res.quarter() + " sales" + (Double.isFinite(res.quarterEst()) ? " · " + Worth.coins(res.quarterEst()) : ""), ChatFormatting.GRAY));
      }

      out.add(kv("Lowest BIN (filters)", Worth.coins(res.lowestBin()) + (Double.isFinite(res.listingCap()) ? " · same item " + Worth.coins(res.listingCap()) : ""), ChatFormatting.GRAY));
      out.add(Double.isFinite(r.clean())
         ? kv("Clean lowest BIN", Worth.coins(r.clean()) + " · mods cost " + Worth.coins(r.mods()), ChatFormatting.GRAY)
         : kv("Mods cost", Worth.coins(r.mods()), ChatFormatting.GRAY));
      if (Double.isFinite(res.anchor())) {
         out.add(kv("Clean + " + Math.round(Appraiser.ANCHOR_ALPHA * 100) + "% of mods", Worth.coins(res.anchor()), ChatFormatting.GRAY));
      }

      CraftQuote q = r.craft();
      if (q != null) {
         out.add(kv("Craft", "from scratch " + Worth.coins(q.fromScratch()) + " · easy " + Worth.coins(q.easy()), ChatFormatting.GRAY));
         if (!q.missing().isEmpty()) {
            wrap("  not priced: " + String.join(", ", q.missing()), ChatFormatting.DARK_GRAY, out);
         }
      }

      wrap("Filters: " + (r.filterLabels().isEmpty() ? "none (any " + r.tag() + ")" : String.join(", ", r.filterLabels())), ChatFormatting.DARK_AQUA, out);
      if (r.error() != null) {
         out.add(Component.literal("Incomplete: " + r.error()).withStyle(ChatFormatting.RED));
      }
   }

   private static Component kv(String k, String v, ChatFormatting valueColor) {
      return Component.literal(k + ": ").withStyle(ChatFormatting.GRAY).append(Component.literal(v).withStyle(valueColor));
   }

   /** Splits a long text at spaces into tooltip lines of at most WRAP characters. */
   private static void wrap(String text, ChatFormatting color, List<Component> out) {
      StringBuilder line = new StringBuilder();
      for (String w : text.split(" ")) {
         if (line.length() > 0 && line.length() + 1 + w.length() > WRAP) {
            out.add(Component.literal(line.toString()).withStyle(color));
            line.setLength(0);
            line.append("  ");
         }

         if (line.length() > 0 && !line.toString().isBlank()) {
            line.append(' ');
         }

         line.append(w);
      }

      if (!line.toString().isBlank()) {
         out.add(Component.literal(line.toString()).withStyle(color));
      }
   }

   private static final class Entry {
      volatile boolean done;
      volatile long at;
      volatile List<Component> lines = List.of();
      volatile double unit = Double.NaN;

      Entry(long at) {
         this.at = at;
      }
   }
}
