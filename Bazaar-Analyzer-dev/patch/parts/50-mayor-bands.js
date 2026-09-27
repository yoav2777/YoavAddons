/* ---- 50-mayor-bands.js -------------------------------------------------------------------
 * 1. Mayor term bands behind the AH and Bazaar price charts (klinecharts v10) + mayor row in their tooltips.
 * 2. Helpers for the "Clean" AH filter (no modifiers at all).
 * Docs: notes/mayor-bands.md. Mounted by patch/patch_v12.mjs (exact-match replacements).
 * Plain JS, no JSX: React = `y`, jsx runtime = `b`. Only BA_* names at module scope.
 * ------------------------------------------------------------------------------------------ */

/* ============================== mayor terms (data) ============================== */

var BA_MAYOR_API = `https://sky.coflnet.com/api/mayor`; // CORS *, ~350 terms back to SB year 164
var BA_MAYOR_LS = `bz.mayors.v1`;
var BA_MAYOR_TTL = 864e5; // 24 h
var BA_mayorMem = null; // Array<{start,end,name,key,year,minister}> sorted by start, or null
var BA_mayorReq = null; // in-flight promise (shared)
var BA_mayorLastTry = 0; // Date.now() of the last network attempt
var BA_MAYOR_RETRY = 6e5; // 10 min between refetches while the list is stale (Coflnet caches for 10 min)

// On/off switch shared by the AH and Bazaar charts ("Mayors" button in their toolbars). Off by default and
// while off nothing is fetched, drawn or added to the tooltips.
var BA_MAYOR_ON_LS = `bz.mayorBands.on`;
var BA_mayorOn = (() => {
  try {
    return localStorage.getItem(BA_MAYOR_ON_LS) === `1`;
  } catch {
    return !1;
  }
})();
var BA_mayorOnSubs = new Set();
function BA_mayorSetOn(v) {
  BA_mayorOn = !!v;
  try {
    localStorage.setItem(BA_MAYOR_ON_LS, BA_mayorOn ? `1` : `0`);
  } catch {}
  BA_mayorOnSubs.forEach((f) => f());
}
function BA_useMayorOn() {
  return (0, y.useSyncExternalStore)(
    (f) => (BA_mayorOnSubs.add(f), () => BA_mayorOnSubs.delete(f)),
    () => BA_mayorOn,
  );
}

// Toolbar button. The feature is still being fixed, so the hover says so.
function BA_MayorToggle() {
  let on = BA_useMayorOn();
  return (0, b.jsx)(`button`, {
    type: `button`,
    onClick: () => BA_mayorSetOn(!on),
    title: `Beta`,
    "aria-pressed": on,
    className: `rounded-md px-2 py-1 transition-colors ${on ? `bg-white/10 text-ink` : `text-mute hover:bg-white/5 hover:text-ink`}`,
    children: `Mayors`,
  });
}

// The list only knows terms up to the current one: once that ends the right edge of every chart goes bare.
function BA_mayorStale(terms) {
  return !Array.isArray(terms) || !terms.length || Date.now() > terms[terms.length - 1].end;
}

// Coflnet sends "MM/DD/YYYY HH:mm:ss +00:00" (start) and "MM/DD/YYYY HH:mm:ss" (end). Both are UTC.
function BA_mayorTime(s) {
  if (typeof s !== `string`) return NaN;
  let m = /^(\d{1,2})\/(\d{1,2})\/(\d{4})[ T](\d{1,2}):(\d{1,2}):(\d{1,2})/.exec(s.trim());
  if (!m) {
    let t = Date.parse(s);
    return Number.isFinite(t) ? t : NaN;
  }
  return Date.UTC(+m[3], +m[1] - 1, +m[2], +m[4], +m[5], +m[6]);
}

function BA_mayorParse(raw) {
  if (!Array.isArray(raw)) return [];
  let out = [];
  for (let e of raw) {
    if (!e || !e.winner) continue;
    let start = BA_mayorTime(e.start),
      end = BA_mayorTime(e.end),
      name = typeof e.winner.name === `string` ? e.winner.name.trim() : ``;
    if (!name || !Number.isFinite(start) || !Number.isFinite(end) || end <= start) continue;
    out.push({
      start,
      end,
      name,
      key: typeof e.winner.key === `string` ? e.winner.key : ``,
      year: Number.isFinite(e.year) ? e.year : null,
      minister:
        e.winner.minister && typeof e.winner.minister.name === `string` ? e.winner.minister.name : null,
    });
  }
  out.sort((a, t) => a.start - t.start || a.end - t.end);
  // Defensive: never let two terms overlap (bands would stack). Clamp the earlier one.
  for (let i = 1; i < out.length; i++) if (out[i].start < out[i - 1].end) out[i - 1].end = out[i].start;
  return out.filter((t) => t.end > t.start);
}

