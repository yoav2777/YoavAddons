package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.ModPrefs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Drag-to-place for the trade-window button: a mock trade screen with the real button. The button follows the mouse and
 * stays exactly where it is dropped; the nearest corner is only the anchor its offsets are measured from. Every move is
 * applied through ModSettings.live, so the position is kept even if the release event never arrives.
 */
final class PlaceButtonScreen extends Screen {
   private static final int N = 3;
   private static final int TEXT = MoulUi.TEXT_BRIGHT;
   private static final int MUTED = MoulUi.TEXT;
   private final Screen parent;
   private boolean dragging;
   private double grabX;
   private double grabY;
   private int x;
   private int y;

   PlaceButtonScreen(Screen parent) {
      super(Component.literal("Place the trade button"));
      this.parent = parent;
   }

   private int bw() {
      return this.font.width(TradeAhUi.label(N)) + 12;
   }

   private void snap() {
      int[] xy = ModSettings.get().buttonXY(this.width, this.height, this.bw(), TradeAhUi.H);
      this.x = xy[0];
      this.y = xy[1];
   }

   /** Anchors the button to the corner nearest to (x, y) and applies it. */
   void placeAt(int nx, int ny) {
      ModSettings.live(ModSettings.get().placedAt(nx, ny, this.bw(), TradeAhUi.H, this.width, this.height));
      this.snap();
   }

   /** Developer harness: the button's current top-left plus 1 when a drag is in progress. */
   int[] devXY() {
      return new int[]{this.x, this.y, this.dragging ? 1 : 0};
   }

   protected void init() {
      this.snap();
      int cx = this.width / 2;
      int cy = this.height / 2;
      this.addRenderableWidget(new MoulUi.Button(cx - 62, cy + 30, 60, 18, "Done", this::onClose));
      this.addRenderableWidget(new MoulUi.Button(cx + 2, cy + 30, 60, 18, "Reset", () -> {
         ModPrefs d = ModPrefs.DEFAULT;
         ModSettings.live(ModSettings.get().withPosition(d.corner(), d.offsetX(), d.offsetY()));
         this.snap();
      }));
   }

   private boolean onButton(double mx, double my) {
      return mx >= this.x && mx <= this.x + this.bw() && my >= this.y && my <= this.y + TradeAhUi.H;
   }

   /** Moves the button so the point it was grabbed at stays under the mouse, and remembers where it is now. */
   private void dragTo(double mx, double my) {
      int[] xy = ModPrefs.dragXY(mx, my, this.grabX, this.grabY, this.bw(), TradeAhUi.H, this.width, this.height);
      this.placeAt(xy[0], xy[1]);
   }

   // The button is picked up before the widgets below it get the click, so it can still be dragged away after being
   // dropped on top of Done/Reset (before, the press went to the widget and the screen closed instead).
   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (event.button() == 0 && this.onButton(event.x(), event.y())) {
         this.dragging = true;
         this.grabX = event.x() - this.x;
         this.grabY = event.y() - this.y;
         return true;
      } else {
         return super.mouseClicked(event, doubleClick);
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      if (this.dragging) {
         this.dragTo(event.x(), event.y());
         return true;
      } else {
         return super.mouseDragged(event, dx, dy);
      }
   }

   /**
    * Minecraft only sends mouseDragged while the window has focus and it saw the press itself; mouseMoved arrives for every
    * movement, so the button follows the mouse from here too. Without this, a drag that started (or continued) after the
    * window lost and regained focus left the button sitting still.
    */
   public void mouseMoved(double mx, double my) {
      if (this.dragging) {
         this.dragTo(mx, my);
      }

      super.mouseMoved(mx, my);
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.dragging) {
         this.dragging = false;
         this.dragTo(event.x(), event.y());
         return true;
      } else {
         return super.mouseReleased(event);
      }
   }

   public boolean keyPressed(KeyEvent event) {
      int dx = event.key() == GLFW.GLFW_KEY_LEFT ? -1 : event.key() == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
      int dy = event.key() == GLFW.GLFW_KEY_UP ? -1 : event.key() == GLFW.GLFW_KEY_DOWN ? 1 : 0;
      if (dx != 0 || dy != 0) {
         this.placeAt(this.x + dx, this.y + dy);
         return true;
      } else {
         return super.keyPressed(event);
      }
   }

   public void removed() {
      ModSettings.set(ModSettings.get());
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      g.fill(0, 0, this.width, this.height, 0xA0000000);
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      int cx = this.width / 2;
      int cy = this.height / 2;
      int w = SettingsScreen.TRADE_W;
      int h = SettingsScreen.TRADE_H;
      int x0 = cx - w / 2;
      int y0 = cy - h / 2;
      MoulUi.floating(g, x0, y0, w, h, true);
      // Faint slot grid (6 chest rows at y 18, player inventory at 140 + hotbar at 198, 18 px slots from x 8) so you see what the button would cover.
      for (int r = 0; r < 10; r++) {
         int sy = y0 + (r < 6 ? 18 + r * 18 : (r < 9 ? 140 + (r - 6) * 18 : 198));
         for (int c = 0; c < 9; c++) {
            MoulUi.inner(g, x0 + 7 + c * 18, sy - 1, 18, 18);
         }
      }

      g.centeredText(this.font, "Trade window", cx, y0 + 6, MUTED);
      g.centeredText(this.font, "Drag the blue button", cx, cy - 30, TEXT);
      g.centeredText(this.font, "Arrow keys: 1 pixel, Esc keeps it", cx, cy - 18, MUTED);
      ModPrefs m = ModSettings.get();
      g.centeredText(this.font, m.corner().label + ", " + m.offsetX() + " / " + m.offsetY() + " px in", cx, cy + 4, SettingsScreen.ACCENT);
      super.extractRenderState(g, mouseX, mouseY, a);
      TradeAhUi.drawButton(this.font, g, N, this.x, this.y, this.dragging || this.onButton(mouseX, mouseY));
   }
}
