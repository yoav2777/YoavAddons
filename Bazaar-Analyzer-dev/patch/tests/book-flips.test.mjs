// Book Flips math (patch/parts/40-book-flips.js -> BA_bookFlips) against a recorded bazaar snapshot.
// Run: "C:/Program Files/nodejs/node.exe" --test C:/Users/Lenovo/Bazaar-Analyzer-dev/patch/tests/book-flips.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { canAnvilEnchant } from '../../craft/craftModifiers.js';

const HERE = path.dirname(fileURLToPath(import.meta.url));
// The part is plain bundle code: evaluate it in a sandbox (UI functions reference bundle ids only when called).
// BA_bookCanAnvil falls back to the shared engine rule, so the sandbox gets the real BA_craft.canAnvilEnchant.
const ctx = vm.createContext({ BA_craft: { canAnvilEnchant } });
vm.runInContext(fs.readFileSync(path.join(HERE, '../parts/40-book-flips.js'), 'utf8')
  + '\n;globalThis.__f=BA_bookFlips;globalThis.__can=BA_bookCanAnvil;globalThis.__max=BA_bookAnvilMax;', ctx);
// rows come from another realm: copy them so deepEqual compares plain arrays
const flips = (...a) => JSON.parse(JSON.stringify(ctx.__f(...a)));
const canAnvil = (...a) => ctx.__can(...a); // the rule the tab uses: hand-kept caps, else the engine rule

// same mapping as the bundle's gt(id, raw)
const map = (id, t) => ({ id, instaBuy: t.buy_summary[0]?.pricePerUnit ?? null, instaSell: t.sell_summary[0]?.pricePerUnit ?? null,
  boughtWeek: t.quick_status.buyMovingWeek, soldWeek: t.quick_status.sellMovingWeek });
const SNAP = JSON.parse(fs.readFileSync(path.join(HERE, 'fixtures/bazaar-enchants-2026-09-21.json'), 'utf8'));
const PRODUCTS = Object.entries(SNAP.products).map(([id, t]) => map(id, t));
const TAX = 0.0125;
// the tab's own settings: 0.1 order step (site setting "Outbid / undercut by 0.1 coins" on) and the tab's canAnvil
const run = (products = PRODUCTS, o = {}) => flips(products, { tax: TAX, tick: 0.1, canAnvil, ...o });
const of = (rows, name) => rows.filter((r) => r.name === name);
const close = (a, b, msg) => assert.ok(Math.abs(a - b) < 1e-6 * Math.max(1, Math.abs(b)), `${msg}: ${a} != ${b}`);
// synthetic product: ask = lowest sell offer (instaBuy), bid = top buy order (instaSell), per-week volumes
const P = (id, ask, bid, bought = 1680, sold = 1680) => ({ id, instaBuy: ask, instaSell: bid, boughtWeek: bought, soldWeek: sold });

test('engine rule used for targets', () => {
  assert.equal(canAnvilEnchant('SHARPNESS', 5), true);
  assert.equal(canAnvilEnchant('SHARPNESS', 6), false);
  assert.equal(canAnvilEnchant('LOOTING', 4), true);
  assert.equal(canAnvilEnchant('LOOTING', 5), false);
  assert.equal(canAnvilEnchant('ULTIMATE_WISE', 5), true);
  assert.equal(canAnvilEnchant('DRAGON_HUNTER', 2), false); // not an enchanting-table enchant -> never combined
});

test('tab rule: hand-kept caps first, engine rule for everything else', () => {
  for (const [name, cap] of Object.entries(ctx.__max)) {
    assert.equal(canAnvil(name, cap), true, name + ' ' + cap);
    assert.equal(canAnvil(name, cap + 1), false, name + ' ' + (cap + 1));
    assert.equal(canAnvil(name, 1), false, name + ' I is never made in an anvil');
  }
  assert.equal(canAnvil('FEAST', 5), true);          // only non-bazaar source is Feast I (Grand Bakery)
  assert.equal(canAnvil('DEDICATION', 3), true);
  assert.equal(canAnvil('DEDICATION', 4), false);    // IV comes from garden visitors, not the anvil
  assert.equal(canAnvil('TURBO_WHEAT', 6), false);   // VI comes from the Turbo Gourd
  assert.equal(canAnvil('CHAMPION', 2), false);      // level-up enchant: not in the map, engine says no
  assert.equal(canAnvil('SHARPNESS', 5), true);      // untouched enchants keep the engine rule
  assert.equal(canAnvil('SHARPNESS', 6), false);
  assert.equal(canAnvil('ULTIMATE_WISE', 5), true);
});

