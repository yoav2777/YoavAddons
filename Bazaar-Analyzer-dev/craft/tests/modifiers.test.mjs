// craftModifiers.js against real Coflnet listing rows (fixtures/listings) and the independent reference
// (fixtures/refmods.mjs parse + fixtures/reference.mjs prices -> expected.json).
// Enchant lines: the reference follows craft-spec 5a (own book vs anvil combine, table only as a fallback); the engine
// may legitimately find something cheaper (enchanting table = XP only), so enchant lines are checked as upper bounds.
import test from 'node:test';
import assert from 'node:assert/strict';
import { EXP, HYITEMS, STONES, ENCH, LISTING_KEYS, listing, enginePresent, freshCore, freshMods, fixtureCtx, near, assertNear } from './fx.mjs';
import { parseListing } from '../fixtures/refmods.mjs';

const skip = enginePresent() ? false : 'craftCore.js / craftModifiers.js not written yet';
const core = skip ? null : await freshCore();
const mods = skip ? null : await freshMods();
const HY = {}; for (const i of HYITEMS.items) HY[i.id] = i;
const CORE_API = core && { loadRecipeTree: core.loadRecipeTree, cleanCraftCost: core.cleanCraftCost, priceItem: core.priceItem };

async function ctxWithData() {
  const ctx = fixtureCtx(core, {});
  ctx.hyItems = HYITEMS; ctx.reforgeStones = STONES; ctx.enchantTable = ENCH;
  const data = await mods.loadModifierData(ctx);
  return { ctx, data };
}
// reference group -> engine kinds
const KINDS = { enchant: ['enchant'], stars: ['stars', 'masterStar', 'dungeonize'], recomb: ['recomb'], hpb: ['potatoBook', 'fumingBook'],
  upgrade: ['upgrade'], scroll: ['abilityScroll', 'powerScroll'], gemslot: ['gemSlot'], gem: ['gem'], pet: ['petItem'],
  drill: ['drillPart'], dye: ['dye'], rune: ['rune'], reforge: ['reforge'] };
const COIN = 'SKYBLOCK_COIN';
const key = (id, qty) => id + ' x' + (Math.round(qty * 1000) / 1000);
const sorted = a => [...a].sort();

test('exports match the contract', { skip }, () => {
  for (const f of ['listModifiers', 'modifierItemIds', 'priceModifiers', 'listingCraftCost']) assert.equal(typeof mods[f], 'function', f);
});

test('listModifiers: same non-enchant parts and coins as the reference for every real listing', { skip }, async t => {
  const { data } = await ctxWithData();
  for (const k of LISTING_KEYS) {
    const L = listing(k);
    const ref = parseListing(L, HY[L.tag] || {}, STONES);
    const got = mods.listModifiers(L, data);
    assert.ok(Array.isArray(got), k);
    for (const m of got) {
      assert.equal(typeof m.kind, 'string', k); assert.equal(typeof m.label, 'string', k); assert.ok(Array.isArray(m.parts), k);
      for (const p of m.parts) { assert.equal(typeof p.id, 'string', k); assert.ok(p.qty > 0, k + ' ' + p.id); }
    }
    const refParts = ref.filter(p => p.group !== 'enchant').map(p => key(p.id, p.qty));
    const gotParts = [];
    for (const m of got) {
      if (m.kind === 'enchant' || m.kind === 'unknown') continue;
      for (const p of m.parts) gotParts.push(key(p.id, p.qty));
      if (m.coins) gotParts.push(key(COIN, m.coins));
    }
    assert.deepEqual(sorted(gotParts), sorted(refParts), k + ' parts');
    // every reference group shows up as a line of the matching kind
    for (const g of new Set(ref.map(p => p.group))) assert.ok(got.some(m => KINDS[g].includes(m.kind)), `${k}: no ${KINDS[g]} line`);
    // enchantments: one line per enchant with the same level
    const re = ref.filter(p => p.enchant).map(p => p.enchant.type + ' ' + p.enchant.lvl);
    const ge = got.filter(m => m.kind === 'enchant').map(m => (m.enchant ? m.enchant.type + ' ' + m.enchant.level : m.label.toLowerCase()));
    assert.deepEqual(sorted(ge), sorted(re), k + ' enchants');
    const unk = got.filter(m => m.kind === 'unknown');
    for (const u of unk) assert.equal(u.parts.length, 0);
    t.diagnostic(`${k}: ${got.length} modifiers${unk.length ? ', unknown: ' + unk.map(u => u.label).join(' | ') : ''}`);
  }
});

