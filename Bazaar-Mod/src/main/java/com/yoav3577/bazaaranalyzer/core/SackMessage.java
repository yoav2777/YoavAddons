package com.yoav3577.bazaaranalyzer.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel's "[Sacks] +1,234 items. (Last 30s.)" chat line: the hover text of each part lists what went in or out
 * ("+1,234 Mithril (Mining Sack)", "-5 Coal (Mining Sack, Large Mining Sack)"). Needs Sack Notifications on in /settings.
 * Also reads the SkyBlock area from the tab list, for "on a mining island".
 */
public final class SackMessage {
   private static final Pattern AREA = Pattern.compile("^Area:\\s*(.+)$");
   private static final Set<String> MINING = Set.of("dwarven mines", "crystal hollows", "mineshaft", "gold mine", "deep caverns");
   private static final Pattern LINE = Pattern.compile("^\\s*([+-][\\d,]+)\\s+(.+?)\\s+\\(([^()]*)\\)\\s*$");

   private SackMessage() {
   }

   public static String plain(String s) {
      return s == null ? "" : s.replaceAll("§.", "").replace('\u00a0', ' ').strip();
   }

   /** "Dwarven Mines", "Hub"... from the tab list lines, or null (not SkyBlock / not loaded yet). */
   public static String area(List<String> tab) {
      for (String line : tab) {
         Matcher m = AREA.matcher(plain(line));
         if (m.find()) {
            return m.group(1).strip();
         }
      }

      return null;
   }

   public static boolean isMining(String area) {
      return area != null && (MINING.contains(area.toLowerCase(Locale.ROOT)) || area.toLowerCase(Locale.ROOT).contains("mineshaft"));
   }

   public static boolean isSacks(String plain) {
      return plain(plain).startsWith("[Sacks]");
   }

   /** Item name -> change (negative for removed), summed over the hover texts; empty when nothing matches. */
   public static Map<String, Long> changes(List<String> hovers) {
      Map<String, Long> out = new LinkedHashMap<>();
      for (String h : hovers) {
         if (h == null) {
            continue;
         }

         for (String line : plain(h).split("\\R")) {
            Matcher m = LINE.matcher(line);
            if (!m.find()) {
               continue;
            }

            try {
               long n = Long.parseLong(m.group(1).replace(",", "").replace("+", ""));
               out.merge(m.group(2).strip(), n, Long::sum);
            } catch (NumberFormatException ignored) {
            }
         }
      }

      return out;
   }
}
