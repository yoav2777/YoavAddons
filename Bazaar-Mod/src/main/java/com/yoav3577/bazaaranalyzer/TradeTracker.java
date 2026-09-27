package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.AhLink;
import com.yoav3577.bazaaranalyzer.core.FailureLimiter;
import com.yoav3577.bazaaranalyzer.core.ItemMods;
import com.yoav3577.bazaaranalyzer.core.PlayerTrade;
import com.yoav3577.bazaaranalyzer.core.TradeChat;
import com.yoav3577.bazaaranalyzer.core.TradeItem;
import com.yoav3577.bazaaranalyzer.core.TradeWindow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

final class TradeTracker {
   private static final int WATCH_TICKS = 80;
   private static final long MAX_DEBUG_BYTES = 2097152L;
   private static TradeWindow.Snapshot last;
   private static String lastDump = "";
   private static Map<String, Integer> invBefore = Map.of();
   private static TradeTracker.Watch watch;
   private static TradeWindow.Snapshot seen;
   private static List<ItemMods> lastAh = List.of();
   private static volatile List<ItemMods> ahNow = List.of();
   private static long lastOpenTs;
   private static volatile long cancelChatAt;
   private static volatile long completeChatAt;
   // Change detection for the open trade window: slot stack identities + counts from the last rebuild (null = window not open).
   private static Object[] lastStacks;
   private static int[] lastCounts;
   private static final FailureLimiter tickFailures = new FailureLimiter(600);

   private TradeTracker() {
   }

