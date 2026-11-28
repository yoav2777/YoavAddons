package com.yoav3577.bazaaranalyzer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yoav3577.bazaaranalyzer.core.Coins;
import com.yoav3577.bazaaranalyzer.core.FailureLimiter;
import com.yoav3577.bazaaranalyzer.core.ItemNames;
import com.yoav3577.bazaaranalyzer.core.MiningItems;
import com.yoav3577.bazaaranalyzer.core.MiningPrefs;
import com.yoav3577.bazaaranalyzer.core.MiningSession;
import com.yoav3577.bazaaranalyzer.core.SackMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

/**
 * Mining profit tracker, on mining islands only. Counts what goes into your sacks (Hypixel's "[Sacks] +N items" chat line,
 * the list is in its hover text) and what lands in your inventory (bazaar items, when your sacks are full or the item has
 * no sack), adds it up in a MiningSession and shows a draggable box with the bazaar worth (insta-sell), in total and per
 * hour of mining. In inventories the box has buttons: price as, display mode, reset, pause.
 */
final class MiningTracker {
   /** Sack lines can arrive up to ~30 s after the items went in: count them this long after leaving a mining island. */
   private static final long LEAVE_GRACE_MS = 45000L;
   private static final long PRICE_MS = 300000L;
   private static final FailureLimiter failures = new FailureLimiter(600);
   private static MiningSession session = new MiningSession();
   private static volatile ItemNames names = ItemNames.base();
   private static volatile boolean namesLoaded;
   /** Inventory contents at the last look (family raw id or bazaar id -> raw count), null = take a fresh look first. */
   private static Map<String, Long> inventory;
   private static ClientLevel level;
   private static String area;
   private static long lastOnIsland;
   /** Mining time when the last sack line came: the hourly rate only moves with the sack lines, so it does not jump around. */
   private static long rateMs;
   /** Pause session (inventory button): nothing counts and the timer stops. */
   private static boolean paused;
   private static long pricesAt;
   private static int ticks;

   private MiningTracker() {
   }

