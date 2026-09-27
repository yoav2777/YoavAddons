package com.yoav3577.bazaaranalyzer.core;

import java.util.Locale;

/**
 * Trade-window and website settings, stored in config/bazaaranalyzer/mod-settings.json (new in 0.6; the defaults are the 0.5 behaviour:
 * button at the top-left corner, 6 px in, cancel link on, trades recorded, website found by scanning 47831-47850).
 * fillTime/fillHours: DurationFill types fillHours into the AH Custom Duration sign. priceDebug: PriceDebug adds the worth
 * breakdown to item tooltips.
 */
public record ModPrefs(
   boolean tradeButton, Corner corner, int offsetX, int offsetY, boolean cancelLink, boolean recordTrades, int sitePort, boolean fillTime, int fillHours,
   boolean priceDebug
) {
   /** Stored offsets are only sanity-clamped; buttonXY() keeps the button on screen, so a drag-placed button stays where it was dropped. */
   public static final int MAX_OFFSET = 4000;
   /** The offset sliders go at least this far (further on big GUIs, see SettingsScreen). */
   public static final int SLIDER_OFFSET = 200;
   public static final int FIRST_SITE_PORT = 47831;
   public static final int LAST_SITE_PORT = 47850;
   public static final ModPrefs DEFAULT = new ModPrefs(true, Corner.TOP_LEFT, 6, 6, true, true, 0, true, ModPrefs.MAX_FILL_HOURS, false);
   public static final int MAX_FILL_HOURS = 336; // Hypixel's longest auction: 14 days

   public enum Corner {
      TOP_LEFT("Top left"),
      TOP_RIGHT("Top right"),
      BOTTOM_LEFT("Bottom left"),
      BOTTOM_RIGHT("Bottom right");

      public final String label;

      Corner(String label) {
         this.label = label;
      }

      public boolean right() {
         return this == TOP_RIGHT || this == BOTTOM_RIGHT;
      }

      public boolean bottom() {
         return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
      }

      static Corner of(boolean right, boolean bottom) {
         return bottom ? (right ? BOTTOM_RIGHT : BOTTOM_LEFT) : (right ? TOP_RIGHT : TOP_LEFT);
      }
   }

   public ModPrefs {
      corner = corner == null ? Corner.TOP_LEFT : corner;
      offsetX = Cfg.clamp(offsetX, 0, MAX_OFFSET);
      offsetY = Cfg.clamp(offsetY, 0, MAX_OFFSET);
      sitePort = sitePort >= FIRST_SITE_PORT && sitePort <= LAST_SITE_PORT ? sitePort : 0;
      fillHours = Cfg.clamp(fillHours, 1, MAX_FILL_HOURS);
   }

   /** Top-left x,y of a w*h button on a screenW*screenH screen; always fully on screen when it fits. */
   public int[] buttonXY(int screenW, int screenH, int w, int h) {
      int x = corner.right() ? screenW - w - offsetX : offsetX;
      int y = corner.bottom() ? screenH - h - offsetY : offsetY;
      return new int[]{Cfg.clamp(x, 0, Math.max(0, screenW - w)), Cfg.clamp(y, 0, Math.max(0, screenH - h))};
   }

   /**
    * Where a w*h button lands while it is dragged: the point it was grabbed at (grabX, grabY from its top-left) stays under
    * the mouse, and the button stays on the screen. Clamping never changes the grab point, so the button doesn't drift when
    * the mouse goes past an edge and comes back.
    */
   public static int[] dragXY(double mouseX, double mouseY, double grabX, double grabY, int w, int h, int screenW, int screenH) {
      int x = (int) Math.round(mouseX - grabX);
      int y = (int) Math.round(mouseY - grabY);
      return new int[]{Cfg.clamp(x, 0, Math.max(0, screenW - w)), Cfg.clamp(y, 0, Math.max(0, screenH - h))};
   }

   /** The same settings with the button anchored to the corner nearest to where it was dropped (top-left x,y). */
   public ModPrefs placedAt(int x, int y, int w, int h, int screenW, int screenH) {
      x = Cfg.clamp(x, 0, Math.max(0, screenW - w));
      y = Cfg.clamp(y, 0, Math.max(0, screenH - h));
      boolean right = x + w / 2 > screenW / 2;
      boolean bottom = y + h / 2 > screenH / 2;
      int dx = right ? screenW - w - x : x;
      int dy = bottom ? screenH - h - y : y;
      return new ModPrefs(tradeButton, Corner.of(right, bottom), dx, dy, cancelLink, recordTrades, sitePort, fillTime, fillHours, priceDebug);
   }

   public ModPrefs withPosition(Corner c, int dx, int dy) {
      return new ModPrefs(tradeButton, c, dx, dy, cancelLink, recordTrades, sitePort, fillTime, fillHours, priceDebug);
   }

   public ModPrefs withTradeButton(boolean v) {
      return new ModPrefs(v, corner, offsetX, offsetY, cancelLink, recordTrades, sitePort, fillTime, fillHours, priceDebug);
   }

   public ModPrefs withCancelLink(boolean v) {
      return new ModPrefs(tradeButton, corner, offsetX, offsetY, v, recordTrades, sitePort, fillTime, fillHours, priceDebug);
   }

   public ModPrefs withRecordTrades(boolean v) {
      return new ModPrefs(tradeButton, corner, offsetX, offsetY, cancelLink, v, sitePort, fillTime, fillHours, priceDebug);
   }

   public ModPrefs withSitePort(int v) {
      return new ModPrefs(tradeButton, corner, offsetX, offsetY, cancelLink, recordTrades, v, fillTime, fillHours, priceDebug);
   }

   public ModPrefs(boolean tradeButton, Corner corner, int offsetX, int offsetY, boolean cancelLink, boolean recordTrades, int sitePort, boolean fillTime, int fillHours) {
      this(tradeButton, corner, offsetX, offsetY, cancelLink, recordTrades, sitePort, fillTime, fillHours, false);
   }

   public ModPrefs withPriceDebug(boolean v) {
      return new ModPrefs(tradeButton, corner, offsetX, offsetY, cancelLink, recordTrades, sitePort, fillTime, fillHours, v);
   }

   public ModPrefs withFill(boolean on, int hours) {
      return new ModPrefs(tradeButton, corner, offsetX, offsetY, cancelLink, recordTrades, sitePort, on, hours, priceDebug);
   }

   public static ModPrefs fromJson(String json) {
      var o = Cfg.object(json);
      Corner c;
      try {
         c = Corner.valueOf(Cfg.str(o, "corner", "top_left").toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         c = Corner.TOP_LEFT;
      }

      return new ModPrefs(
         Cfg.bool(o, "tradeButton", DEFAULT.tradeButton),
         c,
         Cfg.integer(o, "offsetX", DEFAULT.offsetX),
         Cfg.integer(o, "offsetY", DEFAULT.offsetY),
         Cfg.bool(o, "cancelLink", DEFAULT.cancelLink),
         Cfg.bool(o, "recordTrades", DEFAULT.recordTrades),
         Cfg.integer(o, "sitePort", DEFAULT.sitePort),
         Cfg.bool(o, "fillTime", DEFAULT.fillTime),
         Cfg.integer(o, "fillHours", DEFAULT.fillHours),
         Cfg.bool(o, "priceDebug", DEFAULT.priceDebug)
      );
   }

   public String toJson() {
      return "{\"tradeButton\":" + tradeButton + ",\"corner\":\"" + corner.name().toLowerCase(Locale.ROOT) + "\",\"offsetX\":" + offsetX
         + ",\"offsetY\":" + offsetY + ",\"cancelLink\":" + cancelLink + ",\"recordTrades\":" + recordTrades + ",\"sitePort\":" + sitePort
         + ",\"fillTime\":" + fillTime + ",\"fillHours\":" + fillHours + ",\"priceDebug\":" + priceDebug + "}\n";
   }
}
