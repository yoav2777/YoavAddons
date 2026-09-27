// Fixture -> contract shapes (shared by reference.mjs and the tests). No engine code here.
const TYPES = { c: "craft", f: "forge", n: "npc", t: "other", k: "other" };
const NOTES = { t: "trade", k: "Kat pet upgrade" };
// compact recipes.json ({id: [[type, out, [[id,n]...], duration?]]}) -> Map<id, Recipe[]> (coins moved to Recipe.coins)
export function toContractRecipes(compact) {
  const m = new Map();
  for (const id in compact) m.set(id, compact[id].map(([t, out, ins]) => {
    let coins = 0; const inputs = [];
    for (const [i, n] of ins) { if (i === "SKYBLOCK_COIN") coins += n; else inputs.push({ id: i, qty: n }); }
    const r = { type: TYPES[t], output: out, inputs, coins };
    if (NOTES[t]) r.note = NOTES[t];
    return r;
  }));
  return m;
}
// raw Hypixel products -> Map<productId, {instaBuy, buyOrder}>; empty side = null
export function toContractBazaar(products) {
  const m = new Map();
  for (const id in products) { const p = products[id]; m.set(id, { instaBuy: p.buy_summary[0] ? p.buy_summary[0].pricePerUnit : null, buyOrder: p.sell_summary[0] ? p.sell_summary[0].pricePerUnit : null }); }
  return m;
}
export function toContractLowestBin(obj) { const m = new Map(); for (const k in obj) if (typeof obj[k] === "number" && obj[k] > 0) m.set(k, obj[k]); return m; }
