package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.CaptureFilter;
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

   private Capture() {
   }

   public static void init() {
      Path dir = FabricLoader.getInstance().getConfigDir().resolve("bazaaranalyzer");
      file = dir.resolve("capture.log");
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

         BazaarClient.LOG.info("Loaded {} past trades from the capture files", BazaarClient.BOOK.size());
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
