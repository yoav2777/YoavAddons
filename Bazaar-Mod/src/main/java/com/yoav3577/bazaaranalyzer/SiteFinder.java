package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.ModPrefs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SiteFinder {
   static final int FILTER_VERSION = 11;

   private static final Pattern BUNDLE = Pattern.compile("index-[A-Za-z0-9]+-v(\\d+)\\.js");

   private SiteFinder() {
   }

   /** Finds the website: only the port set in the settings, or (Auto) the newest site on 47831-47850. Null when none answers. */
   static SiteFinder.Site find() {
      SiteFinder.Site best = null;
      int fixed = ModSettings.get().sitePort();
      int first = fixed != 0 ? fixed : ModPrefs.FIRST_SITE_PORT;
      int last = fixed != 0 ? fixed : ModPrefs.LAST_SITE_PORT;

      for (int port = first; port <= last; port++) {
         try {
            String html = Http.get("http://127.0.0.1:" + port + "/", 600);
            if (html.contains("<title>Bazaar Analyzer</title>")) {
               Matcher m = BUNDLE.matcher(html);
               int version = m.find() ? Integer.parseInt(m.group(1)) : 0;
               if (best == null || version > best.version()) {
                  best = new SiteFinder.Site("http://127.0.0.1:" + port, version);
               }
            }
         } catch (RuntimeException | IOException var5) {
         }
      }

      return best;
   }

   /** Starts the website in its own window; serve.ps1 saves its folder on every run. False when it never ran here (or not Windows). */
   static boolean launch() {
      String local = System.getenv("LOCALAPPDATA");
      if (local == null) {
         return false;
      }

      try {
         Path ps1 = Path.of(Files.readString(Path.of(local, "BazaarAnalyzer", "site-folder.txt")).strip(), "serve.ps1");
         if (!Files.exists(ps1)) {
            return false;
         }

         List<String> cmd = new ArrayList<>(
            List.of("cmd", "/c", "start", "Bazaar Analyzer", "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps1.toString(), "-NoBrowser")
         );
         int port = ModSettings.get().sitePort();
         if (port != 0) {
            cmd.addAll(List.of("-Port", String.valueOf(port)));
         }

         new ProcessBuilder(cmd).start();
         return true;
      } catch (IOException | RuntimeException e) {
         BazaarClient.LOG.warn("Could not start the website", e);
         return false;
      }
   }

   record Site(String base, int version) {
   }
}
