package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.AhLink;
import com.yoav3577.bazaaranalyzer.core.ItemMods;
import com.yoav3577.bazaaranalyzer.core.ModPrefs;
import com.yoav3577.bazaaranalyzer.core.TradeWindow;
import com.yoav3577.bazaaranalyzer.core.Worth;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

final class TradeAhUi {
   static final int H = 16;
   private static final int WORTH_H = 12;
   /** Last drawn worth / "!" boxes {x, y, w, h} (null = not drawn), for the dev harness hover shots. */
   static volatile int[] devWorthBox;
   static volatile int[] devBangBox;

   private TradeAhUi() {
   }

   static void init() {
      ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
         if (screen instanceof AbstractContainerScreen<?> cs && TradeWindow.isTradeTitle(cs.getTitle().getString())) {
            ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> draw(mc, s, g, mouseX, mouseY));
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !click(mc, cs, event));
         }
      });
   }

   static String label(int n) {
      return "Open " + n + " AH item" + (n == 1 ? "" : "s") + " on website";
   }

   /** The button rectangle {x, y, w, h} for n items on a screen of the given size (see ModPrefs.buttonXY). */
   static int[] rect(Font font, int n, int screenW, int screenH) {
      int w = font.width(label(n)) + 12;
      int[] xy = ModSettings.get().buttonXY(screenW, screenH, w, H);
      return new int[]{xy[0], xy[1], w, H};
   }

   /** Draws the button; also used by the settings preview and the drag-to-place screen. */
   static void drawButton(Font font, GuiGraphicsExtractor g, int n, int x, int y, boolean hot) {
      String text = label(n);
      drawBox(font, g, text, new int[]{x, y, font.width(text) + 12, H}, hot);
   }

   private static boolean inside(int[] r, double x, double y) {
      return r != null && x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
   }

   /** Shorter button text, used when the long one doesn't fit next to the trade window. */
   private static String shortLabel(int n) {
      return "Website (" + n + ")";
   }

   /** Where the trade window itself is drawn {x, y, w, h}: a centered chest screen (176 wide, 114 + 18 per chest row high). */
   static int[] containerRect(Screen s) {
      int rows = 6;
      if (s instanceof AbstractContainerScreen<?> cs) {
         int chest = cs.getMenu().slots.size() - 36;
         if (chest > 0 && chest % 9 == 0) {
            rows = chest / 9;
         }
      }

      int w = SettingsScreen.TRADE_W;
      int h = 114 + 18 * rows;
      return new int[]{(s.width - w) / 2, (s.height - h) / 2, w, h};
   }

   /**
    * Where everything goes {x, y, w, h} (null = not drawn): one column of the button, the worth box and a row of the
    * craft "!" and inflation badges, at the button's corner. The column never covers the trade window: when it would, it
    * moves beside, above or below the window (with the short button text if the long one doesn't fit anywhere).
    */
   record Layout(String label, int[] button, int[] worth, int[] bang, int[] warn, int[] container) {
   }

   static Layout layout(Font font, Screen s, int n, TradeWorth.View v) {
      int[] c = containerRect(s);
      Layout best = null;
      for (String label : List.of(label(n), shortLabel(n))) {
         Layout l = layout(font, s, n, v, label, c);
         if (best == null || overlap(l, c) < overlap(best, c)) {
            best = l;
         }

         if (overlap(l, c) == 0) {
            return l;
         }
      }

      return best;
   }

   private static Layout layout(Font font, Screen s, int n, TradeWorth.View v, String label, int[] c) {
      int bw = n == 0 ? 0 : font.width(label) + 12;
      int ww = v == null ? 0 : font.width(worthText(v)) + 8;
      int bangW = v == null || v.craft().isEmpty() ? 0 : font.width("!") + 8;
      int warnW = v == null || v.warnings().isEmpty() ? 0 : font.width(warnText(v)) + 8;
      int badgesW = bangW + warnW + (bangW != 0 && warnW != 0 ? 2 : 0);
      int colW = Math.max(bw, Math.max(ww, badgesW));
      int colH = 0;
      for (int h : new int[]{bw == 0 ? 0 : H, ww == 0 ? 0 : WORTH_H, badgesW == 0 ? 0 : WORTH_H}) {
         colH += h == 0 ? 0 : h + (colH == 0 ? 0 : 2);
      }

      ModPrefs p = ModSettings.get();
      int[] xy = p.buttonXY(s.width, s.height, Math.max(1, colW), Math.max(1, colH));
      int[] col = {xy[0], xy[1], colW, colH};
      if (overlaps(col, c)) {
         boolean right = p.corner().right();
         int left = c[0] - 3 - colW;
         int rightX = c[0] + c[2] + 3;
         int above = c[1] - 3 - colH;
         int below = c[1] + c[3] + 3;
         int[][] tries = {
            {right ? rightX : left, xy[1]}, {right ? left : rightX, xy[1]}, {xy[0], p.corner().bottom() ? below : above}, {xy[0], p.corner().bottom() ? above : below}
         };
         for (int[] t : tries) {
            if (t[0] >= 0 && t[1] >= 0 && t[0] + colW <= s.width && t[1] + colH <= s.height) {
               col = new int[]{t[0], t[1], colW, colH};
               break;
            }
         }
      }

      // Stack from the corner's edge: top corners put the button on top, bottom corners at the bottom.
      boolean up = p.corner().bottom();
      boolean alignRight = p.corner().right();
      int y = up ? col[1] + colH : col[1];
      int[] button = null;
      int[] worth = null;
      int[] bang = null;
      int[] warn = null;
      for (int row = 0; row < 3; row++) {
         int w = row == 0 ? bw : row == 1 ? ww : badgesW;
         int h = row == 0 ? H : WORTH_H;
         if (w == 0) {
            continue;
         }

         int ry = up ? y - h : y;
         int rx = alignRight ? col[0] + colW - w : col[0];
         if (row == 0) {
            button = new int[]{rx, ry, w, h};
         } else if (row == 1) {
            worth = new int[]{rx, ry, w, h};
         } else {
            if (bangW != 0) {
               bang = new int[]{rx, ry, bangW, h};
            }

            if (warnW != 0) {
               warn = new int[]{rx + (bangW == 0 ? 0 : bangW + 2), ry, warnW, h};
            }
         }

         y = up ? ry - 2 : ry + h + 2;
      }

      return new Layout(label, button, worth, bang, warn, c);
   }

   private static boolean overlaps(int[] a, int[] b) {
      return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
   }

   /** Overlapping area of the layout's boxes with the trade window. */
   private static int overlap(Layout l, int[] c) {
      int sum = 0;
      for (int[] r : new int[][]{l.button(), l.worth(), l.bang(), l.warn()}) {
         if (r != null) {
            int w = Math.min(r[0] + r[2], c[0] + c[2]) - Math.max(r[0], c[0]);
            int h = Math.min(r[1] + r[3], c[1] + c[3]) - Math.max(r[1], c[1]);
            sum += Math.max(0, w) * Math.max(0, h);
         }
      }

      return sum;
   }

   private static String worthText(TradeWorth.View v) {
      return v.loading() ? "Worth: …" : "Worth: " + Worth.coins(v.total()) + (v.failed() > 0 ? " (partial)" : "");
   }

   private static String warnText(TradeWorth.View v) {
      return v.warnings().size() == 1 ? "⚠" : "⚠ " + v.warnings().size();
   }

   private static void draw(Minecraft mc, Screen s, GuiGraphicsExtractor g, int mouseX, int mouseY) {
      if (ModSettings.get().tradeButton()) {
         int n = TradeTracker.theirAhCount();
         TradeWorth.View v = TradeWorth.view();
         Layout l = layout(mc.font, s, n, v);
         if (l.button() != null) {
            drawBox(mc.font, g, l.label(), l.button(), inside(l.button(), mouseX, mouseY));
         }

         if (v != null) {
            drawWorth(mc.font, g, v, l, s.width, s.height, mouseX, mouseY);
         }
      }
   }

   private static void drawBox(Font font, GuiGraphicsExtractor g, String text, int[] r, boolean hot) {
      g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hot ? 0xFF2B4F86 : 0xFF1B3358);
      g.outline(r[0], r[1], r[2], r[3], 0xFF5B7BB0);
      g.text(font, text, r[0] + 6, r[1] + 4, -1);
   }

   /** Their items' worth, the craft "!" and the inflation "⚠" badges; the details are in their hovers. */
   private static void drawWorth(Font font, GuiGraphicsExtractor g, TradeWorth.View v, Layout l, int screenW, int screenH, int mouseX, int mouseY) {
      int[] wb = l.worth();
      devWorthBox = wb;
      g.fill(wb[0], wb[1], wb[0] + wb[2], wb[1] + wb[3], 0xE0101722);
      g.outline(wb[0], wb[1], wb[2], wb[3], 0xFF3A4A66);
      g.text(font, worthText(v), wb[0] + 4, wb[1] + 2, v.failed() > 0 ? 0xFFFFB86B : 0xFFD8DEE9);
      int[] bang = l.bang();
      if (bang != null) {
         devBangBox = bang;
         g.fill(bang[0], bang[1], bang[0] + bang[2], bang[1] + bang[3], 0xE05A3A00);
         g.outline(bang[0], bang[1], bang[2], bang[3], 0xFFFFB020);
         g.text(font, "!", bang[0] + 4, bang[1] + 2, 0xFFFFD060);
      }

      int[] warn = l.warn();
      if (warn != null) {
         g.fill(warn[0], warn[1], warn[0] + warn[2], warn[1] + warn[3], 0xE03A1010);
         g.outline(warn[0], warn[1], warn[2], warn[3], 0xFFB04040);
         g.text(font, warnText(v), warn[0] + 4, warn[1] + 2, 0xFFFFCC55);
      }

      if (inside(wb, mouseX, mouseY) && !v.lines().isEmpty()) {
         List<String> lines = new ArrayList<>(List.of("Their items"));
         List<Integer> colors = new ArrayList<>(List.of(0xFF7C8699));
         for (TradeWorth.Line line : v.lines()) {
            lines.add(line.text());
            colors.add(Double.isNaN(line.value()) ? 0xFFFF6B6B : 0xFFD8DEE9);
         }

         tooltip(font, g, lines, colors, mouseX, mouseY, screenW, screenH);
      } else if (inside(bang, mouseX, mouseY)) {
         List<String> lines = new ArrayList<>(List.of("Cheaper to craft than it is worth"));
         List<Integer> colors = new ArrayList<>(List.of(0xFF7C8699));
         for (String line : v.craft()) {
            lines.add(line);
            colors.add(0xFFD8DEE9);
         }

         tooltip(font, g, lines, colors, mouseX, mouseY, screenW, screenH);
      } else if (inside(warn, mouseX, mouseY)) {
         List<String> lines = new ArrayList<>(List.of("Price looks inflated"));
         List<Integer> colors = new ArrayList<>(List.of(0xFF7C8699));
         for (String warning : v.warnings()) {
            for (String line : wrap(font, warning.startsWith("⚠ ") ? warning.substring(2) : warning, Math.max(80, Math.min(260, screenW - 30)))) {
               lines.add(line);
               colors.add(0xFFFFCC55);
            }
         }

         tooltip(font, g, lines, colors, mouseX, mouseY, screenW, screenH);
      }
   }

   /**
    * A hover box next to the mouse, kept on the screen (same look as the price graph's hover box). Drawn in a new stratum
    * so nothing drawn earlier shows through; the game's deferred tooltip is already drawn by the time this event runs.
    */
   private static void tooltip(Font font, GuiGraphicsExtractor g, List<String> lines, List<Integer> colors, int mouseX, int mouseY, int screenW, int screenH) {
      int bw = 0;
      for (String l : lines) {
         bw = Math.max(bw, font.width(l));
      }

      bw += 10;
      int bh = 6 + 11 * lines.size();
      int bx = mouseX + 10 + bw > screenW ? Math.max(0, mouseX - 10 - bw) : mouseX + 10;
      int by = Math.max(0, Math.min(screenH - bh, mouseY + 4));
      g.nextStratum();
      g.fill(bx, by, bx + bw, by + bh, 0xF0090B0F);
      g.outline(bx, by, bw, bh, 0xFF222A37);
      for (int i = 0; i < lines.size(); i++) {
         g.text(font, lines.get(i), bx + 5, by + 4 + 11 * i, colors.get(i));
      }
   }

   /** Splits text into lines at most maxW pixels wide (a single long word stays whole). */
   private static List<String> wrap(Font font, String text, int maxW) {
      List<String> out = new ArrayList<>();
      String line = "";

      for (String word : text.split(" ")) {
         String next = line.isEmpty() ? word : line + " " + word;
         if (!line.isEmpty() && font.width(next) > maxW) {
            out.add(line);
            line = word;
         } else {
            line = next;
         }
      }

      if (!line.isEmpty()) {
         out.add(line);
      }

      return out;
   }

   private static boolean click(Minecraft mc, AbstractContainerScreen<?> cs, MouseButtonEvent event) {
      if (event.button() != 0) {
         return false;
      } else {
         int n = TradeTracker.theirAhCount();
         if (n == 0 || !ModSettings.get().tradeButton()) {
            return false;
         } else {
            if (inside(layout(mc.font, cs, n, TradeWorth.view()).button(), event.x(), event.y())) {
               try {
                  AhTabs.open(collect(cs));
               } catch (RuntimeException var6) {
                  BazaarClient.LOG.warn("Could not read the trade items for the website", var6);
               }

               return true;
            } else {
               return false;
            }
         }
      }
   }

   static boolean devClick(Minecraft mc, AbstractContainerScreen<?> cs) {
      int[] r = layout(mc.font, cs, Math.max(1, TradeTracker.theirAhCount()), TradeWorth.view()).button();
      BazaarClient.LOG.info("Dev: trade button at x={} y={} w={} h={} (screen {}x{})", r[0], r[1], r[2], r[3], cs.width, cs.height);
      return click(mc, cs, new MouseButtonEvent(r[0] + r[2] / 2.0, r[1] + r[3] / 2.0, new MouseButtonInfo(0, 0)));
   }

   private static List<ItemMods> collect(AbstractContainerScreen<?> cs) {
      List<Slot> slots = cs.getMenu().slots;
      List<ItemMods> out = new ArrayList<>();
      StringBuilder raw = new StringBuilder("=== raw item data ").append(Instant.now()).append('\n');

      for (int idx : TradeWindow.theirSlotIndexes()) {
         if (idx < slots.size()) {
            ItemStack stack = slots.get(idx).getItem();
            ItemMods m = ItemReader.mods(stack);
            if (m != null && AhLink.isAhItem(m, id -> PriceData.products().contains(id))) {
               out.add(m);
               raw.append(idx).append(" | ").append(m.name()).append(" | ").append(ItemReader.snbt(stack)).append('\n');
            }
         }
      }

      AhTabs.append(raw.toString());
      return out;
   }
}
