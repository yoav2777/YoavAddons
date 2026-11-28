package com.yoav3577.bazaaranalyzer.core;

import java.util.Locale;

/**
 * Mining tracker settings (config/bazaaranalyzer/mining.json): on/off, the box (shown, position, size, what it shows),
 * which form items are priced as, when the timer pauses, and hiding the sack chat lines.
 */
public record MiningPrefs(boolean enabled, boolean hud, MiningItems.Tier tier, Show show, int lines, int idle, boolean hideSackChat, int x, int y, int scale) {
   public enum Show {
      TOTAL("Total profit"),
      HOURLY("Per hour"),
      BOTH("Both");

      public final String label;

      Show(String label) {
         this.label = label;
      }
   }

   public static final int MAX_LINES = 15;
   public static final int MIN_IDLE = 10;
   public static final int MAX_IDLE = 300;
   public static final MiningPrefs DEFAULT = new MiningPrefs(true, true, MiningItems.Tier.ENCHANTED, Show.BOTH, 6, 60, false, 4, 40, 100);

   public MiningPrefs {
      tier = tier == null ? MiningItems.Tier.ENCHANTED : tier;
      show = show == null ? Show.BOTH : show;
      lines = Cfg.clamp(lines, 0, MAX_LINES);
      idle = Cfg.clamp(idle, MIN_IDLE, MAX_IDLE);
      x = Math.max(0, x);
      y = Math.max(0, y);
      scale = Cfg.clamp(scale, 50, 200);
   }

   public MiningPrefs withEnabled(boolean v) {
      return new MiningPrefs(v, hud, tier, show, lines, idle, hideSackChat, x, y, scale);
   }

   public MiningPrefs withHud(boolean v) {
      return new MiningPrefs(enabled, v, tier, show, lines, idle, hideSackChat, x, y, scale);
   }

   public MiningPrefs withTier(MiningItems.Tier v) {
      return new MiningPrefs(enabled, hud, v, show, lines, idle, hideSackChat, x, y, scale);
   }

   public MiningPrefs withShow(Show v) {
      return new MiningPrefs(enabled, hud, tier, v, lines, idle, hideSackChat, x, y, scale);
   }

   public MiningPrefs withLines(int v) {
      return new MiningPrefs(enabled, hud, tier, show, v, idle, hideSackChat, x, y, scale);
   }

   public MiningPrefs withIdle(int v) {
      return new MiningPrefs(enabled, hud, tier, show, lines, v, hideSackChat, x, y, scale);
   }

   public MiningPrefs withHideSackChat(boolean v) {
      return new MiningPrefs(enabled, hud, tier, show, lines, idle, v, x, y, scale);
   }

   /** Position and size (the move screen). */
   public MiningPrefs withBox(int nx, int ny, int nscale) {
      return new MiningPrefs(enabled, hud, tier, show, lines, idle, hideSackChat, nx, ny, nscale);
   }

   /** The box's top-left on a screen this big, kept fully on screen (w, h = the box's size there). */
   public int[] place(int screenW, int screenH, int w, int h) {
      return new int[]{Math.max(0, Math.min(this.x, screenW - w)), Math.max(0, Math.min(this.y, screenH - h))};
   }

   public static MiningPrefs fromJson(String json) {
      var o = Cfg.object(json);
      MiningPrefs d = DEFAULT;
      return new MiningPrefs(
         Cfg.bool(o, "enabled", d.enabled), Cfg.bool(o, "hud", d.hud), parse(MiningItems.Tier.class, Cfg.str(o, "tier", null), d.tier),
         parse(Show.class, Cfg.str(o, "show", null), d.show), Cfg.integer(o, "lines", d.lines), Cfg.integer(o, "idle", d.idle),
         Cfg.bool(o, "hideSackChat", d.hideSackChat), Cfg.integer(o, "x", d.x), Cfg.integer(o, "y", d.y), Cfg.integer(o, "scale", d.scale)
      );
   }

   private static <E extends Enum<E>> E parse(Class<E> type, String s, E def) {
      try {
         return s == null ? def : Enum.valueOf(type, s.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return def;
      }
   }

   public String toJson() {
      return "{\"enabled\":" + enabled + ",\"hud\":" + hud + ",\"tier\":\"" + tier + "\",\"show\":\"" + show + "\",\"lines\":" + lines
         + ",\"idle\":" + idle + ",\"hideSackChat\":" + hideSackChat + ",\"x\":" + x + ",\"y\":" + y + ",\"scale\":" + scale + "}\n";
   }
}
