package com.yoav3577.bazaaranalyzer.core;

import java.util.List;
import java.util.Locale;

/**
 * In-game graph settings, stored in config/bazaaranalyzer/graph.json. The 0.5 keys (width, height, range) keep their meaning;
 * the other keys are new in 0.6 and default to the 0.5 behaviour when absent.
 */
public record GraphPrefs(int width, int height, String range, boolean rememberRange, Lines lines, boolean grid, int opacity) {
   public static final int MIN_W = 300;
   public static final int MIN_H = 170;
   public static final int MAX_W = 4000;
   public static final int MAX_H = 4000;
   public static final int MIN_OPACITY = 20;
   public static final List<String> RANGES = List.of("day", "week", "month", "year");
   public static final GraphPrefs DEFAULT = new GraphPrefs(460, 260, "day", true, Lines.BOTH, true, 94);

   /** Which price lines the chart draws: BUY = insta-buy (bazaar) / lowest sale (AH), SELL = insta-sell / average sale. */
   public enum Lines {
      BOTH,
      BUY,
      SELL;

      public boolean buy() {
         return this != SELL;
      }

      public boolean sell() {
         return this != BUY;
      }
   }

   public GraphPrefs {
      width = Cfg.clamp(width, MIN_W, MAX_W);
      height = Cfg.clamp(height, MIN_H, MAX_H);
      range = range != null && RANGES.contains(range) ? range : "day";
      lines = lines == null ? Lines.BOTH : lines;
      opacity = Cfg.clamp(opacity, MIN_OPACITY, 100);
   }

   public GraphPrefs withSize(int w, int h) {
      return new GraphPrefs(w, h, range, rememberRange, lines, grid, opacity);
   }

   public GraphPrefs withRange(String r) {
      return new GraphPrefs(width, height, r, rememberRange, lines, grid, opacity);
   }

   public GraphPrefs withRememberRange(boolean v) {
      return new GraphPrefs(width, height, range, v, lines, grid, opacity);
   }

   public GraphPrefs withLines(Lines v) {
      return new GraphPrefs(width, height, range, rememberRange, v, grid, opacity);
   }

   public GraphPrefs withGrid(boolean v) {
      return new GraphPrefs(width, height, range, rememberRange, lines, v, opacity);
   }

   public GraphPrefs withOpacity(int v) {
      return new GraphPrefs(width, height, range, rememberRange, lines, grid, v);
   }

   /** Panel background colour (0xAARRGGBB) for the given RGB with this opacity. */
   public int panelColor(int rgb) {
      return Math.round(opacity * 2.55F) << 24 | rgb & 0xFFFFFF;
   }

   public static GraphPrefs fromJson(String json) {
      var o = Cfg.object(json);
      Lines lines;
      try {
         lines = Lines.valueOf(Cfg.str(o, "lines", "both").toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         lines = Lines.BOTH;
      }

      return new GraphPrefs(
         Cfg.integer(o, "width", DEFAULT.width),
         Cfg.integer(o, "height", DEFAULT.height),
         Cfg.str(o, "range", DEFAULT.range),
         Cfg.bool(o, "rememberRange", DEFAULT.rememberRange),
         lines,
         Cfg.bool(o, "grid", DEFAULT.grid),
         Cfg.integer(o, "opacity", DEFAULT.opacity)
      );
   }

   public String toJson() {
      return "{\"width\":" + width + ",\"height\":" + height + ",\"range\":\"" + range + "\",\"rememberRange\":" + rememberRange
         + ",\"lines\":\"" + lines.name().toLowerCase(Locale.ROOT) + "\",\"grid\":" + grid + ",\"opacity\":" + opacity + "}\n";
   }
}
