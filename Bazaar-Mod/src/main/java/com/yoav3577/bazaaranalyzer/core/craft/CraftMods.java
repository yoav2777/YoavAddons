package com.yoav3577.bazaaranalyzer.core.craft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The modifiers of one item (enchants, stars, books, gems, reforge...) and their price in both modes. Port of the
 * site's craft/craftModifiers.js (rules: notes/craft-modifiers.md, craft-spec.md section 5) for raw SkyBlock NBT.
 */
final class CraftMods {
   private static final List<String> TIERS = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL");
   private static final String[] MASTER = {"FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"};
   private static final Set<String> QUAL = Set.of("ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT");
   // NBT enchant name -> bazaar name (Duplex was renamed Reiterate)
   private static final Map<String, String> ALIAS = Map.of(
      "ultimate_duplex", "ULTIMATE_REITERATE", "dragon_tracer", "AIMING", "turbo_cocoa", "TURBO_COCO", "turbo_cacti", "TURBO_CACTUS"
   );
   // level grows by use: only the level-1 book is bought
   private static final Set<String> USE_LEVELED = Set.of("champion", "compact", "cultivating", "expertise", "hecatomb", "toxophilite");
   // NBT key -> item; value = how many were applied
   private static final Map<String, String> COUNTED = ordered(
      "art_of_war_count", "THE_ART_OF_WAR", "wood_singularity_count", "WOOD_SINGULARITY", "farming_for_dummies_count", "FARMING_FOR_DUMMIES",
      "tuned_transmission", "TRANSMISSION_TUNER", "polarvoid", "POLARVOID_BOOK", "bookworm_books", "BOOKWORM_BOOK",
      "jalapeno_count", "JALAPENO_BOOK", "mana_disintegrator_count", "MANA_DISINTEGRATOR"
   );
   // NBT key set (flag or counter, e.g. stats_book = kills) -> 1 item
   private static final Map<String, String> FLAGS = ordered(
      "artOfPeaceApplied", "THE_ART_OF_PEACE", "stats_book", "BOOK_OF_STATS", "divan_powder_coating", "DIVAN_POWDER_COATING"
   );
   private static final String[][] DRILL = {
      {"drill_part_engine", "engine.id"}, {"drill_part_fuel_tank", "fuel_tank.id"}, {"drill_part_upgrade_module", "upgrade_module.id"}
   };
   private static final Set<String> MARKET = Set.of("buyOrder", "instaBuy", "lowestBin");
   private static final Pattern SLOT = Pattern.compile("^([A-Z]+)_(\\d+)$");
   private static final Pattern RUNE = Pattern.compile("^RUNE_(.+)$");
   private static final Pattern SPLIT = Pattern.compile("[\\s,]+");

   private record Stone(String item, Double[] costs) {
   }

   // reforge name (lowercase letters only) -> stone + apply cost COMMON..DIVINE ("-" = none). Not listed = blacksmith
   // reforge (free). From NEU constants/reforgestones.json (repo commit a332242), same table as the site.
   private static final Map<String, Stone> STONES = new LinkedHashMap<>();
   // enchanting-table max level (XP only, no coins). NEU enchants.json max_xp_table_levels.
   private static final Map<String, Integer> TABLE_MAX = new HashMap<>();

