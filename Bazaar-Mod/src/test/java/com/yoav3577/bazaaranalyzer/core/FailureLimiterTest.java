package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FailureLimiterTest {
   @Test
   void logsTheFirstFailureThenSuppresses() {
      FailureLimiter lim = new FailureLimiter(600);
      assertEquals(FailureLimiter.Action.LOG_FULL, lim.record("java.lang.RuntimeException"));
      assertEquals(FailureLimiter.Action.SUPPRESS, lim.record("java.lang.RuntimeException"));
      assertEquals(FailureLimiter.Action.SUPPRESS, lim.record("java.lang.RuntimeException"));
   }

   @Test
   void logsASummaryEveryNSuppressedFailures() {
      FailureLimiter lim = new FailureLimiter(3);
      lim.record("java.lang.RuntimeException");
      assertEquals(FailureLimiter.Action.SUPPRESS, lim.record("java.lang.RuntimeException"));
      assertEquals(FailureLimiter.Action.SUPPRESS, lim.record("java.lang.RuntimeException"));
      assertEquals(FailureLimiter.Action.LOG_SUMMARY, lim.record("java.lang.RuntimeException"));
      assertEquals(3, lim.suppressedCount());
   }

   @Test
   void logsInFullAgainWhenTheFailureKindChanges() {
      FailureLimiter lim = new FailureLimiter(600);
      lim.record("java.lang.RuntimeException");
      lim.record("java.lang.RuntimeException");
      assertEquals(FailureLimiter.Action.LOG_FULL, lim.record("java.lang.NullPointerException"));
   }

   @Test
   void resetStartsOverAsIfNothingFailedYet() {
      FailureLimiter lim = new FailureLimiter(600);
      lim.record("java.lang.RuntimeException");
      lim.record("java.lang.RuntimeException");
      lim.reset();
      assertEquals(FailureLimiter.Action.LOG_FULL, lim.record("java.lang.RuntimeException"));
      assertEquals(0, lim.suppressedCount());
   }
}
