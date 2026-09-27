package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.ModPrefs;

/** Holds the trade-button and website settings (config/bazaaranalyzer/mod-settings.json, see ModPrefs). */
final class ModSettings {
   private static final String FILE = "mod-settings.json";
   private static volatile ModPrefs current;

   private ModSettings() {
   }

   static synchronized ModPrefs get() {
      if (current == null) {
         current = ModPrefs.fromJson(ConfigFiles.read(FILE));
      }

      return current;
   }

   /** Applies the settings now without writing the file (the settings screen saves when it closes). */
   static synchronized void live(ModPrefs p) {
      current = p;
   }

   static synchronized void set(ModPrefs p) {
      current = p;
      ConfigFiles.write(FILE, p.toJson());
   }
}
