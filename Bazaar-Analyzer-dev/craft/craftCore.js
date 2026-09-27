// craftCore.js - clean craft cost engine ("from scratch" and "easy way").
// Plain ES module, no imports. The site patch strips `export` and inlines this file.
// Spec: notes/craft-spec.md. Docs: notes/craft-core.md.
//
// Ids: recipes use NEU ids (HYPERION, LOG-2, SHARPNESS;5, ENDER_DRAGON;4). Bazaar ids (LOG:2,
// ENCHANTMENT_SHARPNESS_5) and lowestbins keys (ENCHANTED_BOOK-SHARPNESS-5, PET-ENDER_DRAGON-LEGENDARY)
// are looked up through the helpers below, so any of these forms can be passed in.

const CC_NEU_RAW = 'https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/';
const CC_NEU_CDN = 'https://cdn.jsdelivr.net/gh/NotEnoughUpdates/NotEnoughUpdates-REPO@master/items/';
const CC_BAZAAR_URL = 'https://api.hypixel.net/v2/skyblock/bazaar';
const CC_LBIN_URL = 'https://lb.tricked.pro/lowestbins';
const CC_COFL_BIN = 'https://sky.coflnet.com/api/item/price/';
const CC_LS_PREFIX = 'ba.craft.r1.';
const CC_TTL = 24 * 3600 * 1000;
const CC_COIN = 'SKYBLOCK_COIN';
const CC_PET_TIERS = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY', 'MYTHIC'];
const CC_TYPES = { c: 'craft', f: 'forge', n: 'npc', t: 'other', k: 'other' };
const CC_NOTES = { t: 'trade', k: 'Kat pet upgrade' };
const CC_DEAD_BO = 0.01;         // top buy order below 1% of insta-buy = dead/lowball order, ignored (craft-core.md)
const CC_LBIN_RETRY = 5 * 60 * 1000;   // after a lowestbins failure, retry it at most this often
const CC_MAX_STACK = 15;         // pricing depth guard (deepest real tree: DIVAN_DRILL = 10)

const CC_MEM = new Map();        // per-item recipes fetched this page load, shared by all contexts
const CC_BULK = new Map();       // bulk recipes url -> Promise<Map|null>
const CC_INFLIGHT = new Map();   // id -> Promise<Recipe[]|null> (in-flight dedupe)
const CC_BIN_INFLIGHT = new Map(); // id -> Promise (Coflnet per-item BIN fallback, shared by all contexts)
let CC_LBIN_P = null;            // in-flight lowestbins download, shared by all contexts

// ---------------------------------------------------------------- ids
function ccNum(v) { const n = typeof v === 'number' ? v : Number(v); return Number.isFinite(n) && n > 0 ? n : null; }

// Any id form -> NEU id (recipe key / NEU file name).
function ccNeuId(id) {
  id = String(id || '').trim().toUpperCase();
  let m = id.match(/^ENCHANTMENT_(.+)_(\d+)$/);
  if (m) return m[1] + ';' + m[2];
  m = id.match(/^([A-Z0-9_]+):(\d+)$/);
  if (m) return m[1] + '-' + m[2];
  return id;
}

// AH tag (+ optional pet tier) -> NEU id. PET_ENDER_DRAGON + LEGENDARY -> ENDER_DRAGON;4.
function ccRecipeId(tag, tier) {
  const t = String(tag || '').trim().toUpperCase();
  if (t.startsWith('PET_') && tier != null) {
    const i = CC_PET_TIERS.indexOf(String(tier).toUpperCase());
    if (i >= 0) return t.slice(4) + ';' + i;
  }
  return ccNeuId(t);
}

function ccBazaarEntry(id, ctx) {
  const bz = ctx.bazaar;
  if (!bz || !bz.size) return null;
  if (bz.has(id)) return bz.get(id);
  let m = id.match(/^([A-Z0-9_]+)-(\d+)$/);
  if (m && bz.has(m[1] + ':' + m[2])) return bz.get(m[1] + ':' + m[2]);
  m = id.match(/^([A-Z0-9_]+);(\d+)$/);
  if (m && bz.has('ENCHANTMENT_' + m[1] + '_' + m[2])) return bz.get('ENCHANTMENT_' + m[1] + '_' + m[2]);
  return null;
}

