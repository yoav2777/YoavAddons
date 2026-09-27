# craftCore.js (engine part 1): how it works and how to use it

File: `Bazaar-Analyzer-dev/craft/craftCore.js` (~25 KB, a plain ES module with no imports). It exports exactly the
contract functions: `createCraftContext, loadPrices, loadRecipeTree, priceItem, craftTree, cleanCraftCost, flattenTree`.
Every internal name starts with `cc` / `CC_` so it can't collide with craftModifiers.js or the bundle after inlining.
Only `export function` / `export async function` forms are used. Inline check: strip `^export ` and wrap in
`new Function(body + ';return{...}')`, which works.

## Verified
- **Snapshot check:** fed the research snapshots (`notes/cache/bazaar.json` + `lowestbins.json` + `samples/recipes-compact.json`
  as the bulk file), it reproduces every number in craft-spec.md section 4 exactly. That covers HYPERION 509,326,963 / 550,785,199,
  TERMINATOR, AOTV, JUJU, DIVAN_DRILL, TITANIUM_DRILL_1, REFINED_MITHRIL_PICKAXE, DIVAN_CHESTPLATE and ENDER_DRAGON;4, plus priceItem
  values like HOT_POTATO_BOOK craft 56,323, SAPPHIRE_POWER_SCROLL lbin 1,199,998 and FLINT npc 6.
