// Test-suite reference implementation (independent of craftCore.js / craftModifiers.js): reads ONLY the fixture
// files in this folder and writes expected.json, the numbers the tests compare the engine against.
// Rules = notes/craft-spec.md sections 3, 5, 6. Run: "C:/Program Files/nodejs/node.exe" craft/fixtures/reference.mjs
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { parseListing, USE_LEVELED } from "./refmods.mjs";
import { toContractRecipes, toContractBazaar } from "./convert.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const rd = n => JSON.parse(fs.readFileSync(path.join(HERE, n), "utf8"));
export const BZRAW = rd("bazaar.json").products;
export const LB = rd("lowestbins.json");
const COMPACT = rd("recipes.json");
const HY = {}; for (const i of rd("hypixel-items.json").items) HY[i.id] = i;
const STONES = rd("constants-reforgestones.json");
const TABLE = rd("constants-enchants.json").max_xp_table_levels;

const R = toContractRecipes(COMPACT);
const BZ = toContractBazaar(BZRAW);

// ---- ids + market
const PETS = ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"];
function bzOf(id) {
  if (BZ.has(id)) return BZ.get(id);
  const d = id.replace(/-(\d+)$/, ":$1"); if (BZ.has(d)) return BZ.get(d);
  const m = id.match(/^([A-Z0-9_]+);(\d+)$/); if (m && BZ.has("ENCHANTMENT_" + m[1] + "_" + m[2])) return BZ.get("ENCHANTMENT_" + m[1] + "_" + m[2]);
  return null;
}
function lbOf(id) {
  if (bzOf(id)) return null;
  const keys = [id];
  const m = id.match(/^([A-Z0-9_]+);(\d+)$/);
  if (m) keys.push("PET-" + m[1] + "-" + PETS[+m[2]], "ENCHANTED_BOOK-" + m[1] + "-" + m[2]);
  const e = id.match(/^ENCHANTMENT_([A-Z0-9_]+)_(\d+)$/); if (e) keys.push("ENCHANTED_BOOK-" + e[1] + "-" + e[2]);
  for (const k of keys) if (typeof LB[k] === "number" && LB[k] > 0) return LB[k];
  return null;
}

// NEU "trade" entries for enchant books (50 enchanted emeralds -> Ender Slayer VI) are ignored
function recipesOf(id) {
  const m = id.match(/^([A-Z0-9_]+);(\d+)$/);
  const book = m && (BZ.has("ENCHANTMENT_" + m[1] + "_" + m[2]) || typeof LB["ENCHANTED_BOOK-" + m[1] + "-" + m[2]] === "number");
  return (R.get(id) || []).filter(r => !(book && r.note === "trade"));
}
// ---- from scratch (cycle rule: recipe using an id on the stack is invalid; only uncut results memoized)
const memo = new Map();
function recipeUnit(r, stack, price) {
  let total = r.coins || 0, cut = false;
  for (const { id, qty } of r.inputs) {
    if (id.startsWith("SKYBLOCK_")) return { unit: null };
    if (stack.includes(id)) return { unit: null, cut: true };
    const c = price(id, stack);
    if (c.cut) cut = true;
    if (c.unit == null) return { unit: null, cut };
    total += c.unit * qty;
  }
  return { unit: total / r.output, cut };
}
export function scratch(id, stack = []) {
  if (memo.has(id)) return memo.get(id);
  const c = []; let cut = false;
  const b = bzOf(id);
  // spec 1b: a top buy order under 1% of insta-buy is a dead order -> insta-buy
  if (b && b.buyOrder != null && !(b.instaBuy != null && b.buyOrder < b.instaBuy * 0.01)) c.push({ unit: b.buyOrder, method: "buyOrder" });
  else if (b && b.instaBuy != null) c.push({ unit: b.instaBuy, method: "instaBuy" });
  const l = lbOf(id); if (l != null) c.push({ unit: l, method: "lowestBin" });
  if (stack.length >= 15) cut = true;
  else for (const r of recipesOf(id)) {
    const x = recipeUnit(r, [...stack, id], scratch);
    if (x.cut) cut = true;
    if (x.unit != null) c.push({ unit: x.unit, method: r.type, recipe: r });
  }
  let best = null; for (const x of c) if (!best || x.unit < best.unit) best = x;
  const res = { unit: best ? best.unit : null, method: best ? best.method : "unavailable", recipe: best && best.recipe, cut };
  if (!cut) memo.set(id, res);
  return res;
}
// easy: insta-buy / lowest BIN (/ NPC coin shop per spec 3B); no market -> from-scratch cost, flagged
export function easyUnit(id, { npc = true, stack = [] } = {}) {
  const o = [];
  const b = bzOf(id); if (b && b.instaBuy != null) o.push({ unit: b.instaBuy, method: "instaBuy" });
  const l = lbOf(id); if (l != null) o.push({ unit: l, method: "lowestBin" });
  if (npc) for (const r of recipesOf(id)) if (r.type === "npc" && !r.inputs.length) o.push({ unit: r.coins / r.output, method: "npc" });
  o.sort((a, b) => a.unit - b.unit);
  if (o[0]) return o[0];
  const s = scratch(id, stack);   // stack = the root being crafted: the fallback may not loop back through it
  return { unit: s.unit, method: s.method, flagged: true };
}
// root forced through its recipe
function root(id, mode) {
  let best = null;
  for (const r of recipesOf(id)) {
    const kids = [];
    let total = r.coins || 0, ok = true;
    for (const { id: i, qty } of r.inputs) {
      if (i.startsWith("SKYBLOCK_")) { ok = false; break; }
      const p = mode === "fromScratch" ? (i === id ? { unit: null } : scratch(i, [id])) : easyUnit(i, { stack: [id] });
      if (p.unit == null) { ok = false; break; }
      total += p.unit * qty; kids.push({ id: i, qty, unitCost: p.unit, total: p.unit * qty, method: p.method, flagged: !!p.flagged });
    }
    if (!ok) continue;
    if (r.coins) kids.push({ id: "SKYBLOCK_COIN", qty: r.coins, unitCost: 1, total: r.coins, method: "coins" });
    const unit = total / r.output;
    if (!best || unit < best.unitCost) best = { id, unitCost: unit, perCraft: total, output: r.output, method: r.type, children: kids };
  }
  return best;
}