function ccBazaar(id, ctx) {
  const e = ccBazaarEntry(id, ctx);
  if (!e) return null;
  return { instaBuy: ccNum(e.instaBuy), buyOrder: ccNum(e.buyOrder) };
}

// Lowest BIN; ignored for bazaar products (lowestbins holds their insta-buy, spec 1b).
function ccLbin(id, ctx) {
  const lb = ctx.lowestBin;
  if (!lb || !lb.size || ccBazaarEntry(id, ctx)) return null;
  const keys = [id];
  let m = id.match(/^([A-Z0-9_]+);(\d+)$/);
  if (m) {
    keys.push('ENCHANTED_BOOK-' + m[1] + '-' + m[2]);
    if (CC_PET_TIERS[+m[2]]) keys.push('PET-' + m[1] + '-' + CC_PET_TIERS[+m[2]]);
  }
  m = id.match(/^ENCHANTMENT_(.+)_(\d+)$/);
  if (m) keys.push('ENCHANTED_BOOK-' + m[1] + '-' + m[2]);
  for (const k of keys) { const v = ccNum(lb.get(k)); if (v != null) return v; }
  return null;
}

// ---------------------------------------------------------------- recipe parsing
function ccStack(s) {                 // "ID:count" -> {id, qty}; split on the LAST ':'
  s = String(s || '').trim();
  const i = s.lastIndexOf(':');
  if (i > 0) { const n = Number(s.slice(i + 1)); if (Number.isFinite(n)) return { id: s.slice(0, i), qty: n }; }
  return { id: s, qty: 1 };
}

function ccMakeRecipe(type, output, stacks, extra) {
  const inputs = [], sum = new Map();
  let coins = 0;
  for (const s of stacks) {
    if (!s || !s.id || !(s.qty > 0)) continue;
    const id = ccNeuId(s.id);
    if (id === CC_COIN) { coins += s.qty; continue; }
    sum.set(id, (sum.get(id) || 0) + s.qty);
  }
  for (const [id, qty] of sum) inputs.push({ id, qty });
  const r = { type, output: Number(output) > 0 ? Number(output) : 1, inputs, coins };
  if (extra && extra.note) r.note = extra.note;
  if (extra && extra.duration > 0) r.duration = extra.duration;
  return r;
}

const CC_GRID = ['A1', 'A2', 'A3', 'B1', 'B2', 'B3', 'C1', 'C2', 'C3'];
function ccGrid(g) { return CC_GRID.filter(k => g[k]).map(k => ccStack(g[k])); }

// One NEU item JSON -> Recipe[] producing `id` (npc_shop offers live on NPC files and are not visible here).
function ccParseNeu(id, j) {
  const out = [];
  if (!j || typeof j !== 'object') return out;
  if (j.recipe && typeof j.recipe === 'object') out.push(ccMakeRecipe('craft', j.recipe.count, ccGrid(j.recipe)));
  for (const r of Array.isArray(j.recipes) ? j.recipes : []) {
    if (!r || typeof r !== 'object') continue;
    try {
      if (r.type === 'crafting' && ccNeuId(r.overrideOutputId || id) === id) out.push(ccMakeRecipe('craft', r.count, ccGrid(r)));
      else if (r.type === 'forge' && ccNeuId(r.overrideOutputId || id) === id)
        out.push(ccMakeRecipe('forge', r.count, (r.inputs || []).map(ccStack), { duration: Number(r.duration) || 0 }));
      else if (r.type === 'trade') {
        const o = ccStack(r.result), c = ccStack(r.cost);
        // EMERALD.json trades carry min/max ("65-76 logs for 1 emerald"): use the average count
        const mm = [Number(r.min), Number(r.max)].filter(n => n > 0);
        if (mm.length) c.qty *= mm.reduce((a, b) => a + b) / mm.length;
        if (ccNeuId(o.id) === id) out.push(ccMakeRecipe('other', o.qty, [c], { note: 'trade' }));
      } else if (r.type === 'katgrade' && ccNeuId(r.output) === id) {
        out.push(ccMakeRecipe('other', 1, [{ id: r.input, qty: 1 }, ...(r.items || []).map(ccStack), { id: CC_COIN, qty: Number(r.coins) || 0 }],
          { note: 'Kat pet upgrade', duration: Number(r.time) || 0 }));
      } else if (r.type === 'npc_shop') {
        const o = ccStack(r.result);
        if (ccNeuId(o.id) === id) out.push(ccMakeRecipe('npc', o.qty, (r.cost || []).map(ccStack)));
      }
    } catch (e) { /* odd recipe entry: skip it */ }
  }
  return out.filter(r => r.inputs.length || r.coins > 0);
}

