package com.yoav3577.bazaaranalyzer.core;

/** Rate-limits repeated-failure logging: the first failure of a kind logs in full, later ones are suppressed until a summary is due. */
public final class FailureLimiter {
   private final int summaryEvery;
   private String lastKind;
   private int suppressed;

   public FailureLimiter(int summaryEvery) {
      this.summaryEvery = summaryEvery;
   }

   public synchronized FailureLimiter.Action record(String kind) {
      if (!kind.equals(this.lastKind)) {
         this.lastKind = kind;
         this.suppressed = 0;
         return FailureLimiter.Action.LOG_FULL;
      }

      this.suppressed++;
      return this.suppressed % this.summaryEvery == 0 ? FailureLimiter.Action.LOG_SUMMARY : FailureLimiter.Action.SUPPRESS;
   }

   public synchronized int suppressedCount() {
      return this.suppressed;
   }

   public synchronized void reset() {
      this.lastKind = null;
      this.suppressed = 0;
   }

   public enum Action {
      LOG_FULL,
      LOG_SUMMARY,
      SUPPRESS
   }
}
