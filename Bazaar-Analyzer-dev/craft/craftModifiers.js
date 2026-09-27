// craftModifiers.js - price the modifiers of ONE AH listing (enchants, stars, books, gems, reforge...) on top of
// the clean craft cost. Plain ES module, no imports. The site patch strips `export` and inlines this file after
// craftCore.js. Spec: notes/craft-spec.md section 5. Docs: notes/craft-modifiers.md.
//
// Input `item` = a Coflnet row: active BIN / auction detail (`flatNbt`, `reforge`, `tier`, `enchantments`), sold row
// (`flattenedNbt`, no `reforge`: parsed from `itemName`) or only raw `nbtData.data` (typed NBT, flattened here).
// Static data: Hypixel items API (star + gem slot costs, tier, name) loaded by loadModifierData(ctx); reforge
// stones and enchanting-table max levels are small tables below (from NEU constants, repo commit a332242).

const CM_ITEMS_URL = 'https://api.hypixel.net/v2/resources/skyblock/items';
const CM_COIN = 'SKYBLOCK_COIN';
const CM_TIERS = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY', 'MYTHIC', 'DIVINE', 'SPECIAL', 'VERY_SPECIAL'];
const CM_PET_TIERS = CM_TIERS.slice(0, 6);
const CM_MASTER = ['FIRST_MASTER_STAR', 'SECOND_MASTER_STAR', 'THIRD_MASTER_STAR', 'FOURTH_MASTER_STAR', 'FIFTH_MASTER_STAR'];
const CM_QUAL = ['ROUGH', 'FLAWED', 'FINE', 'FLAWLESS', 'PERFECT'];
// NBT enchant name -> bazaar name (Duplex was renamed Reiterate; NEU enchant_mapping)
const CM_ALIAS = { ultimate_duplex: 'ULTIMATE_REITERATE', dragon_tracer: 'AIMING', turbo_cocoa: 'TURBO_COCO', turbo_cacti: 'TURBO_CACTUS' };
// level grows by use: only the level-1 book is bought
const CM_USE_LEVELED = new Set(['champion', 'compact', 'cultivating', 'expertise', 'hecatomb', 'toxophilite']);
// flat key -> [item id, label]; value = how many were applied
const CM_COUNTED = {
  art_of_war_count: ['THE_ART_OF_WAR', 'Art of War'], wood_singularity_count: ['WOOD_SINGULARITY', 'Wood Singularity'],
  farming_for_dummies_count: ['FARMING_FOR_DUMMIES', 'Farming for Dummies'], tuned_transmission: ['TRANSMISSION_TUNER', 'Transmission Tuner'],
  polarvoid: ['POLARVOID_BOOK', 'Polarvoid Book'], bookworm_books: ['BOOKWORM_BOOK', "Bookworm's Favorite Book"],
  jalapeno_count: ['JALAPENO_BOOK', 'Jalapeno Book'], mana_disintegrator_count: ['MANA_DISINTEGRATOR', 'Mana Disintegrator'],
};
// flat key present (value is a flag or a counter, e.g. stats_book = kills) -> 1 item
const CM_FLAGS = {
  artOfPeaceApplied: ['THE_ART_OF_PEACE', 'Art of Peace'], stats_book: ['BOOK_OF_STATS', 'Book of Stats'],
  divan_powder_coating: ['DIVAN_POWDER_COATING', 'Divan Powder Coating'],
};
const CM_DRILL = { engine: ['drill_part_engine', 'engine.id'], fuel_tank: ['drill_part_fuel_tank', 'fuel_tank.id'], upgrade_module: ['drill_part_upgrade_module', 'upgrade_module.id'] };
// keys that carry no buyable value (ids, counters, cosmetics we can't price, pet state)
const CM_IGNORE = new Set(['uid', 'uuid', 'uniqueId', 'timestamp', 'originTag', 'color', 'cc', 'boss_tier', 'bossId', 'spawnedFor',
  'compact_blocks', 'drill_fuel', 'hideInfo', 'hideRightClick', 'noMove', 'active', 'petSoulbound', 'exp', 'candyUsed', 'tier', 'type',
  'heldItemUuid', 'winning_bid', 'anvil_uses', 'modifier', 'hot_potato_count', 'hpc', 'dungeon_item', 'upgrade_level',
  'dungeon_item_level', 'unlocked_slots', 'ethermerge', 'ability_scroll', 'power_ability_scroll', 'rarity_upgrades', 'heldItem',
  'dye_item', 'skin', 'donated_museum', 'item_durability', 'baseStatBoostPercentage', 'item_tier', 'is_shiny', 'edition', 'recipient_id',
  'recipient_name', 'new_years_cake', 'party_hat_color', 'party_hat_emoji', 'year', 'raider_kills', 'zombie_kills', 'eman_kills',
  'enchantments', 'gems', 'runes', 'petInfo', 'attributes', 'extraAttributes', 'id', 'name', 'lore', 'count']);
const CM_IGNORE_RE = /(uuid|Uuid|_uid|_xp|_kills?|_time|_timestamp|_date|_at|_blocks|_count_mob|\.id)$/;

