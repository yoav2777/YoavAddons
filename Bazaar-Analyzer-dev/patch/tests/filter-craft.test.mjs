// AH item page filters -> synthetic listing for the craft engine (parts/15-filter-craft.js -> BA_filterListing),
// and the priced result on a Hyperion with the Wither Impact chip (engine + craft fixtures, no network).
// Run: node --test Bazaar-Analyzer-dev/patch/tests/filter-craft.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { listModifiers } from '../../craft/craftModifiers.js';
import { HYITEMS, STONES, ENCH, freshCore, freshMods, fixtureCtx, assertNear } from '../../craft/tests/fx.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
// The part is plain bundle code: run it in a sandbox. Only BA_filterListing (pure) is called; it needs the engine's
// listModifiers (BA_craft namespace in the bundle). React bits are only referenced inside functions we don't call.
const ctx = vm.createContext({ BA_craft: { listModifiers } });
vm.runInContext(fs.readFileSync(path.join(HERE, '../parts/15-filter-craft.js'), 'utf8') + '\n;globalThis.__fl=BA_filterListing;', ctx);
const fl = (tag, filters, opts) => JSON.parse(JSON.stringify(ctx.__fl(tag, filters, opts))); // plain objects from the vm realm

// chip shapes = the bundle's filter factory Ii(kind, arg) + what the chips / URL can set
let n = 0;
const F = {
  enchant: (type, min = null, max = null) => ({ id: 'f' + ++n, kind: 'enchant', type, min, max }),
  stars: (min = 1, max = null) => ({ id: 'f' + ++n, kind: 'stars', min, max }),
  hpc: (min = 1, max = null) => ({ id: 'f' + ++n, kind: 'hpc', min, max }),
  recomb: (value = true) => ({ id: 'f' + ++n, kind: 'recomb', value }),
  reforge: (value) => ({ id: 'f' + ++n, kind: 'reforge', value }),
  rarity: (value) => ({ id: 'f' + ++n, kind: 'rarity', value }),
  bin: (value = true) => ({ id: 'f' + ++n, kind: 'bin', value }),
  price: (min = null, max = null) => ({ id: 'f' + ++n, kind: 'price', min, max }),
  clean: () => ({ id: 'f' + ++n, kind: 'clean', value: true }),
  nbt: (key, value, extra = {}) => ({ id: 'f' + ++n, kind: 'nbt', key, mode: 'eq', value, min: null, max: null, ...extra }),
  range: (key, min, max = null) => ({ id: 'f' + ++n, kind: 'nbt', key, mode: 'range', value: '', min, max }),
};
const WIMP = 'IMPLOSION_SCROLL SHADOW_WARP_SCROLL WITHER_SHIELD_SCROLL';
const labels = (r) => r.skipped.map((s) => s.label);

test('no filters, or only non-modifier chips: no listing (the plain clean craft is shown)', () => {
  assert.deepEqual(fl('HYPERION', []), { item: null, key: '', skipped: [] });
  assert.deepEqual(fl('HYPERION', null), { item: null, key: '', skipped: [] });
  const r = fl('HYPERION', [F.price(1e8, 2e9), F.bin(false), F.clean(), F.recomb(false), F.stars(null), F.hpc(0)]);
  assert.equal(r.item, null);
  assert.deepEqual(labels(r), ['Sale price', 'Buy It Now / Auction']);
  assert.ok(r.skipped.every((s) => s.why === 'not a modifier'));
});

test('Wither Impact chip (ability_scroll with the three scrolls) -> the listing carries the three scrolls', () => {
  const r = fl('HYPERION', [F.nbt('ability_scroll', WIMP), F.bin()]);
  assert.equal(r.item.tag, 'HYPERION');
  assert.equal(r.item.flatNbt.ability_scroll, WIMP);
  assert.deepEqual(r.item.enchantments, []);
  assert.deepEqual(labels(r), ['Buy It Now / Auction']);
  const mods = listModifiers(r.item, null).filter((m) => m.kind === 'abilityScroll');
  assert.deepEqual(mods.map((m) => m.parts[0].id), ['IMPLOSION_SCROLL', 'SHADOW_WARP_SCROLL', 'WITHER_SHIELD_SCROLL']);
});

test('enchants: lowest level the chip allows, at least 1; two chips of one enchant keep the higher', () => {
  const r = fl('HYPERION', [F.enchant('ultimate_wise', 5), F.enchant('sharpness'), F.enchant('critical', null, 4), F.enchant('sharpness', 6), F.enchant('')]);
  assert.deepEqual(r.item.enchantments, [{ type: 'critical', level: 1 }, { type: 'sharpness', level: 6 }, { type: 'ultimate_wise', level: 5 }]);
  assert.deepEqual(r.skipped, []);
});

