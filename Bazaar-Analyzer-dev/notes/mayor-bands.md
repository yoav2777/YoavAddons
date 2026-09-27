# Mayor bands on the AH price chart + the "Clean" AH filter (v12)

Everything lives in one part file: `C:/Users/Lenovo/Bazaar-Analyzer-dev/patch/parts/50-mayor-bands.js`
(plain JS, no CSS file — all styling is inline so it cannot clash with the other agents' css).
Mounts: 13 exact-match replacements in `patch/patch_v12.mjs` (2 for the bands, 11 for Clean).
Tests: `patch/tests/mayor-bands.test.mjs` (20 tests, `node patch/tests/mayor-bands.test.mjs`).
Screenshots: `notes/shots/mayor/` (hyperion-1m, pet-1m, hyperion-clean).

---

## 1. Mayor data source

`GET https://sky.coflnet.com/api/mayor` — **no parameters, returns the whole election history**.

* `access-control-allow-origin: *`, `Cache-Control: public, max-age=600` → callable straight from the browser.
* 123 KB, **351 entries = SkyBlock years 164 … 514**, i.e. 2021-10-07 → 2026-09-24. No gaps
  (`max-min+1 === length`), but the array is **NOT sorted** (it comes back 321, 392, 180, 251, 463, …).
* One entry per term:

```jsonc
{ "candidates": null, "id": null, "year": 514,
  "start": "09/19/2026 15:15:00 +00:00",     // MM/DD/YYYY, +00:00 suffix
  "end":   "09/24/2026 19:15:00",            // MM/DD/YYYY, NO suffix — also UTC
  "winner": { "key": "slayer", "name": "Aatrox", "votes": 951246,
              "perks": [{ "name": "SLASHED Pricing", "description": null, "minister": false }],
              "minister": { "key": "fishing", "name": "Marina",
                            "perk": { "name": "Double Trouble", "description": "…§7…", "minister": true } } } }
```

* `end` of year N === `start` of year N+1 exactly, and `end - start === 124 h` (a SkyBlock year), so the
  terms tile the timeline with no gaps and the SkyBlock-calendar math is **not** needed.
* `winner.minister` only exists on recent terms (ministers were added later); `winner.perks` can be `null`
  on old ones; `winner.key` can be `""`. `candidates` and `id` are always null on this endpoint.
* Dead ends checked: `/api/mayor/current` and `/api/mayor/<millis>` are **400** — the path segment is a
  *year* (`/api/mayor/514`). The Hypixel v2 election resource only has the current mayor + current election,
  so it is not used at all.

### Caching (`BA_mayorLoad()`)

memory (`BA_mayorMem`) → `localStorage["bz.mayors.v1"]` → network, everything in try/catch.
Stored slim: `{v:1, at: Date.now(), terms:[{start,end,name,key,year,minister}, …]}` (~30 KB, not the raw
123 KB). The cache is rejected when it is older than 24 h **or when `now > lastTerm.end`** (a new term
started — otherwise the right edge of the chart would stay bare for up to a day).
On failure: one `console.warn`, `[]` returned, no bands, nothing else breaks.

### Helpers (all pure, all unit-tested)

| function | what |
|---|---|
| `BA_mayorTime(s)` | parses both Coflnet shapes as **UTC** via `Date.UTC` (never `new Date(str)`, which would be local time) |
| `BA_mayorParse(raw)` | validates + sorts by `start`; clamps any overlap (`out[i-1].end = out[i].start`); drops junk |
| `BA_mayorAt(terms, ts)` | binary search, `start` inclusive / `end` exclusive, `null` outside the known range |
| `BA_mayorColor(t)` | dot colour by mayor name (`BA_MAYOR_DOT`), falls back to `#a09d93` |
| `BA_mayorLegendRow(ts)` | `[]` or `[{title:'Mayor: ', value:{text,color}}]` for the chart tooltip |
| `BA_mayorFloatIndex(list, ts, periodMs)` | timestamp → **fractional** klinecharts dataIndex |
| `BA_mayorTimeAt(list, idx, periodMs)` | the inverse (used to pick which terms can be visible) |

## 2. How the bands are drawn

The AH price chart is `id(...)` in the bundle (klinecharts **10.0.3**, see `notes/bundle-map.md` §2).
Its canvases are **all transparent** (`background-color: rgba(0,0,0,0)`; the visible dark colour is
`bg-bg` on the outer 460 px box), so a plain DOM layer *underneath* them shows through and can never
recolour the price line, the drawings or the indicators.

`BA_MayorBands({chart, ready, tf})` renders **one empty div** and does everything imperatively:

* mounted as the **first child** of the chart's `relative min-w-0 flex-1` wrapper → it paints below the
  later positioned siblings (the chart container, the loading/empty/error overlays at `z-10`).
  `pointer-events:none` + `aria-hidden` → the drawing tools and the crosshair are untouched.
* geometry per paint:
  * `chart.getSize('candle_pane','main')` → `{left,top,width,height}` of the **plot area only**
    (excludes the y-axis and the x-axis); the layer is positioned to exactly that box.
    Measured live: `left 0, top 0, 1212 x 394` in a 1400 px viewport.
  * `x = chart.convertToPixel({dataIndex: BA_mayorFloatIndex(...)}, {paneId:'candle_pane'}).x`.
    klinecharts' own `{timestamp}` form would work too but it **floors to whole bars**
    (`StoreImp.timestampToDataIndex`), which makes band edges jump; the fractional dataIndex is linear
    (`dataIndexToCoordinate`) and extrapolates past both ends of the data, so partial bands and bands
    beyond the last bar are exact.
  * `periodMs` must match `setPeriod`: **1D/1W → 3600000 (1 h), 1M/ALL → 86400000 (1 day)**.
* redraw: `subscribeAction('onVisibleRangeChange' | 'onZoom' | 'onScroll')` + a `ResizeObserver` on the
  wrapper + a 500 ms safety interval, all coalesced into one `requestAnimationFrame`. **Every scheduled paint
  recomputes the positions** (see §6: the old "signature" skip was the drag/zoom desync); band nodes are kept
  per term and only changed styles are written, so a paint that finds nothing moved writes nothing.
* each band: see §6 for the current look (was: opaque `--color-panel` / `--color-panel2` alternation + border).
  Bands are clamped to `[left edge of the first bar, width]` and skipped under 0.5 px.
* label (name + colour dot) — see §6 (was 6 px dot, 10 px, ≥ 52 px, first at `top:20px`, then `bottom:5px`).

Tooltip: the legend factory `nd` (used *only* by the AH chart) gained
`…,...BA_mayorLegendRow(t.timestamp)]` → a `Mayor: Aatrox · Marina` row in the crosshair tooltip on every
range. Minister is appended after `·` when the term has one.

Verified live (port 47850, 1400x900): HYPERION / TERMINATOR / PET_ENDER_DRAGON, ranges 1D (one full-width
Aatrox band), 1M (7 bands of 194 px, labels), ALL (258 bands of 5 px, no labels, tooltip still correct),
drag-pan and the "Fit" button (bands follow, and stop at the last known term end — the ~45 px after
2026-09-24 stay unbanded on purpose), drawing a horizontal segment + deleting it, console empty.
Bazaar item charts (`#/item/...`, a different component) have **0** band layers — untouched.

## 3. Clean filter

Coflnet has a first-class filter for this — `/api/filter/options?itemTag=HYPERION` lists
`{"name":"Clean","options":["yes"],"type":256,"longType":"BOOLEAN"}`, i.e. the query param is
**`Clean=yes`** (there is no `Clean=no`; leave the param off instead). It works on both endpoints the site
already calls with filter params:

* `/item/price/{TAG}/history/{day|week|month|full}?Clean=yes` → the price graph.
  HYPERION month: plain first bucket `avg 939.8M, min 457M, max 1.9B, volume 46` vs clean
  `avg 470.4M, min 470M, max 472.5M, volume 6`.
* `/auctions/tag/{TAG}/active/bin?Clean=yes` → the Lowest BIN card. Clean rows have
  `reforge:"None"`, `enchantments:[]` and `flatNbt` containing only `uid`/`uuid`.
* The sold list (`/auctions/tag/{TAG}/sold`) takes **no** filter params — it has always been filtered
  client-side by `Ji`/`qi`, so Clean is matched there by `BA_isCleanListing`.

### `BA_isCleanListing(a, reforges)`

Returns false when any of these is present: a non-empty `enchantments` array; a reforge
(`a.reforge`, else `Gi(a, reforges)`); stars (`Ri(nbt)` or `Vi(itemName)`); **or any flattened-nbt entry**
that is not noise. The nbt rule is a deny-by-default scan, so new modifiers are caught automatically:

```
skip when the value is "", "0", "0.0", "false", "none", "null"
skip when the key is in BA_CLEAN_NEUTRAL  (structural keys every item/pet carries:
     type tier active hideInfo hideRightClick noMove petSoulbound bossId spawnedFor
     spawnedForBest donated_museum item_tier boss_tier edition)
key "exp"  -> modified when > 0 (a levelled pet); `ea` would otherwise swallow it as "*xp"
skip when `ea` matches (the bundle's own noise regex: uuid|uid|timestamp|originTag|id|*xp|date|*time*)
otherwise -> modified
```

That covers recomb, hpc, gem slots/qualities, ability + power scrolls, runes, dyes, skins, art of war,
wood singularity, pet candy/held item, etc. without listing them. Checked against Coflnet's own
`Clean=yes` rows: clean gear is `{uid,uuid}` only → clean; a Lvl 1 pet
(`type/active/exp:"0"/tier/hideInfo/candyUsed:"0"/hideRightClick/noMove/petSoulbound/bossId/spawnedFor/uid/uuid`)
→ clean; exp>0, candyUsed>0 or heldItem → not clean. Never throws (junk input returns a boolean).

### Wiring (all in patch_v12.mjs, anchors verified unique in the v11 bundle)

| bundle symbol | change |
|---|---|
| `Fi` (single-instance kinds) | `+ 'clean'` — cannot be added twice |
| `Ii(kind,arg)` (factory) | `case 'clean': {id,kind,value:!0}` |
| `dd` (Add-filter "Common" list) | `{kind:'clean',label:'Clean',hint:'no modifiers at all'}` **first** |
| `ud` (active chip) | `Clean` chip with a `no modifiers` sub-label + the normal × |
| `Xi` (filters → Coflnet params) | `case 'clean': params.Clean = 'yes'` |
| `qi` (client matcher) | `case 'clean': return BA_isCleanListing(e, n && n.reforges)` |
| `BA_parseFilters` ok-list | `+ 'clean'` → `#/ah/item/TAG?f=[{"kind":"clean","value":true}]` works (mod links) |
| `fd` add handler | wrapped in `BA_cleanApply(list, addedKind)` |
| `fd` menu entries (common / enchants / modifiers) | `hint: BA_cleanHint(t, kind) || <old hint>` |

`BA_cleanApply` / `BA_cleanHint` with `BA_CLEAN_CONFLICT = {enchant, stars, reforge, recomb, hpc, nbt}`:
adding **Clean** drops those filters (rarity / bin / price survive); adding one of **those** drops Clean.
The menu says why before the click — `Stars … turns Clean off`, `Clean … clears modifier filters`.

Verified live: HYPERION with Clean → graph drops from ~1B to ~490M, all 40 sold rows are a bare
`Hyperion` (no reforge prefix, no stars, no enchant pills) at 490–504M; PET_ENDER_DRAGON opened straight
from `?f=%5B%7B%22kind%22%3A%22clean%22%2C%22value%22%3Atrue%7D%5D` → chip restored from the URL, every
row `[Lvl 1] Ender Dragon`; clicking Stars removed the Clean chip and clicking Clean removed the Stars
chip; console empty. The Lowest BIN card usually shows the same listing with and without Clean simply
because the cheapest BIN is normally already clean (checked ASPECT_OF_THE_VOID and DIVAN_CHESTPLATE).

## 4. Gotchas for the next agent

* `nd` is used **only** by `id`; the Bazaar charts use `BA_Tip` + `BA_rowsA`, so the mayor row cannot leak
  into them.
* Do not switch the band layer to `{timestamp}` conversion "for simplicity" — see §2, it snaps to bars.
* Do not put a "did the view change" check back in front of the paint unless it uses the exact x mapping
  (`convertToPixel` of index 0 + `getBarSpace`): `getVisibleRange()` is whole bars and clamped, see §6.
* If `getSize('candle_pane','main')` ever returns `left/top !== 0` (a left-hand y-axis), the layer already
  follows it; nothing else assumes 0.
* The part file must stay free of CSS-file dependencies: the theme agent owns `theme-claude.css` and the
  craft agent owns `ba-craft.css`.
* Cached API bodies used while writing this: `notes/cache/mayor_list.json`,
  `filteropts_HYPERION.json`, `clean_bin_{HYPERION,TERMINATOR,PET}.json`,
  `clean_hist_HYPERION.json` / `plain_hist_HYPERION.json`, `cmp_{plain,clean}_*.json`.

---

## 5. Update 2026-09-24

* **Bazaar chart too.** `BA_MayorBands` is also mounted in the Bazaar price chart `Hd` (first child of its
  `relative min-w-0 flex-1` wrapper, like the AH chart), with a new `periodMs` prop = `ma[i.tf].barMs`
  (1 min … 1 day; the AH chart still derives it from `tf`). Its tooltip (`BA_rowsA` → `BA_Tip`, rows `{k,v,c}`)
  gets `...BA_mayorTipRow(bar.timestamp)` → `Mayor  Aatrox · Marina`. The bazaar canvases are transparent too.
* **Labels at the bottom** (`bottom:5px`). At `top:20px` they sat on klinecharts' legend as soon as it wrapped
  (≈1000 px wide window: "Time … Mayor: …" goes to 2 lines) — that was the visible bug.
* **Tint:** band background = `linear-gradient(<mayor colour>1a)` over the alternating panel greys, so each
  mayor's terms are recognisable even when too narrow for a label (1Y/ALL). The left border line is skipped
  on bands < 8 px (it was just noise there).
* **Refresh:** `BA_mayorStale(terms)` = now > last term end. `BA_mayorLoad` refetches a stale in-memory list
  (max every `BA_MAYOR_RETRY` = 10 min, Coflnet caches 10 min) and keeps serving the old list meanwhile;
  `BA_MayorBands` checks every 60 s. Before, a page left open past a term change never got the new term.
* Term timing re-checked: Coflnet's start/end are exactly Late Spring 27, 00:00 of the SkyBlock calendar
  (epoch 1560275700000, 124 h years, 31-day months); Hypixel's election API agreed on the current mayor.
* **Mayors toggle (beta), off by default.** `BA_MayorToggle` sits in both chart toolbars (AH: after Fit;
  Bazaar: before the camera/fullscreen group), hover title `Beta`. State `BA_mayorOn` in
  `localStorage["bz.mayorBands.on"]` ("1"/"0", missing = off), shared by both charts through
  `BA_useMayorOn()` (useSyncExternalStore). While off: no fetch, no bands, `BA_mayorLegendRow`/`BA_mayorTipRow`
  return []. The owner wants the feature re-checked/fixed later before dropping the Beta label.

## 6. Update 2026-09-25: bands locked to the chart + quieter look

**Desync (owner: bands move out of sync with the candles when dragging or zooming).** Root cause:
`BA_mayorPaint` skipped the repaint when a signature was unchanged, and the signature used
`chart.getVisibleRange()` `{from,to}`. In klinecharts 10 those are **whole-bar indices, rounded and clamped to
`[0, dataLength]`** (`StoreImp._adjustVisibleRange`). So every drag / trackpad swipe / pinch-pan step smaller
than a bar kept the old bands, and while every bar is on screen (1M = 30 daily bars fill the plot) `from/to`
stay `0/30` whatever the offset, so the bands did not move at all until the next zoom step changed `barSpace`.
The zoom itself repainted, but always from whatever stale offset the last pan had left (and a trackpad
"pinch" on a laptop is a mix of wheel zoom and horizontal scroll).

Fix (parts/50-mayor-bands.js): no signature. Each scheduled frame recomputes every visible band from the
chart's current mapping (`convertToPixel` of the fractional dataIndex, as before); the band divs are kept per
term (`el.__ba.nodes`, keyed by term start) and only `left/width/background/separator/label` that changed
are written. Visible terms are culled from the pixel window itself (`x0 = convertToPixel(dataIndex 0)`,
`getBarSpace().bar`), not from the clamped visible range, and bands stop at the first bar's left edge (no
bands over the empty area when the chart is dragged past its first bar). Timing: the chart actions fire
synchronously in the input event, klinecharts lays out in a microtask and draws its canvases in the next
`requestAnimationFrame`; the band paint is queued for that same frame, so DOM and canvas change together.