// reforgeName (lowercase, non-letters removed) -> [stone item, apply cost COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC, DIVINE]
// Not listed (Heroic, Sharp, Spicy, ...) = blacksmith reforge (random, not counted).
const CM_STONES = {
  ambered:["AMBER_MATERIAL",20000,40000,80000,150000,300000,600000,800000],
  ancient:["PRECURSOR_GEAR",10000,20000,30000,40000,50000,60000],
  auspicious:["ROCK_GEMSTONE",20000,40000,80000,150000,300000,600000,600000],
  beady:["BEADY_EYES",10000,20000,50000,75000,100000,150000],
  blazing:["BLAZEN_SPHERE",20000,40000,80000,150000,300000,300000,300000],
  blessed:["BLESSED_FRUIT",10000,10000,10000,10000,10000,10000],
  bloodsoaked:["PRESUMED_GALLON_OF_RED_PAINT",7500,15000,30000,75000,150000,300000,400000],
  bloodshot:["SHRIVELED_CORNEA",50000,100000,250000,500000,1000000,2500000],
  blooming:["FLOWERING_BOUQUET",5000,10000,20000,50000,100000,200000],
  bountiful:["GOLDEN_BALL",20000,40000,80000,150000,300000,600000],
  bulky:["BULKY_STONE",20000,40000,80000,150000,300000,600000],
  bustling:["SKYMART_BROCHURE",1000,2000,3000,6000,10000,15000],
  buzzing:["CLIPPED_WINGS",10000,20000,50000,75000,100000,150000,250000],
  calcified:["CALCIFIED_HEART",20000,40000,80000,150000,300000,600000,800000],
  candied:["CANDY_CORN",20000,40000,80000,150000,300000,600000],
  chomp:["KUUDRA_MANDIBLE",20000,40000,80000,150000,300000,600000],
  coldfused:["ENTROPY_SUPPRESSOR",60000,125000,250000,500000,1000000,2000000],
  cubic:["MOLTEN_CUBE",4000,7500,15000,40000,75000,150000],
  deepfried:["HASHBROWN",20000,40000,80000,150000,300000],
  dimensional:["TITANIUM_TESSERACT",15000,30000,60000,125000,250000,500000,500000],
  dirty:["DIRT_BOTTLE",1000,5000,10000,15000,50000,75000], earthy:["LARGE_WALNUT",5000,10000,20000,50000,100000],
  empowered:["SADAN_BROOCH",60000,125000,250000,500000,1000000,2000000],
  erudite:["DAEDALUS_NOTES",null,null,500000,1000000,2000000,2000000],
  fabled:["DRAGON_CLAW",60000,125000,250000,500000,1000000,2000000],
  fanged:["FULL_JAW_FANGING_KIT",10000,12500,25000,50000,100000,250000],
  festive:["FROZEN_BAUBLE",25000,75000,150000,250000,400000,600000],
  fleet:["DIAMONITE",15000,30000,60000,125000,250000,500000,500000],
  fortified:["METEOR_SHARD",7500,15000,30000,75000,150000,300000,400000],
  fruitful:["ONYX",100,250,500,1000,2500,5000,10000],
  giant:["GIANT_TOOTH",60000,125000,250000,500000,1000000,2000000],
  gilded:["MIDAS_JEWEL",null,null,null,null,5000000,10000000],
  glacial:["FRIGID_HUSK",20000,40000,80000,150000,300000,600000,800000],
  glistening:["SHINY_PRISM",7500,15000,30000,75000,150000,300000,400000],
  greaterspook:["BOO_STONE",null,null,null,10000,10000],
  groovy:["MANGROVE_GEM",10000,15000,20000,30000,40000,50000,50000],
  headstrong:["SALMON_OPAL",15000,30000,60000,125000,250000,500000],
  heated:["HOT_STUFF",20000,40000,80000,150000,300000,600000,600000],
  hyper:["ENDSTONE_GEODE",5000,10000,20000,50000,100000,200000],
  jaded:["JADERALD",20000,40000,80000,150000,300000,600000], jerrys:["JERRY_STONE",1,2,3],
  loving:["RED_SCARF",30000,75000,150000,300000,600000,1200000],
  lucky:["LUCKY_DICE",20000,40000,80000,150000,300000,600000],
  lunar:["MOONSTONE",5000,10000,20000,50000,100000,200000,200000],
  lustrous:["GLEAMING_CRYSTAL",20000,40000,80000,150000,300000,600000,800000],
  magnetic:["LAPIS_CRYSTAL",250,500,1000,2500,5000,10000,15000],
  majestic:["MORNING_DEW",5000,25000,100000,500000,1000000,2000000],
  mantid:["MANTID_CLAW",7500,15000,30000,75000,150000,150000,150000],
  marshy:["MARSHROOM",5000,25000,100000,250000,500000,1000000],
  mithraic:["PURE_MITHRIL",15000,30000,60000,125000,250000,500000,500000],
  moil:["MOIL_LOG",1000,5000,10000,15000,50000,75000],
  moonglade:["MOONGLADE_JEWEL",10000,15000,20000,30000,40000,50000,50000],
  mossy:["OVERGROWN_GRASS",20000,40000,80000,150000,300000,600000],
  necrotic:["NECROMANCER_BROOCH",20000,40000,80000,150000,300000,1000000],
  overpriced:["OVERPRICED_DRINK",20000,40000,80000,150000,300000],
  perfect:["DIAMOND_ATOM",25000,50000,150000,300000,600000,800000],
  pitchin:["PITCHIN_KOI",5000,20000,40000,80000,120000,280000],
  precise:["OPTICAL_LENS",30000,75000,150000,300000,600000,1200000],
  refined:["REFINED_AMBER",10000,10000,10000,10000,10000,10000,10000],
  reinforced:["RARE_DIAMOND",2500,5000,10000,25000,50000,100000],
  renowned:["DRAGON_HORN",60000,125000,250000,500000,1000000,2000000],
  ridiculous:["RED_NOSE",7500,15000,30000,75000,150000,300000],
  rooted:["BURROWING_SPORES",20000,40000,80000,150000,300000,600000],
  royal:["DWARVEN_TREASURE",5000,10000,20000,50000,100000,100000,100000],
  salty:["SALT_CUBE",2500,10000,20000,40000,80000,120000],
  scraped:["POCKET_ICEBERG",15000,30000,60000,125000,250000,500000,500000],
  snowy:["TERRY_SNOWGLOBE",10000,25000,50000,100000,200000,300000],
  spiked:["DRAGON_SCALE",30000,75000,150000,300000,600000,2000000],
  spiritual:["SPIRIT_DECOY",60000,125000,250000,500000,1000000,2000000],
  squeaky:["SQUEAKY_TOY",7500,15000,30000,75000,150000,150000,150000],
  stellar:["PETRIFIED_STARFALL",25000,50000,100000,200000,400000,800000,800000],
  sticky:["EXTREMELY_MILD_ADHESIVE",null,10000,25000,100000,250000,500000],
  stiff:["HARDENED_WOOD",4000,7500,15000,40000,75000,150000],
  strengthened:["SEARING_STONE",7500,15000,30000,75000,150000,300000,400000],
  submerged:["DEEP_SEA_ORB",50000,150000,350000,600000,750000,800000],
  sunny:["SUNSTONE",5000,10000,20000,50000,100000,200000,200000],
  suspicious:["SUSPICIOUS_VIAL",60000,125000,250000,500000,1000000,2000000],
  thorny:["BLOOMING_THORNS",20000,40000,80000,150000,300000,600000],
  toil:["TOIL_LOG",10000,10000,10000,10000,10000,10000],
  trashy:["OVERFLOWING_TRASH_CAN",2500,10000,20000,40000,80000,120000,500000],
  treacherous:["RUSTY_ANCHOR",5000,20000,40000,80000,120000,280000],
  undead:["PREMIUM_FLESH",5000,15000,30000,75000,150000,300000],
  warped:["AOTE_STONE",null,null,5000000,5000000,5000000],
  waxed:["BLAZE_WAX",7500,15000,30000,75000,150000,300000,400000],
  withered:["WITHER_BLOOD",10000,20000,30000,40000,50000,60000]
};
// enchanting table max level per enchant (XP only, no coins). NEU enchants.json max_xp_table_levels.
const CM_TABLE_MAX = {
  sharpness:5, smite:5, bane_of_arthropods:5, looting:3, cubism:5, cleave:5, life_steal:3, giant_killer:5, critical:5,
  first_strike:4, triple_strike:4, ender_slayer:5, execute:5, thunderlord:5, lethality:5, syphon:3, vampirism:5,
  venomous:5, thunderbolt:5, prosecute:5, titan_killer:5, luck:5, protection:5, blast_protection:5,
  projectile_protection:5, fire_protection:5, thorns:3, growth:5, frost_walker:2, feather_falling:5, depth_strider:3,
  aqua_affinity:1, respiration:3, silk_touch:1, smelting_touch:1, fortune:3, experience:3, efficiency:5, harvesting:5,
  piscary:5, spiked_hook:5, caster:5, frail:5, angler:5, chance:3, power:5, infinite_quiver:5, lure:5, magnet:5,
  luck_of_the_sea:5, scavenger:3
};

