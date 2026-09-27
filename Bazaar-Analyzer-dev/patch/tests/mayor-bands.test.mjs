// Term math for parts/50-mayor-bands.js (timestamp -> term, chart index math, Clean predicate).
// Run: node C:/Users/Lenovo/Bazaar-Analyzer-dev/patch/tests/mayor-bands.test.mjs
// The part file is plain browser JS (no exports); we load it in a tiny sandbox that provides the
// few bundle globals it touches, then pull the functions out of the sandbox.
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import assert from 'node:assert'; // loose: the part runs in a vm context, so its arrays/objects are cross-realm
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PART = path.join(HERE, '..', 'parts', '50-mayor-bands.js');

// Stand-ins for the bundle globals the part uses (see notes/bundle-map.md).
const sandbox = {
  // React / jsx runtime: only referenced inside the component, which we never render here.
  y: { useRef: () => {}, useState: () => [], useEffect: () => {} },
  b: { jsx: () => null },
  // noise-key regex + star helpers from the bundle
  ea: /^(uuid|uid|timestamp|originTag|id|.*_xp|.*xp|.*_uuid|date|.*time.*|.*timestamp.*)$/i,
  Ri: (nbt) => {
    for (const k of ['upgrade_level', 'dungeon_item_level']) {
      const n = Number(nbt?.[k]);
      if (Number.isFinite(n) && n > 0) return Math.floor(n);
    }
    return 0;
  },
  Vi: (name) => ((String(name).match(/✪/g) ?? []).length),
  Gi: (a) => (a.reforge != null ? String(a.reforge) : 'None'),
  console,
  localStorage: {
    _v: new Map(),
    getItem(k) { return this._v.has(k) ? this._v.get(k) : null; },
    setItem(k, v) { this._v.set(k, String(v)); },
  },
  fetch: () => Promise.reject(new Error('no network in tests')),
  Date,
  Promise,
  Number,
  Object,
  Array,
  Math,
  JSON,
  Set,
  String,
  RegExp,
  Error,
  document: undefined,
  getComputedStyle: undefined,
};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(PART, 'utf8'), sandbox, { filename: PART });
const {
  BA_mayorTime, BA_mayorParse, BA_mayorAt, BA_mayorFloatIndex, BA_mayorTimeAt,
  BA_mayorLegendRow, BA_mayorColor, BA_isCleanListing, BA_cleanApply, BA_cleanHint,
} = sandbox;

let n = 0;
const t = (name, fn) => { fn(); n++; console.log('  ok  ' + name); };
const ta = async (name, fn) => { await fn(); n++; console.log('  ok  ' + name); };

/* ---------- date parsing (real Coflnet strings) ---------- */
t('parses the two Coflnet date shapes as UTC', () => {
  assert.equal(BA_mayorTime('12/27/2023 11:15:00 +00:00'), Date.UTC(2023, 11, 27, 11, 15, 0));
  assert.equal(BA_mayorTime('01/01/2024 15:15:00'), Date.UTC(2024, 0, 1, 15, 15, 0));
  assert.equal(BA_mayorTime('9/19/2026 15:15:00 +00:00'), Date.UTC(2026, 8, 19, 15, 15, 0));
  assert.ok(Number.isNaN(BA_mayorTime(null)));
  assert.ok(Number.isNaN(BA_mayorTime('not a date')));
});

/* ---------- parsing / sorting / cleaning ---------- */
const RAW = [
  // deliberately out of order, like the live API
  { year: 514, start: '09/19/2026 15:15:00 +00:00', end: '09/24/2026 19:15:00',
    winner: { key: 'slayer', name: 'Aatrox', minister: { key: 'fishing', name: 'Marina' } } },
  { year: 321, start: '12/27/2023 11:15:00 +00:00', end: '01/01/2024 15:15:00',
    winner: { key: 'events', name: 'Foxy' } },
  { year: 513, start: '09/14/2026 11:15:00 +00:00', end: '09/19/2026 15:15:00',
    winner: { key: 'farming', name: 'Finnegan', minister: { key: 'mining', name: 'Cole' } } },
  { year: 999, start: 'garbage', end: 'garbage', winner: { name: 'Nobody' } },   // dropped
  { year: 998, start: '01/02/2024 00:00:00 +00:00', end: '01/01/2024 00:00:00' }, // no winner -> dropped
];
const terms = BA_mayorParse(RAW);

