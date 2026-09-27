package com.yoav3577.bazaaranalyzer.core;

import java.net.URI;

public final class RequestGuard {
   private static final int SITE_FIRST_PORT = 47831;
   private static final int SITE_LAST_PORT = 47850;
   /** The mod's own server, which also serves the built-in copy of the site. */
   private static final int MOD_FIRST_PORT = 47860;
   private static final int MOD_LAST_PORT = 47869;
   /** The online copy of the site (GitHub Pages); it reads the Activity data from the mod like the local one. */
   public static final String ONLINE_SITE = "https://yoav2777.github.io";

   private RequestGuard() {
   }

   public static boolean originAllowed(String origin) {
      if (origin == null) {
         return true;
      } else {
         try {
            if (ONLINE_SITE.equals(origin)) {
               return true;
            }

            URI u = URI.create(origin);
            String host = u.getHost();
            int p = u.getPort();
            return "http".equals(u.getScheme()) && ("127.0.0.1".equals(host) || "localhost".equals(host))
               && (p >= SITE_FIRST_PORT && p <= SITE_LAST_PORT || p >= MOD_FIRST_PORT && p <= MOD_LAST_PORT);
         } catch (RuntimeException var3) {
            return false;
         }
      }
   }

   public static boolean hostAllowed(String hostHeader, int serverPort) {
      return ("127.0.0.1:" + serverPort).equals(hostHeader) || ("localhost:" + serverPort).equals(hostHeader);
   }
}
