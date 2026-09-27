package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TradeChatTest {
   @Test
   void recognisesCancelLines() {
      assertTrue(TradeChat.isCancel("You cancelled the trade!"));
      assertTrue(TradeChat.isCancel("  Steve canceled the trade!  "));
      assertTrue(TradeChat.isCancel("[MVP+] Bob cancelled the trade!"));
      assertFalse(TradeChat.isCancel("You cancelled the trade"));
      assertFalse(TradeChat.isCancel(null));
   }

   @Test
   void ignoresPlayerChatThatQuotesTheCancelLine() {
      assertFalse(TradeChat.isCancel("[MVP+] Bob: lol he cancelled the trade!"));
      assertFalse(TradeChat.isCancel("Guild > Bob: someone cancelled the trade!"));
      assertFalse(TradeChat.isCancel("Party > [VIP] Bob: i cancelled the trade!"));
   }

   @Test
   void recognisesCompleteLines() {
      assertTrue(TradeChat.isComplete("Trade completed with [MVP+] Steve!"));
      assertFalse(TradeChat.isComplete("Trade completed"));
      assertFalse(TradeChat.isComplete("You cancelled the trade!"));
   }

   @Test
   void verdictUsesNewestStampSinceTheWindow() {
      assertNull(TradeChat.verdict(0, 0, 1000));
      assertNull(TradeChat.verdict(500, 600, 1000));
      assertEquals(Boolean.FALSE, TradeChat.verdict(1500, 0, 1000));
      assertEquals(Boolean.TRUE, TradeChat.verdict(0, 1500, 1000));
      assertEquals(Boolean.TRUE, TradeChat.verdict(1200, 1500, 1000));
      assertEquals(Boolean.FALSE, TradeChat.verdict(1600, 1500, 1000));
      assertEquals(Boolean.TRUE, TradeChat.verdict(900, 1500, 1000));
   }
}
