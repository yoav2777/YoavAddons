// ===== Bazaar "Book Flips" sub-tab (parts/40-book-flips.js) =====
// Buy low-level enchant books, anvil-combine them into the highest level the anvil can make, sell that book.
// Mounted by patch_v12.mjs: route #/ renders BA_BazaarPage (Market = original `af`, or Book Flips when #/?sub=books),
// and af's header gets the BA_BazaarSubTabs switch. Uses bundle ids: y, b, $t (bazaar rows), Xt (bazaar query),
// Kt (settings: tax, outbid), Qi (parse "5m"), jt (roman), en/tn (format), Jr (icon), zr (svg icon), lt (hash query),
// ft (navigate), st (routes); BA_CraftBoundary / BA_el from parts/00-shared.js; BA_craft.canAnvilEnchant (engine).
//
// Hypixel naming inversion (see notes/bundle-map.md §1): site `instaBuy` = buy_summary[0] = lowest SELL OFFER,
// site `instaSell` = sell_summary[0] = highest BUY ORDER. `soldWeek` (sellMovingWeek) = items insta-sold into buy
// orders, `boughtWeek` (buyMovingWeek) = items insta-bought out of sell offers.

// Highest level the anvil can make, for books the shared engine rule (BA_craft.canAnvilEnchant) cannot judge: not
// ultimates, not enchanting-table enchants. Hand-kept, derived from the NEU repo "Source:"/"Sources:" lore (see
// notes/book-flips.md): an enchant is only in here when every level above its lowest obtainable one lists no source
// but the Bazaar, i.e. those levels exist yet cannot be obtained anywhere except by combining. The cap is the last
// such level (Dedication IV comes from garden visitors, Charm VI from a chain, Turbo VI/VII from the Turbo Gourd,
// so those stop below). Level-up enchants (Champion, Compact, Cultivating, Expertise, Hecatomb) gain levels by use,
// not in an anvil, and are deliberately absent. Anything not listed falls back to the engine rule.
const BA_bookAnvilMax = {
  BUG_BLENDER: 5, CHARM: 5, CORRUPTION: 5, DEDICATION: 3, DIVINE_GIFT: 5, FEAST: 5, ICE_COLD: 5, KARMA: 5,
  PALEONTOLOGIST: 5, PESTERMINATOR: 5, PETALFALL: 5, QUANTUM: 5, STEALTH: 5, SUGAR_RUSH: 5, TIDAL: 3,
  TURBO_CACTUS: 5, TURBO_CANE: 5, TURBO_CARROT: 5, TURBO_COCO: 5, TURBO_MELON: 5, TURBO_MOONFLOWER: 5,
  TURBO_MUSHROOMS: 5, TURBO_POTATO: 5, TURBO_PUMPKIN: 5, TURBO_ROSE: 5, TURBO_SUNFLOWER: 5, TURBO_WARTS: 5,
  TURBO_WHEAT: 5,
};
// canAnvil for Book Flips: the hand-kept cap when there is one, else the shared engine rule (ultimates any level,
// table enchants up to one level over the table max). Craft prices keep using the engine rule on its own.
function BA_bookCanAnvil(name, level) {
  const cap = BA_bookAnvilMax[name];
  return cap != null ? level > 1 && level <= cap : BA_craft.canAnvilEnchant(name, level);
}

