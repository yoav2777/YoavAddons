package com.yoav3577.bazaaranalyzer.core;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TradeParser {
   private static final String NUM = "([\\d,]+(?:\\.\\d+)?)";
   private static final String QTY = "([\\d,]+)";
   private static final Pattern PROGRESS = Pattern.compile(
      "^\\[Bazaar] (?:Putting goods in escrow|Submitting (?:buy order|sell offer)|Claiming order|Executing instant (?:buy|sell))\\.\\.\\.$"
   );
   private static final Pattern BUY_SETUP = Pattern.compile("^\\[Bazaar] Buy Order Setup! ([\\d,]+)x (.+?) for ([\\d,]+(?:\\.\\d+)?) coins\\.$");
   private static final Pattern SELL_SETUP = Pattern.compile("^\\[Bazaar] Sell Offer Setup! ([\\d,]+)x (.+?) for ([\\d,]+(?:\\.\\d+)?) coins\\.$");
   private static final Pattern CLAIM_BOUGHT = Pattern.compile(
      "^\\[Bazaar] Claimed ([\\d,]+)x (.+?) worth ([\\d,]+(?:\\.\\d+)?) coins bought for ([\\d,]+(?:\\.\\d+)?) each!$"
   );
   private static final Pattern CLAIM_SOLD = Pattern.compile(
      "^\\[Bazaar] Claimed ([\\d,]+(?:\\.\\d+)?) coins from selling ([\\d,]+)x (.+?) at ([\\d,]+(?:\\.\\d+)?) each!$"
   );
   private static final Pattern INSTA_BUY = Pattern.compile("^\\[Bazaar] Bought ([\\d,]+)x (.+?) for ([\\d,]+(?:\\.\\d+)?) coins!$");
   private static final Pattern INSTA_SELL = Pattern.compile("^\\[Bazaar] Sold ([\\d,]+)x (.+?) for ([\\d,]+(?:\\.\\d+)?) coins!$");
   private static final Pattern AH_LISTED = Pattern.compile("^(BIN )?Auction started for (.+)!$");
   private static final Pattern AH_PURCHASED = Pattern.compile("^You purchased (.+?) for ([\\d,]+(?:\\.\\d+)?) coins!$");
   private static final Pattern AH_COLLECTED = Pattern.compile("^You collected ([\\d,]+(?:\\.\\d+)?) coins from selling (.+?) to (.+) in an auction!$");
   private static final Pattern AH_CANCELED = Pattern.compile("^You canceled your auction for (.+)!$");
   private static final Pattern AH_RETURNED = Pattern.compile("^You claimed (.+?) back from your expired auction!$");

   private TradeParser() {
   }

   public static Optional<Trade> parse(long ts, String rawLine) {
      if (rawLine == null) {
         return Optional.empty();
      } else {
         String line = rawLine.strip();
         if (!line.isEmpty() && !PROGRESS.matcher(line).matches()) {
            try {
               Matcher m;
               if ((m = BUY_SETUP.matcher(line)).matches()) {
                  return bazaar(ts, Trade.Kind.BZ_BUY_ORDER_SETUP, m, line);
               }

               if ((m = SELL_SETUP.matcher(line)).matches()) {
                  return bazaar(ts, Trade.Kind.BZ_SELL_OFFER_SETUP, m, line);
               }

               if ((m = INSTA_BUY.matcher(line)).matches()) {
                  return bazaar(ts, Trade.Kind.BZ_INSTA_BUY, m, line);
               }

               if ((m = INSTA_SELL.matcher(line)).matches()) {
                  return bazaar(ts, Trade.Kind.BZ_INSTA_SELL, m, line);
               }

               if ((m = CLAIM_BOUGHT.matcher(line)).matches()) {
                  return Optional.of(new Trade(ts, Trade.Kind.BZ_CLAIM_BOUGHT, name(m.group(2)), qty(m.group(1)), num(m.group(3)), num(m.group(4)), null, line));
               }

               if ((m = CLAIM_SOLD.matcher(line)).matches()) {
                  return Optional.of(new Trade(ts, Trade.Kind.BZ_CLAIM_SOLD, name(m.group(3)), qty(m.group(2)), num(m.group(1)), num(m.group(4)), null, line));
               }

               if ((m = AH_LISTED.matcher(line)).matches()) {
                  return Optional.of(
                     new Trade(ts, Trade.Kind.AH_LISTED, name(m.group(2)), 1, Double.NaN, Double.NaN, m.group(1) != null ? "BIN" : "Auction", line)
                  );
               }

               if ((m = AH_PURCHASED.matcher(line)).matches()) {
                  double coins = num(m.group(2));
                  return Optional.of(new Trade(ts, Trade.Kind.AH_PURCHASED, name(m.group(1)), 1, coins, coins, null, line));
               }

               if ((m = AH_COLLECTED.matcher(line)).matches()) {
                  double coins = num(m.group(1));
                  return Optional.of(new Trade(ts, Trade.Kind.AH_COLLECTED, name(m.group(2)), 1, coins, coins, m.group(3), line));
               }

               if ((m = AH_CANCELED.matcher(line)).matches()) {
                  return Optional.of(new Trade(ts, Trade.Kind.AH_CANCELED, name(m.group(1)), 1, Double.NaN, Double.NaN, null, line));
               }

               if ((m = AH_RETURNED.matcher(line)).matches()) {
                  return Optional.of(new Trade(ts, Trade.Kind.AH_RETURNED, name(m.group(1)), 1, Double.NaN, Double.NaN, null, line));
               }
            } catch (NumberFormatException var7) {
            }

            return CaptureFilter.interesting(line)
               ? Optional.of(new Trade(ts, Trade.Kind.OTHER, null, 0, Double.NaN, Double.NaN, null, line))
               : Optional.empty();
         } else {
            return Optional.empty();
         }
      }
   }

   private static Optional<Trade> bazaar(long ts, Trade.Kind kind, Matcher m, String line) {
      int qty = qty(m.group(1));
      double coins = num(m.group(3));
      return Optional.of(new Trade(ts, kind, name(m.group(2)), qty, coins, qty > 0 ? coins / qty : Double.NaN, null, line));
   }

   private static String name(String s) {
      return s.strip().replaceAll("\\s+", " ");
   }

   private static int qty(String s) {
      return Integer.parseInt(s.replace(",", ""));
   }

   private static double num(String s) {
      return Double.parseDouble(s.replace(",", ""));
   }
}