// Bulk file: compact {"ID":[["c",out,[[id,n]...],duration?],...]} (samples/recipes-compact.json)
// or already-parsed {"ID":[Recipe,...]}.
function ccParseBulk(obj) {
  const map = new Map();
  if (!obj || typeof obj !== 'object') return map;
  for (const key of Object.keys(obj)) {
    const list = Array.isArray(obj[key]) ? obj[key] : [];
    const rs = [];
    for (const e of list) {
      try {
        if (Array.isArray(e) && CC_TYPES[e[0]] && Array.isArray(e[2]))
          rs.push(ccMakeRecipe(CC_TYPES[e[0]], e[1], e[2].map(p => ({ id: p[0], qty: Number(p[1]) })), { note: CC_NOTES[e[0]], duration: Number(e[3]) || 0 }));
        else if (e && typeof e === 'object' && Array.isArray(e.inputs))
          rs.push(ccMakeRecipe(e.type || 'craft', e.output, [...e.inputs, { id: CC_COIN, qty: Number(e.coins) || 0 }], e));
      } catch (err) { /* skip */ }
    }
    map.set(ccNeuId(key), rs.filter(r => r.inputs.length || r.coins > 0));
  }
  return map;
}

// ---------------------------------------------------------------- context + loaders
export function createCraftContext({ fetchFn, bazaar, lowestBin, recipes, recipesUrl } = {}) {
  const toMap = v => v instanceof Map ? v : (v && typeof v === 'object' ? new Map(Object.entries(v)) : null);
  const rec = new Map();
  if (recipes) for (const [k, v] of toMap(recipes)) rec.set(ccNeuId(k), Array.isArray(v) ? v : []);
  return {
    fetchFn: fetchFn || ((...a) => globalThis.fetch(...a)),
    bazaar: toMap(bazaar),
    lowestBin: toMap(lowestBin),
    recipes: rec,
    // preloaded recipes are taken as complete (missing id = no recipe): tests never hit the network
    recipesComplete: !!recipes,
    // bulk recipes file shipped with the site (relative to index.html); null = don't try
    recipesUrl: recipesUrl === undefined ? 'data/recipes.json' : recipesUrl,
    lowestBinFailed: false,
    errors: [],
    _memo: null,
  };
}

function ccAbortErr() { const e = new Error('Aborted'); e.name = 'AbortError'; return e; }

async function ccJson(ctx, url, signal) {
  const r = await ctx.fetchFn(url, signal ? { signal } : undefined);
  if (r.status === 404 || r.status === 204) return { status: r.status, data: null };
  if (!r.ok) throw new Error('HTTP ' + r.status + ' for ' + url);
  return { status: r.status, data: await r.json() };
}

function ccErr(ctx, msg) { if (!ctx.errors.includes(msg) && ctx.errors.length < 50) ctx.errors.push(msg); }
// drops errors starting with prefix (the thing they were about worked this time)
function ccErrClear(ctx, prefix) { if (ctx.errors.some(e => e.startsWith(prefix))) ctx.errors = ctx.errors.filter(e => !e.startsWith(prefix)); }
// lowestbins missing, or failed long enough ago to try again
function ccNeedBins(ctx) { return ctx.lowestBin == null || (!!ctx.lowestBinFailed && !(Date.now() - (ctx.lowestBinFailedAt || 0) < CC_LBIN_RETRY)); }

// Fills ctx.bazaar / ctx.lowestBin when missing. Failures go to ctx.errors (never throws except on abort).
// Concurrent callers on one ctx share one download (ctx._pricesP); it runs without a signal so one caller
// aborting cannot fail the others, and each caller checks its own signal afterwards.
export async function loadPrices(ctx, { signal } = {}) {
  if (signal && signal.aborted) throw ccAbortErr();
  if (!ctx._pricesP) ctx._pricesP = ccLoadPrices(ctx).finally(() => { ctx._pricesP = null; });
  await ctx._pricesP;
  if (signal && signal.aborted) throw ccAbortErr();
}