test('stars (dungeonized), hot potato books, recomb, reforge stone', () => {
  const r = fl('HYPERION', [F.stars(7), F.hpc(12), F.recomb(true), F.reforge('Fabled')]);
  assert.deepEqual(r.item.flatNbt, { dungeon_item: '1', hpc: '12', rarity_upgrades: '1', upgrade_level: '7' });
  assert.equal(r.item.reforge, 'Fabled');
  assert.equal(r.item.tier, undefined); // the engine derives it (item tier + recomb)
  const kinds = listModifiers(r.item, null).map((m) => m.kind);
  for (const k of ['stars', 'recomb', 'potatoBook', 'fumingBook', 'reforge']) assert.ok(kinds.includes(k), k);
  // stars above 10 / books above 15 are capped like the game
  assert.equal(fl('HYPERION', [F.stars(12), F.hpc(20)]).item.flatNbt.upgrade_level, '10');
  assert.equal(fl('HYPERION', [F.hpc(20)]).item.flatNbt.hpc, '15');
});

test('reforge: blacksmith reforges (no stone) and None add nothing', () => {
  const r = fl('HYPERION', [F.reforge('Heroic')]);
  assert.equal(r.item, null);
  assert.deepEqual(labels(r), ['Reforge Heroic']);
  assert.equal(fl('HYPERION', [F.reforge('None')]).item, null);
  assert.deepEqual(fl('HYPERION', [F.reforge('None')]).skipped, []);
});

test('rarity on gear: one tier above the item = recombobulated, its own tier = nothing, else skipped', () => {
  const L = { baseTier: 'LEGENDARY' };
  let r = fl('HYPERION', [F.rarity('MYTHIC')], L);
  assert.deepEqual(r.item.flatNbt, { rarity_upgrades: '1' });
  assert.deepEqual(r.skipped, []);
  assert.ok(listModifiers(r.item, null).some((m) => m.kind === 'recomb'));
  r = fl('HYPERION', [F.rarity('LEGENDARY')], L);
  assert.equal(r.item, null);
  assert.deepEqual(r.skipped, []);
  r = fl('HYPERION', [F.rarity('DIVINE')], L);
  assert.equal(r.item, null);
  assert.deepEqual(labels(r), ['Rarity Divine']);
  assert.match(r.skipped[0].why, /Legendary/);
  assert.deepEqual(labels(fl('HYPERION', [F.rarity('MYTHIC')])), ['Rarity Mythic']); // item tier unknown
  r = fl('HYPERION', [F.rarity('MYTHIC'), F.reforge('Suspicious')], L);
  assert.equal(r.item.tier, 'MYTHIC'); // apply cost of the reforge stone is per tier
  assert.equal(r.item.flatNbt.rarity_upgrades, '1');
  assert.deepEqual(r.skipped, []);
});

test('gems: typed slot kept + unlocked; universal slot needs the gem type chip', () => {
  let r = fl('HYPERION', [F.nbt('SAPPHIRE_0', 'PERFECT')]);
  assert.deepEqual(r.item.flatNbt, { SAPPHIRE_0: 'PERFECT', unlocked_slots: 'SAPPHIRE_0' });
  r = fl('HYPERION', [F.nbt('COMBAT_0', 'FINE')]);
  assert.equal(r.item, null);
  assert.deepEqual(labels(r), ['Combat 0']);
  assert.match(r.skipped[0].why, /Combat 0 Gem/);
  r = fl('HYPERION', [F.nbt('COMBAT_0', 'FINE'), F.nbt('COMBAT_0_gem', 'SAPPHIRE'), F.nbt('unlocked_slots', 'SAPPHIRE_0')]);
  assert.deepEqual(r.item.flatNbt, { COMBAT_0: 'FINE', COMBAT_0_gem: 'SAPPHIRE', unlocked_slots: 'SAPPHIRE_0,COMBAT_0' });
  assert.deepEqual(r.skipped, []);
  const gem = listModifiers(r.item, null).find((m) => m.kind === 'gem');
  assert.equal(gem.parts[0].id, 'FINE_SAPPHIRE_GEM');
  r = fl('HYPERION', [F.nbt('COMBAT_0_gem', 'SAPPHIRE')]); // type without quality
  assert.equal(r.item, null);
  assert.match(r.skipped[0].why, /quality/);
});