test('listModifiers: key cases from the spec', { skip }, async () => {
  const { data } = await ctxWithData();
  const has = (list, id, qty) => list.some(m => m.parts.some(p => p.id === id && (qty == null || p.qty === qty)));
  const h = mods.listModifiers(listing('hyperion-full'), data);
  assert.ok(has(h, 'ESSENCE_WITHER', 3350), 'stars 1-5 essence');
  for (const s of ['FIRST_MASTER_STAR', 'SECOND_MASTER_STAR', 'THIRD_MASTER_STAR', 'FOURTH_MASTER_STAR', 'FIFTH_MASTER_STAR']) assert.ok(has(h, s, 1), s);
  assert.ok(has(h, 'HOT_POTATO_BOOK', 10) && has(h, 'FUMING_POTATO_BOOK', 5), 'hpc 15 -> 10 + 5');
  assert.ok(has(h, 'IMPLOSION_SCROLL') && has(h, 'SHADOW_WARP_SCROLL') && has(h, 'WITHER_SHIELD_SCROLL'));
  assert.ok(has(h, 'SAPPHIRE_POWER_SCROLL'), 'power scroll');
  assert.ok(has(h, 'PERFECT_SAPPHIRE_GEM'), 'universal slot gem via COMBAT_0_gem');
  assert.ok(has(h, 'RECOMBOBULATOR_3000', 1));
  const ench = h.filter(m => m.kind === 'enchant');
  assert.ok(ench.some(m => m.parts.some(p => /^ENCHANTMENT_ULTIMATE_(WISE|REITERATE)_/.test(p.id))), 'ultimate enchant');
  // terminator: dungeonized conversion + a stone reforge with apply cost
  const tr = mods.listModifiers(listing('terminator-full'), data);
  assert.ok(tr.some(m => m.kind === 'reforge' && m.parts.some(p => p.id === 'OPTICAL_LENS') && m.coins > 0), 'Precise = OPTICAL_LENS + coins');
  // sold row: flattenedNbt, reforge only in itemName ("Heroic" = blacksmith, no stone)
  const s = mods.listModifiers(listing('hyperion-sold'), data);
  assert.ok(s.length > 10);
  assert.ok(!s.some(m => m.kind === 'reforge' && m.parts.length), 'Heroic has no stone');
  // pet: held item, no reforge
  const p = mods.listModifiers(listing('pet-ender-dragon'), data);
  assert.ok(has(p, 'CROCHET_TIGER_PLUSHIE', 1));
  assert.ok(!p.some(m => m.kind === 'reforge'));
  // synthetic: rune, dye, wood singularity, etherwarp
  const x = mods.listModifiers(listing('synthetic-extras'), data);
  for (const id of ['RUNE-SOULTWIST-3', 'DYE_LAVA', 'WOOD_SINGULARITY', 'ETHERWARP_MERGER', 'ETHERWARP_CONDUIT']) assert.ok(has(x, id, 1), id);
  // divan chestplate: 5 slots unlocked with GEMSTONE_CHAMBER + 5 perfect gems
  const d = mods.listModifiers(listing('divan-chestplate-gems'), data);
  assert.equal(d.filter(m => m.kind === 'gem').length, 5);
  assert.ok(d.filter(m => m.kind === 'gemSlot').every(m => m.parts.some(q => q.id === 'GEMSTONE_CHAMBER')));
  // drill: parts + Glacial (FRIGID_HUSK) with tier UNKNOWN resolved from the items API
  const dr = mods.listModifiers(listing('divan-drill'), data);
  assert.ok(dr.filter(m => m.kind === 'drillPart').length === 3);
  assert.ok(dr.some(m => m.kind === 'reforge' && m.parts.some(q => q.id === 'FRIGID_HUSK') && m.coins > 0));
});

test('unrecognized modifiers come back as kind unknown, priced as nothing', { skip }, async () => {
  const { ctx, data } = await ctxWithData();
  const L = { ...listing('terminator-full'), flatNbt: { ...listing('terminator-full').flatNbt, brand_new_upgrade_count: '3' } };
  const got = mods.listModifiers(L, data);
  const u = got.filter(m => m.kind === 'unknown');
  assert.ok(u.some(m => /brand_new_upgrade_count/.test(m.label)), JSON.stringify(u));
  const lines = mods.priceModifiers(L, 'fromScratch', ctx, core.priceItem);
  for (const l of lines.filter(l => l.kind === 'unknown')) assert.ok(l.total == null || l.total === 0);
});