async function ccLoadPrices(ctx) {
  const jobs = [];
  if (ctx.bazaar == null) jobs.push((async () => {
    try {
      const { data } = await ccJson(ctx, CC_BAZAAR_URL);
      const m = new Map();
      for (const [id, p] of Object.entries((data && data.products) || {})) {
        m.set(id, {
          instaBuy: ccNum(p && p.buy_summary && p.buy_summary[0] && p.buy_summary[0].pricePerUnit),   // cheapest sell offer
          buyOrder: ccNum(p && p.sell_summary && p.sell_summary[0] && p.sell_summary[0].pricePerUnit), // top buy order
        });
      }
      if (!m.size) throw new Error('empty bazaar response');
      ctx.bazaar = m;
      ccErrClear(ctx, 'Bazaar prices:');
    } catch (e) { if (e && e.name === 'AbortError') throw e; ccErr(ctx, 'Bazaar prices: ' + (e && e.message)); }
  })());
  if (ccNeedBins(ctx)) jobs.push((async () => {
    try {
      // one download for every context on the page (the site builds a new ctx on each bazaar poll)
      if (!CC_LBIN_P) CC_LBIN_P = (async () => {
        const { data } = await ccJson(ctx, CC_LBIN_URL);
        const m = new Map();
        for (const [k, v] of Object.entries(data || {})) { const n = ccNum(v); if (n != null) m.set(k, n); }
        if (!m.size) throw new Error('empty lowest BIN response');
        return m;
      })().finally(() => { CC_LBIN_P = null; });
      ctx.lowestBin = await CC_LBIN_P; ctx.lowestBinFailed = false; ctx.lowestBinFailedAt = 0;
      ccErrClear(ctx, 'Lowest BIN list:');
    } catch (e) {
      if (e && e.name === 'AbortError') throw e;
      // keep what the Coflnet fallback already found (and its recorded misses)
      ctx.lowestBin = ctx.lowestBin || new Map(); ctx.lowestBinFailed = true; ctx.lowestBinFailedAt = Date.now();
      ccErr(ctx, 'Lowest BIN list: ' + (e && e.message));
    }
  })());
  await Promise.all(jobs);
  ctx._memo = null;
}

// Fallback when lowestbins is down: Coflnet per-item BIN for up to 20 non-bazaar ids, 300 ms apart.
// Each id is asked once: a miss is stored as 0 (ccNum ignores it) in the same Map, which the site carries over to
// every new ctx; a request already running for another caller is awaited instead of repeated.
async function ccFillBins(ctx, ids, signal) {
  const lb = ctx.lowestBin;
  const todo = ids.filter(id => /^[A-Z0-9_]+$/.test(id) && !id.startsWith('SKYBLOCK_') && !ccBazaarEntry(id, ctx) && !lb.has(id)).slice(0, 20);
  let sent = 0;
  for (const id of todo) {
    if (signal && signal.aborted) throw ccAbortErr();
    let p = CC_BIN_INFLIGHT.get(id);
    if (!p) {
      if (sent++) await new Promise(res => setTimeout(res, 300));
      if (lb.has(id)) continue;
      if ((p = CC_BIN_INFLIGHT.get(id)) == null) {
        // no signal: other callers may be waiting on this request
        p = ccJson(ctx, CC_COFL_BIN + encodeURIComponent(id) + '/bin')
          .then(({ data }) => { lb.set(id, ccNum(data && data.lowest) || 0); }, () => { /* network error: not recorded, retried next time */ })
          .finally(() => CC_BIN_INFLIGHT.delete(id));
        CC_BIN_INFLIGHT.set(id, p);
      }
    }
    await p;
  }
  if (signal && signal.aborted) throw ccAbortErr();
  ctx._memo = null;
}

function ccLsGet(id) {
  try {
    const s = globalThis.localStorage && globalThis.localStorage.getItem(CC_LS_PREFIX + id);
    if (!s) return null;
    const o = JSON.parse(s);
    if (!o || !Array.isArray(o.r) || !(Date.now() - o.t < CC_TTL)) return null;
    return o.r;
  } catch (e) { return null; }
}
function ccLsSet(id, r) {
  try { globalThis.localStorage && globalThis.localStorage.setItem(CC_LS_PREFIX + id, JSON.stringify({ t: Date.now(), r })); } catch (e) { /* full / blocked */ }
}