// Pure flip math (node test: patch/tests/book-flips.test.mjs).
// products: site bazaar products [{id, instaBuy, instaSell, soldWeek, boughtWeek}]
// opts: { tax (fraction), instaIn, instaOut, canAnvil(name, level) -> bool,
//         tick: price step for orders (0.1 with the site's "Outbid / undercut by 0.1 coins" setting on, else 0) }
// -> rows, one per (enchant, source level). Per enchant exactly one target level (the highest the anvil can make
//    that is a bazaar product); enchants whose target has no price are skipped, never replaced by a lower target.
function BA_bookFlips(products, { tax = 0.0125, instaIn = false, instaOut = false, tick = 0, canAnvil } = {}) {
  if (typeof canAnvil !== 'function') throw new Error('BA_bookFlips: canAnvil missing');
  const step = typeof tick === 'number' && Number.isFinite(tick) && tick > 0 ? tick : 0;
  const pos = (v) => (typeof v === 'number' && Number.isFinite(v) && v > 0 ? v : null);
  const perHour = (w) => (typeof w === 'number' && Number.isFinite(w) && w > 0 ? w / 168 : 0);
  const books = new Map(); // NAME -> Map(level -> product)
  for (const p of products || []) {
    const m = /^ENCHANTMENT_(.+)_(\d+)$/.exec((p && p.id) || '');
    if (!m || !(+m[2] >= 1)) continue;
    let g = books.get(m[1]);
    if (!g) books.set(m[1], (g = new Map()));
    g.set(+m[2], p);
  }
  const rows = [];
  for (const [name, g] of books) {
    // climb while two books of level L-1 combine into L; the target is the highest such L that is a bazaar product
    let target = 0;
    const top = Math.max(...g.keys());
    for (let L = 2; L <= top && canAnvil(name, L); L++) if (g.has(L)) target = L;
    if (!target) continue;
    const out = g.get(target);
    // output: sell offer one step under the lowest sell offer (fills when players insta-buy -> boughtWeek),
    // or insta-sell into the top buy order (limited by how fast new buy orders appear ~ soldWeek)
    const ask = pos(out.instaBuy), bid = pos(out.instaSell);
    const outPrice = instaOut ? bid : ask != null && ask - step > 0 ? ask - step : null;
    if (outPrice == null) continue;
    const outRate = instaOut ? perHour(out.soldWeek) : perHour(out.boughtWeek);
    const net = outPrice * (1 - tax);
    for (const [k, src] of g) {
      if (k >= target) continue;
      // input: buy order one step over the top buy order (fills when players insta-sell -> soldWeek),
      // or insta-buy from the lowest sell offer (limited by how fast new sell offers appear ~ boughtWeek)
      const sAsk = pos(src.instaBuy), sBid = pos(src.instaSell);
      const inPrice = instaIn ? sAsk : sBid != null ? sBid + step : null;
      if (inPrice == null) continue;
      const inRate = instaIn ? perHour(src.boughtWeek) : perHour(src.soldWeek);
      const n = 2 ** (target - k);
      const cost = n * inPrice;
      const profit = net - cost;
      const inFlips = inRate / n;
      const flips = Math.min(inFlips, outRate);
      rows.push({
        key: name + ':' + k, name, from: k, target, books: n, srcId: src.id, outId: out.id,
        inPrice, cost, outPrice, net, taxPaid: outPrice - net, profit, pct: profit / cost,
        inRate, outRate, flips, limit: inFlips <= outRate ? 'input' : 'output',
        coinsHr: profit > 0 ? flips * profit : 0,
      });
    }
  }
  return rows;
}

// ---- Bazaar page: Market (unchanged) | Book Flips, choice kept in the hash (#/?sub=books) ----
function BA_bazaarSub() { return lt(window.location.hash, 'sub') === 'books' ? 'books' : 'market'; }

function BA_BazaarPage() {
  return BA_bazaarSub() === 'books'
    ? (0, b.jsx)(BA_CraftBoundary, { label: 'Book Flips', children: (0, b.jsx)(BA_BookFlips, {}) })
    : (0, b.jsx)(af, { mode: 'all' });
}

// Book Flips watchlist: row keys, own store (the Market watchlist `L` holds bazaar item ids)
const BA_bfWatch = Ht()(Gt((e) => ({ ids: [], toggle: (k) => e((s) => ({ ids: s.ids.includes(k) ? s.ids.filter((x) => x !== k) : [k, ...s.ids] })) }), { name: 'bz.bookwatch.v1' }));

