package com.yoav3577.bazaaranalyzer.core;

import java.net.URI;

public final class RequestGuard {
   private static final int SITE_FIRST_PORT = 47831;
   private static final int SITE_LAST_PORT = 47850;

   private RequestGuard() {
   }

   public static boolean originAllowed(String origin) {
      if (origin == null) {
         return true;
      } else {
         try {
            URI u = URI.create(origin);
            String host = u.getHost();
            return "http".equals(u.getScheme()) && ("127.0.0.1".equals(host) || "localhost".equals(host)) && u.getPort() >= SITE_FIRST_PORT && u.getPort() <= SITE_LAST_PORT;
         } catch (RuntimeException var3) {
            return false;
         }
      }
   }

   public static boolean hostAllowed(String hostHeader, int serverPort) {
      return ("127.0.0.1:" + serverPort).equals(hostHeader) || ("localhost:" + serverPort).equals(hostHeader);
   }
}
