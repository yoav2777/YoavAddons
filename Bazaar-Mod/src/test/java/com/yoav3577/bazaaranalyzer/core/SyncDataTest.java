package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SyncDataTest {
   // 2026-09-30T23:59:59Z and 2026-10-01T00:00:00Z
   private static final long SEP = 1790812799000L;
   private static final long OCT = 1790812800000L;

   @Test
   void filesAreOnePerKindAndUtcMonth() {
      assertEquals("player-trades-2026-09.jsonl", SyncData.fileName(SyncData.PLAYER_TRADES, SEP));
      assertEquals("capture-2026-10.log", SyncData.fileName(SyncData.CAPTURE, OCT));
      assertEquals(SyncData.GEM_LOG, SyncData.kindOf("gem-log-2026-10.jsonl"));
      assertEquals(SyncData.CAPTURE, SyncData.kindOf("capture-2026-10.log"));
      assertNull(SyncData.kindOf("yoavaddons-sync.md"));
      assertNull(SyncData.kindOf("player-trades.jsonl"));
   }

   @Test
   void readsTheTimeOfBothLineKinds() {
      assertEquals(OCT, SyncData.ts("{\"ts\":" + OCT + ",\"kind\":\"START\"}"));
      assertEquals(OCT, SyncData.ts(OCT + "\t[Bazaar] Sold 1x Fine Ruby Gemstone for 100 coins!"));
      assertEquals(0L, SyncData.ts("garbage"));
   }

   @Test
   void missingCountsRepeats() {
      assertEquals(List.of("b", "a"), SyncData.missing(List.of("a"), List.of("a", "b", "a")));
      assertEquals(List.of(), SyncData.missing(List.of("a", "a"), List.of("a")));
   }

   @Test
   void unionKeepsEveryLineOfBothSidesInTimeOrder() {
      String a = "{\"ts\":3,\"x\":1}";
      String b = "{\"ts\":1,\"x\":2}";
      String c = "{\"ts\":2,\"x\":3}";
      assertEquals(b + "\n" + c + "\n" + a + "\n", SyncData.union(List.of(a, b), List.of(c, a)));
   }

   @Test
   void uploadsOnlyMonthsWithLinesTheGistLacks() {
      String sep = SEP + "\tone";
      String oct = OCT + "\ttwo";
      String other = (OCT + 5) + "\tfrom the laptop";
      Map<String, String> remote = Map.of("capture-2026-09.log", sep + "\n", "capture-2026-10.log", other + "\n");
      Map<String, String> up = SyncData.changed(SyncData.byFile(SyncData.CAPTURE, List.of(sep, oct)), remote);
      assertEquals(Map.of("capture-2026-10.log", oct + "\n" + other + "\n"), up);
      // once uploaded, nothing more to do
      assertTrue(SyncData.changed(SyncData.byFile(SyncData.CAPTURE, List.of(sep, oct)), Map.of("capture-2026-09.log", sep + "\n", "capture-2026-10.log", up.get("capture-2026-10.log"))).isEmpty());
   }

   @Test
   void linesOfReadsEveryMonthOfOneKind() {
      Map<String, String> remote = Map.of("gem-log-2026-10.jsonl", "b\n", "gem-log-2026-09.jsonl", "a\r\n\n", "capture-2026-10.log", "c\n", "yoavaddons-sync.md", "hi");
      assertEquals(List.of("a", "b"), SyncData.linesOf(SyncData.GEM_LOG, remote));
   }

   @Test
   void gemLogLinesSurviveTheRoundTrip() {
      GemLog e = new GemLog(OCT, GemLog.Kind.REMOVED, "uuid-1", "Divan's Drill", List.of(new Parts.Part("FINE_RUBY_GEM", "Fine Ruby Gemstone")));
      assertEquals(e.toJson(), GemLog.parse(e.toJson()).orElseThrow().toJson());
      assertEquals(OCT, SyncData.ts(e.toJson()));
   }

   @Test
   void prefsKeepTheTokenAndForgetTheGistWhenItChanges() {
      SyncPrefs p = SyncPrefs.fromJson(SyncPrefs.DEFAULT.withToken(" ghp_abcdefghijklmnopqrstuvwxyz0123456789 ").withGist("g1").toJson());
      assertEquals("ghp_abcdefghijklmnopqrstuvwxyz0123456789", p.token());
      assertEquals("g1", p.gistId());
      assertTrue(p.on());
      assertNull(p.withToken("ghp_other").gistId());
      assertFalse(p.withEnabled(false).on());
      assertFalse(SyncPrefs.fromJson(null).on());
      assertTrue(SyncPrefs.looksLikeToken("github_pat_11ABCDEFG0123456789_abcdefghijklmnopqrstuvwxyz"));
      assertFalse(SyncPrefs.looksLikeToken("ghp_short"));
   }
}
