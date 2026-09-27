// Regression tests for the review round 1 engine fixes (notes/craft-engine.md "Fixes, review round 1").
// One test (or a few) per finding; small synthetic contexts unless a real fixture shows it better.
import test from 'node:test';
import assert from 'node:assert/strict';
import { enginePresent, freshCore, freshMods, fixtureCtx, fakeFetch, listing, HYITEMS, STONES, ENCH, assertNear } from './fx.mjs';

const skip = enginePresent() ? false : 'craftCore.js / craftModifiers.js not written yet';
const B = (buyOrder, instaBuy) => ({ buyOrder, instaBuy });

async function engine() { return { core: await freshCore(), mods: await freshMods() }; }
function enchLine(mods, core, ctx, type, level, mode) {
  const item = { tag: 'TERMINATOR', enchantments: [{ type, level }], flatNbt: {} };
  const lines = mods.priceModifiers(item, mode, ctx, core.priceItem).filter(l => l.kind === 'enchant');
  assert.equal(lines.length, 1);
  return lines[0];
}

// ---- 1 + 9: anvil combine rule
test('anvil: non-ultimates combine only up to min(V, table max + 1); Chance 5 is priced as its own book', { skip }, async () => {
  const { core, mods } = await engine();
  const ctx = core.createCraftContext({ recipes: {}, lowestBin: {}, bazaar: new Map([
    ['ENCHANTMENT_CHANCE_3', B(50, 100)], ['ENCHANTMENT_CHANCE_4', B(1000.1, 162186.7)], ['ENCHANTMENT_CHANCE_5', B(14559286.5, 19482924.1)],
    ['ENCHANTMENT_OVERLOAD_4', B(5733918, null)], ['ENCHANTMENT_OVERLOAD_5', B(15259860, 24653987)],
    ['ENCHANTMENT_ULTIMATE_WISE_4', B(180000, 300000)], ['ENCHANTMENT_ULTIMATE_WISE_5', B(500000, 700000)],
  ]) });
  // chance: table max III -> V can't be anviled, IV can (2 x III)
  const c5 = enchLine(mods, core, ctx, 'chance', 5, 'fromScratch');
  assert.equal(c5.id, 'ENCHANTMENT_CHANCE_5'); assert.equal(c5.method, 'buyOrder'); assertNear(assert, c5.total, 14559286.5, 'Chance 5');
  const c4 = enchLine(mods, core, ctx, 'chance', 4, 'fromScratch');
  assert.equal(c4.method, 'buyOrder+anvil'); assertNear(assert, c4.total, 100, 'Chance 4 = 2 x Chance 3');
  // overload: not an enchanting-table enchant -> never combined
  const o5 = enchLine(mods, core, ctx, 'overload', 5, 'fromScratch');
  assert.equal(o5.id, 'ENCHANTMENT_OVERLOAD_5'); assertNear(assert, o5.total, 15259860, 'Overload 5');
  // ultimates: combining allowed at any level
  const u5 = enchLine(mods, core, ctx, 'ultimate_wise', 5, 'fromScratch');
  assert.equal(u5.method, 'buyOrder+anvil'); assertNear(assert, u5.total, 360000, 'UW5 = 2 x UW4');
});

test('anvil: a lower book priced through a recipe never feeds the combine (Life Steal 4 = its own book)', { skip }, async () => {
  const { core, mods } = await engine();
  const ctx = core.createCraftContext({ lowestBin: {},
    recipes: { 'LIFE_STEAL;2': [{ type: 'craft', output: 1, inputs: [{ id: 'PAPER', qty: 6 }], coins: 0 }] },
    bazaar: new Map([['PAPER', B(10, 12)], ['ENCHANTMENT_LIFE_STEAL_4', B(310003.7, 740992.5)]]) });
  const fs = enchLine(mods, core, ctx, 'life_steal', 4, 'fromScratch');
  assert.equal(fs.id, 'ENCHANTMENT_LIFE_STEAL_4'); assertNear(assert, fs.total, 310003.7, 'LS4 from scratch');
  const ez = enchLine(mods, core, ctx, 'life_steal', 4, 'easy');
  assert.equal(ez.method, 'instaBuy'); assertNear(assert, ez.total, 740992.5, 'LS4 easy');
});

