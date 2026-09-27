// Reference implementation of the craft-cost rules in craft-spec.md (node, dev only, NOT shipped).
// Reads the local cache in notes/cache (bazaar.json, lowestbins.json, neurepo/repo/items/*.json).
// Usage: node ref-craft.js HYPERION [ID...]      -> prints both trees + totals
//        node ref-craft.js --json HYPERION       -> JSON result (used for samples/worked-*.json)
"use strict";
const fs = require("fs");
const C = __dirname + "/cache/";
const BZ = JSON.parse(fs.readFileSync(C + "bazaar.json")).products;
const LB = JSON.parse(fs.readFileSync(C + "lowestbins.json"));
const SLIM = JSON.parse(fs.readFileSync(C + "neu-slim.json")); // built from neurepo/repo/items (see craft-spec.md)
const ITEMS = {};
for (const id in SLIM) ITEMS[id] = { internalname: id, ...SLIM[id] };

// ---------- ids ----------
const PET_TIERS = ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"];
function bzId(id) {                      // NEU id -> bazaar product id (or null)
  if (BZ[id]) return id;
  const dash = id.replace(/-(\d+)$/, ":$1");           // LOG-2 -> LOG:2
  if (BZ[dash]) return dash;
  const m = id.match(/^([A-Z0-9_]+);(\d+)$/);            // SHARPNESS;5 -> ENCHANTMENT_SHARPNESS_5
  if (m && BZ["ENCHANTMENT_" + m[1] + "_" + m[2]]) return "ENCHANTMENT_" + m[1] + "_" + m[2];
  return null;
}
function lbKey(id) {                     // NEU id -> key in lowestbins.json (non-bazaar only)
  const m = id.match(/^([A-Z0-9_]+);(\d+)$/);
  if (m && ITEMS[id] && ITEMS[id].itemid === "minecraft:skull" && PET_TIERS[+m[2]]) return "PET-" + m[1] + "-" + PET_TIERS[+m[2]];
  if (m) return "ENCHANTED_BOOK-" + m[1] + "-" + m[2];
  const e = id.match(/^ENCHANTMENT_([A-Z0-9_]+)_(\d+)$/);   // bazaar-style book id -> AH key
  if (e) return "ENCHANTED_BOOK-" + e[1] + "-" + e[2];
  return id;
}

// ---------- prices ----------
function bz(id) {
  const b = bzId(id); if (!b) return null;
  const p = BZ[b];
  return { id: b, instaBuy: p.buy_summary[0] ? p.buy_summary[0].pricePerUnit : null, order: p.sell_summary[0] ? p.sell_summary[0].pricePerUnit : null };
}
function lbin(id) { if (bzId(id)) return null; const v = LB[lbKey(id)]; return typeof v === "number" && v > 0 ? v : null; }

// ---------- recipes (indexed by output id) ----------
const RECIPES = {};
function parseStack(s) { const i = s.lastIndexOf(":"); if (i < 0) return { id: s, n: 1 }; const n = Number(s.slice(i + 1)); return isFinite(n) ? { id: s.slice(0, i), n } : { id: s, n: 1 }; }
function add(out, r) { (RECIPES[out] = RECIPES[out] || []).push(r); }
function gridInputs(g) {
  const tot = {};
  for (const k of ["A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"]) if (g[k]) { const s = parseStack(g[k]); tot[s.id] = (tot[s.id] || 0) + s.n; }
  return Object.entries(tot).map(([id, n]) => ({ id, n }));
}
for (const id in ITEMS) {
  const j = ITEMS[id];
  if (j.recipe) add(id, { type: "craft", inputs: gridInputs(j.recipe), out: Number(j.recipe.count) || 1 });
  for (const r of j.recipes || []) {
    if (r.type === "crafting") add(r.overrideOutputId || id, { type: "craft", inputs: gridInputs(r), out: Number(r.count) || 1 });
    else if (r.type === "forge") add(r.overrideOutputId || id, { type: "forge", inputs: r.inputs.map(parseStack), out: Number(r.count) || 1, duration: r.duration });
    else if (r.type === "npc_shop") { const o = parseStack(r.result); add(o.id, { type: "npc", shop: id, inputs: r.cost.map(parseStack), out: o.n }); }
    else if (r.type === "katgrade") add(r.output, { type: "kat", inputs: [{ id: r.input, n: 1 }, ...(r.items || []).map(parseStack), { id: "SKYBLOCK_COIN", n: Number(r.coins) || 0 }], out: 1, duration: r.time });
    else if (r.type === "trade") { const o = parseStack(r.result), c = parseStack(r.cost); const mm = [Number(r.min), Number(r.max)].filter(x => x > 0); if (mm.length) c.n *= mm.reduce((a, b) => a + b) / mm.length; add(o.id, { type: "trade", inputs: [c], out: o.n }); }
  }
}

