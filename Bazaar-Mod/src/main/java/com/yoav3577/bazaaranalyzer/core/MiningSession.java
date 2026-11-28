package com.yoav3577.bazaaranalyzer.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * One mining session: what went into the sacks (every compacting family summed in raw items, see MiningItems; other
 * items by id) and how long you actually mined. Breaks longer than the idle time do not count at all, so the hourly
 * rate is per hour of mining, not of standing around. Removals (crafting from sacks, taking items out) cancel gains of
 * the same family but never take a family below zero. Client thread only; times are passed in.
 */
public final class MiningSession {
   /** A row of the box: the item as the chosen tier shows it, how many and their worth (NaN when unpriced). */
   public record Line(String key, String name, double count, double value) {
   }

   private final Map<String, Long> raw = new LinkedHashMap<>();
   private boolean started;
   private long activeMs;
   /** Time since you last mined, counted in only when you mine again within the idle time (a short break). */
   private long pendingMs;
   private long lastActivity;
   private long lastTick;

   /** count of this item went in (negative: out). key is a bazaar id, or the item name when the id is not known. */
   public void add(String key, long count) {
      MiningItems.Hit h = MiningItems.byId(key);
      String k = h == null ? key : h.family().id();
      long n = h == null ? count : count * h.raw();
      long v = Math.max(0L, this.raw.getOrDefault(k, 0L) + n);
      if (v == 0L) {
         this.raw.remove(k);
      } else {
         this.raw.put(k, v);
      }
   }

   /** Called every tick: mining = you are breaking a block on a mining island now. */
   public void tick(long now, boolean mining, long idleMs) {
      long dt = this.lastTick > 0L ? Math.max(0L, Math.min(now - this.lastTick, 1000L)) : 0L;
      this.lastTick = now;
      if (this.started) {
         if (mining) {
            this.activeMs += this.pendingMs + dt;
            this.pendingMs = 0L;
         } else if (now - this.lastActivity <= idleMs) {
            this.pendingMs += dt;
         } else {
            this.pendingMs = 0L;
         }
      }

      if (mining) {
         this.started = true;
         this.lastActivity = now;
      }
   }

   public boolean started() {
      return this.started;
   }

   public boolean isEmpty() {
      return !this.started && this.raw.isEmpty();
   }

   public boolean paused(long now, long idleMs) {
      return !this.started || now - this.lastActivity > idleMs;
   }

   /** Time since you last mined (0 before the first block). */
   public long idleFor(long now) {
      return this.started ? Math.max(0L, now - this.lastActivity) : 0L;
   }

   /** Time mined: while breaking blocks, plus breaks shorter than the idle time (a break still going counts until it gets too long). */
   public long activeMs() {
      return this.activeMs + this.pendingMs;
   }

   /** Raw totals: family raw id or item key -> count. */
   public Map<String, Long> totals() {
      return Map.copyOf(this.raw);
   }

   /**
    * The rows, worth first. price gives an id's coins each (NaN or 0 = not on the bazaar), nameOf the name of a key that
    * is no compacting family.
    */
   public List<Line> lines(MiningItems.Tier tier, ToDoubleFunction<String> price, Function<String, String> nameOf) {
      List<Line> out = new ArrayList<>();
      for (Map.Entry<String, Long> e : this.raw.entrySet()) {
         MiningItems.Family f = MiningItems.family(e.getKey());
         if (f != null) {
            MiningItems.Form form = f.form(tier);
            out.add(new Line(e.getKey(), form.name(), e.getValue() / (double) form.raw(), e.getValue() * perRaw(f, tier, price)));
         } else {
            double p = price.applyAsDouble(e.getKey());
            out.add(new Line(e.getKey(), nameOf.apply(e.getKey()), e.getValue(), p > 0.0 ? e.getValue() * p : Double.NaN));
         }
      }

      out.sort(Comparator.comparingDouble((Line l) -> Double.isNaN(l.value) ? -1.0 : l.value).reversed().thenComparing(Line::name));
      return out;
   }

   /** Total worth of the rows (unpriced ones count 0). */
   public static double total(List<Line> lines) {
      double t = 0.0;
      for (Line l : lines) {
         if (!Double.isNaN(l.value)) {
            t += l.value;
         }
      }

      return t;
   }

   /** Coins per raw item when sold as the tier's form; a form not on the bazaar falls back to the one below it. */
   public static double perRaw(MiningItems.Family f, MiningItems.Tier tier, ToDoubleFunction<String> price) {
      int top = f.forms().indexOf(f.form(tier));
      for (int i = top; i >= 0; i--) {
         MiningItems.Form form = f.forms().get(i);
         double p = price.applyAsDouble(form.id());
         if (p > 0.0 && Double.isFinite(p)) {
            return p / form.raw();
         }
      }

      return Double.NaN;
   }

   /** total per hour of mining, NaN until 10 s of mining. */
   public static double perHour(double total, long activeMs) {
      return activeMs < 10000L ? Double.NaN : total * 3600000.0 / activeMs;
   }
}
