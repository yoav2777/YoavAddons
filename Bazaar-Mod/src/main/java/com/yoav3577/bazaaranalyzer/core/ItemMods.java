package com.yoav3577.bazaaranalyzer.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ItemMods(
   String id,
   String name,
   Map<String, Integer> enchants,
   String reforge,
   int stars,
   boolean recombobulated,
   int hotPotatoBooks,
   Map<String, String> gemsFlat,
   List<String> scrolls,
   String powerScroll,
   Map<String, Integer> counts,
   String petType,
   String petTier,
   String rune
) {
   public ItemMods {
      // Enchants and counts keep insertion order (the order the item NBT lists them in).
      enchants = enchants == null ? Map.of() : new LinkedHashMap<>(enchants);
      gemsFlat = gemsFlat == null ? Map.of() : Map.copyOf(gemsFlat);
      scrolls = scrolls == null ? List.of() : List.copyOf(scrolls);
      counts = counts == null ? Map.of() : new LinkedHashMap<>(counts);
   }

   public static ItemMods plain(String id, String name) {
      return new ItemMods(id, name, null, null, 0, false, 0, null, null, null, null, null, null, null);
   }

   public List<TradeItem.Gem> gems() {
      return Gems.parse(this.gemsFlat);
   }
}