// Watchlist tab: Market (original af) | Book Flips (starred flips only), #/watchlist?sub=books
function BA_WatchlistPage() {
  return BA_bazaarSub() === 'books'
    ? (0, b.jsx)(BA_CraftBoundary, { label: 'Book Flips watchlist', children: (0, b.jsx)(BA_BookFlips, { watch: true }) })
    : (0, b.jsx)(af, { mode: 'watchlist' });
}

function BA_BazaarSubTabs({ sub, base = st.market }) {
  const tab = (id, label, href) => (0, b.jsx)('button', {
    type: 'button', role: 'tab', 'aria-selected': sub === id,
    onClick: () => { if (sub !== id) ft(href); },
    className: `rounded-md px-2.5 py-1 text-sm transition-colors ${sub === id ? 'bg-accent/20 text-accent' : 'text-mute hover:bg-white/5 hover:text-ink'}`,
    children: label,
  }, id);
  return (0, b.jsxs)('div', { role: 'tablist', className: 'flex gap-0.5 rounded-lg border border-line bg-panel p-0.5',
    children: [tab('market', 'Market', base), tab('books', 'Book Flips', base + '?sub=books')] });
}

const BA_bookCols = [
  { key: 'books', label: 'Books', title: 'Books of the lower level needed for one target book (2^levels)' },
  { key: 'cost', label: 'Input cost', title: 'Price of all input books' },
  { key: 'outPrice', label: 'Sells for', title: 'Price of the target book (before tax)' },
  { key: 'profit', label: 'Profit/flip', title: 'Target price after Bazaar tax minus input cost. Anvil combining costs XP levels only, no coins.' },
  { key: 'pct', label: 'Margin', title: 'Profit per flip as a percentage of the input cost' },
  { key: 'flips', label: 'Flips/hr', title: 'min(input fill rate / books needed, output fill rate), 7-day averages. Ignores competition.' },
  { key: 'coinsHr', label: 'Coins/hr', title: 'Flips/hr × profit per flip (0 when unprofitable). Rough upper bound.' },
];
const BA_bookGrid = '44px minmax(220px,2.2fr) 120px 64px repeat(6,minmax(96px,1fr)) 84px';
// same "very wide spread" flag the Market tab uses (kt): margin over 100% means the top orders are probably
// placeholders, so the price (and every coins/hr built on it) is not achievable
const BA_bookWide = (r) => r.pct > 1;
const BA_bookWideTip = 'Very wide spread (margin over 100%): one of the top orders may be a placeholder, so check the order book before trusting this number';
const BA_bookSign = (v) => (v == null ? 'text-mute' : v > 0 ? 'text-up' : v < 0 ? 'text-down' : 'text-mute');
const BA_bookInput = 'num w-16 rounded-md border bg-panel px-2 py-1 text-ink placeholder:text-mute/60 focus:border-accent focus:outline-none';

function BA_BookSeg({ label, title, value, options, onChange }) {
  return (0, b.jsxs)('div', { className: 'flex items-center gap-1.5 text-mute', title, children: [label,
    (0, b.jsx)('div', { className: 'flex gap-0.5 rounded-lg border border-line bg-panel p-0.5', children: options.map(([v, l]) =>
      (0, b.jsx)('button', { type: 'button', onClick: () => onChange(v),
        className: `rounded-md px-2.5 py-1 transition-colors ${value === v ? 'bg-white/10 text-ink' : 'text-mute hover:text-ink'}`, children: l }, String(v))) })] });
}

// text filter; parse(s) -> number | null. Invalid text is shown red and ignored.
function BA_BookField({ label, title, value, onChange, bad, placeholder = 'any' }) {
  return (0, b.jsxs)('label', { className: 'flex items-center gap-1.5 text-mute', title, children: [label,
    (0, b.jsx)('input', { value, onChange: (e) => onChange(e.target.value), placeholder,
      className: `${BA_bookInput} ${bad ? 'ba-bf-bad' : 'border-line'}` })] });
}

