package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.CaptureFilter;
import com.yoav3577.bazaaranalyzer.core.SyncData;
import com.yoav3577.bazaaranalyzer.core.Trade;
import com.yoav3577.bazaaranalyzer.core.TradeBook;
import com.yoav3577.bazaaranalyzer.core.TradeParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class Capture {
   private static final long MAX_BYTES = 5242880L;
   private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "bazaaranalyzer-capture");
      t.setDaemon(true);
      return t;
   });
   private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
   private static Path file;
   /** Lines other PCs captured (CloudSync), kept apart so capture.log stays this PC's own chat in time order. */
   private static Path synced;

   private Capture() {
   }

   public static void init() {
      Path dir = FabricLoader.getInstance().getConfigDir().resolve("bazaaranalyzer");
      file = dir.resolve("capture.log");
      synced = dir.resolve("capture-sync.log");
      loadHistory();
      ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
         if (!overlay) {
            try {
               String line = message.getString();
               if (!CaptureFilter.interesting(line)) {
                  return;
               }

               long now = System.currentTimeMillis();
               TradeParser.parse(now, line).ifPresent(BazaarClient.BOOK::add);
               String record = now + "\t" + CaptureFilter.oneLine(line) + "\n";
               IO.execute(() -> append(record));
            } catch (Throwable var6) {
               BazaarClient.LOG.warn("Capture skipped a message", var6);
            }
         }
      });
      BazaarClient.LOG.info("Capturing Bazaar/Auction lines to {}", file);
   }

   /** Runs on IO (off the init thread, and before any append queued behind it); reads files newest first until the book is full. */
   private static void loadHistory() {
      IO.execute(() -> {
         List<Path> files = new ArrayList<>();
         try (Stream<Path> s = Files.list(file.getParent())) {
            // capture.log.1 (before 0.7.1) < capture-<date>.log (sorted by name = by time) < capture.log
            s.filter(p -> p.getFileName().toString().matches("capture-\\d{8}-\\d{6}\\.log")).sorted().forEach(files::add);
         } catch (IOException | RuntimeException var6) {
            // no folder yet
         }
         files.add(0, file.resolveSibling("capture.log.1"));
         files.add(file);

         for (int i = files.size() - 1; i >= 0 && !BazaarClient.BOOK.full(); i--) {
            Path p = files.get(i);
            try {
               if (Files.exists(p)) {
                  BazaarClient.BOOK.loadCaptureTail(List.of(Files.readAllLines(p, StandardCharsets.UTF_8)));
               }
            } catch (RuntimeException | IOException var5) {
               BazaarClient.LOG.warn("Could not read {}", p, var5);
            }
         }

         try {
            if (Files.exists(synced)) {
               List<Trade> more = new ArrayList<>();
               for (String l : Files.readAllLines(synced, StandardCharsets.UTF_8)) {
                  TradeBook.parseRecord(l).ifPresent(more::add);
               }

               BazaarClient.BOOK.addSorted(more);
            }
         } catch (RuntimeException | IOException e) {
            BazaarClient.LOG.warn("Could not read {}", synced, e);
         }

         BazaarClient.LOG.info("Loaded {} past trades from the capture files", BazaarClient.BOOK.size());
      });
   }

   /** A trade as the line CloudSync uploads (the capture.log record it was read from). */
   static String syncLine(Trade t) {
      return t.ts() + "\t" + CaptureFilter.oneLine(t.raw());
   }

   /**
    * Lines another PC uploaded (CloudSync, "ts\tline" since {@code from}): the trades not in the book yet go into it and
    * into capture-sync.log. Runs on IO, so after the history has loaded.
    */
   static void addSynced(List<String> lines, long from) {
      IO.execute(() -> {
         try {
            List<String> have = BazaarClient.BOOK.snapshot().stream().filter(t -> t.ts() >= from).map(Capture::syncLine).toList();
            List<Trade> more = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (String l : SyncData.missing(have, lines.stream().filter(l -> SyncData.ts(l) >= from).toList())) {
               TradeBook.parseRecord(l).ifPresent(t -> {
                  more.add(t);
                  text.append(l).append('\n');
               });
            }

            if (!more.isEmpty()) {
               BazaarClient.BOOK.addSorted(more);
               Files.createDirectories(synced.getParent());
               Files.writeString(synced, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
               BazaarClient.LOG.info("Cloud sync: {} Bazaar/AH lines from other PCs", more.size());
            }
         } catch (IOException | RuntimeException e) {
            BazaarClient.LOG.warn("Could not save synced capture lines", e);
         }
      });
   }

   private static void append(String record) {
      try {
         Files.createDirectories(file.getParent());
         if (Files.exists(file) && Files.size(file) > MAX_BYTES) {
            // kept forever (the AH & Bazaar history); ~5 MB each
            Files.move(file, file.resolveSibling("capture-" + STAMP.format(LocalDateTime.now()) + ".log"), StandardCopyOption.REPLACE_EXISTING);
         }

         Files.writeString(file, record, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (IOException var2) {
         BazaarClient.LOG.warn("Could not write capture log", var2);
      }
   }
}
