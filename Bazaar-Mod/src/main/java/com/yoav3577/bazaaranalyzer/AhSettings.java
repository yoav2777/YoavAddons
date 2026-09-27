package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.LinkSettings;

/** Holds the website-link filter settings (config/bazaaranalyzer/link-settings.json, see LinkSettings). */
final class AhSettings {
   private static final String FILE = "link-settings.json";
   private static LinkSettings current;

   private AhSettings() {
   }

   static synchronized LinkSettings get() {
      if (current == null) {
         current = LinkSettings.fromJson(ConfigFiles.read(FILE));
      }

      return current;
   }

   /** Applies the settings now without writing the file (the settings screen saves when it closes). */
   static synchronized void live(LinkSettings s) {
      current = s;
   }

   static synchronized void save(LinkSettings s) {
      current = s;
      ConfigFiles.write(FILE, s.toJson());
   }
}
