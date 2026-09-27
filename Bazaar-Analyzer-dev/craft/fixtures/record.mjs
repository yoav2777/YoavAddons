// Dev script: records the deterministic test fixtures from the live-API snapshots in notes/cache
// (bazaar 18:48 UTC, lb.tricked.pro lowestbins 18:52 UTC, Hypixel items, NEU repo zip @ a332242, all 2026-09-21)
// and the real Coflnet listing rows in notes/samples. Re-run only when you want new fixtures:
//   "C:/Program Files/nodejs/node.exe" craft/fixtures/record.mjs && "C:/Program Files/nodejs/node.exe" craft/fixtures/reference.mjs
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { parseListing, ENCH_ALIAS } from "./refmods.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const NOTES = path.join(HERE, "../../notes");
const CACHE = path.join(NOTES, "cache");
const REPO = path.join(CACHE, "neurepo/repo");
const rd = p => JSON.parse(fs.readFileSync(p, "utf8"));
const wr = (name, obj) => { fs.mkdirSync(path.dirname(path.join(HERE, name)), { recursive: true }); fs.writeFileSync(path.join(HERE, name), JSON.stringify(obj, null, name.includes("listings/") ? 1 : 0)); };

const BZ = rd(path.join(CACHE, "bazaar.json"));
const LB = rd(path.join(CACHE, "lowestbins.json"));
const HY = rd(path.join(CACHE, "hyitems.json"));
const HYBY = {}; for (const i of HY.items) HYBY[i.id] = i;
const STONES_FULL = rd(path.join(REPO, "constants/reforgestones.json"));
const STONES = {};
for (const k in STONES_FULL) { const s = STONES_FULL[k]; STONES[k] = { internalName: s.internalName, reforgeName: s.reforgeName, reforgeType: s.reforgeType, itemTypes: s.itemTypes, requiredRarities: s.requiredRarities, reforgeCosts: s.reforgeCosts }; }
const ENCH = rd(path.join(REPO, "constants/enchants.json"));

// ---- NEU items (all) -> recipe index by output (compact spec 2b format) + which file holds each recipe
const NEU = {};
for (const f of fs.readdirSync(path.join(REPO, "items"))) if (f.endsWith(".json")) NEU[f.slice(0, -5)] = rd(path.join(REPO, "items", f));
const parseStack = s => { s = String(s); const i = s.lastIndexOf(":"); if (i < 0) return [s, 1]; const n = Number(s.slice(i + 1)); return Number.isFinite(n) ? [s.slice(0, i), n] : [s, 1]; };
const grid = g => { const t = {}; for (const k of ["A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"]) if (g[k]) { const [id, n] = parseStack(g[k]); t[id] = (t[id] || 0) + n; } return Object.entries(t); };
const REC = {}, SRC = {}; // SRC[output] = Set of NEU file ids holding its recipes
const add = (out, r, file) => { (REC[out] = REC[out] || []).push(r); (SRC[out] = SRC[out] || new Set()).add(file); };
for (const id in NEU) {
  const j = NEU[id];
  if (j.recipe) add(id, ["c", Number(j.recipe.count) || 1, grid(j.recipe)], id);
  for (const r of j.recipes || []) {
    if (r.type === "crafting") add(r.overrideOutputId || id, ["c", Number(r.count) || 1, grid(r)], id);
    else if (r.type === "forge") add(r.overrideOutputId || id, ["f", Number(r.count) || 1, r.inputs.map(parseStack), r.duration], id);
    else if (r.type === "npc_shop") { const [o, n] = parseStack(r.result); add(o, ["n", n, r.cost.map(parseStack)], id); }
    else if (r.type === "trade") { const [o, n] = parseStack(r.result); const [c, cn] = parseStack(r.cost); const mm = [Number(r.min), Number(r.max)].filter(x => x > 0); add(o, ["t", n, [[c, mm.length ? cn * mm.reduce((a, b) => a + b) / mm.length : cn]]], id); } // min/max (EMERALD.json) = average count
    else if (r.type === "katgrade") add(r.output, ["k", 1, [[r.input, 1], ...(r.items || []).map(parseStack), ["SKYBLOCK_COIN", Number(r.coins) || 0]], r.time], id);
  }
}

