// ---- 20-listing-craft.js: "Craft cost: X" for ONE AH listing + hover/focus breakdown popover ----
// Plain JS inlined into the v12 bundle by patch_v12.mjs (module scope, after the engine). No JSX.
// Uses bundle ids: y (React), b (jsx runtime), en/tn (number formats), Ft/It/Wi (names), Oa (items info hook).
// Uses the shared parts (00-shared.js): useCraftCtx(), BA_CraftBoundary, BA_CraftMethodBadge, BA_craftName/Qty/Fmt.
// Engine namespace: BA_craft.
// Mount notes: notes/listing-craft-mounts.md.
//
// <BA_CraftListingCost tag item price [lazy] [compact] [className]>
//   tag   AH tag of the page (PET_X for pets); falls back to item.tag
//   item  the Coflnet row as-is (auction detail, active BIN row, sold row)
//   price what the listing costs / sold for (whole stack)
//   lazy  compute only when the popover is first opened (sold-list rows)
//   compact short trigger text for table cells

// ctx -> Map(listing key -> Promise<listingCraftCost result>). One computation per listing per ctx,
// shared by every mounted copy; the engine itself shares its downloads per ctx.
const BA_CraftLstCache = new WeakMap();

function BA_CraftLstGet(ctx, tag, item) {
  const E = BA_craft;
  let m = BA_CraftLstCache.get(ctx);
  if (!m) { m = new Map(); BA_CraftLstCache.set(ctx, m); }
  const key = item && item.uuid ? tag + '|' + item.uuid : item;
  let p = m.get(key);
  if (!p) {
    // no signal on purpose: the promise is shared, so one unmounting row must not abort it for the others
    p = E.listingCraftCost(tag, item, ctx, { loadRecipeTree: E.loadRecipeTree, cleanCraftCost: E.cleanCraftCost, priceItem: E.priceItem });
    m.set(key, p);
    p.catch(() => m.delete(key)); // let a later hover retry after a failure
  }
  return p;
}

// jsx helper: children as rest args, key taken out of props
function BA_CraftLstEl(type, props, ...kids) {
  const { key, ...p } = props || {};
  const k = kids.filter(c => c != null && c !== false && c !== '');
  if (k.length > 1) return (0, b.jsxs)(type, { ...p, children: k }, key);
  if (k.length === 1) p.children = k[0];
  return (0, b.jsx)(type, p, key);
}

function BA_CraftLstName(id, info, petBase) { return id ? BA_craftName(id, info, petBase) : ''; }

// empty span without a method keeps the ba-cr-line grid (name, badge, total) aligned
function BA_CraftLstBadge({ method, note }) {
  return method ? (0, b.jsx)(BA_CraftMethodBadge, { method, note }) : BA_CraftLstEl('span', null);
}

// listing price vs craft cost: {pct, cheaper} or null
function BA_CraftLstDiff(price, cost) {
  if (!(price > 0) || !(cost > 0) || !Number.isFinite(price) || !Number.isFinite(cost)) return null;
  return { pct: Math.abs(price - cost) / cost * 100, cheaper: price < cost };
}

function BA_CraftLstDiffText({ price, cost }) {
  const d = BA_CraftLstDiff(price, cost);
  if (!d) return null;
  if (d.pct < 0.05) return BA_CraftLstEl('span', { className: 'text-mute' }, 'same as the listing');
  return BA_CraftLstEl('span', { className: d.cheaper ? 'text-up' : 'text-down' },
    'listing is ' + d.pct.toFixed(1) + '% ' + (d.cheaper ? 'cheaper' : 'dearer'));
}

function BA_CraftLstTotal(res, mode, n) {
  const t = res && res.totals ? res.totals[mode] : null;
  return t == null || !Number.isFinite(t) ? null : t * n;
}

