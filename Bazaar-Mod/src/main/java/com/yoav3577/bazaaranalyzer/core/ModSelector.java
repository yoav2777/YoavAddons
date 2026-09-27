package com.yoav3577.bazaaranalyzer.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class ModSelector {
   private ModSelector() {
   }

   public static List<Modifier> keepKnownEnchants(List<Modifier> all, Set<String> known) {
      return known.isEmpty()
         ? all
         : all.stream()
            .filter(m -> m.group() != Modifier.Group.ENCHANT && m.group() != Modifier.Group.MUST_MATCH || m.filters().stream().allMatch(f -> known.contains(f.type())))
            .toList();
   }

   public static List<Modifier> select(List<Modifier> all, double baseValue, LinkSettings settings) {
      double total = !Double.isNaN(baseValue) && !(baseValue < 0.0) ? baseValue : 0.0;

      for (Modifier m : all) {
         if (!Double.isNaN(m.value())) {
            total += m.value();
         }
      }

      // IDENTITY is the pet rarity (the only identity modifier ModPricer makes); the petRarity setting turns it off.
      List<Modifier> chosen = new ArrayList<>(all.stream().filter(mx -> mx.group() == Modifier.Group.IDENTITY && settings.petRarity()).toList());
      // a level the Bazaar doesn't sell splits the market (Atomsplit: Ender Slayer 7 145M, 6 83M): always matched
      chosen.addAll(all.stream().filter(mx -> mx.group() == Modifier.Group.MUST_MATCH).toList());
      chosen.addAll(
         all.stream()
            .filter(mx -> mx.group() == Modifier.Group.ENCHANT && !Double.isNaN(mx.value()) && mx.value() > settings.minEnchantValue())
            .sorted(Comparator.comparingDouble(Modifier::value).reversed())
            .limit(settings.maxEnchants())
            .toList()
      );
      double bar = settings.minShare() * total;

      for (Modifier mx : all) {
         if (mx.group() != Modifier.Group.ENCHANT && mx.group() != Modifier.Group.MUST_MATCH && mx.group() != Modifier.Group.IDENTITY && !Double.isNaN(mx.value()) && mx.value() > bar) {
            chosen.add(mx);
         }
      }

      return chosen;
   }
}
