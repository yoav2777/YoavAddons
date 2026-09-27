package com.yoav3577.bazaaranalyzer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class Http {
   private Http() {
   }

   static long parseUtc(String s) {
      if (s == null) {
         return 0L;
      } else {
         try {
            return s.endsWith("Z") ? Instant.parse(s).toEpochMilli() : LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli();
         } catch (RuntimeException var2) {
            return 0L;
         }
      }
   }

   static String get(String url, int timeoutMs) throws IOException {
      HttpURLConnection c = (HttpURLConnection)URI.create(url).toURL().openConnection();

      String var5;
      try {
         c.setConnectTimeout(timeoutMs);
         c.setReadTimeout(timeoutMs);
         c.setRequestProperty("User-Agent", "YoavAddons/" + BazaarClient.VERSION);
         c.setRequestProperty("Accept", "application/json");
         int code = c.getResponseCode();
         if (code != 200) {
            throw new IOException("HTTP " + code + " for " + url);
         }

         try (InputStream in = c.getInputStream()) {
            var5 = new String(in.readAllBytes(), StandardCharsets.UTF_8);
         }
      } finally {
         c.disconnect();
      }

      return var5;
   }
}
