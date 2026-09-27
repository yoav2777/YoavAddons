package com.yoav3577.bazaaranalyzer.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Which items bought in a lowball still carry parts (gems, drill parts, ... see Parts) that came with them, and what to
 * log when one loses a part.
 * Built from the player trades + gem log; fed with what the inventory shows now. Client thread only.
 */
public final class GemTracker {
   /** A bought item whose trade window showed no gems gets its gems from the first inventory look within this time. */
   static final long BASE_GRACE_MS = 600000L;
   private final Map<String, GemTracker.State> items = new HashMap<>();

   private GemTracker() {
   }

   public static GemTracker build(List<PlayerTrade> trades, List<GemLog> log, long now) {
      GemTracker t = new GemTracker();
      List<PlayerTrade> ordered = new ArrayList<>(trades);
      ordered.sort(Comparator.comparingLong(PlayerTrade::ts));

      for (PlayerTrade p : ordered) {
         for (TradeItem i : p.given()) {
            if (i.uuid() != null) {
               t.items.remove(key(i.uuid()));
            }
         }

         if (p.givenCoins() > 0.0) {
            for (TradeItem i : p.received()) {
               if (i.uuid() != null) {
                  List<String> ids = Parts.of(i.gems(), i.parts());
                  t.items.put(key(i.uuid()), new GemTracker.State(p.ts(), ids.isEmpty() ? null : new ArrayList<>(ids)));
               }
            }
         }
      }

      List<GemLog> entries = new ArrayList<>(log);
      entries.sort(Comparator.comparingLong(GemLog::ts));

      for (GemLog e : entries) {
         GemTracker.State s = e.uuid() == null ? null : t.items.get(key(e.uuid()));
         if (s != null && e.ts() >= s.tradeTs) {
            if (e.kind() == GemLog.Kind.BASE && s.left == null) {
               s.left = new ArrayList<>(e.ids());
            } else if (e.kind() == GemLog.Kind.REMOVED && s.left != null) {
               s.left = new ArrayList<>(Gems.minus(s.left, e.ids()));
            }
         }
      }

      t.items.values().removeIf(s -> s.left == null ? now - s.tradeTs > BASE_GRACE_MS : s.left.isEmpty());
      return t;
   }

   public boolean isEmpty() {
      return this.items.isEmpty();
   }

   public boolean tracks(String uuid) {
      return uuid != null && this.items.containsKey(key(uuid));
   }

   /**
    * The item (uuid) is in the inventory with these parts (Parts.of ids) now: the log entry to write, or null when nothing
    * changed. nameOf gives each part's display name (from the item in the inventory when it is there).
    */
   public GemLog observe(String uuid, String name, List<String> now, long ts, Function<String, String> nameOf) {
      GemTracker.State s = uuid == null ? null : this.items.get(key(uuid));
      if (s == null) {
         return null;
      } else if (s.left == null) {
         if (now.isEmpty()) {
            this.items.remove(key(uuid));
            return null;
         } else {
            s.left = new ArrayList<>(now);
            return new GemLog(ts, GemLog.Kind.BASE, uuid, name, named(now, nameOf));
         }
      } else {
         List<String> gone = Gems.removed(s.left, now);
         if (gone.isEmpty()) {
            return null;
         } else {
            s.left = new ArrayList<>(Gems.minus(s.left, gone));
            if (s.left.isEmpty()) {
               this.items.remove(key(uuid));
            }

            return new GemLog(ts, GemLog.Kind.REMOVED, uuid, name, named(gone, nameOf));
         }
      }
   }

   private static List<Parts.Part> named(List<String> ids, Function<String, String> nameOf) {
      return ids.stream().map(id -> new Parts.Part(id, nameOf.apply(id))).toList();
   }

   private static String key(String uuid) {
      return uuid.toLowerCase(Locale.ROOT);
   }

   private static final class State {
      final long tradeTs;
      List<String> left;

      State(long tradeTs, List<String> left) {
         this.tradeTs = tradeTs;
         this.left = left;
      }
   }
}
