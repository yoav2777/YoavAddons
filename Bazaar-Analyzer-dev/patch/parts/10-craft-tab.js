// ===== Craft price UI: AH item page tabs (parts/10-craft-tab.js) =====
// Mounted by patch_v12.mjs in the AH item page gd (replaces the price chart's slot) and in the chart id
// (BA_CraftChartLine). Needs parts/00-shared.js.

// Clean craft cost of an AH tag. tier only matters for PET_* tags. A recompute for the same key (new bazaar
// prices) keeps showing the last result with loading false; loading is only true until the first result.
function BA_useCleanCraft(tag, tier) {
  let ctx = useCraftCtx();
  let [st, setSt] = (0, y.useState)({ res: null, err: null, loading: !0, at: 0, key: `` });
  let key = `${tag}|${tier ?? ``}`;
  (0, y.useEffect)(() => {
    if (!ctx) return;
    let ac = new AbortController();
    setSt((s) => (s.key === key ? s : { res: null, err: null, loading: !0, at: 0, key }));
    BA_craft.cleanCraftCost(tag, ctx, { signal: ac.signal, tier }).then(
      (res) => { if (!ac.signal.aborted) setSt({ res, err: null, loading: !1, at: Date.now(), key, ctx }); },
      (err) => { if (err?.name !== `AbortError`) setSt({ res: null, err, loading: !1, at: Date.now(), key, ctx }); },
    );
    return () => ac.abort();
  }, [key, ctx]);
  return st.key === key ? st : { res: null, err: null, loading: !0, at: 0, key };
}

// Recent average sale (volume-weighted, last 3 days) from the chart's 1M history query: all sales of the tag
// (enchanted/upgraded ones too), for pets only the given tier (Rarity param, like the page's Rarity filter).
// Its own request in practice (the chart uses the page's filters and the selected range, default 1D), so it is
// cached for 15 min: a 3-day average barely moves, and revisits / pet tier switches then cost no Coflnet call.
function BA_useAvgSale(tag, tier) {
  let params = tier && tag.startsWith(`PET_`) ? { Rarity: tier } : {};
  let q = at({
    queryKey: [`ah-hist`, tag, `1M`, JSON.stringify(params)],
    queryFn: ({ signal: e }) => wi(tag, `1M`, params, e),
    staleTime: 9e5,
    gcTime: 18e5,
    retry: 1,
  });
  return (0, y.useMemo)(() => {
    let rows = Array.isArray(q.data) ? q.data : [];
    if (!rows.length) return null;
    let ts = rows.map((r) => ha(r.time)), last = Math.max(...ts), v = 0, s = 0;
    rows.forEach((r, i) => {
      if (ts[i] >= last - 3 * 864e5 && r.volume > 0 && Number.isFinite(r.avg)) { v += r.volume; s += r.avg * r.volume; }
    });
    return v ? s / v : null;
  }, [q.data]);
}

function BA_craftLbinOf(ctx, tag, tier) {
  let lb = ctx?.lowestBin;
  if (!lb?.get) return null;
  let v = tag.startsWith(`PET_`) && tier ? lb.get(`PET-${tag.slice(4)}-${tier}`) : lb.get(tag);
  return Number.isFinite(v) && v > 0 ? v : null;
}

// Pet pages: the craft (Kat) price depends on the tier. Rarity filter wins, then the page tier, then LEGENDARY.
function BA_craftPetTier(tag, tier, filters, chosen) {
  if (!tag.startsWith(`PET_`)) return void 0;
  let f = filters?.find?.((x) => x.kind === `rarity` && BA_craftTiers.includes(x.value))?.value;
  return chosen ?? f ?? (BA_craftTiers.includes(tier) ? tier : `LEGENDARY`);
}

// from-scratch craft price per tag, for the chart line (tiny store: BA_CraftStat writes, BA_CraftChartLine reads)
const BA_craftLine = { v: new Map(), subs: new Set(), shown: new Set() };
function BA_craftLineSet(tag, v) {
  if (BA_craftLine.v.get(tag) === v) return;
  BA_craftLine.v.set(tag, v);
  BA_craftLine.subs.forEach((f) => f());
}
function BA_craftLineOn() { return BA_lsGet(`ba.craftLine`) !== `0`; }