- **Tree sums:** every tree node's total equals the sum of its children's totals.
- **Whole DB:** 3726 outputs, 3248 priceable (= spec's 478 unpriceable), about 200-250 ms for both modes.
- **Live data (2026-09-21):** per-item NEU fetch, no bulk file.

  | Item | From scratch | Easy | Time, cold |
  |---|---:|---:|---:|
  | HYPERION | 509,437,375 | 550,785,196 | 1.0 s |
  | TERMINATOR | 411,145,353 | 473,864,261 | 1.9 s |
  | DIVAN_DRILL (forge) | 1,572,637,464 | 1,776,999,900 | 3.6 s, 91 NEU fetches |
  | ENCHANTED_DIAMOND_BLOCK | 71,680 | 204,672 | n/a |

  Cached (memory) for all 4 items in a new context: **7 ms**, 0 fetches. A single cached item takes under 5 ms.
- **Abort:** an already-aborted signal makes the call reject with `AbortError`.
- **lowestbins down (HTTP 502):** it falls back to Coflnet `/api/item/price/{ID}/bin`. Malformed recipes and prices never throw.

## Use from the site
```js
const ctx = createCraftContext({
  bazaar: new Map(rows.map(r => [r.id, { instaBuy: r.product.instaBuy, buyOrder: r.product.instaSell }])), // site's $t().byId
  // lowestBin omitted -> loadPrices fetches https://lb.tricked.pro/lowestbins (one ~200 KB request)
});
const res = await cleanCraftCost(tag, ctx, { signal, tier });   // tier only for PET_* tags (row.tier)
// res = { fromScratch: Tree|null, easy: Tree|null, craftable, missing: [ids], truncated, errors: [strings] }
flattenTree(res.fromScratch)  // [{depth,id,qty,unitCost,total,method,note?}] for the hover breakdown
```
- **Reuse one ctx per page (e.g. a module-level variable).** Pricing memo lives on ctx, so rebuild it when the bazaar
  refreshes. The memo resets automatically when you **replace** `ctx.bazaar`, `ctx.lowestBin` or `ctx.recipes` with a new Map.
  If you mutate one of them in place, set `ctx._memo = null`.
- **Load prices once.** `cleanCraftCost` calls `loadPrices` only for fields that are `null`/`undefined`. An empty Map you
  pass in counts as provided.
- `priceItem(id, mode, ctx)` is sync. It accepts NEU ids, bazaar ids (`ENCHANTMENT_SHARPNESS_5`, `LOG:2`) and plain tags.
  Run `loadRecipeTree(id, ctx)` first for ids whose recipes aren't loaded yet. Once the bulk file is loaded, all recipes are in memory.
- `method` values: `buyOrder | instaBuy | lowestBin | craft | forge | npc | coins | unavailable`. Trade and Kat upgrades
  show as `craft` with note `trade` / `Kat pet upgrade`. Notes also carry `recipe makes 9`, `takes 4h` (forge),
  `no buy orders` (from-scratch fell back to insta-buy), and `can't be bought, craft from scratch` (easy-mode fallback). The
  easy fallback node includes its from-scratch subtree as children.
- Quantities can be fractional (`qty = inputQty * parentQty / recipeOutput`), so the sums always match.
- `missing` lists every non-root node id that ended up `unavailable` in either tree. The root of an unpriceable recipe has
  `total:null`, `method:'unavailable'` and note `can't be priced: <first bad id>`, and its children are still shown.
- `craftable:false` (both trees null) means no recipe at all, e.g. CRIMSON_CHESTPLATE, JUDGEMENT_CORE, ENCHANTED_BOOK.

## Recipes: the bulk file is strongly recommended
- `ctx.recipesUrl` defaults to **`'data/recipes.json'`**, which resolves against index.html, so it points to `SITE/app/data/recipes.json`.
  Ship `notes/samples/recipes-compact.json` there as-is (292 KB, 3726 outputs, compact format from craft-spec 2b; the parser also
  accepts `{ID:[Recipe,...]}`).
- Once it loads, it is parsed once per page (module-level promise per url) and `ctx.recipesComplete = true`, so there are **zero
  per-item requests**. Only this file includes NPC-shop routes (FLINT, STRING, ...).
- If it 404s or fails, ctx remembers the failure (`ctx._bulkFailed`) and falls back to per-item NEU JSON
  (`raw.githubusercontent.com/.../items/{encodeURIComponent(ID)}.json`, then the jsDelivr mirror). That is BFS level by level,
  6 concurrent, in-flight dedupe (module `CC_INFLIGHT`), memory cache (`CC_MEM`, shared across contexts) and localStorage
  `ba.craft.r1.{ID}` = `{t,r}` with a 24 h TTL. Everything is in try/catch, and 404 caches `[]`.
- A network failure is **not** cached and gets retried next call; it is reported in `ctx.errors`.
- Per-item mode misses NPC-shop offers, which is why live TERMINATOR from scratch is ~9M higher (no FLINT via NPC).
- Caps: `maxNodes` 400 and `maxDepth` 12 (BFS levels). Hitting either sets `truncated:true`.
- Pricing depth guard: stack 15. `craftTree` stops expanding after 2000 nodes and shows the rest as leaves.
- `recipesUrl: null` means never try the bulk file.
- Passing `recipes` to `createCraftContext` (tests) marks recipes complete: a missing id means no recipe, and there is **no network**.
- Recipe shape stored in ctx: `{type:'craft'|'forge'|'npc'|'other', output, inputs:[{id,qty}], coins, note?, duration?}`.
  Ids are normalised to NEU form: `ENCHANTMENT_X_5 -> X;5` and `LOG:2 -> LOG-2`. SKYBLOCK_COIN goes to `coins`. Other `SKYBLOCK_*`
  inputs stay in, which makes that recipe unusable (pseudo-currency), so the UI can show why.

## Price lookups (id normalisation)
- **bazaar:** tries `id`, then `X-2 -> X:2`, then `X;5 -> ENCHANTMENT_X_5`. Values `<= 0`, non-numeric or null count as missing.
- **lowestBin:** tried in this order: `id`, `ENCHANTED_BOOK-X-5` (for `X;5` or `ENCHANTMENT_X_5`), `PET-X-TIER` (for `X;n`).
  It is **ignored for bazaar products**, because lowestbins lists them at insta-buy.
- **lowestbins down:** `loadPrices` sets `ctx.lowestBinFailed = true` and `ctx.lowestBin = new Map()`, so it does not refetch.
  `cleanCraftCost` then fetches Coflnet `/item/price/{ID}/bin` for up to 20 reachable non-bazaar ids (plain `[A-Z0-9_]` ids only),
  300 ms apart.

## Pricing rules (same as ref-craft.js)
- **fromScratch(id):** strict minimum of `buyOrder` (or `instaBuy` with note `no buy orders` when there are no buy orders),
  `lowestBin`, and every recipe (`coins + sum(price(input) * qty)) / output`, priced recursively). On a tie, the market option wins.
- **Cycle rule:** a recipe that uses an id already on the stack is invalid. Results not touched by a cycle cut are memoized in
  `ctx._memo.scratch`.
- **easy(id):** minimum of `instaBuy`, `lowestBin`, and coin-only NPC shop. Otherwise it uses the from-scratch cost, flagged.
- **Root:** always forced through its cheapest recipe (under that mode's ingredient pricing). If no recipe can be priced, the
  first recipe is shown as unpriceable.

## Re-verified 2026-09-22 (resumed run)
- Against `craft/fixtures/expected.json` (tests agent's independent reference): all 13 roots (both modes) and all 29 `items`
  (unitCost + method, both modes) match exactly; each cached root < 3 ms.
- Live, per-item NEU (no bulk file): HYPERION 506,437,412 / 550,675,482; ENCHANTED_DIAMOND_BLOCK 140,800 / 210,560;
  TERMINATOR 404,505,265 / 474,748,660; DIVAN_DRILL 1,580,159,706 / 1,770,000,000 (93 fetches, 3.3 s cold). All 4 again in a
  fresh ctx: 1.6 ms, 0 fetches. Aborted signal -> AbortError.
- Change: the shared bulk-file promise (`CC_BULK`) no longer takes a caller's signal, so one caller aborting can't fail
  concurrent callers; each caller checks its own signal after awaiting it. Verified with a fake fetch serving
  recipes-compact.json as `data/recipes.json`: aborted HYPERION call rejects, concurrent TERMINATOR resolves (401,869,945),
  0 per-item fetches. Inlining check (strip `^export `, `new Function`) passes.

## Integration pass 2026-09-22 (see craft-engine.md)
- `loadPrices` shares one in-flight download per ctx (`ctx._pricesP`, no signal; callers re-check their own signal).
- `loadRecipeTree` BFS skips malformed recipe entries (null / `inputs:null`).
- `ccParseNeu` trade: `min`/`max` (EMERALD.json) -> cost count = average. The bulk file was regenerated with `craft/buildRecipes.mjs`.

## Review round 1 fixes (2026-09-22, see craft-engine.md "Fixes, review round 1")
- `ccScratch`: buy order < `CC_DEAD_BO` (1%) of insta-buy -> insta-buy, note `buy order too low, using insta-buy`.
- `ccRecipesOf`: drops `note: 'trade'` recipes for enchant-book ids (`ccIsBook`).
- `ccEasy(id, ctx, stack = [])`: craftTree easy passes `[root]`.
- lowestbins: shared download `CC_LBIN_P`; retry after `CC_LBIN_RETRY` (5 min) via `ctx.lowestBinFailedAt` (`ccNeedBins`);
  `ccFillBins` stores misses as 0 and shares in-flight ids (`CC_BIN_INFLIGHT`).
- `cleanCraftCost().errors` = `ccErrorsFor(ctx, tree.ids)`; `ccErrClear` removes errors once the thing loads.
