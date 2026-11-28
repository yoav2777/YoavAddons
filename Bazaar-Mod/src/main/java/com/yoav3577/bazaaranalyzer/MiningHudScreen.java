package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.MiningPrefs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Drag the mining box where you want it; the mouse wheel over it changes its size. Esc keeps it, Reset puts it back. */
final class MiningHudScreen extends Screen {
   private final Screen parent;
   private boolean dragging;
   private double grabX;
   private double grabY;
   /** The box as drawn last frame: x, y, w, h. */
   private int[] box = {0, 0, 0, 0};

   MiningHudScreen(Screen parent) {
      super(Component.literal("Move the mining box"));
      this.parent = parent;
   }

   protected void init() {
      int cx = this.width / 2;
      int cy = this.height / 2;
      this.addRenderableWidget(new MoulUi.Button(cx - 62, cy + 20, 60, 18, "Done", this::onClose));
      this.addRenderableWidget(new MoulUi.Button(cx + 2, cy + 20, 60, 18, "Reset", () -> {
         MiningPrefs d = MiningPrefs.DEFAULT;
         MiningSettings.live(MiningSettings.get().withBox(d.x(), d.y(), d.scale()));
      }));
   }

   private boolean onBox(double mx, double my) {
      return mx >= this.box[0] && mx <= this.box[0] + this.box[2] && my >= this.box[1] && my <= this.box[1] + this.box[3];
   }

   private void dragTo(double mx, double my) {
      int nx = (int) Math.round(mx - this.grabX);
      int ny = (int) Math.round(my - this.grabY);
      nx = Math.max(0, Math.min(nx, this.width - this.box[2]));
      ny = Math.max(0, Math.min(ny, this.height - this.box[3]));
      MiningSettings.live(MiningSettings.get().withBox(nx, ny, MiningSettings.get().scale()));
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (event.button() == 0 && this.onBox(event.x(), event.y())) {
         this.dragging = true;
         this.grabX = event.x() - this.box[0];
         this.grabY = event.y() - this.box[1];
         return true;
      }

      return super.mouseClicked(event, doubleClick);
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      if (this.dragging) {
         this.dragTo(event.x(), event.y());
         return true;
      }

      return super.mouseDragged(event, dx, dy);
   }

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
      }

      return super.mouseReleased(event);
   }

   public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
      if (this.onBox(mx, my) && scrollY != 0.0) {
         MiningPrefs p = MiningSettings.get();
         MiningSettings.live(p.withBox(p.x(), p.y(), p.scale() + (scrollY > 0 ? 10 : -10)));
         return true;
      }

      return super.mouseScrolled(mx, my, scrollX, scrollY);
   }

   public boolean keyPressed(KeyEvent event) {
      int dx = event.key() == GLFW.GLFW_KEY_LEFT ? -1 : event.key() == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
      int dy = event.key() == GLFW.GLFW_KEY_UP ? -1 : event.key() == GLFW.GLFW_KEY_DOWN ? 1 : 0;
      if (dx != 0 || dy != 0) {
         MiningSettings.live(MiningSettings.get().withBox(this.box[0] + dx, this.box[1] + dy, MiningSettings.get().scale()));
         return true;
      }

      return super.keyPressed(event);
   }

   public void removed() {
      MiningSettings.set(MiningSettings.get());
   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      g.fill(0, 0, this.width, this.height, 0x70000000);
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      int cx = this.width / 2;
      int cy = this.height / 2;
      g.centeredText(this.font, "Drag the mining box", cx, cy - 30, MoulUi.TEXT_BRIGHT);
      g.centeredText(this.font, "Mouse wheel on it: size (" + MiningSettings.get().scale() + "%)", cx, cy - 18, MoulUi.TEXT);
      g.centeredText(this.font, "Arrow keys: 1 pixel, Esc keeps it", cx, cy - 6, MoulUi.TEXT);
      super.extractRenderState(g, mouseX, mouseY, a);
      this.box = MiningTracker.draw(g, this.font, MiningTracker.rows(MiningSettings.get(), true, false), MiningSettings.get(), -1, -1);
      int color = this.dragging || this.onBox(mouseX, mouseY) ? 0xFF55FFFF : 0x80FFFFFF;
      int x = this.box[0] - 1;
      int y = this.box[1] - 1;
      int w = this.box[2] + 2;
      int h = this.box[3] + 2;
      g.fill(x, y, x + w, y + 1, color);
      g.fill(x, y + h - 1, x + w, y + h, color);
      g.fill(x, y, x + 1, y + h, color);
      g.fill(x + w - 1, y, x + w, y + h, color);
   }
}