t('parses, drops junk and sorts by start', () => {
  assert.equal(terms.length, 3);
  assert.deepEqual(terms.map((x) => x.name), ['Foxy', 'Finnegan', 'Aatrox']);
  assert.deepEqual(terms.map((x) => x.year), [321, 513, 514]);
  assert.equal(terms[2].minister, 'Marina');
  assert.equal(terms[0].minister, null);
  assert.equal(terms[2].key, 'slayer');
  for (let i = 1; i < terms.length; i++) assert.ok(terms[i].start >= terms[i - 1].end);
});

t('a term is one SkyBlock year = 124 real hours', () => {
  for (const x of terms) assert.equal(x.end - x.start, 124 * 3600 * 1000);
});

t('clamps overlapping terms instead of stacking bands', () => {
  const o = BA_mayorParse([
    { year: 1, start: '01/01/2024 00:00:00 +00:00', end: '01/10/2024 00:00:00', winner: { name: 'A' } },
    { year: 2, start: '01/05/2024 00:00:00 +00:00', end: '01/12/2024 00:00:00', winner: { name: 'B' } },
  ]);
  assert.equal(o.length, 2);
  assert.equal(o[0].end, o[1].start);
  assert.equal(o[0].end, Date.UTC(2024, 0, 5));
});

t('handles a missing / broken list without throwing', () => {
  assert.deepEqual(BA_mayorParse(null), []);
  assert.deepEqual(BA_mayorParse('nope'), []);
  assert.deepEqual(BA_mayorParse([null, 1, {}]), []);
});

/* ---------- timestamp -> term ---------- */
const FIN = terms[1], AAT = terms[2];
t('timestamp -> term: start inclusive, end exclusive', () => {
  assert.equal(BA_mayorAt(terms, FIN.start).name, 'Finnegan');
  assert.equal(BA_mayorAt(terms, FIN.start + 1).name, 'Finnegan');
  assert.equal(BA_mayorAt(terms, FIN.end - 1).name, 'Finnegan');
  assert.equal(BA_mayorAt(terms, FIN.end).name, 'Aatrox');          // handover instant
  assert.equal(BA_mayorAt(terms, AAT.end - 1).name, 'Aatrox');
});

t('timestamp -> term: outside the known range is null, not a wrong mayor', () => {
  assert.equal(BA_mayorAt(terms, terms[0].start - 1), null);        // before the first term
  assert.equal(BA_mayorAt(terms, AAT.end), null);                   // after the last term
  assert.equal(BA_mayorAt(terms, terms[0].end + 1), null);          // inside the 2024->2026 gap
  assert.equal(BA_mayorAt(terms, NaN), null);
  assert.equal(BA_mayorAt([], 1), null);
  assert.equal(BA_mayorAt(null, 1), null);
});

t('binary search agrees with a linear scan over many timestamps', () => {
  const lin = (ts) => terms.find((x) => ts >= x.start && ts < x.end) ?? null;
  for (let ts = terms[0].start - 6e8; ts < AAT.end + 6e8; ts += 37e6) {
    assert.equal(BA_mayorAt(terms, ts)?.name ?? null, lin(ts)?.name ?? null, String(ts));
  }
});