// One side ("from scratch" or "easy"): clean base line (expandable) + modifier lines, total in the header.
// Same look as BA_CraftTwoLists (00-shared.js): ba-cr-col-h header, ba-cr-line rows, shared badge and colours.
function BA_CraftLstColumn({ res, mode, title, sub, color, open, onToggle, info, petBase, n }) {
  const base = res.base ? res.base[mode] : null;
  const market = res.baseSource === 'market'; // no recipe: the clean item is bought, not crafted
  const lines = (res.modifiers && res.modifiers[mode]) || [];
  const total = BA_CraftLstTotal(res, mode, n);
  const num = (v, miss) => BA_CraftLstEl('span', { className: 'ba-cr-num', style: { minWidth: 56, textAlign: 'right', color: v == null ? '#ef4444' : undefined } },
    v == null ? miss || 'n/a' : BA_craftFmt(v));
  const rows = [];

  if (base) {
    rows.push(BA_CraftLstEl('button', {
      key: 'base', type: 'button', onClick: onToggle, 'aria-expanded': open,
      className: 'ba-cr-line w-full rounded text-left hover:bg-white/5',
    },
      BA_CraftLstEl('span', { className: 'nm' }, BA_CraftLstEl('span', { className: 'text-mute' }, open ? '▾ ' : '▸ '), market ? 'Clean item (no recipe, bought)' : 'Clean item'),
      (0, b.jsx)(BA_CraftLstBadge, { method: base.method, note: base.note }),
      num(base.total)));
    if (open) {
      const flat = BA_craft.flattenTree(base).filter(r => r.depth > 0);
      for (const [i, r] of flat.slice(0, 150).entries()) {
        rows.push(BA_CraftLstEl('div', { key: 'b' + i, className: 'ba-cr-line', style: { paddingLeft: Math.min(r.depth, 8) * 12 } },
          BA_CraftLstEl('span', { className: 'nm', title: r.note || undefined },
            BA_CraftLstEl('span', { className: 'text-mute' }, '└ '),
            r.method !== 'coins' && r.qty != null && r.qty !== 1 ? BA_CraftLstEl('span', { className: 'ba-cr-num text-mute' }, BA_craftQty(r.qty) + '× ') : null,
            BA_CraftLstName(r.id, info, petBase)),
          (0, b.jsx)(BA_CraftLstBadge, { method: r.method, note: r.note }),
          num(r.total)));
      }
      if (flat.length > 150) rows.push(BA_CraftLstEl('div', { key: 'more', className: 'text-mute' }, '+' + (flat.length - 150) + ' more…'));
      if (!flat.length) rows.push(BA_CraftLstEl('div', { key: 'nochild', className: 'text-mute', style: { paddingLeft: 12 } }, market ? (base.note || 'No recipe: the clean item is bought.') : 'Bought as a whole, not crafted.'));
    }
  } else {
    rows.push(BA_CraftLstEl('div', { key: 'base', className: 'text-mute' },
      res.baseSource == null ? 'No recipe and no market price for the clean item: only the modifiers below are counted.' : 'Clean item: no price.'));
  }

  for (const [i, l] of lines.entries()) {
    // "Critical 6 · Critical VI" says the same thing twice: enchant books only show the item when it is not 1 book of that level
    const sameBook = l.kind === 'enchant' && /^ENCHANTMENT_/.test(l.id || '') && !(l.qty > 1);
    const what = l.id && !sameBook && l.id !== 'SKYBLOCK_COIN' && l.method !== 'coins'
      ? (l.qty && l.qty !== 1 ? BA_craftQty(l.qty) + '× ' : '') + BA_CraftLstName(l.id, info, petBase) : '';
    rows.push(BA_CraftLstEl('div', { key: 'm' + i, className: 'ba-cr-line', title: l.note || undefined },
      BA_CraftLstEl('span', { className: 'nm' },
        l.label || l.kind,
        what && what !== l.label ? BA_CraftLstEl('span', { className: 'text-mute' }, ' · ' + what) : null),
      l.kind === 'unknown' ? BA_CraftLstEl('span', null) : (0, b.jsx)(BA_CraftLstBadge, { method: l.method, note: l.note }),
      l.total == null && l.kind === 'unknown'
        ? BA_CraftLstEl('span', { className: 'ba-cr-num text-mute', style: { minWidth: 56, textAlign: 'right' } }, 'not priced')
        : num(l.total)));
  }

  return BA_CraftLstEl('div', { style: { minWidth: 0 } },
    BA_CraftLstEl('div', { className: 'ba-cr-col-h' },
      BA_CraftLstEl('span', null, BA_CraftLstEl('b', null, title), BA_CraftLstEl('span', { className: 'text-mute' }, ' ' + sub)),
      BA_CraftLstEl('b', { className: 'ba-cr-num', style: { color: total == null ? '#ef4444' : color } },
        total == null ? "can't be priced" : tn(total, 0) + (n > 1 ? ' (×' + n + ')' : ''))),
    ...rows);
}

