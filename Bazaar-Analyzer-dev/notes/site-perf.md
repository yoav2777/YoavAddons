# Site performance sweep (polish sweep 1/2, "site-perf")

Read-only audit of `Bazaar-Analyzer/app` (v12 bundle), `Bazaar-Analyzer-dev/patch/parts`, `Bazaar-Analyzer-dev/craft`.
Reference source for understanding the minified code: `C:/Users/Lenovo/bazaar graphs/src` (the TS the v11/v12 bundle was built from).

Bundle audited: `C:/Users/Lenovo/Bazaar-Analyzer/app/assets/index-KsTXbicp-v12.js` (863,755 chars).
Byte offsets below are into THAT file; re-locate with `indexOf` after any repatch.

## Measured baselines (node, real data)

Cache fixture `notes/cache/bazaar.json` = 3,643,298 bytes, 2197 products, 61,398 summary-order objects.

| Work | Cost |
|---|---|
| `JSON.parse` of the 3.6 MB bazaar body | 12.3 ms |
| `toProduct` x2197 (builds 61k BookLevel objects) | 1.8 ms |
| `useRows` rebuild (metrics + search string + Map) | 1.5 ms |
| **total per bazaar poll** | **~15.6 ms** (0.05% of a 30 s poll period) |
| `BA_bookFlips` over 2197 products | 0.58 ms -> 175 rows (174 with the default `hideLoss`) |
| `listingCraftCost` per listing (fixtures) | 0.21-2.06 ms (DIVAN_DRILL worst) |
| Fuse index construction over ~1.5-2.2k rows | 0.5-1.0 ms (lazy; cheap) |
| **one Fuse fuzzy search** (warm index) | **4.8 ms** |
| one Fuse fuzzy search (cold index, incl. build) | 6.1 ms (only 1.26x warm - the build is NOT the problem) |
| literal token scan over 2197 rows | 0.1 ms |

Per-poll main-thread cost is a non-issue. **The expensive thing on this site is `fuse.search`, not parsing.**

## Things that are already correct - do not "fix" these

- **Hypixel bazaar is gzipped.** 3.6 MB of JSON = **489 KB on the wire**. Verified:
  `curl --compressed -w '%{size_download}' https://api.hypixel.net/v2/skyblock/bazaar` -> 488779.
- **`cache: 'no-cache'` on `fetchBazaar` is the right call.** Hypixel sends `last-modified` and
  `Cache-Control: public, max-age=60`. A conditional re-request returns **304 with 0 bytes**:
  ```sh
  curl -s -o /dev/null -D /tmp/h1 --compressed https://api.hypixel.net/v2/skyblock/bazaar
  LM=$(grep -i '^last-modified' /tmp/h1 | sed 's/^[^:]*: //' | tr -d '\r')
  curl -s -o /dev/null -H "If-Modified-Since: $LM" --compressed \
    https://api.hypixel.net/v2/skyblock/bazaar -w 'HTTP=%{http_code} bytes=%{size_download}\n'
  # -> HTTP=304 bytes=0
  ```
  Data changes ~every 60 s, poll is 30 s, so roughly half the polls are free.
- **Hidden-tab polling is handled**: `useBazaar` sets `refetchIntervalInBackground: hasAlerts`, so the
  30 s interval pauses while the tab is hidden unless an alert rule is enabled (deliberate).
- **No duplicate `['bazaar']` fetch.** `Xt()` (site, has `refetchInterval`) and `useCraftCtx()`
  (parts/00-shared.js, no interval) are two observers on one cache entry -> one request.
- **Market table and the AH item list are virtualized** (`@tanstack/react-virtual`; 2 `so({count:` sites in the bundle).
- **Alert log is capped** at 50 entries (`pushLog ... .slice(0, 50)`) - no overnight localStorage growth.
- **Player names are batched** 100 per POST and the cache is capped at 4000 entries (`fetchPlayerNames`).
- **klinecharts instances are disposed** in the effect cleanup of both `PriceChart` and `AhChart`.
  (`dispose(` greps 0 in the bundle only because the import is minified - the call is there.)
- **`BA_mayorPaint`'s 500 ms `setInterval` is cheap and is cleared.** It only `requestAnimationFrame`s a
  repaint, and the paint early-returns on an `el.dataset.baSig` signature (size|visibleRange|barSpace|
  list length|first+last timestamp|terms). Safety net for layout changes that fire no chart action.