test('easy: the exact book is insta-bought when it can be; anvil / fallback only when it cannot, flagged', { skip }, async () => {
  const { core, mods } = await engine();
  const ctx = core.createCraftContext({ recipes: {}, lowestBin: {}, bazaar: new Map([
    ['ENCHANTMENT_OVERLOAD_4', B(5733918, null)], ['ENCHANTMENT_OVERLOAD_5', B(15259860, 24653987)],
    ['ENCHANTMENT_ULTIMATE_WISE_4', B(180000, 300000)], ['ENCHANTMENT_ULTIMATE_WISE_5', B(500000, 700000)],
    ['ENCHANTMENT_ULTIMATE_CHIMERA_4', B(null, 400000)], ['ENCHANTMENT_ULTIMATE_CHIMERA_5', B(900000, null)],
  ]) });
  const o5 = enchLine(mods, core, ctx, 'overload', 5, 'easy');
  assert.equal(o5.method, 'instaBuy'); assertNear(assert, o5.total, 24653987, 'Overload 5 easy');
  const u5 = enchLine(mods, core, ctx, 'ultimate_wise', 5, 'easy');
  assert.equal(u5.method, 'instaBuy'); assertNear(assert, u5.total, 700000, 'UW5 easy = its insta-buy, not 2 x UW4');
  // Chimera 5 has no sell offers: cheaper of its buy-order fallback (900k) and 2 x Chimera 4 insta-buy (800k), flagged
  const c5 = enchLine(mods, core, ctx, 'ultimate_chimera', 5, 'easy');
  assert.equal(c5.method, 'instaBuy+anvil'); assertNear(assert, c5.total, 800000, 'Chimera 5 easy');
  assert.match(c5.note, /^can't be bought/);
});

// ---- 2: dead buy orders
test('from scratch ignores a dead buy order (< 1% of insta-buy) and uses insta-buy, with a note', { skip }, async () => {
  const { core } = await engine();
  const ctx = core.createCraftContext({ recipes: {}, lowestBin: {}, bazaar: new Map([
    ['ENCHANTMENT_ULTIMATE_REITERATE_5', B(2.8, 23374664.6)], ['ENCHANTMENT_LUCK_6', B(10003, 306874)]]) });
  const d = core.priceItem('ENCHANTMENT_ULTIMATE_REITERATE_5', 'fromScratch', ctx);
  assert.equal(d.method, 'instaBuy'); assertNear(assert, d.unitCost, 23374664.6, 'Duplex 5'); assert.match(d.note, /buy order too low/);
  const l = core.priceItem('ENCHANTMENT_LUCK_6', 'fromScratch', ctx);   // 3.3%: a real order, kept
  assert.equal(l.method, 'buyOrder'); assertNear(assert, l.unitCost, 10003, 'Luck 6');
});

// ---- 3: sold-row reforge parse
test('sold-row reforge: "Shiny " and colour codes are not part of the reforge', { skip }, async () => {
  const { mods } = await engine();
  const data = { items: new Map([['HYPERION', { id: 'HYPERION', name: 'Hyperion', tier: 'LEGENDARY' }], ['TERMINATOR', { id: 'TERMINATOR', name: 'Terminator', tier: 'LEGENDARY' }],
    ['DIVAN_CHESTPLATE', { id: 'DIVAN_CHESTPLATE', name: 'Chestplate of Divan', tier: 'LEGENDARY' }]]) };
  const rf = item => mods.listModifiers(item, data).filter(m => m.kind === 'reforge');
  for (const flat of [{ is_shiny: '1' }, {}]) {   // with and without the is_shiny flag
    const r = rf({ tag: 'HYPERION', tier: 'MYTHIC', itemName: 'Shiny Withered Hyperion ✪✪✪✪✪➊', flattenedNbt: { ...flat, rarity_upgrades: '1' } });
    assert.equal(r.length, 1, JSON.stringify(flat));
    assert.equal(r[0].label, 'Withered reforge');
    assert.deepEqual(r[0].parts, [{ id: 'WITHER_BLOOD', qty: 1 }]); assert.equal(r[0].coins, 60000);
  }
  assert.deepEqual(rf({ tag: 'TERMINATOR', tier: 'LEGENDARY', itemName: '§6Terminator', flattenedNbt: {} }), []);
  const d = rf({ tag: 'DIVAN_CHESTPLATE', tier: 'LEGENDARY', itemName: '§6Dimensional Chestplate of Divan', flattenedNbt: {} });
  assert.equal(d.length, 1); assert.equal(d[0].label, 'Dimensional reforge'); assert.deepEqual(d[0].parts, [{ id: 'TITANIUM_TESSERACT', qty: 1 }]);
});

// ---- 4: easy fallback with the root on the stack
test('easy tree: a fallback ingredient may not loop back through the root (root total = sum of children)', { skip }, async () => {
  const { core } = await engine();
  // A = 1 B; B (not buyable) = 1 A. Before the fix: A easy = 10 via B -> A's buy order, while child B showed unavailable.
  const ctx = core.createCraftContext({ lowestBin: {}, bazaar: new Map([['A', B(10, 12)]]),
    recipes: { A: [{ type: 'craft', output: 1, inputs: [{ id: 'B', qty: 1 }], coins: 0 }], B: [{ type: 'craft', output: 1, inputs: [{ id: 'A', qty: 1 }], coins: 0 }] } });
  const t = core.craftTree('A', 'easy', ctx);
  const kids = t.children || [];
  if (t.total == null) assert.ok(kids.some(k => k.total == null), 'null root has a null child');
  else assertNear(assert, t.total, kids.reduce((a, k) => a + k.total, 0), 'root = sum of children');
  assert.equal(t.total, null);
});

// ---- 6: NEU trades for enchant books
test('NEU trade recipes are ignored for enchant books, kept for other items', { skip }, async () => {
  const { core } = await engine();
  const trade = n => ({ type: 'other', note: 'trade', output: 1, inputs: [{ id: 'ENCHANTED_EMERALD', qty: n }], coins: 0 });
  const ctx = core.createCraftContext({ lowestBin: {},
    bazaar: new Map([['ENCHANTMENT_ENDER_SLAYER_6', B(250000.1, 440024.7)], ['ENCHANTED_EMERALD', B(448, 500)], ['SOME_ITEM', B(100000, 120000)]]),
    recipes: { 'ENDER_SLAYER;6': [trade(50), { type: 'npc', output: 1, inputs: [], coins: 1500000 }], SOME_ITEM: [trade(2)] } });
  const e = core.priceItem('ENCHANTMENT_ENDER_SLAYER_6', 'fromScratch', ctx);
  assert.equal(e.method, 'buyOrder'); assertNear(assert, e.unitCost, 250000.1, 'Ender Slayer 6');
  const s = core.priceItem('SOME_ITEM', 'fromScratch', ctx);
  assert.equal(s.method, 'craft'); assertNear(assert, s.unitCost, 896, 'non-book trade');
});

// ---- 7: lowestbins outage
test('lowestbins down: each id goes to Coflnet once across site-style ctx copies; lowestbins retried after the cooldown', { skip }, async () => {
  const core = await freshCore();
  const ff = fakeFetch({ fail: { lbin: 503 } });
  const base = fixtureCtx(core, { fetchFn: ff.fn });
  base.lowestBin = null;
  let ctx = base;
  const cofl = () => ff.count(/sky\.coflnet\.com/);
  // poll 0: two concurrent callers
  await Promise.all([core.cleanCraftCost('TERMINATOR', ctx), core.cleanCraftCost('TERMINATOR', ctx)]);
  const after0 = cofl();
  assert.ok(after0 >= 1, 'fallback used');
  assert.equal(ff.count(/lb\.tricked\.pro/), 1);
  // polls 1-3: the site's BA_craftCtxFor spread
  for (let i = 0; i < 3; i++) {
    ctx = { ...ctx, _memo: null, _pricesP: null, errors: ctx.errors.slice() };
    await Promise.all([core.cleanCraftCost('TERMINATOR', ctx), core.cleanCraftCost('TERMINATOR', ctx)]);
  }
  assert.equal(cofl(), after0, 'no repeated Coflnet requests');
  assert.equal(ff.count(/lb\.tricked\.pro/), 1, 'no lowestbins retry inside the cooldown');
  // cooldown over and lowestbins back: next ctx reloads it and the warning goes away
  const ok = fakeFetch();
  ctx = { ...ctx, fetchFn: ok.fn, lowestBinFailedAt: Date.now() - 6 * 60 * 1000, _memo: null, _pricesP: null, errors: ctx.errors.slice() };
  const r = await core.cleanCraftCost('TERMINATOR', ctx);
  assert.equal(ok.count(/lb\.tricked\.pro/), 1);
  assert.equal(ctx.lowestBinFailed, false);
  assert.ok(ctx.lowestBin.size > 20);
  assert.ok(!r.errors.some(e => /Lowest BIN/.test(e)), JSON.stringify(r.errors));
});

// ---- 8: stale / foreign warnings
test('cleanCraftCost reports only errors about this item; failed item data is not cached on ctx', { skip }, async () => {
  const { core, mods } = await engine();
  const ctx = fixtureCtx(core);
  ctx.errors.push('Recipe SOME_OTHER_ITEM: HTTP 500 for x', 'Lowest BIN list: HTTP 503 for y');
  const r = await core.cleanCraftCost('HYPERION', ctx);
  assert.deepEqual(r.errors, [], 'foreign recipe error and a stale lowestbins error are not shown');
  ctx.errors.push('Recipe NECRON_HANDLE: HTTP 500 for z');
  const r2 = await core.cleanCraftCost('HYPERION', ctx);
  assert.deepEqual(r2.errors, ['Recipe NECRON_HANDLE: HTTP 500 for z'], 'an error about an id in the tree is shown');

  const ff = fakeFetch({ fail: { items: 500 } });
  const c2 = { fetchFn: ff.fn };
  const d1 = await mods.loadModifierData(c2);
  assert.match(d1.note, /item data unavailable/);
  assert.equal(c2.modData, undefined, 'failure not cached on ctx');
  const d2 = await mods.loadModifierData({ fetchFn: ff.fn });
  assert.match(d2.note, /item data unavailable/);
  assert.equal(ff.count(/resources\/skyblock\/items/), 1, 'retried only after the cooldown');
  const c3 = { hyItems: HYITEMS };
  const d3 = await mods.loadModifierData(c3);
  assert.ok(d3.items.size > 0 && !d3.note && c3.modData === d3, 'success is cached');
});

// ---- 10: pets without a recipe for their tier
test('pet tier without a recipe: the clean pet is bought at its lowest BIN and counted in the totals', { skip }, async () => {
  const { core, mods } = await engine();
  const ctx = fixtureCtx(core);
  ctx.hyItems = HYITEMS; ctx.reforgeStones = STONES; ctx.enchantTable = ENCH;
  const L = listing('pet-ender-dragon');   // Epic Lvl 80, Crochet Tiger Plushie
  const r = await mods.listingCraftCost(L.tag, L, ctx, { loadRecipeTree: core.loadRecipeTree, cleanCraftCost: core.cleanCraftCost, priceItem: core.priceItem });
  assert.equal(r.craftable, false);
  assert.equal(r.baseSource, 'market');
  assert.ok(r.notes.some(n => /exp/.test(n)));
  const lb = core.priceItem('ENDER_DRAGON;3', 'fromScratch', ctx);
  assert.equal(lb.method, 'lowestBin');
  for (const mode of ['fromScratch', 'easy']) {
    const b = r.base[mode];
    assert.equal(b.method, 'lowestBin'); assertNear(assert, b.total, lb.unitCost, mode + ' base');
    const mods_ = r.modifiers[mode].reduce((a, l) => a + (l.total || 0), 0);
    assertNear(assert, r.totals[mode], lb.unitCost + mods_, mode + ' total');
  }
});
