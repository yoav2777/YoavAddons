package com.yoav3577.bazaaranalyzer.core.craft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clean craft cost, "from scratch" (buy orders + cheapest recipes) and "easy" (insta-buy / lowest BIN / NPC coins).
 * Port of the site's craft/craftCore.js (rules: notes/craft-core.md). Ids are NEU ids (HYPERION, LOG-2, SHARPNESS;5,
 * ENDER_DRAGON;4); bazaar ids (LOG:2, ENCHANTMENT_SHARPNESS_5) and lowestbins keys are found through the lookups.
 */
final class CraftCore {
   static final String COIN = "SKYBLOCK_COIN";
   static final List<String> PET_TIERS = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
   private static final Map<String, String> TYPES = Map.of("c", "craft", "f", "forge", "n", "npc", "t", "other", "k", "other");
   private static final Map<String, String> NOTES = Map.of("t", "trade", "k", "Kat pet upgrade");
   private static final double DEAD_BO = 0.01;   // top buy order below 1% of insta-buy = dead order, ignored
   private static final int MAX_STACK = 15;      // pricing depth guard (deepest real tree: DIVAN_DRILL = 10)
   private static final Pattern BOOK = Pattern.compile("^ENCHANTMENT_(.+)_(\\d+)$");
   private static final Pattern DAMAGE = Pattern.compile("^([A-Z0-9_]+):(\\d+)$");
   private static final Pattern NEU_DAMAGE = Pattern.compile("^([A-Z0-9_]+)-(\\d+)$");
   private static final Pattern NEU_LEVEL = Pattern.compile("^([A-Z0-9_]+);(\\d+)$");
   private static final Pattern PLAIN = Pattern.compile("^[A-Z0-9_]+$");
   private static final Pattern JS_NUMBER = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?|[+-]?Infinity");

   private CraftCore() {
   }

   record Input(String id, double qty) {
   }

   /** type craft | forge | npc | other (trade, Kat upgrade); note "trade" / "Kat pet upgrade" or null. */
   record Recipe(String type, double output, List<Input> inputs, double coins, String note) {
   }

   /** A price. unit NaN = unavailable; cut = touched a recipe-loop cut (never memoized); fallback = easy mode used the from-scratch cost. */
   record Px(double unit, String method, Recipe recipe, boolean cut, boolean fallback) {
      static final Px NONE = new Px(Double.NaN, "unavailable", null, false, false);

      Px(double unit, String method) {
         this(unit, method, null, false, false);
      }
   }

   /** Result of the clean craft: totals NaN when the recipe can't be priced; missing = unpriced ids in either tree. */
   record Clean(boolean craftable, double fromScratch, double easy, Set<String> missing) {
   }

   private record Cost(double unit, boolean cut) {
   }

   private interface Pricer {
      Px price(String id, List<String> stack);
   }

