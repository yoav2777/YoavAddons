// Test-suite reference: pure parse of ONE Coflnet listing row into modifier parts (craft-spec.md section 5).
// Written independently of craftModifiers.js; used by record.mjs (to know which ids to keep) and reference.mjs.
// hy = Hypixel items API entry for the tag (upgrade_costs, gemstone_slots, dungeon_item_conversion_cost, tier) or {}.
// stones = NEU reforgestones.json (trimmed), tableMax = enchants.json max_xp_table_levels.

export const MASTER = ["FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"];
export const QUAL = ["ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT"];
export const ENCH_ALIAS = { ultimate_duplex: "ULTIMATE_REITERATE", dragon_tracer: "AIMING", turbo_cocoa: "TURBO_COCO", turbo_cacti: "TURBO_CACTUS" };
export const USE_LEVELED = new Set(["champion", "compact", "cultivating", "expertise", "hecatomb", "toxophilite"]);
const COUNTED = { art_of_war_count: "THE_ART_OF_WAR", wood_singularity_count: "WOOD_SINGULARITY", farming_for_dummies_count: "FARMING_FOR_DUMMIES",
  tuned_transmission: "TRANSMISSION_TUNER", polarvoid: "POLARVOID_BOOK", bookworm_books: "BOOKWORM_BOOK", jalapeno_count: "JALAPENO_BOOK",
  mana_disintegrator_count: "MANA_DISINTEGRATOR" };
const FLAGS = { artOfPeaceApplied: "THE_ART_OF_PEACE", stats_book: "BOOK_OF_STATS", divan_powder_coating: "DIVAN_POWDER_COATING" };
const TIERS = ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL"];

export function flat(L) { return (L && (L.flatNbt || L.flattenedNbt)) || {}; }

// -> [{ group, label, id, qty }] ; enchant lines carry { enchant: {name, lvl, type} } instead of a fixed id;
//    coin lines have id 'SKYBLOCK_COIN'
export function parseListing(L, hy = {}, stones = {}) {
  const f = flat(L), out = [];
  const add = (group, label, id, qty, extra) => out.push({ group, label, id, qty, ...extra });
  for (const e of (L && L.enchantments) || []) {
    const t = String(e.type).toLowerCase(), name = ENCH_ALIAS[t] || t.toUpperCase();
    add("enchant", t + " " + e.level, "ENCHANTMENT_" + name + "_" + e.level, 1, { enchant: { type: t, name, lvl: Number(e.level) } });
  }
  const stars = Number(f.upgrade_level ?? f.dungeon_item_level ?? 0) || 0;
  const uc = hy.upgrade_costs || [];
  const ess = {}, items = {};
  let coins = 0;
  for (let i = 0; i < Math.min(stars, uc.length); i++) for (const c of uc[i]) {
    if (c.type === "ESSENCE") ess[c.essence_type] = (ess[c.essence_type] || 0) + c.amount;
    else if (c.type === "ITEM") items[c.item_id] = (items[c.item_id] || 0) + c.amount;
    else if (c.type === "COINS") coins += c.coins;
  }
  if (f.dungeon_item === "1" && hy.dungeon_item_conversion_cost) { const c = hy.dungeon_item_conversion_cost; ess[c.essence_type] = (ess[c.essence_type] || 0) + c.amount; }
  for (const k in ess) add("stars", "essence " + k, "ESSENCE_" + k, ess[k]);
  for (const k in items) add("stars", "star items", k, items[k]);
  if (coins) add("stars", "star coins", "SKYBLOCK_COIN", coins);
  const master = uc.length === 5 && stars > 5 ? Math.min(stars - 5, 5) : 0;
  for (let i = 0; i < master; i++) add("stars", "master star " + (i + 1), MASTER[i], 1);
  if (Number(f.rarity_upgrades) > 0) add("recomb", "recomb", "RECOMBOBULATOR_3000", 1);
  const hpc = Number(f.hpc ?? f.hot_potato_count ?? 0) || 0;
  if (hpc > 0) add("hpb", "hpb", "HOT_POTATO_BOOK", Math.min(hpc, 10));
  if (hpc > 10) add("hpb", "fuming", "FUMING_POTATO_BOOK", hpc - 10);
  for (const k in COUNTED) if (Number(f[k]) > 0) add("upgrade", k, COUNTED[k], Number(f[k]));
  for (const k in FLAGS) if (f[k] != null && f[k] !== "0") add("upgrade", k, FLAGS[k], 1);
  if (f.ethermerge != null && f.ethermerge !== "0") { add("upgrade", "ethermerge", "ETHERWARP_MERGER", 1); add("upgrade", "ethermerge", "ETHERWARP_CONDUIT", 1); }
  if (f.ability_scroll) for (const s of String(f.ability_scroll).split(/[ ,]+/).filter(Boolean)) add("scroll", s, s, 1);
  if (f.power_ability_scroll) add("scroll", "power scroll", f.power_ability_scroll, 1);
  const slots = hy.gemstone_slots || [];
  for (const s of String(f.unlocked_slots || "").split(",").filter(Boolean)) {
    const m = s.match(/^([A-Z]+)_(\d+)$/); if (!m) continue;
    const def = slots.filter(x => x.slot_type === m[1])[+m[2]];
    for (const c of (def && def.costs) || []) {
      if (c.type === "COINS") add("gemslot", s + " coins", "SKYBLOCK_COIN", c.coins);
      else add("gemslot", s, c.item_id, c.amount);
    }
  }
  for (const k of Object.keys(f)) {
    const q = String(f[k]).toUpperCase();
    if (!/^[A-Z]+_\d+$/.test(k) || !QUAL.includes(q)) continue;
    const type = String(f[k + "_gem"] || k.split("_")[0]).toUpperCase();
    add("gem", k, q + "_" + type + "_GEM", 1);
  }
  if (f.heldItem) add("pet", "held item", f.heldItem, 1);
  for (const k of ["drill_part_engine", "drill_part_fuel_tank", "drill_part_upgrade_module"]) if (f[k]) add("drill", k, String(f[k]).toUpperCase(), 1);
  if (f.dye_item) add("dye", "dye", f.dye_item, 1);
  for (const k of Object.keys(f)) { const m = k.match(/^RUNE_(.+)$/); if (m) add("rune", k, "RUNE-" + m[1] + "-" + f[k], 1, { lbKey: true }); }
  const rf = L && L.reforge && L.reforge !== "None" ? String(L.reforge).toLowerCase() : null;
  if (rf) {
    const stone = Object.keys(stones).find(k => String(stones[k].reforgeName).toLowerCase() === rf);
    const tier = L.tier && L.tier !== "UNKNOWN" ? L.tier : hy.tier ? TIERS[TIERS.indexOf(hy.tier) + (Number(f.rarity_upgrades) > 0 ? 1 : 0)] : null;
    if (stone) {
      add("reforge", "stone", stone, 1);
      const apply = tier && stones[stone].reforgeCosts ? stones[stone].reforgeCosts[tier] : null;
      if (apply != null) add("reforge", "apply", "SKYBLOCK_COIN", apply);
    }
  }
  return out;
}
