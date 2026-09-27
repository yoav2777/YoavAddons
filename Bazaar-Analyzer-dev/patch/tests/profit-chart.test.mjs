// Activity profit chart series (patch/parts/95-profit-chart.js -> BA_profitSeries).
// Run: node --test Bazaar-Analyzer-dev/patch/tests/profit-chart.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ctx = vm.createContext({
  BA_modKinds: { BZ_INSTA_BUY: ['', -1], BZ_CLAIM_SOLD: ['', 1], AH_COLLECTED: ['', 1], AH_LISTED: ['', 0] },
});
vm.runInContext(fs.readFileSync(path.join(HERE, '../parts/95-profit-chart.js'), 'utf8') + '\n;globalThis.__f=BA_profitSeries;', ctx);
const f = (...a) => JSON.parse(JSON.stringify(ctx.__f(...a)));

test('lowball: gained + pending per day with lowballs, oldest first', () => {
  const low = { days: [
    { day: '2026-09-23', gained: 7, pending: 5, lowballs: [{}] },
    { day: '2026-09-22', gained: 0, pending: 0, lowballs: [] },
    { day: '2026-09-21', gained: -2, pending: 0, lowballs: [{}] },
  ] };
  assert.deepEqual(f('lowball', low, []), [
    { day: '2026-09-21', v: -2, p: 0 },
    { day: '2026-09-23', v: 7, p: 5 },
  ]);
});

test('market: earned - spent per day, listings and unknown coins skipped', () => {
  const rows = [
    { day: '2026-09-22', kind: 'BZ_INSTA_BUY', coins: 100 },
    { day: '2026-09-22', kind: 'AH_COLLECTED', coins: 30 },
    { day: '2026-09-22', kind: 'AH_LISTED', coins: 999 },
    { day: '2026-09-23', kind: 'BZ_CLAIM_SOLD', coins: 50 },
    { day: '2026-09-23', kind: 'AH_COLLECTED', coins: null },
  ];
  assert.deepEqual(f('market', null, rows), [
    { day: '2026-09-22', v: -70, p: 0 },
    { day: '2026-09-23', v: 50, p: 0 },
  ]);
});

test('trades tab has no chart; only the last 30 days', () => {
  assert.deepEqual(f('trades', { days: [{ day: '2026-09-01', gained: 1, lowballs: [{}] }] }, []), []);
  const days = Array.from({ length: 40 }, (_, i) => ({ day: `2026-08-${String(i + 1).padStart(2, '0')}`, gained: i, pending: 0, lowballs: [{}] }));
  const s = f('lowball', { days }, []);
  assert.equal(s.length, 30);
  assert.equal(s[0].v, 10);
});

test('market: the mod per-day totals win over the loaded rows', () => {
  const rows = [{ day: '2026-09-23', kind: 'BZ_CLAIM_SOLD', coins: 50 }];
  assert.deepEqual(f('market', null, rows, { '2026-09-23': 80, '2026-09-21': -10 }), [
    { day: '2026-09-21', v: -10, p: 0 },
    { day: '2026-09-23', v: 80, p: 0 },
  ]);
});