t('Mayors switch: off by default, remembered, and while off the tooltips get no row', () => {
  sandbox.BA_mayorMem = terms;
  assert.equal(sandbox.BA_mayorOn, false);
  assert.deepEqual(BA_mayorLegendRow(AAT.start + 1000), []);
  assert.deepEqual(sandbox.BA_mayorTipRow(AAT.start + 1000), []);
  let pinged = 0;
  const f = () => pinged++;
  sandbox.BA_mayorOnSubs.add(f);
  sandbox.BA_mayorSetOn(true);
  assert.equal(sandbox.BA_mayorOn, true);
  assert.equal(sandbox.localStorage.getItem('bz.mayorBands.on'), '1');
  assert.equal(pinged, 1);
  assert.equal(BA_mayorLegendRow(AAT.start + 1000).length, 1);
  sandbox.BA_mayorSetOn(false);
  assert.equal(sandbox.localStorage.getItem('bz.mayorBands.on'), '0');
  assert.deepEqual(BA_mayorLegendRow(AAT.start + 1000), []);
  sandbox.BA_mayorOnSubs.delete(f);
  sandbox.BA_mayorSetOn(true); // the tests below check the rows themselves
});

t('tooltip row: one row inside a term, nothing outside, minister appended', () => {
  sandbox.BA_mayorMem = terms;
  const row = BA_mayorLegendRow(AAT.start + 1000);
  assert.equal(row.length, 1);
  assert.equal(row[0].title, 'Mayor: ');
  assert.equal(row[0].value.text, 'Aatrox · Marina');
  assert.equal(row[0].value.color, BA_mayorColor(AAT));
  assert.equal(BA_mayorLegendRow(terms[0].start + 1000)[0].value.text, 'Foxy'); // no minister
  assert.deepEqual(BA_mayorLegendRow(AAT.end + 1), []);
  assert.deepEqual(BA_mayorLegendRow(NaN), []);
  sandbox.BA_mayorMem = null;
  assert.deepEqual(BA_mayorLegendRow(AAT.start + 1000), []); // not loaded yet -> no row, no throw
  sandbox.BA_mayorMem = terms;
});

t('Bazaar tooltip row: same mayor in the BA_Tip {k,v,c} shape', () => {
  sandbox.BA_mayorMem = terms;
  const row = sandbox.BA_mayorTipRow(AAT.start + 1000);
  assert.equal(row.length, 1);
  assert.equal(row[0].k, 'Mayor');
  assert.equal(row[0].v, 'Aatrox · Marina');
  assert.equal(row[0].c, BA_mayorColor(AAT));
  assert.deepEqual(sandbox.BA_mayorTipRow(AAT.end + 1), []);
});

await ta('stale list (the current term ended while the page stayed open) is refetched, at most every 10 min', async () => {
  const past = [{ start: Date.now() - 2e8, end: Date.now() - 1e3, name: 'Old', key: '', year: 1, minister: null }];
  const fresh = [{ year: 2, start: '01/01/2020 00:00:00 +00:00', end: '01/01/2999 00:00:00', winner: { key: '', name: 'Newer' } }];
  assert.equal(sandbox.BA_mayorStale(past), true);
  assert.equal(sandbox.BA_mayorStale(null), true);
  assert.equal(sandbox.BA_mayorStale(sandbox.BA_mayorParse(fresh)), false);
  const realFetch = sandbox.fetch;
  let calls = 0;
  sandbox.fetch = () => (calls++, Promise.resolve({ ok: true, json: () => Promise.resolve(fresh) }));
  sandbox.BA_mayorMem = past;
  sandbox.BA_mayorLastTry = 0;
  const got = await sandbox.BA_mayorLoad();
  assert.equal(calls, 1);
  assert.equal(got[0].name, 'Newer');
  assert.equal(sandbox.BA_mayorMem[0].name, 'Newer');
  // Coflnet has no new term yet: keep the old list, and don't ask again within 10 min
  sandbox.fetch = () => (calls++, Promise.reject(new Error('down')));
  sandbox.BA_mayorMem = past;
  sandbox.BA_mayorLastTry = 0;
  const warn = sandbox.console.warn; sandbox.console = { ...console, warn: () => {} };
  assert.equal((await sandbox.BA_mayorLoad())[0].name, 'Old');
  assert.equal((await sandbox.BA_mayorLoad())[0].name, 'Old');
  assert.equal(calls, 2); // the second call was throttled
  sandbox.console = console; void warn;
  sandbox.fetch = realFetch;
  sandbox.BA_mayorMem = terms;
});