   static {
      for (String line : """
         ambered AMBER_MATERIAL 20000 40000 80000 150000 300000 600000 800000
         ancient PRECURSOR_GEAR 10000 20000 30000 40000 50000 60000
         auspicious ROCK_GEMSTONE 20000 40000 80000 150000 300000 600000 600000
         beady BEADY_EYES 10000 20000 50000 75000 100000 150000
         blazing BLAZEN_SPHERE 20000 40000 80000 150000 300000 300000 300000
         blessed BLESSED_FRUIT 10000 10000 10000 10000 10000 10000
         bloodsoaked PRESUMED_GALLON_OF_RED_PAINT 7500 15000 30000 75000 150000 300000 400000
         bloodshot SHRIVELED_CORNEA 50000 100000 250000 500000 1000000 2500000
         blooming FLOWERING_BOUQUET 5000 10000 20000 50000 100000 200000
         bountiful GOLDEN_BALL 20000 40000 80000 150000 300000 600000
         bulky BULKY_STONE 20000 40000 80000 150000 300000 600000
         bustling SKYMART_BROCHURE 1000 2000 3000 6000 10000 15000
         buzzing CLIPPED_WINGS 10000 20000 50000 75000 100000 150000 250000
         calcified CALCIFIED_HEART 20000 40000 80000 150000 300000 600000 800000
         candied CANDY_CORN 20000 40000 80000 150000 300000 600000
         chomp KUUDRA_MANDIBLE 20000 40000 80000 150000 300000 600000
         coldfused ENTROPY_SUPPRESSOR 60000 125000 250000 500000 1000000 2000000
         cubic MOLTEN_CUBE 4000 7500 15000 40000 75000 150000
         deepfried HASHBROWN 20000 40000 80000 150000 300000
         dimensional TITANIUM_TESSERACT 15000 30000 60000 125000 250000 500000 500000
         dirty DIRT_BOTTLE 1000 5000 10000 15000 50000 75000
         earthy LARGE_WALNUT 5000 10000 20000 50000 100000
         empowered SADAN_BROOCH 60000 125000 250000 500000 1000000 2000000
         erudite DAEDALUS_NOTES - - 500000 1000000 2000000 2000000
         fabled DRAGON_CLAW 60000 125000 250000 500000 1000000 2000000
         fanged FULL_JAW_FANGING_KIT 10000 12500 25000 50000 100000 250000
         festive FROZEN_BAUBLE 25000 75000 150000 250000 400000 600000
         fleet DIAMONITE 15000 30000 60000 125000 250000 500000 500000
         fortified METEOR_SHARD 7500 15000 30000 75000 150000 300000 400000
         fruitful ONYX 100 250 500 1000 2500 5000 10000
         giant GIANT_TOOTH 60000 125000 250000 500000 1000000 2000000
         gilded MIDAS_JEWEL - - - - 5000000 10000000
         glacial FRIGID_HUSK 20000 40000 80000 150000 300000 600000 800000
         glistening SHINY_PRISM 7500 15000 30000 75000 150000 300000 400000
         greaterspook BOO_STONE - - - 10000 10000
         groovy MANGROVE_GEM 10000 15000 20000 30000 40000 50000 50000
         headstrong SALMON_OPAL 15000 30000 60000 125000 250000 500000
         heated HOT_STUFF 20000 40000 80000 150000 300000 600000 600000
         hyper ENDSTONE_GEODE 5000 10000 20000 50000 100000 200000
         jaded JADERALD 20000 40000 80000 150000 300000 600000
         jerrys JERRY_STONE 1 2 3
         loving RED_SCARF 30000 75000 150000 300000 600000 1200000
         lucky LUCKY_DICE 20000 40000 80000 150000 300000 600000
         lunar MOONSTONE 5000 10000 20000 50000 100000 200000 200000
         lustrous GLEAMING_CRYSTAL 20000 40000 80000 150000 300000 600000 800000
         magnetic LAPIS_CRYSTAL 250 500 1000 2500 5000 10000 15000
         majestic MORNING_DEW 5000 25000 100000 500000 1000000 2000000
         mantid MANTID_CLAW 7500 15000 30000 75000 150000 150000 150000
         marshy MARSHROOM 5000 25000 100000 250000 500000 1000000
         mithraic PURE_MITHRIL 15000 30000 60000 125000 250000 500000 500000
         moil MOIL_LOG 1000 5000 10000 15000 50000 75000
         moonglade MOONGLADE_JEWEL 10000 15000 20000 30000 40000 50000 50000
         mossy OVERGROWN_GRASS 20000 40000 80000 150000 300000 600000
         necrotic NECROMANCER_BROOCH 20000 40000 80000 150000 300000 1000000
         overpriced OVERPRICED_DRINK 20000 40000 80000 150000 300000
         perfect DIAMOND_ATOM 25000 50000 150000 300000 600000 800000
         pitchin PITCHIN_KOI 5000 20000 40000 80000 120000 280000
         precise OPTICAL_LENS 30000 75000 150000 300000 600000 1200000
         refined REFINED_AMBER 10000 10000 10000 10000 10000 10000 10000
         reinforced RARE_DIAMOND 2500 5000 10000 25000 50000 100000
         renowned DRAGON_HORN 60000 125000 250000 500000 1000000 2000000
         ridiculous RED_NOSE 7500 15000 30000 75000 150000 300000
         rooted BURROWING_SPORES 20000 40000 80000 150000 300000 600000
         royal DWARVEN_TREASURE 5000 10000 20000 50000 100000 100000 100000
         salty SALT_CUBE 2500 10000 20000 40000 80000 120000
         scraped POCKET_ICEBERG 15000 30000 60000 125000 250000 500000 500000
         snowy TERRY_SNOWGLOBE 10000 25000 50000 100000 200000 300000
         spiked DRAGON_SCALE 30000 75000 150000 300000 600000 2000000
         spiritual SPIRIT_DECOY 60000 125000 250000 500000 1000000 2000000
         squeaky SQUEAKY_TOY 7500 15000 30000 75000 150000 150000 150000
         stellar PETRIFIED_STARFALL 25000 50000 100000 200000 400000 800000 800000
         sticky EXTREMELY_MILD_ADHESIVE - 10000 25000 100000 250000 500000
         stiff HARDENED_WOOD 4000 7500 15000 40000 75000 150000
         strengthened SEARING_STONE 7500 15000 30000 75000 150000 300000 400000
         submerged DEEP_SEA_ORB 50000 150000 350000 600000 750000 800000
         sunny SUNSTONE 5000 10000 20000 50000 100000 200000 200000
         suspicious SUSPICIOUS_VIAL 60000 125000 250000 500000 1000000 2000000
         thorny BLOOMING_THORNS 20000 40000 80000 150000 300000 600000
         toil TOIL_LOG 10000 10000 10000 10000 10000 10000
         trashy OVERFLOWING_TRASH_CAN 2500 10000 20000 40000 80000 120000 500000
         treacherous RUSTY_ANCHOR 5000 20000 40000 80000 120000 280000
         undead PREMIUM_FLESH 5000 15000 30000 75000 150000 300000
         warped AOTE_STONE - - 5000000 5000000 5000000
         waxed BLAZE_WAX 7500 15000 30000 75000 150000 300000 400000
         withered WITHER_BLOOD 10000 20000 30000 40000 50000 60000
         """.strip().split("\n")) {
         String[] w = line.strip().split(" ");
         Double[] costs = new Double[w.length - 2];

         for (int i = 2; i < w.length; i++) {
            costs[i - 2] = w[i].equals("-") ? null : Double.valueOf(w[i]);
         }

         STONES.put(w[0], new Stone(w[1], costs));
      }

      for (String e : """
         sharpness:5 smite:5 bane_of_arthropods:5 looting:3 cubism:5 cleave:5 life_steal:3 giant_killer:5 critical:5
         first_strike:4 triple_strike:4 ender_slayer:5 execute:5 thunderlord:5 lethality:5 syphon:3 vampirism:5
         venomous:5 thunderbolt:5 prosecute:5 titan_killer:5 luck:5 protection:5 blast_protection:5
         projectile_protection:5 fire_protection:5 thorns:3 growth:5 frost_walker:2 feather_falling:5 depth_strider:3
         aqua_affinity:1 respiration:3 silk_touch:1 smelting_touch:1 fortune:3 experience:3 efficiency:5 harvesting:5
         piscary:5 spiked_hook:5 caster:5 frail:5 angler:5 chance:3 power:5 infinite_quiver:5 lure:5 magnet:5
         luck_of_the_sea:5 scavenger:3
         """.strip().split("\\s+")) {
         TABLE_MAX.put(e.substring(0, e.indexOf(':')), Integer.valueOf(e.substring(e.indexOf(':') + 1)));
      }
   }