function BA_mayorFromCache() {
  try {
    let o = JSON.parse(localStorage.getItem(BA_MAYOR_LS) || `null`);
    if (!o || o.v !== 1 || !Number.isFinite(o.at) || Date.now() - o.at > BA_MAYOR_TTL) return null;
    if (!Array.isArray(o.terms) || !o.terms.length) return null;
    let t = o.terms.filter(
      (x) =>
        x && Number.isFinite(x.start) && Number.isFinite(x.end) && x.end > x.start && typeof x.name === `string` && x.name,
    );
    // A new term may have started since we cached: refetch instead of leaving the right edge bare.
    if (!t.length || Date.now() > t[t.length - 1].end) return null;
    return t;
  } catch {
    return null;
  }
}

// Resolves to the term list ([] when nothing could be loaded). A stale in-memory list (a term ended while
// the page stayed open) is refetched at most every BA_MAYOR_RETRY; until then the old list is returned.
function BA_mayorLoad() {
  if (BA_mayorMem && !BA_mayorStale(BA_mayorMem)) return Promise.resolve(BA_mayorMem);
  let c = BA_mayorMem ? null : BA_mayorFromCache();
  if (c) return (BA_mayorMem = c), Promise.resolve(c);
  if (BA_mayorReq) return BA_mayorReq;
  if (BA_mayorMem && Date.now() - BA_mayorLastTry < BA_MAYOR_RETRY) return Promise.resolve(BA_mayorMem);
  BA_mayorLastTry = Date.now();
  BA_mayorReq = fetch(BA_MAYOR_API, { headers: { Accept: `application/json` } })
    .then((r) => {
      if (!r.ok) throw Error(`mayor list: HTTP ${r.status}`);
      return r.json();
    })
    .then((j) => {
      let t = BA_mayorParse(j);
      if (!t.length) throw Error(`mayor list: no usable terms`);
      BA_mayorMem = t;
      BA_mayorReq = null;
      try {
        localStorage.setItem(BA_MAYOR_LS, JSON.stringify({ v: 1, at: Date.now(), terms: t }));
      } catch {}
      return t;
    })
    .catch((e) => {
      BA_mayorReq = null; // allow a later retry (e.g. next page visit)
      console.warn(`[BA] mayor bands off:`, (e && e.message) || e);
      return BA_mayorMem || [];
    });
  return BA_mayorReq;
}

// Last term whose [start,end) contains ts, or null.
function BA_mayorAt(terms, ts) {
  if (!Array.isArray(terms) || !terms.length || !Number.isFinite(ts)) return null;
  let lo = 0,
    hi = terms.length - 1,
    best = null;
  while (lo <= hi) {
    let mid = (lo + hi) >> 1;
    if (terms[mid].start <= ts) (best = terms[mid]), (lo = mid + 1);
    else hi = mid - 1;
  }
  return best && ts < best.end ? best : null;
}

var BA_MAYOR_DOT = {
  aatrox: `#ef4444`,
  cole: `#f59e0b`,
  diana: `#4ade80`,
  diaz: `#facc15`,
  finnegan: `#a3e635`,
  foxy: `#f472b6`,
  marina: `#38bdf8`,
  paul: `#a78bfa`,
  derpy: `#fb7185`,
  scorpius: `#c084fc`,
  jerry: `#22d3ee`,
  barry: `#94a3b8`,
};
function BA_mayorColor(t) {
  return (t && BA_MAYOR_DOT[String(t.name || ``).toLowerCase()]) || `#a09d93`;
}

// Extra row for the klinecharts tooltip legend (see `nd` in the bundle). Never throws.
function BA_mayorLegendRow(ts) {
  if (!BA_mayorOn) return [];
  try {
    let t = BA_mayorAt(BA_mayorMem, ts);
    if (!t) return [];
    return [
      {
        title: `Mayor: `,
        value: { text: t.minister ? `${t.name} · ${t.minister}` : t.name, color: BA_mayorColor(t) },
      },
    ];
  } catch {
    return [];
  }
}