   static void init() {
      PlayerTradeStore.load();
      ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
         if (!overlay) {
            String line = message.getString().strip();
            if (TradeChat.isCancel(line)) {
               cancelChatAt = System.currentTimeMillis();
            } else if (TradeChat.isComplete(line)) {
               completeChatAt = System.currentTimeMillis();
            }
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register(mc -> {
         try {
            tick(mc);
            tickFailures.reset();
         } catch (Throwable var2) {
            onTickFailure(var2);
         }
      });
   }

   private static void onTickFailure(Throwable t) {
      switch (tickFailures.record(t.getClass().getName())) {
         case LOG_FULL -> BazaarClient.LOG.warn("Trade tracking skipped a tick", t);
         case LOG_SUMMARY -> BazaarClient.LOG
            .warn("Trade tracking: suppressed {} further failures ({})", tickFailures.suppressedCount(), t.getClass().getSimpleName());
         case SUPPRESS -> {
         }
      }
   }

   private static void tick(Minecraft mc) {
      if (mc.screen instanceof AbstractContainerScreen<?> cs && TradeWindow.isTradeTitle(cs.getTitle().getString())) {
         watch = null;
         List<Slot> slots = cs.getMenu().slots;
         lastOpenTs = System.currentTimeMillis();
         if (lastStacks == null) {
            // A new trade window: chat stamps from an earlier trade must not decide this one.
            cancelChatAt = 0L;
            completeChatAt = 0L;
         }
         if (!slotsChanged(slots)) {
            return;
         }
         int container = Math.min(54, slots.size() - 36);
         List<TradeWindow.RawStack> raw = new ArrayList<>(container);

         for (int i = 0; i < container; i++) {
            raw.add(ItemReader.read(slots.get(i).getItem()));
         }

         Optional<TradeWindow.Snapshot> snap = TradeWindow.parse(cs.getTitle().getString(), raw);
         if (snap.isPresent() && !snap.get().equals(seen)) {
            seen = snap.get();
            ahNow = readAh(slots);
            if (!seen.isEmpty()) {
               last = seen;
               lastAh = ahNow;
               lastDump = dump(cs.getTitle().getString(), raw);
            }

            TradeWorth.update(slots);
         }

         invBefore = inventory(mc);
      } else {
         lastStacks = null;
         lastCounts = null;
         if (last != null && watch == null && mc.screen == null) {
            watch = new TradeTracker.Watch(last, invBefore, lastDump, new int[]{WATCH_TICKS}, lastAh, lastOpenTs);
            last = null;
            lastAh = List.of();
         }

         if (!(mc.screen instanceof AbstractContainerScreen<?> open && TradeWindow.isTradeTitle(open.getTitle().getString()))) {
            seen = null;
            ahNow = List.of();
            TradeWorth.clear();
         }

         if (watch != null) {
            Boolean verdict = decide(watch, inventory(mc), --watch.ticksLeft()[0] <= 0);
            if (verdict != null) {
               TradeTracker.Watch w = watch;
               watch = null;
               cancelChatAt = 0L;
               completeChatAt = 0L;
               if (verdict) {
                  complete(w);
               } else {
                  AhTabs.offerAfterCancel(w.snap().partner(), w.ah());
               }

               writeDebug(w.dump() + "verdict: " + (verdict ? "COMPLETED" : "cancelled") + "\n");
            }
         }
      }
   }

   static Boolean decide(TradeTracker.Watch w, Map<String, Integer> now, boolean timeout) {
      Boolean chat = TradeChat.verdict(cancelChatAt, completeChatAt, w.openTs() - 3000L);
      if (chat != null) {
         return chat;
      } else {
         int gain = 0;
         int regain = 0;

         for (TradeItem i : w.snap().received()) {
            gain += Math.max(0, Math.min(now.getOrDefault(key(i), 0) - w.before().getOrDefault(key(i), 0), i.count()));
         }

         for (TradeItem i : w.snap().given()) {
            regain += Math.max(0, Math.min(now.getOrDefault(key(i), 0) - w.before().getOrDefault(key(i), 0), i.count()));
         }

         if (gain > 0) {
            return Boolean.TRUE;
         } else if (regain > 0) {
            return Boolean.FALSE;
         } else {
            return !timeout ? null : w.snap().received().isEmpty() && !w.snap().given().isEmpty();
         }
      }
   }

   /** True when any slot's stack object or count differs from the last call (the client swaps stacks on every slot packet). */
   private static boolean slotsChanged(List<Slot> slots) {
      int n = slots.size();
      boolean changed = lastStacks == null || lastStacks.length != n;
      if (changed) {
         lastStacks = new Object[n];
         lastCounts = new int[n];
      }

      for (int i = 0; i < n; i++) {
         ItemStack st = slots.get(i).getItem();
         if (lastStacks[i] != st || lastCounts[i] != st.getCount()) {
            lastStacks[i] = st;
            lastCounts[i] = st.getCount();
            changed = true;
         }
      }

      return changed;
   }

   static int theirAhCount() {
      return ahNow.size();
   }

   private static List<ItemMods> readAh(List<Slot> slots) {
      if (PriceData.products().isEmpty()) {
         PriceData.async(() -> {
            try {
               PriceData.refreshBazaar();
            } catch (IOException var1x) {
            }
         });
      }

      List<ItemMods> out = new ArrayList<>();

      for (int idx : TradeWindow.theirSlotIndexes()) {
         if (idx < slots.size()) {
            ItemMods m = ItemReader.mods(slots.get(idx).getItem());
            if (m != null && AhLink.isAhItem(m, id -> PriceData.products().contains(id))) {
               out.add(m);
            }
         }
      }

      return out;
   }

   private static void complete(TradeTracker.Watch w) {
      TradeWindow.Snapshot s = w.snap();
      PlayerTrade trade = new PlayerTrade(System.currentTimeMillis(), s.partner(), s.givenCoins(), s.receivedCoins(), s.given(), s.received());
      if (!ModSettings.get().recordTrades()) {
         BazaarClient.LOG.info("Player trade with {} not recorded (turned off in the settings)", s.partner());
         return;
      }

      PlayerTradeStore.add(trade);
      GemWatch.rebuild();
      BazaarClient.LOG
         .info(
            "Player trade recorded with {} (gave {} coins, {} items; got {} coins, {} items)",
            s.partner(), s.givenCoins(), s.given().size(), s.receivedCoins(), s.received().size()
         );
   }

   private static String key(TradeItem i) {
      return i.uuid() != null ? "u:" + i.uuid() : "i:" + (i.id() != null ? i.id() : i.name());
   }

   private static Map<String, Integer> inventory(Minecraft mc) {
      Map<String, Integer> m = new HashMap<>();
      if (mc.player == null) {
         return m;
      } else {
         Inventory inv = mc.player.getInventory();

         for (int i = 0; i < inv.getContainerSize(); i++) {
            TradeWindow.RawStack r = ItemReader.read(inv.getItem(i));
            if (r != null) {
               m.merge(r.uuid() != null ? "u:" + r.uuid() : "i:" + (r.id() != null ? r.id() : r.name()), r.count(), Integer::sum);
            }
         }

         return m;
      }
   }

   private static String dump(String title, List<TradeWindow.RawStack> raw) {
      StringBuilder sb = new StringBuilder("=== ").append(System.currentTimeMillis()).append("  title=[").append(title).append("]\n");

      for (int i = 0; i < raw.size(); i++) {
         TradeWindow.RawStack r = raw.get(i);
         if (r != null) {
            sb.append(i)
               .append(" | ")
               .append(r.name())
               .append(" | x")
               .append(r.count())
               .append(" | id=")
               .append(r.id())
               .append(" | uuid=")
               .append(r.uuid())
               .append(" | gems=")
               .append(r.gems().size());

            for (int l = 0; l < Math.min(3, r.lore().size()); l++) {
               sb.append(" | ").append(r.lore().get(l));
            }

            sb.append('\n');
         }
      }

      return sb.toString();
   }

   private static void writeDebug(String text) {
      Path p = FabricLoader.getInstance().getConfigDir().resolve("bazaaranalyzer").resolve("trade-debug.log");
      ModIo.submit(() -> {
         try {
            Files.createDirectories(p.getParent());
            if (Files.exists(p) && Files.size(p) > MAX_DEBUG_BYTES) {
               Files.move(p, p.resolveSibling("trade-debug.log.1"), StandardCopyOption.REPLACE_EXISTING);
            }

            Files.writeString(p, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         } catch (IOException var3) {
            BazaarClient.LOG.warn("Could not write trade debug log", var3);
         }
      });
   }

   private record Watch(TradeWindow.Snapshot snap, Map<String, Integer> before, String dump, int[] ticksLeft, List<ItemMods> ah, long openTs) {
   }
}