// ---- roots
const CRAFT_ROOTS = ["HYPERION", "TERMINATOR", "ASPECT_OF_THE_VOID", "JUJU_SHORTBOW", "DIVAN_DRILL", "TITANIUM_DRILL_1",
  "ENCHANTED_DIAMOND_BLOCK", "ENCHANTED_DIAMOND", "REFINED_MITHRIL_PICKAXE", "TARANTULA_SILK", "DIVAN_CHESTPLATE",
  "POWER_WITHER_CHESTPLATE", "CRIMSON_CHESTPLATE", "ENDER_DRAGON;4"];
const LISTINGS = {
  "hyperion-full": "listing-hyperion-full.json",
  "hyperion-sold": "sold-hyperion-row.json",
  "terminator-full": "listing-terminator-full.json",
  "terminator-sold-aow": "coflnet_sold_TERMINATOR_aow_runes.json",
  "divan-chestplate-gems": "listing-divan-chestplate-gems.json",
  "necron-chestplate": "listing-necron-chestplate.json",
  "divan-drill": "listing-divan-drill.json",
  "crimson-chestplate": "listing-crimson-chestplate.json",
  "pet-ender-dragon": "listing-pet-ender-dragon.json",
};
const listings = {};
for (const k in LISTINGS) { let L = rd(path.join(NOTES, "samples", LISTINGS[k])); if (Array.isArray(L)) L = L[0]; listings[k] = L; }
// synthetic row (real TERMINATOR row + a rune, a dye, wood singularity and ethermerge keys) for the modifiers nobody listed today
listings["synthetic-extras"] = { ...listings["terminator-full"], uuid: "synthetic", reforge: "None",
  flatNbt: { ...listings["terminator-full"].flatNbt, RUNE_SOULTWIST: "3", dye_item: "DYE_LAVA", wood_singularity_count: "1", ethermerge: "1" } };
for (const k in listings) wr("listings/" + k + ".json", listings[k]);

// ids the listings reference (+ every lower level of each enchant for the anvil rule)
const modIds = new Set(), lbKeys = new Set(), enchNames = new Set();
for (const k in listings) {
  const L = listings[k];
  for (const p of parseListing(L, HYBY[L.tag] || {}, STONES)) {
    if (p.enchant) { enchNames.add(p.enchant.name); continue; }
    if (p.lbKey) { lbKeys.add(p.id); continue; }
    if (p.id !== "SKYBLOCK_COIN") modIds.add(p.id);
  }
}
modIds.add("SIL_EX");

// ---- closure over recipes (depth guard 20)
const need = new Set();
const walk = (id, d) => { if (need.has(id) || d > 20) return; need.add(id); for (const r of REC[id] || []) for (const [i] of r[2]) walk(i, d + 1); };
for (const id of [...CRAFT_ROOTS, ...modIds]) walk(id, 0);

// id mapping helpers (same rules as the spec 2c)
const bzId = id => { const P = BZ.products; if (P[id]) return id; const d = id.replace(/-(\d+)$/, ":$1"); if (P[d]) return d; const m = id.match(/^([A-Z0-9_]+);(\d+)$/); if (m && P["ENCHANTMENT_" + m[1] + "_" + m[2]]) return "ENCHANTMENT_" + m[1] + "_" + m[2]; return null; };

// ---- bazaar (raw Hypixel shape, top-of-book only)
const products = {};
const keepBz = pid => { const p = BZ.products[pid]; if (!p || products[pid]) return; products[pid] = { product_id: pid, sell_summary: p.sell_summary.slice(0, 1), buy_summary: p.buy_summary.slice(0, 1), quick_status: p.quick_status }; };
for (const id of need) { const b = bzId(id); if (b) keepBz(b); }
for (const pid in BZ.products) for (const n of enchNames) if (pid.startsWith("ENCHANTMENT_" + n + "_") && /^\d+$/.test(pid.slice(("ENCHANTMENT_" + n + "_").length))) keepBz(pid);
wr("bazaar.json", { success: true, lastUpdated: BZ.lastUpdated, products });