// The item page tab bar. The price chart stays mounted (only hidden) so switching back doesn't reload it.
function BA_AhTabs({ tag, name, tier, filters, params, paramsKey }) {
  let [tab, setTab] = (0, y.useState)(() => (BA_lsGet(`ba.ahTab`) === `craft` ? `craft` : `graph`));
  let [petTier, setPetTier] = (0, y.useState)(null);
  let t = BA_craftPetTier(tag, tier, filters, petTier);
  // the chart is mounted the first time its tab is shown (klinecharts measures its size on load; a hidden box is 0 wide)
  let [graphSeen, setGraphSeen] = (0, y.useState)(tab === `graph`);
  let pick = (v) => { setTab(v); if (v === `graph`) setGraphSeen(!0); BA_lsSet(`ba.ahTab`, v); };
  // back on the graph: a chart that (re)loaded while hidden was sized at 0 wide; BA_CraftChartLine re-fits it
  (0, y.useEffect)(() => { if (tab === `graph`) BA_craftLine.shown.forEach((f) => f()); }, [tab]);
  let tabBtn = (v, label) => BA_el(`button`, { key: v, type: `button`, role: `tab`, 'aria-selected': tab === v, className: tab === v ? `on` : ``, onClick: () => pick(v) }, label);
  return BA_el(`div`, null,
    BA_el(`div`, { className: `ba-cr-bar` },
      BA_el(`div`, { className: `ba-cr-tabs`, role: `tablist` }, tabBtn(`graph`, `Price graph`), tabBtn(`craft`, `Craft price`)),
      t ? BA_el(`label`, { className: `flex items-center gap-2 text-xs text-mute` }, `Pet tier`,
        BA_el(`select`, { className: `ba-cr-sel`, value: t, onChange: (e) => setPetTier(e.target.value) },
          ...BA_craftTiers.map((x) => BA_el(`option`, { key: x, value: x }, Mt(x))))) : null,
      BA_el(BA_CraftBoundary, { label: `Craft price`, inline: !0, resetKey: `${tag}|${t}` },
        BA_el(BA_CraftStat, { tag, tier: t, baseTier: tier, filters, onOpen: () => pick(`craft`) }))),
    BA_el(`div`, { style: tab === `graph` ? void 0 : { display: `none` } },
      graphSeen ? BA_el(id, { tag, params, paramsKey }) : null),
    tab === `craft`
      ? BA_el(BA_CraftBoundary, { label: `Craft price`, resetKey: `${tag}|${t}` }, BA_el(BA_CraftPanel, { tag, name, tier: t, baseTier: tier, filters }))
      : null);
}

// Totals shown by the stat / Craft tab: clean craft, or clean craft + the selected filters (BA_useFilterCraft).
function BA_craftShown(c) {
  let r = c.res, m = c.mods, withMods = !!c.syn?.item;
  let tot = (mode) => (withMods ? BA_CraftLstTotal(m, mode, 1) : r?.[mode]?.total ?? null);
  return {
    withMods, fs: tot(`fromScratch`), ez: tot(`easy`),
    // the clean result decides "craftable"; the modifiers only have to finish loading when there is a recipe
    loading: (c.loading && !r) || (withMods && !!r?.craftable && c.modsLoading && !c.modsErr),
    err: c.err ?? (r?.craftable ? c.modsErr : null),
  };
}