// Same row in the `BA_Tip` shape ({k,v,c}) used by the Bazaar price chart.
function BA_mayorTipRow(ts) {
  return BA_mayorLegendRow(ts).map((r) => ({ k: `Mayor`, v: r.value.text, c: r.value.color }));
}

/* ============================== chart geometry ============================== */

// Fractional dataIndex of a timestamp inside a klinecharts data list (sorted ascending).
// Outside the list it extrapolates with the chart period, exactly like the chart's own x-axis.
function BA_mayorFloatIndex(list, ts, periodMs) {
  let n = list.length;
  if (!n || !Number.isFinite(ts)) return 0;
  let p = periodMs > 0 ? periodMs : 0;
  let first = list[0].timestamp,
    last = list[n - 1].timestamp;
  if (ts <= first) return p ? (ts - first) / p : 0;
  if (ts >= last) return n - 1 + (p ? (ts - last) / p : 0);
  let lo = 0,
    hi = n - 1;
  while (lo + 1 < hi) {
    let mid = (lo + hi) >> 1;
    if (list[mid].timestamp <= ts) lo = mid;
    else hi = mid;
  }
  let a = list[lo].timestamp,
    t = list[hi].timestamp;
  return t > a ? lo + (ts - a) / (t - a) : lo;
}

// Inverse of the above (used only to pick which terms can be visible).
function BA_mayorTimeAt(list, idx, periodMs) {
  let n = list.length;
  if (!n) return NaN;
  let p = periodMs > 0 ? periodMs : 36e5;
  if (idx <= 0) return list[0].timestamp + idx * p;
  if (idx >= n - 1) return list[n - 1].timestamp + (idx - (n - 1)) * p;
  let lo = Math.floor(idx),
    f = idx - lo;
  return list[lo].timestamp + (list[lo + 1].timestamp - list[lo].timestamp) * f;
}

/* ============================== the band layer ============================== */

var BA_MAYOR_PANE = `candle_pane`;

