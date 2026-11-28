package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.MiningPrefs;

/** Holds the mining tracker settings (config/bazaaranalyzer/mining.json, see MiningPrefs). */
final class MiningSettings {
   private static final String FILE = "mining.json";
   private static volatile MiningPrefs current;

   private MiningSettings() {
   }

   static synchronized MiningPrefs get() {
      if (current == null) {
         current = MiningPrefs.fromJson(ConfigFiles.read(FILE));
      }

      return current;
   }

   /** Applies the settings now without writing the file (the settings screen saves when it closes). */
   static synchronized void live(MiningPrefs p) {
      current = p;
   }

   static synchronized void set(MiningPrefs p) {
      current = p;
      ConfigFiles.write(FILE, p.toJson());
   }
}
