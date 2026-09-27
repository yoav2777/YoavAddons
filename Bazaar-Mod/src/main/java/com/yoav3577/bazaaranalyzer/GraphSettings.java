package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.GraphPrefs;

/** Holds the graph settings (config/bazaaranalyzer/graph.json, see GraphPrefs). */
final class GraphSettings {
   private static final String FILE = "graph.json";
   private static GraphPrefs current;

   private GraphSettings() {
   }

   static synchronized GraphPrefs get() {
      if (current == null) {
         current = GraphPrefs.fromJson(ConfigFiles.read(FILE));
      }

      return current;
   }

   /** Applies the settings now without writing the file (the settings screen saves when it closes). */
   static synchronized void live(GraphPrefs p) {
      current = p;
   }

   static synchronized void set(GraphPrefs p) {
      current = p;
      ConfigFiles.write(FILE, p.toJson());
   }

   static int width() {
      return get().width();
   }

   static int height() {
      return get().height();
   }

   static String range() {
      return get().range();
   }

   static synchronized void saveSize(int w, int h) {
      set(get().withSize(w, h));
   }

   /** A range button was pressed: it becomes the default only when "remember last range" is on. */
   static synchronized void rangePicked(String r) {
      if (get().rememberRange()) {
         set(get().withRange(r));
      }
   }

   static synchronized void saveRange(String r) {
      set(get().withRange(r));
   }
}