// Compact "Craft price (clean)" stat in the tab bar; hover = two-list breakdown. With modifier filters selected
// (e.g. Wither Impact on a Hyperion) it is "Craft price (+ filters)" = clean craft + those modifiers.
function BA_CraftStat({ tag, tier, baseTier, filters, onOpen }) {
  let c = BA_useFilterCraft(tag, tier, filters, baseTier);
  let r = c.res, sh = BA_craftShown(c), fs = sh.fs, ez = sh.ez;
  let lbin = BA_craftLbinOf(c.ctx, tag, tier);
  let petBase = tag.startsWith(`PET_`) ? tag.slice(4) : void 0;
  (0, y.useEffect)(() => { if (!sh.loading) BA_craftLineSet(tag, fs); }, [tag, fs, sh.loading]);
  (0, y.useEffect)(() => () => BA_craftLineSet(tag, null), [tag]);
  let [line, setLine] = (0, y.useState)(BA_craftLineOn);
  let lbl = BA_el(`span`, { className: `lbl`, title: sh.withMods ? `Clean craft + the modifiers picked in the filters` : void 0 },
    sh.withMods ? `Craft price (+ filters)` : `Craft price (clean)`);
  let skip = BA_el(BA_CraftSkipNote, { skipped: c.syn?.skipped });
  if (sh.loading) return BA_el(`div`, { className: `ba-cr-stat` }, lbl,
    BA_el(`span`, { className: `ba-cr-skel`, style: { width: 180 } }));
  if (sh.err) return BA_el(`div`, { className: `ba-cr-stat` }, lbl, BA_el(`span`, { className: `text-down` }, sh.err.message ?? `failed`));
  if (!r?.craftable) return BA_el(`div`, { className: `ba-cr-stat` }, lbl,
    BA_el(`span`, { className: `text-mute` }, `not craftable (no recipe)`));
  let diff = (base, lblTxt) => {
    if (fs == null || base == null) return null;
    let d = fs - base;
    return BA_el(`span`, { className: `text-mute` }, `vs ${lblTxt} `,
      BA_el(`span`, { className: `ba-cr-num ${d <= 0 ? `text-up` : `text-down`}` }, `${d > 0 ? `+` : ``}${nn(d)} (${d > 0 ? `+` : ``}${rn(d / base, 1)})`));
  };
  return BA_el(`div`, { className: `ba-cr-stat` },
    lbl,
    BA_el(BA_CraftHover, {
      content: () => sh.withMods
        ? BA_el(BA_CraftFilterBreakdown, { res: c.mods, petBase, skipped: c.syn.skipped, title: `Craft price breakdown · clean + selected filters` })
        : BA_el(`div`, null,
          // same header as the listing popover (20-listing-craft.js)
          BA_el(`div`, { className: `mb-1.5 text-[11px] font-semibold uppercase text-accent` }, `Clean craft price breakdown`),
          BA_el(BA_CraftTwoLists, { fromScratch: r.fromScratch, easy: r.easy, kind: `tree`, petBase,
          footer: `Clean item, no enchants/modifiers. From scratch = buy orders, cheapest of buy/craft/forge/NPC at every step. Easy = insta-buy + lowest BIN, no sub-crafting. Full tree: Craft price tab.` })),
    },
    BA_el(`span`, null, `From scratch `, BA_el(`b`, { className: `ba-cr-num`, style: { color: `#38bdf8` } }, fs == null ? `can't be priced` : BA_craftFmt(fs))),
    BA_el(`span`, { className: `text-mute` }, ` · `),
    BA_el(`span`, null, `Easy `, BA_el(`b`, { className: `ba-cr-num`, style: { color: `#fbbf24` } }, ez == null ? `can't be priced` : BA_craftFmt(ez)))),
    // the lowest BIN here is the clean item's: comparing it with a modded craft price would say nothing
    sh.withMods ? null : diff(lbin, `lowest BIN (no filters)`),
    skip,
    fs != null ? BA_el(`label`, { className: `flex items-center gap-1 text-xs text-mute`, title: `Dashed line on the price graph at the from-scratch craft price` },
      BA_el(`input`, { type: `checkbox`, checked: line, onChange: (e) => { setLine(e.target.checked); BA_lsSet(`ba.craftLine`, e.target.checked ? `1` : `0`); BA_craftLine.subs.forEach((f) => f()); } }),
      `line`) : null,
    BA_el(`button`, { type: `button`, className: `ba-cr-link`, onClick: onOpen }, `details →`));
}

