/* ---- 80-search.js -------------------------------------------------------------------------
 * BA_TopSearch: the header search ($r) now finds Bazaar items, AH item types and players in one box.
 * It still writes the shared query (qt), which filters the Market list and (patched) the AH list.
 * BA_ActSearch: one search box per Activity tab (Trades / Lowballing / AH & Bazaar), items or players.
 * Mounted by patch/patch_v12.mjs ("Search: ...").
 * ------------------------------------------------------------------------------------------ */

function BA_TopSearch() {
  let q = qt((s) => s.query),
    setQ = qt((s) => s.setQuery),
    { rows, byId } = $t(),
    ah = ka().data,
    [open, setOpen] = (0, y.useState)(!1),
    [idx, setIdx] = (0, y.useState)(-1), // -1 = nothing marked: Enter just closes the list, Tab/arrows mark a result
    ref = (0, y.useRef)(null),
    dq = hd(q, 300),
    players = at({
      queryKey: [`ah-player-search`, dq.trim().toLowerCase()],
      queryFn: ({ signal }) => BA_searchPlayers(dq, signal),
      enabled: dq.trim().length >= 2,
      staleTime: 6e4,
      retry: 1,
    });
  (0, y.useEffect)(() => {
    let k = (e) => {
      (e.ctrlKey || e.metaKey) && e.key.toLowerCase() === `k` && (e.preventDefault(), ref.current?.focus(), ref.current?.select());
    };
    return window.addEventListener(`keydown`, k), () => window.removeEventListener(`keydown`, k);
  }, []);
  // AH-only item types, kept as one array so the fuzzy index Fr caches per array is built once, not per keystroke
  let ahOnly = (0, y.useMemo)(() => (ah ?? []).filter((r) => !byId.has(r.tag)), [ah, byId]);
  let list = (0, y.useMemo)(() => {
    if (!q.trim()) return [];
    let bz = Ir(rows, q, 6),
      bzl = bz.items.map((r) => ({ kind: `bz`, key: r.id, r })),
      ahl = Fr(ahOnly, q, 5).map((r) => ({ kind: `ah`, key: r.tag, r })),
      pl = (players.data ?? []).slice(0, 5).map((p) => ({ kind: `pl`, key: p.uuid, r: p }));
    // fuzzy Bazaar hits are guesses: list the AH item types first then
    return [...(bz.fuzzy ? [...ahl, ...bzl] : [...bzl, ...ahl]), ...pl];
  }, [rows, ahOnly, q, players.data]);
  (0, y.useEffect)(() => {
    setIdx(-1);
  }, [q]);
  let go = (s) => {
    ft(s.kind === `bz` ? st.item(s.r.id) : s.kind === `ah` ? st.ahItem(s.r.tag) : st.ahPlayer(s.r.uuid, s.r.name));
    setOpen(!1);
    ref.current?.blur();
  };
  let row = (s, i) =>
    (0, b.jsxs)(
      `button`,
      {
        onMouseDown: (e) => e.preventDefault(),
        onClick: () => go(s),
        // hover only highlights: Enter opens a result only after Tab/arrows mark it
        className: `flex w-full items-center gap-2.5 px-3 py-2 text-left ${i === idx ? `bg-white/8` : `hover:bg-white/5`}`,
        children: [
          s.kind === `pl`
            ? (0, b.jsx)(BA_Avatar, { uuid: s.r.uuid, size: 28 })
            : (0, b.jsx)(Jr, { id: s.key, info: s.r.info, size: 28 }),
          (0, b.jsxs)(`span`, {
            className: `min-w-0 flex-1`,
            children: [
              (0, b.jsx)(`span`, { className: `block truncate text-sm`, children: s.r.name }),
              (0, b.jsx)(`span`, {
                className: `block truncate text-[11px] text-mute`,
                children: s.kind === `bz` ? `Bazaar · ${s.key}` : s.kind === `ah` ? `Auction House · ${s.key}` : `Player`,
              }),
            ],
          }),
          s.kind === `bz` &&
            (0, b.jsxs)(`span`, {
              className: `num text-right text-xs`,
              children: [
                (0, b.jsx)(`span`, { className: `block text-buy`, children: en(s.r.m.instaBuy) }),
                (0, b.jsx)(`span`, { className: `block text-sell`, children: en(s.r.m.instaSell) }),
              ],
            }),
        ],
      },
      `${s.kind}-${s.key}`,
    );
  return (0, b.jsxs)(`div`, {
    className: `relative w-[min(340px,70vw)]`,
    children: [
      (0, b.jsx)(zr, {
        name: `search`,
        size: 16,
        className: `pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-mute`,
      }),
      (0, b.jsx)(`input`, {
        ref,
        value: q,
        onChange: (e) => setQ(e.target.value),
        onFocus: () => setOpen(!0),
        onBlur: () => setTimeout(() => setOpen(!1), 120),
        onKeyDown: (e) => {
          // Tab / Shift+Tab walk the open list (wrapping) like the arrows; with no list, Tab moves focus as usual.
          // Enter goes to the marked result, or with none marked closes the list, keeps the typed text and (from an item / other page) opens the Bazaar list it filters
          e.key === `Tab` && open && list.length
            ? (e.preventDefault(), setIdx((i) => (i < 0 ? (e.shiftKey ? list.length - 1 : 0) : (i + (e.shiftKey ? list.length - 1 : 1)) % list.length)))
            : e.key === `ArrowDown`
            ? (e.preventDefault(), setIdx((i) => Math.min(i + 1, list.length - 1)))
            : e.key === `ArrowUp`
              ? (e.preventDefault(), setIdx((i) => Math.max(i - 1, 0)))
              : e.key === `Enter`
                ? list[idx]
                  ? go(list[idx])
                  : (setOpen(!1), ref.current?.blur(), /^#\/?(\?.*)?$|^#\/(ah|watchlist)(\?.*)?$/.test(window.location.hash) || ft(st.market))
                : e.key === `Escape` && (setQ(``), ref.current?.blur());
        },
        placeholder: `Search items or players…  (Ctrl K)`,
        "aria-label": `Search Bazaar items, Auction House items or players`,
        className: `w-full rounded-lg border border-line bg-panel py-1.5 pl-8 pr-8 text-sm text-ink placeholder:text-mute focus:border-accent focus:outline-none`,
      }),
      q &&
        (0, b.jsx)(`button`, {
          onMouseDown: (e) => e.preventDefault(),
          onClick: () => setQ(``),
          "aria-label": `Clear search`,
          className: `absolute right-2 top-1/2 -translate-y-1/2 text-mute hover:text-ink`,
          children: (0, b.jsx)(zr, { name: `x`, size: 14 }),
        }),
      open &&
        q.trim() &&
        (0, b.jsx)(`div`, {
          className: `absolute right-0 top-full z-50 mt-1.5 w-[min(420px,92vw)] overflow-hidden rounded-lg border border-line bg-panel2 shadow-2xl shadow-black/60`,
          children: list.length
            ? list.map(row)
            : (0, b.jsx)(`div`, {
                className: `px-3 py-3 text-sm text-mute`,
                children: players.isFetching || dq !== q ? `Searching…` : `Nothing matches “${q}”.`,
              }),
        }),
    ],
  });
}

// ---- Market page with a search: the Auction House item types that match too ----
// Mounted in af above the list (Bazaar tab only), fed the Market's debounced query, so Enter in the header search
// (which lands on the Bazaar tab) shows Bazaar and AH matches on one page. Literal hits only: fuzzy AH guesses are noise.
function BA_MarketAhHits({ q }) {
  let { byId } = $t(),
    ah = ka().data,
    ahOnly = (0, y.useMemo)(() => (ah ?? []).filter((r) => !byId.has(r.tag)), [ah, byId]),
    res = (0, y.useMemo)(() => (q && q.trim() ? Ir(ahOnly, q, 24) : null), [ahOnly, q]);
  if (!res || res.fuzzy || !res.items.length) return null;
  // Coflnet gives some different items the same name (5x "Inferno Minion Fuel"): those chips show their tag too
  let seen = {};
  res.items.forEach((r) => (seen[r.name] = (seen[r.name] ?? 0) + 1));
  return (0, b.jsxs)(`div`, {
    className: `mb-3 rounded-lg border border-line bg-panel px-3 py-2`,
    children: [
      (0, b.jsxs)(`div`, {
        className: `mb-2 flex items-center gap-2 text-xs text-mute`,
        children: [
          `Auction House items`,
          (0, b.jsx)(`button`, { onClick: () => ft(st.ah), className: `ml-auto text-accent hover:underline`, children: `Show all in the AH →` }),
        ],
      }),
      (0, b.jsx)(`div`, {
        className: `flex flex-wrap gap-1.5`,
        children: res.items.map((r) =>
          (0, b.jsxs)(
            `button`,
            {
              onClick: () => ft(st.ahItem(r.tag)),
              title: r.tag,
              className: `flex items-center gap-1.5 rounded-md border border-line bg-panel2 px-2 py-1 text-sm hover:border-accent`,
              children: [(0, b.jsx)(Jr, { id: r.tag, info: r.info, size: 20 }), r.name, seen[r.name] > 1 && (0, b.jsx)(`span`, { className: `text-[11px] text-mute`, children: r.tag })],
            },
            r.tag,
          ),
        ),
      }),
    ],
  });
}

// ---- Activity tab search ----
var BA_actQ = {}; // query per tab, kept while switching tabs

function BA_has(q, ...xs) {
  return xs.some((x) => x != null && String(x).toLowerCase().includes(q));
}

// Pure: the tab's data narrowed to rows matching q (lowercase, trimmed). Day totals are summed from what is left.
function BA_actFilter(tab, q, low, rows) {
  if (!q) return { low, rows };
  if (tab === `market`) {
    // A listing and its sale are shown as one line: keep both when either matches.
    let hit = rows.filter((t) => BA_has(q, t.item, t.other, t.raw)),
      keep = new Set(hit),
      sales = new Set(hit.map((t) => t.listed).filter((x) => x != null)),
      listings = new Set(hit.map((t) => t.ts));
    return { low, rows: rows.filter((t) => keep.has(t) || sales.has(t.ts) || (t.listed != null && listings.has(t.listed))) };
  }
  if (!low) return { low, rows };
  let days = (low.days ?? []).map((d) => {
    if (tab === `trades`) {
      let trades = (d.trades ?? []).filter((t) =>
        BA_has(q, t.partner, ...[...(t.given ?? []), ...(t.received ?? [])].map((i) => i.name)),
      );
      return { ...d, trades, givenCoins: trades.reduce((a, t) => a + (t.givenCoins || 0), 0) };
    }
    let lowballs = (d.lowballs ?? []).filter((r) => BA_has(q, r.trade?.partner, ...(r.lines ?? []).map((l) => l.label)));
    return {
      ...d,
      lowballs,
      gained: lowballs.reduce((a, r) => a + (r.gained || 0), 0),
      pending: lowballs.reduce((a, r) => a + (r.pending || 0), 0),
    };
  });
  return { low: { ...low, days: days.filter((d) => (tab === `trades` ? d.trades : d.lowballs).length) }, rows };
}

function BA_ActSearch({ tab, mod, low, s, rows }) {
  let [q, setQ] = (0, y.useState)(() => BA_actQ[tab] ?? ``),
    k = q.trim().toLowerCase(),
    f = (0, y.useMemo)(() => BA_actFilter(tab, k, low, rows), [tab, k, low, rows]);
  return (0, b.jsxs)(b.Fragment, {
    children: [
      (0, b.jsx)(`input`, {
        value: q,
        onChange: (e) => setQ((BA_actQ[tab] = e.target.value)),
        onKeyDown: (e) => e.key === `Escape` && setQ((BA_actQ[tab] = ``)),
        placeholder: tab === `market` ? `Search items or players…` : `Search items or the player you traded with…`,
        "aria-label": `Search this tab`,
        className: `w-full rounded-lg border border-line bg-panel px-3 py-2 text-sm text-ink placeholder:text-mute/60 focus:border-accent focus:outline-none`,
      }),
      tab !== `trades` && (0, b.jsx)(BA_ProfitChart, { tab, low: f.low, rows: f.rows, days: k ? null : s?.days }),
      tab === `trades`
        ? (0, b.jsx)(BA_TradesTab, { mod, low: f.low })
        : tab === `lowball`
          ? (0, b.jsx)(BA_LowballTab, { mod, low: f.low })
          : (0, b.jsx)(BA_MarketTab, { s, rows: f.rows }),
    ],
  });
}