let CM_DATA = null;              // last loaded modifier data (shared by all contexts on the page)
let CM_ITEMS_INFLIGHT = null;    // Promise<Map> for the Hypixel items request
let CM_ITEMS_FAIL = null;        // { at, msg } of the last failed items request (retried after CM_ITEMS_RETRY)
const CM_ITEMS_RETRY = 60 * 1000;
const CM_MARKET = new Set(['buyOrder', 'instaBuy', 'lowestBin']);

// ---------------------------------------------------------------- small helpers
function cmNum(v) { const n = typeof v === 'number' ? v : Number(v); return Number.isFinite(n) ? n : 0; }
function cmOn(v) { return v != null && v !== '' && v !== 0 && v !== false && !/^(0|false|none)$/i.test(String(v)); }
function cmNorm(s) { return String(s || '').toLowerCase().replace(/[^a-z]/g, ''); }
function cmPretty(s) { return String(s || '').toLowerCase().split(/[_\s]+/).filter(Boolean).map(w => w[0].toUpperCase() + w.slice(1)).join(' '); }
function cmIsAbort(e) { return !!e && e.name === 'AbortError'; }
function cmAbortErr() { const e = new Error('Aborted'); e.name = 'AbortError'; return e; }

// Hypixel items in any shape (API json, items array, Map, {id: item}) -> Map<id, item>
function cmItemMap(x) {
  if (!x) return null;
  if (x instanceof Map) return x;
  const arr = Array.isArray(x) ? x : Array.isArray(x.items) ? x.items : null;
  if (arr) { const m = new Map(); for (const i of arr) if (i && i.id) m.set(i.id, i); return m; }
  if (typeof x === 'object') return new Map(Object.entries(x));
  return null;
}

// NEU reforgestones.json override -> CM_STONES shape
function cmStoneMap(x) {
  if (!x || typeof x !== 'object') return CM_STONES;
  const o = {};
  for (const k in x) {
    const s = x[k];
    if (!s || !s.reforgeName) continue;
    o[cmNorm(s.reforgeName)] = [s.internalName || k, ...CM_TIERS.slice(0, 7).map(t => (s.reforgeCosts && s.reforgeCosts[t] != null ? s.reforgeCosts[t] : null))];
  }
  return o;
}

function cmData(ctx) { return (ctx && ctx.modData) || CM_DATA || { items: new Map(), stones: CM_STONES, tableMax: CM_TABLE_MAX }; }

// Flattened NBT with string-ish values: flatNbt (active/detail) | flattenedNbt (sold) | raw nbtData.data flattened.
function cmFlat(item) {
  if (!item || typeof item !== 'object') return {};
  const f = item.flatNbt || item.flattenedNbt;
  if (f && typeof f === 'object') return f;
  const raw = item.nbtData && item.nbtData.data;
  if (!raw || typeof raw !== 'object') return {};
  const o = {};
  for (const k in raw) {
    const v = raw[k];
    if (k === 'gems' && v && typeof v === 'object') {
      for (const g in v) { const x = v[g]; o[g] = Array.isArray(x) ? x.join(',') : x && typeof x === 'object' ? x.quality : x; }
    } else if (k === 'runes' && v && typeof v === 'object') {
      for (const r in v) o['RUNE_' + r] = v[r];
    } else if (k === 'petInfo') {
      let p = v;
      if (typeof p === 'string') { try { p = JSON.parse(p); } catch { p = null; } }
      if (p && typeof p === 'object') for (const pk in p) if (p[pk] == null || typeof p[pk] !== 'object') o[pk] = p[pk];
    } else if (Array.isArray(v)) o[k] = v.join(' ');
    else if (v == null || typeof v !== 'object') o[k] = v;
  }
  return o;
}

