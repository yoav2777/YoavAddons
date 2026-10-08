package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.FailureLimiter;
import com.yoav3577.bazaaranalyzer.core.GemLog;
import com.yoav3577.bazaaranalyzer.core.GemTracker;
import com.yoav3577.bazaaranalyzer.core.Parts;
import com.yoav3577.bazaaranalyzer.core.SyncData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Watches items bought in lowballs (by uuid) in the inventory; when one loses gems or other parts that came with it
 * (drill parts, pet item... see Parts), the removal goes to gem-log.jsonl so the lowball tracker can count those parts
 * when they are sold on the Bazaar or the AH soon after.
 */
final class GemWatch {
   private static final int SCAN_TICKS = 20;
   private static final List<GemLog> LOG = new ArrayList<>();
   private static final FailureLimiter failures = new FailureLimiter(600);
   private static Path file;
   private static volatile GemTracker tracker;
   private static int ticks;

   private GemWatch() {
   }

   static void init() {
      load();
      rebuild();
      ClientTickEvents.END_CLIENT_TICK.register(mc -> {
         try {
            tick(mc);
            failures.reset();
         } catch (Throwable t) {
            switch (failures.record(t.getClass().getName())) {
               case LOG_FULL -> BazaarClient.LOG.warn("Gem watch skipped a scan", t);
               case LOG_SUMMARY -> BazaarClient.LOG.warn("Gem watch: suppressed {} further failures ({})", failures.suppressedCount(), t.getClass().getSimpleName());
               case SUPPRESS -> {
               }
            }
         }
      });
   }

   private static synchronized void load() {
      file = FabricLoader.getInstance().getConfigDir().resolve("bazaaranalyzer").resolve("gem-log.jsonl");
      LOG.clear();
      if (Files.exists(file)) {
         try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
               GemLog.parse(line).ifPresent(LOG::add);
            }
         } catch (RuntimeException | IOException e) {
            BazaarClient.LOG.warn("Could not read {}", file, e);
         }
      }

      if (GemLog.watchSince(LOG) == Long.MAX_VALUE) {
         add(GemLog.start(System.currentTimeMillis()));
      }

      BazaarClient.LOG.info("Gem watch: {} gem log entries", LOG.size());
   }

   /** Call after a player trade is recorded (client thread). */
   static void rebuild() {
      tracker = GemTracker.build(PlayerTradeStore.all(), all(), System.currentTimeMillis());
   }

   static synchronized List<GemLog> all() {
      return new ArrayList<>(LOG);
   }

   /** Entries another PC uploaded (CloudSync, one JSON line each): the ones not here yet go into the log and the file. */
   static synchronized int addSynced(List<String> lines) {
      if (file == null) {
         return 0;
      }

      int added = 0;
      for (String l : SyncData.missing(LOG.stream().map(GemLog::toJson).toList(), lines)) {
         GemLog e = GemLog.parse(l).orElse(null);
         if (e != null) {
            add(e);
            added++;
         }
      }

      return added;
   }

   private static synchronized void add(GemLog e) {
      LOG.add(e);
      String json = e.toJson() + "\n";
      Path f = file;
      ModIo.submit(() -> {
         try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         } catch (IOException ex) {
            BazaarClient.LOG.warn("Could not save gem log", ex);
         }
      });
   }

   private static void tick(Minecraft mc) {
      GemTracker t = tracker;
      if (++ticks < SCAN_TICKS || t == null || t.isEmpty() || mc.player == null) {
         return;
      }

      ticks = 0;
      Inventory inv = mc.player.getInventory();

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         CompoundTag tag = ItemReader.tag(stack);
         String uuid = tag == null ? null : tag.getString("uuid").orElse(null);
         if (t.tracks(uuid)) {
            String name = stack.getHoverName().getString();
            GemLog e = t.observe(uuid, name, Parts.of(ItemReader.gems(tag), ItemReader.parts(tag)), System.currentTimeMillis(), id -> nameInInventory(inv, id));
            if (e != null) {
               add(e);
               String parts = e.parts().stream().map(Parts.Part::name).collect(Collectors.joining(", "));
               if (e.kind() == GemLog.Kind.REMOVED) {
                  BazaarClient.LOG.info("Gem watch: {} removed from {} ({})", parts, name, uuid);
                  AhTabs.say(
                     parts + " removed from " + name + ". Sell " + (e.parts().size() == 1 ? "it" : "them")
                        + " (Bazaar or AH) within a day and it counts in the lowball profit.",
                     ChatFormatting.GRAY
                  );
               } else {
                  BazaarClient.LOG.info("Gem watch: {} came with {}", name, parts);
               }
            }
         }
      }
   }

   /** The display name of the removed part as it now sits in the inventory, or a name made from its id. */
   private static String nameInInventory(Inventory inv, String id) {
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (id.equals(ItemReader.skyblockId(stack))) {
            return stack.getHoverName().getString();
         }
      }

      return Parts.name(id);
   }
}
