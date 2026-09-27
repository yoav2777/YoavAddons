package com.yoav3577.bazaaranalyzer.core;

public record Trade(long ts, Trade.Kind kind, String item, int qty, double coins, double unit, String other, String raw) {
   public String toJson() {
      return this.toJson(null);
   }

   public String toJson(String extraFields) {
      return "{\"ts\":"
         + this.ts
         + ",\"day\":\""
         + Days.key(this.ts)
         + "\",\"kind\":\""
         + this.kind
         + "\",\"item\":"
         + Json.str(this.item)
         + ",\"qty\":"
         + this.qty
         + ",\"coins\":"
         + Json.num(this.coins)
         + ",\"unit\":"
         + Json.num(this.unit)
         + ",\"other\":"
         + Json.str(this.other)
         + ",\"raw\":"
         + Json.str(this.raw)
         + (extraFields == null ? "" : "," + extraFields)
         + "}";
   }

   public static enum Kind {
      BZ_BUY_ORDER_SETUP,
      BZ_SELL_OFFER_SETUP,
      BZ_CLAIM_BOUGHT,
      BZ_CLAIM_SOLD,
      BZ_INSTA_BUY,
      BZ_INSTA_SELL,
      AH_LISTED,
      AH_PURCHASED,
      AH_COLLECTED,
      AH_CANCELED,
      AH_RETURNED,
      OTHER;
   }
}
