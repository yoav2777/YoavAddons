package com.yoav3577.bazaaranalyzer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

/** Reads and writes the small JSON settings files in config/bazaaranalyzer/. */
final class ConfigFiles {
   private ConfigFiles() {
   }

   static Path path(String name) {
      return FabricLoader.getInstance().getConfigDir().resolve("bazaaranalyzer").resolve(name);
   }

   /** The file's text, or null when it does not exist or cannot be read (the caller then uses its defaults). */
   static String read(String name) {
      Path p = path(name);
      try {
         return Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
      } catch (IOException | RuntimeException e) {
         BazaarClient.LOG.warn("Could not read {}; using the defaults", name, e);
         return null;
      }
   }

   static void write(String name, String text) {
      Path p = path(name);
      try {
         Files.createDirectories(p.getParent());
         Path tmp = p.resolveSibling(name + ".tmp");
         Files.writeString(tmp, text, StandardCharsets.UTF_8);
         Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException | RuntimeException e) {
         BazaarClient.LOG.warn("Could not save {}", name, e);
      }
   }
}
