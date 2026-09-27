package com.yoav3577.bazaaranalyzer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.yoav3577.bazaaranalyzer.core.AhAuction;
import com.yoav3577.bazaaranalyzer.core.AhLedger;
import com.yoav3577.bazaaranalyzer.core.GemLog;
import com.yoav3577.bazaaranalyzer.core.Lowball;
import com.yoav3577.bazaaranalyzer.core.PlayerTrade;
import com.yoav3577.bazaaranalyzer.core.RequestGuard;
import com.yoav3577.bazaaranalyzer.core.Trade;
import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

public final class LocalServer {
   public static final int FIRST_PORT = 47860;
   public static final int LAST_PORT = 47869;
   private static HttpServer server;
   private static int port = -1;
   private static volatile Cached lowballCache;

   private LocalServer() {
   }

   public static synchronized void start() throws IOException {
      if (server == null) {
         BindException last = null;

         for (int p = FIRST_PORT; p <= LAST_PORT; p++) {
            try {
               server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), p), 0);
               port = p;
               break;
            } catch (BindException var3) {
               last = var3;
            }
         }

         if (server == null) {
            throw last != null ? last : new IOException("no free port in 47860-47869");
         } else {
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
               Thread t = new Thread(r, "bazaaranalyzer-http");
               t.setDaemon(true);
               return t;
            }));
            server.createContext(
               "/health", ex -> handle(ex, () -> "{\"ok\":true,\"mod\":\"bazaaranalyzer\",\"version\":\"" + BazaarClient.VERSION + "\",\"port\":" + port + "}")
            );
            server.createContext("/trades", ex -> handle(ex, () -> {
               List<AhLedger.Listing> ledger = AhLedger.build(BazaarClient.BOOK.snapshot(), AhLookup.get());
               return BazaarClient.BOOK.toJson(limit(ex.getRequestURI().getRawQuery()), ledger);
            }));
            server.createContext("/lowballs", ex -> handle(ex, LocalServer::lowballs));
            server.start();
            BazaarClient.LOG.info("Local server listening on 127.0.0.1:{}", port);
         }
      }
   }

   private static String lowballs() {
      List<PlayerTrade> trades = PlayerTradeStore.all();
      List<AhAuction> ah = AhLookup.get();
      List<Trade> book = BazaarClient.BOOK.snapshot();
      List<GemLog> gems = GemWatch.all();
      // The site asks every 5 s: redo the analysis only when an input changed (or a minute passed, for the 1-day windows).
      String key = trades.size() + "|" + book.size() + "|" + (book.isEmpty() ? 0 : book.get(book.size() - 1).ts()) + "|"
         + System.identityHashCode(ah) + "|" + gems.size() + "|" + AhLookup.refreshing() + "|" + AhLookup.lastError();
      Cached c = lowballCache;
      if (c != null && c.key().equals(key) && System.currentTimeMillis() - c.at() < 60000L) {
         return c.json();
      }

      String json = analyzeLowballs(trades, ah, book, gems);
      lowballCache = new Cached(key, System.currentTimeMillis(), json);
      return json;
   }

   private static String analyzeLowballs(List<PlayerTrade> trades, List<AhAuction> ah, List<Trade> book, List<GemLog> gems) {
      List<Lowball.Result> results = Lowball.analyze(trades, ah, book, gems, System.currentTimeMillis());
      List<Lowball.Day> days = Lowball.byDay(trades, results);
      User user = Minecraft.getInstance().getUser();
      return Lowball.responseJson(user.getName(), user.getProfileId().toString(), AhLookup.refreshing(), ah.size(), AhLookup.lastError(), days);
   }

   private static int limit(String query) {
      int limit = 200;
      if (query != null) {
         for (String part : query.split("&")) {
            if (part.startsWith("limit=")) {
               try {
                  limit = Integer.parseInt(part.substring(6));
               } catch (NumberFormatException var7) {
               }
            }
         }
      }

      return Math.max(1, Math.min(1000, limit));
   }

   private static void handle(HttpExchange ex, LocalServer.Body body) throws IOException {
      try {
         if (!RequestGuard.hostAllowed(ex.getRequestHeaders().getFirst("Host"), port)) {
            ex.sendResponseHeaders(403, -1L);
         } else {
            String origin = ex.getRequestHeaders().getFirst("Origin");
            if (!RequestGuard.originAllowed(origin)) {
               ex.sendResponseHeaders(403, -1L);
            } else if (!"GET".equals(ex.getRequestMethod())) {
               ex.sendResponseHeaders(405, -1L);
            } else {
               byte[] bytes;
               try {
                  bytes = body.get().getBytes(StandardCharsets.UTF_8);
               } catch (Exception var14) {
                  BazaarClient.LOG.warn("Local server request failed", var14);
                  ex.sendResponseHeaders(500, -1L);
                  return;
               }

               if (origin != null) {
                  ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
                  ex.getResponseHeaders().set("Vary", "Origin");
               }

               ex.getResponseHeaders().set("Cache-Control", "no-store");
               ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
               ex.sendResponseHeaders(200, bytes.length);

               try (OutputStream os = ex.getResponseBody()) {
                  os.write(bytes);
               }
            }
         }
      } finally {
         ex.close();
      }
   }

   private record Cached(String key, long at, String json) {
   }

   private interface Body {
      String get() throws Exception;
   }
}