// Popover body. Only rendered while open, so Oa() (items info) is only subscribed then.
function BA_CraftListingPopover({ st, ctxReady, tag, item, price, open, onToggle, n }) {
  const info = Oa();
  const petBase = tag && String(tag).startsWith('PET_') ? String(tag).slice(4) : undefined;
  const res = st.res;
  let body;
  if (!ctxReady) body = BA_CraftLstEl('p', { className: 'text-mute' }, 'Waiting for bazaar prices…');
  else if (st.err && !res) body = BA_CraftLstEl('p', { className: 'text-down' }, 'Craft cost failed: ' + (st.err && st.err.message || 'error'));
  else if (!res) body = BA_CraftLstEl('p', { className: 'text-mute' }, 'Working out the craft cost… (the first one loads recipes and prices)');
  else {
    const fs = BA_CraftLstTotal(res, 'fromScratch', n), ez = BA_CraftLstTotal(res, 'easy', n);
    const miss = (res.missing || []).filter(Boolean);
    // no recipe and no market price for the clean item: totals are modifiers only, a % vs the listing is meaningless;
    // nothing priced at all (a clean item without recipe/market): no totals to show
    const mods = res.baseSource == null, empty = mods && !(fs > 0) && !(ez > 0);
    const notes = (res.notes || []).filter(Boolean);
    const sumRow = (label, val, cls, cost) => BA_CraftLstEl('div', { className: 'flex flex-wrap items-baseline justify-between gap-x-3' },
      BA_CraftLstEl('span', { className: 'text-mute' }, label),
      BA_CraftLstEl('span', { className: 'flex items-baseline gap-2' },
        cost !== undefined ? (0, b.jsx)(BA_CraftLstDiffText, { price, cost }) : null,
        BA_CraftLstEl('span', { className: 'num font-semibold ' + cls }, val == null ? 'n/a' : tn(val, 0))));
    body = BA_CraftLstEl(b.Fragment, null,
      BA_CraftLstEl('div', { className: 'mb-2 rounded border border-line bg-panel px-2 py-1.5 text-xs' },
        sumRow('Listing price', price > 0 ? price : null, 'text-ink'),
        empty ? BA_CraftLstEl('div', { className: 'text-mute' }, 'No recipe and no market price for this item, and nothing on it to price.') : null,
        empty ? null : sumRow(mods ? 'From scratch (modifiers only, no recipe)' : 'Craft from scratch', fs, 'text-sell', mods ? undefined : fs),
        empty ? null : sumRow(mods ? 'Easy way (modifiers only)' : 'Easy way', ez, 'text-buy', mods ? undefined : ez),
        res.baseSource === 'market' ? BA_CraftLstEl('div', { className: 'text-mute', style: { fontSize: 11 } }, 'No recipe: the clean item is counted at its market price.') : null,
        notes.length ? BA_CraftLstEl('div', { className: 'text-mute', style: { fontSize: 11 } }, 'Not counted: ' + notes.join(', ') + '.') : null),
      BA_CraftLstEl('div', { className: 'ba-cr-two' },
        (0, b.jsx)(BA_CraftLstColumn, { res, mode: 'fromScratch', title: 'From scratch', sub: '(cheapest)', color: '#38bdf8', open, onToggle, info, petBase, n }),
        (0, b.jsx)(BA_CraftLstColumn, { res, mode: 'easy', title: 'Easy way', sub: '(insta-buy + lowest BIN)', color: '#fbbf24', open, onToggle, info, petBase, n })),
      BA_CraftLstEl('div', { className: 'mt-2 text-mute', style: { fontSize: 11 } },
        'From scratch = buy orders, cheapest of buy/craft/forge/NPC/lowest BIN at every step. Easy = insta-buy + lowest BIN, no sub-crafting. Modifier lines = what this listing has on top of the clean item.'),
      miss.length ? BA_CraftLstEl('p', { className: 'mt-2 text-[11px] text-down' },
        'No price for: ' + miss.slice(0, 8).map(x => /^[A-Z0-9_;:-]+$/.test(x) ? BA_CraftLstName(x, info, petBase) : x).join(', ') + (miss.length > 8 ? ' (+' + (miss.length - 8) + ' more)' : '')) : null,
      st.err ? BA_CraftLstEl('p', { className: 'mt-1 text-[11px] text-down' }, 'Refresh failed, showing the previous result.') : null);
  }
  return BA_CraftLstEl(b.Fragment, null,
    BA_CraftLstEl('div', { className: 'mb-1.5 flex items-baseline justify-between gap-3' },
      BA_CraftLstEl('span', { className: 'text-[11px] font-semibold uppercase text-accent' }, 'Craft cost breakdown'),
      item && item.itemName ? BA_CraftLstEl('span', { className: 'min-w-0 truncate text-[11px] text-mute' }, Wi(String(item.itemName)) + (n > 1 ? ' ×' + n : '')) : null),
    body);
}