/* ---------- chart index math ---------- */
const HOUR = 36e5;
const bars = Array.from({ length: 24 }, (_, i) => ({ timestamp: Date.UTC(2026, 8, 20) + i * HOUR }));
t('float index: exact on bars, linear between them', () => {
  assert.equal(BA_mayorFloatIndex(bars, bars[0].timestamp, HOUR), 0);
  assert.equal(BA_mayorFloatIndex(bars, bars[5].timestamp, HOUR), 5);
  assert.equal(BA_mayorFloatIndex(bars, bars[23].timestamp, HOUR), 23);
  assert.equal(BA_mayorFloatIndex(bars, bars[5].timestamp + HOUR / 2, HOUR), 5.5);
  assert.equal(BA_mayorFloatIndex(bars, bars[5].timestamp + HOUR / 4, HOUR), 5.25);
});

t('float index: extrapolates past both ends with the chart period', () => {
  assert.equal(BA_mayorFloatIndex(bars, bars[0].timestamp - 3 * HOUR, HOUR), -3);
  assert.equal(BA_mayorFloatIndex(bars, bars[23].timestamp + 2 * HOUR, HOUR), 25);
  assert.equal(BA_mayorFloatIndex([], 123, HOUR), 0);
  assert.equal(BA_mayorFloatIndex(bars, NaN, HOUR), 0);
});

t('float index handles gaps in the data (index space, not time space)', () => {
  const gappy = [{ timestamp: 0 }, { timestamp: HOUR }, { timestamp: 10 * HOUR }];
  assert.equal(BA_mayorFloatIndex(gappy, HOUR, HOUR), 1);
  assert.equal(BA_mayorFloatIndex(gappy, 5.5 * HOUR, HOUR), 1.5); // half way across the gap bar
  assert.equal(BA_mayorFloatIndex(gappy, 10 * HOUR, HOUR), 2);
});

t('time-at-index is the inverse of float index', () => {
  for (const idx of [-4, 0, 3.5, 12, 22.25, 23, 27]) {
    const ts = BA_mayorTimeAt(bars, idx, HOUR);
    assert.ok(Math.abs(BA_mayorFloatIndex(bars, ts, HOUR) - idx) < 1e-6, `idx ${idx}`);
  }
  assert.ok(Number.isNaN(BA_mayorTimeAt([], 1, HOUR)));
});