test('Sharpness: target V even though VI/VII exist (not combinable); books = powers of two', () => {
  const ps = [1, 2, 3, 4, 5, 6, 7].map((l) => P('ENCHANTMENT_SHARPNESS_' + l, 10 * 2 ** l, 9 * 2 ** l));
  const rows = run(ps);
  assert.deepEqual(rows.map((r) => r.target), [5, 5, 5, 5]);
  assert.deepEqual(rows.map((r) => [r.from, r.books]).sort(), [[1, 16], [2, 8], [3, 4], [4, 2]]);
  assert.ok(rows.every((r) => r.outId === 'ENCHANTMENT_SHARPNESS_5'));
});

test('snapshot: Sharpness I-V have no market -> no Sharpness flip at all (never VI/VII, never a lower target)', () => {
  const rows = run();
  assert.equal(of(rows, 'SHARPNESS').length, 0);
  assert.ok(!rows.some((r) => r.target > 5 && !r.name.startsWith('ULTIMATE_')), 'no non-ultimate target above V');
});

test('no lower target: target without a price skips the enchant instead of falling back to IV', () => {
  const ps = [1, 2, 3, 4].map((l) => P('ENCHANTMENT_SMITE_' + l, 100 * l, 90 * l)).concat(P('ENCHANTMENT_SMITE_5', null, null, 0, 0));
  assert.equal(run(ps).length, 0);
  // and with a price on V every row targets V
  ps[4] = P('ENCHANTMENT_SMITE_5', 5000, 4000);
  assert.ok(run(ps).every((r) => r.target === 5));
});

test('snapshot: one target per enchant, the highest combinable bazaar level', () => {
  const rows = run();
  assert.ok(rows.length > 20, 'some flips found: ' + rows.length);
  const levels = new Map();
  for (const p of PRODUCTS) {
    const m = /^ENCHANTMENT_(.+)_(\d+)$/.exec(p.id);
    if (m && +m[2] >= 1) (levels.get(m[1]) || levels.set(m[1], new Set()).get(m[1])).add(+m[2]);
  }
  for (const [name, rs] of Object.entries(Object.groupBy(rows, (r) => r.name))) {
    const t = rs[0].target;
    assert.ok(rs.every((r) => r.target === t), name + ' single target');
    for (let L = 2; L <= t; L++) assert.ok(canAnvil(name, L), `${name} ${L} combinable`);
    assert.ok(!(levels.get(name).has(t + 1) && canAnvil(name, t + 1)), `${name}: a higher combinable level exists`);
    for (const r of rs) { assert.equal(r.books, 2 ** (t - r.from)); assert.ok(r.from < t); }
  }
  // Looting: table max III -> anvil IV is the target although LOOTING_5 is a bazaar product
  assert.ok(of(rows, 'LOOTING').every((r) => r.target === 4));
  assert.ok(of(rows, 'ULTIMATE_WISE').length >= 3 && of(rows, 'ULTIMATE_WISE').every((r) => r.target === 5));
});

test('snapshot: Ultimate Wise IV -> V by hand (orders, tax, fill-rate limit, coins/hr)', () => {
  const r = of(run(), 'ULTIMATE_WISE').find((x) => x.from === 4);
  // UW4 top buy order 180000.5 (+0.1), UW5 lowest sell offer 656553.3 (-0.1); UW4 sellMovingWeek 95, UW5 buyMovingWeek 8285
  close(r.inPrice, 180000.6, 'input price');
  close(r.cost, 360001.2, 'cost');
  close(r.outPrice, 656553.2, 'output price');
  close(r.net, 656553.2 * (1 - TAX), 'after tax');
  close(r.profit, 656553.2 * 0.9875 - 360001.2, 'profit');
  close(r.pct, r.profit / 360001.2, 'margin');
  close(r.inRate, 95 / 168, 'input rate');
  close(r.outRate, 8285 / 168, 'output rate');
  close(r.flips, 95 / 168 / 2, 'flips/hr');
  assert.equal(r.limit, 'input');
  close(r.coinsHr, (95 / 168 / 2) * r.profit, 'coins/hr');
});

test('snapshot: non-ultimate books from the cap map (Feast I -> V by hand, Dedication stops at III)', () => {
  const rows = run();
  assert.ok(rows.some((r) => !r.name.startsWith('ULTIMATE_')), 'non-ultimate flips exist');
  assert.ok(of(rows, 'FEAST').every((r) => r.target === 5), 'Feast targets V');
  const r = of(rows, 'FEAST').find((x) => x.from === 1);
  // FEAST1 top buy order 3161612.3 (+0.1) x16; FEAST5 lowest sell offer 53291683 (-0.1)
  // FEAST1 sellMovingWeek 77000 -> 458.3/h -> 28.6 flips/h; FEAST5 buyMovingWeek 1219 -> 7.26/h limits
  assert.equal(r.books, 16);
  close(r.inPrice, 3161612.4, 'input price');
  close(r.cost, 16 * 3161612.4, 'cost');
  close(r.outPrice, 53291682.9, 'output price');
  close(r.profit, 53291682.9 * (1 - TAX) - 16 * 3161612.4, 'profit');
  close(r.flips, 1219 / 168, 'flips/hr');
  assert.equal(r.limit, 'output');
  close(r.coinsHr, (1219 / 168) * r.profit, 'coins/hr');
  // Dedication IV is a bazaar product but comes from garden visitors, so III is the target
  assert.ok(of(rows, 'DEDICATION').length > 0 && of(rows, 'DEDICATION').every((r) => r.target === 3));
  assert.equal(of(rows, 'CHAMPION').length, 0, 'level-up enchants stay out');
});

