package com.yoav3577.bazaaranalyzer.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class Days {
   private static final ZoneId ISRAEL = ZoneId.of("Asia/Jerusalem");

   private Days() {
   }

   public static String key(long epochMillis) {
      ZonedDateTime z = Instant.ofEpochMilli(epochMillis).atZone(ISRAEL);
      LocalDate d = z.toLocalDate();
      return (z.getHour() < 12 ? d.minusDays(1L) : d).toString();
   }
}
