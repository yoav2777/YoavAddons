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
import java.util.Map;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * hour of mining.
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
            if (p.enabled() && p.hud() && !mc.gui.hud.isHidden() && mc.player != null && onIsland() && !(mc.gui.screen() instanceof MiningHudScreen)) {
               draw(g, mc.font, lines(p, false), p);
            }
         } catch (Throwable t) {
            log("Mining box skipped a frame", t);
         }
      });
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
      if (!p.enabled() || !SackMessage.isSacks(message.getString()) || !onIsland() && System.currentTimeMillis() - lastOnIsland > LEAVE_GRACE_MS) {
         return false;
      }

      List<String> hovers = new ArrayList<>();
      hovers(message, hovers, 0);
      for (Map.Entry<String, Long> e : SackMessage.changes(hovers).entrySet()) {
         String id = names.id(e.getKey());
         session.add(id != null ? id : e.getKey(), e.getValue());
      }

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

   /** The box's text. sample: made-up rows when nothing is tracked yet (for the move screen). */
   static List<String> lines(MiningPrefs p, boolean sample) {
      List<String> out = new ArrayList<>();
      MiningSession s = session;
      if (sample && s.isEmpty()) {
         out.add("§6§lMining Profit §7(12m 30s)");
         if (p.show() != MiningPrefs.Show.HOURLY) {
            out.add("§fProfit: §6" + Coins.shortFmt(10200000));
         }

         if (p.show() != MiningPrefs.Show.TOTAL) {
            out.add("§fPer hour: §6" + Coins.shortFmt(48960000) + "/h");
         }

         String[][] rows = {{"Enchanted Mithril", "512", "6.1M"}, {"Fine Ruby Gemstone", "3.2", "2.4M"}, {"Enchanted Titanium", "14", "1.7M"}};
         for (int i = 0; i < Math.min(p.lines(), rows.length); i++) {
            out.add("§7" + rows[i][0] + " §fx" + rows[i][1] + " §8» §6" + rows[i][2]);
         }

         return out;
      }

      long now = System.currentTimeMillis();
      ItemNames n = names;
      // An item seen before the item list loaded is kept by name: look its id up again now.
      UnaryOperator<String> id = k -> {
         String found = n.id(k);
         return found != null ? found : k;
      };
      List<MiningSession.Line> rows = s.lines(p.tier(), k -> price(id.apply(k)), k -> n.name(id.apply(k)));
      double total = MiningSession.total(rows);
      String state = !s.started() ? "" : " §7(" + Coins.time(s.activeMs()) + ")" + (s.paused(now, p.idle() * 1000L) ? " §8paused" : "");
      out.add("§6§lMining Profit" + state);
      if (s.isEmpty()) {
         out.add("§7Start mining: sack gains show here.");
         return out;
      }

      if (p.show() != MiningPrefs.Show.HOURLY) {
         out.add("§fProfit: §6" + Coins.shortFmt(total));
      }

      if (p.show() != MiningPrefs.Show.TOTAL) {
         double ph = MiningSession.perHour(total, s.activeMs());
         out.add("§fPer hour: §6" + (Double.isNaN(ph) ? "§7..." : Coins.shortFmt(ph) + "/h"));
      }

      int shown = Math.min(p.lines(), rows.size());
      for (int i = 0; i < shown; i++) {
         MiningSession.Line l = rows.get(i);
         out.add("§7" + l.name() + " §fx" + Coins.count(l.count()) + " §8» " + (Double.isNaN(l.value()) ? "§8?" : "§6" + Coins.shortFmt(l.value())));
      }

      if (rows.size() > shown && p.lines() > 0) {
         out.add("§8+" + (rows.size() - shown) + " more");
      }

      if (PriceData.products().isEmpty()) {
         out.add("§8Loading bazaar prices...");
      }

      return out;
   }

   /** Draws the box where the settings put it; returns its {x, y, w, h} on screen (after scaling). */
   static int[] draw(GuiGraphicsExtractor g, Font font, List<String> lines, MiningPrefs p) {
      float s = p.scale() / 100.0F;
      int w = 0;
      for (String l : lines) {
         w = Math.max(w, font.width(l));
      }

      w += 10;
      int h = lines.size() * 10 + 5;
      int sw = Math.round(w * s);
      int sh = Math.round(h * s);
      int[] xy = p.place(g.guiWidth(), g.guiHeight(), sw, sh);
      g.pose().pushMatrix();
      g.pose().translate(xy[0], xy[1]);
      g.pose().scale(s, s);
      g.fill(0, 0, w, h, 0xA0101016);
      g.fill(0, 0, 2, h, 0xFFFFAA00);
      for (int i = 0; i < lines.size(); i++) {
         g.text(font, lines.get(i), 6, 3 + i * 10, 0xFFFFFFFF, true);
      }

      g.pose().popMatrix();
      return new int[]{xy[0], xy[1], sw, sh};
   }
}