// ---------- from scratch ----------
// Cycle rule: while pricing X, any use of an item already on the stack (X or an ancestor) makes that recipe invalid.
// Results that never touched the stack are context-free and memoized globally; the rest ("cut") are recomputed.
const memo = {};
function recipeCost(r, stack, priceFn) {
  let total = 0, cut = false; const parts = [];
  for (const inp of r.inputs) {
    if (inp.id === "SKYBLOCK_COIN") { total += inp.n; parts.push({ id: "COINS", n: inp.n, unit: 1, cost: inp.n, via: "coins" }); continue; }
    if (inp.id.startsWith("SKYBLOCK_")) return null;          // bits, copper, motes, north stars...: not buyable with coins
    if (stack.includes(inp.id)) return { cycle: true };
    const c = priceFn(inp.id, stack);
    if (!c || c.unit == null) return c && c.cut ? { cycle: true } : null;
    if (c.cut) cut = true;
    total += c.unit * inp.n; parts.push({ ...c, n: inp.n, cost: c.unit * inp.n });
  }
  return { unit: total / r.out, parts, out: r.out, cut };
}
// returns {id, unit, via, parts?, cut}; unit null = cannot be priced
function scratch(id, stack = [], forceCraft = false) {
  if (!forceCraft && memo[id]) return memo[id];
  const cands = []; let cut = false;
  const b = bz(id);
  if (!forceCraft && b && b.order != null) cands.push({ id, unit: b.order, via: "bz-order" });
  else if (!forceCraft && b && b.instaBuy != null) cands.push({ id, unit: b.instaBuy, via: "bz-instabuy (no buy orders)" });
  const l = lbin(id);
  if (!forceCraft && l != null) cands.push({ id, unit: l, via: "lbin" });
  if (stack.length >= 15) cut = true;                        // depth guard (deepest real tree is ~10)
  else for (const r of RECIPES[id] || []) {
    const rc = recipeCost(r, [...stack, id], (x, s) => scratch(x, s));
    if (!rc) continue;
    if (rc.cycle) { cut = true; continue; }
    if (rc.cut) cut = true;
    cands.push({ id, unit: rc.unit, via: r.type, out: rc.out, parts: rc.parts });
  }
  // strict min; on a tie the market option (pushed first) wins
  let best = null; for (const c of cands) if (!best || c.unit < best.unit) best = c;
  best = best ? { ...best, cut } : { id, unit: null, via: "none", cut };
  if (!forceCraft && !cut) memo[id] = best;
  return best;
}
// ---------- easy way ----------
function easyUnit(id) {
  const b = bz(id), l = lbin(id);
  const opts = [];
  if (b && b.instaBuy != null) opts.push({ id, unit: b.instaBuy, via: "bz-instabuy" });
  if (l != null) opts.push({ id, unit: l, via: "lbin" });
  const npc = (RECIPES[id] || []).filter(r => r.type === "npc" && r.inputs.every(i => i.id === "SKYBLOCK_COIN"));
  for (const r of npc) opts.push({ id, unit: r.inputs.reduce((s, i) => s + i.n, 0) / r.out, via: "npc" });
  opts.sort((a, b) => a.unit - b.unit);
  if (opts[0]) return opts[0];
  const s = scratch(id);
  return { ...s, flagged: "no market price, used from-scratch cost" };
}
function easy(id) {
  let best = null;
  for (const r of RECIPES[id] || []) {
    const rc = recipeCost(r, [id], (x) => { const e = easyUnit(x); return { id: x, unit: e.unit, via: e.via, flagged: e.flagged }; });
    if (rc && (!best || rc.unit < best.unit)) best = { id, unit: rc.unit, via: r.type, out: rc.out, parts: rc.parts };
  }
  return best || { id, unit: null, via: "none" };
}
function craftCost(id) {
  const s = scratch(id, [], true);  // top node: must be crafted
  return { id, scratch: s, easy: easy(id), bz: bz(id), lbin: lbin(id) };
}
module.exports = { craftCost, scratch, easy, easyUnit, bz, lbin, RECIPES, ITEMS };

if (require.main === module) {
  const args = process.argv.slice(2); const asJson = args[0] === "--json"; if (asJson) args.shift();
  const fmt = n => n == null ? "n/a" : Math.round(n).toLocaleString("en-US");
  function show(n, d, mult) {
    const pad = "  ".repeat(d);
    console.log(`${pad}${n.n != null ? n.n + "x " : ""}${n.id}  ${n.via}  unit=${fmt(n.unit)}  total=${fmt(n.unit * (n.n || 1))}${n.out > 1 ? "  (recipe makes " + n.out + ")" : ""}${n.flagged ? "  [" + n.flagged + "]" : ""}`);
    if (n.parts && d < 6) for (const p of n.parts) show(p, d + 1);
  }
  for (const id of args) {
    const r = craftCost(id);
    if (asJson) { console.log(JSON.stringify(r)); continue; }
    console.log(`\n##### ${id}  lbin=${fmt(r.lbin)}  bz=${JSON.stringify(r.bz)}`);
    console.log("--- FROM SCRATCH total " + fmt(r.scratch.unit)); show(r.scratch, 0);
    console.log("--- EASY total " + fmt(r.easy.unit)); show(r.easy, 0);
  }
}
