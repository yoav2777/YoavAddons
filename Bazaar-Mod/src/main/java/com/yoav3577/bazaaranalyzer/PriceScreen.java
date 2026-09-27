package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.GraphPrefs;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

final class PriceScreen extends Screen {
   private static final int BUY = 0xFFFBBF24;
   private static final int SELL = 0xFF38BDF8;
   private static final int TEXT = 0xFFD8DEE9;
   private static final int MUTED = 0xFF7C8699;
   private static final int PANEL = MoulUi.MAIN;
   private static final int EDGE_COLOR = 0xFF46464E;
   private static final int EDGE_HOT = 0xFF5B7BB0;
   private static final int GRID = 0xFF1B2230;
   private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault());
   private static final DateTimeFormatter WHEN_DAY = DateTimeFormatter.ofPattern("d MMM yy").withZone(ZoneId.systemDefault());
   private static final int ROW_H = 12;
   private static final long SEARCH_DELAY_MS = 300L;
   private static final String[] RANGES = new String[]{"day", "week", "month", "year"};
   private static final String[] RANGE_LABELS = new String[]{"1D", "1W", "1M", "1Y"};
   private static final int MIN_W = 300;
   private static final int MIN_H = 170;
   private static final int EDGE = 5;
   private static final int CORNER = 12;
   private static final int LEFT = 1;
   private static final int RIGHT = 2;
   private static final int TOP = 4;
   private static final int BOTTOM = 8;
   private static PriceData.Choice rememberedChoice = new PriceData.Choice(PriceData.Kind.BAZAAR, "ENCHANTED_DIAMOND", "Enchanted Diamond");
   private final String startQuery;
   private Screen parent;
   private PriceData.Choice choice;
   private ItemStack hoveredStack;
   private ItemStack icon;
   private String range = GraphSettings.range();
   private String searchText = "";
   private EditBox search;
   private final MoulUi.Button[] rangeButtons = new MoulUi.Button[RANGES.length];
   private MoulUi.Button settingsButton;
   private volatile List<PriceData.Point> points = List.of();
   private volatile String status = "";
   private List<PriceData.Choice> suggestions = List.of();
   private int suggestIdx;   // the suggestion Tab / arrows point at, Enter picks it
   private String suggestFor;
   private int suggestSource = -1;
   private List<PriceData.Choice> suggestAh;
   private volatile List<PriceData.Choice> ahHits = List.of();
   private volatile PriceData.Choice pendingPick;
   private volatile String ahAsked = "";
   private long lastEdit;
   private volatile boolean triedNameSearch;
   private volatile int loadToken;
   private PriceData.Choice loadedChoice;
   private String loadedRange;
   private long lastBazaarRefreshFired;
   private int px;
   private int py;
   private int pw;
   private int ph;
   /** The saved size this screen last took over; when the settings screen changes it, init() applies the new size. */
   private int appliedW;
   private int appliedH;
   private boolean placed;
   private int dragEdges;
   private double dragMx;
   private double dragMy;
   private int dragPx;
   private int dragPy;
   private int dragPw;
   private int dragPh;
   private volatile long viewT0;
   private volatile long viewT1;
   private boolean panning;
   private double panMx;
   private long panT0;
   private long panT1;

   private PriceScreen(PriceData.Choice choice, String startQuery, ItemStack hovered) {
      super(Component.literal("Item prices"));
      this.choice = choice;
      this.startQuery = startQuery;
      this.hoveredStack = hovered;
      this.icon = hovered;
      if (choice != null) {
         this.searchText = choice.name();
      } else if (startQuery != null) {
         this.searchText = startQuery;
      }

      this.ahAsked = this.searchText;
   }

   static PriceData.Choice remembered() {
      return rememberedChoice;
   }

   static void open(Minecraft mc, PriceData.Choice choice, String query, ItemStack hovered) {
      mc.setScreen(new PriceScreen(choice, query, hovered));
   }

   /** Opens the graph from another screen (the settings preview); Esc goes back to it. */
   static void openFrom(Minecraft mc, Screen parent) {
      PriceScreen s = new PriceScreen(rememberedChoice, null, null);
      s.parent = parent;
      mc.setScreen(s);
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   private int panelW() {
      return this.pw;
   }

   private int panelH() {
      return this.ph;
   }

   private int left() {
      return this.px;
   }

   private int top() {
      return this.py;
   }

   private int searchW() {
      return Math.max(90, Math.min(190, this.pw - 16 - RANGES.length * 29 - 19 - 8));
   }

   private int cx0() {
      return this.px + 48;
   }

   private int cx1() {
      return this.px + this.pw - 10;
   }

   private int cy0() {
      return this.py + 46;
   }

   private int cy1() {
      // Leaves room for the date labels (cy1 + 4) above the legend line (ph - 14).
      return this.py + this.ph - 30;
   }

   private boolean inChart(double mx, double my) {
      return mx >= this.cx0() && mx <= this.cx1() && my >= this.cy0() && my <= this.cy1();
   }

   private void clampGeometry() {
      this.pw = Math.max(MIN_W, Math.min(this.pw, Math.max(MIN_W, this.width - 8)));
      this.ph = Math.max(MIN_H, Math.min(this.ph, Math.max(MIN_H, this.height - 8)));
      this.px = Math.max(0, Math.min(this.px, Math.max(0, this.width - this.pw)));
      this.py = Math.max(0, Math.min(this.py, Math.max(0, this.height - this.ph)));
   }

   private void layout() {
      if (this.search != null) {
         // no vanilla border: the MoulConfig style box is drawn behind it (extractRenderState)
         this.search.setX(this.px + 12);
         this.search.setY(this.py + 12);
         this.search.setWidth(this.searchW() - 8);
         this.search.setHeight(10);
         int x = this.px + this.pw - 8;

         for (int i = RANGES.length - 1; i >= 0; i--) {
            x -= 26;
            this.rangeButtons[i].setX(x);
            this.rangeButtons[i].setY(this.py + 8);
            x -= 3;
         }

         this.settingsButton.setX(x - 16);
         this.settingsButton.setY(this.py + 8);
      }
   }

   private int edgeAt(double mx, double my) {
      if (!(mx < this.px - 3) && !(mx > this.px + this.pw + 3) && !(my < this.py - 3) && !(my > this.py + this.ph + 3)) {
         int h = mx <= this.px + CORNER ? LEFT : (mx >= this.px + this.pw - CORNER ? RIGHT : 0);
         int v = my <= this.py + CORNER ? TOP : (my >= this.py + this.ph - CORNER ? BOTTOM : 0);
         if (h != 0 && v != 0) {
            return h | v;
         } else {
            int he = mx <= this.px + EDGE ? LEFT : (mx >= this.px + this.pw - EDGE ? RIGHT : 0);
            int ve = my <= this.py + EDGE ? TOP : (my >= this.py + this.ph - EDGE ? BOTTOM : 0);
            return he | ve;
         }
      } else {
         return 0;
      }
   }

   protected void init() {
      int gw = GraphSettings.width();
      int gh = GraphSettings.height();
      if (this.pw == 0 || gw != this.appliedW || gh != this.appliedH) {
         this.pw = gw;
         this.ph = gh;
         this.appliedW = gw;
         this.appliedH = gh;
      }

      this.clampGeometry();
      if (!this.placed) {
         this.px = (this.width - this.pw) / 2;
         this.py = (this.height - this.ph) / 2;
         this.placed = true;
         this.clampGeometry();
      }

      this.search = MoulUi.textBox(this.font, "Search Bazaar or AH item...");
      this.search.setMaxLength(48);
      this.search.setValue(this.searchText);
      this.search.setResponder(text -> {
         this.searchText = text;
         this.lastEdit = System.currentTimeMillis();
      });
      this.addRenderableWidget(this.search);
      String devQuery = System.getProperty("bazaaranalyzer.dev.query");
      if (devQuery != null && this.choice == null && this.startQuery == null) {
         this.search.setValue(devQuery);
      }

      for (int i = 0; i < RANGES.length; i++) {
         String r = RANGES[i];
         this.rangeButtons[i] = new MoulUi.Button(this.px, this.py, 26, 16, RANGE_LABELS[i], () -> this.setRange(r)).selected(() -> r.equals(this.range));
         this.addRenderableWidget(this.rangeButtons[i]);
      }

      this.settingsButton = new MoulUi.Button(this.px, this.py, 16, 16, "⚙", () -> this.minecraft.setScreen(new SettingsScreen(this)));
      this.settingsButton.setTooltip(Tooltip.create(Component.literal("Yoav Addons settings")));
      this.addRenderableWidget(this.settingsButton);
      this.layout();
      if (this.choice == null) {
         this.setFocused(this.search);
      }

      long now = System.currentTimeMillis();
      if (now - this.lastBazaarRefreshFired > 5000L) {
         this.lastBazaarRefreshFired = now;
         PriceData.async(() -> {
            try {
               PriceData.refreshBazaar();
            } catch (IOException var1x) {
            }
         });
      }

      if (this.choice != null) {
         this.resolveIcon();
         if (!Objects.equals(this.choice, this.loadedChoice)
            || !Objects.equals(this.range, this.loadedRange)
            || this.points.isEmpty() && !"Loading...".equals(this.status)) {
            this.load();
         }
      } else if (this.startQuery != null) {
         this.searchAndPick(this.startQuery);
      }
   }

   private void setRange(String r) {
      this.range = r;
      GraphSettings.rangePicked(r);
      this.resetView();
      if (this.choice != null) {
         this.load();
      }
   }

   private void pick(PriceData.Choice c, boolean keepIcon) {
      this.choice = c;
      rememberedChoice = c;
      this.searchText = c.name();
      this.ahAsked = c.name();
      if (this.search != null) {
         this.search.setValue(this.searchText);
      }

      this.suggestions = List.of();
      if (!keepIcon) {
         this.hoveredStack = null;
      }

      this.resolveIcon();
      this.resetView();
      this.load();
   }

   private void resolveIcon() {
      try {
         this.icon = this.hoveredStack != null ? this.hoveredStack : (this.choice == null ? null : ItemIcons.forId(this.choice.id()));
      } catch (LinkageError | RuntimeException var2) {
         this.icon = null;
      }
   }

   private void load() {
      PriceData.Choice c = this.choice;
      String r = this.range;
      this.loadedChoice = c;
      this.loadedRange = r;
      int token = ++this.loadToken;
      this.status = "Loading...";
      this.points = List.of();
      PriceData.async(() -> {
         try {
            List<PriceData.Point> pts = PriceData.history(c.kind(), c.id(), r);
            if (token != this.loadToken) {
               return;
            }

            this.viewT0 = 0L;
            this.viewT1 = 0L;
            this.points = pts;
            this.status = pts.size() < 2 ? "No price history for this item yet" : "";
            if (pts.size() < 2) {
               this.fallBackToNameSearch(c);
            }
         } catch (Exception var5) {
            if (token != this.loadToken) {
               return;
            }

            this.points = List.of();
            this.status = "Could not load prices (" + (var5.getMessage() == null ? var5.getClass().getSimpleName() : var5.getMessage()) + ")";
            this.fallBackToNameSearch(c);
         }
      });
   }

   private void fallBackToNameSearch(PriceData.Choice tried) {
      if (tried.kind() == PriceData.Kind.AH && this.startQuery != null && !this.triedNameSearch) {
         this.triedNameSearch = true;
         this.searchAndPick(this.startQuery);
      }
   }

   private void searchAndPick(String query) {
      this.status = "Searching...";
      PriceData.async(() -> {
         try {
            List<PriceData.Choice> hits = PriceData.searchAh(query);
            if (hits.isEmpty()) {
               this.status = "No item found for \"" + query + "\"";
            } else {
               this.pendingPick = hits.get(0);
            }
         } catch (Exception var3) {
            this.status = "Could not search (" + (var3.getMessage() == null ? var3.getClass().getSimpleName() : var3.getMessage()) + ")";
         }
      });
   }

   public void tick() {
      super.tick();
      PriceData.Choice p = this.pendingPick;
      if (p != null) {
         this.pendingPick = null;
         this.pick(p, this.hoveredStack != null);
      } else {
         String q = this.searchText.trim();
         boolean typedByUser = this.choice == null || !q.equalsIgnoreCase(this.choice.name());
         if (typedByUser && q.length() >= 2 && !q.equalsIgnoreCase(this.ahAsked) && System.currentTimeMillis() - this.lastEdit > SEARCH_DELAY_MS) {
            this.ahAsked = q;
            PriceData.async(() -> {
               try {
                  List<PriceData.Choice> hits = PriceData.searchAh(q);
                  if (q.equalsIgnoreCase(this.ahAsked)) {
                     this.ahHits = hits;
                  }
               } catch (IOException var3x) {
               }
            });
         }
      }
   }

   private void refreshSuggestions() {
      String q = this.searchText.trim().toLowerCase(Locale.ROOT);
      List<String> prods = PriceData.products();
      List<PriceData.Choice> ah = this.ahHits;
      if (!q.equals(this.suggestFor) || prods.size() != this.suggestSource || ah != this.suggestAh) {
         this.suggestFor = q;
         this.suggestSource = prods.size();
         this.suggestAh = ah;
         if (!q.isEmpty() && (this.choice == null || !q.equals(this.choice.name().toLowerCase(Locale.ROOT)))) {
            List<String> starts = new ArrayList<>();
            List<String> contains = new ArrayList<>();

            for (String id : prods) {
               String p = PriceData.pretty(id).toLowerCase(Locale.ROOT);
               if (p.startsWith(q)) {
                  starts.add(id);
               } else if (p.contains(q)) {
                  contains.add(id);
               }
            }

            starts.addAll(contains);
            List<PriceData.Choice> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (String idx : starts.subList(0, Math.min(5, starts.size()))) {
               out.add(new PriceData.Choice(PriceData.Kind.BAZAAR, idx, PriceData.pretty(idx)));
               seen.add(PriceData.pretty(idx).toLowerCase(Locale.ROOT));
            }

            for (PriceData.Choice c : ah) {
               if (out.size() < 10 && seen.add(c.name().toLowerCase(Locale.ROOT))) {
                  out.add(c);
               }
            }

            this.suggestions = out;
         } else {
            this.suggestions = List.of();
         }

         this.suggestIdx = 0;
      }
   }

   private void resetView() {
      this.viewT0 = 0L;
      this.viewT1 = 0L;
   }

   private void zoom(double mx, double factor) {
      List<PriceData.Point> pts = this.points;
      if (pts.size() >= 2) {
         long d0 = pts.get(0).ts();
         long d1 = pts.get(pts.size() - 1).ts();
         long full = Math.max(1L, d1 - d0);
         long v0 = this.viewT0 == 0L ? d0 : this.viewT0;
         long v1 = this.viewT1 == 0L ? d1 : this.viewT1;
         double ratio = Math.max(0.0, Math.min(1.0, (mx - this.cx0()) / (this.cx1() - this.cx0())));
         long anchor = v0 + (long)(ratio * (v1 - v0));
         long minSpan = Math.max(600000L, full / 400L);
         long span = Math.max(minSpan, Math.min(full, Math.round((v1 - v0) * factor)));
         long n0 = Math.max(d0, Math.min(d1 - span, anchor - (long)(ratio * span)));
         if (span >= full) {
            this.resetView();
         } else {
            this.viewT0 = n0;
            this.viewT1 = n0 + span;
         }
      }
   }

   public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
      if (this.inChart(mx, my) && scrollY != 0.0 && this.points.size() >= 2) {
         this.zoom(mx, Math.pow(0.8, scrollY));
         return true;
      } else {
         return super.mouseScrolled(mx, my, scrollX, scrollY);
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      int sx = this.left() + 8;
      int sy = this.top() + 27;

      for (int i = 0; i < this.suggestions.size(); i++) {
         int ry = sy + i * 12;
         if (event.x() >= sx && event.x() <= sx + this.searchW() && event.y() >= ry && event.y() < ry + 12) {
            this.pick(this.suggestions.get(i), false);
            return true;
         }
      }

      if (event.button() == 0) {
         int edges = this.edgeAt(event.x(), event.y());
         if (edges != 0) {
            this.dragEdges = edges;
            this.dragMx = event.x();
            this.dragMy = event.y();
            this.dragPx = this.px;
            this.dragPy = this.py;
            this.dragPw = this.pw;
            this.dragPh = this.ph;
            return true;
         }

         if (this.inChart(event.x(), event.y()) && this.points.size() >= 2) {
            if (doubleClick) {
               this.resetView();
            } else if (this.viewT0 != 0L) {
               this.panning = true;
               this.panMx = event.x();
               this.panT0 = this.viewT0;
               this.panT1 = this.viewT1;
            }

            return true;
         }
      }

      return super.mouseClicked(event, doubleClick);
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      if (this.dragEdges != 0) {
         double ddx = event.x() - this.dragMx;
         double ddy = event.y() - this.dragMy;
         int nx = this.dragPx;
         int ny = this.dragPy;
         int nw = this.dragPw;
         int nh = this.dragPh;
         if ((this.dragEdges & 2) != 0) {
            nw = this.dragPw + (int)ddx;
         }

         if ((this.dragEdges & 8) != 0) {
            nh = this.dragPh + (int)ddy;
         }

         if ((this.dragEdges & 1) != 0) {
            nw = this.dragPw - (int)ddx;
            nx = this.dragPx + (int)ddx;
         }

         if ((this.dragEdges & 4) != 0) {
            nh = this.dragPh - (int)ddy;
            ny = this.dragPy + (int)ddy;
         }

         if (nw < 300) {
            if ((this.dragEdges & 1) != 0) {
               nx -= 300 - nw;
            }

            nw = 300;
         }

         if (nh < 170) {
            if ((this.dragEdges & 4) != 0) {
               ny -= 170 - nh;
            }

            nh = 170;
         }

         if (nx < 0) {
            nw += nx;
            nx = 0;
         }

         if (ny < 0) {
            nh += ny;
            ny = 0;
         }

         nw = Math.min(nw, this.width - nx);
         nh = Math.min(nh, this.height - ny);
         this.px = nx;
         this.py = ny;
         this.pw = Math.max(300, nw);
         this.ph = Math.max(170, nh);
         this.layout();
         return true;
      } else if (this.panning && this.points.size() >= 2) {
         List<PriceData.Point> pts = this.points;
         long d0 = pts.get(0).ts();
         long d1 = pts.get(pts.size() - 1).ts();
         long span = this.panT1 - this.panT0;
         long shift = (long)((this.panMx - event.x()) * span / (this.cx1() - this.cx0()));
         long n0 = Math.max(d0, Math.min(d1 - span, this.panT0 + shift));
         this.viewT0 = n0;
         this.viewT1 = n0 + span;
         return true;
      } else {
         return super.mouseDragged(event, dx, dy);
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.dragEdges != 0) {
         this.dragEdges = 0;
         GraphSettings.saveSize(this.pw, this.ph);
         this.appliedW = GraphSettings.width();
         this.appliedH = GraphSettings.height();
         return true;
      } else if (this.panning) {
         this.panning = false;
         return true;
      } else {
         return super.mouseReleased(event);
      }
   }

   public boolean keyPressed(KeyEvent event) {
      boolean inSearch = this.search != null && this.search.isFocused();
      int k = event.key();
      if (inSearch && (k == GLFW.GLFW_KEY_TAB || k == GLFW.GLFW_KEY_DOWN || k == GLFW.GLFW_KEY_UP)) {
         this.refreshSuggestions();
         int n = this.suggestions.size();
         if (n > 0) {
            boolean back = k == GLFW.GLFW_KEY_UP || k == GLFW.GLFW_KEY_TAB && (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            this.suggestIdx = Math.floorMod(this.suggestIdx + (back ? -1 : 1), n);
            return true;
         }
      }

      if ((k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) && inSearch) {
         this.refreshSuggestions();
         if (!this.suggestions.isEmpty()) {
            this.pick(this.suggestions.get(Math.min(this.suggestIdx, this.suggestions.size() - 1)), false);
            return true;
         }

         String q = this.searchText.trim();
         if (q.length() >= 2) {
            this.searchAndPick(q);
            return true;
         }
      }

      return super.keyPressed(event);
   }

   public boolean isPauseScreen() {
      return false;
   }

   /** Dev harness: presses the gear button. */
   void devGear() {
      this.settingsButton.onPress(new net.minecraft.client.input.InputWithModifiers() {
         public int input() {
            return 0;
         }

         public int modifiers() {
            return 0;
         }
      });
   }

   /** Dev harness: the panel geometry and a point in the middle of the chart. */
   int[] devGeometry() {
      return new int[]{this.px, this.py, this.pw, this.ph, (this.cx0() + this.cx1()) / 2, (this.cy0() + this.cy1()) / 2};
   }

   void devGestures(double zoomNotches, int cornerDx, int cornerDy) {
      if (zoomNotches != 0.0) {
         this.mouseScrolled((this.cx0() + this.cx1()) / 2.0 + (this.cx1() - this.cx0()) / 4.0, (this.cy0() + this.cy1()) / 2.0, 0.0, zoomNotches);
      }

      if (cornerDx != 0 || cornerDy != 0) {
         double x = this.px + this.pw - 2;
         double y = this.py + this.ph - 2;
         MouseButtonInfo info = new MouseButtonInfo(0, 0);
         this.mouseClicked(new MouseButtonEvent(x, y, info), false);
         this.mouseDragged(new MouseButtonEvent(x + cornerDx, y + cornerDy, info), cornerDx, cornerDy);
         this.mouseReleased(new MouseButtonEvent(x + cornerDx, y + cornerDy, info));
      }
   }

   private String[] labels() {
      return this.isAh() ? new String[]{"Lowest sale", "Average sale"} : new String[]{"Insta-buy", "Insta-sell"};
   }

   private boolean isAh() {
      return this.choice != null && this.choice.kind() == PriceData.Kind.AH;
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      int pw = this.panelW();
      int ph = this.panelH();
      int x0 = this.left();
      int y0 = this.top();
      GraphPrefs prefs = GraphSettings.get();
      g.fill(x0, y0, x0 + pw, y0 + ph, prefs.panelColor(PANEL));
      int hot = this.dragEdges != 0 ? this.dragEdges : this.edgeAt(mouseX, mouseY);
      // MoulConfig edges: light top-left, dark bottom-right
      g.fill(x0, y0, x0 + 1, y0 + ph, MoulUi.LIGHT);
      g.fill(x0 + 1, y0, x0 + pw, y0 + 1, MoulUi.LIGHT);
      g.fill(x0 + pw - 1, y0 + 1, x0 + pw, y0 + ph, MoulUi.DARK);
      g.fill(x0 + 1, y0 + ph - 1, x0 + pw - 1, y0 + ph, MoulUi.DARK);
      if (this.search != null) {
         MoulUi.valueBox(g, this.font, this.px + 8, this.py + 8, this.searchW(), 16, null, this.search.isFocused());
      }
      this.drawResizeHints(g, x0, y0, pw, ph, hot);
      super.extractRenderState(g, mouseX, mouseY, a);
      this.refreshSuggestions();
      String[] lab = this.labels();
      List<PriceData.Point> pts = this.points;
      String name = this.choice == null ? "Pick an item above" : this.choice.name();
      int nameX = x0 + 8;
      if (this.icon != null) {
         try {
            g.item(this.icon, x0 + 8, y0 + 26);
            nameX += 20;
         } catch (RuntimeException var23) {
            BazaarClient.LOG.warn("Could not draw the item icon; continuing without it", var23);
            this.icon = null;
         }
      }

      g.text(this.font, name, nameX, y0 + 30, TEXT);
      String prices = null;
      if (this.choice != null && this.choice.kind() == PriceData.Kind.BAZAAR) {
         PriceData.Live lv = PriceData.live(this.choice.id());
         if (lv != null) {
            prices = lab[0] + " " + fmt(lv.instaBuy()) + "   " + lab[1] + " " + fmt(lv.instaSell());
         }
      } else if (this.isAh() && !pts.isEmpty()) {
         PriceData.Point last = pts.get(pts.size() - 1);
         prices = "Lowest " + fmt(last.buy()) + "   Avg " + fmt(last.sell());
      }

      int pricesX = prices == null ? x0 + pw - 8 : x0 + pw - 8 - this.font.width(prices);
      if (prices != null) {
         g.text(this.font, prices, pricesX, y0 + 30, TEXT);
      }

      if (this.choice != null) {
         String badge = this.isAh() ? "Auction House" : "Bazaar";
         int bx = nameX + this.font.width(name) + 8;
         if (bx + this.font.width(badge) < pricesX - 6) {
            g.text(this.font, badge, bx, y0 + 30, MUTED);
         }
      }

      int cx0 = this.cx0();
      int cx1 = this.cx1();
      int cy0 = this.cy0();
      int cy1 = this.cy1();
      if (pts.size() >= 2) {
         this.drawChart(g, pts, cx0, cy0, cx1, cy1, mouseX, mouseY, lab, prefs);
      } else if (!this.status.isEmpty()) {
         g.centeredText(this.font, this.status, (cx0 + cx1) / 2, (cy0 + cy1) / 2, MUTED);
      }

      int legendEnd = x0 + 8;
      if (prefs.lines().buy()) {
         g.text(this.font, lab[0], legendEnd, y0 + ph - 14, BUY);
         legendEnd += this.font.width(lab[0]) + 10;
      }

      if (prefs.lines().sell()) {
         g.text(this.font, lab[1], legendEnd, y0 + ph - 14, SELL);
         legendEnd += this.font.width(lab[1]);
      }
      String full = "Scroll: zoom  Drag: pan  Hover an item + " + GraphKey.keyName();
      String hint = legendEnd + 16 + this.font.width(full) < x0 + pw - 8 ? full : "Scroll: zoom  Drag: pan";
      if (legendEnd + 16 + this.font.width(hint) < x0 + pw - 8) {
         g.text(this.font, hint, x0 + pw - 8 - this.font.width(hint), y0 + ph - 14, MUTED);
      }

      this.drawSuggestions(g, mouseX, mouseY, x0 + 8, y0 + 26);
   }

   private void drawResizeHints(GuiGraphicsExtractor g, int x0, int y0, int w, int h, int hot) {
      if ((hot & 1) != 0) {
         g.verticalLine(x0, y0, y0 + h - 1, EDGE_HOT);
      }

      if ((hot & 2) != 0) {
         g.verticalLine(x0 + w - 1, y0, y0 + h - 1, EDGE_HOT);
      }

      if ((hot & 4) != 0) {
         g.horizontalLine(x0, x0 + w - 1, y0, EDGE_HOT);
      }

      if ((hot & 8) != 0) {
         g.horizontalLine(x0, x0 + w - 1, y0 + h - 1, EDGE_HOT);
      }

      int c = 7;
      int[][] corners = new int[][]{{x0, y0, 1, 1, 5}, {x0 + w - 1, y0, -1, 1, 6}, {x0, y0 + h - 1, 1, -1, 9}, {x0 + w - 1, y0 + h - 1, -1, -1, 10}};

      for (int[] k : corners) {
         int color = hot == k[4] ? 0xFFB8D0F5 : MUTED;
         g.horizontalLine(Math.min(k[0], k[0] + k[2] * c), Math.max(k[0], k[0] + k[2] * c), k[1], color);
         g.verticalLine(k[0], Math.min(k[1], k[1] + k[3] * c), Math.max(k[1], k[1] + k[3] * c), color);
      }
   }

   private void drawSuggestions(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y) {
      if (!this.suggestions.isEmpty()) {
         int w = this.searchW();
         int h = this.suggestions.size() * 12 + 2;
         g.fill(x, y, x + w, y + h, 0xFF141418);
         g.outline(x, y, w, h, 0xFF6A6A74);

         for (int i = 0; i < this.suggestions.size(); i++) {
            int ry = y + 1 + i * 12;
            PriceData.Choice c = this.suggestions.get(i);
            boolean hotRow = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY < ry + 12 || i == this.suggestIdx;
            if (hotRow) {
               g.fill(x + 1, ry, x + w - 1, ry + 12, 0xFF2E2E38);
            }

            g.text(this.font, c.name(), x + 4, ry + 2, hotRow ? -1 : TEXT);
            String tag = c.kind() == PriceData.Kind.AH ? "AH" : "BZ";
            g.text(this.font, tag, x + w - 4 - this.font.width(tag), ry + 2, MUTED);
         }
      }
   }

   private static String when(long ts, long span) {
      return (span > 345600000L ? WHEN_DAY : WHEN).format(Instant.ofEpochMilli(ts));
   }

   private void drawChart(
      GuiGraphicsExtractor g, List<PriceData.Point> pts, int cx0, int cy0, int cx1, int cy1, int mouseX, int mouseY, String[] lab, GraphPrefs prefs
   ) {
      boolean showBuy = prefs.lines().buy();
      boolean showSell = prefs.lines().sell();
      long d0 = pts.get(0).ts();
      long d1 = pts.get(pts.size() - 1).ts();
      long t0 = this.viewT0 == 0L ? d0 : this.viewT0;
      long t1 = this.viewT1 == 0L ? d1 : this.viewT1;
      double lo = Double.MAX_VALUE;
      double hi = -Double.MAX_VALUE;

      for (int i = 0; i < pts.size(); i++) {
         PriceData.Point p = pts.get(i);
         boolean inside = p.ts() >= t0 && p.ts() <= t1;
         boolean edgeNeighbour = i + 1 < pts.size() && pts.get(i + 1).ts() >= t0 && p.ts() < t0 || i > 0 && pts.get(i - 1).ts() <= t1 && p.ts() > t1;
         if (inside || edgeNeighbour) {
            // Scale the y axis to the lines that are drawn (NaN values are skipped by min/max below).
            if (showBuy && !Double.isNaN(p.buy())) {
               lo = Math.min(lo, p.buy());
               hi = Math.max(hi, p.buy());
            }

            if (showSell && !Double.isNaN(p.sell())) {
               lo = Math.min(lo, p.sell());
               hi = Math.max(hi, p.sell());
            }
         }
      }

      if (!(lo > hi)) {
         double pad = Math.max((hi - lo) * 0.06, hi * 0.001);
         boolean neverNegative = lo >= 0.0;
         lo -= pad;
         hi += pad;
         if (neverNegative) {
            lo = Math.max(0.0, lo);
         }

         for (int ix = 0; ix <= 3; ix++) {
            int y = cy0 + (cy1 - cy0) * ix / 3;
            if (prefs.grid()) {
               g.horizontalLine(cx0, cx1, y, GRID);
            }

            g.text(this.font, fmt(hi - (hi - lo) * ix / 3.0), this.left() + 4, y - 4, MUTED);
         }

         long span = t1 - t0;
         g.text(this.font, when(t0, span), cx0, cy1 + 4, MUTED);
         String end = when(t1, span);
         g.text(this.font, end, cx1 - this.font.width(end), cy1 + 4, MUTED);
         if (this.viewT0 != 0L) {
            String zoomed = "zoomed - double-click to reset";
            g.text(this.font, zoomed, cx0 + (cx1 - cx0 - this.font.width(zoomed)) / 2, cy1 + 4, MUTED);
         }

         if (showBuy) {
            series(g, pts, true, BUY, cx0, cy0, cx1, cy1, t0, t1, lo, hi);
         }

         if (showSell) {
            series(g, pts, false, SELL, cx0, cy0, cx1, cy1, t0, t1, lo, hi);
         }
         if (mouseX >= cx0 && mouseX <= cx1 && mouseY >= cy0 && mouseY <= cy1 && this.dragEdges == 0) {
            PriceData.Point best = null;
            int bestDx = Integer.MAX_VALUE;

            for (PriceData.Point p : pts) {
               if (p.ts() >= t0 && p.ts() <= t1) {
                  int dx = Math.abs(x(p.ts(), cx0, cx1, t0, t1) - mouseX);
                  if (dx < bestDx) {
                     bestDx = dx;
                     best = p;
                  }
               }
            }

            if (best == null) {
               return;
            }

            int hx = x(best.ts(), cx0, cx1, t0, t1);
            g.verticalLine(hx, cy0, cy1, 0x66FFFFFF);
            boolean sold = this.isAh() && best.volume() > 0.0;
            String l1 = WHEN.format(Instant.ofEpochMilli(best.ts()));
            // Only the lines the Lines setting shows (plus "Sold" on AH items), each with its color.
            List<String> lines = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            if (showBuy) {
               lines.add(pad(lab[0]) + fmt(best.buy()));
               colors.add(BUY);
            }

            if (showSell) {
               lines.add(pad(lab[1]) + fmt(best.sell()));
               colors.add(SELL);
            }

            if (sold) {
               lines.add(pad("Sold") + (long)best.volume());
               colors.add(TEXT);
            }

            int bw = this.font.width(l1);
            for (String l : lines) {
               bw = Math.max(bw, this.font.width(l));
            }

            bw += 10;
            int bh = 16 + 11 * lines.size();
            int bx = hx + 8 + bw > cx1 ? hx - 8 - bw : hx + 8;
            int by = Math.min(cy1 - bh, Math.max(cy0, mouseY - 18));
            g.fill(bx, by, bx + bw, by + bh, 0xF0090B0F);
            g.outline(bx, by, bw, bh, EDGE_COLOR);
            g.text(this.font, l1, bx + 5, by + 4, MUTED);
            for (int i = 0; i < lines.size(); i++) {
               g.text(this.font, lines.get(i), bx + 5, by + 15 + 11 * i, colors.get(i));
            }
         }
      }
   }

   private static String pad(String label) {
      return label + "  ";
   }

   private static int x(long ts, int cx0, int cx1, long t0, long t1) {
      return cx0 + (int)((double)(ts - t0) * (cx1 - cx0) / Math.max(1L, t1 - t0));
   }

   private static void series(
      GuiGraphicsExtractor g, List<PriceData.Point> pts, boolean buy, int color, int cx0, int cy0, int cx1, int cy1, long t0, long t1, double lo, double hi
   ) {
      int prevX = Integer.MIN_VALUE;
      int prevY = 0;

      for (PriceData.Point p : pts) {
         int px = x(p.ts(), cx0, cx1, t0, t1);
         double v = buy ? p.buy() : p.sell();
         int py = cy1 - (int)((v - lo) * (cy1 - cy0) / Math.max(1.0E-9, hi - lo));
         py = Math.max(cy0, Math.min(cy1, py));
         if (prevX != Integer.MIN_VALUE && (px >= cx0 || prevX >= cx0) && (px <= cx1 || prevX <= cx1)) {
            int span = Math.max(1, px - prevX);
            int from = Math.max(prevX, cx0);
            int to = Math.min(px, cx1);

            for (int xc = from; xc <= to; xc++) {
               double f0 = (double)(xc - prevX) / span;
               double f1 = Math.min(1.0, (double)(xc + 1 - prevX) / span);
               int ya = (int)Math.round(prevY + (py - prevY) * f0);
               int yb = (int)Math.round(prevY + (py - prevY) * f1);
               g.fill(xc, Math.max(cy0, Math.min(ya, yb)), xc + 1, Math.min(cy1, Math.max(ya, yb)) + 1, color);
            }
         }

         prevX = px;
         prevY = py;
      }
   }

   static String fmt(double v) {
      if (Double.isNaN(v)) {
         return "-";
      } else {
         double a = Math.abs(v);
         if (a >= 1.0E9) {
            return String.format(Locale.ROOT, "%.2fB", v / 1.0E9);
         } else if (a >= 1000000.0) {
            return String.format(Locale.ROOT, "%.2fM", v / 1000000.0);
         } else {
            return a >= 10000.0 ? String.format(Locale.ROOT, "%.1fk", v / 1000.0) : String.format(Locale.ROOT, "%,.1f", v);
         }
      }
   }
}
