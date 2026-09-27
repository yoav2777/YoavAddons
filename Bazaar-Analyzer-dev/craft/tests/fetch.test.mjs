// craftCore.js loaders fed by a fake fetchFn that serves the recorded fixtures. Counts requests:
// bulk path = 3 requests, per-item NEU path dedupes, respects concurrency / maxNodes / maxDepth, caches, aborts.
// Every test imports a FRESH module instance so the module-level caches start empty.
import test from 'node:test';
import assert from 'node:assert/strict';
import { EXP, NEU, LBRAW, enginePresent, freshCore, fakeFetch, assertNear, memoryStorage } from './fx.mjs';

const skip = enginePresent() ? false : 'craftCore.js not written yet';
const NEU_RE = /NotEnoughUpdates-REPO/;
const dupes = list => list.filter((x, i) => list.indexOf(x) !== i);

// ids reachable from root through the raw NEU files (grid recipes + recipes[] crafting/forge/trade/katgrade), my own BFS.
function neuClosure(root) {
  const seen = new Set([root]), q = [root];
  const ids = s => { const i = String(s).lastIndexOf(':'); return i > 0 && Number.isFinite(Number(String(s).slice(i + 1))) ? String(s).slice(0, i) : String(s); };
  const grid = g => ['A1', 'A2', 'A3', 'B1', 'B2', 'B3', 'C1', 'C2', 'C3'].filter(k => g[k]).map(k => ids(g[k]));
  while (q.length) {
    const j = NEU[q.shift()]; if (!j) continue;
    const ins = [];
    if (j.recipe) ins.push(...grid(j.recipe));
    for (const r of j.recipes || []) {
      if (r.type === 'crafting') ins.push(...grid(r));
      else if (r.type === 'forge') ins.push(...r.inputs.map(ids));
    }
    for (const i of ins) if (!i.startsWith('SKYBLOCK_') && !seen.has(i)) { seen.add(i); q.push(i); }
  }
  return seen;
}

test('bulk path: bazaar + lowestbins + data/recipes.json = 3 requests, no per-item fetches, reference totals', { skip }, async () => {
  const core = await freshCore();
  const ff = fakeFetch();
  const ctx = core.createCraftContext({ fetchFn: ff.fn });
  const h = await core.cleanCraftCost('HYPERION', ctx);
  const t = await core.cleanCraftCost('TERMINATOR', ctx);
  assert.equal(ff.count(/api\.hypixel\.net\/v2\/skyblock\/bazaar/), 1, 'bazaar once: ' + ff.calls);
  assert.equal(ff.count(/lb\.tricked\.pro/), 1, 'lowestbins once');
  assert.equal(ff.count(/data\/recipes\.json$/), 1, 'bulk recipes once');
  assert.equal(ff.count(NEU_RE), 0, 'no per-item NEU requests');
  assert.equal(ff.calls.length, 3, ff.calls.join('\n'));
  for (const [id, r] of [['HYPERION', h], ['TERMINATOR', t]]) {
    assertNear(assert, r.fromScratch.total, EXP.roots[id].fromScratch.unitCost, id + ' fromScratch');
    assertNear(assert, r.easy.total, EXP.roots[id].easy.unitCost, id + ' easy');
  }
  // new context on the same page: bulk file parsed once per page (prices are per ctx)
  const ff2 = fakeFetch();
  const ctx2 = core.createCraftContext({ fetchFn: ff2.fn });
  await core.cleanCraftCost('DIVAN_DRILL', ctx2);
  assert.equal(ff2.count(/data\/recipes\.json$/), 0, 'bulk file reused across contexts');
  assert.equal(ff2.count(NEU_RE), 0);
});

test('concurrent cleanCraftCost calls on one ctx share the price requests (no double bazaar download)', { skip }, async () => {
  const core = await freshCore();
  const ff = fakeFetch({ delay: 5 });
  const ctx = core.createCraftContext({ fetchFn: ff.fn });
  await Promise.all([core.cleanCraftCost('HYPERION', ctx), core.cleanCraftCost('TERMINATOR', ctx)]);
  assert.equal(ff.count(/data\/recipes\.json$/), 1, 'bulk recipes once');
  assert.equal(ff.count(/api\.hypixel\.net\/v2\/skyblock\/bazaar/), 1, 'bazaar (3.6 MB) once');
  assert.equal(ff.count(/lb\.tricked\.pro/), 1, 'lowestbins once');
});

