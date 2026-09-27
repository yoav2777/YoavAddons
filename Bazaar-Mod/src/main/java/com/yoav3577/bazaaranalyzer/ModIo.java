package com.yoav3577.bazaaranalyzer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared single-thread executor for disk writes that would otherwise block the render/tick thread. */
final class ModIo {
   private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "bazaaranalyzer-io");
      t.setDaemon(true);
      return t;
   });

   private ModIo() {
   }

   static void submit(Runnable task) {
      IO.execute(() -> {
         try {
            task.run();
         } catch (RuntimeException var2) {
            BazaarClient.LOG.warn("Background IO task failed", var2);
         }
      });
   }
}