/* ---------- band layer follows the chart (regression: bands stayed put during drag / pinch) ---------- */
// Minimal DOM + a chart with klinecharts' own x math (StoreImp.dataIndexToCoordinate, whole-bar clamped
// getVisibleRange), so BA_mayorPaint runs exactly as in the page.
function fakeEl(tag) {
  const el = {
    tagName: tag, style: { cssText: '' }, dataset: {}, children: [], parentNode: null, created: 0,
    appendChild(c) { if (c && typeof c === 'object') c.parentNode = this; this.children.push(c); return c; },
    remove() { const p = this.parentNode; if (p) { p.children.splice(p.children.indexOf(this), 1); this.parentNode = null; } },
    get firstChild() { return this.children[0] ?? null; },
    get lastChild() { return this.children[this.children.length - 1] ?? null; },
    set textContent(_) { for (const c of this.children) if (c && typeof c === 'object') c.parentNode = null; this.children = []; },
  };
  return el;
}
let made = 0;
const fakeDoc = { createElement: (t) => (made++, fakeEl(t)), createTextNode: (s) => ({ text: s }) };
const DAY = 864e5;
function fakeChart(n, { width = 1200, bar = 40, right = 1.2 } = {}) {
  const list = Array.from({ length: n }, (_, i) => ({ timestamp: Date.UTC(2026, 7, 26) + i * DAY }));
  const c = {
    list, bs: bar, diff: right, W: width,
    getSize: () => ({ left: 0, top: 0, width: c.W, height: 394 }),
    getDataList: () => list,
    getBarSpace: () => ({ bar: c.bs }),
    // klinecharts: whole bars, clamped to [0, n] -> unchanged by a sub-bar drag or while every bar is on screen
    getVisibleRange() {
      const t = c.W / c.bs; let to = Math.round(c.diff + n + 0.5); if (to > n) to = n;
      return { from: Math.max(0, Math.round(to - t) - 1), to };
    },
    convertToPixel: ({ dataIndex }) => ({ x: Math.floor(c.W - (n + c.diff - dataIndex - 0.5) * c.bs + 0.5) }),
    xOf: (ts) => c.convertToPixel({ dataIndex: BA_mayorFloatIndex(list, ts, DAY) }).x,
    scroll(dx) { c.diff -= dx / c.bs; }, // drag right by dx px
  };
  return c;
}
const COL = { alt: 'rgba(236,233,225,0.015)', sep: 'linear-gradient(red,red)', mute: '#a09d93' };
const bandTerms = () => Array.from({ length: 12 }, (_, i) => ({
  start: Date.UTC(2026, 7, 20) + i * 124 * HOUR, end: Date.UTC(2026, 7, 20) + (i + 1) * 124 * HOUR,
  name: ['Aatrox', 'Cole', 'Diana', 'Paul'][i % 4], key: '', year: 500 + i, minister: null,
}));
const bandOf = (el, t) => el.children.find((d) => d.dataset.start === String(t.start));
const px = (s) => parseFloat(s);

t('band edges follow every sub-bar drag step, also while the whole data set is on screen', () => {
  sandbox.document = fakeDoc;
  const chart = fakeChart(30), terms = bandTerms(), el = fakeEl('div');
  const vr0 = JSON.stringify(chart.getVisibleRange());
  sandbox.BA_mayorPaint(el, chart, terms, DAY, COL);
  const probe = terms[3];
  for (let step = 1; step <= 12; step++) {
    chart.scroll(3); // 3 px, far less than a 40 px bar
    sandbox.BA_mayorPaint(el, chart, terms, DAY, COL);
    const d = bandOf(el, probe);
    assert.ok(d, 'probe band drawn');
    assert.equal(px(d.style.left), chart.xOf(probe.start), `step ${step}: band starts where the chart puts the term start`);
  }
  // the clamped visible range never changed: the old "signature" check would have skipped all 12 paints
  assert.equal(JSON.stringify(chart.getVisibleRange()), vr0);
  sandbox.document = undefined;
});

t('zoom + resize move the bands too, and nodes are reused instead of rebuilt', () => {
  sandbox.document = fakeDoc;
  const chart = fakeChart(30), terms = bandTerms(), el = fakeEl('div');
  sandbox.BA_mayorPaint(el, chart, terms, DAY, COL);
  const before = made, nodes = [...el.children];
  for (const [bar, W] of [[33, 1200], [25.5, 1200], [25.5, 1050], [41, 1300]]) {
    chart.bs = bar; chart.W = W;
    sandbox.BA_mayorPaint(el, chart, terms, DAY, COL);
    assert.equal(el.style.width, `${W}px`);
    for (const d of el.children) {
      const tm = terms.find((x) => String(x.start) === d.dataset.start);
      const x1 = chart.xOf(tm.start);
      const first = Math.max(0, chart.convertToPixel({ dataIndex: 0 }).x - bar / 2);
      assert.equal(px(d.style.left), Math.max(first, Math.min(W, x1)), `bar ${bar} W ${W}`);
    }
  }
  assert.ok(nodes.filter((d) => el.children.includes(d)).length >= 5, 'bands that stay on screen keep their node');
  assert.ok(made - before < 20, 'only newly visible terms create nodes');
  sandbox.document = undefined;
});