async function ccEnsureBulk(ctx, signal) {
  if (ctx.recipesComplete || !ctx.recipesUrl || ctx._bulkFailed) return;
  const url = ctx.recipesUrl;
  if (!CC_BULK.has(url)) {
    // shared by every caller, so it gets no signal (one caller aborting must not fail the others)
    CC_BULK.set(url, (async () => {
      try {
        const { data } = await ccJson(ctx, url);
        const m = ccParseBulk(data);
        return m.size ? m : null;
      } catch (e) { return null; }
    })());
  }
  const m = await CC_BULK.get(url);
  if (signal && signal.aborted) throw ccAbortErr();
  if (!m) { CC_BULK.delete(url); ctx._bulkFailed = true; return; }
  for (const [k, v] of m) if (!ctx.recipes.has(k)) ctx.recipes.set(k, v);
  ctx.recipesComplete = true;
  ctx._memo = null;
}

// Per-item NEU fetch (raw GitHub, then jsDelivr mirror). Returns Recipe[]; null on network failure.
async function ccFetchNeu(id, ctx, signal) {
  const file = encodeURIComponent(id) + '.json';
  let lastErr = null;
  for (const base of [CC_NEU_RAW, CC_NEU_CDN]) {
    try {
      const { data } = await ccJson(ctx, base + file, signal);
      return data ? ccParseNeu(id, data) : [];
    } catch (e) { if (e && e.name === 'AbortError') throw e; lastErr = e; }
  }
  ccErr(ctx, 'Recipe ' + id + ': ' + (lastErr && lastErr.message));
  return null;
}

async function ccGetRecipes(id, ctx, signal) {
  if (ctx.recipes.has(id)) return ctx.recipes.get(id);
  if (ctx.recipesComplete) return [];
  let rs = CC_MEM.get(id);
  if (rs === undefined) rs = ccLsGet(id);
  if (rs == null) {
    let p = CC_INFLIGHT.get(id);
    if (!p) {
      p = ccFetchNeu(id, ctx, signal).finally(() => CC_INFLIGHT.delete(id));
      CC_INFLIGHT.set(id, p);
    }
    try { rs = await p; } catch (e) {
      // someone else's request was aborted: retry with our own signal
      if (!(e && e.name === 'AbortError') || (signal && signal.aborted)) throw e;
      rs = await ccFetchNeu(id, ctx, signal);
    }
    if (rs == null) return [];                // network failure: not cached, retried next time
    ccLsSet(id, rs);
  }
  CC_MEM.set(id, rs);
  ccErrClear(ctx, 'Recipe ' + id + ':');
  ctx.recipes.set(id, rs);
  ctx._memo = null;
  return rs;
}

// Walks the recipe graph from tag (BFS) and loads every reachable recipe into ctx.recipes.
// opts.tier: pet tier for PET_* tags. Returns { root, ids, truncated }.
export async function loadRecipeTree(tag, ctx, { signal, maxNodes = 400, maxDepth = 12, tier, concurrency = 6 } = {}) {
  if (signal && signal.aborted) throw ccAbortErr();
  const root = ccRecipeId(tag, tier);
  await ccEnsureBulk(ctx, signal);
  const seen = new Set([root]);
  let level = [root], truncated = false;
  for (let depth = 0; level.length; depth++) {
    const next = [];
    let i = 0;
    const worker = async () => {
      while (i < level.length) {
        if (signal && signal.aborted) throw ccAbortErr();
        const id = level[i++];
        const rs = await ccGetRecipes(id, ctx, signal);
        if (depth >= maxDepth) { if (rs.length) truncated = true; continue; }
        for (const r of rs) for (const inp of (r && Array.isArray(r.inputs) ? r.inputs : [])) {
          if (!inp || typeof inp.id !== 'string' || !inp.id || seen.has(inp.id) || inp.id.startsWith('SKYBLOCK_')) continue;
          if (seen.size >= maxNodes) { truncated = true; continue; }
          seen.add(inp.id); next.push(inp.id);
        }
      }
    };
    await Promise.all(Array.from({ length: Math.min(concurrency, level.length) }, worker));
    level = next;
  }
  return { root, ids: [...seen], truncated };
}

