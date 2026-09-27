package com.yoav3577.bazaaranalyzer.core;

import java.util.List;

public record PlayerTrade(long ts, String partner, double givenCoins, double receivedCoins, List<TradeItem> given, List<TradeItem> received) {
   public PlayerTrade {
      given = given == null ? List.of() : List.copyOf(given);
      received = received == null ? List.of() : List.copyOf(received);
   }

   public String day() {
      return Days.key(this.ts);
   }

   public String toJson() {
      return "{\"ts\":"
         + this.ts
         + ",\"day\":\""
         + this.day()
         + "\",\"partner\":"
         + Json.str(this.partner)
         + ",\"givenCoins\":"
         + Json.num(this.givenCoins)
         + ",\"receivedCoins\":"
         + Json.num(this.receivedCoins)
         + ",\"given\":"
         + items(this.given)
         + ",\"received\":"
         + items(this.received)
         + "}";
   }

   static String items(List<TradeItem> list) {
      StringBuilder sb = new StringBuilder("[");

      for (int i = 0; i < list.size(); i++) {
         if (i > 0) {
            sb.append(',');
         }

         sb.append(list.get(i).toJson());
      }

      return sb.append(']').toString();
   }
}