test('modifierItemIds: buyable part ids, no coins / enchant books', { skip }, async () => {
  const { data } = await ctxWithData();
  const ids = mods.modifierItemIds(listing('hyperion-full'), data);
  assert.ok(Array.isArray(ids));
  for (const id of ['HOT_POTATO_BOOK', 'FUMING_POTATO_BOOK', 'SAPPHIRE_POWER_SCROLL', 'PERFECT_SAPPHIRE_GEM', 'RECOMBOBULATOR_3000']) assert.ok(ids.includes(id), id);
  assert.ok(!ids.includes(COIN));
  assert.ok(!ids.some(id => /^ENCHANTMENT_/.test(id)));
  assert.equal(new Set(ids).size, ids.length, 'no duplicates');
});

test('priceModifiers: every non-enchant line matches the reference price; enchant lines never exceed it', { skip }, async t => {
  const { ctx } = await ctxWithData();
  for (const k of LISTING_KEYS) {
    const L = listing(k), exp = EXP.listings[k];
    for (const mode of ['fromScratch', 'easy']) {
      const lines = mods.priceModifiers(L, mode, ctx, core.priceItem);
      assert.ok(Array.isArray(lines), k);
      for (const l of lines) {
        assert.equal(typeof l.method, 'string', `${k} ${mode} ${l.label}`);
        assert.ok(l.total == null || (Number.isFinite(l.total) && l.total >= 0), `${k} ${mode} ${l.label} total ${l.total}`);
        if (l.total != null && l.unitCost != null) assertNear(assert, l.total, l.unitCost * l.qty, `${k} ${mode} ${l.label} total = unit*qty`);
      }
      // non-enchant lines: multiset match on (id, qty) then compare totals
      const refRest = exp.lines[mode].filter(r => r.group !== 'enchant');
      const pool = refRest.slice();
      for (const l of lines.filter(l => l.kind !== 'enchant' && l.kind !== 'unknown' && !(l.id === COIN && !l.qty) && l.id != null)) {
        const i = pool.findIndex(r => r.id === l.id && near(r.qty, l.qty));
        assert.ok(i >= 0, `${k} ${mode}: engine line ${l.id} x${l.qty} not in reference`);
        const r = pool.splice(i, 1)[0];
        assertNear(assert, l.total, r.total, `${k} ${mode} ${l.id} x${l.qty}`);
        if (r.method !== 'coins') assert.equal(l.method === 'other' ? 'craft' : l.method, r.method === 'other' ? 'craft' : r.method, `${k} ${mode} ${l.id} method`);
      }
      assert.deepEqual(pool.map(r => r.id), [], `${k} ${mode}: reference lines the engine did not produce`);
      // enchant lines: engine <= reference (reference = own book vs anvil; engine may use the table or a cheaper lower book)
      const refE = exp.lines[mode].filter(r => r.group === 'enchant');
      const gotE = lines.filter(l => l.kind === 'enchant');
      assert.equal(gotE.length, refE.length, `${k} ${mode} enchant line count`);
      let refSum = 0, gotSum = 0;
      for (const r of refE) {
        const g = gotE.find(l => l.label.toLowerCase().replace(/ /g, '_') === r.label.replace(/ /g, '_').toLowerCase()) ||
                  gotE.find(l => l.id === r.id);
        assert.ok(g, `${k} ${mode}: enchant ${r.label} missing (engine labels: ${gotE.map(l => l.label)})`);
        const gt = g.total == null ? 0 : g.total;
        assert.ok(gt <= (r.total || 0) * (1 + 1e-9) + 0.01, `${k} ${mode} ${r.label}: engine ${gt} > reference ${r.total} (${r.method})`);
        if (g.method === 'table') {
          const max = ENCH.max_xp_table_levels[r.label.split(' ')[0]];
          assert.ok(max != null && Number(r.label.split(' ').pop()) <= max, `${k} ${r.label} priced as table above the table max`);
        }
        refSum += r.total || 0; gotSum += gt;
      }
      t.diagnostic(`${k} ${mode}: enchants engine ${Math.round(gotSum).toLocaleString('en-US')} vs reference ${Math.round(refSum).toLocaleString('en-US')}`);
    }
  }
});

