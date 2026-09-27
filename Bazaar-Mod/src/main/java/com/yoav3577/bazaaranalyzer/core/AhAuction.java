package com.yoav3577.bazaaranalyzer.core;

public record AhAuction(String auctionId, String uid, String tag, String name, boolean bin, double startingBid, double soldFor, long endTs, boolean active, long startTs) {
   public boolean sold() {
      return !this.active && this.soldFor > 0.0;
   }

   public static boolean tagMatches(String itemId, String tag) {
      return itemId != null && tag != null && (tag.equals(itemId) || tag.startsWith(itemId + "_"));
   }
}
