// Reference implementation of the listing-modifier rules in craft-spec.md section 5 (node, dev only, NOT shipped).
// Usage: node ref-listing.js samples/listing-hyperion-full.json   (a Coflnet active/bin or sold row, or an array of them -> first)
"use strict";
const fs = require("fs");
const { craftCost, scratch, easyUnit, bz, lbin } = require("./ref-craft.js");
const HY = {}; for (const i of JSON.parse(fs.readFileSync(__dirname + "/cache/hyitems.json")).items) HY[i.id] = i;
const RS = JSON.parse(fs.readFileSync(__dirname + "/cache/neurepo/repo/constants/reforgestones.json"));
const EN = JSON.parse(fs.readFileSync(__dirname + "/cache/neurepo/repo/constants/enchants.json"));

const MASTER = ["FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"];
const QUAL = ["ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT"];
const ENCH_ALIAS = { ultimate_duplex: "ULTIMATE_REITERATE", dragon_tracer: "AIMING", turbo_cocoa: "TURBO_COCO", turbo_cacti: "TURBO_CACTUS" };
const USE_LEVELED = new Set(["champion", "compact", "cultivating", "expertise", "hecatomb", "toxophilite"]); // level grows by use -> price level 1
const COUNTED = { art_of_war_count: "THE_ART_OF_WAR", wood_singularity_count: "WOOD_SINGULARITY", farming_for_dummies_count: "FARMING_FOR_DUMMIES",
  tuned_transmission: "TRANSMISSION_TUNER", polarvoid: "POLARVOID_BOOK", bookworm_books: "BOOKWORM_BOOK", jalapeno_count: "JALAPENO_BOOK",
  mana_disintegrator_count: "MANA_DISINTEGRATOR" };
const FLAGS = { artOfPeaceApplied: "THE_ART_OF_PEACE", stats_book: "BOOK_OF_STATS", divan_powder_coating: "DIVAN_POWDER_COATING" };
const ETHERMERGE = ["ETHERWARP_MERGER", "ETHERWARP_CONDUIT"]; // same pair SkyHanni prices for ethermerge

// price of n x id in a variant: scratch = cheapest route (bz order / lbin / craft...), easy = insta-buy / lbin / npc
function price(id, n, variant) {
  const r = variant === "scratch" ? scratch(id) : easyUnit(id);
  return { id, n, unit: r.unit, via: r.via, total: r.unit == null ? null : r.unit * n };
}
function enchantLine(type, lvl, variant) {
  const t = type.toLowerCase();
  const name = ENCH_ALIAS[t] || t.toUpperCase();
  if (USE_LEVELED.has(t) && lvl > 1) return { ...price("ENCHANTMENT_" + name + "_1", 1, variant), note: "levels by use; priced as level 1" };
  if (t === "efficiency" && lvl > 5) return { ...price("SIL_EX", lvl - 5, variant), note: "Efficiency 6+ = Silex per level (1-5 from table)" };
  const own = price("ENCHANTMENT_" + name + "_" + lvl, 1, variant);
  const opts = own.unit != null ? [own] : [];
  // anvil: two level-(L-1) books -> level L, only up to level 5 (heuristic; ultimates up to their max)
  if (lvl > 1 && (lvl <= 5 || name.startsWith("ULTIMATE_"))) {
    for (let l = lvl - 1; l >= 1; l--) {
      const lower = price("ENCHANTMENT_" + name + "_" + l, 1, variant);
      if (lower.unit != null) { opts.push({ id: lower.id, n: 2 ** (lvl - l), unit: lower.unit, via: lower.via + "+anvil", total: lower.unit * 2 ** (lvl - l) }); break; }
    }
  }
  const tableMax = EN.max_xp_table_levels[t] ?? EN.max_xp_table_levels[t.toUpperCase()];
  if (!opts.length && tableMax != null && lvl <= tableMax) return { id: "ENCHANTMENT_" + name + "_" + lvl, n: 1, unit: 0, total: 0, via: "table" };
  opts.sort((a, b) => a.total - b.total);
  return opts[0] || { id: "ENCHANTMENT_" + name + "_" + lvl, n: 1, unit: 0, total: 0, via: "no market (counted as 0)" };
}

