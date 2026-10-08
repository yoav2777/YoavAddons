package com.yoav3577.bazaaranalyzer.core;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cloud sync (CloudSync) files: the lowball tracker's lines, one gist file per kind and UTC month
 * ("player-trades-2026-10.jsonl"), and the merge rule. Both sides only ever add lines (a union that keeps repeats),
 * so a push never erases what another PC uploaded.
 */
public final class SyncData {
   public static final String PLAYER_TRADES = "player-trades";
   public static final String GEM_LOG = "gem-log";
   /** Bazaar/AH chat lines ("ts\tline", like capture.log). */
   public static final String CAPTURE = "capture";
   private static final List<String> KINDS = List.of(PLAYER_TRADES, GEM_LOG, CAPTURE);
   private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
   private static final Comparator<String> ORDER = Comparator.comparingLong(SyncData::ts).thenComparing(Comparator.naturalOrder());

   private SyncData() {
   }

   public static String fileName(String kind, long ts) {
      return kind + "-" + MONTH.format(Instant.ofEpochMilli(ts)) + (CAPTURE.equals(kind) ? ".log" : ".jsonl");
   }

   /** The kind of a file named by fileName, or null for any other gist file. */
   public static String kindOf(String file) {
      for (String k : KINDS) {
         if (file.matches(k + "-\\d{4}-\\d{2}\\.(jsonl|log)")) {
            return k;
         }
      }

      return null;
   }

   /** The time of a line: "ts\t..." (capture) or {"ts":...} (jsonl); 0 when it has none. */
   public static long ts(String line) {
      int start = line.startsWith("{\"ts\":") ? 6 : 0;
      int end = start;
      while (end < line.length() && Character.isDigit(line.charAt(end))) {
         end++;
      }

      try {
         return end > start ? Long.parseLong(line.substring(start, end)) : 0L;
      } catch (NumberFormatException e) {
         return 0L;
      }
   }

   /** The non-blank lines of a file. */
   public static List<String> lines(String text) {
      List<String> out = new ArrayList<>();
      if (text != null) {
         for (String l : text.split("\n")) {
            String s = l.endsWith("\r") ? l.substring(0, l.length() - 1) : l;
            if (!s.isBlank()) {
               out.add(s);
            }
         }
      }

      return out;
   }

   /** The lines of {@code other} that {@code have} lacks (a line twice in other and once in have: one is missing). */
   public static List<String> missing(Collection<String> have, Collection<String> other) {
      Map<String, Integer> left = counts(have);
      List<String> out = new ArrayList<>();
      for (String l : other) {
         Integer n = left.get(l);
         if (n == null || n == 0) {
            out.add(l);
         } else {
            left.put(l, n - 1);
         }
      }

      return out;
   }

   /** Every line of both (a repeated line as often as the side that has it most), by time then text, one per line. */
   public static String union(Collection<String> a, Collection<String> b) {
      List<String> all = new ArrayList<>(a);
      all.addAll(missing(a, b));
      all.sort(ORDER);
      StringBuilder sb = new StringBuilder();
      for (String l : all) {
         sb.append(l).append('\n');
      }

      return sb.toString();
   }

   /** The lines grouped by the file their time falls in. */
   public static Map<String, List<String>> byFile(String kind, Collection<String> lines) {
      Map<String, List<String>> out = new TreeMap<>();
      for (String l : lines) {
         out.computeIfAbsent(fileName(kind, ts(l)), f -> new ArrayList<>()).add(l);
      }

      return out;
   }

   /** The files to upload: each local file merged with the gist's copy, when that adds a line the gist lacks. */
   public static Map<String, String> changed(Map<String, List<String>> local, Map<String, String> remote) {
      Map<String, String> out = new TreeMap<>();
      for (Map.Entry<String, List<String>> e : local.entrySet()) {
         List<String> theirs = lines(remote.get(e.getKey()));
         if (!missing(theirs, e.getValue()).isEmpty()) {
            out.put(e.getKey(), union(theirs, e.getValue()));
         }
      }

      return out;
   }

   /** The gist's lines of one kind, every month together. */
   public static List<String> linesOf(String kind, Map<String, String> remote) {
      List<String> out = new ArrayList<>();
      for (Map.Entry<String, String> e : new TreeMap<>(remote).entrySet()) {
         if (kind.equals(kindOf(e.getKey()))) {
            out.addAll(lines(e.getValue()));
         }
      }

      return out;
   }

   private static Map<String, Integer> counts(Collection<String> lines) {
      Map<String, Integer> out = new HashMap<>();
      for (String l : lines) {
         out.merge(l, 1, Integer::sum);
      }

      return out;
   }
}
