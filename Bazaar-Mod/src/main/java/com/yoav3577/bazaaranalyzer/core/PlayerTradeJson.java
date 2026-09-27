package com.yoav3577.bazaaranalyzer.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PlayerTradeJson {
   private PlayerTradeJson() {
   }

   public static Optional<PlayerTrade> parse(String line) {
      try {
         JsonObject o = JsonParser.parseString(line).getAsJsonObject();
         return Optional.of(
            new PlayerTrade(
               o.get("ts").getAsLong(), str(o, "partner"), num(o, "givenCoins"), num(o, "receivedCoins"), items(o.get("given")), items(o.get("received"))
            )
         );
      } catch (RuntimeException var2) {
         return Optional.empty();
      }
   }

   private static List<TradeItem> items(JsonElement el) {
      List<TradeItem> out = new ArrayList<>();
      if (el != null && el.isJsonArray()) {
         for (JsonElement e : el.getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            List<TradeItem.Gem> gems = new ArrayList<>();
            JsonElement g = o.get("gems");
            if (g != null && g.isJsonArray()) {
               for (JsonElement ge : (JsonArray)g) {
                  JsonObject go = ge.getAsJsonObject();
                  gems.add(new TradeItem.Gem(str(go, "type"), str(go, "quality")));
               }
            }

            List<String> parts = new ArrayList<>();
            JsonElement pa = o.get("parts");
            if (pa != null && pa.isJsonArray()) {
               for (JsonElement pe : pa.getAsJsonArray()) {
                  parts.add(pe.getAsString());
               }
            }

            out.add(new TradeItem(str(o, "id"), str(o, "uuid"), str(o, "name"), o.get("count").getAsInt(), gems, parts));
         }

         return out;
      } else {
         return out;
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
}
