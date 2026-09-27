package com.yoav3577.bazaaranalyzer.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One line of gem-log.jsonl. START = when watching began (trades before it use the old "gems sold within a day of the
 * trade" guess), BASE = the parts first seen on a bought item whose trade window showed none, REMOVED = parts (gems,
 * drill parts, pet item... see Parts) that disappeared from a bought item (uuid) while it was in the inventory.
 */
public record GemLog(long ts, GemLog.Kind kind, String uuid, String name, List<Parts.Part> parts) {
   public GemLog {
      parts = parts == null ? List.of() : List.copyOf(parts);
   }

   public List<String> ids() {
      return this.parts.stream().map(Parts.Part::id).toList();
   }

   public static GemLog start(long ts) {
      return new GemLog(ts, GemLog.Kind.START, null, null, null);
   }

   /** First START time in the log, or Long.MAX_VALUE when there is none (every trade is then "before watching"). */
   public static long watchSince(List<GemLog> log) {
      long since = Long.MAX_VALUE;

      for (GemLog e : log) {
         if (e.kind == GemLog.Kind.START) {
            since = Math.min(since, e.ts);
         }
      }

      return since;
   }

   public String toJson() {
      StringBuilder sb = new StringBuilder("{\"ts\":")
         .append(this.ts)
         .append(",\"kind\":\"")
         .append(this.kind)
         .append("\",\"uuid\":")
         .append(Json.str(this.uuid))
         .append(",\"name\":")
         .append(Json.str(this.name))
         .append(",\"parts\":[");

      for (int i = 0; i < this.parts.size(); i++) {
         if (i > 0) {
            sb.append(',');
         }

         sb.append("{\"id\":").append(Json.str(this.parts.get(i).id())).append(",\"name\":").append(Json.str(this.parts.get(i).name())).append('}');
      }

      return sb.append("]}").toString();
   }

   public static Optional<GemLog> parse(String line) {
      try {
         JsonObject o = JsonParser.parseString(line).getAsJsonObject();
         List<Parts.Part> parts = new ArrayList<>();
         JsonElement p = o.get("parts");
         if (p != null && p.isJsonArray()) {
            for (JsonElement pe : p.getAsJsonArray()) {
               JsonObject po = pe.getAsJsonObject();
               parts.add(new Parts.Part(str(po, "id"), str(po, "name")));
            }
         }

         // gems-only lines written by the first gem-watch build
         JsonElement g = o.get("gems");
         if (g != null && g.isJsonArray()) {
            for (JsonElement ge : g.getAsJsonArray()) {
               JsonObject go = ge.getAsJsonObject();
               TradeItem.Gem gem = new TradeItem.Gem(str(go, "type"), str(go, "quality"));
               parts.add(new Parts.Part(Parts.gemId(gem), gem.bazaarName()));
            }
         }

         return Optional.of(new GemLog(o.get("ts").getAsLong(), GemLog.Kind.valueOf(str(o, "kind")), str(o, "uuid"), str(o, "name"), parts));
      } catch (RuntimeException var6) {
         return Optional.empty();
      }
   }

   private static String str(JsonObject o, String key) {
      JsonElement e = o.get(key);
      return e != null && !e.isJsonNull() ? e.getAsString() : null;
   }

   public static enum Kind {
      START,
      BASE,
      REMOVED;
   }
}
