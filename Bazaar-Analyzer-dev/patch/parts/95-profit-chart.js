/* ---- 95-profit-chart.js -------------------------------------------------------------------
 * BA_ProfitChart: bars per day on the Activity tabs, above the day boxes (follows the tab's search).
 * Lowballing: gained (green) + pending (faded, stacked). AH & Bazaar: earned - spent (red below zero).
 * Mounted by BA_ActSearch (parts/80-search.js). Test: patch/tests/profit-chart.test.mjs.
 * ------------------------------------------------------------------------------------------ */

var BA_chartDays = 30;

// Pure: [{ day, v, p }] oldest first, the last BA_chartDays days that have data. v = main value, p = pending (>= 0).
// days = the mod's per-day net over its whole trade book ({ day: net }, mod 0.7.1+); rows (latest few hundred) otherwise.
function BA_profitSeries(tab, low, rows, days) {
  let out = [];
  if (tab === `lowball`) {
    for (let d of low?.days ?? []) d.lowballs?.length && out.push({ day: d.day, v: d.gained || 0, p: d.pending || 0 });
  } else if (tab === `market` && days) {
    for (let [day, v] of Object.entries(days)) out.push({ day, v, p: 0 });
  } else if (tab === `market`) {
    let m = new Map();
    for (let t of rows ?? []) {
      let k = BA_modKinds[t.kind]?.[1] ?? 0;
      if (!k || t.coins == null || !t.day) continue;
      m.set(t.day, (m.get(t.day) || 0) + k * t.coins);
    }
    for (let [day, v] of m) out.push({ day, v, p: 0 });
  }
  return out.sort((a, b) => (a.day < b.day ? -1 : 1)).slice(-BA_chartDays);
}

function BA_ProfitChart({ tab, low, rows, days: all }) {
  let [hover, setHover] = (0, y.useState)(-1),
    days = (0, y.useMemo)(() => BA_profitSeries(tab, low, rows, all), [tab, low, rows, all]);
  if (days.length < 2) return null;
  let top = Math.max(0, ...days.map((d) => Math.max(0, d.v) + d.p)),
    bot = Math.min(0, ...days.map((d) => d.v)),
    span = top - bot || 1,
    H = 120,
    zero = (top / span) * H,
    h = (x) => (Math.abs(x) / span) * H,
    d = days[hover],
    total = days.reduce((a, x) => a + x.v, 0),
    main = tab === `lowball` ? `gained` : `net`;
  return (0, b.jsx)(BA_Box, {
    title: tab === `lowball` ? `Lowball profit per day` : `Net per day (earned − spent)`,
    right: (0, b.jsx)(`span`, {
      className: `num text-xs text-mute`,
      children: d
        ? `${BA_dayLabel(d.day)} · ${main} ${BA_e0(d.v)}${d.p ? ` · pending ${BA_e0(d.p)}` : ``}`
        : `${days.length} days · ${main} ${BA_e0(total)}`,
    }),
    children: (0, b.jsx)(`div`, {
      className: `px-4 py-3`,
      children: (0, b.jsx)(`div`, {
        className: `relative flex`,
        style: { height: H, gap: 2 },
        onMouseLeave: () => setHover(-1),
        children: [
          (0, b.jsx)(`div`, {
            className: `pointer-events-none absolute border-t border-line`,
            style: { left: 0, right: 0, top: zero },
          }, `zero`),
          ...days.map((x, i) =>
            (0, b.jsxs)(
              `div`,
              {
                className: `relative min-w-0 flex-1 ${i === hover ? `bg-white/5` : ``}`,
                onMouseEnter: () => setHover(i),
                children: [
                  x.p > 0 &&
                    (0, b.jsx)(`div`, {
                      className: `absolute bg-buy`,
                      style: { left: 1, right: 1, bottom: H - zero + h(Math.max(0, x.v)), height: h(x.p), opacity: 0.45 },
                    }),
                  (0, b.jsx)(`div`, {
                    className: `absolute ${x.v >= 0 ? `bg-up` : `bg-down`}`,
                    style: x.v >= 0
                      ? { left: 1, right: 1, bottom: H - zero, height: h(x.v) }
                      : { left: 1, right: 1, top: zero, height: h(x.v) },
                  }),
                ],
              },
              x.day,
            ),
          ),
        ],
      }),
    }),
  });
}