- **`useNow(1000)` is isolated** in the tiny `Freshness` component - a 1 Hz re-render of one label, not the page.
- **The craft engine's caches are sound**: `CC_MEM` / `CC_BULK` / `CC_INFLIGHT` / `CC_BIN_INFLIGHT` are
  module-level and shared across contexts; `ctx.lowestBin` and the recipe map are carried onto each new ctx
  by `BA_craftCtxFor`, so a bazaar poll never re-downloads them. `ctx.errors` is capped at 50.
- **`BA_craftLstCache` is a `WeakMap` keyed by ctx**, so old per-poll caches are collectable.

### Dead-ish path worth knowing
`ccGetRecipes` starts with `if (ctx.recipesComplete) return []`, and `ccEnsureBulk` sets
`recipesComplete = true` as soon as the shipped `app/data/recipes.json` (292,395 bytes) loads. So the
per-item NEU fetch (`ccFetchNeu`) **and** the per-item `localStorage` recipe cache
(`ba.craft.r1.<ID>`, `ccLsSet`, 24 h TTL, **no eviction anywhere**) only ever run when that local file
fails to load. The unbounded-localStorage risk is therefore theoretical today - but if anyone ever makes
`recipesUrl` remote or partial, that cache grows one key per item forever and, once the quota is hit,
`setItem` throws into an empty `catch` and the cache silently dies. Add pruning before relying on it.

## Findings (reported to the orchestrator)

1. **medium** - Market search does the 4.8 ms fuzzy search up to 3x per keystroke; one whole search
   exists only to produce a boolean. Bundle `@697486`: `x=useMemo(()=>a.trim()?Ir(t,a,1).fuzzy:!1,[t,a])`,
   while the list memo just above already calls `Fr(r,a)` - and `Fr(e,t,n)` is literally
   `Ir(e,t,n).items`, i.e. it computes `.fuzzy` and throws it away. No debounce on the query either
   (`useUi.setQuery` fires per keystroke), and both memos depend on `rows`, which changes identity on
   every 30 s poll, so an active query re-runs them every poll too.
   Fix: call `Ir` once in the list memo, keep `{items, fuzzy}`, delete the `x` memo; debounce the query ~150 ms.
2. **low-medium** - Book Flips table renders all 174 rows unvirtualized (`list.map((r) => jsx(BA_BookRow ...))`,
   parts/40-book-flips.js:246) while the Market table next to it is virtualized; full re-render each poll.
3. **low-medium** - `BA_useAvgSale` (parts/10-craft-tab.js:29) pins `tf` to `1M` and `params` to `{}`, but the
   chart's default `tf` is `1D` (`stores.ts` `tf: '1D'`) and it passes the page's filter params, so the
   query keys almost never coincide -> one extra Coflnet `/history/month` request per AH item page view.
   The code comment "often no extra request" is wrong for the default config. (Response is small: 1.1 KB
   wire / 31 rows for HYPERION, so this is about Coflnet rate limits, not bandwidth.)
4. **low** - A sold-list row that has been hovered once sets `want=true` permanently, and its effect deps
   are `[want, ctx, tag, itemKey]`. `ctx` is a new object on every Hypixel price update (~60 s), so every
   row ever hovered recomputes `listingCraftCost` (0.2-2 ms) and re-renders forever, popover closed or not.

## Repro scripts

Benchmarks were run ad-hoc; to redo them, build `rows` as `useRows` does
(`{id, name, search: `${name} ${id.replace(/[_:]/g,' ')}`.toLowerCase(), activity}`) from
`notes/cache/bazaar.json` and use the project's own fuse.js:
```js
const req = require('node:module').createRequire('C:/Users/Lenovo/bazaar graphs/package.json');
const Fuse = req('fuse.js').default || req('fuse.js');
new Fuse(rows, { keys: ['name','search'], threshold: 0.32, ignoreLocation: true }).search('hyperlon');
```
`listingCraftCost` timing uses the offline fixtures: `craft/tests/fx.mjs` -> `fixtureCtx(core)`,
`LISTING_KEYS`, `listing(k)`; pass `{loadRecipeTree, cleanCraftCost, priceItem}` from `BA_craft`, and build
a **fresh** `fixtureCtx` per iteration to model a new bazaar poll.
Book Flips row count: `vm.runInContext` on `parts/40-book-flips.js` with
`{ BA_craft: { canAnvilEnchant } }`, then `__f(products, {tax:.0125, tick:.1, canAnvil: __can})`
(see `patch/tests/book-flips.test.mjs` for the pattern).
