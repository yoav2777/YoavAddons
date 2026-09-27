package com.yoav3577.bazaaranalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * The mod's look (settings, graph, button placer): SkyHanni's MoulConfig style. Dark floating panels with a light
 * top-left and dark bottom-right edge, sunken "inner boxes", light grey buttons, red/green toggle switches, sliders with a
 * green knob. Colours are MoulConfig's (RenderUtils.drawFloatingRectDark / drawInnerBox).
 */
final class MoulUi {
   static final int MAIN = 0xF0202026;
   static final int LIGHT = 0xFF303036;
   static final int DARK = 0xFF101016;
   static final int INNER = 0x6008080E;
   static final int INNER_EDGE = 0xFF08080E;
   static final int INNER_EDGE_LIGHT = 0xFF28282E;
   static final int SHADOW = 0x70000000;
   static final int TEXT = 0xFFC0C0C0;
   static final int TEXT_BRIGHT = 0xFFE0E0E0;
   static final int TEXT_DIM = 0xFF8A8A92;
   static final int PURPLE = 0xFFA368EF;
   static final int AQUA = 0xFF55FFFF;
   static final int GREEN = 0xFF2BC02B;
   static final int RED = 0xFFD02828;

   private MoulUi() {
   }

   /** MoulConfig's floating dark rectangle (panels, option cards), with a drop shadow when shadow is set. */
   static void floating(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean shadow) {
      g.fill(x, y, x + 1, y + h, LIGHT);
      g.fill(x + 1, y, x + w, y + 1, LIGHT);
      g.fill(x + w - 1, y + 1, x + w, y + h, DARK);
      g.fill(x + 1, y + h - 1, x + w - 1, y + h, DARK);
      g.fill(x + 1, y + 1, x + w - 1, y + h - 1, MAIN);
      if (shadow) {
         g.fill(x + w, y + 2, x + w + 2, y + h + 2, SHADOW);
         g.fill(x + 2, y + h, x + w, y + h + 2, SHADOW);
      }
   }

   /** A sunken box: dark top-left edge, lighter bottom-right edge, see-through middle. */
   static void inner(GuiGraphicsExtractor g, int x, int y, int w, int h) {
      g.fill(x, y, x + w, y + h, INNER);
      g.fill(x, y, x + 1, y + h, INNER_EDGE);
      g.fill(x, y, x + w, y + 1, INNER_EDGE);
      g.fill(x + w - 1, y, x + w, y + h, INNER_EDGE_LIGHT);
      g.fill(x, y + h - 1, x + w, y + h, INNER_EDGE_LIGHT);
   }

