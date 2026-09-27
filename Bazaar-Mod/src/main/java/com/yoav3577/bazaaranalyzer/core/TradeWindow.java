package com.yoav3577.bazaaranalyzer.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TradeWindow {
   private static final Pattern TITLE = Pattern.compile("^You {5,}(.+)$");
   private static final Pattern TRADING_WITH = Pattern.compile("Trading with (?:\\[[^\\]]*]\\s*)?(\\w{1,16})");

   private TradeWindow() {
   }

   public static List<Integer> theirSlotIndexes() {
      List<Integer> out = new ArrayList<>();

      for (int row = 0; row < 4; row++) {
         for (int col = 0; col < 4; col++) {
            out.add(row * 9 + 5 + col);
         }
      }

      return out;
   }

   public static boolean isTradeTitle(String title) {
      return title != null && TITLE.matcher(title.strip()).matches();
   }

   public static Optional<TradeWindow.Snapshot> parse(String title, List<TradeWindow.RawStack> slots) {
      if (title == null) {
         return Optional.empty();
      } else {
         Matcher m = TITLE.matcher(title.strip());
         if (m.matches() && slots != null && slots.size() >= 36) {
            double[] yourCoins = new double[]{0.0};
            double[] theirCoins = new double[]{0.0};
            List<TradeItem> yours = side(slots, 0, yourCoins);
            List<TradeItem> theirs = side(slots, 5, theirCoins);
            return Optional.of(new TradeWindow.Snapshot(partner(m.group(1).strip(), slots), yourCoins[0], theirCoins[0], yours, theirs));
         } else {
            return Optional.empty();
         }
      }
   }

   private static String partner(String fromTitle, List<TradeWindow.RawStack> slots) {
      for (TradeWindow.RawStack s : slots) {
         if (s != null) {
            for (String l : s.lore()) {
               Matcher p = TRADING_WITH.matcher(l);
               if (p.find()) {
                  return p.group(1);
               }
            }
         }
      }

      return fromTitle;
   }

   private static List<TradeItem> side(List<TradeWindow.RawStack> slots, int firstColumn, double[] coinsOut) {
      List<TradeItem> items = new ArrayList<>();

      for (int row = 0; row < 4; row++) {
         for (int col = 0; col < 4; col++) {
            int idx = row * 9 + firstColumn + col;
            if (idx < slots.size()) {
               TradeWindow.RawStack s = slots.get(idx);
               if (s != null && s.count() > 0 && s.name() != null && !s.name().isBlank()) {
                  if (isCoins(s)) {
                     double v = Coins.parse(s.name().endsWith(" coins") ? s.name() : (s.lore().isEmpty() ? "" : s.lore().get(s.lore().size() - 1)));
                     if (!Double.isNaN(v)) {
                        coinsOut[0] += v;
                     }
                  } else {
                     items.add(new TradeItem(s.id(), s.uuid(), s.name(), s.count(), s.gems(), s.parts()));
                  }
               }
            }
         }
      }

      return items;
   }

   private static boolean isCoins(TradeWindow.RawStack s) {
      if (s.name().endsWith(" coins")) {
         return true;
      } else {
         for (String l : s.lore()) {
            if (l.contains("Lump-sum amount")) {
               return true;
            }
         }

         return false;
      }
   }

   public record RawStack(String name, int count, List<String> lore, String id, String uuid, List<TradeItem.Gem> gems, List<String> parts) {
      public RawStack {
         lore = lore == null ? List.of() : List.copyOf(lore);
         gems = gems == null ? List.of() : List.copyOf(gems);
         parts = parts == null ? List.of() : List.copyOf(parts);
      }

      public RawStack(String name, int count, List<String> lore, String id, String uuid, List<TradeItem.Gem> gems) {
         this(name, count, lore, id, uuid, gems, null);
      }
   }

   public record Snapshot(String partner, double givenCoins, double receivedCoins, List<TradeItem> given, List<TradeItem> received) {
      public boolean isEmpty() {
         return this.givenCoins == 0.0 && this.receivedCoins == 0.0 && this.given.isEmpty() && this.received.isEmpty();
      }
   }
}