// ---------------------------------------------------------------- pricing (sync, never throws)
// NEU 'trade' entries for enchant books (ENDER_SLAYER;6, DRAGON_HUNTER;1 = 50 enchanted emeralds) are not open
// trades (the book's market price is ~10x higher), so they are ignored for books.
function ccIsBook(id, ctx) {
  const m = id.match(/^([A-Z0-9_]+);(\d+)$/);
  if (!m) return false;
  return !!((ctx.bazaar && ctx.bazaar.has('ENCHANTMENT_' + m[1] + '_' + m[2])) || (ctx.lowestBin && ctx.lowestBin.has('ENCHANTED_BOOK-' + m[1] + '-' + m[2])));
}
function ccRecipesOf(id, ctx) {
  const r = ctx.recipes && (ctx.recipes.get(id) || ctx.recipes.get(ccNeuId(id)));
  if (!Array.isArray(r)) return [];
  const book = r.some(x => x && x.note === 'trade') && ccIsBook(ccNeuId(id), ctx);
  return r.filter(x => x && typeof x === 'object' && !(book && x.note === 'trade'));
}

function ccMemo(ctx) {
  const m = ctx._memo;
  if (m && m.b === ctx.bazaar && m.l === ctx.lowestBin && m.r === ctx.recipes) return m;
  return (ctx._memo = { b: ctx.bazaar, l: ctx.lowestBin, r: ctx.recipes, scratch: new Map() });
}

function ccMethod(r) { return r.type === 'forge' ? 'forge' : r.type === 'npc' ? 'npc' : 'craft'; }

// Cost of one recipe: { unit, cut, bad } ; unit null = unusable. price(id, stack) -> { unit, cut }.
function ccRecipeCost(r, stack, price) {
  const coins = Number((r && r.coins) || 0), inputs = r && Array.isArray(r.inputs) ? r.inputs : [];
  if (!(coins >= 0) || !Number.isFinite(coins) || (!inputs.length && !coins)) return { unit: null, cut: false, bad: 'malformed recipe' };
  let total = coins, cut = false;
  for (const inp of inputs) {
    if (!inp || typeof inp.id !== 'string' || !inp.id || !(inp.qty > 0) || !Number.isFinite(inp.qty)) return { unit: null, cut, bad: 'malformed recipe' };
    if (inp.id.startsWith('SKYBLOCK_')) return { unit: null, cut, bad: inp.id };   // bits, copper, motes...
    if (stack.includes(inp.id)) return { unit: null, cut: true, bad: inp.id };     // cycle -> recipe invalid here
    const c = price(inp.id, stack);
    if (c.cut) cut = true;
    if (c.unit == null) return { unit: null, cut, bad: inp.id };
    total += c.unit * inp.qty;
  }
  return { unit: total / (r.output > 0 ? r.output : 1), cut };
}

// From scratch: cheapest of buy order / lowest BIN / any recipe, recursively.
// Returns { unit, method, note, recipe, cut }. Results not affected by a cycle cut are memoized.
function ccScratch(id, stack, ctx) {
  const memo = ccMemo(ctx).scratch;
  if (id === CC_COIN) return { unit: 1, method: 'coins', cut: false };
  if (memo.has(id)) return memo.get(id);
  let best = null, cut = false;
  const consider = c => { if (c.unit != null && (!best || c.unit < best.unit)) best = c; };   // strict: earlier wins ties
  const b = ccBazaar(id, ctx);
  if (b && b.buyOrder != null && !(b.instaBuy != null && b.buyOrder < b.instaBuy * CC_DEAD_BO)) consider({ unit: b.buyOrder, method: 'buyOrder' });
  else if (b && b.instaBuy != null) consider({ unit: b.instaBuy, method: 'instaBuy', note: b.buyOrder != null ? 'buy order too low, using insta-buy' : 'no buy orders' });
  consider({ unit: ccLbin(id, ctx), method: 'lowestBin' });
  if (id.startsWith('SKYBLOCK_')) { /* pseudo-currency: no price */ }
  else if (stack.length >= CC_MAX_STACK) cut = true;
  else {
    const st = stack.concat(id);
    for (const r of ccRecipesOf(id, ctx)) {
      const rc = ccRecipeCost(r, st, (x, s) => ccScratch(x, s, ctx));
      if (rc.cut) cut = true;
      consider({ unit: rc.unit, method: ccMethod(r), recipe: r, note: r.note });
    }
  }
  const res = best ? Object.assign(best, { cut }) : { unit: null, method: 'unavailable', cut, note: ccRecipesOf(id, ctx).length ? 'no priceable route' : 'no market price and no recipe' };
  if (!cut) memo.set(id, res);
  return res;
}