   private CraftMods() {
   }

   record Part(String id, double qty) {
   }

   record Ench(String type, String name, double level) {
   }

   /**
    * One modifier. coins NaN = none, 0 = free (blacksmith reforge); enchant set only for enchant lines priced by the
    * anvil rule; label = what missing shows when a modifier without parts can't be priced.
    */
   record Mod(String kind, List<Part> parts, double coins, Ench enchant, String label) {
      Mod(String kind, List<Part> parts, double coins) {
         this(kind, parts, coins, null, null);
      }

      Mod(String kind, String id, double qty) {
         this(kind, List.of(new Part(id, qty)), Double.NaN, null, null);
      }
   }

   /** One priced line; unit / total NaN = unavailable. fallback = easy mode had to use the from-scratch cost. */
   record Line(String kind, String label, String id, double qty, double unit, double total, String method, boolean fallback) {
   }

   // ---------------------------------------------------------------- JS value helpers
   private static boolean has(Map<String, JsonElement> f, String k) {
      JsonElement v = f.get(k);
      return v != null && !v.isJsonNull();
   }

   // JS truthiness
   private static boolean truthy(JsonElement v) {
      if (v == null || v.isJsonNull()) {
         return false;
      } else if (!v.isJsonPrimitive()) {
         return true;
      }

      JsonPrimitive p = v.getAsJsonPrimitive();
      return p.isBoolean() ? p.getAsBoolean() : p.isNumber() ? p.getAsDouble() != 0 && !Double.isNaN(p.getAsDouble()) : !p.getAsString().isEmpty();
   }

