// craftCore.js against the contract, with fixture data and expected values from fixtures/reference.mjs
// (an independent implementation of craft-spec.md) plus small hand-computed synthetic graphs.
import test from 'node:test';
import assert from 'node:assert/strict';
import { EXP, LBRAW, enginePresent, freshCore, fixtureCtx, near, assertNear, walk, failFetch } from './fx.mjs';

const skip = enginePresent() ? false : 'craftCore.js / craftModifiers.js not written yet';
const core = skip ? null : await freshCore();

// reference uses the recipe type; the contract method enum has no 'other' (trade / Kat upgrade show as craft + note)
const M = m => (m === 'other' ? 'craft' : m);

// tag + opts used to reach each expected root through the public API
const ROOT_CALL = id => (id.includes(';') ? ['PET_' + id.split(';')[0], { tier: ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY', 'MYTHIC'][+id.split(';')[1]] }] : [id, {}]);

test('exports match the contract', { skip }, () => {
  for (const f of ['createCraftContext', 'loadPrices', 'loadRecipeTree', 'priceItem', 'craftTree', 'cleanCraftCost', 'flattenTree'])
    assert.equal(typeof core[f], 'function', f);
});

test('clean craft cost of every fixture root matches the reference (both modes)', { skip }, async t => {
  const ctx = fixtureCtx(core);
  for (const id in EXP.roots) {
    const exp = EXP.roots[id];
    const [tag, opts] = ROOT_CALL(id);
    const res = await core.cleanCraftCost(tag, ctx, opts);
    if (!exp.fromScratch) {
      assert.equal(res.craftable, false, id + ' should not be craftable');
      assert.equal(res.fromScratch, null); assert.equal(res.easy, null);
      continue;
    }
    assert.equal(res.craftable, true, id);
    for (const mode of ['fromScratch', 'easy']) {
      const tree = res[mode], e = exp[mode];
      assert.ok(tree, `${id} ${mode} tree`);
      assertNear(assert, tree.total, e.unitCost, `${id} ${mode} total`);
      assertNear(assert, tree.unitCost, e.unitCost, `${id} ${mode} unitCost`);
      assert.equal(tree.method, M(e.method), `${id} ${mode} root method (root is always crafted)`);
      // direct children: same ids, quantities, methods and totals as the reference's chosen recipe
      const kids = new Map((tree.children || []).map(c => [c.id, c]));
      for (const k of e.children) {
        const c = kids.get(k.id);
        assert.ok(c, `${id} ${mode} child ${k.id} missing (got ${[...kids.keys()]})`);
        assertNear(assert, c.qty, k.qty / e.output, `${id} ${mode} ${k.id} qty`);
        assertNear(assert, c.total, k.total / e.output, `${id} ${mode} ${k.id} total`);
        assert.equal(c.method, M(k.method), `${id} ${mode} ${k.id} method`);
      }
      assert.equal(kids.size, e.children.length, `${id} ${mode} child count`);
    }
    // the headline invariant: buy orders + cheapest routes can never cost more than insta-buying
    assert.ok(res.fromScratch.total <= res.easy.total + 0.01, `${id} fromScratch ${res.fromScratch.total} > easy ${res.easy.total}`);
    t.diagnostic(`${id}: fromScratch ${Math.round(res.fromScratch.total).toLocaleString('en-US')} / easy ${Math.round(res.easy.total).toLocaleString('en-US')}`);
  }
});

test('root is forced through its recipe even when buying it is cheaper (HYPERION lbin < craft)', { skip }, () => {
  const ctx = fixtureCtx(core);
  const tree = core.craftTree('HYPERION', 'fromScratch', ctx);
  assert.ok(LBRAW.HYPERION < tree.total, 'fixture premise: HYPERION lbin is cheaper than crafting');
  assert.equal(tree.method, 'craft');
  assert.equal(core.priceItem('HYPERION', 'fromScratch', ctx).method, 'lowestBin', 'as an ingredient it is bought');
});

test('every tree node total = unitCost * qty, and = sum of its children when expanded', { skip }, () => {
  const ctx = fixtureCtx(core);
  for (const id of ['HYPERION', 'TERMINATOR', 'DIVAN_DRILL', 'TITANIUM_DRILL_1', 'ENCHANTED_DIAMOND_BLOCK', 'REFINED_MITHRIL_PICKAXE', 'ASPECT_OF_THE_VOID', 'JUJU_SHORTBOW']) {
    for (const mode of ['fromScratch', 'easy']) {
      let nodes = 0;
      walk(core.craftTree(id, mode, ctx), n => {
        nodes++;
        assert.equal(typeof n.id, 'string'); assert.equal(typeof n.method, 'string');
        if (n.total == null) return;
        assertNear(assert, n.total, n.unitCost * n.qty, `${id} ${mode} ${n.id} total = unit*qty`);
        if (n.children && n.children.length) {
          const s = n.children.reduce((a, c) => a + (c.total ?? NaN), 0);
          assertNear(assert, s, n.total, `${id} ${mode} ${n.id} children sum`);
        }
        if (n.id === 'SKYBLOCK_COIN') { assert.equal(n.method, 'coins'); assert.equal(n.unitCost, 1); }
      });
      assert.ok(nodes > 1, `${id} ${mode} expanded`);
    }
  }
});

test('easy tree never sub-crafts buyable ingredients', { skip }, () => {
  const ctx = fixtureCtx(core);
  for (const id of ['HYPERION', 'TERMINATOR', 'DIVAN_DRILL', 'ASPECT_OF_THE_VOID']) {
    const tree = core.craftTree(id, 'easy', ctx);
    for (const c of tree.children) {
      assert.ok(['instaBuy', 'lowestBin', 'npc', 'coins'].includes(c.method) || /can't be bought/.test(c.note || ''), `${id} easy child ${c.id} method ${c.method}`);
      if (!/can't be bought/.test(c.note || '')) assert.ok(!c.children || !c.children.length, `${id} easy child ${c.id} expanded`);
    }
  }
});

test('priceItem matches the reference at key nodes (methods + unit costs)', { skip }, () => {
  const ctx = fixtureCtx(core);
  for (const id in EXP.items) {
    const e = EXP.items[id];
    const s = core.priceItem(id, 'fromScratch', ctx), z = core.priceItem(id, 'easy', ctx);
    assertNear(assert, s.unitCost, e.fromScratch.unitCost, `${id} fromScratch`);
    assert.equal(s.method, M(e.fromScratch.method), `${id} fromScratch method`);
    assertNear(assert, z.unitCost, e.easy.unitCost, `${id} easy`);
    assert.equal(z.method, M(e.easy.method), `${id} easy method`);
    if (e.easy.flagged) assert.ok(z.note, `${id} easy fallback must carry a note`);
  }
});

test('spec: NPC coin shops count as buying in easy mode (FLINT 60 coins / 10)', { skip }, () => {
  const ctx = fixtureCtx(core);
  for (const d of EXP.easyNpcDiff) {
    const z = core.priceItem(d.id, 'easy', ctx);
    assertNear(assert, z.unitCost, d.withNpc, `${d.id} easy`);
    assert.equal(z.method, 'npc', d.id);
  }
});

test('id forms: bazaar ids, NEU ids and book ids resolve to the same price', { skip }, () => {
  const ctx = fixtureCtx(core);
  const a = core.priceItem('INK_SACK-4', 'fromScratch', ctx), b = core.priceItem('INK_SACK:4', 'fromScratch', ctx);
  assert.equal(a.unitCost, b.unitCost); assert.ok(a.unitCost > 0);
  const u = core.priceItem('ENCHANTMENT_ULTIMATE_WISE_4', 'easy', ctx), v = core.priceItem('ULTIMATE_WISE;4', 'easy', ctx);
  assert.equal(u.unitCost, v.unitCost); assert.ok(u.unitCost > 0);
});

test('lowest BIN is ignored for bazaar products (lowestbins lists them at insta-buy)', { skip }, () => {
  const ctx = fixtureCtx(core);
  for (const id of ['GIANT_FRAGMENT_LASER', 'WITHER_CATALYST', 'ENCHANTED_DIAMOND']) {
    assert.equal(typeof LBRAW[id], 'number', 'fixture premise');
    assert.notEqual(core.priceItem(id, 'fromScratch', ctx).method, 'lowestBin', id);
    assert.notEqual(core.priceItem(id, 'easy', ctx).method, 'lowestBin', id);
  }
});

test('vanilla cycles (IRON_INGOT <-> IRON_BLOCK) terminate and stay finite', { skip }, () => {
  const ctx = fixtureCtx(core);
  for (const id of ['IRON_INGOT', 'IRON_BLOCK', 'DIAMOND_BLOCK', 'ENCHANTED_DIAMOND_BLOCK']) {
    const p = core.priceItem(id, 'fromScratch', ctx);
    assert.ok(Number.isFinite(p.unitCost) && p.unitCost > 0, id + ' ' + JSON.stringify(p));
    assertNear(assert, p.unitCost, EXP.items[id].fromScratch.unitCost, id);
  }
});

// ---------------------------------------------------------------- synthetic graphs (hand-computed)
const R = (inputs, output = 1, coins = 0, type = 'craft') => ({ type, output, inputs: inputs.map(([id, qty]) => ({ id, qty })), coins });
function synth(core, recipes, bazaar = {}, lbin = {}) {
  return core.createCraftContext({
    fetchFn: failFetch,
    bazaar: new Map(Object.entries(bazaar)),
    lowestBin: new Map(Object.entries(lbin)),
    recipes: new Map(Object.entries(recipes)),
  });
}

test('output count > 1 divides the recipe cost (4 X from 2 A)', { skip }, () => {
  const ctx = synth(core, { X: [R([['A', 2]], 4)], TOP: [R([['X', 1]])] }, { A: { instaBuy: 10, buyOrder: 8 } });
  const px = core.priceItem('X', 'fromScratch', ctx);
  assert.equal(px.unitCost, 4); assert.equal(px.method, 'craft');
  const t = core.craftTree('X', 'fromScratch', ctx);
  assert.equal(t.total, 4); assert.equal(t.children[0].id, 'A'); assert.equal(t.children[0].qty, 0.5); assert.equal(t.children[0].total, 4);
  const e = core.craftTree('X', 'easy', ctx);
  assert.equal(e.total, 5); assert.equal(e.children[0].method, 'instaBuy');
  assert.equal(core.craftTree('TOP', 'fromScratch', ctx).total, 4);
});

test('cycles: memo never caches a result that depended on a cycle cut', { skip }, () => {
  // A <-> B; A market 50, B market 100. Price B first (B = craft from A = 50), then A (A = buy 50).
  const recipes = { A: [R([['B', 1]])], B: [R([['A', 1]])] };
  const bz = { A: { instaBuy: 60, buyOrder: 50 }, B: { instaBuy: 110, buyOrder: 100 } };
  const ctx = synth(core, recipes, bz);
  const b = core.priceItem('B', 'fromScratch', ctx), a = core.priceItem('A', 'fromScratch', ctx);
  assert.equal(b.unitCost, 50); assert.equal(b.method, 'craft');
  assert.equal(a.unitCost, 50); assert.equal(a.method, 'buyOrder');
  // pure cycle with no market anywhere: unavailable, no throw, no hang
  const ctx2 = synth(core, { P: [R([['Q', 1]])], Q: [R([['P', 1]])] });
  assert.equal(core.priceItem('P', 'fromScratch', ctx2).method, 'unavailable');
  assert.equal(core.priceItem('P', 'fromScratch', ctx2).unitCost, null);
  const tree = core.craftTree('P', 'fromScratch', ctx2);
  assert.equal(tree.total, null);
  // ingot/block pair: 9 I -> B, B -> 9 I, I bought at 100
  const ctx3 = synth(core, { I: [R([['BL', 1]], 9)], BL: [R([['I', 9]])] }, { I: { instaBuy: 120, buyOrder: 100 } });
  assert.equal(core.priceItem('BL', 'fromScratch', ctx3).unitCost, 900);
  assert.equal(core.priceItem('I', 'fromScratch', ctx3).unitCost, 100);
  // root I forced through BL: BL can only be made from I (on the stack) and has no market -> unpriceable, not a loop
  const ti = core.craftTree('I', 'fromScratch', ctx3);
  assert.equal(ti.total, null); assert.equal(ti.method, 'unavailable');
});

test('coins become a SKYBLOCK_COIN child; pseudo-currencies make a recipe unusable', { skip }, () => {
  const ctx = synth(core, {
    F: [R([['A', 1]], 1, 1000, 'forge')],
    BITS: [R([['SKYBLOCK_BIT', 100]])],
    MIX: [R([['SKYBLOCK_COPPER', 5]]), R([['A', 2]])],
  }, { A: { instaBuy: 10, buyOrder: 8 } });
  const f = core.craftTree('F', 'fromScratch', ctx);
  assert.equal(f.total, 1008); assert.equal(f.method, 'forge');
  const coin = f.children.find(c => c.id === 'SKYBLOCK_COIN');
  assert.ok(coin); assert.equal(coin.method, 'coins'); assert.equal(coin.qty, 1000); assert.equal(coin.unitCost, 1); assert.equal(coin.total, 1000);
  assert.equal(core.priceItem('BITS', 'fromScratch', ctx).method, 'unavailable');
  assert.equal(core.craftTree('BITS', 'fromScratch', ctx).total, null);
  assert.equal(core.priceItem('MIX', 'fromScratch', ctx).unitCost, 16);
  assert.equal(core.craftTree('MIX', 'easy', ctx).total, 20);
});

test('missing prices: root unavailable, missing[] names the leaf, nothing throws', { skip }, async () => {
  const ctx = synth(core, { M: [R([['A', 1], ['NOWHERE_ITEM', 2]])] }, { A: { instaBuy: 10, buyOrder: 8 } });
  const res = await core.cleanCraftCost('M', ctx);
  assert.equal(res.craftable, true);
  for (const mode of ['fromScratch', 'easy']) {
    assert.equal(res[mode].total, null, mode); assert.equal(res[mode].method, 'unavailable', mode);
    const leaf = res[mode].children.find(c => c.id === 'NOWHERE_ITEM');
    assert.equal(leaf.method, 'unavailable'); assert.equal(leaf.total, null);
  }
  assert.ok(res.missing.includes('NOWHERE_ITEM'), JSON.stringify(res.missing));
  const none = await core.cleanCraftCost('NO_RECIPE_AT_ALL', ctx);
  assert.equal(none.craftable, false); assert.equal(none.fromScratch, null); assert.equal(none.easy, null);
});

test('easy mode: unbuyable ingredient falls back to its from-scratch cost, flagged', { skip }, () => {
  const ctx = synth(core, { TOP: [R([['SUB', 1], ['A', 1]])], SUB: [R([['A', 3]])] }, { A: { instaBuy: 10, buyOrder: 8 } });
  const e = core.craftTree('TOP', 'easy', ctx);
  const sub = e.children.find(c => c.id === 'SUB');
  assert.equal(sub.unitCost, 24, 'from-scratch cost of SUB (3 x buy order 8)');
  assert.ok(/can't be bought/.test(sub.note || ''), 'flag note: ' + sub.note);
  assert.equal(e.total, 34);
  const z = core.priceItem('SUB', 'easy', ctx);
  assert.equal(z.unitCost, 24); assert.ok(z.note);
});

test('thin books: no buy orders -> insta-buy with a note; junk/zero prices are unavailable', { skip }, () => {
  const ctx = synth(core, {}, { T: { instaBuy: 20, buyOrder: null }, Z: { instaBuy: 0, buyOrder: -1 }, S: { instaBuy: 'x', buyOrder: NaN } }, { Z: 0, L: -5 });
  const t = core.priceItem('T', 'fromScratch', ctx);
  assert.equal(t.unitCost, 20); assert.equal(t.method, 'instaBuy'); assert.ok(t.note);
  for (const id of ['Z', 'S', 'L']) {
    const p = core.priceItem(id, 'fromScratch', ctx), q = core.priceItem(id, 'easy', ctx);
    assert.equal(p.unitCost, null, id); assert.equal(p.method, 'unavailable', id);
    assert.equal(q.unitCost, null, id); assert.equal(q.method, 'unavailable', id);
  }
});

const MALFORMED = () => synth(core, {
    BAD: [null, 5, { type: 'craft', output: 0, inputs: null }, { inputs: [{ id: 7, qty: 'x' }] }, { type: 'craft', output: 1, inputs: [{ id: 'A', qty: -1 }], coins: NaN }],
    HALF: [{ type: 'craft', output: 1, inputs: [null, { id: 'A', qty: 1 }], coins: 0 }],
  }, { A: { instaBuy: 10, buyOrder: 8 } });
const WEIRD = ['', null, undefined, 42, {}, 'lowercase_id', '???', 'A;', ';5', 'ENCHANTMENT__', 'LOG:', 'X'.repeat(500), 'BAD', 'HALF', 'SKYBLOCK_COIN', 'SKYBLOCK_BIT', '__proto__', 'constructor'];

test('weird ids and malformed recipes never throw (sync API)', { skip }, () => {
  const ctx = MALFORMED();
  for (const id of WEIRD) {
    for (const mode of ['fromScratch', 'easy', 'nonsense']) {
      const p = core.priceItem(id, mode, ctx);
      assert.ok(p && typeof p.method === 'string', `priceItem(${String(id)}, ${mode})`);
      assert.ok(p.unitCost === null || Number.isFinite(p.unitCost));
      const t = core.craftTree(id, mode, ctx);
      assert.ok(t === null || (typeof t === 'object' && typeof t.method === 'string'), `craftTree(${String(id)})`);
      assert.ok(Array.isArray(core.flattenTree(t)));
    }
  }
  assert.equal(core.priceItem('SKYBLOCK_BIT', 'fromScratch', ctx).method, 'unavailable');
  assert.deepEqual(core.flattenTree(null), []);
});

test('weird ids never throw (cleanCraftCost)', { skip }, async () => {
  const ctx = synth(core, { A2: [R([['A', 1]])] }, { A: { instaBuy: 10, buyOrder: 8 } });
  for (const id of WEIRD) {
    const r = await core.cleanCraftCost(id, ctx);
    assert.equal(typeof r.craftable, 'boolean', String(id)); assert.ok(Array.isArray(r.missing));
  }
});

test('malformed preloaded recipes never throw (cleanCraftCost / loadRecipeTree)', { skip }, async () => {
  const ctx = MALFORMED();
  for (const id of ['BAD', 'HALF']) {
    const r = await core.cleanCraftCost(id, ctx);
    assert.equal(typeof r.craftable, 'boolean', id); assert.ok(Array.isArray(r.missing));
  }
});

test('flattenTree: depth-first with depth, no children field', { skip }, () => {
  const ctx = fixtureCtx(core);
  const tree = core.craftTree('HYPERION', 'fromScratch', ctx);
  const flat = core.flattenTree(tree);
  assert.equal(flat[0].depth, 0); assert.equal(flat[0].id, 'HYPERION');
  let count = 0; walk(tree, () => count++);
  assert.equal(flat.length, count);
  for (let i = 1; i < flat.length; i++) assert.ok(flat[i].depth >= 1 && flat[i].depth <= flat[i - 1].depth + 1);
  assert.ok(flat.every(n => !('children' in n)));
  const blade = flat.find(n => n.id === 'NECRON_BLADE');
  assert.equal(blade.depth, 1); assert.equal(blade.method, 'craft');
  assert.ok(flat.some(n => n.id === 'NECRON_HANDLE' && n.depth === 2 && n.method === 'lowestBin'));
});

test('memo resets when ctx.bazaar is replaced', { skip }, () => {
  const ctx = synth(core, { X: [R([['A', 2]])] }, { A: { instaBuy: 10, buyOrder: 8 } });
  assert.equal(core.priceItem('X', 'fromScratch', ctx).unitCost, 16);
  ctx.bazaar = new Map([['A', { instaBuy: 10, buyOrder: 5 }]]);
  assert.equal(core.priceItem('X', 'fromScratch', ctx).unitCost, 10);
});

test('pricing the whole fixture recipe set is fast and total', { skip }, () => {
  const ctx = fixtureCtx(core);
  const t0 = Date.now();
  let priced = 0, n = 0;
  for (const id of ctx.recipes.keys()) for (const mode of ['fromScratch', 'easy']) {
    const p = core.priceItem(id, mode, ctx); n++;
    if (p.unitCost != null) priced++;
    assert.ok(p.unitCost === null || (Number.isFinite(p.unitCost) && p.unitCost >= 0), id);
  }
  assert.ok(Date.now() - t0 < 2000, 'took ' + (Date.now() - t0) + ' ms');
  assert.ok(priced > n * 0.5);
});
