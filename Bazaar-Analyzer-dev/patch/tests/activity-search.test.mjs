// Activity tab search (patch/parts/80-search.js -> BA_actFilter).
// Run: node --test Bazaar-Analyzer-dev/patch/tests/activity-search.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ctx = vm.createContext({});
vm.runInContext(fs.readFileSync(path.join(HERE, '../parts/80-search.js'), 'utf8') + '\n;globalThis.__f=BA_actFilter;', ctx);
const f = (...a) => JSON.parse(JSON.stringify(ctx.__f(...a)));

const low = {
  days: [
    { day: '2026-09-23', givenCoins: 30,
      trades: [
        { partner: 'Steve', givenCoins: 10, given: [], received: [{ name: 'Hyperion' }] },
        { partner: 'Alex', givenCoins: 20, given: [{ name: 'Wither Blade' }], received: [] },
      ],
      gained: 7, pending: 5,
      lowballs: [
        { trade: { partner: 'Steve' }, gained: 3, pending: 0, lines: [{ label: 'Hyperion' }] },
        { trade: { partner: 'Alex' }, gained: 4, pending: 5, lines: [{ label: 'Necron Chestplate' }] },
      ] },
    { day: '2026-09-22', givenCoins: 1, trades: [{ partner: 'Bob', givenCoins: 1, given: [], received: [] }],
      gained: 0, pending: 0, lowballs: [] },
  ],
};
const rows = [{ item: 'Hyperion', other: 'to Steve' }, { item: 'Enchanted Diamond' }, { raw: 'weird line' }];

test('empty query keeps everything', () => {
  assert.deepEqual(f('trades', '', low, rows), { low, rows });
});

test('trades: by partner or item, day total re-summed, empty days dropped', () => {
  let r = f('trades', 'alex', low, rows).low.days;
  assert.equal(r.length, 1);
  assert.deepEqual(r[0].trades.map((t) => t.partner), ['Alex']);
  assert.equal(r[0].givenCoins, 20);
  assert.deepEqual(f('trades', 'hyper', low, rows).low.days[0].trades.map((t) => t.partner), ['Steve']);
});

test('lowball: by partner or line, gained/pending re-summed', () => {
  let d = f('lowball', 'necron', low, rows).low.days;
  assert.equal(d.length, 1);
  assert.equal(d[0].gained, 4);
  assert.equal(d[0].pending, 5);
  assert.equal(f('lowball', 'steve', low, rows).low.days[0].gained, 3);
});

test('market: item, other player or raw line', () => {
  assert.deepEqual(f('market', 'steve', low, rows).rows, [rows[0]]);
  assert.deepEqual(f('market', 'weird', low, rows).rows, [rows[2]]);
  assert.equal(f('market', 'zzz', low, rows).rows.length, 0);
});

test('market: a listing and its sale stay together', () => {
  const pair = [
    { ts: 1, kind: 'AH_LISTED', item: 'Hyperion', raw: 'BIN Auction started for Hyperion' },
    { ts: 2, kind: 'AH_SOLD', item: 'Hyperion', other: 'Steve', listed: 1 },
    { ts: 3, item: 'Dirt' },
  ];
  assert.deepEqual(f('market', 'steve', null, pair).rows.map((t) => t.ts), [1, 2]);
  assert.deepEqual(f('market', 'started', null, pair).rows.map((t) => t.ts), [1, 2]);
});