function priceListing(L, variant) {
  const f = L.flatNbt || L.flattenedNbt || {};
  const tag = L.tag, hy = HY[tag] || {};
  const lines = [];
  const add = (group, label, p) => lines.push({ group, label, ...p });
  // enchantments
  for (const e of L.enchantments || []) add("enchant", e.type + " " + e.level, enchantLine(e.type, e.level, variant));
  // stars
  const stars = Number(f.upgrade_level ?? f.dungeon_item_level ?? 0);
  const uc = hy.upgrade_costs || [];
  const ess = {}; const starItems = {};
  for (let i = 0; i < Math.min(stars, uc.length); i++) for (const c of uc[i]) {
    if (c.type === "ESSENCE") ess[c.essence_type] = (ess[c.essence_type] || 0) + c.amount;
    else if (c.type === "ITEM") starItems[c.item_id] = (starItems[c.item_id] || 0) + c.amount;
    else if (c.type === "COINS") starItems.COINS = (starItems.COINS || 0) + c.coins;
  }
  if (f.dungeon_item === "1" && hy.dungeon_item_conversion_cost) { const c = hy.dungeon_item_conversion_cost; ess[c.essence_type] = (ess[c.essence_type] || 0) + c.amount; }
  for (const k in ess) add("stars", "essence " + k, price("ESSENCE_" + k, ess[k], variant));
  for (const k in starItems) add("stars", "star items", k === "COINS" ? { id: "COINS", n: starItems[k], unit: 1, total: starItems[k], via: "coins" } : price(k, starItems[k], variant));
  if (stars > uc.length && uc.length === 5) for (let i = 0; i < Math.min(stars - 5, 5); i++) add("stars", "master star " + (i + 1), price(MASTER[i], 1, variant));
  // recomb, potato books
  if (Number(f.rarity_upgrades) > 0) add("recomb", "recombobulated", price("RECOMBOBULATOR_3000", 1, variant));
  const hpc = Number(f.hpc ?? f.hot_potato_count ?? 0);
  if (hpc > 0) add("hpb", "hot potato x" + Math.min(hpc, 10), price("HOT_POTATO_BOOK", Math.min(hpc, 10), variant));
  if (hpc > 10) add("hpb", "fuming x" + (hpc - 10), price("FUMING_POTATO_BOOK", hpc - 10, variant));
  // counted + flag upgrades
  for (const k in COUNTED) if (Number(f[k]) > 0) add("upgrade", k, price(COUNTED[k], Number(f[k]), variant));
  for (const k in FLAGS) if (f[k] != null && f[k] !== "0") add("upgrade", k, price(FLAGS[k], 1, variant));
  if (f.ethermerge != null && f.ethermerge !== "0") for (const e of ETHERMERGE) add("upgrade", "ethermerge", price(e, 1, variant));
  // scrolls
  if (f.ability_scroll) for (const s of String(f.ability_scroll).split(/[ ,]+/).filter(Boolean)) add("scroll", s, price(s, 1, variant));
  if (f.power_ability_scroll) add("scroll", f.power_ability_scroll, price(f.power_ability_scroll, 1, variant));
  // gems + slot unlocks
  const slots = hy.gemstone_slots || [];
  const unlocked = String(f.unlocked_slots || "").split(",").filter(Boolean);
  for (const s of unlocked) {
    const m = s.match(/^([A-Z]+)_(\d+)$/); if (!m) continue;
    const def = slots.filter(x => x.slot_type === m[1])[+m[2]];
    for (const c of (def && def.costs) || []) {
      if (c.type === "COINS") add("gem-slot", s + " unlock coins", { id: "COINS", n: c.coins, unit: 1, total: c.coins, via: "coins" });
      else add("gem-slot", s + " unlock", price(c.item_id, c.amount, variant));
    }
  }
  for (const k of Object.keys(f)) {
    const q = String(f[k]).toUpperCase();
    if (!/^[A-Z]+_\d+$/.test(k) || !QUAL.includes(q)) continue;
    const type = (f[k + "_gem"] || k.split("_")[0]).toUpperCase();
    add("gem", k, price(q + "_" + type + "_GEM", 1, variant));
  }
  // pets: held item (candyUsed only lowers value, pet level/exp is not buyable -> ignored)
  if (f.heldItem) add("pet", "held item", price(f.heldItem, 1, variant));
  // drill parts, dye, runes
  for (const k of ["drill_part_engine", "drill_part_fuel_tank", "drill_part_upgrade_module"]) if (f[k]) add("drill", k, price(String(f[k]).toUpperCase(), 1, variant));
  if (f.dye_item) add("cosmetic", "dye", price(f.dye_item, 1, variant));
  for (const k of Object.keys(f)) { const m = k.match(/^RUNE_(.+)$/); if (m) add("cosmetic", "rune", { ...price("RUNE-" + m[1] + "-" + f[k], 1, variant) }); }
  // reforge (active rows have L.reforge; sold rows need it parsed from itemName)
  const rf = L.reforge && L.reforge !== "None" ? L.reforge.toLowerCase() : null;
  if (rf) {
    const stone = Object.keys(RS).find(k => RS[k].reforgeName.toLowerCase() === rf);
    const TIERS = ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL"];
    // Coflnet sends tier "UNKNOWN" for some items (drills): fall back to the Hypixel items API tier, +1 when recombobulated
    const tier = L.tier && L.tier !== "UNKNOWN" ? L.tier : hy.tier ? TIERS[TIERS.indexOf(hy.tier) + (Number(f.rarity_upgrades) > 0 ? 1 : 0)] : null;
    if (stone) {
      add("reforge", "stone " + stone, price(stone, 1, variant));
      const apply = tier && RS[stone].reforgeCosts ? RS[stone].reforgeCosts[tier] : null;
      add("reforge", "apply cost @" + tier, { id: "COINS", n: apply ?? 0, unit: 1, total: apply ?? null, via: apply == null ? "unknown tier" : "coins" });
    } else add("reforge", "blacksmith " + rf, { id: "COINS", n: 0, unit: 0, total: 0, via: "blacksmith (not counted)" });
  }
  const total = lines.reduce((s, l) => s + (l.total || 0), 0);
  const missing = lines.filter(l => l.total == null).map(l => l.label);
  return { total, missing, lines };
}
module.exports = { priceListing };

if (require.main === module) {
  let L = JSON.parse(fs.readFileSync(process.argv[2])); if (Array.isArray(L)) L = L[Number(process.argv[3] || 0)];
  const base = craftCost(L.tag);
  const fmt = n => n == null ? "n/a" : Math.round(n).toLocaleString("en-US");
  console.log(`${L.tag} ${L.itemName} listed ${fmt(L.startingBid)}  tier ${L.tier} reforge ${L.reforge}`);
  for (const v of ["scratch", "easy"]) {
    const r = priceListing(L, v);
    const clean = v === "scratch" ? base.scratch.unit : base.easy.unit;
    console.log(`\n== ${v}: clean craft ${fmt(clean)} + modifiers ${fmt(r.total)} = ${fmt(clean + r.total)}${r.missing.length ? "  MISSING: " + r.missing.join(", ") : ""}`);
    for (const l of r.lines) console.log(`  [${l.group}] ${l.label}: ${l.n} x ${l.id} ${l.via} @ ${fmt(l.unit)} = ${fmt(l.total)}${l.note ? "  (" + l.note + ")" : ""}`);
  }
}
