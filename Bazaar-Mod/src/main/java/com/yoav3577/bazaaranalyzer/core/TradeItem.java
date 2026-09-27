package com.yoav3577.bazaaranalyzer.core;

import java.util.List;
import java.util.Locale;

/** parts = market ids of the other removable things on the item (drill parts, pet item/skin, skin, dye, rod parts). */
public record TradeItem(String id, String uuid, String name, int count, List<TradeItem.Gem> gems, List<String> parts) {
   public TradeItem {
      gems = gems == null ? List.of() : List.copyOf(gems);
      parts = parts == null ? List.of() : List.copyOf(parts);
   }

   public TradeItem(String id, String uuid, String name, int count, List<TradeItem.Gem> gems) {
      this(id, uuid, name, count, gems, null);
   }

   public String uid() {
      if (this.uuid == null) {
         return null;
      } else {
         String flat = this.uuid.replace("-", "");
         return flat.length() >= 12 ? flat.substring(flat.length() - 12).toLowerCase(Locale.ROOT) : null;
      }
   }

   public String toJson() {
      StringBuilder gemJson = new StringBuilder("[");

      for (int i = 0; i < this.gems.size(); i++) {
         if (i > 0) {
            gemJson.append(',');
         }

         gemJson.append("{\"type\":")
            .append(Json.str(this.gems.get(i).type()))
            .append(",\"quality\":")
            .append(Json.str(this.gems.get(i).quality()))
            .append(",\"name\":")
            .append(Json.str(this.gems.get(i).bazaarName()))
            .append('}');
      }

      gemJson.append(']');
      return "{\"id\":"
         + Json.str(this.id)
         + ",\"uuid\":"
         + Json.str(this.uuid)
         + ",\"name\":"
         + Json.str(this.name)
         + ",\"count\":"
         + this.count
         + ",\"gems\":"
         + gemJson
         + ",\"parts\":["
         + String.join(",", this.parts.stream().map(Json::str).toList())
         + "]}";
   }

   public record Gem(String type, String quality) {
      public String bazaarName() {
         return title(this.quality) + " " + title(this.type) + " Gemstone";
      }

      private static String title(String s) {
         if (s != null && !s.isEmpty()) {
            String lower = s.toLowerCase(Locale.ROOT);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
         } else {
            return "";
         }
      }
   }
}
