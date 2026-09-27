// ===== Craft price UI: shared pieces (parts/00-shared.js) =====
// Plain JS inlined into the v12 bundle by patch_v12.mjs. Uses bundle ids (notes/bundle-map.md):
// y=React, b=JSX runtime, at=useQuery, S=useQueryClient, _t=bazaar fetch, Oa=items info, Ft/Pt/It/Mt=names,
// Jr=item icon, en/tn/nn/an/rn/on=formatters. Engine: BA_craft (craftCore.js + craftModifiers.js).
// Exports for other parts: BA_el, BA_CraftBoundary, BA_CraftMethodBadge, BA_CraftTwoLists, BA_CraftHover,
// useCraftCtx, BA_craftRefresh, BA_craftName, BA_craftSiteId, BA_craftFmt, BA_craftQty, BA_lsGet, BA_lsSet.

// h(type, props, ...children) -> (0,b.jsx|jsxs); `key` is taken out of props.
function BA_el(type, props, ...kids) {
  let { key, ...p } = props || {};
  kids = kids.filter((k) => k !== undefined);
  if (kids.length === 1) p.children = kids[0];
  else if (kids.length > 1) return (0, b.jsxs)(type, { ...p, children: kids }, key);
  return (0, b.jsx)(type, p, key);
}

function BA_lsGet(k) { try { return localStorage.getItem(k); } catch { return null; } }
function BA_lsSet(k, v) { try { localStorage.setItem(k, v); } catch {} }

// ---- error boundary: a craft failure shows an inline message, never a blank page ----
class BA_CraftBoundary extends y.Component {
  constructor(p) { super(p); this.state = { err: null }; }
  static getDerivedStateFromError(err) { return { err }; }
  componentDidCatch(e) { console.error(`[${this.props.label ?? `craft`}]`, e); }
  componentDidUpdate(prev) { if (this.state.err && prev.resetKey !== this.props.resetKey) this.setState({ err: null }); }
  render() {
    if (!this.state.err) return this.props.children ?? null;
    let msg = `${this.props.label ?? `Craft price`} failed: ${this.state.err?.message ?? `error`}`;
    return this.props.inline
      ? BA_el(`span`, { className: `text-xs text-down`, title: msg }, `craft cost unavailable`)
      : BA_el(`div`, { className: `rounded-lg border border-line bg-panel px-3 py-2 text-sm text-down` }, msg);
  }
}

// ---- shared engine context (one per page load, rebuilt when the bazaar data changes) ----
// Bazaar prices come from the site's own ['bazaar'] query (same key/fn as Xt(), but this observer doesn't poll).
// Site `instaSell` (sell_summary[0]) = top buy order = engine `buyOrder`; site `instaBuy` = engine `instaBuy`.
// Lowest BINs, recipes (data/recipes.json) and Hypixel item data are loaded lazily by the engine and carried
// over to every new ctx, so a bazaar update never re-downloads them.
const BA_craftStore = { ctx: null, prod: undefined, subs: new Set() };
// stamp = the bazaar's lastUpdated: the site polls every 30 s and gets a new products array each time, so the ctx
// (and every craft result) is only rebuilt when Hypixel actually published new prices.
function BA_craftCtxFor(products, stamp = products) {
  let s = BA_craftStore;
  if (s.ctx && s.prod === stamp) return s.ctx;
  let bazaar = products ? new Map(products.map((p) => [p.id, { instaBuy: p.instaBuy, buyOrder: p.instaSell }])) : null;
  s.ctx = s.ctx
    ? { ...s.ctx, bazaar: bazaar ?? s.ctx.bazaar, _memo: null, _pricesP: null, errors: s.ctx.errors.slice() }
    : BA_craft.createCraftContext(bazaar ? { bazaar } : {});
  s.ctx.pricesAt = Date.now();
  s.prod = stamp;
  return s.ctx;
}
// Returns the shared ctx (a NEW object whenever prices change, so it works as an effect dependency),
// or null while the bazaar is still loading. If the bazaar query fails, the engine fetches it itself.
function useCraftCtx() {
  let q = at({ queryKey: [`bazaar`], queryFn: ({ signal: e }) => _t(e), staleTime: 6e4, retry: 2, structuralSharing: !1 });
  let [, bump] = (0, y.useState)(0);
  (0, y.useEffect)(() => {
    let f = () => bump((n) => n + 1);
    BA_craftStore.subs.add(f);
    return () => { BA_craftStore.subs.delete(f); };
  }, []);
  let products = q.data?.products;
  if (products?.length) return BA_craftCtxFor(products, q.data.lastUpdated ?? products);
  if (q.isError) return BA_craftCtxFor(null);
  return null;
}
// "Refresh" button: reload bazaar + lowest BINs, then every useCraftCtx() user re-renders with a new ctx.
function BA_craftRefresh(qc) {
  let s = BA_craftStore;
  if (s.ctx) s.ctx = { ...s.ctx, lowestBin: null, lowestBinFailed: false, _memo: null, _pricesP: null, errors: [], pricesAt: Date.now() };
  s.subs.forEach((f) => f());
  return qc?.invalidateQueries({ queryKey: [`bazaar`] });
}

