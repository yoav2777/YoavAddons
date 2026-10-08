package com.yoav3577.bazaaranalyzer;

import com.yoav3577.bazaaranalyzer.core.GemLog;
import com.yoav3577.bazaaranalyzer.core.GistClient;
import com.yoav3577.bazaaranalyzer.core.PlayerTrade;
import com.yoav3577.bazaaranalyzer.core.SyncData;
import com.yoav3577.bazaaranalyzer.core.SyncPrefs;
import com.yoav3577.bazaaranalyzer.core.Trade;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

/**
 * Cloud sync: keeps the lowball tracker's data (player trades, the gem log, the last 90 days of Bazaar/AH chat) in one
 * secret gist on the player's GitHub, so every PC with the same token shows the same tracker. Pulls when the game
 * starts and every 5 minutes (a free 304 when nothing changed), uploads within a minute of new data and when the game
 * closes. Both sides only ever add lines (SyncData), so two PCs never erase each other's data. Settings: sync.json.
 */
final class CloudSync {
   static final String DESCRIPTION = "Yoav Addons sync: lowball tracker data (do not edit)";
   private static final String FILE = "sync.json";
   private static final String API = "https://api.github.com";
   private static final String README = "yoavaddons-sync.md";
   private static final String README_TEXT = "Yoav Addons keeps your lowball tracker here so all your PCs share it.\n"
      + "Every PC with the same token reads and adds to these files. Deleting this gist starts the cloud copy over.\n";
   /** Bazaar/AH chat older than this is not synced (the tracker looks at most a week past a trade). */
   static final long CAPTURE_MS = 90L * 86400000L;
   private static final long PULL_MS = 300000L;
   /** Upload at most about this much per request. */
   private static final int PATCH_CHARS = 4 << 20;
   private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
   private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "bazaaranalyzer-sync");
      t.setDaemon(true);
      return t;
   });
   private static volatile SyncPrefs prefs = SyncPrefs.DEFAULT;
   private static volatile String status = "Sync now";
   private static ScheduledFuture<?> pending;
   // sync thread only: the gist as last seen (file -> content), its ETag, when it was read, the data sizes last uploaded
   private static final Map<String, String> remote = new HashMap<>();
   private static String etag;
   private static long lastPull;
   private static String lastKey;
   private static boolean warned;

   private CloudSync() {
   }

   static void init() {
      prefs = SyncPrefs.fromJson(ConfigFiles.read(FILE));
      status = idle();
      // right away (the trade stores are loaded; capture lines queue behind its history load), so another PC's gem
      // removals are in before the gem watch can see the same items in a world and log them again
      EXEC.scheduleWithFixedDelay(() -> run(false), 1L, 60L, TimeUnit.SECONDS);
      ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
         if (prefs.on()) {
            try {
               EXEC.submit(() -> run(true)).get(10L, TimeUnit.SECONDS);
            } catch (Exception e) {
               BazaarClient.LOG.warn("Cloud sync: could not upload before closing ({})", e.toString());
            }
         }
      });
   }

   static boolean enabled() {
      return prefs.enabled();
   }

   static void setEnabled(boolean v) {
      save(prefs.withEnabled(v));
      if (v) {
         syncSoon();
      } else {
         status = idle();
      }
   }

   /** A pasted token (the settings box); a different one starts over with that account's gist. */
   static void setToken(String token) {
      String t = token.strip();
      if (!t.equals(prefs.token())) {
         save(prefs.withToken(t));
         EXEC.execute(() -> {
            remote.clear();
            etag = null;
            lastKey = null;
            warned = false;
         });
         syncSoon();
      }
   }

   static String tokenHint() {
      String t = prefs.token();
      return t == null ? "Paste token" : "Saved (..." + t.substring(Math.max(0, t.length() - 4)) + ")";
   }

   /** What the settings button shows: Sync now / Syncing... / Synced 14:03 / Bad token ... */
   static String status() {
      return status;
   }

   /** Syncs now; {@code done} (any thread, may be null) gets the status after. */
   static void syncNow(Consumer<String> done) {
      if (!prefs.on()) {
         if (done != null) {
            done.accept(prefs.token() == null ? "no token yet: /ya > Cloud sync" : "sync is off: /ya > Cloud sync");
         }

         return;
      }

      status = "Syncing...";
      EXEC.execute(() -> {
         run(true);
         if (done != null) {
            done.accept(status);
         }
      });
   }

   /** Typing a token fires on every key: sync once it has been still for 2 s. */
   private static synchronized void syncSoon() {
      if (pending != null) {
         pending.cancel(false);
      }

      status = prefs.on() ? "Syncing..." : idle();
      pending = EXEC.schedule(() -> run(true), 2L, TimeUnit.SECONDS);
   }

   private static void save(SyncPrefs p) {
      prefs = p;
      ConfigFiles.write(FILE, p.toJson());
   }

   private static String idle() {
      return prefs.token() == null ? "No token" : prefs.enabled() ? "Sync now" : "Off";
   }

   private static void run(boolean force) {
      SyncPrefs p = prefs;
      if (!p.on()) {
         status = idle();
         return;
      }

      GistClient gh = new GistClient(API, p.token(), "YoavAddons/" + BazaarClient.VERSION);
      try {
         String id = gist(gh, p);
         long now = System.currentTimeMillis();
         if (force || now - lastPull >= PULL_MS) {
            id = pull(gh, p, id);
         }

         push(gh, p, id, force);
         status = "Synced " + LocalTime.now().format(HH_MM);
         warned = false;
      } catch (GistClient.Failure e) {
         fail(e);
      } catch (IOException e) {
         status = "Offline";
         BazaarClient.LOG.warn("Cloud sync: {}", e.toString());
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      } catch (RuntimeException e) {
         status = "Error (log)";
         BazaarClient.LOG.warn("Cloud sync failed", e);
      }
   }

   private static void fail(GistClient.Failure e) {
      String tell = null;
      if (e.status == 401) {
         status = "Bad token";
         tell = "GitHub refused the token. Paste a new one in /ya > Cloud sync.";
      } else if (e.status == 403 || e.status == 404) {
         status = "No gist access";
         tell = "the token can't use gists. Make a classic token with the \"gist\" scope (/ya > Cloud sync).";
      } else {
         status = "GitHub error";
      }

      BazaarClient.LOG.warn("Cloud sync: {}", e.getMessage());
      if (tell != null && !warned) {
         warned = true;
         AhTabs.say("Cloud sync: " + tell, ChatFormatting.RED);
      }
   }

   /** This account's sync gist (found, or made the first time). */
   private static String gist(GistClient gh, SyncPrefs p) throws IOException, InterruptedException {
      if (p.gistId() != null) {
         return p.gistId();
      }

      String id = gh.findOrCreate(DESCRIPTION, README, README_TEXT);
      BazaarClient.LOG.info("Cloud sync: using gist {}", id);
      remote.clear();
      etag = null;
      if (p.token().equals(prefs.token())) {
         save(prefs.withGist(id));
      }

      return id;
   }

   /** Reads the gist (when it changed) and adds what other PCs uploaded; returns the gist id (a new one if it was deleted). */
   private static String pull(GistClient gh, SyncPrefs p, String id) throws IOException, InterruptedException {
      GistClient.Snapshot s;
      try {
         s = gh.get(id, etag);
      } catch (GistClient.Failure e) {
         if (e.status != 404 || p.gistId() == null) {
            throw e;
         }

         // deleted on github.com: start a new one
         BazaarClient.LOG.info("Cloud sync: gist {} is gone, making a new one", id);
         p = p.withGist(null);
         if (p.token().equals(prefs.token())) {
            save(prefs.withGist(null));
         }

         id = gist(gh, p);
         s = gh.get(id, null);
      }

      lastPull = System.currentTimeMillis();
      if (s.files() == null) {
         return id;
      }

      remote.clear();
      s.files().forEach((name, text) -> {
         if (SyncData.kindOf(name) != null) {
            remote.put(name, text);
         }
      });
      etag = s.etag();
      int trades = PlayerTradeStore.addSynced(SyncData.linesOf(SyncData.PLAYER_TRADES, remote));
      int gems = GemWatch.addSynced(SyncData.linesOf(SyncData.GEM_LOG, remote));
      Capture.addSynced(SyncData.linesOf(SyncData.CAPTURE, remote), lastPull - CAPTURE_MS);
      if (trades + gems > 0) {
         Minecraft.getInstance().execute(GemWatch::rebuild);
         BazaarClient.LOG.info("Cloud sync: {} player trades, {} gem log entries from other PCs", trades, gems);
      }

      if (trades > 0) {
         AhTabs.say("Cloud sync: " + trades + (trades == 1 ? " trade" : " trades") + " from your other PCs added to the lowball tracker.", ChatFormatting.GRAY);
      }

      return id;
   }

   /** Uploads the months that have lines the gist lacks (merged with the gist's copy). */
   private static void push(GistClient gh, SyncPrefs p, String id, boolean force) throws IOException, InterruptedException {
      long from = System.currentTimeMillis() - CAPTURE_MS;
      List<PlayerTrade> trades = PlayerTradeStore.all();
      List<GemLog> gems = GemWatch.all();
      List<Trade> book = BazaarClient.BOOK.snapshot().stream().filter(t -> t.ts() >= from).toList();
      String key = trades.size() + "|" + gems.size() + "|" + book.size() + "|" + (book.isEmpty() ? 0 : book.get(book.size() - 1).ts()) + "|" + etag;
      if (!force && key.equals(lastKey)) {
         return;
      }

      Map<String, List<String>> local = new TreeMap<>();
      local.putAll(SyncData.byFile(SyncData.PLAYER_TRADES, trades.stream().map(PlayerTrade::toJson).toList()));
      local.putAll(SyncData.byFile(SyncData.GEM_LOG, gems.stream().map(GemLog::toJson).toList()));
      local.putAll(SyncData.byFile(SyncData.CAPTURE, book.stream().map(Capture::syncLine).toList()));
      Map<String, String> up = SyncData.changed(local, remote);
      if (!up.isEmpty() && System.currentTimeMillis() - lastPull > 30000L) {
         // another PC may have uploaded since the last read: merge with that
         id = pull(gh, p, id);
         up = SyncData.changed(local, remote);
      }

      List<Map<String, String>> parts = new ArrayList<>();
      int chars = 0;
      for (Map.Entry<String, String> e : up.entrySet()) {
         if (parts.isEmpty() || chars > 0 && chars + e.getValue().length() > PATCH_CHARS) {
            parts.add(new LinkedHashMap<>());
            chars = 0;
         }

         parts.get(parts.size() - 1).put(e.getKey(), e.getValue());
         chars += e.getValue().length();
      }

      for (Map<String, String> part : parts) {
         etag = gh.patch(id, part);
         remote.putAll(part);
      }

      if (!up.isEmpty()) {
         BazaarClient.LOG.info("Cloud sync: uploaded {}", up.keySet());
      }

      lastKey = trades.size() + "|" + gems.size() + "|" + book.size() + "|" + (book.isEmpty() ? 0 : book.get(book.size() - 1).ts()) + "|" + etag;
   }
}