// ---- listing modifiers
function enchantLine(e, mode) {
  const price = id => mode === "fromScratch" ? scratch(id) : easyUnit(id);
  const { type, name, lvl } = e;
  if (USE_LEVELED.has(type) && lvl > 1) { const p = price("ENCHANTMENT_" + name + "_1"); return { id: "ENCHANTMENT_" + name + "_1", qty: 1, unit: p.unit, total: p.unit, method: p.method }; }
  if (type === "efficiency" && lvl > 5) { const p = price("SIL_EX"); return { id: "SIL_EX", qty: lvl - 5, unit: p.unit, total: p.unit == null ? null : p.unit * (lvl - 5), method: p.method }; }
  // spec 5a: anvil only for ultimates or up to min(V, table max + 1); lower books only at a market price;
  // easy uses the book itself whenever it can be bought, anvil / fallback only when it can't
  const opts = [];
  const own = price("ENCHANTMENT_" + name + "_" + lvl);
  if (own.unit != null) opts.push({ id: "ENCHANTMENT_" + name + "_" + lvl, qty: 1, unit: own.unit, total: own.unit, method: own.method });
  if (mode === "easy" && own.unit != null && !own.flagged) return opts[0];
  const tmax = TABLE[type] ?? TABLE[type.toUpperCase()];
  const canAnvil = lvl > 1 && (name.startsWith("ULTIMATE_") || (tmax > 0 && lvl <= Math.min(5, tmax + 1)));
  if (canAnvil) for (let l = lvl - 1; l >= 1; l--) {
    const p = price("ENCHANTMENT_" + name + "_" + l);
    if (p.unit == null || p.flagged || !["buyOrder", "instaBuy", "lowestBin"].includes(p.method)) continue;
    opts.push({ id: "ENCHANTMENT_" + name + "_" + l, qty: 2 ** (lvl - l), unit: p.unit, total: p.unit * 2 ** (lvl - l), method: p.method + "+anvil" }); break;
  }
  opts.sort((a, b) => a.total - b.total);
  if (opts[0]) return opts[0];
  const tm = TABLE[type] ?? TABLE[type.toUpperCase()];
  return { id: "ENCHANTMENT_" + name + "_" + lvl, qty: 1, unit: 0, total: 0, method: tm != null && lvl <= tm ? "table" : "noMarket" };
}
function listingLines(L, mode) {
  const parts = parseListing(L, HY[L.tag] || {}, STONES);
  return parts.map(p => {
    if (p.enchant) return { group: p.group, label: p.label, ...enchantLine(p.enchant, mode) };
    if (p.id === "SKYBLOCK_COIN") return { group: p.group, label: p.label, id: p.id, qty: p.qty, unit: 1, total: p.qty, method: "coins" };
    let pr;
    if (p.lbKey) pr = { unit: typeof LB[p.id] === "number" ? LB[p.id] : null, method: "lowestBin" };
    else pr = mode === "fromScratch" ? scratch(p.id) : easyUnit(p.id);
    return { group: p.group, label: p.label, id: p.id, qty: p.qty, unit: pr.unit, total: pr.unit == null ? null : pr.unit * p.qty, method: pr.method };
  });
}
function petRootId(L) { const m = /^PET_(.+)$/.exec(L.tag); return m ? m[1] + ";" + PETS.indexOf(L.tier) : L.tag; }