test('per-item NEU path: every id fetched at most once, only reachable ids, HYPERION matches reference', { skip }, async () => {
  const core = await freshCore();
  const ff = fakeFetch({ delay: 2 });
  const ctx = core.createCraftContext({ fetchFn: ff.fn, recipesUrl: null });
  // two concurrent requests for the same tree + one overlapping tree share the in-flight fetches
  const [a, b] = await Promise.all([core.cleanCraftCost('HYPERION', ctx), core.cleanCraftCost('HYPERION', ctx), core.loadRecipeTree('NECRON_BLADE', ctx)]);
  const ids = ff.neuIds();
  assert.deepEqual(dupes(ids), [], 'duplicate NEU requests');
  const reach = neuClosure('HYPERION');
  for (const id of reach) assert.ok(ids.includes(id), 'not fetched: ' + id);
  for (const id of ids) assert.ok(reach.has(id), 'fetched but not reachable: ' + id);
  assert.equal(ff.count(/data\/recipes\.json/), 0, 'recipesUrl null -> no bulk request');
  assertNear(assert, a.fromScratch.total, EXP.roots.HYPERION.fromScratch.unitCost, 'HYPERION fromScratch (no NPC routes needed)');
  assertNear(assert, b.easy.total, EXP.roots.HYPERION.easy.unitCost, 'HYPERION easy');
  // second context, same page: memory cache, zero NEU requests
  const before = ff.count(NEU_RE);
  const ctx2 = core.createCraftContext({ fetchFn: ff.fn, recipesUrl: null, bazaar: ctx.bazaar, lowestBin: ctx.lowestBin });
  const c = await core.cleanCraftCost('HYPERION', ctx2);
  assert.equal(ff.count(NEU_RE), before, 'memory cache');
  assert.equal(c.fromScratch.total, a.fromScratch.total);
});

test('per-item path: concurrency <= 6 in flight, forge tree loads fully', { skip }, async () => {
  const core = await freshCore();
  const ff = fakeFetch({ delay: 5 });
  const ctx = core.createCraftContext({ fetchFn: ff.fn, recipesUrl: null, bazaar: new Map(), lowestBin: new Map() });
  const res = await core.loadRecipeTree('DIVAN_DRILL', ctx);
  const ids = ff.neuIds();
  assert.ok(ff.maxInFlight <= 6, 'max in flight ' + ff.maxInFlight);
  assert.ok(ff.maxInFlight >= 2, 'requests should run in parallel, max in flight ' + ff.maxInFlight);
  assert.deepEqual(dupes(ids), []);
  const reach = neuClosure('DIVAN_DRILL');
  assert.ok(reach.size > 30, 'fixture premise: big tree ' + reach.size);
  for (const id of reach) assert.ok(ids.includes(id), 'not fetched: ' + id);
  assert.equal(res.truncated, false);
  const d = ctx.recipes.get('DIVAN_DRILL');
  assert.equal(d.length, 1); assert.equal(d[0].type, 'forge'); assert.equal(d[0].coins, 50000000);
  assert.deepEqual(d[0].inputs.map(i => i.id).sort(), ['DIVAN_ALLOY', 'TITANIUM_DRILL_4']);
});

test('per-item path: maxNodes and maxDepth caps are respected', { skip }, async () => {
  let core = await freshCore();
  let ff = fakeFetch();
  let ctx = core.createCraftContext({ fetchFn: ff.fn, recipesUrl: null, bazaar: new Map(), lowestBin: new Map() });
  let r = await core.loadRecipeTree('DIVAN_DRILL', ctx, { maxNodes: 10 });
  assert.ok(ff.count(NEU_RE) <= 10, 'requests ' + ff.count(NEU_RE));
  assert.equal(r.truncated, true);

  core = await freshCore();
  ff = fakeFetch();
  ctx = core.createCraftContext({ fetchFn: ff.fn, recipesUrl: null, bazaar: new Map(), lowestBin: new Map() });
  r = await core.loadRecipeTree('DIVAN_DRILL', ctx, { maxDepth: 1 });
  // depth 0 = DIVAN_DRILL, depth 1 = its direct inputs (TITANIUM_DRILL_4, DIVAN_ALLOY); nothing deeper
  assert.deepEqual(ff.neuIds().sort(), ['DIVAN_ALLOY', 'DIVAN_DRILL', 'TITANIUM_DRILL_4']);
  assert.equal(r.truncated, true);
});