test('other nbt chips: kept when the engine prices them, skipped otherwise; empty chips are ignored', () => {
  const r = fl('TERMINATOR', [F.range('art_of_war_count', 1), F.nbt('power_ability_scroll', 'SAPPHIRE_POWER_SCROLL'),
    F.nbt('RUNE_ICE', '3'), F.nbt('dye_item', 'DYE_PURE_BLACK'), F.nbt('color', '0:0:0'), F.nbt('stats_book', '1'),
    F.nbt('ability_scroll', ''), F.range('wood_singularity_count', 0)]);
  assert.deepEqual(r.item.flatNbt, { RUNE_ICE: '3', art_of_war_count: '1', dye_item: 'DYE_PURE_BLACK', power_ability_scroll: 'SAPPHIRE_POWER_SCROLL', stats_book: '1' });
  assert.deepEqual(labels(r), ['Color']);
  assert.equal(r.skipped[0].why, 'not priced by the craft engine');
});

test('pets: tier from the page, held item / skin priced, candy and reforge not', () => {
  const r = fl('PET_ENDER_DRAGON', [F.rarity('EPIC'), F.nbt('heldItem', 'PET_ITEM_TIER_BOOST'), F.nbt('candyUsed', '0'), F.reforge('Fabled')], { tier: 'EPIC' });
  assert.equal(r.item.tier, 'EPIC');
  assert.deepEqual(r.item.flatNbt, { heldItem: 'PET_ITEM_TIER_BOOST', tier: 'EPIC', type: 'ENDER_DRAGON' });
  assert.deepEqual(labels(r), ['Candy Used', 'Reforge Fabled']); // rarity picks the tier: not skipped
  const held = listModifiers(r.item, null).find((m) => m.kind === 'petItem');
  assert.equal(held.parts[0].id, 'PET_ITEM_TIER_BOOST');
  assert.equal(fl('PET_ENDER_DRAGON', [F.rarity('EPIC')], { tier: 'EPIC' }).item, null); // tier alone: the clean pet
});

test('the key only depends on what is priced (not on chip order or ids)', () => {
  const a = fl('HYPERION', [F.nbt('ability_scroll', WIMP), F.enchant('ultimate_wise', 5), F.stars(5), F.price(1)]);
  const b = fl('HYPERION', [F.stars(5), F.enchant('ultimate_wise', 5), F.bin(), F.nbt('ability_scroll', WIMP)]);
  assert.equal(a.key, b.key);
  assert.equal(a.item.uuid, 'filters:' + a.key);
  assert.notEqual(a.key, fl('HYPERION', [F.nbt('ability_scroll', WIMP)]).key);
});

test('never throws on junk chips', () => {
  for (const junk of [[null], [1], ['x'], [{}], [{ kind: 'nbt' }], [{ kind: 'enchant', min: 'abc' }], [{ kind: 'stars', min: 'x' }]]) {
    assert.doesNotThrow(() => fl('HYPERION', junk));
  }
  assert.equal(fl('', [F.enchant('sharpness', 5)]).item.tag, '');
});

// ---- priced by the engine, same as the page does (listingCraftCost on the synthetic listing) ----
test('Hyperion + Wither Impact: craft price = clean craft + the three scrolls, both ways', async () => {
  const core = await freshCore(), mods = await freshMods();
  const c = fixtureCtx(core, {});
  c.hyItems = HYITEMS; c.reforgeStones = STONES; c.enchantTable = ENCH;
  const api = { loadRecipeTree: core.loadRecipeTree, cleanCraftCost: core.cleanCraftCost, priceItem: core.priceItem };
  const clean = await core.cleanCraftCost('HYPERION', c);
  const r = fl('HYPERION', [F.nbt('ability_scroll', WIMP), F.price(null, 2e9)]);
  const res = await mods.listingCraftCost('HYPERION', r.item, c, api);
  assert.equal(res.craftable, true);
  assert.equal(res.baseSource, 'recipe');
  for (const mode of ['fromScratch', 'easy']) {
    assert.equal(res.base[mode].total, clean[mode].total, mode + ': base = the clean craft');
    const scrolls = ['IMPLOSION_SCROLL', 'SHADOW_WARP_SCROLL', 'WITHER_SHIELD_SCROLL'].map((id) => core.priceItem(id, mode, c).unitCost);
    assert.ok(scrolls.every((v) => v > 1e8), mode + ': scrolls priced');
    const lines = res.modifiers[mode];
    assert.deepEqual(lines.map((l) => l.id), ['IMPLOSION_SCROLL', 'SHADOW_WARP_SCROLL', 'WITHER_SHIELD_SCROLL']);
    assertNear(assert, res.totals[mode] - clean[mode].total, scrolls[0] + scrolls[1] + scrolls[2], mode + ': added = the three scrolls');
  }
  // bazaar fixture: from scratch uses buy orders (sell_summary), easy insta-buys (buy_summary)
  assertNear(assert, res.modifiers.fromScratch[0].unitCost, 196444744.4, 'Implosion buy order');
  assertNear(assert, res.modifiers.easy[0].unitCost, 201999997.1, 'Implosion insta-buy');
});
