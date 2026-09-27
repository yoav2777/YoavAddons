// Shared test helpers: fixture loading, contract-shaped maps, fresh engine modules, a fake fetch.
// Not a test file itself (name does not match *.test.mjs).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { toContractRecipes, toContractBazaar, toContractLowestBin } from '../fixtures/convert.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const FX = path.join(HERE, '../fixtures');
const rd = n => JSON.parse(fs.readFileSync(path.join(FX, n), 'utf8'));

export const EXP = rd('expected.json');            // written by fixtures/reference.mjs (independent of the engine)
export const BZRAW = rd('bazaar.json');             // raw Hypixel /v2/skyblock/bazaar shape (top of book only)
export const LBRAW = rd('lowestbins.json');         // raw lb.tricked.pro shape {key: price}
export const COMPACT = rd('recipes.json');          // bulk recipes file (craft-spec 2b compact format)
export const NEU = rd('neu-items.json');            // raw NEU item JSON per id (per-item loader)
export const HYITEMS = rd('hypixel-items.json');    // raw Hypixel items resource (trimmed)
export const STONES = rd('constants-reforgestones.json');
export const ENCH = rd('constants-enchants.json');
export const LISTING_KEYS = fs.readdirSync(path.join(FX, 'listings')).map(f => f.replace(/\.json$/, '')).sort();
export const listing = k => rd('listings/' + k + '.json');

export const bazaarMap = () => toContractBazaar(BZRAW.products);
export const lbinMap = () => toContractLowestBin(LBRAW);
export const recipeMap = () => toContractRecipes(COMPACT);

export const CORE = path.join(HERE, '../craftCore.js');
export const MODS = path.join(HERE, '../craftModifiers.js');
export const enginePresent = () => fs.existsSync(CORE) && fs.existsSync(MODS);

// Fresh module instance (module-level caches reset) by adding a query string to the URL.
let seq = 0;
export async function freshCore() { return import(pathToFileURL(CORE).href + '?fresh=' + (++seq)); }
export async function freshMods() { return import(pathToFileURL(MODS).href + '?fresh=' + (++seq)); }

// Context fed entirely from fixtures (no network).
export function fixtureCtx(core, extra = {}) {
  return core.createCraftContext({ bazaar: bazaarMap(), lowestBin: lbinMap(), recipes: recipeMap(), fetchFn: failFetch, ...extra });
}
export async function failFetch(url) { throw new Error('network used in an offline test: ' + url); }

// relative closeness (numbers are coins; 1e-6 relative + 0.01 absolute)
export function near(a, b, rel = 1e-6) {
  if (a == null || b == null) return a == b;
  return Math.abs(a - b) <= Math.max(Math.abs(b) * rel, 0.01);
}
export function assertNear(assert, a, b, msg, rel) {
  assert.ok(near(a, b, rel), `${msg}: got ${a}, expected ${b}`);
}

// Walks a Tree; calls fn(node, parent).
export function walk(tree, fn, parent = null) {
  if (!tree) return;
  fn(tree, parent);
  for (const c of tree.children || []) walk(c, fn, tree);
}

// ---------------------------------------------------------------- fake fetch
// Routes every URL the engine may call to fixture data. Records calls and the max number in flight.
// opts.fail = { bazaar|lbin|bulk|neu|cofl|items: status }, opts.delay ms per response, opts.bulk = serve data/recipes.json.
export function fakeFetch(opts = {}) {
  const st = { calls: [], inFlight: 0, maxInFlight: 0 };
  const fail = opts.fail || {};
  const json = (data, status = 200) => status === 204 ? new Response(null, { status }) : new Response(JSON.stringify(data), { status, headers: { 'content-type': 'application/json' } });
  const route = url => {
    const u = String(url);
    if (u === 'https://api.hypixel.net/v2/skyblock/bazaar') return fail.bazaar ? json({ success: false }, fail.bazaar) : json(BZRAW);
    if (u.startsWith('https://lb.tricked.pro/lowestbins')) return fail.lbin ? json({}, fail.lbin) : json(LBRAW);
    if (u.startsWith('https://api.hypixel.net/v2/resources/skyblock/items')) return fail.items ? json({}, fail.items) : json(HYITEMS);
    if (/(^|\/)data\/recipes\.json$/.test(u)) return fail.bulk || opts.bulk === false ? json({}, fail.bulk || 404) : json(COMPACT);
    let m = u.match(/NotEnoughUpdates-REPO(?:\/master|@master)\/items\/(.+)\.json$/);
    if (m) {
      if (fail.neu) return json({}, fail.neu);
      const id = decodeURIComponent(m[1]);
      return NEU[id] ? json(NEU[id]) : json({ message: 'Not Found' }, 404);
    }
    m = u.match(/^https:\/\/sky\.coflnet\.com\/api\/item\/price\/([^/]+)\/bin$/);
    if (m) {
      if (fail.cofl) return json({}, fail.cofl);
      const id = decodeURIComponent(m[1]);
      return typeof LBRAW[id] === 'number' ? json({ lowest: LBRAW[id], secondLowest: LBRAW[id] }) : json(null, 204);
    }
    return json({ message: 'unrouted' }, 404);
  };
  st.fn = async (url, init) => {
    st.calls.push(String(url));
    const signal = init && init.signal;
    if (signal && signal.aborted) { const e = new Error('Aborted'); e.name = 'AbortError'; throw e; }
    st.inFlight++; st.maxInFlight = Math.max(st.maxInFlight, st.inFlight);
    try {
      if (opts.delay) await new Promise(r => setTimeout(r, opts.delay));
      if (fail.network && fail.network(String(url))) throw new TypeError('fetch failed');
      return route(url);
    } finally { st.inFlight--; }
  };
  st.count = re => st.calls.filter(u => re.test(u)).length;
  st.neuIds = () => st.calls.map(u => (u.match(/items\/(.+)\.json$/) || [])[1]).filter(Boolean).map(decodeURIComponent);
  return st;
}

// In-memory localStorage stand-in (node has none).
export function memoryStorage() {
  const m = new Map();
  return { getItem: k => (m.has(k) ? m.get(k) : null), setItem: (k, v) => { m.set(k, String(v)); }, removeItem: k => { m.delete(k); }, get size() { return m.size; } };
}
