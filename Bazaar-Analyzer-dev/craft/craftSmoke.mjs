// Live smoke run of the craft engine (dev only, not shipped).
//   "C:/Program Files/nodejs/node.exe" craft/craftSmoke.mjs [TAG ...] [--neu] [--depth N]
// Default tags: HYPERION TERMINATOR. Prints both breakdowns (from scratch / easy), compares with Coflnet
// /api/craft/profit craftCost where the item is listed there, then prices the most upgraded of the 10 cheapest live HYPERION BIN listings
// with all its modifiers. Recipes come from the bulk file the site ships (app/data/recipes.json, else
// notes/samples/recipes-compact.json), like in the browser; --neu uses the per-item NEU fetch path instead.
// Coflnet responses are cached 10 minutes in notes/cache/smoke_*.json (rate limit ~100/min).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import * as core from './craftCore.js';
import * as mods from './craftModifiers.js';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const CACHE = path.join(HERE, '../notes/cache');
const BULK = [path.join(HERE, '../../Bazaar-Analyzer/app/data/recipes.json'), path.join(HERE, '../notes/samples/recipes-compact.json')].find(p => fs.existsSync(p));
const UA = { 'User-Agent': 'BazaarAnalyzer-dev (craft smoke test)' };
const args = process.argv.slice(2);
const useNeu = args.includes('--neu');
const depthArg = args.indexOf('--depth');
const MAX_DEPTH = depthArg >= 0 ? Number(args[depthArg + 1]) : 3;
const tags = args.filter((a, i) => !a.startsWith('--') && !(depthArg >= 0 && i === depthArg + 1));
if (!tags.length) tags.push('HYPERION', 'TERMINATOR');

let requests = 0;
async function fetchFn(url, init = {}) {
  requests++;
  if (url === 'data/recipes.json') {
    if (!BULK) return new Response('not found', { status: 404 });
    return new Response(fs.readFileSync(BULK), { status: 200, headers: { 'content-type': 'application/json' } });
  }
  return fetch(url, { ...init, headers: { ...(init.headers || {}), ...UA } });
}
async function coflCached(name, url) {
  const f = path.join(CACHE, 'smoke_' + name + '.json');
  try { const st = fs.statSync(f); if (Date.now() - st.mtimeMs < 10 * 60 * 1000) return JSON.parse(fs.readFileSync(f, 'utf8')); } catch { /* no cache */ }
  const r = await fetch(url, { headers: UA });
  if (!r.ok) throw new Error('HTTP ' + r.status + ' ' + url);
  const j = await r.json();
  fs.mkdirSync(CACHE, { recursive: true });
  fs.writeFileSync(f, JSON.stringify(j));
  return j;
}

const fmt = n => (n == null ? 'n/a' : Math.round(n).toLocaleString('en-US'));
const qfmt = q => (Number.isInteger(q) ? String(q) : q.toFixed(3).replace(/\.?0+$/, ''));
function printTree(tree) {
  if (!tree) { console.log('    (no tree)'); return; }
  for (const n of core.flattenTree(tree)) {
    if (n.depth > MAX_DEPTH) continue;
    const line = '    ' + '  '.repeat(n.depth) + `${qfmt(n.qty)} x ${n.id}`;
    console.log(line.padEnd(64) + ` ${n.method.padEnd(11)} ${fmt(n.unitCost).padStart(15)} ${fmt(n.total).padStart(15)}${n.note ? '  (' + n.note + ')' : ''}`);
  }
}
function printLines(lines) {
  for (const l of lines) console.log(('    ' + l.kind + ': ' + l.label).padEnd(56) + ` ${String(l.id).padEnd(26)} x${qfmt(l.qty)} ${l.method.padEnd(11)} ${fmt(l.total).padStart(15)}${l.note ? '  (' + l.note + ')' : ''}`);
}

const ctx = core.createCraftContext({ fetchFn, recipesUrl: useNeu ? null : 'data/recipes.json' });
const t0 = Date.now();
await core.loadPrices(ctx);
console.log(`prices: bazaar ${ctx.bazaar ? ctx.bazaar.size : 0} products, lowest BIN ${ctx.lowestBin ? ctx.lowestBin.size : 0} keys (${Date.now() - t0} ms)` +
  `, recipes: ${useNeu ? 'per-item NEU' : BULK ? path.basename(path.dirname(BULK)) + '/' + path.basename(BULK) : 'none'}`);