// Easy: insta-buy / lowest BIN / NPC coins-only shop; else from-scratch cost, flagged.
// stack = ids being crafted above this one (the tree root), so a fallback never loops back through them.
function ccEasy(id, ctx, stack = []) {
  if (id === CC_COIN) return { unit: 1, method: 'coins' };
  let best = null;
  const consider = c => { if (c.unit != null && (!best || c.unit < best.unit)) best = c; };
  const b = ccBazaar(id, ctx);
  if (b) consider({ unit: b.instaBuy, method: 'instaBuy' });
  consider({ unit: ccLbin(id, ctx), method: 'lowestBin' });
  for (const r of ccRecipesOf(id, ctx))
    if (r.type === 'npc' && !(r.inputs || []).length && r.coins > 0) consider({ unit: r.coins / (r.output || 1), method: 'npc', recipe: r });
  if (best) return best;
  const s = ccScratch(id, stack, ctx);
  if (s.unit == null) return { unit: null, method: 'unavailable', note: "can't be bought" + (s.note ? ' (' + s.note + ')' : '') };
  return Object.assign({}, s, { note: "can't be bought, " + s.method + ' from scratch' + (s.note ? ' (' + s.note + ')' : ''), fallback: true });
}

function ccClean(v) { return v == null || !Number.isFinite(v) ? null : v; }

export function priceItem(id, mode, ctx) {
  try {
    const nid = ccNeuId(id);
    if (!nid) return { unitCost: null, method: 'unavailable', note: 'no id' };
    const r = mode === 'easy' ? ccEasy(nid, ctx) : ccScratch(nid, [], ctx);
    const out = { unitCost: ccClean(r.unit), method: r.unit == null ? 'unavailable' : r.method };
    if (r.note) out.note = r.note;
    return out;
  } catch (e) {
    return { unitCost: null, method: 'unavailable', note: 'error: ' + (e && e.message) };
  }
}

// ---------------------------------------------------------------- trees
function ccNode(id, qty, r, children) {
  const unit = ccClean(r.unit);
  const n = { id, qty, unitCost: unit, total: unit == null ? null : unit * qty, method: unit == null ? 'unavailable' : r.method };
  const notes = [];
  if (r.note) notes.push(r.note);
  if (r.recipe && r.recipe.output > 1) notes.push('recipe makes ' + r.recipe.output);
  if (r.recipe && r.recipe.duration > 0) notes.push(ccDur(r.recipe.duration));
  if (notes.length) n.note = notes.join('; ');
  if (children) n.children = children;
  return n;
}
function ccDur(s) { return s >= 3600 ? 'takes ' + Math.round(s / 360) / 10 + 'h' : s >= 60 ? 'takes ' + Math.round(s / 60) + 'm' : 'takes ' + s + 's'; }

// Children of one recipe for `qty` outputs. child(id, stack) -> Tree.
function ccChildren(r, qty, stack, child) {
  const k = qty / (r.output > 0 ? r.output : 1), kids = [];
  for (const inp of Array.isArray(r.inputs) ? r.inputs : []) {
    if (!inp || typeof inp.id !== 'string' || !(inp.qty > 0)) { kids.push({ id: String((inp && inp.id) || '?'), qty: 0, unitCost: null, total: null, method: 'unavailable', note: 'malformed recipe entry' }); continue; }
    kids.push(child(inp.id, inp.qty * k, stack));
  }
  if (r.coins > 0 && Number.isFinite(r.coins)) kids.push({ id: CC_COIN, qty: r.coins * k, unitCost: 1, total: r.coins * k, method: 'coins' });
  return kids;
}

function ccScratchTree(id, qty, stack, ctx, nodes) {
  if (id.startsWith('SKYBLOCK_')) return ccNode(id, qty, { unit: null, note: 'not buyable with coins' });
  if (stack.includes(id)) return ccNode(id, qty, { unit: null, note: 'recipe loop' });
  const r = ccScratch(id, stack, ctx);
  if (!r.recipe || r.unit == null || nodes.n++ > 2000) return ccNode(id, qty, r);
  const st = stack.concat(id);
  return ccNode(id, qty, r, ccChildren(r.recipe, qty, st, (x, q, s) => ccScratchTree(x, q, s, ctx, nodes)));
}

