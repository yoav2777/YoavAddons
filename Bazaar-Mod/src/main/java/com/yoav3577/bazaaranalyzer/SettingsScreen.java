package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.Cfg;
import com.yoav3577.bazaaranalyzer.core.GraphPrefs;
import com.yoav3577.bazaaranalyzer.core.LinkSettings;
import com.yoav3577.bazaaranalyzer.core.ModPrefs;
import com.yoav3577.bazaaranalyzer.core.SyncPrefs;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The mod's settings, laid out like SkyHanni's (MoulConfig): categories on the left, option cards on the right (name and
 * control on the left of each card, description on the right), a search box behind the magnifier. Every change applies
 * at once (the *Settings holders' live()); the files are written when the screen closes. Opened by /ba, /ya, /yoavaddons,
 * /bazaaranalyzer, the settings key, the graph's gear button and Mod Menu.
 */
final class SettingsScreen extends Screen {
   static final int ACCENT = MoulUi.AQUA;
   /** The trade window: a 6-row chest screen with the player inventory (176x222, centered). */
   static final int TRADE_W = 176;
   static final int TRADE_H = 222;
   private static final int CAT_W = 112;
   private static final int CARD_GAP = 5;
   private static final String[] RANGE_LABELS = {"1 day", "1 week", "1 month", "1 year"};

   /** A category on the left. The built-in ones below, plus the pages add-on mods register (api.Settings#page). */
   static final class Page {
      private static final List<Page> ALL = new ArrayList<>();
      static final Page ABOUT = new Page("ABOUT", "About", "Yoav Addons by yoav3577.");
      static final Page GRAPH = new Page("GRAPH", "Price graph", "The price graph (key I): its default range, look and size.");
      static final Page TRADE = new Page("TRADE", "Trade window", "The website button in trade windows, and recording trades.");
      static final Page SYNC = new Page("SYNC", "Cloud sync", "Shares the lowball tracker between your PCs through your GitHub.");
      static final Page WORTH = new Page("WORTH", "Item worth", "How items are valued: the trade window worth and the debug hover.");
      static final Page LINKS = new Page("LINKS", "Website links", "Which modifiers become filters on the website's AH item pages.");
      static final Page FILL = new Page("FILL", "Auto fill time", "Fills the Custom Duration sign in the AH Auction Duration menu.");

      final String id;
      final String label;
      final String desc;

      private Page(String id, String label, String desc) {
         this.id = id;
         this.label = label;
         this.desc = desc;
         ALL.add(this);
      }

      static synchronized Page[] values() {
         return ALL.toArray(new Page[0]);
      }

      String name() {
         return this.id;
      }

      @Override
      public String toString() {
         return this.id;
      }

      int ordinal() {
         synchronized (Page.class) {
            return ALL.indexOf(this);
         }
      }

      /** The page with this id (any case), or null. */
      static synchronized Page byId(String id) {
         for (Page p : ALL) {
            if (p.id.equalsIgnoreCase(id)) {
               return p;
            }
         }

         return null;
      }

      static Page valueOf(String id) {
         Page p = byId(id);
         if (p == null) {
            throw new IllegalArgumentException("No settings page " + id);
         }

         return p;
      }

      /** An add-on's page: the existing one with this id, or a new one at the end of the list. */
      static synchronized Page addon(String id, String label, String desc) {
         Page p = byId(id);
         return p != null ? p : new Page(id, label, desc);
      }
   }

   private static Page lastPage = Page.ABOUT;
   private static volatile String siteResult;
   /** A page to show when the screen next opens (api.YoavAddons#settingsScreen); an add-on page exists only after its options are built. */
   private static volatile String wantedPage;
   private final Screen parent;
   private Page page = lastPage;
   private final List<Opt> options = new ArrayList<>();
   private EditBox search;
   private boolean searching;
   private int scroll;
   private int contentH;
   private boolean resetArmed;
   // add-on pages (api.YoavAddonsPlugin): what "Reset everything" and closing the screen also run
   private final List<Runnable> addonResets = new ArrayList<>();
   private final List<Runnable> addonCloses = new ArrayList<>();
   // layout (from the last frame)
   private int x0;
   private int y0;
   private int pw;
   private int ph;
   private int listX;
   private int listY;
   private int listW;
   private int listH;
   // interaction
   private Dropdown<?> open;
   private Slider dragging;
   private boolean draggingScroll;

   SettingsScreen(Screen parent) {
      super(Component.literal("Yoav Addons settings"));
      this.parent = parent;
      String dev = System.getProperty("bazaaranalyzer.dev.settingsPage");
      if (dev != null) {
         wantedPage = dev;
      }
   }

   /** Opens on this page next time (any case; an add-on page id works too). */
   static void wantPage(String id) {
      wantedPage = id;
   }

   Page page() {
      return this.page;
   }

   void showPage(Page p) {
      this.page = p;
      lastPage = p;
      this.resetArmed = false;
      this.scroll = 0;
      this.open = null;
      if (this.searching) {
         this.searching = false;
         this.search.setValue("");
      }

      this.rebuildWidgets();
   }

   protected void init() {
      this.options.clear();
      this.open = null;
      this.dragging = null;
      this.buildOptions();
      this.buildAddonOptions();
      String want = wantedPage;
      if (want != null) {
         wantedPage = null;
         Page p = Page.byId(want);
         if (p != null) {
            this.page = p;
            lastPage = p;
         }
      }

      for (Opt o : this.options) {
         if (o instanceof Text t) {
            this.addRenderableWidget(t.box);
         }
      }

      String q = this.search == null ? "" : this.search.getValue();
      this.search = MoulUi.textBox(this.font, "Search all settings...");
      this.search.setMaxLength(40);
      this.search.setValue(q);
      this.search.setResponder(s -> this.scroll = 0);
      this.search.setVisible(this.searching);
      this.addRenderableWidget(this.search);
      if (this.searching) {
         this.setFocused(this.search);
      }
   }

   // ---------------------------------------------------------------- options

   private void buildOptions() {
      // About
      this.options.add(new Info(Page.ABOUT, "Yoav Addons", "Version " + BazaarClient.VERSION + " by yoav3577. Prices come from Coflnet and the Hypixel API."));
      this.options.add(new Info(
         Page.ABOUT, "Commands",
         "/ba, /ya, /yoavaddons, /bazaaranalyzer: this menu.\n/ya site: the website in your browser (built into the mod).\n/ya sync: sync the lowball tracker with your other PCs now.\n/bazaaranalyzer open <n>: a cancelled trade's AH items on the website.\nKey I: the price graph."
      ));
      this.options.add(new Action(
         Page.ABOUT, "Reset everything", "Every setting on every page back to the defaults. Click twice to confirm.",
         () -> this.resetArmed ? "Click again" : "Reset all", () -> {
            if (this.resetArmed) {
               GraphSettings.live(GraphPrefs.DEFAULT);
               ModSettings.live(ModPrefs.DEFAULT);
               AhSettings.live(LinkSettings.DEFAULT);
               this.runAll(this.addonResets, "reset");
               this.resetArmed = false;
               this.rebuildWidgets();
            } else {
               this.resetArmed = true;
            }
         }
      ));

      // Graph
      this.options.add(new Dropdown<>(
         Page.GRAPH, "Default range", "The range the graph opens with.", List.of(0, 1, 2, 3), i -> RANGE_LABELS[i],
         () -> Math.max(0, GraphPrefs.RANGES.indexOf(GraphSettings.get().range())), i -> GraphSettings.live(GraphSettings.get().withRange(GraphPrefs.RANGES.get(i)))
      ));
      this.options.add(new Toggle(
         Page.GRAPH, "Remember range", "When on, the 1D/1W/1M/1Y button you press in the graph becomes the default range.",
         () -> GraphSettings.get().rememberRange(), v -> GraphSettings.live(GraphSettings.get().withRememberRange(v))
      ));
      this.options.add(new Dropdown<>(
         Page.GRAPH, "Lines", "Which price lines to draw: insta-buy / insta-sell for Bazaar items, lowest / average sale for AH items.",
         List.of(GraphPrefs.Lines.values()), SettingsScreen::linesLabel, () -> GraphSettings.get().lines(), v -> GraphSettings.live(GraphSettings.get().withLines(v))
      ));
      this.options.add(new Toggle(
         Page.GRAPH, "Grid lines", "Horizontal grid lines behind the chart.", () -> GraphSettings.get().grid(), v -> GraphSettings.live(GraphSettings.get().withGrid(v))
      ));
      this.options.add(new Slider(
         Page.GRAPH, "Background", "How see-through the graph panel is (100% = solid).", GraphPrefs.MIN_OPACITY, 100, 5,
         () -> GraphSettings.get().opacity(), v -> GraphSettings.live(GraphSettings.get().withOpacity(v)), v -> v + "%"
      ));
      this.options.add(new Slider(
         Page.GRAPH, "Width", "Graph window width in GUI pixels. You can also drag its edges; it never gets bigger than the screen.", GraphPrefs.MIN_W, 1000, 10,
         () -> GraphSettings.get().width(), v -> GraphSettings.live(GraphSettings.get().withSize(v, GraphSettings.get().height())), String::valueOf
      ));
      this.options.add(new Slider(
         Page.GRAPH, "Height", "Graph window height in GUI pixels.", GraphPrefs.MIN_H, 600, 10,
         () -> GraphSettings.get().height(), v -> GraphSettings.live(GraphSettings.get().withSize(GraphSettings.get().width(), v)), String::valueOf
      ));
      this.options.add(new Action(
         Page.GRAPH, "Reset size", "Back to " + GraphPrefs.DEFAULT.width() + " x " + GraphPrefs.DEFAULT.height() + ".", () -> "Reset",
         () -> GraphSettings.live(GraphSettings.get().withSize(GraphPrefs.DEFAULT.width(), GraphPrefs.DEFAULT.height()))
      ));
      this.options.add(new Action(
         Page.GRAPH, "Preview graph", "Opens the graph with these settings; Esc comes back here.", () -> "Open", () -> PriceScreen.openFrom(this.minecraft, this)
      ));

      // Trade window
      this.options.add(new Toggle(
         Page.TRADE, "Trade button", "The \"Open N AH items on website\" button in trade windows, with the other player's items' worth under it. It moves beside the trade window if it would cover it.",
         () -> ModSettings.get().tradeButton(), v -> ModSettings.live(ModSettings.get().withTradeButton(v))
      ));
      this.options.add(new Dropdown<>(
         Page.TRADE, "Corner", "Which screen corner the button sits in.", List.of(ModPrefs.Corner.values()), c -> c.label,
         () -> ModSettings.get().corner(), v -> {
            ModPrefs p = ModSettings.get();
            ModSettings.live(p.withPosition(v, p.offsetX(), p.offsetY()));
         }
      ));
      this.options.add(new Slider(
         Page.TRADE, "X offset", "Distance from the left or right screen edge.", 0, Math.max(ModPrefs.SLIDER_OFFSET, this.width / 2), 1,
         () -> ModSettings.get().offsetX(), v -> {
            ModPrefs p = ModSettings.get();
            ModSettings.live(p.withPosition(p.corner(), v, p.offsetY()));
         }, String::valueOf
      ));
      this.options.add(new Slider(
         Page.TRADE, "Y offset", "Distance from the top or bottom screen edge.", 0, Math.max(ModPrefs.SLIDER_OFFSET, this.height / 2), 1,
         () -> ModSettings.get().offsetY(), v -> {
            ModPrefs p = ModSettings.get();
            ModSettings.live(p.withPosition(p.corner(), p.offsetX(), v));
         }, String::valueOf
      ));
      this.options.add(new Action(
         Page.TRADE, "Place the button", "Move the button with the mouse on a mock trade screen.", () -> "Drag to place...",
         () -> this.minecraft.setScreen(new PlaceButtonScreen(this))
      ));
      this.options.add(new Preview(Page.TRADE));
      this.options.add(new Toggle(
         Page.TRADE, "Link after cancel", "After a cancelled trade, post an [Open on website] chat link for the other player's AH items.",
         () -> ModSettings.get().cancelLink(), v -> ModSettings.live(ModSettings.get().withCancelLink(v))
      ));
      this.options.add(new Toggle(
         Page.TRADE, "Record trades", "Save completed player trades to player-trades.jsonl (the website's lowball view uses them).",
         () -> ModSettings.get().recordTrades(), v -> ModSettings.live(ModSettings.get().withRecordTrades(v))
      ));
      this.options.add(new Action(Page.TRADE, "Reset page", "Put this page's settings back to the defaults.", () -> "Reset", () -> {
         ModPrefs cur = ModSettings.get();
         ModSettings.live(ModPrefs.DEFAULT.withSitePort(cur.sitePort()).withFill(cur.fillTime(), cur.fillHours()).withPriceDebug(cur.priceDebug()));
      }));

      // Item worth
      this.options.add(new Toggle(
         Page.WORTH, "Debug on hover",
         "Adds how each item is valued to its tooltip: recent sales, 30 / 90-day history, lowest BIN, craft cost and the filters used.",
         () -> ModSettings.get().priceDebug(), v -> ModSettings.live(ModSettings.get().withPriceDebug(v))
      ));
      this.options.add(new Info(
         Page.WORTH, "How it works",
         "Recent sales with the same important modifiers, each moved by 70% of the difference in modifier cost; the newest sales weigh most. "
            + "Fewer than 3 such sales: the 30-day history, then 90 days. A BIN listing of the same item caps it."
      ));

      // Website links
      this.options.add(new Slider(
         Page.LINKS, "Enchants over", "Only enchants worth more than this become website filters.", 0, LinkSettings.ENCHANT_STEPS.length - 1, 1,
         () -> LinkSettings.nearestEnchantStep(AhSettings.get().minEnchantValue()),
         i -> AhSettings.live(AhSettings.get().withMinEnchantValue(LinkSettings.ENCHANT_STEPS[i])), i -> coins(LinkSettings.ENCHANT_STEPS[i])
      ));
      this.options.add(new Slider(
         Page.LINKS, "Max enchants", "At most this many of the most valuable enchants become filters.", 0, 10, 1,
         () -> AhSettings.get().maxEnchants(), v -> AhSettings.live(AhSettings.get().withMaxEnchants(v)), String::valueOf
      ));
      this.options.add(new Slider(
         Page.LINKS, "Other mods over", "Stars, recombobulator, hot potato books... become filters when worth more than this share of the item's value.",
         0, 100, 1, () -> (int) Math.round(AhSettings.get().minShare() * 100.0), v -> AhSettings.live(AhSettings.get().withMinShare(v / 100.0)), v -> v + "%"
      ));
      this.options.add(new Toggle(
         Page.LINKS, "Pet rarity filter", "Filter pets by their rarity on the website.", () -> AhSettings.get().petRarity(), v -> AhSettings.live(AhSettings.get().withPetRarity(v))
      ));
      int ports = ModPrefs.LAST_SITE_PORT - ModPrefs.FIRST_SITE_PORT + 1;
      this.options.add(new Slider(
         Page.LINKS, "Website port",
         "Auto finds the newest Bazaar Analyzer site on ports " + ModPrefs.FIRST_SITE_PORT + "-" + ModPrefs.LAST_SITE_PORT + " (else the copy built into the mod); pick a port to use only that one.",
         0, ports, 1, () -> ModSettings.get().sitePort() == 0 ? 0 : ModSettings.get().sitePort() - ModPrefs.FIRST_SITE_PORT + 1, i -> {
            ModSettings.live(ModSettings.get().withSitePort(i == 0 ? 0 : ModPrefs.FIRST_SITE_PORT + i - 1));
            siteResult = null;
         }, i -> i == 0 ? "Auto" : String.valueOf(ModPrefs.FIRST_SITE_PORT + i - 1)
      ));
      this.options.add(new Action(
         Page.LINKS, "Test connection", "Looks for the website the same way the trade button does.", () -> siteResult == null ? "Test" : siteResult, this::testSite
      ));
      this.options.add(new Action(Page.LINKS, "Reset page", "Put this page's settings back to the defaults.", () -> "Reset", () -> {
         AhSettings.live(LinkSettings.DEFAULT);
         ModSettings.live(ModSettings.get().withSitePort(ModPrefs.DEFAULT.sitePort()));
         siteResult = null;
      }));

      // Cloud sync
      this.options.add(new Info(
         Page.SYNC, "How it works",
         "Keeps the lowball tracker (player trades, parts taken off bought items, the last 90 days of Bazaar/AH chat) in a secret gist on your GitHub, so every PC with the same token shows the same tracker.\nToken: github.com/settings/tokens > Generate new token (classic), tick only \"gist\", no expiration. Paste the same token on every PC."
      ));
      this.options.add(new Toggle(
         Page.SYNC, "Sync", "Turn cloud sync on or off (the token stays saved).", CloudSync::enabled, CloudSync::setEnabled
      ));
      this.options.add(new Text(
         Page.SYNC, "GitHub token", "Paste the token (Ctrl+V). It is saved on this PC only (config/bazaaranalyzer/sync.json); paste another one to replace it.",
         CloudSync.tokenHint(), "", t -> {
            if (SyncPrefs.looksLikeToken(t)) {
               CloudSync.setToken(t);
            }
         }
      ));
      this.options.add(new Action(
         Page.SYNC, "Sync now", "Gets your other PCs' data and uploads this PC's. It also runs by itself every few minutes and when the game closes.",
         CloudSync::status, () -> CloudSync.syncNow(null)
      ));

      // Auto fill time
      this.options.add(new Toggle(
         Page.FILL, "Auto fill time", "Types the number below into the Custom Duration sign of the AH Auction Duration menu.",
         () -> ModSettings.get().fillTime(), v -> ModSettings.live(ModSettings.get().withFill(v, ModSettings.get().fillHours()))
      ));
      Text hours = new Text(Page.FILL, "Hours", "The number to fill in (hours, 1-336 = up to 14 days; anything that is not a number is ignored).", "Hours",
         String.valueOf(ModSettings.get().fillHours()), t -> {
            if (t.matches("\\d+")) {
               ModSettings.live(ModSettings.get().withFill(ModSettings.get().fillTime(), Integer.parseInt(t)));
            }
         });
      hours.box.setMaxLength(String.valueOf(ModPrefs.MAX_FILL_HOURS).length());
      this.options.add(hours);
   }

   /** The options shown now: this page's, or every page's that match the search. */
   private List<Opt> shown() {
      String q = this.searching ? this.search.getValue().trim().toLowerCase(Locale.ROOT) : "";
      List<Opt> out = new ArrayList<>();
      for (Opt o : this.options) {
         if (q.isEmpty() ? o.page == this.page : o.matches(q)) {
            out.add(o);
         }
      }

      return out;
   }

   private boolean pageMatches(Page p, String q) {
      for (Opt o : this.options) {
         if (o.page == p && o.matches(q)) {
            return true;
         }
      }

      return false;
   }

   // ---------------------------------------------------------------- layout + drawing

   private void layout() {
      this.pw = Math.min(this.width - 16, 520);
      this.ph = Math.min(this.height - 16, 340);
      this.x0 = (this.width - this.pw) / 2;
      this.y0 = (this.height - this.ph) / 2;
      int ox = this.x0 + 5 + CAT_W + 4;
      int ow = this.pw - 10 - CAT_W - 4;
      this.listX = ox + 5;
      this.listY = this.y0 + 29 + 20;
      this.listW = ow - 10;
      this.listH = this.ph - 34 - 25;
      int cw = this.listW - 16;
      int y = this.listY + 5 - this.scroll;
      int total = 5;
      for (Opt o : this.options) {
         o.visible = false;
      }

      for (Opt o : this.shown()) {
         int h = o.height(this.font, cw);
         o.place(this.listX + 4, y, cw, h);
         o.visible = true;
         y += h + CARD_GAP;
         total += h + CARD_GAP;
      }

      this.contentH = total;
      int max = Math.max(0, this.contentH - this.listH);
      if (this.scroll > max) {
         this.scroll = max;
      }
   }

   private int optionsX() {
      return this.x0 + 5 + CAT_W + 4;
   }

   private int optionsW() {
      return this.pw - 10 - CAT_W - 4;
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      this.layout();
      MoulUi.floating(g, this.x0, this.y0, this.pw, this.ph, true);

      // header
      MoulUi.inner(g, this.x0 + 5, this.y0 + 5, this.pw - 10, 20);
      String title = "§fYoav Addons §7" + BazaarClient.VERSION + " by §cyoav3577§7, prices by §5Coflnet §7and §5Hypixel";
      if (this.font.width(title) > this.pw - 20) {
         title = "§fYoav Addons §7" + BazaarClient.VERSION + " by §cyoav3577";
      }

      g.text(this.font, title, this.x0 + (this.pw - this.font.width(title)) / 2, this.y0 + 11, 0xFFFFFFFF, true);

      // categories
      int cx = this.x0 + 5;
      int cy = this.y0 + 29;
      int chh = this.ph - 34;
      MoulUi.inner(g, cx, cy, CAT_W, chh);
      String cats = "Categories";
      g.text(this.font, cats, cx + (CAT_W - this.font.width(cats)) / 2, cy + 6, MoulUi.PURPLE, true);
      MoulUi.inner(g, cx + 5, cy + 18, CAT_W - 10, chh - 23);
      String q = this.searching ? this.search.getValue().trim().toLowerCase(Locale.ROOT) : "";
      for (Page p : Page.values()) {
         int ry = this.catY(p);
         boolean sel = q.isEmpty() && p == this.page;
         boolean dim = !q.isEmpty() && !this.pageMatches(p, q);
         boolean hot = !sel && inside(mouseX, mouseY, cx + 5, ry - 3, CAT_W - 10, 14);
         Component label = Component.literal(MoulUi.fit(this.font, p.label, CAT_W - 16));
         if (sel) {
            label = label.copy().withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE);
         }

         int color = sel ? 0xFFFFFFFF : dim ? MoulUi.TEXT_DIM : hot ? 0xFFFFFFFF : MoulUi.TEXT;
         g.text(this.font, label, cx + (CAT_W - this.font.width(label)) / 2, ry, color, true);
      }

      // options panel
      int ox = this.optionsX();
      int ow = this.optionsW();
      MoulUi.inner(g, ox, cy, ow, chh);
      if (!this.searching) {
         String d = MoulUi.fit(this.font, this.page.desc, ow - 30);
         g.text(this.font, d, ox + 6, cy + 6, MoulUi.TEXT, true);
      } else {
         MoulUi.valueBox(g, this.font, ox + 5, cy + 3, ow - 30, 14, null, this.search.isFocused());
         this.search.setX(ox + 9);
         this.search.setY(cy + 6);
         this.search.setWidth(ow - 38);
         this.search.setHeight(10);
      }

      MoulUi.magnifier(g, ox + ow - 19, cy + 3, inside(mouseX, mouseY, ox + ow - 20, cy + 2, 16, 16) || this.searching);
      MoulUi.inner(g, this.listX, this.listY, this.listW, this.listH);
      g.enableScissor(this.listX + 1, this.listY + 1, this.listX + this.listW - 1, this.listY + this.listH - 1);
      boolean anyShown = false;
      for (Opt o : this.options) {
         if (o.visible && o.y + o.h > this.listY && o.y < this.listY + this.listH) {
            o.render(g, this.font, mouseX, mouseY);
            anyShown = true;
         }
      }

      if (!anyShown) {
         g.text(this.font, "Nothing matches.", this.listX + 10, this.listY + 10, MoulUi.TEXT_DIM, true);
      }

      g.disableScissor();
      MoulUi.scrollbar(g, this.listX + this.listW - 9, this.listY + 2, this.listH - 4, this.listH, this.contentH, this.scroll);

      // text boxes: only while their card is fully in view
      for (Opt o : this.options) {
         if (o instanceof Text t) {
            boolean in = o.visible && o.y >= this.listY && o.y + o.h <= this.listY + this.listH;
            t.box.setVisible(in);
            if (in) {
               t.placeBox();
            } else if (t.box.isFocused()) {
               this.setFocused(null);
            }
         }
      }

      super.extractRenderState(g, mouseX, mouseY, a);
      if (this.open != null) {
         g.nextStratum();
         this.open.renderList(g, this.font, mouseX, mouseY);
      }
   }

   private int catY(Page p) {
      return this.y0 + 29 + 18 + 7 + p.ordinal() * 16;
   }

   static boolean inside(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   // ---------------------------------------------------------------- input

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      double mx = event.x();
      double my = event.y();
      if (this.open != null) {
         this.open.clickList(mx, my);
         this.open = null;
         return true;
      }

      if (super.mouseClicked(event, doubleClick)) {
         return true;
      }

      this.setFocused(null);
      int ox = this.optionsX();
      int ow = this.optionsW();
      int cy = this.y0 + 29;
      if (inside(mx, my, ox + ow - 20, cy + 2, 16, 16)) {
         this.searching = !this.searching;
         this.search.setVisible(this.searching);
         this.scroll = 0;
         if (this.searching) {
            this.setFocused(this.search);
         } else {
            this.search.setValue("");
         }

         return true;
      }

      for (Page p : Page.values()) {
         if (inside(mx, my, this.x0 + 10, this.catY(p) - 3, CAT_W - 10, 14)) {
            this.showPage(p);
            return true;
         }
      }

      int sbx = this.listX + this.listW - 9;
      if (this.contentH > this.listH && inside(mx, my, sbx, this.listY, 8, this.listH)) {
         this.draggingScroll = true;
         this.scrollTo(my);
         return true;
      }

      if (inside(mx, my, this.listX, this.listY, this.listW, this.listH)) {
         for (Opt o : this.options) {
            if (o.visible && inside(mx, my, o.x, o.y, o.w, o.h)) {
               o.click(mx, my, event.button());
               return true;
            }
         }
      }

      return false;
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      if (this.dragging != null) {
         this.dragging.drag(event.x());
         return true;
      } else if (this.draggingScroll) {
         this.scrollTo(event.y());
         return true;
      }

      return super.mouseDragged(event, dx, dy);
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.dragging != null || this.draggingScroll) {
         this.dragging = null;
         this.draggingScroll = false;
         return true;
      }

      return super.mouseReleased(event);
   }

   public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
      if (this.open != null) {
         return true;
      }

      if (inside(mx, my, this.listX, this.listY, this.listW, this.listH)) {
         this.scroll = Math.max(0, Math.min(Math.max(0, this.contentH - this.listH), this.scroll - (int) Math.round(scrollY * 22)));
         return true;
      }

      return super.mouseScrolled(mx, my, scrollX, scrollY);
   }

   public boolean keyPressed(KeyEvent event) {
      if (event.key() == GLFW.GLFW_KEY_ESCAPE && this.open != null) {
         this.open = null;
         return true;
      }

      if (event.key() == GLFW.GLFW_KEY_ESCAPE && this.searching && this.search.isFocused()) {
         this.searching = false;
         this.search.setValue("");
         this.search.setVisible(false);
         this.setFocused(null);
         return true;
      }

      return super.keyPressed(event);
   }

   private void scrollTo(double my) {
      double f = (my - this.listY) / Math.max(1, this.listH);
      this.scroll = (int) Math.max(0, Math.min(this.contentH - this.listH, f * this.contentH - this.listH / 2.0));
   }

   private void testSite() {
      siteResult = "Testing...";
      PriceData.async(() -> {
         SiteFinder.Site s = SiteFinder.find();
         String r = s == null ? "Not found" : "Found v" + s.version() + " :" + s.base().substring(s.base().lastIndexOf(':') + 1);
         BazaarClient.LOG.info("Settings: site test -> {}", r);
         this.minecraft.execute(() -> siteResult = r);
      });
   }

   /** 0 -> "any", 5e6 -> "5M", 2e9 -> "2B". */
   static String coins(double v) {
      if (v <= 0.0) {
         return "any";
      }

      double d = v >= 1.0E9 ? v / 1.0E9 : v / 1.0E6;
      String n = d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
      return n + (v >= 1.0E9 ? "B" : "M");
   }

   private static String linesLabel(GraphPrefs.Lines l) {
      return switch (l) {
         case BOTH -> "Both";
         case BUY -> "Buy / lowest";
         case SELL -> "Sell / average";
      };
   }

   /** Writes all the files (they only change through live() while this screen or its sub-screens are open). */
   public void removed() {
      GraphSettings.set(GraphSettings.get());
      ModSettings.set(ModSettings.get());
      AhSettings.save(AhSettings.get());
      this.runAll(this.addonCloses, "save");
   }

   private void runAll(List<Runnable> rs, String what) {
      for (Runnable r : rs) {
         try {
            r.run();
         } catch (RuntimeException e) {
            BazaarClient.LOG.warn("An add-on's settings could not {}", what, e);
         }
      }
   }

   // ---------------------------------------------------------------- add-on pages

   /** Pages and options from other mods (the "yoavaddons" entrypoint, api.YoavAddonsPlugin). A broken add-on is skipped. */
   private void buildAddonOptions() {
      this.addonResets.clear();
      this.addonCloses.clear();
      for (com.yoav3577.bazaaranalyzer.api.YoavAddonsPlugin plugin : Addons.plugins()) {
         List<Opt> before = new ArrayList<>(this.options);
         try {
            plugin.settings(new AddonSettings());
         } catch (RuntimeException | LinkageError e) {
            BazaarClient.LOG.warn("An add-on's settings page failed to load: {}", plugin.getClass().getName(), e);
            this.options.clear();
            this.options.addAll(before);
         }
      }
   }

   /** What an add-on sees: adds options to this screen. */
   private final class AddonSettings implements com.yoav3577.bazaaranalyzer.api.Settings {
      private Page page(String id) {
         Page p = Page.byId(id);
         if (p == null) {
            throw new IllegalArgumentException("Unknown settings page " + id + " (register it with page() first)");
         }

         return p;
      }

      public void page(String id, String label, String description) {
         Page.addon(id, label, description);
      }

      public void info(String page, String name, String text) {
         SettingsScreen.this.options.add(new Info(this.page(page), name, text));
      }

      public void toggle(String page, String name, String description, Supplier<Boolean> get, Consumer<Boolean> set) {
         SettingsScreen.this.options.add(new Toggle(this.page(page), name, description, get, set));
      }

      public <T> void choice(String page, String name, String description, List<T> values, Function<T, String> label, Supplier<T> get, Consumer<T> set) {
         SettingsScreen.this.options.add(new Dropdown<>(this.page(page), name, description, values, label, get, set));
      }

      public void slider(
         String page, String name, String description, int min, int max, int step, IntSupplier get, IntConsumer set, IntFunction<String> format
      ) {
         SettingsScreen.this.options.add(new Slider(this.page(page), name, description, min, max, step, get, set, format));
      }

      public void text(String page, String name, String description, String hint, String value, Consumer<String> onChange) {
         SettingsScreen.this.options.add(new Text(this.page(page), name, description, hint, value, onChange));
      }

      public void button(String page, String name, String description, Supplier<String> label, Runnable run) {
         SettingsScreen.this.options.add(new Action(this.page(page), name, description, label, run));
      }

      public void onResetAll(Runnable r) {
         SettingsScreen.this.addonResets.add(r);
      }

      public void onClose(Runnable r) {
         SettingsScreen.this.addonCloses.add(r);
      }

      public void refresh() {
         SettingsScreen.this.rebuildWidgets();
      }
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   // ---------------------------------------------------------------- dev harness

   /** "name=value" of every option on the current page. */
   String devDump() {
      StringBuilder b = new StringBuilder(this.page.name());
      for (Opt o : this.options) {
         if (o.page == this.page) {
            b.append(" [").append(o.name).append('=').append(o.devValue()).append(']');
         }
      }

      return b.toString();
   }

   /** Changes every setting on the current page once (toggles flip, dropdowns step, sliders go to 75%). */
   void devChangeAll() {
      for (Opt o : this.options) {
         if (o.page == this.page) {
            o.devChange();
         }
      }
   }

   /** Opens the search with this text. */
   void devSearch(String q) {
      this.searching = true;
      this.search.setVisible(true);
      this.search.setValue(q);
      this.setFocused(this.search);
   }

   /** Middle of an option's control on screen (after a frame was drawn), or null. */
   double[] devControl(String name) {
      for (Opt o : this.options) {
         if (o.visible && o.name.equals(name)) {
            return new double[]{o.x + o.w / 6.0, o.y + 28};
         }
      }

      return null;
   }

   // ---------------------------------------------------------------- option types

   /** One option card: name and control in the left third, description in the other two thirds. */
   private abstract class Opt {
      final Page page;
      final String name;
      final String desc;
      int x;
      int y;
      int w;
      int h;
      boolean visible;

      Opt(Page page, String name, String desc) {
         this.page = page;
         this.name = name;
         this.desc = desc;
      }

      boolean matches(String q) {
         return this.name.toLowerCase(Locale.ROOT).contains(q) || this.desc.toLowerCase(Locale.ROOT).contains(q)
            || this.page.label.toLowerCase(Locale.ROOT).contains(q);
      }

      int height(Font font, int cw) {
         return Math.max(42, 12 + 10 * MoulUi.wrap(font, this.desc, 2 * cw / 3 - 12).size());
      }

      void place(int x, int y, int w, int h) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
      }

      /** Left third's middle and width. */
      int cx() {
         return this.x + this.w / 6;
      }

      int third() {
         return this.w / 3;
      }

      int controlY() {
         return this.y + 21;
      }

      void render(GuiGraphicsExtractor g, Font font, int mx, int my) {
         MoulUi.floating(g, this.x, this.y, this.w, this.h, true);
         String n = MoulUi.fit(font, this.name, this.third() - 8);
         g.text(font, n, this.cx() - font.width(n) / 2, this.y + 7, MoulUi.TEXT_BRIGHT, true);
         this.renderDesc(g, font);
         this.control(g, font, mx, my);
         if (SettingsScreen.this.searching && !SettingsScreen.this.search.getValue().isBlank()) {
            String p = this.page.label;
            g.text(font, p, this.x + this.w - 4 - font.width(p), this.y + this.h - 11, MoulUi.TEXT_DIM, false);
         }
      }

      void renderDesc(GuiGraphicsExtractor g, Font font) {
         List<String> lines = MoulUi.wrap(font, this.desc, 2 * this.w / 3 - 12);
         for (int i = 0; i < lines.size(); i++) {
            g.text(font, lines.get(i), this.x + this.third() + 6, this.y + 6 + 10 * i, MoulUi.TEXT, true);
         }
      }

      abstract void control(GuiGraphicsExtractor g, Font font, int mx, int my);

      void click(double mx, double my, int button) {
      }

      String devValue() {
         return "";
      }

      void devChange() {
      }
   }

   private final class Info extends Opt {
      Info(Page page, String name, String desc) {
         super(page, name, desc);
      }

      void control(GuiGraphicsExtractor g, Font font, int mx, int my) {
      }
   }

   private final class Toggle extends Opt {
      private final Supplier<Boolean> get;
      private final Consumer<Boolean> set;
      private float anim = -1f;

      Toggle(Page page, String name, String desc, Supplier<Boolean> get, Consumer<Boolean> set) {
         super(page, name, desc);
         this.get = get;
         this.set = set;
      }

      void control(GuiGraphicsExtractor g, Font font, int mx, int my) {
         float target = this.get.get() ? 1f : 0f;
         this.anim = this.anim < 0 ? target : this.anim + (target - this.anim) * 0.35f;
         MoulUi.toggle(g, this.cx() - MoulUi.TOGGLE_W / 2, this.controlY(), this.anim);
      }

      void click(double mx, double my, int button) {
         if (inside(mx, my, this.cx() - MoulUi.TOGGLE_W / 2 - 4, this.controlY() - 3, MoulUi.TOGGLE_W + 8, MoulUi.TOGGLE_H + 6)) {
            this.set.accept(!this.get.get());
         }
      }

      String devValue() {
         return String.valueOf(this.get.get());
      }

      void devChange() {
         this.set.accept(!this.get.get());
      }
   }

   private final class Dropdown<T> extends Opt {
      private final List<T> values;
      private final Function<T, String> label;
      private final Supplier<T> get;
      private final Consumer<T> set;

      Dropdown(Page page, String name, String desc, List<T> values, Function<T, String> label, Supplier<T> get, Consumer<T> set) {
         super(page, name, desc);
         this.values = values;
         this.label = label;
         this.get = get;
         this.set = set;
      }

      int bw() {
         return Math.min(this.third() - 12, 100);
      }

      int bx() {
         return this.cx() - this.bw() / 2;
      }

      void control(GuiGraphicsExtractor g, Font font, int mx, int my) {
         boolean hot = SettingsScreen.this.open == this || inside(mx, my, this.bx(), this.controlY(), this.bw(), 14);
         MoulUi.dropdown(g, font, this.bx(), this.controlY(), this.bw(), 14, this.label.apply(this.get.get()), hot);
      }

      void click(double mx, double my, int button) {
         if (inside(mx, my, this.bx(), this.controlY(), this.bw(), 14)) {
            SettingsScreen.this.open = this;
         }
      }

      private int listY() {
         int ly = this.controlY() + 14;
         int lh = this.values.size() * 12 + 2;
         return ly + lh > SettingsScreen.this.height - 2 ? this.controlY() - lh : ly;
      }

      private int hovered(double mx, double my) {
         int ly = this.listY();
         if (mx < this.bx() || mx >= this.bx() + this.bw() || my < ly + 1) {
            return -1;
         }

         int i = (int) ((my - ly - 1) / 12);
         return i < this.values.size() ? i : -1;
      }

      void renderList(GuiGraphicsExtractor g, Font font, int mx, int my) {
         List<String> items = this.values.stream().map(this.label).toList();
         MoulUi.dropdownList(g, font, this.bx(), this.listY(), this.bw(), items, this.values.indexOf(this.get.get()), this.hovered(mx, my));
      }

      void clickList(double mx, double my) {
         int i = this.hovered(mx, my);
         if (i >= 0) {
            this.set.accept(this.values.get(i));
         }
      }

      String devValue() {
         return this.label.apply(this.get.get());
      }

      void devChange() {
         int i = this.values.indexOf(this.get.get());
         this.set.accept(this.values.get((i + 1) % this.values.size()));
      }
   }

   private final class Slider extends Opt {
      private final int min;
      private final int max;
      private final int step;
      private final IntSupplier get;
      private final IntConsumer set;
      private final IntFunction<String> fmt;

      Slider(Page page, String name, String desc, int min, int max, int step, IntSupplier get, IntConsumer set, IntFunction<String> fmt) {
         super(page, name, desc);
         this.min = min;
         this.max = max;
         this.step = step;
         this.get = get;
         this.set = set;
         this.fmt = fmt;
      }

      int boxW() {
         return Math.max(28, Math.min(40, this.third() / 3));
      }

      int trackW() {
         return Math.max(24, Math.min(80, this.third() - 16 - this.boxW() - 4));
      }

      int tx() {
         return this.cx() - (this.trackW() + 4 + this.boxW()) / 2;
      }

      void control(GuiGraphicsExtractor g, Font font, int mx, int my) {
         int v = Cfg.clamp(this.get.getAsInt(), this.min, this.max);
         double frac = this.max == this.min ? 0 : (v - this.min) / (double) (this.max - this.min);
         boolean hot = SettingsScreen.this.dragging == this || inside(mx, my, this.tx(), this.controlY(), this.trackW(), 14);
         MoulUi.slider(g, this.tx(), this.controlY(), this.trackW(), frac, hot);
         MoulUi.valueBox(g, font, this.tx() + this.trackW() + 4, this.controlY(), this.boxW(), 14, this.fmt.apply(v), false);
      }

      void click(double mx, double my, int button) {
         if (inside(mx, my, this.tx() - 3, this.controlY() - 2, this.trackW() + 6, 18)) {
            SettingsScreen.this.dragging = this;
            this.drag(mx);
         }
      }

      void drag(double mx) {
         double f = (mx - this.tx() - 2) / Math.max(1.0, this.trackW() - 4);
         this.apply(Cfg.fromSlider(Math.max(0.0, Math.min(1.0, f)), this.min, this.max, this.step));
      }

      private void apply(int v) {
         v = Cfg.clamp(v, this.min, this.max);
         if (v != this.get.getAsInt()) {
            this.set.accept(v);
         }
      }

      String devValue() {
         return this.fmt.apply(this.get.getAsInt());
      }

      void devChange() {
         this.apply(Cfg.fromSlider(0.75, this.min, this.max, this.step));
      }
   }

   private final class Text extends Opt {
      final EditBox box;

      Text(Page page, String name, String desc, String hint, String value, Consumer<String> onChange) {
         super(page, name, desc);
         this.box = MoulUi.textBox(SettingsScreen.this.font, hint);
         this.box.setMaxLength(512);
         this.box.setValue(value);
         this.box.setResponder(onChange);
      }

      int bw() {
         return this.third() - 10;
      }

      void placeBox() {
         this.box.setX(this.cx() - this.bw() / 2 + 4);
         this.box.setY(this.controlY() + 3);
         this.box.setWidth(this.bw() - 8);
         this.box.setHeight(10);
      }

      void control(GuiGraphicsExtractor g, Font font, int mx, int my) {
         MoulUi.valueBox(g, font, this.cx() - this.bw() / 2, this.controlY(), this.bw(), 14, null, this.box.isFocused());
      }

      String devValue() {
         return this.box.getValue();
      }
   }

   private final class Action extends Opt {
      private final Supplier<String> label;
      private final Runnable run;

      Action(Page page, String name, String desc, Supplier<String> label, Runnable run) {
         super(page, name, desc);
         this.label = label;
         this.run = run;
      }

      int bw() {
         return Math.min(this.third() - 12, 96);
      }

      void control(GuiGraphicsExtractor g, Font font, int mx, int my) {
         int bx = this.cx() - this.bw() / 2;
         MoulUi.button(g, font, bx, this.controlY() - 1, this.bw(), 16, this.label.get(), inside(mx, my, bx, this.controlY() - 1, this.bw(), 16), true);
      }

      void click(double mx, double my, int button) {
         if (inside(mx, my, this.cx() - this.bw() / 2, this.controlY() - 1, this.bw(), 16)) {
            this.run.run();
         }
      }

      String devValue() {
         return this.label.get();
      }
   }

   /** The trade button's position drawn on a small copy of the screen. */
   private final class Preview extends Opt {
      Preview(Page page) {
         super(page, "Preview", "Where the trade button will be");
      }

      int height(Font font, int cw) {
         return 70;
      }

      void renderDesc(GuiGraphicsExtractor g, Font font) {
         int sw = SettingsScreen.this.width;
         int sh = SettingsScreen.this.height;
         int bh = this.h - 12;
         int bw = Math.min(2 * this.w / 3 - 12, bh * sw / Math.max(1, sh));
         int bx = this.x + this.third() + 6;
         int by = this.y + 6;
         MoulUi.inner(g, bx, by, bw, bh);
         int cw = SettingsScreen.TRADE_W * bw / sw;
         int ch = SettingsScreen.TRADE_H * bh / sh;
         g.fill(bx + (bw - cw) / 2, by + (bh - ch) / 2, bx + (bw + cw) / 2, by + (bh + ch) / 2, 0xFF3A3A42);
         ModPrefs m = ModSettings.get();
         if (m.tradeButton()) {
            int w = font.width(TradeAhUi.label(3)) + 12;
            int[] xy = m.buttonXY(sw, sh, w, TradeAhUi.H);
            int x = bx + xy[0] * bw / sw;
            int y = by + xy[1] * bh / sh;
            g.fill(x, y, x + Math.max(2, w * bw / sw), y + Math.max(2, TradeAhUi.H * bh / sh), 0xFF5B7BB0);
         }
      }

      void control(GuiGraphicsExtractor g, Font font, int mx, int my) {
      }
   }
}
