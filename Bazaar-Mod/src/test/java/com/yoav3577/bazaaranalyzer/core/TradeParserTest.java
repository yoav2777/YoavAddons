package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TradeParserTest {
   @Test
   void parsesInstaBuy() {
      Trade t = TradeParser.parse(1L, "[Bazaar] Bought 64x Enchanted Diamond for 1,280,000 coins!").orElseThrow();
      assertEquals(Trade.Kind.BZ_INSTA_BUY, t.kind());
      assertEquals("Enchanted Diamond", t.item());
      assertEquals(64, t.qty());
      assertEquals(20_000.0, t.unit());
   }

   @Test
   void parsesAhCollect() {
      Trade t = TradeParser.parse(1L, "You collected 9,900,000 coins from selling Hyperion to [MVP+] Steve in an auction!").orElseThrow();
      assertEquals(Trade.Kind.AH_COLLECTED, t.kind());
      assertEquals(9_900_000.0, t.coins());
      assertEquals("[MVP+] Steve", t.other());
   }

   @Test
   void ignoresProgressLinesAndKeepsUnknownAnchoredOnes() {
      assertTrue(TradeParser.parse(1L, "[Bazaar] Claiming order...").isEmpty());
      assertEquals(Trade.Kind.OTHER, TradeParser.parse(1L, "[Bazaar] Something new").orElseThrow().kind());
      assertTrue(TradeParser.parse(1L, "hello").isEmpty());
   }

   @Test
   void tradeBookLoadsCaptureLines() {
      TradeBook book = new TradeBook(10);
      int added = book.loadCaptureLines(List.of("1000\t[Bazaar] Sold 2x Enchanted Diamond for 300 coins!", "not a record", "abc\t[Bazaar] x"));
      assertEquals(1, added);
      assertEquals(Trade.Kind.BZ_INSTA_SELL, book.snapshot().get(0).kind());
   }

   private static String sold(long ts) {
      return ts + "\t[Bazaar] Sold 1x Enchanted Diamond for " + ts + " coins!";
   }

   @Test
   void captureTailKeepsTheNewestTradesAcrossBothFilesAndSkipsNonTrades() {
      TradeBook book = new TradeBook(3);
      List<String> old = List.of(sold(1), sold(2), sold(3));
      List<String> cur = List.of(sold(4), "5\t[Bazaar] Claiming order...", "junk", sold(6), "7\t[Bazaar] Claiming order...");
      assertEquals(3, book.loadCaptureTail(List.of(old, cur)));
      assertEquals(List.of(3.0, 4.0, 6.0), book.snapshot().stream().map(Trade::coins).toList());
   }

   @Test
   void captureTailGoesBeforeTradesCapturedWhileItLoaded() {
      TradeBook book = new TradeBook(3);
      book.add(TradeParser.parse(100, "[Bazaar] Sold 1x Enchanted Diamond for 100 coins!").orElseThrow());
      book.loadCaptureTail(List.of(List.of(sold(1), sold(2), sold(3))));
      assertEquals(List.of(2.0, 3.0, 100.0), book.snapshot().stream().map(Trade::coins).toList());
   }
}
