package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.PlayerTrade;
import com.yoav3577.bazaaranalyzer.core.PlayerTradeJson;
import com.yoav3577.bazaaranalyzer.core.SyncData;
import com.yoav3577.bazaaranalyzer.core.TradeDedupe;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

final class PlayerTradeStore {
   private static final List<PlayerTrade> TRADES = new ArrayList<>();
   private static Path file;

   private PlayerTradeStore() {
   }

   static synchronized void load() {
      file = FabricLoader.getInstance().getConfigDir().resolve("bazaaranalyzer").resolve("player-trades.jsonl");
      TRADES.clear();
      if (Files.exists(file)) {
         List<PlayerTrade> read = new ArrayList<>();

         try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
               PlayerTradeJson.parse(line).ifPresent(read::add);
            }
         } catch (RuntimeException | IOException var3) {
            BazaarClient.LOG.warn("Could not read {}", file, var3);
         }

         TRADES.addAll(TradeDedupe.merge(read));
         BazaarClient.LOG.info("Loaded {} past player trades ({} duplicates merged)", TRADES.size(), read.size() - TRADES.size());
      }
   }

   static synchronized void add(PlayerTrade t) {
      TRADES.add(t);
      List<PlayerTrade> merged = TradeDedupe.merge(TRADES);
      TRADES.clear();
      TRADES.addAll(merged);

      String json = t.toJson() + "\n";
      Path f = file;
      ModIo.submit(() -> {
         try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         } catch (IOException var3) {
            BazaarClient.LOG.warn("Could not save player trade", var3);
         }
      });
   }

   /** Trades another PC uploaded (CloudSync, one JSON line each): the ones not here yet go into the list and the file. */
   static synchronized int addSynced(List<String> lines) {
      List<PlayerTrade> fresh = new ArrayList<>();
      for (String l : SyncData.missing(TRADES.stream().map(PlayerTrade::toJson).toList(), lines)) {
         PlayerTradeJson.parse(l).ifPresent(fresh::add);
      }

      List<PlayerTrade> all = new ArrayList<>(TRADES);
      all.addAll(fresh);
      List<PlayerTrade> merged = TradeDedupe.merge(all);
      Set<PlayerTrade> kept = Collections.newSetFromMap(new IdentityHashMap<>());
      kept.addAll(merged);
      StringBuilder json = new StringBuilder();
      int added = 0;
      for (PlayerTrade t : fresh) {
         // one that merged into a trade already here is not written again
         if (kept.contains(t)) {
            json.append(t.toJson()).append('\n');
            added++;
         }
      }

      TRADES.clear();
      TRADES.addAll(merged);
      Path f = file;
      if (added > 0 && f != null) {
         String text = json.toString();
         ModIo.submit(() -> {
            try {
               Files.createDirectories(f.getParent());
               Files.writeString(f, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
               BazaarClient.LOG.warn("Could not save synced player trades", e);
            }
         });
      }

      return added;
   }

   static synchronized List<PlayerTrade> all() {
      return new ArrayList<>(TRADES);
   }
}