// ---- ids / names / numbers ----
// Engine ids are NEU style: `X;5` (enchant level or pet tier index), `LOG-2` (damage). The site uses
// ENCHANTMENT_X_5 / PET_X / LOG:2. petBase = `X` of the page's PET_X tag (pets and enchants look alike).
const BA_craftTiers = [`COMMON`, `UNCOMMON`, `RARE`, `EPIC`, `LEGENDARY`, `MYTHIC`];
function BA_craftSiteId(id, info, petBase) {
  id = String(id ?? ``);
  if (id === `SKYBLOCK_COIN`) return `GOLD_NUGGET`;
  let m = /^(.+);(\d+)$/.exec(id);
  if (m) return m[1] === petBase || info?.[`PET_${m[1]}`] ? `PET_${m[1]}` : `ENCHANTMENT_${m[1]}_${m[2]}`;
  m = /^(.+)-(\d+)$/.exec(id);
  return m ? `${m[1]}:${m[2]}` : id;
}
function BA_craftName(id, info, petBase) {
  id = String(id ?? ``);
  if (id === `SKYBLOCK_COIN`) return `Coins`;
  let m = /^(.+);(\d+)$/.exec(id);
  if (m && (m[1] === petBase || info?.[`PET_${m[1]}`])) {
    let t = BA_craftTiers[+m[2]];
    return `${It(Ft(`PET_${m[1]}`, info)).replace(/^Pet /, ``)}${t ? ` (${Mt(t)})` : ``}`;
  }
  let s = BA_craftSiteId(id, info, petBase);
  return It(info?.[s]?.name ?? info?.[id]?.name ?? Pt(s));
}
function BA_craftFmt(v) { return v == null || !Number.isFinite(v) ? `—` : v === 0 ? `0` : Math.abs(v) >= 1e3 && Math.abs(v) < 1e7 ? tn(v, 0) : en(v); }
function BA_craftQty(q) {
  if (q == null || !Number.isFinite(q)) return ``;
  return Number.isInteger(q) ? tn(q, 0) : q < 1 ? tn(q, 3) : tn(q, 2);
}