function BA_BookFlips({ watch = false }) {
  const { byId, snapshot, loading, error } = $t();
  const bz = Xt();
  const tax = Kt((s) => s.tax);
  const outbid = Kt((s) => s.outbid); // same setting the Market tab uses: order prices step by 0.1 or not at all
  const tick = outbid ? 0.1 : 0;
  const [instaIn, setInstaIn] = (0, y.useState)(false);
  const [instaOut, setInstaOut] = (0, y.useState)(false);
  const [minCoins, setMinCoins] = (0, y.useState)('');
  const [minMargin, setMinMargin] = (0, y.useState)('');
  const [minProfit, setMinProfit] = (0, y.useState)('');
  const [search, setSearch] = (0, y.useState)('');
  const [hideLoss, setHideLoss] = (0, y.useState)(true);
  // off by default: unlike a single bazaar item, a >100% margin over 16 books is often just the price of the levels,
  // not a placeholder order, so the rows are flagged (⚠ on Margin and Coins/hr) instead of being hidden
  const [hideWide, setHideWide] = (0, y.useState)(false);
  const [sort, setSort] = (0, y.useState)({ key: 'coinsHr', dir: 'desc' });
  const starred = BA_bfWatch((s) => s.ids);
  const toggleStar = BA_bfWatch((s) => s.toggle);

  const all = (0, y.useMemo)(() => {
    if (!snapshot) return [];
    return BA_bookFlips(snapshot.products, { tax, instaIn, instaOut, tick, canAnvil: BA_bookCanAnvil }).map((r) => {
      const row = byId.get(r.outId);
      return { ...r, info: row?.info, label: (row?.name ?? r.name).replace(/\s+[IVXL]+$/, '') };
    });
  }, [snapshot, byId, tax, instaIn, instaOut, tick]);

  // '' = no filter; anything else must parse (k/m/b allowed) or it is flagged and ignored
  const num = (s, pct) => { const t = s.trim(); if (!t) return { v: null, bad: false };
    const v = pct ? Number(t.replace(/%$/, '')) : Qi(t); return v == null || !Number.isFinite(v) ? { v: null, bad: true } : { v, bad: false }; };
  const fc = num(minCoins), fm = num(minMargin, true), fp = num(minProfit);

  const list = (0, y.useMemo)(() => {
    const q = search.trim().toLowerCase();
    let out = all.filter((r) => (!watch || starred.includes(r.key)) && (!hideLoss || r.profit > 0)
      && (!hideWide || !BA_bookWide(r))
      && (fc.v == null || r.coinsHr >= fc.v)
      && (fm.v == null || r.pct * 100 >= fm.v)
      && (fp.v == null || r.profit >= fp.v)
      && (!q || r.label.toLowerCase().includes(q) || r.name.toLowerCase().replace(/_/g, ' ').includes(q)));
    const d = sort.dir === 'asc' ? 1 : -1;
    return out.sort((a, c) => sort.key === 'name' ? d * a.label.localeCompare(c.label) || a.from - c.from
      : typeof a[sort.key] === 'string' ? d * a[sort.key].localeCompare(c[sort.key]) || c.coinsHr - a.coinsHr : d * (a[sort.key] - c[sort.key]));
  }, [all, watch, starred, hideLoss, hideWide, fc.v, fm.v, fp.v, search, sort]);

  // Only the rows on screen are rendered (same virtualizer + overscan as the Market table), so a poll
  // that rebuilds every row object re-renders ~25 rows instead of all ~175. The page scrolls, not the box
  // (parts/90-page-scroll.js).
  const scrollRef = (0, y.useRef)(null);
  const virt = BA_usePageVirt({ count: list.length, listRef: scrollRef, head: 39, minW: 1224, estimateSize: () => BA_BOOK_ROW_H, overscan: 14 });

  const setKey = (key) => setSort((s) => ({ key, dir: s.key === key ? (s.dir === 'desc' ? 'asc' : 'desc') : key === 'name' ? 'asc' : 'desc' }));
  const head = (key, label, title, left) => (0, b.jsxs)('button', { type: 'button', onClick: () => setKey(key), title,
    className: `flex h-full items-center gap-1 px-1 hover:text-ink ${left ? 'justify-start pl-2.5' : 'justify-end pr-3'} ${sort.key === key ? 'text-ink' : ''}`,
    children: [label, sort.key === key && (0, b.jsx)(zr, { name: sort.dir === 'asc' ? 'arrowUp' : 'arrowDown', size: 12 })] }, key);
  const msg = (children, tone) => (0, b.jsx)('div', { className: 'mx-auto max-w-[1500px] px-4 py-16', children: (0, b.jsx)('div', {
    className: `rounded-xl border border-line bg-panel px-6 py-10 text-center text-sm ${tone === 'error' ? 'text-down' : 'text-mute'}`, children }) });

  if (loading) return msg('Loading the Bazaar…');
  if (error && !snapshot) return msg([`Couldn’t load prices from Hypixel (${error instanceof Error ? error.message : 'unknown error'}).`,
    (0, b.jsx)('button', { key: 'r', onClick: () => void bz.refetch(), className: 'ml-3 rounded-md bg-accent/20 px-3 py-1 text-accent hover:bg-accent/30', children: 'Retry' })], 'error');

  const over = tick ? `${tick} above` : 'at';
  const under = tick ? `${tick} below` : 'at';
  const inTitle = instaIn
    ? 'Inputs: insta-buy at the lowest sell offer. Rate = how fast that book is insta-bought (~ how fast new sell offers appear).'
    : `Inputs: buy order ${over} the top buy order (Settings → “Outbid / undercut by 0.1 coins”). Rate = how fast players insta-sell that book (fills your buy orders).`;
  const outTitle = instaOut
    ? 'Output: insta-sell into the top buy order. Rate = how fast that book is insta-sold (~ how fast new buy orders appear).'
    : `Output: sell offer ${under} the lowest sell offer (Settings → “Outbid / undercut by 0.1 coins”). Rate = how fast players insta-buy that book (fills your sell offers).`;

  return (0, b.jsxs)('div', { className: 'mx-auto max-w-[1500px] px-4 py-4', children: [
    (0, b.jsxs)('div', { className: 'mb-3 flex flex-wrap items-center gap-x-4 gap-y-2', children: [
      (0, b.jsx)(BA_BazaarSubTabs, { sub: 'books', base: watch ? st.watchlist : st.market }),
      (0, b.jsx)('h1', { className: 'text-lg font-semibold', children: watch ? 'Watchlist' : 'Book flips' }),
      (0, b.jsx)('span', { className: 'num text-sm text-mute', children: `${list.length.toLocaleString()} of ${all.length.toLocaleString()} flips` }),
      (0, b.jsxs)('div', { className: 'ml-auto flex flex-wrap items-center gap-2 text-sm', children: [
        (0, b.jsx)(BA_BookSeg, { label: 'Inputs', title: inTitle, value: instaIn, onChange: setInstaIn, options: [[false, 'Buy order'], [true, 'Insta-buy']] }),
        (0, b.jsx)(BA_BookSeg, { label: 'Output', title: outTitle, value: instaOut, onChange: setInstaOut, options: [[false, 'Sell order'], [true, 'Insta-sell']] }),
        (0, b.jsx)('input', { value: search, onChange: (e) => setSearch(e.target.value), placeholder: 'Search enchant…', 'aria-label': 'Search enchant',
          className: 'ba-bf-search rounded-md border border-line bg-panel px-2 py-1 text-ink placeholder:text-mute/60 focus:border-accent focus:outline-none' }),
        (0, b.jsx)(BA_BookField, { label: 'Min coins/hr', title: 'e.g. 500k, 5m', value: minCoins, onChange: setMinCoins, bad: fc.bad }),
        (0, b.jsx)(BA_BookField, { label: 'Min margin %', value: minMargin, onChange: setMinMargin, bad: fm.bad }),
        (0, b.jsx)(BA_BookField, { label: 'Min profit', title: 'Per flip, e.g. 100k', value: minProfit, onChange: setMinProfit, bad: fp.bad }),
        (0, b.jsxs)('label', { className: 'flex cursor-pointer items-center gap-2 text-mute', children: [
          (0, b.jsx)('input', { type: 'checkbox', checked: hideLoss, onChange: (e) => setHideLoss(e.target.checked), className: 'h-4 w-4 accent-[var(--color-accent)]' }),
          'Hide unprofitable'] }),
        (0, b.jsxs)('label', { className: 'flex cursor-pointer items-center gap-2 text-mute', title: BA_bookWideTip, children: [
          (0, b.jsx)('input', { type: 'checkbox', checked: hideWide, onChange: (e) => setHideWide(e.target.checked), className: 'h-4 w-4 accent-[var(--color-accent)]' }),
          'Hide ⚠ wide spreads'] }),
      ] }),
    ] }),
    (0, b.jsxs)('div', { className: 'mb-3 text-xs text-mute', children: [
      'Buy the lower book, combine pairs in an anvil up to the highest level the anvil can make (XP levels only, no coins), sell the result. ',
      'Only that top level is the target. Combinable = ultimate enchants, enchanting-table enchants up to one level over the table max (never above V), ',
      'and books whose higher levels have no source but the Bazaar (Feast, Ice Cold, the Turbo books, …) up to the last such level. ',
      `Sale price after ${(tax * 100).toFixed(3).replace(/\.?0+$/, '')}% Bazaar tax; rates are 7-day averages.`] }),
    (0, b.jsx)('div', { ref: scrollRef, style: virt.boxStyle, className: 'rounded-xl border border-line bg-panel', children:
      (0, b.jsxs)('div', { className: 'min-w-[1224px]', children: [
        (0, b.jsxs)('div', { className: 'sticky top-0 z-10 grid items-center border-b border-line bg-panel2 text-xs font-medium text-mute',
          style: { gridTemplateColumns: BA_bookGrid, height: 38, top: virt.stickyTop }, children: [
            (0, b.jsx)('div', { key: 'star' }),
            head('name', 'Enchant', 'Target book', true),
            head('from', 'Route', 'Books needed × source level → target level (anvil: XP levels only, no coins). Sorts by source level.', true),
            ...BA_bookCols.map((c) => head(c.key, c.label, c.title)),
            head('limit', 'Limited by', 'The slower side of the flip: input books or the output book'),
          ] }),
        list.length === 0
          ? (0, b.jsx)('div', { className: 'px-4 py-12 text-center text-sm text-mute', children: watch && !starred.length
            ? 'Your Book Flips watchlist is empty. Click the ☆ next to any flip on the Book Flips tab to add it.'
            : all.length
            ? 'Nothing matches these filters.' + (hideLoss ? ' Unprofitable flips are hidden (untick “Hide unprofitable” to see them).' : '')
              + (hideWide ? ' Wide-spread flips are hidden too (untick “Hide ⚠ wide spreads”).' : '')
            : 'No book flips in the current Bazaar data.' })
          : (0, b.jsx)('div', { style: { height: virt.getTotalSize(), position: 'relative' }, children:
            virt.getVirtualItems().map((v) => {
              const r = list[v.index];
              return (0, b.jsx)(BA_BookRow, { r, instaIn, instaOut, tick, top: v.start, on: starred.includes(r.key), toggleStar }, r.key);
            }) }),
      ] }) }),
  ] });
}