// Picks the cheapest recipe for the root (forced craft) under the given ingredient pricing.
function ccRootRecipe(root, ctx, price) {
  let best = null, first = null;
  for (const r of ccRecipesOf(root, ctx)) {
    const rc = ccRecipeCost(r, [root], price);
    if (!first) first = { r, rc };
    if (rc.unit != null && (!best || rc.unit < best.rc.unit)) best = { r, rc };
  }
  return best || first;   // first = unpriceable, still shown so the user sees which leaf failed
}

export function craftTree(tag, mode, ctx) {
  try {
    const root = ccNeuId(tag);
    const nodes = { n: 0 };
    if (mode === 'easy') {
      const pick = ccRootRecipe(root, ctx, x => ccEasy(x, ctx, [root]));
      if (!pick) return null;
      const kids = ccChildren(pick.r, 1, [root], (x, q) => {
        if (x.startsWith('SKYBLOCK_')) return ccNode(x, q, { unit: null, note: 'not buyable with coins' });
        if (x === root) return ccNode(x, q, { unit: null, note: 'recipe loop' });
        const e = ccEasy(x, ctx, [root]);
        if (e.fallback) {            // not buyable: show its from-scratch subtree
          const t = ccScratchTree(x, q, [root], ctx, nodes);
          t.note = e.note + (t.note && t.note !== e.note ? '; ' + t.note : '');
          return t;
        }
        return ccNode(x, q, e);
      });
      return ccNode(root, 1, { unit: pick.rc.unit, method: ccMethod(pick.r), recipe: pick.r, note: pick.rc.unit == null ? "can't be priced: " + pick.rc.bad : pick.r.note }, kids);
    }
    const pick = ccRootRecipe(root, ctx, (x, s) => ccScratch(x, s, ctx));
    if (!pick) return null;
    const kids = ccChildren(pick.r, 1, [root], (x, q, s) => ccScratchTree(x, q, s, ctx, nodes));
    return ccNode(root, 1, { unit: pick.rc.unit, method: ccMethod(pick.r), recipe: pick.r, note: pick.rc.unit == null ? "can't be priced: " + pick.rc.bad : pick.r.note }, kids);
  } catch (e) {
    return { id: String(tag), qty: 1, unitCost: null, total: null, method: 'unavailable', note: 'error: ' + (e && e.message) };
  }
}

export function flattenTree(tree) {
  const out = [];
  const walk = (n, depth) => {
    if (!n) return;
    const { children, ...rest } = n;
    out.push({ depth, ...rest });
    if (Array.isArray(children)) for (const c of children) walk(c, depth + 1);
  };
  walk(tree, 0);
  return out;
}

function ccMissing(tree, set) {
  for (const n of flattenTree(tree)) if (n.method === 'unavailable' && n.depth > 0) set.add(n.id);
}

// opts: { signal, tier (pet tier for PET_* tags), maxNodes, maxDepth }. Rejects only on abort.
export async function cleanCraftCost(tag, ctx, opts = {}) {
  const root = ccRecipeId(tag, opts.tier);
  const needPrices = ctx.bazaar == null || ccNeedBins(ctx);
  const [, tree] = await Promise.all([
    needPrices ? loadPrices(ctx, opts) : null,
    loadRecipeTree(root, ctx, opts),
  ]);
  if (ctx.lowestBinFailed) await ccFillBins(ctx, tree.ids, opts.signal);
  const errors = ccErrorsFor(ctx, tree.ids);
  const craftable = ccRecipesOf(root, ctx).length > 0;
  if (!craftable) return { fromScratch: null, easy: null, craftable: false, missing: [], truncated: tree.truncated, errors };
  const fromScratch = craftTree(root, 'fromScratch', ctx);
  const easy = craftTree(root, 'easy', ctx);
  const miss = new Set();
  ccMissing(fromScratch, miss); ccMissing(easy, miss);
  return { fromScratch, easy, craftable: true, missing: [...miss], truncated: tree.truncated, errors };
}

// Errors that matter for this item: recipe errors only for ids in its tree, price-list errors only while that
// list is still missing (ctx.errors is shared by every item on the page).
function ccErrorsFor(ctx, ids) {
  const set = new Set(ids);
  return ctx.errors.filter(e => {
    const m = /^Recipe (\S+):/.exec(e);
    if (m) return set.has(m[1]);
    if (e.startsWith('Lowest BIN list:')) return !!ctx.lowestBinFailed;
    if (e.startsWith('Bazaar prices:')) return !(ctx.bazaar && ctx.bazaar.size);
    return true;
  });
}