   // cmOn: set and not 0 / false / none
   private static boolean on(JsonElement v) {
      if (v == null || v.isJsonNull()) {
         return false;
      } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
         return v.getAsBoolean();
      } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
         return v.getAsDouble() != 0;
      }

      String s = CraftCore.jsString(v);
      return !(v.isJsonPrimitive() && s.isEmpty()) && !s.matches("(?i)0|false|none");
   }

   // cmNum: JS Number(v), 0 when not finite
   private static double num(JsonElement v) {
      double n = CraftCore.jsNumber(v);
      return Double.isFinite(n) ? n : 0;
   }

   private static String upper(JsonElement v) {
      return CraftCore.jsString(v).toUpperCase(Locale.ROOT);
   }

   private static String norm(String s) {
      return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
   }

   private static Map<String, String> ordered(String... kv) {
      Map<String, String> m = new LinkedHashMap<>();

      for (int i = 0; i < kv.length; i += 2) {
         m.put(kv[i], kv[i + 1]);
      }

      return m;
   }

   // Raw NBT -> flat keys: gems{} -> slot keys (arrays comma-joined, {quality,uuid} -> quality), runes{} -> RUNE_*,
   // petInfo (JSON string or object) flattened, other arrays space-joined, other objects dropped.
   static Map<String, JsonElement> flat(JsonObject raw) {
      Map<String, JsonElement> o = new LinkedHashMap<>();
      if (raw == null) {
         return o;
      }

      for (Map.Entry<String, JsonElement> e : raw.entrySet()) {
         String k = e.getKey();
         JsonElement v = e.getValue();
         if (k.equals("gems") && v instanceof JsonObject gems) {
            for (Map.Entry<String, JsonElement> g : gems.entrySet()) {
               JsonElement x = g.getValue();
               o.put(g.getKey(), x instanceof JsonArray a ? new JsonPrimitive(CraftCore.join(a, ",")) : x instanceof JsonObject q ? q.get("quality") : x);
            }
         } else if (k.equals("runes") && v instanceof JsonObject runes) {
            for (Map.Entry<String, JsonElement> r : runes.entrySet()) {
               o.put("RUNE_" + r.getKey(), r.getValue());
            }
         } else if (k.equals("petInfo")) {
            JsonElement p = v;
            if (p.isJsonPrimitive() && p.getAsJsonPrimitive().isString()) {
               try {
                  p = JsonParser.parseString(p.getAsString());
               } catch (RuntimeException ex) {
                  p = null;
               }
            }

            if (p instanceof JsonObject pet) {
               for (Map.Entry<String, JsonElement> pe : pet.entrySet()) {
                  if (pe.getValue().isJsonNull() || pe.getValue().isJsonPrimitive()) {
                     o.put(pe.getKey(), pe.getValue());
                  }
               }
            }
         } else if (v instanceof JsonArray a) {
            o.put(k, new JsonPrimitive(CraftCore.join(a, " ")));
         } else if (v.isJsonNull() || v.isJsonPrimitive()) {
            o.put(k, v);
         }
      }

      return o;
   }

   static boolean isPet(String tag, Map<String, JsonElement> f) {
      return tag.startsWith("PET_") && (has(f, "type") || has(f, "exp") || has(f, "candyUsed"));
   }

   /** The pet's tier from petInfo, or null. */
   static String petTier(Map<String, JsonElement> f) {
      String t = truthy(f.get("tier")) ? upper(f.get("tier")) : "";
      return CraftCore.PET_TIERS.contains(t) ? t : null;
   }

   // Rarity for the reforge apply cost: items API tier, +1 when recombobulated.
   private static String itemTier(Map<String, JsonElement> f, CraftCtx.HyItem hy) {
      int i = hy != null && hy.tier() != null ? TIERS.indexOf(hy.tier()) : -1;
      return i < 0 ? null : TIERS.get(Math.min(i + (num(f.get("rarity_upgrades")) > 0 ? 1 : 0), TIERS.size() - 1));
   }

   // ---------------------------------------------------------------- parse
   static List<Mod> list(String tag, JsonObject nbt, CraftCtx ctx) {
      List<Mod> out = new ArrayList<>();
      Map<String, JsonElement> f = flat(nbt);
      String hyTag = !tag.isEmpty() ? tag : truthy(f.get("id")) ? upper(f.get("id")) : "";
      CraftCtx.HyItem hy = ctx.items.get(hyTag);
      boolean pet = isPet(hyTag, f);

      // enchantments
      if (nbt != null && nbt.get("enchantments") instanceof JsonObject en) {
         for (Map.Entry<String, JsonElement> e : en.entrySet()) {
            String type = e.getKey().toLowerCase(Locale.ROOT);
            double level = num(e.getValue());
            String name = ALIAS.getOrDefault(type, type.toUpperCase(Locale.ROOT));
            if (!(level > 0)) {
               continue;
            } else if (USE_LEVELED.contains(type) && level > 1) {
               out.add(new Mod("enchant", "ENCHANTMENT_" + name + "_1", 1));
            } else if (type.equals("efficiency") && level > 5) {
               out.add(new Mod("enchant", "SIL_EX", level - 5));   // levels 6+ need one Silex each; 1-5 from the table
            } else {
               out.add(new Mod("enchant", List.of(new Part("ENCHANTMENT_" + name + "_" + CraftCore.jsNum(level), 1)), Double.NaN, new Ench(type, name, level), null));
            }
         }
      }

      // stars: essence (+ items/coins) per star from the items API, master stars 6-10; dungeon conversion essence merged in
      JsonObject cc = on(f.get("dungeon_item")) && hy != null ? hy.conversion() : null;
      Part conv = cc != null && truthy(cc.get("essence_type")) ? new Part("ESSENCE_" + CraftCore.jsString(cc.get("essence_type")), num(cc.get("amount"))) : null;
      double stars = has(f, "upgrade_level") ? num(f.get("upgrade_level")) : num(f.get("dungeon_item_level"));
      if (stars > 0) {
         JsonArray uc = hy != null ? hy.upgradeCosts() : null;
         if (uc == null || uc.isEmpty()) {
            out.add(new Mod("stars", List.of(), Double.NaN, null, CraftCore.jsNum(stars) + " stars"));
         } else {
            Map<String, Double> tot = new LinkedHashMap<>();
            double coins = 0;

            for (int i = 0; i < Math.min(stars, uc.size()); i++) {
               if (!(uc.get(i) instanceof JsonArray step)) {
                  continue;
               }

               for (JsonElement ce : step) {
                  if (!(ce instanceof JsonObject c)) {
                     continue;
                  }

                  String type = CraftCore.jsString(c.get("type"));
                  if (type.equals("ESSENCE") && truthy(c.get("essence_type"))) {
                     tot.merge("ESSENCE_" + CraftCore.jsString(c.get("essence_type")), num(c.get("amount")), Double::sum);
                  } else if (type.equals("ITEM") && truthy(c.get("item_id"))) {
                     tot.merge(CraftCore.jsString(c.get("item_id")), num(c.get("amount")), Double::sum);
                  } else if (type.equals("COINS")) {
                     coins += num(c.get("coins"));
                  }
               }
            }

            if (conv != null) {
               tot.merge(conv.id(), conv.qty(), Double::sum);
               conv = null;
            }

            List<Part> parts = new ArrayList<>();
            tot.forEach((id, q) -> parts.add(new Part(id, q)));
            out.add(new Mod("stars", parts, coins != 0 ? coins : Double.NaN, null, "Stars 1-" + CraftCore.jsNum(Math.min(stars, uc.size()))));
            if (stars > uc.size()) {
               if (uc.size() == 5) {
                  for (int i = 0; i < Math.min(stars - 5, 5); i++) {
                     out.add(new Mod("masterStar", MASTER[i], 1));
                  }
               } else {
                  out.add(new Mod("stars", List.of(), Double.NaN, null, "Stars " + (uc.size() + 1) + "-" + CraftCore.jsNum(stars)));
               }
            }
         }
      }

      if (conv != null) {
         out.add(new Mod("dungeonize", List.of(conv), Double.NaN));
      }

      if (num(f.get("rarity_upgrades")) > 0) {
         out.add(new Mod("recomb", "RECOMBOBULATOR_3000", 1));
      }

      double hpc = num(has(f, "hpc") ? f.get("hpc") : f.get("hot_potato_count"));
      if (hpc > 0) {
         out.add(new Mod("potatoBook", "HOT_POTATO_BOOK", Math.min(hpc, 10)));
      }

      if (hpc > 10) {
         out.add(new Mod("fumingBook", "FUMING_POTATO_BOOK", Math.min(hpc - 10, 5)));
      }

      COUNTED.forEach((k, id) -> {
         double n = num(f.get(k));
         if (n > 0) {
            out.add(new Mod("upgrade", id, n));
         }
      });
      FLAGS.forEach((k, id) -> {
         if (on(f.get(k))) {
            out.add(new Mod("upgrade", id, 1));
         }
      });
      if (on(f.get("ethermerge"))) {
         out.add(new Mod("upgrade", List.of(new Part("ETHERWARP_MERGER", 1), new Part("ETHERWARP_CONDUIT", 1)), Double.NaN));
      }

      if (truthy(f.get("ability_scroll"))) {
         for (String s : SPLIT.split(CraftCore.jsString(f.get("ability_scroll")))) {
            if (!s.isEmpty()) {
               out.add(new Mod("abilityScroll", s.toUpperCase(Locale.ROOT), 1));
            }
         }
      }

      if (on(f.get("power_ability_scroll"))) {
         out.add(new Mod("powerScroll", upper(f.get("power_ability_scroll")), 1));
      }

      // gem slot unlocks: the N-th slot of that type in the items API (slots without costs are free)
      // a gem can only sit in an unlocked slot: Hypixel often leaves those out of unlocked_slots (39% of sales with gems)
      Set<String> unlocked = new LinkedHashSet<>();
      for (String s : SPLIT.split(truthy(f.get("unlocked_slots")) ? CraftCore.jsString(f.get("unlocked_slots")) : "")) {
         unlocked.add(s);
      }

      f.forEach((k, v) -> {
         if (SLOT.matcher(k).matches() && truthy(v) && QUAL.contains(upper(v))) {
            unlocked.add(k);
         }
      });
      for (String s : unlocked) {
         Matcher m = SLOT.matcher(s);
         if (!m.matches()) {
            continue;
         } else if (hy == null) {
            out.add(new Mod("gemSlot", List.of(), Double.NaN, null, "Unlock " + s));
            continue;
         }

         JsonObject def = null;
         int n = m.group(2).length() < 9 ? Integer.parseInt(m.group(2)) : Integer.MAX_VALUE;

         for (JsonElement x : hy.gemSlots() != null ? hy.gemSlots() : new JsonArray()) {
            if (x instanceof JsonObject so && CraftCore.jsString(so.get("slot_type")).equals(m.group(1)) && n-- == 0) {
               def = so;
               break;
            }
         }

         if (def == null) {
            out.add(new Mod("gemSlot", List.of(), Double.NaN, null, "Unlock " + s));
            continue;
         }

         if (!(def.get("costs") instanceof JsonArray costs) || costs.isEmpty()) {
            continue;
         }

         double coins = 0;
         List<Part> parts = new ArrayList<>();

         for (JsonElement ce : costs) {
            if (ce instanceof JsonObject c && CraftCore.jsString(c.get("type")).equals("COINS")) {
               coins += num(c.get("coins"));
            } else if (ce instanceof JsonObject c && truthy(c.get("item_id"))) {
               double q = num(c.get("amount"));
               parts.add(new Part(CraftCore.jsString(c.get("item_id")), q != 0 ? q : 1));
            }
         }

         out.add(new Mod("gemSlot", parts, coins != 0 ? coins : Double.NaN, null, "Unlock " + s));
      }

      // gems: SLOT_N = quality, SLOT_N_gem = type for universal slots
      for (String k : f.keySet()) {
         if (!SLOT.matcher(k).matches()) {
            continue;
         }

         String q = upper(f.get(k));
         if (QUAL.contains(q)) {
            String type = truthy(f.get(k + "_gem")) ? upper(f.get(k + "_gem")) : k.split("_")[0].toUpperCase(Locale.ROOT);
            out.add(new Mod("gem", q + "_" + type + "_GEM", 1));
         }
      }

      // reforge: stone + apply cost by rarity; a name that isn't a stone reforge is a free blacksmith reforge
      JsonElement rfe = f.get("modifier");
      if (!pet && truthy(rfe) && !CraftCore.jsString(rfe).matches("(?i)none")) {
         String rf = norm(CraftCore.jsString(rfe));
         Stone st = STONES.get(rf);
         if (st == null) {   // Coflnet sometimes sends the stone's name ("aote_stone" = Warped)
            st = STONES.values().stream().filter(x -> norm(x.item()).equals(rf)).findFirst().orElse(null);
         }

         if (st != null) {
            String tier = itemTier(f, hy);
            int ti = tier != null ? TIERS.indexOf(tier) : -1;
            Double cost = ti >= 0 && ti < st.costs().length ? st.costs()[ti] : null;
            out.add(new Mod("reforge", List.of(new Part(st.item(), 1)), cost != null ? cost : Double.NaN));
         } else {
            out.add(new Mod("reforge", List.of(), 0));
         }
      }

      for (String[] d : DRILL) {
         JsonElement v = truthy(f.get(d[0])) ? f.get(d[0]) : f.get(d[1]);
         if (truthy(v)) {
            out.add(new Mod("drillPart", upper(v), 1));
         }
      }

      if (on(f.get("dye_item"))) {
         out.add(new Mod("dye", upper(f.get("dye_item")), 1));
      }

      for (String k : f.keySet()) {
         Matcher m = RUNE.matcher(k);
         double lvl = m.matches() ? num(f.get(k)) : 0;
         if (lvl > 0) {
            out.add(new Mod("rune", "RUNE-" + m.group(1).toUpperCase(Locale.ROOT) + "-" + CraftCore.jsNum(lvl), 1));
         }
      }

      if (pet) {
         if (on(f.get("heldItem"))) {
            out.add(new Mod("petItem", upper(f.get("heldItem")), 1));
         }

         if (on(f.get("skin"))) {
            out.add(new Mod("petSkin", "PET_SKIN_" + upper(f.get("skin")), 1));
         }
      } else if (on(f.get("skin"))) {
         out.add(new Mod("skin", upper(f.get("skin")), 1));   // skin id guessed from NBT
      }

      return out;
   }

   // ---------------------------------------------------------------- price
   private static Line price(Mod m, String id, double qty, boolean easy, CraftCtx ctx) {
      CraftCore.Px r = CraftCore.priceItem(id, easy, ctx);
      double unit = r.unit();
      return new Line(m.kind(), m.label(), id, qty, unit, unit * qty, Double.isNaN(unit) ? "unavailable" : r.method(), r.fallback());
   }

   private static boolean bought(Line l) {
      return !Double.isNaN(l.unit()) && !l.fallback();
   }

   // Can level L be made by anvil-combining two L-1 books? Ultimates: any level. Others: only one level above the
   // enchanting-table max and never above V. Enchants the table can't roll are never combined.
   private static boolean canAnvil(String type, String name, double level) {
      if (!(level > 1)) {
         return false;
      } else if (name.startsWith("ULTIMATE_")) {
         return true;
      }

      Integer t = TABLE_MAX.get(type);
      return t != null && t > 0 && level <= Math.min(5, t + 1);
   }

   // One enchant. fromScratch: cheaper of the book and 2^(L-l) copies of the highest lower level with a MARKET price.
   // easy: the book when it can be bought; only when it can't, the cheaper of an anvil from buyable books and the
   // book's from-scratch fallback. Nothing priced: 0 (enchanting table, or no market).
   private static Line enchantLine(Mod m, boolean easy, CraftCtx ctx) {
      Ench e = m.enchant();
      Line own = price(m, "ENCHANTMENT_" + e.name() + "_" + CraftCore.jsNum(e.level()), 1, easy, ctx);
      if (easy && bought(own)) {
         return own;
      }

      Line best = Double.isNaN(own.unit()) ? null : own;
      String up = CraftEngine.UPGRADE_ITEM.get(e.name() + "_" + CraftCore.jsNum(e.level()));
      if (up != null) {
         Line below = price(m, "ENCHANTMENT_" + e.name() + "_" + CraftCore.jsNum(e.level() - 1), 1, easy, ctx);
         Line item = price(m, up, 1, easy, ctx);
         double sum = below.unit() + item.unit();
         if (!Double.isNaN(sum) && (best == null || sum < best.total())) {
            best = new Line(m.kind(), m.label(), own.id(), 1, sum, sum, "upgrade", below.fallback() || item.fallback());
         }
      }

      if (canAnvil(e.type(), e.name(), e.level())) {
         for (double l = e.level() - 1; l >= 1; l--) {
            Line b = price(m, "ENCHANTMENT_" + e.name() + "_" + CraftCore.jsNum(l), 1, easy, ctx);
            if (Double.isNaN(b.unit()) || !MARKET.contains(b.method()) || (easy && !bought(b))) {
               continue;
            }

            double n = Math.pow(2, e.level() - l);
            if (best == null || b.unit() * n < best.total()) {
               best = new Line(m.kind(), m.label(), b.id(), n, b.unit(), b.unit() * n, b.method() + "+anvil", b.fallback());
            }

            break;
         }
      }

      if (best != null) {
         return best;
      }

      Integer tmax = TABLE_MAX.get(e.type());
      return new Line(m.kind(), m.label(), own.id(), 1, 0, 0, tmax != null && e.level() <= tmax ? "table" : "noMarket", false);
   }

   /** Priced lines of the modifiers (one per part, plus a coins line). */
   static List<Line> price(List<Mod> mods, boolean easy, CraftCtx ctx) {
      List<Line> lines = new ArrayList<>();

      for (Mod m : mods) {
         if (m.enchant() != null) {
            lines.add(enchantLine(m, easy, ctx));
         } else if (m.parts().isEmpty() && Double.isNaN(m.coins())) {
            lines.add(new Line(m.kind(), m.label(), null, 0, Double.NaN, Double.NaN, "unavailable", false));
         } else if (!m.parts().isEmpty() || m.coins() != 0) {
            for (Part p : m.parts()) {
               lines.add(price(m, p.id(), p.qty(), easy, ctx));
            }

            if (!Double.isNaN(m.coins())) {
               lines.add(new Line(m.kind(), m.label(), CraftCore.COIN, m.coins(), 1, m.coins(), "coins", false));
            }
         }
      }

      return lines;
   }
}