var BA_BOOK_ROW_H = 46;

function BA_BookRow({ r, instaIn, instaOut, tick, top, on, toggleStar }) {
  const cell = (children, cls = '', title) => (0, b.jsx)('div', { className: `pr-3 text-right ${cls}`, title, children });
  const two = (top, sub, cls, title) => (0, b.jsxs)('div', { className: `pr-3 text-right ${cls}`, title, children: [
    (0, b.jsx)('div', { children: top }), (0, b.jsx)('div', { className: 'whitespace-nowrap text-[11px] text-mute', children: sub })] });
  const warn = BA_bookWide(r);
  return (0, b.jsxs)('div', {
    onClick: () => ft(st.item(r.outId)), role: 'link', tabIndex: 0,
    onKeyDown: (e) => { if (e.key === 'Enter') ft(st.item(r.outId)); },
    className: 'num absolute left-0 grid w-full cursor-pointer items-center overflow-hidden border-b border-line/60 text-[13px] hover:bg-white/[0.04]',
    style: { gridTemplateColumns: BA_bookGrid, height: BA_BOOK_ROW_H, transform: `translateY(${top}px)` }, children: [
      (0, b.jsx)('button', { type: 'button', onClick: (e) => { e.stopPropagation(); toggleStar(r.key); }, onKeyDown: (e) => e.stopPropagation(),
        'aria-label': on ? 'Remove from watchlist' : 'Add to watchlist',
        className: `mx-auto flex h-7 w-7 items-center justify-center rounded-md hover:bg-white/10 ${on ? 'text-buy' : 'text-mute/60'}`,
        children: (0, b.jsx)(zr, { name: 'star', size: 16, filled: on }) }),
      (0, b.jsxs)('div', { className: 'flex min-w-0 items-center gap-2.5 pr-2', children: [
        (0, b.jsx)(Jr, { id: r.outId, info: r.info, size: 26 }),
        (0, b.jsxs)('div', { className: 'min-w-0', children: [
          (0, b.jsx)('div', { className: 'truncate font-sans text-sm text-ink', children: r.label }),
          (0, b.jsx)('div', { className: 'truncate font-sans text-[11px] text-mute', children: r.outId })] })] }),
      (0, b.jsx)('div', { className: 'pl-2.5 text-mute', title: `${r.books} × ${r.srcId} → ${r.outId} (anvil: XP levels only, no coins)`, children: `${r.books}x ${jt(r.from)} → ${jt(r.target)}` }),
      cell(tn(r.books, 0)),
      two((0, b.jsx)('span', { className: instaIn ? 'text-buy' : 'text-sell', children: en(r.cost) }), `${tn(r.books, 0)} × ${nn(r.inPrice)}`, '',
        instaIn ? 'Insta-buy (lowest sell offer)' : `Buy order (top buy order${tick ? ' + ' + tick : ''})`),
      two((0, b.jsx)('span', { className: instaOut ? 'text-sell' : 'text-buy', children: en(r.outPrice) }), `after tax ${nn(r.net)}`, '',
        instaOut ? 'Insta-sell (top buy order)' : `Sell offer (lowest sell offer${tick ? ' − ' + tick : ''})`),
      cell(en(r.profit), BA_bookSign(r.profit)),
      warn
        ? cell(`⚠ ${rn(r.pct, 1)}`, 'text-buy', BA_bookWideTip)
        : cell(rn(r.pct, 1), BA_bookSign(r.pct)),
      two(r.flips >= 10 ? tn(r.flips, 0) : r.flips.toFixed(2), `in ${tn(r.inRate / r.books, 1)} · out ${tn(r.outRate, 1)}`, '',
        `Input books: ${tn(r.inRate, 1)}/hr → ${tn(r.inRate / r.books, 2)} flips/hr; output book: ${tn(r.outRate, 1)}/hr`),
      warn
        ? cell(`⚠ ${en(r.coinsHr)}`, 'text-buy', BA_bookWideTip)
        : cell(en(r.coinsHr), BA_bookSign(r.coinsHr)),
      cell(r.limit, 'text-mute', r.limit === 'input' ? 'Not enough input books trade per hour' : 'Not enough target books trade per hour'),
    ] });
}