   // ---------------------------------------------------------------- numbers (JS semantics)
   /** JS Number(v); null = undefined. */
   static double jsNumber(JsonElement v) {
      if (v == null) {
         return Double.NaN;
      } else if (v.isJsonNull()) {
         return 0;
      } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
         return v.getAsDouble();
      } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
         return v.getAsBoolean() ? 1 : 0;
      } else {
         String s = jsString(v).strip();
         return s.isEmpty() ? 0 : JS_NUMBER.matcher(s).matches() ? Double.parseDouble(s) : Double.NaN;
      }
   }

   /** ccNum: a usable price (finite and > 0), else NaN. */
   static double num(JsonElement v) {
      double n = jsNumber(v);
      return Double.isFinite(n) && n > 0 ? n : Double.NaN;
   }

   /** JS String(v); null = undefined. */
   static String jsString(JsonElement v) {
      if (v == null) {
         return "undefined";
      } else if (v.isJsonNull()) {
         return "null";
      } else if (v instanceof JsonArray a) {
         return join(a, ",");
      } else if (v instanceof JsonObject) {
         return "[object Object]";
      } else {
         return v.getAsJsonPrimitive().isNumber() ? jsNum(v.getAsDouble()) : v.getAsString();
      }
   }

   /** JS array.join(sep). */
   static String join(JsonArray a, String sep) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < a.size(); i++) {
         sb.append(i > 0 ? sep : "").append(a.get(i).isJsonNull() ? "" : jsString(a.get(i)));
      }

      return sb.toString();
   }

   /** JS number -> string, for the ids built from numbers (levels). */
   static String jsNum(double v) {
      if (!Double.isFinite(v)) {
         return Double.isNaN(v) ? "NaN" : v > 0 ? "Infinity" : "-Infinity";
      }

      return v == Math.rint(v) && Math.abs(v) < 1e21
         ? new BigDecimal(v).toPlainString()
         : new BigDecimal(Double.toString(v)).stripTrailingZeros().toPlainString();
   }

   // ---------------------------------------------------------------- ids
   /** Any id form -> NEU id (ENCHANTMENT_X_5 -> X;5, LOG:2 -> LOG-2). */
   static String neuId(String id) {
      id = id == null ? "" : id.strip().toUpperCase(Locale.ROOT);
      Matcher m = BOOK.matcher(id);
      if (m.matches()) {
         return m.group(1) + ";" + m.group(2);
      }

      m = DAMAGE.matcher(id);
      return m.matches() ? m.group(1) + "-" + m.group(2) : id;
   }

   /** AH tag (+ pet tier) -> NEU id: PET_ENDER_DRAGON + LEGENDARY -> ENDER_DRAGON;4. */
   static String recipeId(String tag, String tier) {
      String t = tag == null ? "" : tag.strip().toUpperCase(Locale.ROOT);
      if (t.startsWith("PET_") && tier != null) {
         int i = PET_TIERS.indexOf(tier.toUpperCase(Locale.ROOT));
         if (i >= 0) {
            return t.substring(4) + ";" + i;
         }
      }

      return neuId(t);
   }

   static CraftCtx.Bz bazaarEntry(String id, CraftCtx ctx) {
      Map<String, CraftCtx.Bz> bz = ctx.bazaar;
      CraftCtx.Bz e = bz.get(id);
      if (e != null) {
         return e;
      }

      Matcher m = NEU_DAMAGE.matcher(id);
      if (m.matches() && (e = bz.get(m.group(1) + ":" + m.group(2))) != null) {
         return e;
      }

      m = NEU_LEVEL.matcher(id);
      return m.matches() ? bz.get("ENCHANTMENT_" + m.group(1) + "_" + m.group(2)) : null;
   }

   // Lowest BIN; ignored for bazaar products (lowestbins lists them at insta-buy).
   static double lbin(String id, CraftCtx ctx) {
      Map<String, Double> lb = ctx.lowestBin;
      if (lb.isEmpty() || bazaarEntry(id, ctx) != null) {
         return Double.NaN;
      }

      List<String> keys = new ArrayList<>(List.of(id));
      Matcher m = NEU_LEVEL.matcher(id);
      if (m.matches()) {
         keys.add("ENCHANTED_BOOK-" + m.group(1) + "-" + m.group(2));
         int tier = m.group(2).length() < 9 ? Integer.parseInt(m.group(2)) : -1;
         if (tier >= 0 && tier < PET_TIERS.size()) {
            keys.add("PET-" + m.group(1) + "-" + PET_TIERS.get(tier));
         }
      }

      m = BOOK.matcher(id);
      if (m.matches()) {
         keys.add("ENCHANTED_BOOK-" + m.group(1) + "-" + m.group(2));
      }

      for (String k : keys) {
         Double v = lb.get(k);
         if (v != null && Double.isFinite(v) && v > 0) {
            return v;
         }
      }

      return Double.NaN;
   }

   // ---------------------------------------------------------------- recipes
   /** One output's entry of the compact recipes file: [["c"|"f"|"n"|"t"|"k", outputCount, [[id, n], ...], duration?], ...]. */
   static List<Recipe> parseBulk(JsonElement list) {
      List<Recipe> out = new ArrayList<>();
      if (!(list instanceof JsonArray a)) {
         return out;
      }

      for (JsonElement e : a) {
         try {
            if (e instanceof JsonArray r && r.size() > 2 && TYPES.containsKey(jsString(r.get(0))) && r.get(2) instanceof JsonArray ins) {
               List<Input> stacks = new ArrayList<>();

               for (JsonElement p : ins) {
                  if (p instanceof JsonArray pa && !pa.isEmpty()) {
                     JsonElement id = pa.get(0);
                     stacks.add(new Input(id.isJsonPrimitive() ? id.getAsString() : null, pa.size() > 1 ? jsNumber(pa.get(1)) : Double.NaN));
                  }
               }

               String t = jsString(r.get(0));
               Recipe rc = make(TYPES.get(t), jsNumber(r.get(1)), stacks, NOTES.get(t));
               if (!rc.inputs().isEmpty() || rc.coins() > 0) {
                  out.add(rc);
               }
            }
         } catch (RuntimeException ex) {
            // odd entry: skip it
         }
      }

      return out;
   }

   // Stacks -> Recipe: ids to NEU form, SKYBLOCK_COIN to coins, the same id summed.
   private static Recipe make(String type, double output, List<Input> stacks, String note) {
      Map<String, Double> sum = new LinkedHashMap<>();
      double coins = 0;

      for (Input s : stacks) {
         if (s.id() == null || s.id().isEmpty() || !(s.qty() > 0)) {
            continue;
         }

         String id = neuId(s.id());
         if (id.equals(COIN)) {
            coins += s.qty();
         } else {
            sum.merge(id, s.qty(), Double::sum);
         }
      }

      List<Input> inputs = new ArrayList<>();
      sum.forEach((id, q) -> inputs.add(new Input(id, q)));
      return new Recipe(type, output > 0 ? output : 1, inputs, coins, note);
   }

   // NEU 'trade' entries for enchant books (ENDER_SLAYER;6 = 50 enchanted emeralds) are not open trades: ignored for books.
   private static boolean isBook(String id, CraftCtx ctx) {
      Matcher m = NEU_LEVEL.matcher(id);
      return m.matches()
         && (ctx.bazaar.containsKey("ENCHANTMENT_" + m.group(1) + "_" + m.group(2)) || ctx.lowestBin.containsKey("ENCHANTED_BOOK-" + m.group(1) + "-" + m.group(2)));
   }

   static List<Recipe> recipesOf(String id, CraftCtx ctx) {
      List<Recipe> r = ctx.recipes.get(id);
      if (r == null) {
         r = ctx.recipes.get(neuId(id));
      }

      if (r == null) {
         return List.of();
      } else if (r.stream().anyMatch(x -> "trade".equals(x.note())) && isBook(neuId(id), ctx)) {
         return r.stream().filter(x -> !"trade".equals(x.note())).toList();
      } else {
         return r;
      }
   }

   private static String method(Recipe r) {
      return r.type().equals("forge") || r.type().equals("npc") ? r.type() : "craft";
   }

   private static List<String> plus(List<String> stack, String id) {
      List<String> out = new ArrayList<>(stack);
      out.add(id);
      return out;
   }

   // ---------------------------------------------------------------- pricing
   // Cost of one recipe per output; NaN = unusable (pseudo-currency, unpriced input, or an input already on the stack = cut).
   private static Cost recipeCost(Recipe r, List<String> stack, Pricer price) {
      if (!(r.coins() >= 0) || !Double.isFinite(r.coins()) || (r.inputs().isEmpty() && r.coins() == 0)) {
         return new Cost(Double.NaN, false);
      }

      double total = r.coins();
      boolean cut = false;

      for (Input in : r.inputs()) {
         if (!Double.isFinite(in.qty()) || in.id().startsWith("SKYBLOCK_")) {
            return new Cost(Double.NaN, cut);
         } else if (stack.contains(in.id())) {
            return new Cost(Double.NaN, true);
         }

         Px c = price.price(in.id(), stack);
         cut |= c.cut();
         if (Double.isNaN(c.unit())) {
            return new Cost(Double.NaN, cut);
         }

         total += c.unit() * in.qty();
      }

      return new Cost(total / (r.output() > 0 ? r.output() : 1), cut);
   }

   // strict: an earlier candidate wins ties (market before recipes)
   private static Px better(Px best, Px c) {
      return !Double.isNaN(c.unit()) && (best == null || c.unit() < best.unit()) ? c : best;
   }

   // From scratch: cheapest of buy order / lowest BIN / any recipe, recursively. Results not touched by a cut are memoized.
   static Px scratch(String id, List<String> stack, CraftCtx ctx) {
      if (id.equals(COIN)) {
         return new Px(1, "coins");
      }

      Px memo = ctx.scratchMemo.get(id);
      if (memo != null) {
         return memo;
      }

      Px best = null;
      boolean cut = false;
      CraftCtx.Bz b = bazaarEntry(id, ctx);
      if (b != null && !Double.isNaN(b.buyOrder()) && !(!Double.isNaN(b.instaBuy()) && b.buyOrder() < b.instaBuy() * DEAD_BO)) {
         best = better(best, new Px(b.buyOrder(), "buyOrder"));
      } else if (b != null && !Double.isNaN(b.instaBuy())) {
         best = better(best, new Px(b.instaBuy(), "instaBuy"));
      }

      best = better(best, new Px(lbin(id, ctx), "lowestBin"));
      if (id.startsWith("SKYBLOCK_")) {
         // pseudo-currency: no price
      } else if (stack.size() >= MAX_STACK) {
         cut = true;
      } else {
         List<String> st = plus(stack, id);

         for (Recipe r : recipesOf(id, ctx)) {
            Cost rc = recipeCost(r, st, (x, s) -> scratch(x, s, ctx));
            cut |= rc.cut();
            best = better(best, new Px(rc.unit(), method(r), r, false, false));
         }
      }

      Px res = best != null ? new Px(best.unit(), best.method(), best.recipe(), cut, false) : new Px(Double.NaN, "unavailable", null, cut, false);
      if (!cut) {
         ctx.scratchMemo.put(id, res);
      }

      return res;
   }

   // Easy: insta-buy / lowest BIN / coins-only NPC shop; else the from-scratch cost, flagged. stack = ids crafted above
   // this one (the root), so the fallback never loops back through them.
   static Px easy(String id, CraftCtx ctx, List<String> stack) {
      if (id.equals(COIN)) {
         return new Px(1, "coins");
      }

      Px best = null;
      CraftCtx.Bz b = bazaarEntry(id, ctx);
      if (b != null) {
         best = better(best, new Px(b.instaBuy(), "instaBuy"));
      }

      best = better(best, new Px(lbin(id, ctx), "lowestBin"));

      for (Recipe r : recipesOf(id, ctx)) {
         if (r.type().equals("npc") && r.inputs().isEmpty() && r.coins() > 0) {
            best = better(best, new Px(r.coins() / r.output(), "npc", r, false, false));
         }
      }

      if (best != null) {
         return best;
      }

      Px s = scratch(id, stack, ctx);
      return Double.isNaN(s.unit()) ? Px.NONE : new Px(s.unit(), s.method(), s.recipe(), s.cut(), true);
   }

   /** Unit price of any id form; unit NaN = unavailable. */
   static Px priceItem(String id, boolean easy, CraftCtx ctx) {
      String nid = neuId(id);
      if (nid.isEmpty()) {
         return Px.NONE;
      }

      Px r = easy ? easy(nid, ctx, List.of()) : scratch(nid, List.of(), ctx);
      return Double.isFinite(r.unit()) ? r : Px.NONE;
   }

   // ---------------------------------------------------------------- trees
   // craftTree(root, mode) reduced to what a listing needs: the root is always crafted through its cheapest recipe;
   // returns its total (NaN = can't be priced) and adds every non-root node that came out unavailable to missing.
   private static double tree(String root, boolean easy, CraftCtx ctx, Set<String> missing) {
      List<String> rs = List.of(root);
      Pricer price = easy ? (x, s) -> easy(x, ctx, rs) : (x, s) -> scratch(x, s, ctx);
      Recipe pick = null;
      Recipe first = null;
      double best = Double.NaN;

      for (Recipe r : recipesOf(root, ctx)) {
         Cost rc = recipeCost(r, rs, price);
         first = first == null ? r : first;
         if (!Double.isNaN(rc.unit()) && (pick == null || rc.unit() < best)) {
            pick = r;
            best = rc.unit();
         }
      }

      pick = pick == null ? first : pick;   // first = unpriceable, its failed leaves still count as missing
      if (pick == null) {
         return Double.NaN;
      }

      int[] nodes = {0};

      for (Input in : pick.inputs()) {
         String x = in.id();
         if (!easy) {
            scratchTree(x, rs, ctx, nodes, missing);
         } else if (x.startsWith("SKYBLOCK_") || x.equals(root)) {
            missing.add(x);
         } else {
            Px e = easy(x, ctx, rs);
            if (e.fallback()) {
               scratchTree(x, rs, ctx, nodes, missing);   // not buyable: its from-scratch subtree
            } else if (!Double.isFinite(e.unit())) {
               missing.add(x);
            }
         }
      }

      return Double.isFinite(best) ? best : Double.NaN;
   }

   private static void scratchTree(String id, List<String> stack, CraftCtx ctx, int[] nodes, Set<String> missing) {
      if (id.startsWith("SKYBLOCK_") || stack.contains(id)) {
         missing.add(id);
         return;
      }

      Px r = scratch(id, stack, ctx);
      if (!Double.isFinite(r.unit())) {
         missing.add(id);
      }

      if (r.recipe() != null && !Double.isNaN(r.unit()) && nodes[0]++ <= 2000) {
         List<String> st = plus(stack, id);

         for (Input in : r.recipe().inputs()) {
            scratchTree(in.id(), st, ctx, nodes, missing);
         }
      }
   }

   /** cleanCraftCost: tier = pet tier for PET_* tags. Not craftable = no recipe at all (totals NaN). */
   static Clean clean(String tag, String tier, CraftCtx ctx) {
      String root = recipeId(tag, tier);
      if (ctx.lowestBinFailed && ctx.binFallback != null) {
         fillBins(ctx, ctx.treeIds(root));
      }

      Set<String> missing = new LinkedHashSet<>();
      if (recipesOf(root, ctx).isEmpty()) {
         return new Clean(false, Double.NaN, Double.NaN, missing);
      }

      double s = tree(root, false, ctx, missing);
      double e = tree(root, true, ctx, missing);
      return new Clean(true, s, e, missing);
   }

   // lowestbins down: per-id BIN for up to 20 plain non-bazaar ids of the tree, each asked once (a miss is stored as 0).
   private static void fillBins(CraftCtx ctx, List<String> ids) {
      int asked = 0;
      boolean changed = false;

      for (String id : ids) {
         if (asked >= 20) {
            break;
         } else if (!PLAIN.matcher(id).matches() || id.startsWith("SKYBLOCK_") || bazaarEntry(id, ctx) != null || ctx.lowestBin.containsKey(id)) {
            continue;
         }

         asked++;

         try {
            Double v = ctx.binFallback.apply(id);
            ctx.lowestBin.put(id, v != null && Double.isFinite(v) && v > 0 ? v : 0.0);
            changed = true;
         } catch (RuntimeException e) {
            // not recorded: asked again next time
         }
      }

      if (changed) {
         ctx.scratchMemo.clear();
      }
   }
}