let profit = [];
try { profit = await coflCached('craft_profit', 'https://sky.coflnet.com/api/craft/profit'); } catch (e) { console.log('coflnet craft/profit unavailable: ' + e.message); }
const profitBy = new Map((Array.isArray(profit) ? profit : []).map(r => [r.itemId, r]));

for (const tag of tags) {
  const t1 = Date.now();
  const res = await core.cleanCraftCost(tag, ctx);
  console.log(`\n=== ${tag}: craftable ${res.craftable}, ${Date.now() - t1} ms` + (res.truncated ? ', TRUNCATED' : ''));
  if (!res.craftable) continue;
  console.log(`  FROM SCRATCH ${fmt(res.fromScratch.total)}   (tree depth <= ${MAX_DEPTH} shown)`);
  printTree(res.fromScratch);
  console.log(`  EASY WAY ${fmt(res.easy.total)}`);
  printTree(res.easy);
  const p = core.priceItem(tag, 'fromScratch', ctx);
  console.log(`  market: ${fmt(p.unitCost)} (${p.method})`);
  const cp = profitBy.get(tag);
  if (cp) {
    const inside = cp.craftCost >= res.fromScratch.total && cp.craftCost <= res.easy.total;
    console.log(`  coflnet craftCost ${fmt(cp.craftCost)} (sellPrice ${fmt(cp.sellPrice)}): ${inside ? 'between our two variants (expected)' : 'OUTSIDE [fromScratch, easy]'}`);
  } else console.log('  coflnet craft/profit: not listed (it only lists profitable 3x3 crafts)');
  if (res.missing.length) console.log('  missing: ' + res.missing.join(', '));
  if (res.errors && res.errors.length) console.log('  errors: ' + res.errors.join(' | '));
}

// cross-check every item that is both in coflnet craft/profit and in our recipes (sanity, not a test)
if (profitBy.size) {
  let n = 0, inside = 0, below = 0, above = 0;
  for (const [id, cp] of profitBy) {
    if (!(ctx.recipes.get(id) || []).length || !(cp.craftCost > 0)) continue;
    const s = core.craftTree(id, 'fromScratch', ctx), e = core.craftTree(id, 'easy', ctx);
    if (!s || !e || s.total == null || e.total == null) continue;
    n++;
    if (cp.craftCost < s.total * 0.999) below++; else if (cp.craftCost > e.total * 1.001) above++; else inside++;
  }
  console.log(`\ncoflnet craft/profit cross-check: ${n} items, ${inside} between fromScratch and easy, ${below} below fromScratch, ${above} above easy`);
}

// one real listing with modifiers: the most upgraded of the 10 cheapest active HYPERION BINs
try {
  const rows = await coflCached('active_bin_HYPERION', 'https://sky.coflnet.com/api/auctions/tag/HYPERION/active/bin');
  const L = (Array.isArray(rows) ? rows : []).slice().sort((a, b) => (b.enchantments || []).length + Object.keys(b.flatNbt || {}).length - (a.enchantments || []).length - Object.keys(a.flatNbt || {}).length)[0];
  if (!L) throw new Error('no active HYPERION BIN rows');
  const t2 = Date.now();
  const r = await mods.listingCraftCost('HYPERION', L, ctx, core);
  console.log(`\n=== listing ${L.uuid}: ${L.itemName} ${L.tier}, listed ${fmt(L.startingBid)} (${Date.now() - t2} ms)`);
  for (const mode of ['fromScratch', 'easy']) {
    console.log(`  ${mode === 'easy' ? 'EASY WAY' : 'FROM SCRATCH'} total ${fmt(r.totals[mode])} = clean ${fmt(r.base[mode] && r.base[mode].total)} + modifiers:`);
    printLines(r.modifiers[mode]);
  }
  if (r.missing.length) console.log('  missing: ' + r.missing.join(', '));
} catch (e) { console.log('\nlisting smoke failed: ' + (e && e.stack || e)); process.exitCode = 1; }
console.log(`\n${requests} engine requests, ${Date.now() - t0} ms total`);