test('per-item path: 404 = no recipe (cached), network failure is reported and retried, never throws', { skip }, async () => {
  const core = await freshCore();
  const ff = fakeFetch({ fail: { network: u => /items\/NECRON_BLADE\.json$/.test(u) } });
  const ctx = core.createCraftContext({ fetchFn: ff.fn, recipesUrl: null });
  const r = await core.cleanCraftCost('HYPERION', ctx);
  assert.ok(ctx.errors.some(e => /NECRON_BLADE/.test(e)), JSON.stringify(ctx.errors));
  assert.ok(r.fromScratch, 'still a tree'); // blade has no recipe now -> bought at lbin
  assert.equal(r.fromScratch.children.find(c => c.id === 'NECRON_BLADE').method, 'lowestBin');
  const blade = ff.count(/items\/NECRON_BLADE\.json$/);
  assert.ok(blade >= 1 && blade <= 2, 'raw + mirror at most: ' + blade);
  // a NEU 404 (item has no file) is not retried on the next call; the network failure is
  const ff2Calls = ff.calls.length;
  await core.loadRecipeTree('HYPERION', ctx);
  const again = ff.calls.slice(ff2Calls);
  assert.ok(again.some(u => /NECRON_BLADE/.test(u)), 'network failure retried');
  assert.ok(!again.some(u => /GIANT_FRAGMENT_LASER/.test(u)), 'fetched ids not refetched');
});

test('bulk recipes 404 -> falls back to per-item NEU', { skip }, async () => {
  const core = await freshCore();
  const ff = fakeFetch({ bulk: false });
  const ctx = core.createCraftContext({ fetchFn: ff.fn });
  const r = await core.cleanCraftCost('HYPERION', ctx);
  assert.equal(ff.count(/data\/recipes\.json$/), 1);
  assert.ok(ff.count(NEU_RE) >= 5);
  assertNear(assert, r.fromScratch.total, EXP.roots.HYPERION.fromScratch.unitCost, 'HYPERION');
});

test('lowestbins down (502): Coflnet per-item BIN fallback, capped at 20, each id once', { skip }, async () => {
  const core = await freshCore();
  const ff = fakeFetch({ fail: { lbin: 502 } });
  const ctx = core.createCraftContext({ fetchFn: ff.fn });
  const r = await core.cleanCraftCost('HYPERION', ctx);
  const cofl = ff.calls.filter(u => /sky\.coflnet\.com/.test(u));
  assert.ok(cofl.length >= 1 && cofl.length <= 20, 'coflnet calls ' + cofl.length);
  assert.deepEqual(dupes(cofl), []);
  assert.ok(cofl.some(u => /NECRON_HANDLE/.test(u)));
  assert.ok(!cofl.some(u => /GIANT_FRAGMENT_LASER|WITHER_CATALYST/.test(u)), 'bazaar products must not be looked up');
  assert.ok(ctx.errors.some(e => /lowest bin/i.test(e)));
  assertNear(assert, r.fromScratch.total, EXP.roots.HYPERION.fromScratch.unitCost, 'HYPERION via fallback prices');
  assert.equal(LBRAW.NECRON_HANDLE, r.fromScratch.children.find(c => c.id === 'NECRON_BLADE').children.find(c => c.id === 'NECRON_HANDLE').unitCost);
});

test('bazaar down (500): no throw, error reported, bazaar ids fall back to the lowestbins list or unavailable', { skip }, async () => {
  const core = await freshCore();
  const ff = fakeFetch({ fail: { bazaar: 500 } });
  const ctx = core.createCraftContext({ fetchFn: ff.fn });
  const r = await core.cleanCraftCost('HYPERION', ctx);
  assert.ok(ctx.errors.some(e => /bazaar/i.test(e)), JSON.stringify(ctx.errors));
  // lowestbins also lists ~1400 bazaar ids at insta-buy, so with no bazaar they are still priced from it
  for (const mode of ['fromScratch', 'easy']) {
    const laser = r[mode].children.find(c => c.id === 'GIANT_FRAGMENT_LASER');
    assert.ok(['lowestBin', 'unavailable'].includes(laser.method), mode + ' ' + laser.method);
    if (laser.method === 'lowestBin') assert.equal(laser.unitCost, LBRAW.GIANT_FRAGMENT_LASER);
  }
});

test('loadPrices maps the raw bazaar: instaBuy = buy_summary[0], buyOrder = sell_summary[0]; empty side = null', { skip }, async () => {
  const core = await freshCore();
  const ff = fakeFetch();
  const ctx = core.createCraftContext({ fetchFn: ff.fn });
  await core.loadPrices(ctx);
  const { BZRAW } = await import('./fx.mjs');
  let checked = 0, emptySides = 0;
  for (const [pid, p] of Object.entries(BZRAW.products)) {
    const e = ctx.bazaar.get(pid);
    assert.ok(e, pid);
    const ib = p.buy_summary[0] ? p.buy_summary[0].pricePerUnit : null, bo = p.sell_summary[0] ? p.sell_summary[0].pricePerUnit : null;
    assert.equal(e.instaBuy ?? null, ib > 0 ? ib : null, pid + ' instaBuy');
    assert.equal(e.buyOrder ?? null, bo > 0 ? bo : null, pid + ' buyOrder');
    if (ib == null || bo == null) emptySides++;
    checked++;
  }
  assert.ok(checked > 100 && emptySides > 0, `checked ${checked}, with an empty side ${emptySides}`);
  assert.equal(ctx.lowestBin.get('HYPERION'), LBRAW.HYPERION);
  assert.equal(ff.calls.length, 2);
});

