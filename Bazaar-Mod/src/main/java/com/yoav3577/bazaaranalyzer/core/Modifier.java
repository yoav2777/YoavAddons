package com.yoav3577.bazaaranalyzer.core;

import java.util.List;

public record Modifier(Modifier.Group group, String label, double value, List<Filter> filters) {
   public Modifier {
      filters = filters == null ? List.of() : List.copyOf(filters);
   }

   public static enum Group {
      IDENTITY,
      ENCHANT,
      /**
       * Always matched, whatever its price: an enchant level the Bazaar doesn't sell (Ender Slayer 7), or an enchant whose
       * presence splits the market at any level (Hecatomb).
       */
      MUST_MATCH,
      STARS,
      RECOMB,
      GEMS,
      SCROLLS,
      POWER_SCROLL,
      POTATO_BOOKS,
      OTHER;
   }
}