// Dashed horizontal "Craft" line on the AH price chart (own overlay group, locked; the user's drawings use group `user`).
// fit = the chart's own "Fit" callback (id's ie). It skips a 0-wide chart, which is what a load behind the hidden
// graph tab gets; when the tab is shown again (BA_craftLine.shown) such a chart is resized and fitted.
function BA_CraftChartLine({ tag, chart, ready, fit }) {
  let [, bump] = (0, y.useState)(0);
  let needFit = (0, y.useRef)(!1);
  (0, y.useEffect)(() => {
    if (chart && ready === `ready` && !chart.getSize?.(Ku, `main`)?.width) needFit.current = !0;
  }, [chart, ready]);
  (0, y.useEffect)(() => {
    let raf = 0;
    let f = () => {
      if (!needFit.current || !chart) return;
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(() => {
        try {
          chart.resize();
          if (chart.getSize?.(Ku, `main`)?.width) { needFit.current = !1; fit?.(); }
        } catch (e) { console.warn(`[craft] chart refit`, e); }
      });
    };
    BA_craftLine.shown.add(f);
    return () => { BA_craftLine.shown.delete(f); cancelAnimationFrame(raf); };
  }, [chart, fit]);
  (0, y.useEffect)(() => {
    let f = () => bump((n) => n + 1);
    BA_craftLine.subs.add(f);
    return () => { BA_craftLine.subs.delete(f); };
  }, []);
  let v = BA_craftLineOn() ? BA_craftLine.v.get(tag) ?? null : null;
  (0, y.useEffect)(() => {
    if (!chart || ready !== `ready`) return;
    try {
      chart.removeOverlay({ groupId: `ba-craft` });
      if (v != null && Number.isFinite(v) && v > 0)
        chart.createOverlay({
          name: `simpleTag`, groupId: `ba-craft`, lock: !0, points: [{ value: v }], extendData: `Craft ${nn(v)}`,
          styles: {
            line: { color: `#d97757`, size: 1, style: `dashed`, dashedValue: [6, 4] },
            text: { color: `#1a1918`, backgroundColor: `#d97757`, borderColor: `#d97757`, family: BA_font, size: 11 },
          },
        });
    } catch (e) { console.warn(`[craft] chart line`, e); }
  }, [chart, ready, v]);
  (0, y.useEffect)(() => () => { try { chart?.removeOverlay({ groupId: `ba-craft` }); } catch {} }, [chart]);
  return null;
}