function BA_CraftListingCostInner({ tag, item, price, lazy, compact, className }) {
  const ctx = useCraftCtx(); // null while the bazaar loads
  tag = String(tag || (item && item.tag) || '');
  const n = item && item.count > 1 ? item.count : 1;
  const [want, setWant] = (0, y.useState)(!lazy);
  const [open, setOpen] = (0, y.useState)(false);
  const [expanded, setExpanded] = (0, y.useState)(false);
  const [st, setSt] = (0, y.useState)({ res: null, err: null });
  const wrap = (0, y.useRef)(null), trig = (0, y.useRef)(null), pop = (0, y.useRef)(null), timer = (0, y.useRef)(0);
  const skipFocus = (0, y.useRef)(false); // Escape moves focus back to the trigger: that focus must not reopen it
  const id = (0, y.useId)();
  const itemKey = item && item.uuid ? item.uuid : item;

  (0, y.useEffect)(() => {
    if (!want || !ctx || !tag || !item) return;
    // A lazy row (sold list) keeps its last figure while closed: without this every row hovered once would
    // recompute on each new bazaar ctx (~1/min) for as long as the page is open. It refreshes when reopened.
    if (lazy && !open && st.res) return;
    let alive = true;
    BA_CraftLstGet(ctx, tag, item).then(
      res => { if (alive) setSt({ res, err: null }); },
      err => { if (alive && !(err && err.name === 'AbortError')) setSt(s => ({ res: s.res, err })); });
    return () => { alive = false; };
  }, [want, open, ctx, tag, itemKey]);

  (0, y.useEffect)(() => () => clearTimeout(timer.current), []);

  const show = () => { clearTimeout(timer.current); setWant(true); setOpen(true); };
  const hide = () => { clearTimeout(timer.current); timer.current = setTimeout(() => setOpen(false), 150); };

  // Fixed-position popover: below the trigger if it fits, else above if there is more room there;
  // clamped to the viewport horizontally, max-height = the room on the chosen side (scrolls inside).
  (0, y.useLayoutEffect)(() => {
    if (!open) return;
    const place = e => {
      const p = pop.current, t = trig.current;
      if (!p || !t) return;
      if (e && e.target && e.target.nodeType === 1 && p.contains(e.target)) return; // scrolling inside the popover
      const r = t.getBoundingClientRect(), m = 8, gap = 6;
      const vw = document.documentElement.clientWidth || window.innerWidth, vh = window.innerHeight;
      p.style.maxHeight = 'none';
      const w = p.offsetWidth, h = p.offsetHeight;
      const below = vh - r.bottom - gap - m, above = r.top - gap - m;
      const down = h <= below || below >= above;
      const room = Math.max(120, down ? below : above);
      const hh = Math.min(h, room);
      p.style.maxHeight = room + 'px';
      p.style.top = Math.max(m, down ? r.bottom + gap : r.top - gap - hh) + 'px';
      p.style.left = Math.max(m, Math.min(r.left, vw - m - w)) + 'px';
    };
    place();
    window.addEventListener('scroll', place, true);
    window.addEventListener('resize', place);
    return () => { window.removeEventListener('scroll', place, true); window.removeEventListener('resize', place); };
  }, [open, st, expanded]);

  const fs = BA_CraftLstTotal(st.res, 'fromScratch', n);
  const noBase = st.res && st.res.baseSource == null; // no recipe, no market price: modifiers only
  const nothing = noBase && !(fs > 0); // ... and no modifiers either: there is no craft cost to show
  const val = !ctx && want ? '…' : st.res ? (nothing ? 'no recipe' : fs == null ? 'n/a' : BA_craftFmt(fs)) : want ? '…' : 'ⓘ';
  const trigger = BA_CraftLstEl('span', {
    ref: trig, role: 'button', tabIndex: 0, 'aria-expanded': open, 'aria-controls': open ? id : undefined,
    'aria-label': 'Craft cost, open the breakdown',
    style: { cursor: 'help' }, className: 'rounded px-1 hover:bg-white/5 ' + (compact ? 'text-[11px] text-mute' : 'text-xs text-mute'),
    onClick: show,
    onKeyDown: e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); show(); } },
  },
    compact ? 'craft ' : 'Craft cost: ',
    BA_CraftLstEl('span', { className: nothing ? 'text-mute' : 'num font-semibold text-sell' }, val),
    noBase && !nothing ? BA_CraftLstEl('span', { className: 'text-mute', title: 'No recipe: only the modifiers are counted' }, compact ? '*' : ' (mods only, no recipe)') : null,
    !compact && st.res && n > 1 ? BA_CraftLstEl('span', { className: 'text-mute' }, ' (×' + n + ')') : null);

  return BA_CraftLstEl('span', {
    ref: wrap, title: '', // suppress a parent's native title tooltip (sold-row price cell)
    className: 'relative inline-block ' + (className || ''),
    onMouseEnter: show, onMouseLeave: hide,
    onFocus: () => { if (skipFocus.current) skipFocus.current = false; else show(); },
    onBlur: e => { if (!wrap.current || !wrap.current.contains(e.relatedTarget)) { clearTimeout(timer.current); setOpen(false); } },
    onKeyDown: e => { if (e.key === 'Escape' && open) { e.stopPropagation(); clearTimeout(timer.current); setOpen(false); if (trig.current && document.activeElement !== trig.current) { skipFocus.current = true; trig.current.focus(); } } },
    // mounted inside <a> rows/cards: clicks here must not navigate
    onClick: e => { e.preventDefault(); e.stopPropagation(); },
  },
    trigger,
    open ? BA_CraftLstEl('div', {
      ref: pop, id, role: 'dialog', 'aria-label': 'Craft cost breakdown',
      tabIndex: -1, // clicks inside focus the popover, so onBlur keeps it open
      className: 'ba-cr-pop text-left font-sans', // same box as the clean-price hover (00-shared.js BA_CraftHover)
      style: { top: 0, left: 0, overflow: 'auto', pointerEvents: 'auto', whiteSpace: 'normal', cursor: 'auto' },
    }, (0, b.jsx)(BA_CraftListingPopover, { st, ctxReady: !!ctx, tag, item, price, open: expanded, onToggle: () => setExpanded(v => !v), n })) : null);
}

function BA_CraftListingCost(props) {
  const k = (props.item && props.item.uuid) || props.tag || 'x';
  return (0, b.jsx)(BA_CraftBoundary, { label: 'Craft cost', inline: true, children: (0, b.jsx)(BA_CraftListingCostInner, props) }, k);
}
