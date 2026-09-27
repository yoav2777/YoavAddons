package com.yoav3577.bazaaranalyzer.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RequestGuardTest {
   @Test
   void originsThatMayReadTheData() {
      assertTrue(RequestGuard.originAllowed(null));
      assertTrue(RequestGuard.originAllowed("http://127.0.0.1:47831"));
      assertTrue(RequestGuard.originAllowed("http://localhost:47850"));
      assertTrue(RequestGuard.originAllowed("http://127.0.0.1:47860"));
      assertTrue(RequestGuard.originAllowed("https://yoav2777.github.io"));
   }

   @Test
   void otherOriginsAreRefused() {
      assertFalse(RequestGuard.originAllowed("http://127.0.0.1:8080"));
      assertFalse(RequestGuard.originAllowed("http://127.0.0.1:47870"));
      assertFalse(RequestGuard.originAllowed("https://127.0.0.1:47831"));
      assertFalse(RequestGuard.originAllowed("http://evil.example:47831"));
      assertFalse(RequestGuard.originAllowed("https://yoav2777.github.io.evil.example"));
      assertFalse(RequestGuard.originAllowed("http://yoav2777.github.io"));
      assertFalse(RequestGuard.originAllowed("https://someone.github.io"));
   }

   @Test
   void hostHeader() {
      assertTrue(RequestGuard.hostAllowed("127.0.0.1:47860", 47860));
      assertTrue(RequestGuard.hostAllowed("localhost:47860", 47860));
      assertFalse(RequestGuard.hostAllowed("evil.example:47860", 47860));
   }
}