// ---- Craft price tab ----
function BA_CraftPanel({ tag, name, tier, baseTier, filters }) {
  let qc = S(), c = BA_useFilterCraft(tag, tier, filters, baseTier), avg = BA_useAvgSale(tag, tier), now = Qt(1e4);
  let r = c.res, petBase = tag.startsWith(`PET_`) ? tag.slice(4) : void 0;
  let lbin = BA_craftLbinOf(c.ctx, tag, tier);
  let sh = BA_craftShown(c), fs = sh.fs, ez = sh.ez, m = sh.withMods ? c.mods : null;
  let [busy, setBusy] = (0, y.useState)(!1);
  let refresh = async () => { setBusy(!0); try { await BA_craftRefresh(qc); } finally { setBusy(!1); } };
  let head = BA_el(`div`, { className: `flex flex-wrap items-center gap-3` },
    BA_el(`h2`, { className: `text-sm font-semibold` }, `Craft price · ${name}${tier ? ` (${Mt(tier)})` : ``}${sh.withMods ? ` + selected filters` : ``}`),
    BA_el(`span`, { className: `ml-auto text-xs text-mute` },
      c.loading ? `Loading prices…` : c.at ? `Updated ${on(now - c.at)}` : ``),
    BA_el(`button`, { type: `button`, className: `ba-cr-btn`, onClick: refresh, disabled: busy || c.loading }, busy ? `Refreshing…` : `Refresh prices`));
  let box = (...kids) => BA_el(`section`, { className: `rounded-xl border border-line bg-panel p-4` }, head, BA_el(`div`, { className: `mt-3` }, ...kids));

  if (sh.loading) return box(
    BA_el(`div`, { className: `ba-cr-stats` }, ...[0, 1, 2, 3].map((i) => BA_el(`div`, { key: i, className: `rounded-lg border border-line bg-panel2 px-3 py-2` },
      BA_el(`span`, { className: `ba-cr-skel`, style: { width: `60%` } }), BA_el(`span`, { className: `ba-cr-skel mt-2`, style: { width: `80%`, height: 18 } })))),
    BA_el(`div`, { className: `ba-cr-grid2 mt-4` }, ...[0, 1].map((i) => BA_el(`div`, { key: i },
      ...[0, 1, 2, 3, 4, 5].map((j) => BA_el(`span`, { key: j, className: `ba-cr-skel mt-2`, style: { width: `${90 - j * 8}%` } }))))));
  if (sh.err) return box(BA_el(`p`, { className: `text-sm text-down` }, `Couldn't compute the craft price: ${sh.err.message ?? sh.err}`));
  if (!r?.craftable) return box(
    BA_el(`p`, { className: `text-sm` }, `${name} has no crafting recipe (it drops, or comes from a shop/NPC not in the recipe data), so there is no craft price. Use the lowest BIN and the price graph instead.`),
    lbin != null ? BA_el(`p`, { className: `mt-2 text-sm text-mute` }, `Lowest BIN: `, BA_el(`span`, { className: `ba-cr-num text-ink` }, BA_craftFmt(lbin))) : null,
    BA_el(BA_CraftWarnings, { res: r, ctx: c.ctx }),
    BA_el(BA_CraftSkipNote, { skipped: c.syn?.skipped, className: `mt-2 block text-xs text-mute` }));

  let stat = (label, value, color, sub) => BA_el(`div`, { key: label, className: `rounded-lg border border-line bg-panel2 px-3 py-2` },
    BA_el(`div`, { className: `text-xs text-mute` }, label),
    BA_el(`div`, { className: `ba-cr-num text-lg font-semibold`, style: { color } }, value == null ? `—` : BA_craftFmt(value)),
    sub ? BA_el(`div`, { className: `text-xs text-mute` }, sub) : null);
  let vs = (base) => {
    if (fs == null || base == null) return null;
    let d = fs - base;
    return BA_el(`span`, { className: d <= 0 ? `text-up` : `text-down` }, `craft ${d > 0 ? `+` : ``}${nn(d)} (${d > 0 ? `+` : ``}${rn(d / base, 1)}) vs this`);
  };
  // with filters: "clean X + filters Y" under each total
  let split = (mode, dflt) => {
    let t = mode === `fromScratch` ? fs : ez, cl = r?.[mode]?.total;
    if (t == null) return `can't be priced`;
    return m && cl != null ? `clean ${BA_craftFmt(cl)} + filters ${BA_craftFmt(t - cl)}` : dflt;
  };
  let modLines = (mode) => m?.modifiers?.[mode] ?? [];
  return box(
    BA_el(`div`, { className: `ba-cr-stats` },
      stat(`From scratch (cheapest)`, fs, `#38bdf8`, split(`fromScratch`, `buy orders + best route`)),
      stat(`Easy way`, ez, `#fbbf24`, split(`easy`, `insta-buy + lowest BIN`)),
      stat(`Lowest BIN (no filters)`, lbin, void 0, m ? `clean item, not compared` : vs(lbin)),
      stat(tier ? `Avg sale 3d (all ${Mt(tier)} sales)` : `Avg sale 3d (all sales)`, avg, void 0, tier ? `this tier only, incl. upgraded pets` : `incl. upgraded items, no filters`)),
    BA_el(BA_CraftWarnings, { res: r, ctx: c.ctx, more: m?.missing?.filter((x) => !(r?.missing ?? []).includes(x)) }),
    m ? BA_el(`section`, { className: `mt-4 rounded-lg border border-line bg-bg px-3 py-2` },
      BA_el(`div`, { className: `mb-1 flex flex-wrap items-baseline gap-2` },
        BA_el(`span`, { className: `text-sm font-semibold` }, `Selected filters`),
        BA_el(`span`, { className: `text-xs text-mute` }, `added on top of the clean item, at the lowest level / amount the filters allow`)),
      BA_el(BA_CraftTwoLists, { fromScratch: modLines(`fromScratch`), easy: modLines(`easy`), kind: `lines`, petBase, maxRows: 40,
        footer: c.syn?.skipped?.length ? `Not in the craft price: ${c.syn.skipped.map((s) => `${s.label} (${s.why})`).join(`, `)}.` : void 0 })) : null,
    !m ? BA_el(BA_CraftSkipNote, { skipped: c.syn?.skipped, className: `mt-2 block text-xs text-mute` }) : null,
    BA_el(`div`, { className: `ba-cr-grid2 mt-4` },
      BA_el(BA_CraftTree, { key: `fs`, tree: r.fromScratch, title: m ? `Clean item: from scratch (cheapest)` : `From scratch (cheapest)`, sub: `Buy orders, and at every step the cheapest of buy order / lowest BIN / craft / forge / NPC.`, color: `#38bdf8`, petBase }),
      BA_el(BA_CraftTree, { key: `ez`, tree: r.easy, title: m ? `Clean item: easy way (insta-buy + lowest BIN)` : `Easy way (insta-buy + lowest BIN)`, sub: `Insta-buy every bazaar ingredient and buy the lowest BIN of every AH ingredient. No sub-crafting.`, color: `#fbbf24`, petBase })),
    BA_el(`p`, { className: `mt-3 text-xs text-mute` },
      m ? `Craft price = the clean item (trees below) + the modifiers picked in the filters (enchant books, scrolls, stars, gems, reforge stone...). Bazaar prices are the top of the book (large amounts can cost more). Recipes: NEU repo.`
        : `Clean item price: no enchants, stars, reforges or other upgrades. Bazaar prices are the top of the book (large amounts can cost more). Recipes: NEU repo.`));
}