t('no bands over the empty area left of the first bar; off-screen terms are dropped', () => {
  sandbox.document = fakeDoc;
  const chart = fakeChart(30), terms = bandTerms(), el = fakeEl('div');
  chart.scroll(500); // data starts ~500 px into the plot
  sandbox.BA_mayorPaint(el, chart, terms, DAY, COL);
  const first = chart.convertToPixel({ dataIndex: 0 }).x - chart.bs / 2;
  assert.ok(first > 400);
  for (const d of el.children) assert.ok(px(d.style.left) >= first - 1e-9, 'band starts at or after the first bar');
  chart.scroll(-5000); // everything scrolled far off to the left
  sandbox.BA_mayorPaint(el, chart, terms, DAY, COL);
  assert.equal(el.children.length, 0);
  sandbox.document = undefined;
});

t('quiet look: faint mayor tint (fainter on thin bands), separator and label only where they fit', () => {
  sandbox.document = fakeDoc;
  assert.equal(sandbox.BA_mayorRgba('#ef4444', 0.04), 'rgba(239,68,68,0.04)');
  assert.equal(sandbox.BA_mayorRgba('#abc', 0.5), 'rgba(170,187,204,0.5)');
  assert.equal(sandbox.BA_mayorRgba('red', 0.5), 'red');
  const terms = bandTerms(), el = fakeEl('div');
  const wide = fakeChart(30, { bar: 40 });
  sandbox.BA_mayorPaint(el, wide, terms, DAY, COL);
  const d = el.children.find((x) => px(x.style.width) > 100);
  assert.match(d.style.background, /rgba\(\d+,\d+,\d+,0\.04\)/);
  assert.equal(d.lastChild.style.display, 'flex'); // label
  assert.match(d.lastChild.style.cssText, /opacity:\.55/);
  const thin = fakeChart(400, { bar: 3 }); // ALL range: ~15 px per term
  const el2 = fakeEl('div');
  sandbox.BA_mayorPaint(el2, thin, terms, DAY, COL);
  for (const x of el2.children) {
    assert.match(x.style.background, /,0\.022\)/);
    assert.notEqual(x.lastChild.style.display, 'flex'); // label stays hidden (display:none from its cssText)
  }
  sandbox.document = undefined;
});

/* ---------- Clean predicate ---------- */
const clean = { itemName: 'Hyperion', reforge: 'None', enchantments: [],
  flattenedNbt: { uid: '27307e5febe4', uuid: '96453e3f-c79e-4ad3-bd25-27307e5febe4' } };
t('clean gear (matches Coflnet Clean=yes rows)', () => {
  assert.equal(BA_isCleanListing(clean), true);
  assert.equal(BA_isCleanListing({ ...clean, flatNbt: clean.flattenedNbt, flattenedNbt: undefined }), true);
});

t('every kind of modifier makes it dirty', () => {
  const dirty = {
    enchant: { enchantments: [{ type: 'sharpness', level: 6 }] },
    reforge: { reforge: 'Fabled' },
    stars: { flattenedNbt: { ...clean.flattenedNbt, upgrade_level: '5' } },
    dungeonStars: { flattenedNbt: { ...clean.flattenedNbt, dungeon_item_level: '3' } },
    nameStars: { itemName: 'Hyperion ✪✪' },
    recomb: { flattenedNbt: { ...clean.flattenedNbt, rarity_upgrades: '1' } },
    hpc: { flattenedNbt: { ...clean.flattenedNbt, hpc: '10' } },
    scroll: { flattenedNbt: { ...clean.flattenedNbt, ability_scroll: 'IMPLOSION_SCROLL' } },
    gemSlots: { flattenedNbt: { ...clean.flattenedNbt, unlocked_slots: 'COMBAT_0' } },
    gem: { flattenedNbt: { ...clean.flattenedNbt, COMBAT_0: 'FINE' } },
    rune: { flattenedNbt: { ...clean.flattenedNbt, RUNE_ICE: '3' } },
    dye: { flattenedNbt: { ...clean.flattenedNbt, dye_item: 'DYE_PURE_BLACK' } },
    artOfWar: { flattenedNbt: { ...clean.flattenedNbt, art_of_war_count: '1' } },
    skin: { flattenedNbt: { ...clean.flattenedNbt, skin: 'SOME_SKIN' } },
  };
  for (const [k, v] of Object.entries(dirty)) {
    assert.equal(BA_isCleanListing({ ...clean, ...v }), false, k);
  }
});

