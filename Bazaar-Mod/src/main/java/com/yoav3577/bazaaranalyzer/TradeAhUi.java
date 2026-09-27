package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.AhLink;
import com.yoav3577.bazaaranalyzer.core.ItemMods;
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
      int w = font.width(text) + 12;
      g.fill(x, y, x + w, y + H, hot ? 0xFF2B4F86 : 0xFF1B3358);
      g.outline(x, y, w, H, 0xFF5B7BB0);
      g.text(font, text, x + 6, y + 4, -1);
   }

   private static boolean inside(int[] r, double x, double y) {
      return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
   }

   private static void draw(Minecraft mc, Screen s, GuiGraphicsExtractor g, int mouseX, int mouseY) {
      if (ModSettings.get().tradeButton()) {
         int n = TradeTracker.theirAhCount();
         int[] r = rect(mc.font, Math.max(1, n), s.width, s.height);
         if (n != 0) {
            drawButton(mc.font, g, n, r[0], r[1], inside(r, mouseX, mouseY));
         }

         drawWorth(mc.font, g, TradeWorth.view(), r, s.width, s.height, mouseX, mouseY);
      }
   }

   /** Under the button (above it when it sits at the bottom): their items' worth, the craft "!" and inflation warnings, with hovers. */
   private static void drawWorth(Font font, GuiGraphicsExtractor g, TradeWorth.View v, int[] r, int screenW, int screenH, int mouseX, int mouseY) {
      if (v == null) {
         return;
      }

      String text = v.loading() ? "Worth: …" : "Worth: " + Worth.coins(v.total()) + (v.failed() > 0 ? " (partial)" : "");
      int w = font.width(text) + 8;
      int bangW = v.craft().isEmpty() ? 0 : font.width("!") + 8;
      List<List<String>> warnings = new ArrayList<>();
      int h = WORTH_H;

      for (String warning : v.warnings()) {
         List<String> lines = wrap(font, warning, Math.max(80, screenW - 16));
         warnings.add(lines);
         h += 2 + 4 + 10 * lines.size();
      }

      int y = r[1] + r[3] + 2;
      if (y + h > screenH) {
         y = Math.max(0, r[1] - 2 - h);
      }

      int x = clampX(r[0], w + (bangW == 0 ? 0 : bangW + 2), screenW);
      int[] worthBox = {x, y, w, WORTH_H};
      devWorthBox = worthBox;
      g.fill(x, y, x + w, y + WORTH_H, 0xE0101722);
      g.outline(x, y, w, WORTH_H, 0xFF3A4A66);
      g.text(font, text, x + 4, y + 2, v.failed() > 0 ? 0xFFFFB86B : 0xFFD8DEE9);
      int[] bangBox = null;
      if (bangW != 0) {
         bangBox = new int[]{x + w + 2, y, bangW, WORTH_H};
         devBangBox = bangBox;
         g.fill(bangBox[0], y, bangBox[0] + bangW, y + WORTH_H, 0xE05A3A00);
         g.outline(bangBox[0], y, bangW, WORTH_H, 0xFFFFB020);
         g.text(font, "!", bangBox[0] + 4, y + 2, 0xFFFFD060);
      }

      int wy = y + WORTH_H + 2;
      for (List<String> lines : warnings) {
         int bw = 0;
         for (String l : lines) {
            bw = Math.max(bw, font.width(l));
         }

         bw += 8;
         int bh = 4 + 10 * lines.size();
         int bx = clampX(r[0], bw, screenW);
         g.fill(bx, wy, bx + bw, wy + bh, 0xE03A1010);
         g.outline(bx, wy, bw, bh, 0xFFB04040);
         for (int i = 0; i < lines.size(); i++) {
            g.text(font, lines.get(i), bx + 4, wy + 3 + 10 * i, 0xFFFFCC55);
         }

         wy += bh + 2;
      }

      if (inside(worthBox, mouseX, mouseY) && !v.lines().isEmpty()) {
         List<String> lines = new ArrayList<>(List.of("Their items"));
         List<Integer> colors = new ArrayList<>(List.of(0xFF7C8699));
         for (TradeWorth.Line l : v.lines()) {
            lines.add(l.text());
            colors.add(Double.isNaN(l.value()) ? 0xFFFF6B6B : 0xFFD8DEE9);
         }

         tooltip(font, g, lines, colors, mouseX, mouseY, screenW, screenH);
      } else if (bangBox != null && inside(bangBox, mouseX, mouseY)) {
         List<String> lines = new ArrayList<>(List.of("Cheaper to craft than it is worth"));
         List<Integer> colors = new ArrayList<>(List.of(0xFF7C8699));
         for (String l : v.craft()) {
            lines.add(l);
            colors.add(0xFFD8DEE9);
         }

         tooltip(font, g, lines, colors, mouseX, mouseY, screenW, screenH);
      }
   }

   private static int clampX(int x, int w, int screenW) {
      return Math.max(0, Math.min(x, screenW - w));
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
            if (inside(rect(mc.font, n, cs.width, cs.height), event.x(), event.y())) {
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
      int[] r = rect(mc.font, Math.max(1, TradeTracker.theirAhCount()), cs.width, cs.height);
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
