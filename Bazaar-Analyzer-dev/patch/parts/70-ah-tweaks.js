// AH item page tweaks (mounted by patch_v12.mjs):
//   - AH price chart hover: a BA_Tip box at the cursor with the bar's price, like the Bazaar chart
//   - Add-filter menu lists every reforge; chip values use a search box (BA_Pick) instead of <select>
//   - "Wither Impact" entry in the Add-filter menu on the wither blades (all three ability scrolls)

/* ============================== chart hover tooltip ============================== */

// Rows for BA_Tip on the AH price chart (same values as its top-left legend `nd`). tz = the chart's time zone.
function BA_ahTipRows(tz) {
  return (bar) => [
    {
      k: `Time`,
      v: new Date(bar.timestamp).toLocaleString(`en-GB`, {
        timeZone: tz,
        day: `2-digit`,
        month: `short`,
        year: `numeric`,
        hour: `2-digit`,
        minute: `2-digit`,
        hour12: !1,
      }),
    },
    { k: `Average`, v: en(bar.close, { compact: !1 }), c: X.ask },
    { k: `Low / High`, v: `${en(bar.low, { compact: !1 })} / ${en(bar.high, { compact: !1 })}` },
    { k: `Sales`, v: nn(bar.volume), c: X.bid },
    ...BA_mayorTipRow(bar.timestamp),
  ];
}

/* ============================== Wither Impact filter ============================== */

// Necron's Blade and its four upgrades. Wither Impact = these three scrolls together.
const BA_WIMP_TAGS = new Set([`HYPERION`, `ASTRAEA`, `SCYLLA`, `VALKYRIE`, `NECRON_BLADE`]);
const BA_WIMP_VALUE = `IMPLOSION_SCROLL SHADOW_WARP_SCROLL WITHER_SHIELD_SCROLL`;
const BA_WIMP_ENTRY = { kind: `wimp`, label: `Wither Impact`, hint: `Implosion + Shadow Warp + Wither Shield` };
const BA_WIMP_WORDS = `wither impact wimp ability scroll implosion shadow warp wither shield`;

// "Common" entries of the Add-filter menu (fd). q = the lowercased search text (`_` already turned into spaces).
function BA_addFilterCommon(tag, q) {
  let list = dd.filter((e) => e.label.toLowerCase().includes(q) || e.hint.includes(q));
  return BA_WIMP_TAGS.has(tag) && BA_WIMP_WORDS.includes(q) ? [BA_WIMP_ENTRY, ...list] : list;
}

// Picking "Wither Impact": one ability_scroll filter with all three scrolls (replaces any other ability_scroll
// filter). Uses the item's own spelling of the value when the sold list has it, so the chip's dropdown shows it.
function BA_wimpApply(list, modifiers) {
  let m = (modifiers ?? []).find((x) => x.key === `ability_scroll`),
    value = m?.values.find((v) => BA_sameTokens(v, BA_WIMP_VALUE)) ?? BA_WIMP_VALUE,
    f = Ii(`nbt`, `ability_scroll`);
  f.value = value;
  return BA_cleanApply([...list.filter((x) => !(x.kind === `nbt` && x.key === `ability_scroll`)), f], `nbt`);
}

// Add-filter menu: every reforge as its own entry (q = lowercased search text). Picking one = a Reforge chip with it.
function BA_reforgeMatch(reforges, q) {
  return reforges.filter((r) => r !== `None` && r.toLowerCase().includes(q));
}

// Value picker of a filter chip (reforge / modifier): a plain search box + list. Typing only filters the list
// (never fills the box); click or Enter picks. Replaces the native <select> and its jump-to-letter typing.
function BA_Pick({ value, options, onChange }) {
  let [open, setOpen] = (0, y.useState)(!1),
    [q, setQ] = (0, y.useState)(``),
    ql = q.trim().toLowerCase(),
    hits = ql ? options.filter((o) => o.toLowerCase().includes(ql)) : options,
    [idx, setIdx] = (0, y.useState)(0), // the row Tab / arrows point at, Enter picks it
    list = (0, y.useRef)(null),
    show = () => { setOpen(!0), setQ(``), setIdx(0); },
    pick = (v) => { onChange(v), setOpen(!1), setQ(``); };
  (0, y.useEffect)(() => { list.current?.children[idx]?.scrollIntoView({ block: `nearest` }); }, [idx, open]);
  return (0, b.jsxs)(`span`, {
    className: `relative`,
    children: [
      (0, b.jsx)(`input`, {
        value: open ? q : value,
        placeholder: open ? value || `search…` : `choose…`,
        autoComplete: `off`,
        spellCheck: !1,
        onFocus: show,
        onClick: () => open || show(),
        onBlur: () => setOpen(!1),
        onChange: (e) => { setQ(e.target.value), setOpen(!0), setIdx(0); },
        onKeyDown: (e) => {
          let n = hits.length,
            step = e.key === `ArrowDown` || (e.key === `Tab` && !e.shiftKey) ? 1 : e.key === `ArrowUp` || (e.key === `Tab` && e.shiftKey) ? -1 : 0;
          if (step && open && n) e.preventDefault(), setIdx((i) => (Math.min(i, n - 1) + step + n) % n);
          else if (e.key === `Enter`) (ql || idx) && hits[Math.min(idx, n - 1)] && pick(hits[Math.min(idx, n - 1)]), e.currentTarget.blur();
          else if (e.key === `Escape`) e.currentTarget.blur();
        },
        className: od,
        style: { width: 150 },
      }),
      open &&
        (0, b.jsx)(`div`, {
          // keep the input focused while dragging the list's scrollbar (a blur would close the list)
          onMouseDown: (e) => e.preventDefault(),
          className: `absolute left-0 top-full z-40 mt-1 overflow-auto rounded-md border border-line bg-panel2 p-1 shadow-2xl shadow-black/60`,
          style: { width: 220, maxHeight: 260 },
          ref: list,
          children: hits.length
            ? hits.map((o, i) =>
                (0, b.jsx)(`button`, {
                  // mousedown + preventDefault: pick before the input's blur closes the list.
                  onMouseDown: (e) => { e.preventDefault(), pick(o); },
                  onMouseEnter: () => setIdx(i),
                  className: `block w-full rounded-md px-2 py-1 text-left hover:bg-white/5${i === idx ? ` bg-white/8` : ``}${o === value ? ` text-accent` : ``}`,
                  children: o,
                }, o),
              )
            : (0, b.jsx)(`div`, { className: `px-2 py-2 text-mute`, children: `No match` }),
        }),
    ],
  });
}