function BA_CraftWarnings({ res, ctx, more }) {
  let info = Oa(), out = [];
  let miss = [...(res?.missing ?? []), ...(more ?? [])];
  if (miss.length) out.push(`No price for: ${miss.slice(0, 8).map((x) => (/^[A-Z0-9_;:-]+$/.test(x) ? BA_craftName(x, info) : x)).join(`, `)}${miss.length > 8 ? ` +${miss.length - 8} more` : ``}. Those rows show "n/a"; a total that depends on them can't be priced.`);
  if (res?.truncated) out.push(`The recipe tree was too big and was cut off; deep ingredients may be missing.`);
  if (ctx?.lowestBinFailed) out.push(`The lowest-BIN list couldn't be loaded, so AH ingredients may be missing.`);
  for (let e of res?.errors ?? []) out.push(e);
  if (!out.length) return null;
  return BA_el(`div`, { className: `mt-3 rounded-lg border border-buy/30 bg-buy/10 px-3 py-2 text-xs text-buy` },
    ...out.slice(0, 5).map((m, i) => BA_el(`div`, { key: i }, m)));
}

// Collapsible tree: root's ingredients are the top rows; click a crafted row to open its recipe.
function BA_CraftTree({ tree, title, sub, color, petBase }) {
  let [open, setOpen] = (0, y.useState)(() => new Set());
  let info = Oa();

  let rows = [];
  let walk = (n, depth, path) => {
    let kids = Array.isArray(n.children) && n.children.length ? n.children : null;

    rows.push({ n, depth, path, kids: !!kids });
    if (kids && open.has(path)) kids.forEach((k, i) => walk(k, depth + 1, `${path}.${i}`));
  };
  (tree?.children ?? []).forEach((k, i) => walk(k, 0, `${i}`));
  let allPaths = () => {
    let out = [];
    let w = (n, p) => { if (n.children?.length) { out.push(p); n.children.forEach((k, i) => w(k, `${p}.${i}`)); } };
    (tree?.children ?? []).forEach((k, i) => w(k, `${i}`));
    return out;
  };
  let toggle = (p) => setOpen((s) => { let x = new Set(s); x.has(p) ? x.delete(p) : x.add(p); return x; });
  let total = tree?.total ?? null;
  return BA_el(`div`, { className: `rounded-lg border border-line bg-bg`, style: { minWidth: 0 } },
    BA_el(`div`, { className: `flex flex-wrap items-baseline gap-2 px-3 py-2` },
      BA_el(`span`, { className: `text-sm font-semibold` }, title),
      BA_el(`span`, { className: `ml-auto ba-cr-num text-base font-semibold`, style: { color: total == null ? `#ef4444` : color } },
        total == null ? `can't be priced` : BA_craftFmt(total))),
    BA_el(`div`, { className: `flex flex-wrap items-center gap-3 px-3 text-xs text-mute`, style: { paddingBottom: 8 } },
      BA_el(`span`, { style: { flex: `1 1 200px` } }, sub),
      tree?.method ? BA_el(`span`, null, `Final step: `, BA_el(BA_CraftMethodBadge, { method: tree.method, note: tree.note })) : null,
      BA_el(`button`, { type: `button`, className: `ba-cr-link`, onClick: () => setOpen(new Set(allPaths())) }, `expand all`),
      BA_el(`button`, { type: `button`, className: `ba-cr-link`, onClick: () => setOpen(new Set()) }, `collapse`)),
    tree?.note ? BA_el(`div`, { className: `px-3 text-xs text-mute`, style: { paddingBottom: 8 } }, tree.note) : null,
    !tree ? BA_el(`div`, { className: `px-3 text-sm text-mute`, style: { paddingBottom: 12 } }, `No data.`) : null,
    tree ? BA_el(`div`, { className: `ba-cr-row head` },
      BA_el(`span`, null, `Ingredient`), BA_el(`span`, { className: `r` }, `Qty`), BA_el(`span`, null, `How`),
      BA_el(`span`, { className: `r unit` }, `Each`), BA_el(`span`, { className: `r` }, `Total`)) : null,
    ...rows.map(({ n, depth, path, kids }) => BA_el(`div`, {
      key: path, className: `ba-cr-row${kids ? ` click` : ``}`, onClick: kids ? () => toggle(path) : void 0,
      ...(kids ? { role: `button`, tabIndex: 0, 'aria-expanded': open.has(path),
        onKeyDown: (e) => { if (e.key === `Enter` || e.key === ` `) { e.preventDefault(); toggle(path); } } } : {}),
      title: n.note || void 0,
    },
    BA_el(`span`, { className: `ba-cr-name`, style: { paddingLeft: depth * 16 } },
      BA_el(`span`, { className: `ba-cr-tog` }, kids ? (open.has(path) ? `▾` : `▸`) : ``),
      BA_el(Jr, { id: BA_craftSiteId(n.id, info, petBase), info: info?.[BA_craftSiteId(n.id, info, petBase)], size: 20 }),
      BA_el(`span`, { className: `t` }, BA_craftName(n.id, info, petBase)),
      n.note ? BA_el(`span`, { className: `ba-cr-note` }, n.note) : null),
    BA_el(`span`, { className: `r ba-cr-num` }, n.method === `coins` ? `` : BA_craftQty(n.qty)),
    BA_el(`span`, null, BA_el(BA_CraftMethodBadge, { method: n.method, note: n.note })),
    BA_el(`span`, { className: `r unit ba-cr-num text-mute` }, n.method === `coins` ? `` : n.unitCost == null ? `n/a` : BA_craftFmt(n.unitCost)),
    BA_el(`span`, { className: `r ba-cr-num`, style: { color: n.total == null ? `#ef4444` : void 0 } }, n.total == null ? `n/a` : BA_craftFmt(n.total)))),
  );
}