   static void init() {
      ClientTickEvents.END_CLIENT_TICK.register(mc -> {
         try {
            tick(mc);
            failures.reset();
         } catch (Throwable t) {
            log("Mining tracker skipped a tick", t);
         }
      });
      ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
         try {
            return overlay || !chat(message);
         } catch (Throwable t) {
            log("Mining tracker could not read a chat line", t);
            return true;
         }
      });
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(BazaarClient.MOD_ID, "mining_tracker"), (g, delta) -> {
         try {
            Minecraft mc = Minecraft.getInstance();
            MiningPrefs p = MiningSettings.get();
            if (p.enabled() && p.hud() && !mc.gui.hud.isHidden() && mc.player != null && onIsland() && !(mc.gui.screen() instanceof MiningHudScreen) && !(mc.gui.screen() instanceof AbstractContainerScreen<?>)) {
               draw(g, mc.font, rows(p, false, false), p, -1, -1);
            }
         } catch (Throwable t) {
            log("Mining box skipped a frame", t);
         }
      });
      screens();
   }

   private static void log(String what, Throwable t) {
      if (failures.record(t.getClass().getName()) == FailureLimiter.Action.LOG_FULL) {
         BazaarClient.LOG.warn(what, t);
      }
   }

   static boolean onIsland() {
      return SackMessage.isMining(area);
   }

   static void reset() {
      session = new MiningSession();
      inventory = null;
      rateMs = 0L;
   }

   static void resetWithMessage() {
      reset();
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§8[§bYA§8] §aMining tracker reset."));
      }
   }

   private static void tick(Minecraft mc) {
      if (mc.level != level) {
         level = mc.level;
         area = null;
         inventory = null;
      }

      MiningPrefs p = MiningSettings.get();
      if (!p.enabled() || mc.player == null || mc.getConnection() == null) {
         return;
      }

      long now = System.currentTimeMillis();
      if (++ticks % 20 == 0) {
         List<String> tab = new ArrayList<>();
         for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            Component name = info.getTabListDisplayName();
            if (name != null) {
               tab.add(name.getString());
            }
         }

         area = SackMessage.area(tab);
         if (onIsland() && now - pricesAt > PRICE_MS) {
            pricesAt = now;
            PriceData.async(MiningTracker::loadPrices);
         }
      }

      boolean island = onIsland();
      if (island) {
         lastOnIsland = now;
      }

      boolean mining = island && mc.gui.screen() == null && mc.options.keyAttack.isDown()
         && (mc.gameMode != null && mc.gameMode.isDestroying() || mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK);
      if (paused) {
         return;
      }

      session.tick(now, mining, p.idle() * 1000L);
      if (ticks % 10 == 0) {
         lookAtInventory(mc, island);
      }
   }

   /**
    * Items that land in the inventory count too (sacks full, items with no sack). Each family counts net in raw items,
    * so a compactor turning 160 Mithril into 1 Enchanted Mithril adds nothing; only rises count. With a screen open
    * (chests, bazaar, storage) nothing counts and the next look starts fresh.
    */
   private static void lookAtInventory(Minecraft mc, boolean island) {
      if (!island || mc.gui.screen() != null) {
         inventory = null;
         return;
      }

      Map<String, Long> now = new HashMap<>();
      Inventory inv = mc.player.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         String id = ItemReader.skyblockId(stack);
         MiningItems.Hit h = MiningItems.byId(id);
         if (h != null) {
            now.merge(h.family().id(), stack.getCount() * h.raw(), Long::sum);
         } else if (id != null && PriceData.live(id) != null) {
            now.merge(id, (long) stack.getCount(), Long::sum);
         }
      }

      Map<String, Long> before = inventory;
      inventory = now;
      if (before != null) {
         for (Map.Entry<String, Long> e : now.entrySet()) {
            long d = e.getValue() - before.getOrDefault(e.getKey(), 0L);
            if (d > 0L) {
               session.add(e.getKey(), d);
            }
         }
      }
   }

   private static void loadPrices() {
      try {
         PriceData.refreshBazaar();
         if (!namesLoaded) {
            ItemNames n = ItemNames.base();
            for (JsonElement el : JsonParser.parseString(Http.get("https://api.hypixel.net/v2/resources/skyblock/items", 20000))
               .getAsJsonObject()
               .getAsJsonArray("items")) {
               JsonObject o = el.getAsJsonObject();
               String id = o.has("id") ? o.get("id").getAsString() : null;
               if (id != null && o.has("name") && PriceData.live(id) != null) {
                  n.add(id, o.get("name").getAsString());
                  n.tier(id, o.has("tier") ? o.get("tier").getAsString() : null);
               }
            }

            PriceData.products().forEach(n::addId);
            names = n;
            namesLoaded = true;
         }
      } catch (Exception e) {
         pricesAt = System.currentTimeMillis() - PRICE_MS + 60000L; // retry in a minute
         BazaarClient.LOG.warn("Mining tracker: could not load prices ({})", e.toString());
      }
   }

   private static double price(String id) {
      PriceData.Live l = PriceData.live(id);
      return l == null ? Double.NaN : l.instaSell() > 0.0 ? l.instaSell() : l.instaBuy();
   }

   /** Reads a sack line; true when it should be hidden. */
   private static boolean chat(Component message) {
      MiningPrefs p = MiningSettings.get();
      if (!p.enabled() || paused || !SackMessage.isSacks(message.getString()) || !onIsland() && System.currentTimeMillis() - lastOnIsland > LEAVE_GRACE_MS) {
         return false;
      }

      List<String> hovers = new ArrayList<>();
      hovers(message, hovers, 0);
      for (Map.Entry<String, Long> e : SackMessage.changes(hovers).entrySet()) {
         String id = names.id(e.getKey());
         session.add(id != null ? id : e.getKey(), e.getValue());
      }

      rateMs = session.activeMs();
      return p.hideSackChat();
   }

   private static void hovers(Component c, List<String> out, int depth) {
      if (depth > 16) {
         return;
      }

      if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText st) {
         out.add(st.value().getString());
      }

      for (Component s : c.getSiblings()) {
         hovers(s, out, depth + 1);
      }
   }

   // ---------------------------------------------------------------- the box

   /** A line of the box: plain text, or an item row (count, name, worth in columns); click = a button (inventory only). */
   record Row(String text, String count, String name, String value, Runnable click) {
      static Row text(String t) {
         return new Row(t, null, null, null, null);
      }

      static Row button(String t, Runnable click) {
         return new Row(t, null, null, null, click);
      }

      static Row item(String count, String name, String value) {
         return new Row(null, count, name, value, null);
      }
   }

   /** The box as drawn last in a container screen, for clicks: x, y, w, h, and its rows. */
   private static int[] shownBox;
   private static List<Row> shownRows = List.of();

   private static void screens() {
      ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
         if (!(screen instanceof AbstractContainerScreen<?>)) {
            return;
         }

         ScreenEvents.afterExtract(screen).register((sc, g, mx, my, delta) -> {
            try {
               MiningPrefs p = MiningSettings.get();
               shownBox = null;
               if (p.enabled() && p.hud() && onIsland()) {
                  shownRows = rows(p, false, true);
                  shownBox = draw(g, mc.font, shownRows, p, mx, my);
               }
            } catch (Throwable t) {
               log("Mining box skipped a frame", t);
            }
         });
         ScreenMouseEvents.allowMouseClick(screen).register((sc, event) -> {
            int[] b = shownBox;
            if (b == null || event.button() != 0 || event.x() < b[0] || event.x() > b[0] + b[2] || event.y() < b[1] || event.y() > b[1] + b[3]) {
               return true;
            }

            int i = (int) ((event.y() - b[1]) / (b[3] / (double) Math.max(1, shownRows.size())));
            Runnable r = i >= 0 && i < shownRows.size() ? shownRows.get(i).click() : null;
            if (r == null) {
               return true;
            }

            r.run();
            return false;
         });
      });
   }

   private static <E extends Enum<E>> E next(E e) {
      E[] all = e.getDeclaringClass().getEnumConstants();
      return all[(e.ordinal() + 1) % all.length];
   }

   private static String coins(double v) {
      return Double.isNaN(v) ? "..." : String.format(Locale.ROOT, "%,d coins", Math.round(v));
   }

   /**
    * The box's lines, laid out like SkyHanni's trackers. sample: made-up rows when nothing is tracked yet (move screen);
    * inventory: with the buttons (price as, display, reset, pause).
    */
   static List<Row> rows(MiningPrefs p, boolean sample, boolean inventory) {
      List<Row> out = new ArrayList<>();
      out.add(Row.text("§e§lMining Profit Tracker"));
      if (inventory) {
         out.add(Row.button("§7Price as §e[" + p.tier().label + "]", () -> MiningSettings.set(MiningSettings.get().withTier(next(MiningSettings.get().tier())))));
      }

      MiningSession s = session;
      double total;
      double perHour;
      int more = 0;
      if (sample && s.isEmpty()) {
         String[][] rows = {{"512x", "§9Enchanted Mithril", "6.1M"}, {"3x", "§5Fine Ruby Gemstone", "2.4M"}, {"14x", "§5Enchanted Titanium", "1.7M"}};
         for (int i = 0; i < Math.min(p.lines(), rows.length); i++) {
            out.add(Row.item("§7" + rows[i][0], rows[i][1], "§6" + rows[i][2]));
         }

         total = 10200000;
         perHour = 48960000;
      } else {
         ItemNames n = names;
         // An item seen before the item list loaded is kept by name: look its id up again now.
         UnaryOperator<String> id = k -> {
            String found = n.id(k);
            return found != null ? found : k;
         };
         List<MiningSession.Line> lines = s.lines(p.tier(), k -> price(id.apply(k)), k -> n.name(id.apply(k)));
         total = MiningSession.total(lines);
         perHour = MiningSession.perHour(total, rateMs);
         int shown = Math.min(p.lines(), lines.size());
         for (int i = 0; i < shown; i++) {
            MiningSession.Line l = lines.get(i);
            out.add(Row.item("§7" + Coins.count(l.count()) + "x", n.color(id.apply(l.id())) + l.name(), Double.isNaN(l.value()) ? "§8?" : "§6" + Coins.shortFmt(l.value())));
         }

         more = lines.size() - shown;
         if (s.isEmpty()) {
            out.add(Row.text("§7Start mining: sack gains show here."));
         }
      }

      if (more > 0 && p.lines() > 0) {
         out.add(Row.text("§7 +" + more + " more"));
      }

      if (p.show() != MiningPrefs.Show.HOURLY) {
         out.add(Row.text("§6Session Profit: " + coins(total)));
      }

      if (p.show() != MiningPrefs.Show.TOTAL) {
         out.add(Row.text("§6Profit Per Hour: " + coins(perHour)));
      }

      if (inventory) {
         out.add(Row.button("§7Display Mode §e[" + p.show().label + "]", () -> MiningSettings.set(MiningSettings.get().withShow(next(MiningSettings.get().show())))));
      }

      long ms = sample && s.isEmpty() ? 750000L : s.activeMs();
      out.add(Row.text("§eTotal Uptime: §b" + Coins.time(ms) + (paused ? " §c(paused)" : "")));
      if (!sample && PriceData.products().isEmpty()) {
         out.add(Row.text("§8Loading bazaar prices..."));
      }

      if (inventory) {
         out.add(Row.button("§cReset session!", MiningTracker::reset));
         out.add(Row.button(paused ? "§aResume session!" : "§cPause session!", () -> {
            paused = !paused;
            MiningTracker.inventory = null;
         }));
      }

      return out;
   }

   /** Draws the box where the settings put it (rows that are buttons light up under the mouse); returns its {x, y, w, h} on screen. */
   static int[] draw(GuiGraphicsExtractor g, Font font, List<Row> rows, MiningPrefs p, int mouseX, int mouseY) {
      int countW = 0;
      int nameW = 0;
      for (Row r : rows) {
         if (r.text() == null) {
            countW = Math.max(countW, font.width(r.count()));
            nameW = Math.max(nameW, font.width(r.name()));
         }
      }

      int nameX = countW + 4;
      int valueX = nameX + nameW + 6;
      int w = 0;
      for (Row r : rows) {
         w = Math.max(w, r.text() != null ? font.width(r.text()) : valueX + font.width(r.value()));
      }

      int h = rows.size() * 10;
      float s = p.scale() / 100.0F;
      int sw = Math.round(w * s);
      int sh = Math.round(h * s);
      int[] xy = p.place(g.guiWidth(), g.guiHeight(), sw, sh);
      g.pose().pushMatrix();
      g.pose().translate(xy[0], xy[1]);
      g.pose().scale(s, s);
      for (int i = 0; i < rows.size(); i++) {
         Row r = rows.get(i);
         int y = i * 10;
         if (r.click() != null && mouseX >= xy[0] && mouseX <= xy[0] + sw && mouseY >= xy[1] + y * s && mouseY < xy[1] + (y + 10) * s) {
            g.fill(-1, y - 1, w + 1, y + 9, 0x40FFFFFF);
         }

         if (r.text() != null) {
            g.text(font, r.text(), 0, y, 0xFFFFFFFF, true);
         } else {
            g.text(font, r.count(), countW - font.width(r.count()), y, 0xFFFFFFFF, true);
            g.text(font, r.name(), nameX, y, 0xFFFFFFFF, true);
            g.text(font, r.value(), valueX, y, 0xFFFFFFFF, true);
         }
      }

      g.pose().popMatrix();
      return new int[]{xy[0], xy[1], sw, sh};
   }
}