// "#rrggbb" (or "#rgb") + alpha -> "rgba(r,g,b,a)"; anything else is returned unchanged (already a colour).
function BA_mayorRgba(hex, a) {
  let m = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(String(hex || ``).trim());
  if (!m) return hex;
  let h = m[1].length === 3 ? m[1].replace(/./g, (c) => c + c) : m[1],
    n = parseInt(h, 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

// Band look: a quiet background hint, not a stripe pattern. Read once per chart/term list (theme tokens on :root).
//   fill  = the mayor's colour at 4% (who; 2.2% on thin 1Y/ALL bands), every other term +1.5% ink (keeps two
//           terms of one mayor apart)
//   sep   = 1 px term boundary, faint and fading out towards the top (where the price usually is)
//   label = 10 px mayor name at the bottom, muted and half transparent, only on bands >= 64 px
function BA_mayorCss() {
  let cs = null;
  try {
    cs = getComputedStyle(document.documentElement);
  } catch {}
  let v = (k, d) => {
    let s = cs ? (cs.getPropertyValue(k) || ``).trim() : ``;
    return s || d;
  };
  let ink = v(`--color-ink`, `#ece9e1`);
  return {
    alt: BA_mayorRgba(ink, 0.015),
    sep: `linear-gradient(to top,${BA_mayorRgba(ink, 0.1)},${BA_mayorRgba(ink, 0.02)})`,
    mute: v(`--color-mute`, `#a09d93`),
  };
}
var BA_MAYOR_TINT = 0.04; // mayor colour alpha on the band
var BA_MAYOR_TINT_THIN = 0.022; // ... on terms narrower than BA_MAYOR_THIN px (1Y / ALL): hundreds of them read as a barcode
var BA_MAYOR_THIN = 24;
var BA_MAYOR_LABEL_MIN = 64; // px: narrower bands get no name
var BA_MAYOR_SEP_MIN = 8; // px: narrower bands get no boundary line (at 1Y/ALL it is just noise)

function BA_mayorFill(t, col, thin) {
  let tint = BA_mayorRgba(BA_mayorColor(t), thin ? BA_MAYOR_TINT_THIN : BA_MAYOR_TINT);
  return t.year != null && t.year % 2 ? `linear-gradient(${tint},${tint}),${col.alt}` : tint;
}

// One band node per term, created once and reused while the chart moves.
function BA_mayorNode(t, col) {
  let d = document.createElement(`div`);
  d.style.cssText = `position:absolute;top:0;bottom:0;pointer-events:none;`;
  d.dataset.start = String(t.start);
  let sep = document.createElement(`i`);
  sep.style.cssText = `position:absolute;left:0;top:0;bottom:0;width:1px;display:none;`;
  sep.style.background = col.sep;
  d.appendChild(sep);
  let s = document.createElement(`span`);
  // Bottom of the plot: the top belongs to klinecharts' legend, which wraps to 2-3 lines on narrow windows.
  s.style.cssText = `position:absolute;bottom:4px;left:5px;right:3px;display:none;align-items:center;gap:4px;font-size:10px;line-height:1;white-space:nowrap;overflow:hidden;opacity:.55;`;
  s.style.color = col.mute;
  let dot = document.createElement(`i`);
  dot.style.cssText = `width:5px;height:5px;border-radius:9999px;flex:none;display:block;opacity:.8;`;
  dot.style.background = BA_mayorColor(t);
  s.appendChild(dot);
  s.appendChild(document.createTextNode(t.name));
  d.appendChild(s);
  d.__ba = { l: NaN, w: NaN, sep: !1, lab: !1, thin: null, t };
  return d;
}

function BA_mayorClear(el) {
  el.textContent = ``;
  el.__ba = null;
}

// Puts the bands at the chart's CURRENT time -> x mapping. Called on every scheduled frame (drag, wheel, pinch, Fit,
// resize, data), so it must not skip on a coarse "did the view change" check: klinecharts' getVisibleRange() is in
// whole bars and clamped to the data (from 0 / to dataLength), so a drag that moved the chart by less than a bar,
// or by anything while every bar was on screen, used to look unchanged and the bands stayed behind the candles.
// Instead each band node is kept per term and only the styles that really changed are written.
// x of a timestamp = convertToPixel of its FRACTIONAL dataIndex (linear, extrapolates past both ends); klinecharts'
// {timestamp} form floors to whole bars. Bands are clipped to [left edge of the first bar, plot width].
function BA_mayorPaint(el, chart, terms, periodMs, col) {
  let st = el.__ba || (el.__ba = { box: ``, nodes: new Map() });
  let size = chart.getSize(BA_MAYOR_PANE, `main`),
    list = chart.getDataList();
  if (!size || !(size.width > 0) || !(size.height > 0) || !Array.isArray(list) || !list.length) {
    BA_mayorClear(el);
    return;
  }
  let W = size.width,
    box = `${size.left || 0}|${size.top || 0}|${W}|${size.height}`;
  if (st.box !== box) {
    st.box = box;
    el.style.left = `${size.left || 0}px`;
    el.style.top = `${size.top || 0}px`;
    el.style.width = `${W}px`;
    el.style.height = `${size.height}px`;
  }
  let at = (idx) => {
    let c = chart.convertToPixel({ dataIndex: idx }, { paneId: BA_MAYOR_PANE });
    return c && Number.isFinite(c.x) ? c.x : null;
  };
  let px = (ts) => at(BA_mayorFloatIndex(list, ts, periodMs));
  // visible time window straight from the pixel mapping (+1 bar each side)
  let x0 = at(0),
    bs = typeof chart.getBarSpace === `function` ? chart.getBarSpace()?.bar : null,
    ok = x0 != null && bs > 0,
    tFrom = ok ? BA_mayorTimeAt(list, -x0 / bs - 1, periodMs) : -Infinity,
    tTo = ok ? BA_mayorTimeAt(list, (W - x0) / bs + 1, periodMs) : Infinity,
    minX = ok ? Math.max(0, x0 - bs / 2) : 0;
  let seen = new Set();
  for (let t of terms) {
    if (!(t.end > tFrom) || !(t.start < tTo)) continue;
    let x1 = px(t.start),
      x2 = px(t.end);
    if (x1 == null || x2 == null) continue;
    let l = Math.max(minX, Math.min(W, x1)),
      r = Math.max(minX, Math.min(W, x2)),
      w = r - l;
    if (!(w > 0.5)) continue;
    let d = st.nodes.get(t.start);
    if (!d || d.__ba.t !== t) {
      if (d) d.remove();
      d = BA_mayorNode(t, col);
      st.nodes.set(t.start, d);
      el.appendChild(d);
    }
    seen.add(t.start);
    let m = d.__ba;
    let thin = x2 - x1 < BA_MAYOR_THIN; // the term's full width, not the part on screen
    if (m.thin !== thin) (m.thin = thin), (d.style.background = BA_mayorFill(t, col, thin));
    if (m.l !== l) (m.l = l), (d.style.left = `${l}px`);
    if (m.w !== w) (m.w = w), (d.style.width = `${w}px`);
    // boundary line only where the term really starts on screen
    let sep = x1 >= minX && x1 <= W && w >= BA_MAYOR_SEP_MIN,
      lab = w >= BA_MAYOR_LABEL_MIN;
    if (m.sep !== sep) (m.sep = sep), (d.firstChild.style.display = sep ? `block` : `none`);
    if (m.lab !== lab) (m.lab = lab), (d.lastChild.style.display = lab ? `flex` : `none`);
  }
  for (let [k, d] of st.nodes)
    if (!seen.has(k)) {
      d.remove();
      st.nodes.delete(k);
    }
}

// Mounted as the FIRST child of the chart's `relative min-w-0 flex-1` wrapper, so it paints
// under the klinecharts canvases (which are transparent) without touching chart internals.
// `periodMs` = the chart's bar length (Bazaar chart); without it the AH chart's rule from `tf` is used.
function BA_MayorBands({ chart, ready, tf, periodMs: barMs }) {
  let host = (0, y.useRef)(null),
    on = BA_useMayorOn(),
    [terms, setTerms] = (0, y.useState)(() => BA_mayorMem);
  (0, y.useEffect)(() => {
    if (!on) return;
    let alive = !0;
    let get = () =>
      BA_mayorLoad().then((t) => {
        if (alive) setTerms((old) => (t && t.length ? (t === old ? old : t) : old || []));
      });
    if (BA_mayorStale(BA_mayorMem)) get();
    // the page can stay open for days: pick up the next term once the current one ends
    let iv = setInterval(() => {
      if (BA_mayorStale(BA_mayorMem)) get();
    }, 6e4);
    return () => {
      alive = !1;
      clearInterval(iv);
    };
  }, [on]);
  (0, y.useEffect)(() => {
    let el = host.current,
      list = terms || BA_mayorMem;
    if (!el) return;
    if (!on || !chart || !list || !list.length) {
      BA_mayorClear(el);
      return;
    }
    // must match the chart's own period: AH chart setPeriod(td(tf) ? 1 day : 1 hour), Bazaar chart passes its barMs
    let periodMs = barMs > 0 ? barMs : tf === `1M` || tf === `ALL` ? 864e5 : 36e5;
    let raf = 0,
      dead = !1,
      col = BA_mayorCss();
    BA_mayorClear(el); // new period / term list / colours: rebuild the nodes
    // Painted in the same animation frame as klinecharts' own canvas redraw: every chart action (scroll, zoom, data,
    // resize) runs synchronously in the input event, klinecharts lays out in a microtask and draws its canvases in the
    // next requestAnimationFrame, and this paint is queued for that same frame.
    let paint = () => {
      raf = 0;
      if (dead) return;
      try {
        BA_mayorPaint(el, chart, list, periodMs, col);
      } catch (e) {
        BA_mayorClear(el);
        console.warn(`[BA] mayor bands:`, (e && e.message) || e);
      }
    };
    let schedule = () => {
      if (!raf && !dead) raf = requestAnimationFrame(paint);
    };
    let acts = [`onVisibleRangeChange`, `onZoom`, `onScroll`],
      subbed = [];
    for (let a of acts) {
      try {
        chart.subscribeAction(a, schedule);
        subbed.push(a);
      } catch {}
    }
    let ro = null;
    try {
      ro = new ResizeObserver(schedule);
      if (el.parentElement) ro.observe(el.parentElement);
    } catch {}
    // safety net: the chart can also move without firing an action (fonts, late layout); a paint that finds
    // nothing moved writes nothing
    let iv = setInterval(schedule, 500);
    schedule();
    return () => {
      dead = !0;
      clearInterval(iv);
      if (raf) cancelAnimationFrame(raf);
      if (ro) ro.disconnect();
      for (let a of subbed) {
        try {
          chart.unsubscribeAction(a, schedule);
        } catch {}
      }
    };
  }, [on, chart, terms, ready, tf, barMs]);
  return (0, b.jsx)(`div`, {
    ref: host,
    "aria-hidden": `true`,
    style: { position: `absolute`, left: 0, top: 0, width: 0, height: 0, overflow: `hidden`, pointerEvents: `none` },
  });
}

/* ============================== "Clean" AH filter ============================== */

// Flattened-nbt keys that every item carries and that are not modifiers.
var BA_CLEAN_NEUTRAL = new Set([
  `type`,
  `tier`,
  `active`,
  `hideinfo`,
  `hiderightclick`,
  `nomove`,
  `petsoulbound`,
  `bossid`,
  `spawnedfor`,
  `spawnedforbest`,
  `donated_museum`,
  `item_tier`,
  `boss_tier`,
  `edition`,
]);
// Values that mean "not present".
var BA_CLEAN_EMPTY = new Set([``, `0`, `0.0`, `false`, `none`, `null`]);
// Filter kinds that describe a modifier — they cannot be combined with Clean.
// (`wimp` is only an Add-filter menu entry, parts/70-ah-tweaks.js; it adds an `nbt` filter.)
var BA_CLEAN_CONFLICT = new Set([`enchant`, `stars`, `reforge`, `recomb`, `hpc`, `nbt`, `wimp`]);

// True when a listing has no modifiers at all: no enchants, reforge, stars, recomb, hot potato
// books, gems, scrolls, runes, dyes, skins, pet xp/held item... Mirrors Coflnet's `Clean=yes`
// (verified against /auctions/tag/{TAG}/active/bin?Clean=yes for gear and pets).
function BA_isCleanListing(a, reforges) {
  if (!a || typeof a !== `object`) return !1;
  if (Array.isArray(a.enchantments) && a.enchantments.length) return !1;
  let rf = ``;
  try {
    rf = a.reforge != null ? String(a.reforge) : typeof Gi === `function` && a.itemName ? String(Gi(a, reforges || [])) : ``;
  } catch {
    rf = ``;
  }
  if (rf && rf.toLowerCase() !== `none`) return !1;
  let nbt = a.flattenedNbt || a.flatNbt || null;
  let stars = 0;
  try {
    stars = (typeof Ri === `function` ? Ri(nbt) : 0) || (typeof Vi === `function` && a.itemName ? Vi(a.itemName) : 0) || 0;
  } catch {
    stars = 0;
  }
  if (stars > 0) return !1;
  if (nbt && typeof nbt === `object`) {
    for (let k of Object.keys(nbt)) {
      let v = nbt[k] == null ? `` : String(nbt[k]).trim(),
        lk = k.toLowerCase();
      if (BA_CLEAN_EMPTY.has(v.toLowerCase())) continue;
      if (BA_CLEAN_NEUTRAL.has(lk)) continue;
      if (lk === `exp`) {
        if (Number(v) > 0) return !1; // levelled pet
        continue;
      }
      try {
        if (ea.test(k)) continue; // uuid / uid / timestamps / xp / ids — noise, same set the site uses
      } catch {}
      return !1;
    }
  }
  return !0;
}

// Keep the filter list sensible when Clean is switched on or a modifier filter is added.
function BA_cleanApply(list, added) {
  if (!Array.isArray(list)) return list;
  if (added === `clean`) return list.filter((f) => f && (f.kind === `clean` || !BA_CLEAN_CONFLICT.has(f.kind)));
  if (BA_CLEAN_CONFLICT.has(added)) return list.filter((f) => f && f.kind !== `clean`);
  return list;
}

// Hint shown on the "Add filter" menu entries that would clash with the current selection.
function BA_cleanHint(list, kind) {
  if (!Array.isArray(list)) return ``;
  let hasClean = list.some((f) => f && f.kind === `clean`);
  if (kind === `clean`) return list.some((f) => f && BA_CLEAN_CONFLICT.has(f.kind)) ? `clears modifier filters` : ``;
  if (hasClean && BA_CLEAN_CONFLICT.has(kind)) return `turns Clean off`;
  return ``;
}