Measured in headless Chromium (mocked Coflnet/Hypixel, 1400x900, AH 1M HYPERION + Bazaar 1M
ENCHANTED_DIAMOND). A hook ran at the end of every animation frame and compared each on-screen band edge
with the x of its term boundary recomputed from the chart state **at the moment klinecharts cleared its candle
canvas** (independent of the band code: `convertToPixel({timestamp})` of the two neighbouring bars, lerp):

| gesture | before: max / mean offset, frames > 1 px | after: max offset, frames > 1 px |
|---|---|---|
| AH drag (40 x 3 px right, 30 x 3 px back), mid-gesture | 189 px / 60 px, 209 of 223 | 0.42 px, 0 |
| AH after the drag, at rest | 147 px | 0.42 px |
| AH wheel zoom in/out | 147 px (stale from the drag) | 0.97 px, 0 |
| AH horizontal trackpad swipe | 85 px / 43 px, 59 of 61 | 0.97 px, 0 |
| AH touch pinch (CDP two-finger) | 85 px, 4 frames | 0.97 px, 0 |
| AH window resize 1400-1050-1400 | 0.34 px | 0.34 px |
| Bazaar drag, mid-gesture | 193 px / 74 px, 245 of 260 | 0.75 px, 0 |
| Bazaar after the drag | 142 px | 0.75 px |
| Bazaar swipe / pinch | 80 px | 0.91 px, 0 |
| Bazaar Fit button, resize | 0.75 px | 0.75 px |

(< 1 px = klinecharts rounds bar x to whole pixels, the reference does not.) Regression tests in
`patch/tests/mayor-bands.test.mjs` run `BA_mayorPaint` against a fake chart with klinecharts' own x math:
sub-bar drag steps with an unchanged clamped visible range, zoom + resize, node reuse, first-bar clip.

**Look (owner: the shading is way too noticeable).** Bands are now a background hint:
* fill = the mayor's colour at 4 % alpha (2.2 % when the term is < 24 px wide, i.e. 1Y/ALL where hundreds of
  bands read as a barcode); every other term gets +1.5 % of `--color-ink` so two terms of one mayor stay apart.
  No more opaque panel/panel2 greys.
* boundary: 1 px line, `--color-ink` fading from 10 % at the bottom to 2 % at the top, only where a term
  really starts on screen and only on bands >= 8 px.
* label: bottom-left, 10 px `--color-mute` at 55 % opacity, 5 px colour dot, only on bands >= 64 px.
* Toggle unchanged: "Mayors" button, off by default, hover "Beta".
