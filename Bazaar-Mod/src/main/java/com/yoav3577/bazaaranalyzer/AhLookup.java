package com.yoav3577.bazaaranalyzer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yoav3577.bazaaranalyzer.core.AhAuction;
import com.yoav3577.bazaaranalyzer.core.PlayerTrade;
import com.yoav3577.bazaaranalyzer.core.Trade;
import com.yoav3577.bazaaranalyzer.core.TradeItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;

final class AhLookup {
   private static final String BASE = "https://sky.coflnet.com/api";
   private static final long REFRESH_MS = 60000L;
   private static final int MAX_PAGES = 20;
   private static final int MAX_DETAIL_CALLS = 30;
   private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "bazaaranalyzer-ah");
      t.setDaemon(true);
      return t;
   });
   private static final AtomicReference<List<AhAuction>> AUCTIONS = new AtomicReference<>(List.of());
   private static final Map<String, AhLookup.Basic> ENDED = new ConcurrentHashMap<>();
   private static final Map<String, String> UID_BY_AUCTION = new ConcurrentHashMap<>();
   private static volatile long lastRefresh;
   private static volatile boolean refreshing;
   private static volatile String lastError = "";

   private AhLookup() {
   }

   static List<AhAuction> get() {
      if (!refreshing && System.currentTimeMillis() - lastRefresh > REFRESH_MS) {
         refreshing = true;
         EXEC.execute(AhLookup::refresh);
      }

      return AUCTIONS.get();
   }

   static boolean refreshing() {
      return refreshing;
   }

   static String lastError() {
      return lastError;
   }

   private static void refresh() {
      try {
         String uuid = Minecraft.getInstance().getUser().getProfileId().toString().replace("-", "");
         Set<String> ids = new HashSet<>();
         long earliestTrade = Long.MAX_VALUE;

         for (PlayerTrade t : PlayerTradeStore.all()) {
            earliestTrade = Math.min(earliestTrade, t.ts());

            for (TradeItem i : t.received()) {
               if (i.uid() != null && i.id() != null) {
                  ids.add(i.id());
               }
            }
         }

         long earliest = earliestTrade;

         for (Trade e : BazaarClient.BOOK.snapshot()) {
            if (e.kind() == Trade.Kind.AH_LISTED) {
               earliest = Math.min(earliest, e.ts());
            }
         }

         if (earliest != Long.MAX_VALUE) {
            long horizon = earliest - 3600000L;
            long now = System.currentTimeMillis();
            List<AhLookup.Basic> running = new ArrayList<>();

            for (int page = 0; page < MAX_PAGES; page++) {
               JsonArray arr = JsonParser.parseString(Http.get(BASE + "/player/" + uuid + "/auctions?page=" + page, 10000))
                  .getAsJsonArray();
               if (arr.isEmpty()) {
                  break;
               }

               boolean anyNew = false;
               boolean reachedOlder = false;

               for (JsonElement el : arr) {
                  AhLookup.Basic b = parse(el.getAsJsonObject());
                  if (b != null) {
                     if (b.end() > now) {
                        running.add(b);
                        anyNew = true;
                     } else {
                        if (ENDED.putIfAbsent(b.id(), b) == null) {
                           anyNew = true;
                        }

                        if (b.end() < horizon) {
                           reachedOlder = true;
                        }
                     }
                  }
               }

               if (!anyNew || reachedOlder) {
                  break;
               }

               Thread.sleep(150L);
            }

            List<AhLookup.Basic> all = new ArrayList<>(running);

            for (AhLookup.Basic b : ENDED.values()) {
               if (b.end() >= horizon) {
                  all.add(b);
               }
            }

            all.sort(Comparator.comparingLong(AhLookup.Basic::end).reversed());
            List<AhAuction> out = new ArrayList<>(all.size());
            int detailCalls = 0;

            for (AhLookup.Basic bx : all) {
               boolean active = bx.end() > now;
               String uid = UID_BY_AUCTION.get(bx.id());
               boolean wanted = uid == null && bx.end() >= earliestTrade - 60000L && ids.stream().anyMatch(id -> AhAuction.tagMatches(id, bx.tag()));
               if (wanted && detailCalls < MAX_DETAIL_CALLS) {
                  detailCalls++;
                  uid = fetchUid(bx.id());
                  if (uid != null) {
                     UID_BY_AUCTION.put(bx.id(), uid); // "" = Coflnet answered, item has no uid: don't ask again
                  }

                  Thread.sleep(120L);
               }

               out.add(new AhAuction(bx.id(), uid == null || uid.isEmpty() ? null : uid, bx.tag(), bx.name(), bx.bin(), bx.starting(), active ? 0.0 : bx.highest(), bx.end(), active, bx.start()));
            }

            AUCTIONS.set(out);
            lastError = "";
            return;
         }

         AUCTIONS.set(List.of());
         lastError = "";
      } catch (Exception var22) {
         lastError = var22.getMessage() == null ? var22.getClass().getSimpleName() : var22.getMessage();
         BazaarClient.LOG.warn("Auction lookup failed: {}", lastError);
         return;
      } finally {
         lastRefresh = System.currentTimeMillis();
         refreshing = false;
      }
   }

   private static AhLookup.Basic parse(JsonObject o) {
      String id = str(o, "auctionId");
      String name = str(o, "itemName");
      return id != null && name != null
         ? new AhLookup.Basic(
            id, str(o, "tag"), name, o.has("bin") && o.get("bin").getAsBoolean(), num(o, "startingBid"), num(o, "highestBid"), Http.parseUtc(str(o, "end")), Http.parseUtc(str(o, "start"))
         )
         : null;
   }

   /** The item's uid, "" when Coflnet has none for it, null when the request failed (try again next refresh). */
   private static String fetchUid(String auctionId) {
      try {
         JsonObject o = JsonParser.parseString(Http.get(BASE + "/auction/" + auctionId, 10000)).getAsJsonObject();
         JsonObject flat = o.has("flatNbt") && o.get("flatNbt").isJsonObject() ? o.getAsJsonObject("flatNbt") : null;
         String uid = flat == null ? null : str(flat, "uid");
         return uid == null ? "" : uid.toLowerCase();
      } catch (Exception var4) {
         return null;
      }
   }

   private static String str(JsonObject o, String key) {
      JsonElement e = o.get(key);
      return e != null && !e.isJsonNull() ? e.getAsString() : null;
   }

   private static double num(JsonObject o, String key) {
      JsonElement e = o.get(key);
      return e != null && !e.isJsonNull() ? e.getAsDouble() : 0.0;
   }

   private record Basic(String id, String tag, String name, boolean bin, double starting, double highest, long end, long start) {
   }
}