test('abort: an aborted signal rejects with AbortError', { skip }, async () => {
  const core = await freshCore();
  const ff = fakeFetch();
  const ctx = core.createCraftContext({ fetchFn: ff.fn, recipesUrl: null });
  const ac = new AbortController(); ac.abort();
  await assert.rejects(core.cleanCraftCost('HYPERION', ctx, { signal: ac.signal }), e => e && e.name === 'AbortError');
  await assert.rejects(core.loadRecipeTree('HYPERION', ctx, { signal: ac.signal }), e => e && e.name === 'AbortError');
  // abort mid-way
  const ff2 = fakeFetch({ delay: 20 });
  const ctx2 = core.createCraftContext({ fetchFn: ff2.fn, recipesUrl: null });
  const ac2 = new AbortController();
  const p = core.cleanCraftCost('DIVAN_DRILL', ctx2, { signal: ac2.signal });
  setTimeout(() => ac2.abort(), 30);
  await assert.rejects(p, e => e && e.name === 'AbortError');
});

test('localStorage cache (24h): a fresh page reuses cached recipes; a throwing storage is harmless', { skip }, async () => {
  const had = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  try {
    const ls = memoryStorage();
    Object.defineProperty(globalThis, 'localStorage', { value: ls, configurable: true, writable: true });
    let core = await freshCore();
    let ff = fakeFetch();
    await core.loadRecipeTree('HYPERION', core.createCraftContext({ fetchFn: ff.fn, recipesUrl: null }));
    assert.ok(ff.count(NEU_RE) >= 5);
    assert.ok(ls.size >= 5, 'stored ' + ls.size);
    core = await freshCore(); // new page load: memory cache empty
    ff = fakeFetch();
    const ctx = core.createCraftContext({ fetchFn: ff.fn, recipesUrl: null });
    await core.loadRecipeTree('HYPERION', ctx);
    assert.equal(ff.count(NEU_RE), 0, 'served from localStorage');
    assert.ok(ctx.recipes.get('HYPERION').length >= 1);

    const bad = { getItem() { throw new Error('SecurityError'); }, setItem() { throw new Error('QuotaExceeded'); } };
    Object.defineProperty(globalThis, 'localStorage', { value: bad, configurable: true, writable: true });
    core = await freshCore();
    ff = fakeFetch();
    const r = await core.cleanCraftCost('HYPERION', core.createCraftContext({ fetchFn: ff.fn, recipesUrl: null }));
    assertNear(assert, r.fromScratch.total, EXP.roots.HYPERION.fromScratch.unitCost, 'works with a throwing storage');
  } finally {
    if (had) Object.defineProperty(globalThis, 'localStorage', had); else delete globalThis.localStorage;
  }
});

test('trade min/max (EMERALD.json "65-76 logs for 1 emerald") = average count, same on the bulk and per-item paths', { skip }, async () => {
  const core = await freshCore();
  const ff = fakeFetch();
  const neu = core.createCraftContext({ fetchFn: ff.fn, recipesUrl: null });
  const bulk = core.createCraftContext({ fetchFn: ff.fn });
  await core.loadRecipeTree('EMERALD', neu, { maxDepth: 0 });
  await core.loadRecipeTree('EMERALD', bulk, { maxDepth: 0 });
  for (const ctx of [neu, bulk]) {
    const logs = ctx.recipes.get('EMERALD').find(r => r.inputs.length === 1 && r.inputs[0].id === 'LOG-1');
    assert.ok(logs, 'LOG-1 trade parsed');
    assert.equal(logs.inputs[0].qty, 70.5, 'LOG-1 trade = (65+76)/2 logs');
    assert.equal(logs.note, 'trade');
  }
  await core.loadPrices(neu); bulk.bazaar = neu.bazaar; bulk.lowestBin = neu.lowestBin;
  assert.deepEqual(core.priceItem('EMERALD', 'fromScratch', neu), core.priceItem('EMERALD', 'fromScratch', bulk));
});