// ---- lowest BIN (lb.tricked.pro shape: flat {key: price})
const lb = {};
const keepLb = k => { if (typeof LB[k] === "number") lb[k] = LB[k]; };
for (const id of need) {
  keepLb(id);
  const m = id.match(/^([A-Z0-9_]+);(\d+)$/);
  if (m) { keepLb("ENCHANTED_BOOK-" + m[1] + "-" + m[2]); for (const t of ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"]) keepLb("PET-" + m[1] + "-" + t); }
}
for (const k of lbKeys) keepLb(k);
for (const k in LB) if (k.startsWith("PET-ENDER_DRAGON-")) keepLb(k);
for (const k in LB) for (const n of enchNames) if (k.startsWith("ENCHANTED_BOOK-" + n + "-")) keepLb(k);
for (const t of CRAFT_ROOTS) keepLb(t);
for (const k in listings) keepLb(listings[k].tag);
// a couple of lowestbins entries that are ALSO bazaar products (the engine must ignore lbin for bazaar ids)
for (const k of ["GIANT_FRAGMENT_LASER", "WITHER_CATALYST", "ENCHANTED_DIAMOND"]) keepLb(k);
wr("lowestbins.json", lb);

// ---- recipes: compact output-indexed file (spec 2b) + raw NEU item files (only what holds reachable recipes)
const recipes = {}, neu = {};
const slimNeu = j => { const o = {}; for (const k of ["internalname", "itemid", "displayname", "recipe", "recipes", "slayer_req", "crafttext", "vanilla", "parent"]) if (j[k] !== undefined) o[k] = j[k]; return o; };
for (const id of need) {
  if (REC[id]) recipes[id] = REC[id].map(r => r[3] != null ? r : r.slice(0, 3));
  if (NEU[id]) neu[id] = slimNeu(NEU[id]);
  for (const f of SRC[id] || []) if (!neu[f]) {
    // NPC files: keep only the offers that produce something we need (they are big otherwise)
    const j = slimNeu(NEU[f]);
    if (j.recipes) j.recipes = j.recipes.filter(r => r.type !== "npc_shop" || need.has(parseStack(r.result)[0]));
    neu[f] = j;
  }
}
wr("recipes.json", recipes);
wr("neu-items.json", neu);

// ---- Hypixel items resource (trimmed to the listing tags + roots + needed ids)
const hyKeep = new Set([...need, ...Object.values(listings).map(l => l.tag)]);
const slimHy = i => { const o = {}; for (const k of ["id", "name", "tier", "category", "material", "npc_sell_price", "upgrade_costs", "gemstone_slots", "dungeon_item_conversion_cost", "dungeon_item", "soulbound", "can_auction"]) if (i[k] !== undefined) o[k] = i[k]; return o; };
wr("hypixel-items.json", { success: true, lastUpdated: HY.lastUpdated, items: HY.items.filter(i => hyKeep.has(i.id)).map(slimHy) });
wr("constants-reforgestones.json", STONES);
wr("constants-enchants.json", ENCH);

// ---- Coflnet craft/profit rows for our roots (sanity comparison only)
const CP = rd(path.join(CACHE, "craft_profit.json"));
wr("craft-profit.json", CP.filter(r => need.has(r.itemId)));

console.log(`need ${need.size} ids, recipes ${Object.keys(recipes).length}, neu files ${Object.keys(neu).length}, bazaar ${Object.keys(products).length}, lbin ${Object.keys(lb).length}, listings ${Object.keys(listings).length}, enchant names ${enchNames.size}, alias check ${ENCH_ALIAS.ultimate_duplex}`);