t('pets: a Lvl 1 pet is clean, xp / candy / held item are not', () => {
  const petNbt = { type: 'ENDER_DRAGON', active: 'False', exp: '0', tier: 'EPIC', hideInfo: 'False',
    candyUsed: '0', hideRightClick: 'False', noMove: 'False', petSoulbound: 'False',
    bossId: '04a78e9f-2cdc-4c0d-88d4-fd6c5899716d', spawnedFor: 'ff5bf97c-c89a-4f5b-8b8b-172189c1d681',
    uid: '0982e0ee962d', uuid: 'c78f5e09-6a8d-4ec2-84fe-0982e0ee962d' };
  const pet = { itemName: '[Lvl 1] Ender Dragon', reforge: 'None', enchantments: [], flattenedNbt: petNbt };
  assert.equal(BA_isCleanListing(pet), true);
  assert.equal(BA_isCleanListing({ ...pet, flattenedNbt: { ...petNbt, exp: '25353230' } }), false);
  assert.equal(BA_isCleanListing({ ...pet, flattenedNbt: { ...petNbt, candyUsed: '5' } }), false);
  assert.equal(BA_isCleanListing({ ...pet, flattenedNbt: { ...petNbt, heldItem: 'PET_ITEM_TIER_BOOST' } }), false);
});

t('clean predicate never throws on junk', () => {
  for (const junk of [null, undefined, 0, 'x', [], {}, { flattenedNbt: 'nope' }]) {
    assert.equal(typeof BA_isCleanListing(junk), 'boolean', String(junk));
  }
  assert.equal(BA_isCleanListing({}), true); // nothing known -> nothing modified
});

/* ---------- Clean vs the other filters ---------- */
const F = (kind, id) => ({ id: id ?? kind, kind });
t('adding Clean clears modifier filters but keeps the others', () => {
  const before = [F('rarity'), F('stars'), F('bin'), F('nbt'), F('price'), F('clean')];
  const after = BA_cleanApply(before, 'clean').map((f) => f.kind);
  assert.deepEqual(after, ['rarity', 'bin', 'price', 'clean']);
});

t('adding a modifier filter turns Clean off', () => {
  assert.deepEqual(BA_cleanApply([F('clean'), F('rarity'), F('stars')], 'stars').map((f) => f.kind),
    ['rarity', 'stars']);
  assert.deepEqual(BA_cleanApply([F('clean'), F('rarity')], 'rarity').map((f) => f.kind),
    ['clean', 'rarity']); // rarity is not a modifier -> both stay
  assert.deepEqual(BA_cleanApply(null, 'clean'), null);
});

t('menu hints explain what a click will clear', () => {
  assert.equal(BA_cleanHint([F('stars')], 'clean'), 'clears modifier filters');
  assert.equal(BA_cleanHint([F('rarity')], 'clean'), '');
  assert.equal(BA_cleanHint([F('clean')], 'enchant'), 'turns Clean off');
  assert.equal(BA_cleanHint([F('clean')], 'rarity'), '');
  assert.equal(BA_cleanHint([], 'enchant'), '');
});

console.log(`\n${n} tests passed`);
