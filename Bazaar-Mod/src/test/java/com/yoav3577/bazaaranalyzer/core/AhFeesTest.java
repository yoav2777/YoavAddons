package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AhFeesTest {
   private static final long H = 3600000L;

   @Test
   void durationFee() {
      assertEquals(20.0, AhFees.durationFee(H, 2 * H));
      assertEquals(1200.0, AhFees.durationFee(H, 49 * H));
      assertEquals(55200.0, AhFees.durationFee(H, 337 * H));
      assertEquals(0.0, AhFees.durationFee(0L, 5 * H));
   }

   @Test
   void listingRateFrom100M() {
      assertEquals(0.025, AhFees.listingRate(1.0E8));
      assertEquals(0.02, AhFees.listingRate(1.0E7));
   }
}