// ---- method badge ----
// Engine methods: buyOrder | instaBuy | lowestBin | craft | forge | npc | coins | unavailable,
// modifier lines also: `<m>+anvil`, table, noMarket.
const BA_craftMethods = {
  buyOrder: [`Buy order`, `#38bdf8`],
  instaBuy: [`Insta-buy`, `#fbbf24`],
  lowestBin: [`Lowest BIN`, `#c084fc`],
  craft: [`Craft`, `#22c55e`],
  forge: [`Forge`, `#fb923c`],
  npc: [`NPC`, `#9aa3b5`],
  coins: [`Coins`, `#facc15`],
  table: [`Ench. table`, `#67e8f9`],
  noMarket: [`No market`, `#9aa3b5`],
  unavailable: [`Can't buy`, `#ef4444`],
};
function BA_CraftMethodBadge({ method, note }) {
  let m = String(method ?? `unavailable`), anvil = m.endsWith(`+anvil`);
  if (anvil) m = m.slice(0, -6);
  let [label, color] = BA_craftMethods[m] ?? [m, `#9aa3b5`];
  if (m === `craft` && String(note ?? ``).startsWith(`trade`)) label = `Trade`;
  if (m === `craft` && String(note ?? ``).startsWith(`Kat pet upgrade`)) label = `Kat`;
  return BA_el(`span`, { className: `ba-cr-badge`, style: { color, background: `${color}22` }, title: note || void 0 },
    anvil ? `${label} + anvil` : label);
}

// ---- hover popover (position: fixed, so parents with overflow:hidden can't clip it) ----
// <BA_CraftHover content={() => node} className=...>trigger</BA_CraftHover>; content is a function so the
// breakdown is only built while open. Opens on hover and keyboard focus; click/tap toggles it (touch screens);
// Escape closes. The box is as tall as the room on its side and scrolls inside (the mouse can move onto it).
function BA_CraftHover({ content, children, className, as }) {
  let ref = (0, y.useRef)(null), pop = (0, y.useRef)(null), timer = (0, y.useRef)(0), [pos, setPos] = (0, y.useState)(null);
  let pid = (0, y.useId)(), openedAt = (0, y.useRef)(0);
  let open = () => {
    clearTimeout(timer.current);
    if (!pos) openedAt.current = Date.now();
    let r = ref.current?.getBoundingClientRect();
    if (!r) return;
    let w = Math.min(780, window.innerWidth - 16), left = Math.max(8, Math.min(r.left, window.innerWidth - w - 8));
    let below = window.innerHeight - r.bottom > 260 || r.top < window.innerHeight / 2;
    let room = Math.max(120, (below ? window.innerHeight - r.bottom : r.top) - 14);
    setPos((p) => p ?? (below ? { left, top: r.bottom + 6, maxHeight: room } : { left, bottom: window.innerHeight - r.top + 6, maxHeight: room }));
  };
  let shut = () => { clearTimeout(timer.current); setPos(null); };
  let later = () => { clearTimeout(timer.current); timer.current = setTimeout(() => setPos(null), 150); };
  (0, y.useEffect)(() => () => clearTimeout(timer.current), []);
  (0, y.useEffect)(() => {
    if (!pos) return;
    let onScroll = (e) => { if (!(e.target instanceof Node && pop.current?.contains(e.target))) setPos(null); };
    window.addEventListener(`scroll`, onScroll, !0);
    window.addEventListener(`resize`, shut);
    return () => { window.removeEventListener(`scroll`, onScroll, !0); window.removeEventListener(`resize`, shut); };
  }, [pos]);
  return BA_el(as ?? `span`, {
    ref, className: className ?? `ba-cr-help`, tabIndex: 0, 'aria-expanded': !!pos, 'aria-describedby': pos ? pid : void 0,
    onMouseEnter: open, onMouseLeave: later, onFocus: open,
    onBlur: (e) => { if (!ref.current?.contains(e.relatedTarget)) shut(); },
    onKeyDown: (e) => { if (e.key === `Escape` && pos) { e.stopPropagation(); shut(); } },
    onClick: (e) => { if (pop.current?.contains(e.target) || e.target.closest?.(`a,button`)) return; // a tap fires mouseenter + focus + click at once: that click must not close what it just opened
      if (!pos) open(); else if (Date.now() - openedAt.current > 400) shut(); },
  },
  children,
  pos ? BA_el(`div`, { ref: pop, id: pid, role: `tooltip`, tabIndex: -1, className: `ba-cr-pop`, style: { ...pos, overflow: `auto`, pointerEvents: `auto`, cursor: `auto` } },
    BA_el(BA_CraftBoundary, { label: `Breakdown` }, typeof content === `function` ? content() : content)) : null);
}