   /** MoulConfig's light grey button with dark text. */
   static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String label, boolean hovered, boolean enabled) {
      int face = !enabled ? 0xFF8A8A8A : hovered ? 0xFFD6D6D6 : 0xFFC0C0C0;
      g.fill(x, y, x + w, y + h, 0xFF000000);
      g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
      g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFEFEFEF);
      g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFEFEFEF);
      g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF6E6E6E);
      g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0xFF6E6E6E);
      String s = fit(font, label, w - 6);
      g.text(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, enabled ? 0xFF202020 : 0xFF505050, false);
   }

   static final int TOGGLE_W = 36;
   static final int TOGGLE_H = 14;

   /** An on/off switch: dark track, red knob on the left (off) or green knob on the right (on); t = knob position 0..1. */
   static void toggle(GuiGraphicsExtractor g, int x, int y, float t) {
      inner(g, x, y, TOGGLE_W, TOGGLE_H);
      g.fill(x + 1, y + 1, x + TOGGLE_W - 1, y + TOGGLE_H - 1, 0xFF18181E);
      for (int i = 1; i < 3; i++) {
         int tx = x + i * TOGGLE_W / 3;
         g.fill(tx, y + 4, tx + 1, y + TOGGLE_H - 4, 0xFF34343C);
      }

      int kw = 12;
      int kx = x + 1 + Math.round(t * (TOGGLE_W - 2 - kw));
      int color = lerp(RED, GREEN, t);
      g.fill(kx, y + 1, kx + kw, y + TOGGLE_H - 1, color);
      g.fill(kx, y + 1, kx + kw, y + 2, brighten(color));
      g.fill(kx, y + 1, kx + 1, y + TOGGLE_H - 1, brighten(color));
      g.fill(kx, y + TOGGLE_H - 2, kx + kw, y + TOGGLE_H - 1, darken(color));
      g.fill(kx + kw - 1, y + 1, kx + kw, y + TOGGLE_H - 1, darken(color));
   }

   /** A slider track w wide with a green knob at frac (0..1). */
   static void slider(GuiGraphicsExtractor g, int x, int y, int w, double frac, boolean hot) {
      int cy = y + 7;
      g.fill(x, cy - 2, x + w, cy + 2, 0xFF000000);
      g.fill(x + 1, cy - 1, x + w - 1, cy + 1, 0xFF3A3A42);
      for (int i = 1; i < 4; i++) {
         int tx = x + i * w / 4;
         g.fill(tx, cy - 1, tx + 1, cy + 1, 0xFF5A5A64);
      }

      int kx = x + (int) Math.round(Math.max(0.0, Math.min(1.0, frac)) * (w - 4));
      int k = hot ? 0xFF4CE04C : GREEN;
      g.fill(kx, y, kx + 4, y + 14, 0xFF000000);
      g.fill(kx + 1, y + 1, kx + 3, y + 13, k);
      g.fill(kx + 1, y + 1, kx + 2, y + 13, brighten(k));
   }

   /** A black box with a light border holding a value (slider numbers, text fields). */
   static void valueBox(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String text, boolean focused) {
      g.fill(x, y, x + w, y + h, focused ? 0xFFFFFFFF : 0xFFA0A0A0);
      g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000);
      if (text != null) {
         String s = fit(font, text, w - 4);
         g.text(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
      }
   }

   /** A closed dropdown: dark box, the value, and a small arrow. */
   static void dropdown(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String value, boolean hot) {
      g.fill(x, y, x + w, y + h, hot ? 0xFF6A6A74 : 0xFF46464E);
      g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF1C1C22);
      String s = fit(font, value, w - 16);
      g.text(font, s, x + 5, y + (h - 8) / 2, TEXT_BRIGHT, false);
      int ax = x + w - 10;
      int ay = y + h / 2 - 1;
      for (int i = 0; i < 3; i++) {
         g.fill(ax + i, ay + i, ax + 5 - i, ay + i + 1, TEXT);
      }
   }

   /** The open list of a dropdown; hovered = index under the mouse or -1. */
   static void dropdownList(GuiGraphicsExtractor g, Font font, int x, int y, int w, List<String> items, int selected, int hovered) {
      int h = items.size() * 12 + 2;
      g.fill(x, y, x + w, y + h, 0xFF6A6A74);
      g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF141418);
      for (int i = 0; i < items.size(); i++) {
         int ry = y + 1 + i * 12;
         if (i == hovered) {
            g.fill(x + 1, ry, x + w - 1, ry + 12, 0xFF2E2E38);
         }

         g.text(font, fit(font, items.get(i), w - 10), x + 5, ry + 2, i == selected ? AQUA : TEXT_BRIGHT, false);
      }
   }

   /** A thin scrollbar: track + thumb for a view of `view` px over `content` px scrolled by `scroll`. */
   static void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int view, int content, int scroll) {
      inner(g, x, y, 6, h);
      if (content <= view) {
         return;
      }

      int th = Math.max(12, h * view / content);
      int ty = y + (int) ((long) (h - th) * scroll / Math.max(1, content - view));
      g.fill(x + 1, ty, x + 5, ty + th, 0xFF5A5A66);
      g.fill(x + 1, ty, x + 2, ty + th, 0xFF7A7A88);
   }

   /** A magnifying glass icon (13 x 13). */
   static void magnifier(GuiGraphicsExtractor g, int x, int y, boolean hot) {
      int c = hot ? 0xFFFFFFFF : 0xFFB8C8D8;
      g.outline(x + 1, y + 1, 8, 8, c);
      g.fill(x + 3, y + 3, x + 7, y + 7, 0x6088C8FF);
      for (int i = 0; i < 4; i++) {
         g.fill(x + 8 + i, y + 8 + i, x + 10 + i, y + 10 + i, 0xFFB08040);
      }
   }

   /** Text cut to maxW pixels with "..." when too long. */
   static String fit(Font font, String s, int maxW) {
      if (s == null || font.width(s) <= maxW) {
         return s == null ? "" : s;
      }

      String out = s;
      while (!out.isEmpty() && font.width(out + "...") > maxW) {
         out = out.substring(0, out.length() - 1);
      }

      return out + "...";
   }

   /** Word-wraps plain text to lines of at most maxW pixels. */
   static List<String> wrap(Font font, String text, int maxW) {
      List<String> out = new ArrayList<>();
      for (String para : text.split("\n")) {
         StringBuilder line = new StringBuilder();
         for (String w : para.split(" ")) {
            String next = line.length() == 0 ? w : line + " " + w;
            if (line.length() > 0 && font.width(next) > maxW) {
               out.add(line.toString());
               line = new StringBuilder(w);
            } else {
               line = new StringBuilder(next);
            }
         }

         out.add(line.toString());
      }

      return out;
   }

   /** A text box styled like MoulConfig's (drawn by valueBox behind it): no vanilla border, white text. */
   static EditBox textBox(Font font, String hint) {
      EditBox box = new EditBox(font, 0, 0, 100, 14, Component.literal(hint));
      box.setBordered(false);
      box.setTextColor(0xFFFFFFFF);
      box.setHint(Component.literal(hint));
      return box;
   }

   static int lerp(int a, int b, float t) {
      t = Math.max(0f, Math.min(1f, t));
      int r = 0xFF000000;
      for (int s = 0; s < 24; s += 8) {
         int ca = a >> s & 0xFF;
         int cb = b >> s & 0xFF;
         r |= Math.round(ca + (cb - ca) * t) << s;
      }

      return r;
   }

   static int brighten(int c) {
      return lerp(c, 0xFFFFFFFF, 0.35f);
   }

   static int darken(int c) {
      return lerp(c, 0xFF000000, 0.4f);
   }

   /** A vanilla-compatible button widget drawn the MoulConfig way (for screens that keep vanilla widgets). */
   static final class Button extends AbstractButton {
      private final Runnable action;
      private final Supplier<String> label;
      private java.util.function.BooleanSupplier selected = () -> false;

      Button(int x, int y, int w, int h, Supplier<String> label, Runnable action) {
         super(x, y, w, h, Component.literal(label.get()));
         this.label = label;
         this.action = action;
      }

      /** Drawn pressed-in with aqua text while this is true (e.g. the graph's current range). */
      Button selected(java.util.function.BooleanSupplier s) {
         this.selected = s;
         return this;
      }

      Button(int x, int y, int w, int h, String label, Runnable action) {
         this(x, y, w, h, () -> label, action);
      }

      @Override
      public void onPress(InputWithModifiers input) {
         this.action.run();
      }

      @Override
      protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
         Font font = net.minecraft.client.Minecraft.getInstance().font;
         if (this.selected.getAsBoolean()) {
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            g.fill(x, y, x + w, y + h, 0xFF000000);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF2A2A32);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF141418);
            String s = fit(font, this.label.get(), w - 6);
            g.text(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, AQUA, true);
         } else {
            button(g, font, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.label.get(), this.isHovered(), this.active);
         }
      }

      @Override
      protected void updateWidgetNarration(NarrationElementOutput out) {
      }
   }
}