test('listingCraftCost: base = clean craft, totals between (base + non-enchant) and (base + non-enchant + reference enchants)', { skip }, async t => {
  for (const k of LISTING_KEYS) {
    const { ctx } = await ctxWithData();
    const L = listing(k), exp = EXP.listings[k];
    const r = await mods.listingCraftCost(L.tag, L, ctx, CORE_API);
    assert.ok(r && r.base && r.modifiers && r.totals && Array.isArray(r.missing), k);
    if (r.baseId !== undefined) assert.equal(r.baseId, exp.rootId, k + ' base id (pets: TYPE;tierIndex)');
    for (const mode of ['fromScratch', 'easy']) {
      const craftBase = exp.base[mode];
      const b = r.base[mode];
      // no recipe: either base null + totals = modifiers only (spec 4b), or the clean item priced at market
      let base;
      if (craftBase == null && b == null) { base = 0; assert.equal(r.craftable, false, k); }
      else {
        base = craftBase != null ? craftBase : exp.marketBase[mode];
        assert.ok(b, `${k} ${mode} base`);
        assertNear(assert, b.total != null ? b.total : b.unitCost, base, `${k} ${mode} base`);
      }
      const lines = exp.lines[mode];
      const rest = lines.filter(l => l.group !== 'enchant').reduce((a, l) => a + (l.total || 0), 0);
      const ench = lines.filter(l => l.group === 'enchant').reduce((a, l) => a + (l.total || 0), 0);
      const tot = r.totals[mode];
      assert.ok(Number.isFinite(tot), `${k} ${mode} total ${tot}`);
      assert.ok(tot >= base + rest - 1 && tot <= base + rest + ench + 1,
        `${k} ${mode}: total ${tot} outside [${base + rest}, ${base + rest + ench}]`);
      if (craftBase != null && mode === 'fromScratch') assert.ok(tot <= exp.total.fromScratch + 1, 'never above the spec total');
      t.diagnostic(`${k} ${mode}: engine ${Math.round(tot).toLocaleString('en-US')} (reference ${Math.round(exp.total[mode] + (craftBase == null ? base : 0)).toLocaleString('en-US')})`);
    }
    assert.ok(r.totals.fromScratch <= r.totals.easy + 1, `${k}: fromScratch ${r.totals.fromScratch} > easy ${r.totals.easy}`);
  }
});

test('odd / empty listing data never throws', { skip }, async () => {
  const { ctx, data } = await ctxWithData();
  const base = listing('hyperion-full');
  const odd = [
    null, undefined, 0, 'str', [], {}, { tag: 'HYPERION' }, { flatNbt: null }, { flattenedNbt: 'x' }, { enchantments: 'x' },
    { enchantments: [null, { type: null, level: 5 }, { type: 'sharpness', level: 'abc' }, { type: 'sharpness', level: -3 }, { type: 'sharpness', level: 99 }] },
    { enchantments: { sharpness: 5 } },
    { tag: 'HYPERION', flatNbt: { hpc: 'abc', upgrade_level: '99', rarity_upgrades: 'yes', unlocked_slots: 5, COMBAT_0: 'SHINY', ability_scroll: 12, RUNE_: '', RUNE_X: 'NaN', art_of_war_count: '-1' } },
    { tag: 'PET_UNKNOWN', tier: 'NOPE', flatNbt: { type: 'X', exp: '1', heldItem: '' } },
    { tag: 'DIVAN_DRILL', tier: 'UNKNOWN', reforge: 'NotAReforge', flatNbt: { drill_part_engine: '', unlocked_slots: 'JADE_9,,MINING_x' } },
    { tag: 'HYPERION', itemName: '✿ Withered Hyperion ✪✪✪', flattenedNbt: {} },
    { nbtData: { data: { enchantments: { sharpness: 5 }, gems: { COMBAT_0: { quality: 'PERFECT' }, COMBAT_0_gem: 'SAPPHIRE', unlocked_slots: ['COMBAT_0'] }, runes: { SOULTWIST: 3 }, petInfo: '{bad json' } } },
    { ...base, flatNbt: { ...base.flatNbt, upgrade_level: '10', dungeon_item_level: '5' } },
  ];
  const throwing = () => { throw new Error('boom'); };
  for (const item of odd) {
    const label = JSON.stringify(item) || String(item);
    assert.ok(Array.isArray(mods.listModifiers(item, data)), label);
    assert.ok(Array.isArray(mods.listModifiers(item)), label + ' (no data)');
    assert.ok(Array.isArray(mods.modifierItemIds(item, data)), label);
    for (const mode of ['fromScratch', 'easy']) {
      assert.ok(Array.isArray(mods.priceModifiers(item, mode, ctx, core.priceItem)), label);
      const bad = mods.priceModifiers(item, mode, ctx, throwing);
      assert.ok(Array.isArray(bad) && bad.every(l => l.total == null || l.total === 0 || l.method === 'coins'), label + ' with a throwing priceItem');
      assert.ok(Array.isArray(mods.priceModifiers(item, mode, {}, undefined)), label + ' no priceItem');
    }
    const r = await mods.listingCraftCost(item && item.tag, item, ctx, CORE_API);
    assert.ok(r && Array.isArray(r.missing), label);
  }
  // no core at all, no tag
  const r = await mods.listingCraftCost(undefined, {}, {}, {});
  assert.ok(r && Array.isArray(r.missing));
});