// ---- two side-by-side lists: "From scratch (cheapest)" / "Easy way (insta-buy + lowest BIN)" ----
// kind 'tree': fromScratch/easy are engine Trees ({id,qty,unitCost,total,method,note,children}); rows = depth 1..maxDepth.
// kind 'lines': fromScratch/easy are arrays of lines ({label?,id,qty,unitCost,total,method,note?}); totals via `totals`
//   ({fromScratch,easy}) or summed. Optional: petBase (see BA_craftSiteId), maxRows (per list), footer (node).
function BA_CraftTwoLists({ fromScratch, easy, kind = `tree`, maxDepth = 2, maxRows = 22, totals, petBase, footer }) {
  let info = Oa();
  let col = (title, sub, data, mode) => {
    let rows, total;
    if (kind === `tree`) {
      total = data?.total ?? null;
      rows = data ? BA_craft.flattenTree(data).filter((n) => n.depth >= 1 && n.depth <= maxDepth) : [];
    } else {
      rows = Array.isArray(data) ? data.map((n) => ({ ...n, depth: 1 })) : [];
      total = totals?.[mode] !== undefined ? totals[mode]
        : rows.some((n) => n.total == null && n.kind !== `unknown`) ? null : rows.reduce((s, n) => s + (n.total ?? 0), 0);
    }
    let shown = rows.slice(0, maxRows);
    return BA_el(`div`, { key: mode, style: { minWidth: 0 } },
      BA_el(`div`, { className: `ba-cr-col-h` },
        BA_el(`span`, null, BA_el(`b`, null, title), BA_el(`span`, { className: `text-mute` }, ` ${sub}`)),
        BA_el(`b`, { className: `ba-cr-num`, style: { color: mode === `fromScratch` ? `#38bdf8` : `#fbbf24` } },
          total == null ? (data ? `can't be priced` : `—`) : BA_craftFmt(total))),
      !data || (!rows.length && kind === `tree`)
        ? BA_el(`div`, { className: `text-mute` }, data ? `No ingredients.` : `No data.`)
        : null,
      ...shown.map((n, i) => BA_el(`div`, { key: i, className: `ba-cr-line`, style: { paddingLeft: (n.depth - 1) * 12 } },
        BA_el(`span`, { className: `nm`, title: n.note || void 0 },
          n.depth > 1 ? BA_el(`span`, { className: `text-mute` }, `└ `) : null,
          n.qty != null && n.qty !== 1 && n.method !== `coins` ? BA_el(`span`, { className: `text-mute ba-cr-num` }, `${BA_craftQty(n.qty)}× `) : null,
          n.label ?? BA_craftName(n.id, info, petBase),
          n.note && kind === `lines` ? BA_el(`span`, { className: `text-mute` }, ` · ${n.note}`) : null),
        BA_el(BA_CraftMethodBadge, { method: n.method, note: n.note }),
        BA_el(`span`, { className: `ba-cr-num`, style: { minWidth: 56, textAlign: `right`, color: n.total == null ? `#ef4444` : void 0 } },
          n.total == null ? (n.kind === `unknown` ? `?` : `n/a`) : BA_craftFmt(n.total)))),
      rows.length > shown.length ? BA_el(`div`, { className: `text-mute` }, `+${rows.length - shown.length} more…`) : null);
  };
  return BA_el(`div`, null,
    BA_el(`div`, { className: `ba-cr-two` },
      col(`From scratch`, `(cheapest)`, fromScratch, `fromScratch`),
      col(`Easy way`, `(insta-buy + lowest BIN)`, easy, `easy`)),
    footer ? BA_el(`div`, { className: `mt-2 text-mute`, style: { fontSize: 11 } }, footer) : null);
}