function cmEnchants(item) {
  let e = item.enchantments;
  if ((!e || (Array.isArray(e) && !e.length)) && item.nbtData && item.nbtData.data && item.nbtData.data.enchantments) e = item.nbtData.data.enchantments;
  const out = [];
  if (Array.isArray(e)) { for (const x of e) if (x && x.type) out.push([String(x.type).toLowerCase(), cmNum(x.level)]); }
  else if (e && typeof e === 'object') { for (const k in e) out.push([k.toLowerCase(), cmNum(e[k])]); }
  return out.filter(x => x[1] > 0);
}

function cmIsPet(tag, f) { return /^PET_/.test(tag) && (f.type != null || f.exp != null || f.candyUsed != null); }

// Pet base tier: petInfo tier is the real one; the row tier is +1 with a Tier Boost held.
function cmPetTier(item, f) {
  if (f.tier && CM_PET_TIERS.includes(String(f.tier).toUpperCase())) return String(f.tier).toUpperCase();
  let i = CM_PET_TIERS.indexOf(String(item.tier || '').toUpperCase());
  if (i < 0) return null;
  if (f.heldItem === 'PET_ITEM_TIER_BOOST' && i > 0) i--;
  return CM_PET_TIERS[i];
}

// Reforge name: row field (active/detail), raw NBT `modifier`, else the words before the item name (sold rows).
// Minecraft colour codes (§6) are dropped first; "Shiny " (is_shiny items) is not part of the reforge.
function cmClean(s) { return String(s || '').replace(/§./g, '').replace(/[^A-Za-z0-9' -]/g, ' ').replace(/\s+/g, ' ').trim(); }
function cmReforge(item, f, hy, stones) {
  let rf = item.reforge != null ? item.reforge : f.modifier;
  if (rf == null && item.itemName) {
    let name = cmClean(item.itemName);
    const base = hy && hy.name ? cmClean(hy.name) : '';
    if (cmOn(f.is_shiny) && /^Shiny /.test(name) && !/^Shiny /.test(base)) name = name.slice(6);
    if (base) {
      const i = name.indexOf(base);
      if (i > 0) rf = name.slice(0, i).trim();
      else if (i === 0) rf = null;
      // extra leading word(s) ("Shiny Withered" without the flag): keep the longest tail that is a stone reforge
      if (rf && !stones[cmNorm(rf)]) {
        const w = rf.split(' ');
        for (let k = 1; k < w.length; k++) if (stones[cmNorm(w.slice(k).join(' '))]) { rf = w.slice(k).join(' '); break; }
      }
    } else {
      // no item name to compare with: accept only a known stone reforge as the first word(s)
      const words = name.split(' ');
      for (let n = 2; n >= 1 && rf == null; n--) if (words.length > n && stones[cmNorm(words.slice(0, n).join(' '))]) rf = words.slice(0, n).join(' ');
    }
  }
  if (!rf || /^none$/i.test(String(rf))) return null;
  return String(rf);
}

function cmItemTier(item, f, hy) {
  const t = String(item.tier || '').toUpperCase();
  if (CM_TIERS.includes(t)) return t;
  if (hy && CM_TIERS.includes(hy.tier)) return CM_TIERS[Math.min(CM_TIERS.indexOf(hy.tier) + (cmNum(f.rarity_upgrades) > 0 ? 1 : 0), CM_TIERS.length - 1)];
  return null;
}

// ---------------------------------------------------------------- parse
// -> [{ kind, label, parts: [{id, qty}], coins?, note?, enchant? }]
// kinds: enchant, stars, masterStar, dungeonize, recomb, potatoBook, fumingBook, upgrade, abilityScroll, powerScroll,
//        gemSlot, gem, reforge, drillPart, dye, rune, petItem, petSkin, skin, unknown
// `data` (optional) = loadModifierData result; without it star/gem-slot costs come back as notes.
export function listModifiers(item, data) {
  const out = [];
  if (!item || typeof item !== 'object') return out;
  const d = data || cmData(null);
  const f = cmFlat(item);
  const tag = String(item.tag || f.id || '').toUpperCase();
  const hy = (d.items && d.items.get && d.items.get(tag)) || null;
  const stones = d.stones || CM_STONES;
  const pet = cmIsPet(tag, f);
  const used = new Set();
  const add = (m, ...keys) => { out.push(m); for (const k of keys) used.add(k); };

  // enchantments
  for (const [type, level] of cmEnchants(item)) {
    const name = CM_ALIAS[type] || type.toUpperCase();
    const label = cmPretty(type) + ' ' + level;
    if (CM_USE_LEVELED.has(type) && level > 1)
      add({ kind: 'enchant', label, parts: [{ id: 'ENCHANTMENT_' + name + '_1', qty: 1 }], note: 'levels up by use; priced as level 1', enchant: { type, name, level, fixed: true } });
    else if (type === 'efficiency' && level > 5)
      add({ kind: 'enchant', label, parts: [{ id: 'SIL_EX', qty: level - 5 }], note: 'levels 6+ need one Silex each; 1-5 from the enchanting table', enchant: { type, name, level, fixed: true } });
    else add({ kind: 'enchant', label, parts: [{ id: 'ENCHANTMENT_' + name + '_' + level, qty: 1 }], enchant: { type, name, level } });
  }

  // dungeon conversion essence (only items that aren't natively dungeon have a cost); merged into the stars line
  const cc = cmOn(f.dungeon_item) && hy && hy.dungeon_item_conversion_cost;
  let conv = cc && cc.essence_type ? { id: 'ESSENCE_' + cc.essence_type, qty: cmNum(cc.amount) } : null;
  // stars: essence (+ items/coins) per star from the items API, master stars 6-10
  const stars = f.upgrade_level != null ? cmNum(f.upgrade_level) : cmNum(f.dungeon_item_level);
  if (stars > 0) {
    const uc = (hy && Array.isArray(hy.upgrade_costs)) ? hy.upgrade_costs : null;
    if (!uc || !uc.length) {
      add({ kind: 'stars', label: stars + ' stars', parts: [], note: hy ? 'no star cost data for this item' : 'item data not loaded' });
    } else {
      const n = Math.min(stars, uc.length), tot = new Map();
      let coins = 0;
      for (let i = 0; i < n; i++) for (const c of uc[i] || []) {
        if (!c) continue;
        if (c.type === 'ESSENCE' && c.essence_type) tot.set('ESSENCE_' + c.essence_type, (tot.get('ESSENCE_' + c.essence_type) || 0) + cmNum(c.amount));
        else if (c.type === 'ITEM' && c.item_id) tot.set(c.item_id, (tot.get(c.item_id) || 0) + cmNum(c.amount));
        else if (c.type === 'COINS') coins += cmNum(c.coins);
      }
      if (conv) { tot.set(conv.id, (tot.get(conv.id) || 0) + conv.qty); conv = null; }
      add({ kind: 'stars', label: 'Stars 1-' + n + (cc && !conv ? ' (incl. dungeonizing)' : ''), parts: [...tot].map(([id, qty]) => ({ id, qty })), coins: coins || undefined });
      if (stars > uc.length) {
        if (uc.length === 5) for (let i = 0; i < Math.min(stars - 5, 5); i++)
          add({ kind: 'masterStar', label: 'Master star ' + (i + 1), parts: [{ id: CM_MASTER[i], qty: 1 }] });
        else add({ kind: 'stars', label: 'Stars ' + (uc.length + 1) + '-' + stars, parts: [], note: 'no cost data above ' + uc.length + ' stars' });
      }
    }
  }
  if (conv) add({ kind: 'dungeonize', label: 'Dungeonized', parts: [conv] });

  if (cmNum(f.rarity_upgrades) > 0) add({ kind: 'recomb', label: 'Recombobulated', parts: [{ id: 'RECOMBOBULATOR_3000', qty: 1 }] });
  const hpc = cmNum(f.hpc != null ? f.hpc : f.hot_potato_count);
  if (hpc > 0) add({ kind: 'potatoBook', label: 'Hot Potato Book x' + Math.min(hpc, 10), parts: [{ id: 'HOT_POTATO_BOOK', qty: Math.min(hpc, 10) }] });
  if (hpc > 10) add({ kind: 'fumingBook', label: 'Fuming Potato Book x' + Math.min(hpc - 10, 5), parts: [{ id: 'FUMING_POTATO_BOOK', qty: Math.min(hpc - 10, 5) }] });

  for (const k in CM_COUNTED) {
    const n = cmNum(f[k]);
    if (n > 0) add({ kind: 'upgrade', label: CM_COUNTED[k][1] + (n > 1 ? ' x' + n : ''), parts: [{ id: CM_COUNTED[k][0], qty: n }] }, k);
    else if (f[k] != null) used.add(k);
  }
  for (const k in CM_FLAGS) {
    if (cmOn(f[k])) add({ kind: 'upgrade', label: CM_FLAGS[k][1], parts: [{ id: CM_FLAGS[k][0], qty: 1 }] }, k);
    else if (f[k] != null) used.add(k);
  }
  if (cmOn(f.ethermerge)) add({ kind: 'upgrade', label: 'Etherwarp', parts: [{ id: 'ETHERWARP_MERGER', qty: 1 }, { id: 'ETHERWARP_CONDUIT', qty: 1 }] });

  if (f.ability_scroll) for (const s of String(f.ability_scroll).split(/[\s,]+/).filter(Boolean))
    add({ kind: 'abilityScroll', label: cmPretty(s), parts: [{ id: s.toUpperCase(), qty: 1 }] });
  if (cmOn(f.power_ability_scroll))
    add({ kind: 'powerScroll', label: cmPretty(f.power_ability_scroll), parts: [{ id: String(f.power_ability_scroll).toUpperCase(), qty: 1 }] });

  // gem slot unlocks: N-th slot of that type in the items API (slots without costs are free). A gem can only sit in an
  // unlocked slot: Hypixel often leaves those out of unlocked_slots (39% of sales with gems)
  const unlocked = new Set(String(f.unlocked_slots || '').split(/[\s,]+/).filter(Boolean));
  for (const k in f) if (/^[A-Z]+_\d+$/.test(k) && CM_QUAL.includes(String(f[k]).toUpperCase())) unlocked.add(k);
  for (const s of unlocked) {
    const m = s.match(/^([A-Z]+)_(\d+)$/);
    if (!m) continue;
    if (!hy) { add({ kind: 'gemSlot', label: 'Unlock ' + s, parts: [], note: 'item data not loaded' }); continue; }
    const def = (hy.gemstone_slots || []).filter(x => x && x.slot_type === m[1])[+m[2]];
    const costs = (def && def.costs) || [];
    if (!def) { add({ kind: 'gemSlot', label: 'Unlock ' + s, parts: [], note: 'slot not in item data' }); continue; }
    if (!costs.length) continue;
    let coins = 0;
    const parts = [];
    for (const c of costs) {
      if (c && c.type === 'COINS') coins += cmNum(c.coins);
      else if (c && c.item_id) parts.push({ id: c.item_id, qty: cmNum(c.amount) || 1 });
    }
    add({ kind: 'gemSlot', label: 'Unlock ' + s, parts, coins: coins || undefined });
  }
  // gems: SLOT_N = quality, SLOT_N_gem = type for universal slots
  for (const k of Object.keys(f)) {
    if (!/^[A-Z]+_\d+$/.test(k)) continue;
    used.add(k); used.add(k + '_gem');
    const q = String(f[k]).toUpperCase();
    if (!CM_QUAL.includes(q)) continue;
    const type = String(f[k + '_gem'] || k.split('_')[0]).toUpperCase();
    add({ kind: 'gem', label: cmPretty(q) + ' ' + cmPretty(type) + ' (' + k + ')', parts: [{ id: q + '_' + type + '_GEM', qty: 1 }] });
  }

  // reforge
  if (!pet) {
    const rf = cmReforge(item, f, hy, stones);
    if (rf) {
      // Coflnet sometimes sends the stone's name instead of the reforge ("aote_stone" = Warped, "jerry_stone")
      const st = stones[cmNorm(rf)] || Object.values(stones).find(x => x && cmNorm(x[0]) === cmNorm(rf));
      if (st) {
        const tier = cmItemTier(item, f, hy);
        const ti = tier ? CM_TIERS.indexOf(tier) : -1;
        const cost = ti >= 0 && ti < 7 ? st[1 + ti] : null;
        add({ kind: 'reforge', label: cmPretty(rf) + ' reforge', parts: [{ id: st[0], qty: 1 }], coins: cost != null ? cost : undefined,
          note: cost != null ? 'stone + apply cost (' + tier + ')' : 'apply cost unknown for tier ' + (tier || '?') });
      } else add({ kind: 'reforge', label: cmPretty(rf) + ' reforge', parts: [], coins: 0, note: 'blacksmith reforge (no stone), not counted' });
    }
  }

  for (const part in CM_DRILL) {
    const [a, b] = CM_DRILL[part];
    const v = f[a] || f[b];
    if (v) add({ kind: 'drillPart', label: cmPretty(v), parts: [{ id: String(v).toUpperCase(), qty: 1 }] }, a, b);
  }
  if (cmOn(f.dye_item)) add({ kind: 'dye', label: cmPretty(f.dye_item), parts: [{ id: String(f.dye_item).toUpperCase(), qty: 1 }] });
  for (const k of Object.keys(f)) {
    const m = k.match(/^RUNE_(.+)$/);
    if (!m) continue;
    used.add(k);
    const lvl = cmNum(f[k]);
    if (lvl > 0) add({ kind: 'rune', label: cmPretty(m[1]) + ' rune ' + lvl, parts: [{ id: 'RUNE-' + m[1].toUpperCase() + '-' + lvl, qty: 1 }] });
  }
  if (pet) {
    if (cmOn(f.heldItem)) add({ kind: 'petItem', label: 'Held item: ' + cmPretty(f.heldItem), parts: [{ id: String(f.heldItem).toUpperCase(), qty: 1 }] });
    if (cmOn(f.skin)) add({ kind: 'petSkin', label: cmPretty(f.skin) + ' skin', parts: [{ id: 'PET_SKIN_' + String(f.skin).toUpperCase(), qty: 1 }] });
  } else if (cmOn(f.skin)) {
    add({ kind: 'skin', label: cmPretty(f.skin) + ' skin', parts: [{ id: String(f.skin).toUpperCase(), qty: 1 }], note: 'skin id guessed from NBT' });
  }

  // everything else that looks like a real modifier -> unknown (shown, not priced)
  for (const k of Object.keys(f)) {
    if (used.has(k) || CM_IGNORE.has(k) || CM_IGNORE_RE.test(k) || !cmOn(f[k])) continue;
    if (k in CM_COUNTED || k in CM_FLAGS) continue;
    add({ kind: 'unknown', label: k + ' = ' + String(f[k]).slice(0, 40), parts: [], note: 'not priced (unrecognized modifier)' });
  }
  return out;
}

// Ids whose recipes may need loading for fromScratch pricing (enchant books have no recipes: skipped).
export function modifierItemIds(item, data) {
  const ids = new Set();
  let mods = [];
  try { mods = listModifiers(item, data); } catch { mods = []; }
  for (const m of mods) for (const p of m.parts || [])
    if (p && p.id && p.id !== CM_COIN && !/^ENCHANTMENT_/.test(p.id) && !/^RUNE-/.test(p.id)) ids.add(p.id);
  return [...ids];
}

// ---------------------------------------------------------------- price
function cmPrice(id, qty, mode, ctx, priceItem) {
  let r = null;
  try { r = priceItem(id, mode, ctx); } catch (e) { r = { unitCost: null, method: 'unavailable', note: 'error: ' + (e && e.message) }; }
  const unit = r && typeof r.unitCost === 'number' && Number.isFinite(r.unitCost) ? r.unitCost : null;
  const line = { id, qty, unitCost: unit, total: unit == null ? null : unit * qty, method: unit == null ? 'unavailable' : (r.method || 'unavailable') };
  if (r && r.note) line.note = r.note;
  return line;
}
function cmCoinLine(qty) { return { id: CM_COIN, qty, unitCost: 1, total: qty, method: 'coins' }; }
function cmJoin(...n) { return n.filter(Boolean).join('; ') || undefined; }

// Levels made by applying an item to the level below (the owner: Ender Slayer VII needs an End Stone Idol).
const CM_UPGRADE_ITEM = { ENDER_SLAYER_7: 'ENDSTONE_IDOL' };

// Can level L be made by anvil-combining two L-1 books? Ultimates: any level. Others: only one level above the
// enchanting table max and never above V (Chance IV+IV does NOT make V: CHANCE_5 trades ~100x CHANCE_4). Enchants
// the table can't roll (tableMax unknown) are never combined.
function cmCanAnvil(type, name, level, tableMax) {
  if (!(level > 1)) return false;
  if (name.startsWith('ULTIMATE_')) return true;
  const t = tableMax[type];
  return t > 0 && level <= Math.min(5, t + 1);
}
// Same rule for a bazaar book id part: canAnvilEnchant('SHARPNESS', 5) -> true, ('SHARPNESS', 6) -> false.
// Used by the Bazaar "Book Flips" tab (patch/parts/40-book-flips.js).
export function canAnvilEnchant(name, level) {
  return cmCanAnvil(String(name).toLowerCase(), String(name).toUpperCase(), level, CM_TABLE_MAX);
}
function cmBought(l) { return l.unitCost != null && !/^can't be bought/.test(l.note || ''); }

// One enchant (spec 5a).
// fromScratch: cheapest of the book itself and anvil-combining 2^(L-l) copies of the highest lower level that has a
//   market price (buy order / insta-buy / lowest BIN; a lower book priced through a recipe is not used).
// easy: the book itself when it can be bought (insta-buy / lowest BIN / NPC). Only when it can't: anvil from lower
//   books that can be bought, or the book's from-scratch fallback, whichever is cheaper, flagged "can't be bought".
// Nothing priced: enchanting table (XP only, 0 coins) when it can roll that level, else 0 with method 'noMarket'.
function cmEnchantLine(m, mode, ctx, priceItem, tableMax) {
  const { type, name, level } = m.enchant;
  const own = cmPrice('ENCHANTMENT_' + name + '_' + level, 1, mode, ctx, priceItem);
  if (mode === 'easy' && cmBought(own)) return own;
  let best = own.unitCost != null ? own : null;
  const up = CM_UPGRADE_ITEM[name + '_' + level];
  if (up) {
    const below = cmPrice('ENCHANTMENT_' + name + '_' + (level - 1), 1, mode, ctx, priceItem);
    const item = cmPrice(up, 1, mode, ctx, priceItem);
    if (below.unitCost != null && item.unitCost != null && (!best || below.unitCost + item.unitCost < best.total)) {
      const sum = below.unitCost + item.unitCost;
      best = { id: own.id, qty: 1, unitCost: sum, total: sum, method: 'upgrade',
        note: cmJoin('level ' + (level - 1) + ' + ' + up, below.note, item.note) };
    }
  }
  if (cmCanAnvil(type, name, level, tableMax)) {
    for (let l = level - 1; l >= 1; l--) {
      const b = cmPrice('ENCHANTMENT_' + name + '_' + l, 1, mode, ctx, priceItem);
      if (b.unitCost == null || !CM_MARKET.has(b.method) || (mode === 'easy' && !cmBought(b))) continue;
      const n = 2 ** (level - l);
      if (!best || b.unitCost * n < best.total)
        best = { ...b, qty: n, total: b.unitCost * n, method: b.method + '+anvil',
          note: cmJoin(mode === 'easy' ? "can't be bought, anvil: " + n + ' x level ' + l + ' -> ' + level : 'anvil: ' + n + ' x level ' + l + ' -> ' + level, b.note) };
      break;
    }
  }
  if (best) return best;
  const tmax = tableMax[type];
  if (tmax != null && level <= tmax) return { id: own.id, qty: 1, unitCost: 0, total: 0, method: 'table', note: 'enchanting table (XP only)' };
  return { id: own.id, qty: 1, unitCost: 0, total: 0, method: 'noMarket', note: cmJoin('no market price for this book, counted as 0', own.note) };
}

// -> [{ kind, label, id, qty, unitCost, total, method, note? }]  (never throws)
export function priceModifiers(item, mode, ctx, priceItem) {
  const lines = [];
  const d = cmData(ctx);
  let mods;
  try { mods = listModifiers(item, d); } catch (e) {
    return [{ kind: 'unknown', label: 'modifiers', id: null, qty: 0, unitCost: null, total: null, method: 'unavailable', note: 'parse error: ' + (e && e.message) }];
  }
  if (typeof priceItem !== 'function') priceItem = () => ({ unitCost: null, method: 'unavailable', note: 'no price function' });
  for (const m of mods) {
    const push = (l, note) => lines.push({ kind: m.kind, label: m.label, ...l, ...(cmJoin(l.note, note) ? { note: cmJoin(l.note, note) } : {}) });
    try {
      if (m.kind === 'enchant' && !m.enchant.fixed) { push(cmEnchantLine(m, mode, ctx, priceItem, d.tableMax || CM_TABLE_MAX)); continue; }
      const parts = m.parts || [];
      if (!parts.length && m.coins === 0) continue;   // free (blacksmith reforge)
      if (!parts.length && m.coins == null) {
        push({ id: null, qty: 0, unitCost: null, total: null, method: 'unavailable' }, m.note);
        continue;
      }
      parts.forEach((p, i) => push(cmPrice(p.id, p.qty, mode, ctx, priceItem), i === 0 ? m.note : undefined));
      if (m.coins != null) push(cmCoinLine(m.coins), parts.length ? undefined : m.note);
    } catch (e) {
      push({ id: null, qty: 0, unitCost: null, total: null, method: 'unavailable' }, 'error: ' + (e && e.message));
    }
  }
  return lines;
}

// ---------------------------------------------------------------- data
// Loads the Hypixel items API once per page (5 MB, browser-cached) unless ctx.hyItems is given.
// Optional overrides: ctx.hyItems (API json | items array | Map), ctx.reforgeStones (NEU reforgestones.json),
// ctx.enchantTable (NEU enchants.json or its max_xp_table_levels). Rejects only on abort.
// A failed items request is not cached on ctx (it is retried at most once a minute), so one outage doesn't hide star /
// gem-slot data for the rest of the page's life.
export async function loadModifierData(ctx = {}, { signal } = {}) {
  if (ctx.modData) return ctx.modData;
  let items = cmItemMap(ctx.hyItems), note;
  if (!items && CM_DATA && CM_DATA.items.size) items = CM_DATA.items;
  if (!items && CM_ITEMS_FAIL && Date.now() - CM_ITEMS_FAIL.at < CM_ITEMS_RETRY) { note = CM_ITEMS_FAIL.msg; items = new Map(); }
  if (!items) {
    try {
      if (!CM_ITEMS_INFLIGHT) {
        const fetchFn = ctx.fetchFn || globalThis.fetch;
        CM_ITEMS_INFLIGHT = (async () => {
          const r = await fetchFn(CM_ITEMS_URL);
          if (!r || !r.ok) throw new Error('items API HTTP ' + (r && r.status));
          const j = await r.json();
          const m = cmItemMap(j);
          if (!m || !m.size) throw new Error('items API returned no items');
          return m;
        })();
      }
      items = await CM_ITEMS_INFLIGHT;
      CM_ITEMS_FAIL = null;
    } catch (e) {
      CM_ITEMS_INFLIGHT = null;
      note = 'item data unavailable: ' + (e && e.message);
      CM_ITEMS_FAIL = { at: Date.now(), msg: note };
      items = new Map();
    }
  }
  if (signal && signal.aborted) throw cmAbortErr();
  let tableMax = CM_TABLE_MAX;
  const et = ctx.enchantTable && (ctx.enchantTable.max_xp_table_levels || ctx.enchantTable);
  if (et && typeof et === 'object') { tableMax = {}; for (const k in et) tableMax[k.toLowerCase()] = cmNum(et[k]); }
  const data = { items, stones: ctx.reforgeStones ? cmStoneMap(ctx.reforgeStones) : CM_STONES, tableMax, note };
  if (items.size) { ctx.modData = data; CM_DATA = data; }
  return data;
}

// ---------------------------------------------------------------- listing total
// core = { loadRecipeTree, cleanCraftCost, priceItem } from craftCore.js. opts = { signal }. Rejects only on abort.
// -> { baseId, craftable, base: {fromScratch, easy}, modifiers: {fromScratch, easy}, totals: {fromScratch, easy}, missing }
// base = craftCore Tree of the clean item. No recipe (craftable false, e.g. an Epic pet tier): the clean item bought
//   at market (a leaf node, method lowestBin / buyOrder / instaBuy, baseSource 'market'); no market price either:
//   null, totals = modifiers only (baseSource null). baseSource 'recipe' = crafted tree.
// notes = extra remarks for the whole listing (pets: exp / level not priced).
// totals = base total + every priced line; null when the base recipe exists but can't be priced.
// missing = ids / labels that had no price (unknown modifiers are listed in the lines, not here).
export async function listingCraftCost(tag, item, ctx, core, opts = {}) {
  const { signal } = opts || {};
  ctx = ctx || {};
  core = core || {};
  item = item && typeof item === 'object' ? item : {};
  tag = String(tag || item.tag || '').toUpperCase();
  const missing = new Set();
  const data = await loadModifierData(ctx, { signal });
  if (data.note) missing.add(data.note);
  const f = cmFlat(item);
  const pet = cmIsPet(tag, f);
  const tier = pet ? cmPetTier(item, f) : undefined;
  const baseId = pet && tier ? tag.replace(/^PET_/, '') + ';' + CM_PET_TIERS.indexOf(tier) : tag;

  if (typeof core.loadRecipeTree === 'function') {
    await Promise.all(modifierItemIds(item, data).map(id =>
      Promise.resolve().then(() => core.loadRecipeTree(id, ctx, { signal })).catch(e => { if (cmIsAbort(e)) throw e; })));
  }
  let clean = null;
  try { if (typeof core.cleanCraftCost === 'function') clean = await core.cleanCraftCost(tag, ctx, { signal, tier }); }
  catch (e) { if (cmIsAbort(e) || (signal && signal.aborted)) throw e; missing.add('clean craft: ' + (e && e.message)); }
  if (signal && signal.aborted) throw cmAbortErr();
  for (const x of (clean && clean.missing) || []) missing.add(x);

  const craftable = !!(clean && clean.craftable);
  const out = { baseId, craftable, baseSource: craftable ? 'recipe' : null, base: {}, modifiers: {}, totals: {}, missing: [], notes: [] };
  if (pet) out.notes.push('pet exp / level not priced');
  for (const mode of ['fromScratch', 'easy']) {
    let base = (clean && clean[mode]) || null;
    if (!base && !craftable && typeof core.priceItem === 'function') {
      let p = null;
      try { p = core.priceItem(baseId, mode, ctx); } catch (e) { p = null; }
      if (p && typeof p.unitCost === 'number' && Number.isFinite(p.unitCost)) {
        base = { id: baseId, qty: 1, unitCost: p.unitCost, total: p.unitCost, method: p.method,
          note: cmJoin(pet ? 'no recipe for this pet tier, clean pet bought' : 'no recipe, clean item bought', p.note) };
        out.baseSource = 'market';
      } else missing.add(baseId + ' (no recipe, no market price)');
    }
    const baseTotal = base ? base.total : 0;
    if (base && baseTotal == null) missing.add(baseId + ' (clean craft)');
    const lines = priceModifiers(item, mode, ctx, core.priceItem);
    let sum = 0;
    for (const l of lines) {
      if (l.total != null) sum += l.total;
      else if (l.kind !== 'unknown') missing.add(l.id || l.label);
    }
    out.base[mode] = base;
    out.modifiers[mode] = lines;
    out.totals[mode] = baseTotal == null ? null : baseTotal + sum;
  }
  out.missing = [...missing];
  return out;
}