// ---- expected.json
const ROOTS = ["HYPERION", "TERMINATOR", "ASPECT_OF_THE_VOID", "JUJU_SHORTBOW", "DIVAN_DRILL", "TITANIUM_DRILL_1",
  "ENCHANTED_DIAMOND_BLOCK", "REFINED_MITHRIL_PICKAXE", "TARANTULA_SILK", "DIVAN_CHESTPLATE", "POWER_WITHER_CHESTPLATE", "CRIMSON_CHESTPLATE", "ENDER_DRAGON;4"];
const KEY_IDS = ["NECRON_BLADE", "GIANT_FRAGMENT_LASER", "WITHER_CATALYST", "NECRON_HANDLE", "NULL_OVOID", "NULL_BLADE", "TESSELLATED_ENDER_PEARL",
  "TARANTULA_SILK", "ENCHANTED_FLINT", "FLINT", "JUDGEMENT_CORE", "ENCHANTED_DIAMOND", "ENCHANTED_DIAMOND_BLOCK", "DIAMOND_BLOCK", "IRON_INGOT", "IRON_BLOCK",
  "HOT_POTATO_BOOK", "SAPPHIRE_POWER_SCROLL", "RECOMBOBULATOR_3000", "FLAWLESS_SAPPHIRE_GEM", "PERFECT_SAPPHIRE_GEM", "TITANIUM_DRILL_4",
  "DIVAN_ALLOY", "DIVAN_POWDER_COATING", "MITHRIL_PICKAXE", "INK_SACK-4", "ENCHANTED_LAPIS_LAZULI", "ENCHANTMENT_ULTIMATE_WISE_4", "SIL_EX"];
const exp = { generated: new Date().toISOString(), note: "computed by fixtures/reference.mjs from the fixture files only", roots: {}, items: {}, listings: {}, easyNpcDiff: [] };
for (const id of ROOTS) exp.roots[id] = { fromScratch: root(id, "fromScratch"), easy: root(id, "easy"), lowestBin: lbOf(id) };
for (const id of KEY_IDS) {
  const s = scratch(id), e = easyUnit(id), e2 = easyUnit(id, { npc: false });
  exp.items[id] = { fromScratch: { unitCost: s.unit, method: s.method }, easy: { unitCost: e.unit, method: e.method, flagged: !!e.flagged } };
}
// where the spec's "NPC counts as buying" rule changes an easy price vs the contract's plain "instaBuy / lowestBin"
for (const id of R.keys()) { const a = easyUnit(id), b = easyUnit(id, { npc: false }); if (a.unit !== b.unit) exp.easyNpcDiff.push({ id, withNpc: a.unit, withoutNpc: b.unit }); }
for (const f of fs.readdirSync(path.join(HERE, "listings"))) {
  const L = rd("listings/" + f), k = f.replace(/\.json$/, "");
  const base = root(petRootId(L), "fromScratch"), baseE = root(petRootId(L), "easy");
  const s = listingLines(L, "fromScratch"), e = listingLines(L, "easy");
  const sum = ls => ls.reduce((a, l) => a + (l.total || 0), 0);
  const ms = scratch(petRootId(L)), me = easyUnit(petRootId(L));
  exp.listings[k] = { tag: L.tag, rootId: petRootId(L), marketBase: { fromScratch: ms.unit, easy: me.unit }, base: { fromScratch: base ? base.unitCost : null, easy: baseE ? baseE.unitCost : null },
    lines: { fromScratch: s, easy: e }, modifierSum: { fromScratch: sum(s), easy: sum(e) },
    total: { fromScratch: (base ? base.unitCost : 0) + sum(s), easy: (baseE ? baseE.unitCost : 0) + sum(e) } };
}
fs.writeFileSync(path.join(HERE, "expected.json"), JSON.stringify(exp, null, 1));

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  const f = n => n == null ? "n/a" : Math.round(n).toLocaleString("en-US");
  for (const id of ROOTS) { const r = exp.roots[id]; console.log(id.padEnd(26), "scratch", f(r.fromScratch && r.fromScratch.unitCost).padStart(15), " easy", f(r.easy && r.easy.unitCost).padStart(15), " lbin", f(r.lowestBin)); }
  for (const k in exp.listings) { const l = exp.listings[k]; console.log("listing", k.padEnd(24), "scratch", f(l.total.fromScratch).padStart(15), " easy", f(l.total.easy).padStart(15)); }
  console.log("easy NPC diffs:", exp.easyNpcDiff.length, exp.easyNpcDiff.slice(0, 8).map(d => d.id).join(" "));
}