test('tick: 0.1 order step only when asked for (site setting), 0 by default', () => {
  const ps = [P('ENCHANTMENT_ULTIMATE_JERRY_4', 3000, 2000), P('ENCHANTMENT_ULTIMATE_JERRY_5', 5000, 4000)];
  const [a] = run(ps);                       // run() passes tick 0.1
  close(a.inPrice, 2000.1, 'buy order + 0.1'); close(a.outPrice, 4999.9, 'sell offer - 0.1');
  const [b] = run(ps, { tick: 0 });
  close(b.inPrice, 2000, 'plain top of book'); close(b.outPrice, 5000, 'plain top of book');
  const [c] = flips(ps, { tax: TAX, canAnvil }); // no tick option -> 0
  close(c.inPrice, 2000, 'default tick'); close(c.outPrice, 5000, 'default tick');
  const [d] = run(ps, { tick: null });       // junk tick -> 0, never NaN prices
  close(d.inPrice, 2000, 'bad tick'); close(d.outPrice, 5000, 'bad tick');
});

test('fill-rate limiting and insta toggles switch prices and rates', () => {
  // I: ask 110, bid 100, bought 168/wk (1/h), sold 3360/wk (20/h). V: ask 3000, bid 2000, bought 336/wk (2/h), sold 16800/wk (100/h)
  const ps = [P('ENCHANTMENT_ULTIMATE_JERRY_1', 110, 100, 168, 3360), P('ENCHANTMENT_ULTIMATE_JERRY_2', null, null, 0, 0),
    P('ENCHANTMENT_ULTIMATE_JERRY_3', null, null, 0, 0), P('ENCHANTMENT_ULTIMATE_JERRY_4', null, null, 0, 0),
    P('ENCHANTMENT_ULTIMATE_JERRY_5', 3000, 2000, 336, 16800)];
  let [r] = run(ps); // orders: input fills at 20/h -> 20/16 = 1.25 flips/h; output fills at 2/h -> input limits
  assert.equal(r.books, 16);
  close(r.cost, 16 * 100.1, 'order cost'); close(r.outPrice, 2999.9, 'sell offer');
  close(r.flips, 1.25, 'flips'); assert.equal(r.limit, 'input');
  [r] = run(ps, { instaIn: true }); // insta-buy at 110, rate = input boughtWeek 1/h -> 1/16 flips/h
  close(r.inPrice, 110, 'insta-buy price'); close(r.flips, 1 / 16, 'insta flips');
  [r] = run(ps, { instaOut: true }); // insta-sell at 2000 (taxed), rate = output soldWeek 100/h -> input still limits
  close(r.outPrice, 2000, 'insta-sell'); close(r.net, 2000 * 0.9875, 'insta-sell taxed'); close(r.flips, 1.25, 'flips');
  const slowOut = ps.map((p) => (p.id.endsWith('_5') ? { ...p, boughtWeek: 84 } : p)); // 0.5/h output
  [r] = run(slowOut);
  close(r.flips, 0.5, 'output-limited'); assert.equal(r.limit, 'output');
  close(r.coinsHr, 0.5 * (2999.9 * 0.9875 - 1601.6), 'coins/hr');
});

test('unprofitable -> coins/hr 0; missing prices skipped; tax option used', () => {
  const ps = [P('ENCHANTMENT_ULTIMATE_BANK_4', 1000, 900), P('ENCHANTMENT_ULTIMATE_BANK_5', 1500, 1400),
    P('ENCHANTMENT_ULTIMATE_BANK_3', 500, null)];
  const rows = run(ps);
  assert.equal(rows.length, 1, 'level III has no buy order -> skipped with orders');
  assert.ok(rows[0].profit < 0); assert.equal(rows[0].coinsHr, 0);
  assert.equal(run(ps, { instaIn: true }).length, 2, 'with insta-buy level III is usable');
  close(run(ps, { tax: 0.01 })[0].net, 1499.9 * 0.99, 'tax 1%');
  assert.throws(() => flips(ps, { tax: TAX }), /canAnvil/);
});
