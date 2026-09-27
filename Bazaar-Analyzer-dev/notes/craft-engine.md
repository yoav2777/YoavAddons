# Craft engine: integrated state and how the site should use it

Files: `craft/craftCore.js` (clean craft cost) + `craft/craftModifiers.js` (one listing's modifiers). Plain ES modules,
no imports, no name collisions (`cc`/`CC_` vs `cm`/`CM_` prefixes, checked). Details per file: `craft-core.md`,
`craft-modifiers.md`; rules: `craft-spec.md`; tests: `craft-tests.md`.

## Status (2026-09-22, after review round 1 engine fixes)
- `"C:/Program Files/nodejs/node.exe" --test "C:/Users/Lenovo/Bazaar-Analyzer-dev/craft/tests/*.test.mjs"` -> **53 pass, 0 fail**
  (~2 s, offline). Use the quoted glob: on Node 24 the directory form `node --test .../craft/tests/` FAILS (it treats
  the folder as one test file). Orchestrator prompts that use the directory form should switch to the glob.
- `node craft/craftSmoke.mjs` (live) runs clean.

## Fixes, review round 1 (engine fixer) -- READ THIS, UI FIXER
Regression tests: `craft/tests/fixes-r1.test.mjs` (one per finding, all fail on the pre-fix engine).
fixtures/reference.mjs + convert.mjs follow the same rules; expected.json regenerated.

1. **Anvil rule (cmEnchantLine, craftModifiers.js).** Non-ultimates are combined only up to `min(5, tableMax[type] + 1)`
   (tableMax = NEU max_xp_table_levels; enchants the table can't roll, e.g. Overload, Vicious, Divine Gift, are never
   combined). Ultimates: any level. The lower book must have a MARKET price (buyOrder / instaBuy / lowestBin); a book
   priced through a NEU recipe (LIFE_STEAL;2 = 6 paper + 2 god apples) never feeds the combine.
   **Easy mode:** the exact book's easy price whenever it can be bought (insta-buy / lowest BIN / NPC). Only when it
   can't: the cheaper of 2^(L-l) lower books that CAN be bought (method `instaBuy+anvil`) and the book's own from-scratch
   fallback; the note then starts with `can't be bought`. So 'Buy order + anvil' in easy mode is gone unless the book is
   unbuyable. Live Terminator (sold row 59cb7e...): from scratch 1,074,988,257 -> 1,143,718,292, easy -> 1,347,660,957.
2. **Dead buy orders (ccScratch).** A top buy order below 1% of insta-buy (`CC_DEAD_BO = 0.01`) is ignored: from scratch
   uses insta-buy, method `instaBuy`, note `buy order too low, using insta-buy`. (Duplex 5: 2.8 vs 23.37M.) 1% keeps
   real-but-low orders like Luck 6 (3.3%) and Looting 4 (9.9%).
3. **Sold-row reforge (cmReforge).** Colour codes (`§6`) are stripped before parsing; `Shiny ` is dropped when
   `is_shiny` is set; if the prefix still isn't a stone reforge, its longest trailing part that is one is used
   ("Shiny Withered" -> Withered: WITHER_BLOOD + 60k MYTHIC apply). `§6Terminator` -> no reforge line.
4. **Easy fallback + root (ccEasy).** `ccEasy(id, ctx, stack)`; craftTree easy passes `[root]`, so a non-buyable
   ingredient's from-scratch fallback can't loop back through the root. Root total now always = sum of its children.
5. Test command: see Status (doc only).
6. **NEU trades for enchant books ignored** (ccRecipesOf): `trade` recipes of an id `X;N` that is a book (bazaar
   ENCHANTMENT_X_N or lowestbins ENCHANTED_BOOK-X-N) are dropped. Ender Slayer 6 = its 250k buy order (was 22,400),
   Dragon Hunter 1 = market / NPC 1M. Trades of other items are unchanged. Needs `note: 'trade'` on the recipe, which
   both parsers set (bulk `t` entries and per-item NEU).
7. **lowestbins outage.** (a) One lowestbins download is shared by all contexts (`CC_LBIN_P`). (b) After a failure the
   engine itself retries lowestbins at most every 5 min (`ctx.lowestBinFailedAt`, `CC_LBIN_RETRY`); the spread in
   BA_craftCtxFor carries `lowestBinFailedAt` along, so no UI change is REQUIRED. (c) ccFillBins asks Coflnet once per id:
   misses are stored as 0 in the same lowestBin Map (ccNum ignores 0), requests in flight are shared (`CC_BIN_INFLIGHT`).
   Measured in the test: 2 callers x 4 site-style polls -> Coflnet count unchanged after poll 0, lowestbins asked once.
   The UI may still reset `lowestBin: null, lowestBinFailed: false` on Refresh as it does now (fine).
8. **Errors.** `cleanCraftCost(...).errors` now lists only errors about THIS item: `Recipe X: ...` only if X is in the
   item's tree, `Lowest BIN list: ...` only while `ctx.lowestBinFailed`, `Bazaar prices: ...` only while there is
   no bazaar. Errors are also removed from ctx.errors when the thing works later (recipe loaded, list loaded).
   `loadModifierData` no longer caches a failed Hypixel items request on ctx (`ctx.modData` is set only when items
   loaded); it retries at most once a minute (module-level). UI: BA_CraftWarnings can keep using `res.errors`; if
   BA_craftRefresh wants to force a retry it can also reset `modData: null` (optional).
9. Same fix as 1 (easy anvil).
10. **Pets / items without a recipe (listingCraftCost).** When `craftable` is false, the base is the clean item bought
   at market: `base[mode] = { id: baseId, qty: 1, unitCost, total, method: 'lowestBin'|..., note: 'no recipe for this pet
   tier, clean pet bought' | 'no recipe, clean item bought' }` (a leaf node, no children) and it IS in the totals.
   **Contract additions** on the listingCraftCost result: `baseSource: 'recipe' | 'market' | null` (null = no recipe and no
   market price: base null, totals = modifiers only, `"<baseId> (no recipe, no market price)"` in missing) and
   `notes: string[]` (pets: `'pet exp / level not priced'`). `craftable` keeps its meaning (has a recipe).
   **UI fixer:** parts/20-listing-craft.js keys "mods only, no recipe" labels on `res.craftable === false` (lines ~126,
   164-173, 257). Switch those to `res.baseSource == null`; when `baseSource === 'market'` show the base row as
   "Clean item (no recipe): bought at lowest BIN" and treat the totals as full craft costs (the % vs listing is valid).
   Show `res.notes` somewhere small. PET_ENDER_DRAGON Epic Lvl 80: totals 0.5M -> 143.09M.
- Inlining check: `craftCore.js + "\n" + craftModifiers.js` through the exact `inlineEngine()` regex of
  `patch/patch_v12.mjs` gives `const BA_CE=(()=>{...})()` with 12 names and passes `node --check`.
  (Done: patch_v12.mjs inlines craft/craftCore.js + craft/craftModifiers.js directly, in this order, as `BA_craft`.)
- `SITE/app/data/recipes.json` has been shipped (copy of `notes/samples/recipes-compact.json`, 292 KB, 3726 outputs).
  serve.ps1 already serves `.json`. Regenerate with `craft/buildRecipes.mjs` (below).

### Fixes made in this pass
1. **Engine bug, trade counts:** NEU `EMERALD.json` has 27 `trade` entries with `min`/`max` and no count in `cost`
   (`{"cost":"LOG-1","min":65,"max":76,"result":"EMERALD"}` = 65-76 logs per emerald). Every parser took them as
   1 log -> 1 emerald, so EMERALD came out at 0.1 coins from scratch, and everything bought with (enchanted) emeralds
   became nearly free (Dragon Hunter I 800 coins, Wood Singularity 96). Now the count = average of min/max, in
   `ccParseNeu` (per-item path), the new generator, `fixtures/record.mjs` and `notes/ref-craft.js`. Only EMERALD
   changed in the recipe file. Fixtures and expected.json were re-recorded, and one expected line changed
   (synthetic-extras Wood Singularity 96 -> 1,824). New regression test in fetch.test.mjs.
2. **Engine bug, test was right:** `loadRecipeTree` BFS crashed on preloaded recipe lists containing `null` / `inputs:null`.
   It now skips malformed entries like `ccRecipesOf` does.
3. **Engine bug, test was right:** concurrent `cleanCraftCost` calls on one ctx each downloaded bazaar and lowestbins.
   Now `loadPrices` shares one in-flight promise (`ctx._pricesP`). It runs without a signal, so one caller aborting can't
   fail the others, and each caller re-checks its own signal.
4. Dedup: `CM_PET_TIERS = CM_TIERS.slice(0, 6)`. The rest of the overlap is intentional: two 1-line abort helpers, and the
   pet-tier id logic in `listingCraftCost`, which only feeds `baseId`. The contract keeps the files decoupled: craftModifiers
   gets craftCore's functions passed in as `core` / `priceItem`.
5. New `craft/buildRecipes.mjs`: a reproducible generator for the recipe file. It reproduces the old
   recipes-compact.json byte-for-byte except EMERALD.
   `node craft/buildRecipes.mjs [NEU_REPO_DIR] [OUT]` (default `notes/cache/neurepo/repo` -> `notes/samples/recipes-compact.json`).

## Public API (what the site uses)
```js
// once per page (module-level), rebuilt/refreshed when the bazaar data changes
const ctx = BA_CE.createCraftContext({ bazaar /* Map */, /* lowestBin omitted -> engine loads it */ });
// clean craft price of an AH tag (price-graph view)
const res = await BA_CE.cleanCraftCost(tag, ctx, { signal, tier /* only for PET_* */ });
//   -> { fromScratch: Tree|null, easy: Tree|null, craftable, missing: string[], truncated, errors: string[] }
// one listing (auction page / lowest-BIN row / sold row)
const core = { loadRecipeTree: BA_CE.loadRecipeTree, cleanCraftCost: BA_CE.cleanCraftCost, priceItem: BA_CE.priceItem };
const r = await BA_CE.listingCraftCost(tag, listingRow, ctx, core, { signal });
//   -> { baseId, craftable, base:{fromScratch,easy} (Trees), modifiers:{fromScratch,easy} (lines),
//        totals:{fromScratch,easy} (number|null), missing: string[] }
BA_CE.flattenTree(tree)   // [{depth,id,qty,unitCost,total,method,note?}] for the hover breakdown
```
- Tree node: `{ id, qty, unitCost, total, method, note?, children? }`. `method` is one of
  `buyOrder | instaBuy | lowestBin | craft | forge | npc | coins | unavailable`. Trades and Kat upgrades show as
  `craft` with note `trade` / `Kat pet upgrade`. Coins are `{id:'SKYBLOCK_COIN', method:'coins'}`. `qty` can be fractional
  when a recipe makes >1.
- Modifier line: `{ kind, label, id, qty, unitCost, total, method, note? }`. Extra enchant methods are `<m>+anvil`,
  `table` (0 coins, XP only) and `noMarket` (0, counted as 0). Lines with `kind:'unknown'` are unpriced info lines.
- `totals[mode] == null` means a recipe exists but can't be priced; show "can't be priced" + `missing`, never 0.
  `craftable:false` means no recipe (e.g. CRIMSON_CHESTPLATE): the listing total is the modifiers only, so label it that way.
- Nothing throws on bad data. Only an abort rejects (`AbortError`), so ignore that error in effects.
- Everything after loading is sync and memoized on ctx. Replace `ctx.bazaar` / `ctx.lowestBin` with a NEW Map to
  invalidate. If you mutate in place, set `ctx._memo = null`.

## Which existing site data to pass (see bundle-map.md)
| Engine input | Site source | Mapping |
|---|---|---|
| `bazaar` | `$t().byId` (Map id -> row) or `qc.fetchQuery({queryKey:['bazaar'],...})` | `new Map([...byId].map(([id,r]) => [id, { instaBuy: r.product.instaBuy, buyOrder: r.product.instaSell }]))`. The site's `instaSell` = top buy order = the engine's `buyOrder`. Build a new Map (and so a new memo) when the bazaar query data changes (30 s poll), not per render: `useMemo` on `data`. |
| `lowestBin` | none (the site doesn't have it) | leave it out, and the engine fetches `https://lb.tricked.pro/lowestbins` once per ctx (~6.5k keys). If that fails, it falls back to Coflnet `/item/price/{ID}/bin` for up to 20 ids, 300 ms apart. |
| recipes | `data/recipes.json` (shipped) | automatic (`ctx.recipesUrl` default, relative to index.html). |
| `ctx.hyItems` (items API with `upgrade_costs`, `gemstone_slots`, `dungeon_item_conversion_cost`, `tier`, `name`) | The site's `Da()` fetches `mt` (items API) but keeps only a slim 24 h localStorage copy, without these fields | Don't patch Da (the raw JSON is 5 MB, too big for localStorage). Let `loadModifierData` fetch it once per page, lazily, on the first listing breakdown (~0.4-0.5 s; the server sends `last-modified`, so the browser revalidates). |
| listing row | active BIN rows (`/auctions/tag/{TAG}/active/bin`), auction detail `Ei(uuid)` (`r` in `ja`), sold rows (`xi`) | Pass the Coflnet row as-is. It accepts `flatNbt` / `flattenedNbt` / `nbtData.data`, `reforge` or `itemName`, `tier`, `enchantments`. BA_LowestBin's mapped rows (`{...a, flattenedNbt: a.flatNbt}`) work too. |
| `tag` | page tag (`e` in the AH item page) | pets: pass the `PET_X` tag. `listingCraftCost` derives the tier from the row. For `cleanCraftCost` on a pet page, pass `{tier}`, or it has no recipe. |

Wrap every craft UI in the `BA_Boundary` from bundle-map section 5. Compute listing costs only on hover or for visible rows, never for
all 500 sold rows up front (it is cheap once loaded, but the first call triggers the downloads).

## Request volume per page view (measured with live data)
Bulk-file path (the shipped default), bazaar passed from the site:
| When | Requests | Size | Time |
|---|---|---|---|
| first `cleanCraftCost` on the page | 2: `lb.tricked.pro/lowestbins` + `data/recipes.json` | ~200 KB + 292 KB (~50 KB gzip) | ~20 ms compute after download; downloads ~0.2-0.35 s |
| first `listingCraftCost` | +1: Hypixel items API (unless `ctx.hyItems`) | 5 MB (browser revalidates, 304 later) | ~0.45-0.55 s |
| every further item / listing on the page | 0 | - | cached HYPERION 0.15 ms, TERMINATOR 0.14 ms, 10 real HYPERION listings 4.6 ms total |
| if the site does NOT pass `bazaar` | +1 bazaar | 3.6 MB | shared by concurrent callers now |

Per-item NEU fallback (only when data/recipes.json 404s): HYPERION 6 requests / 0.9 s cold, TERMINATOR +38 / 1.8 s,
DIVAN_DRILL ~93 / 3.3 s. Cached afterwards in memory and localStorage (`ba.craft.r1.{ID}`, 24 h). This path misses
NPC-shop routes, so from-scratch costs come out slightly higher.
Coflnet is never called on the normal path (only the lowestbins-down fallback, max 20 calls).

## Live smoke output (`node craft/craftSmoke.mjs HYPERION TERMINATOR DIVAN_DRILL ENCHANTED_DIAMOND_BLOCK`, 2026-09-22)
| Item | From scratch | Easy | Market |
|---|---:|---:|---:|
| HYPERION | 509,437,423 | 550,675,478 | lbin 504,000,000 |
| TERMINATOR | 392,398,655 | 475,698,657 | lbin 460M (from-scratch craft is cheaper) |
| DIVAN_DRILL (forge) | 1,581,927,016 | 1,770,000,000 | lbin 1.885B |
| ENCHANTED_DIAMOND_BLOCK | 145,920 | 210,464 | Coflnet craftCost 176,856, between our two |
| Live Heroic 5-star Hyperion listing (listed 542.5M) | 532,944,202 | 593,966,980 | |
4 engine requests, 1.27 s total including the 5 MB items API. Full tree printing: `--depth N`. Per-item path: `--neu`.

### Cross-check with Coflnet `/api/craft/profit` (884 overlapping items)
811 fall between our from-scratch and easy values, 26 are below from-scratch, 47 above easy.
Coflnet doesn't list HYPERION, TERMINATOR or DIVAN_DRILL (it only lists profitable 3x3 crafts). The gaps are expected:
- **Below ours:** Coflnet uses NPC buy prices we don't have, or cheaper ones. ENCHANTED_OBSIDIAN is 2,240 there (160 OBSIDIAN
  from an NPC at 14) vs our 3,456; our NEU data only has OBSIDIAN at 50 (Pearl Dealer). ENCHANTED_DANDELION: YELLOW_FLOWER from an NPC at 50,
  which isn't in the NEU repo. Also snapshot age (its rows are minutes old).
- **Above ours:** Coflnet walks the insta-buy order book for the full quantity (`instaBuyCapacity`), so big quantities cost
  more than the top price. It also ignores cheaper NPC/lowest-BIN routes we use (ENCHANTED_BREAD 274 vs our 140 via NPC
  wheat at 7 per 3; CENTURY_ARTIFACT via lowest-BIN tokens).

## Known limitations
- **Top-of-book prices only:** there is no order-book depth. Big quantities (128 Tarantula Silk, 3,350 Wither essence) are priced at
  the top order.
- **Thin markets:** from-scratch uses the top buy order unless it is below 1% of insta-buy (then insta-buy, noted). Low
  but real orders between 1% and 100% are still used as-is (Luck 6 10k vs 306k insta-buy).
- **NEU trade data:** trades for enchant books are ignored (review round 1 fix 6).
- **Anvil rule is a heuristic:** combine only up to min(V, table max + 1) for table enchants, any level for ultimates.
  Enchants the table can't roll are never combined (may overprice a few that really combine, e.g. Overload IV+IV).
  The Bazaar **Book Flips** tab no longer relies on this alone: `BA_bookAnvilMax` in `patch/parts/40-book-flips.js`
  adds hand-kept caps for 28 non-table books (Feast, Ice Cold, the Turbo books, Dedication III, ...) derived from
  the NEU `Source:` lore — see notes/book-flips.md. Craft prices still use the engine rule on its own, so enchant
  craft costs are unchanged; moving that map into the engine would change them and needs the craft tests re-run.
- **Enchants with no book price:** these are 0. Enchanting-table levels show `table` (0 coins, XP only). Everything else
  (Knockback, Impaling, Magmarizer...) shows `noMarket`, counted as 0 with a note. The anvil rule only combines the highest
  market-priced lower level (spec).
- **Not priced:** pet level/exp (a pet tier without a recipe is its lowest BIN, which is usually a low-level pet), candy, attributes (Kuudra), soulbound/untradeable ingredients (`unavailable`, listed in
  `missing`), and unrecognized NBT keys (`unknown` lines). Blacksmith reforges (no stone) are free.
- **Recipe data freshness:** recipes come from the NEU repo snapshot (commit a332242, 2026-09-21). Regenerate with buildRecipes.mjs
  when recipes change. The per-item fallback always reads NEU master.
- **Staleness:** lowestbins is cached 30 s by its server. The bazaar is whatever the site last polled. Recipes are in the file.
