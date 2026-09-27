# Craft engine test suite (craft/tests, craft/fixtures, craft/craftSmoke.mjs)

## Run
```
"C:/Program Files/nodejs/node.exe" --test "C:/Users/Lenovo/Bazaar-Analyzer-dev/craft/tests/*.test.mjs"
```
Use the quoted glob. Node 24.19 does NOT accept a directory argument: `node --test .../craft/tests/` fails with
one test called "...\craft\tests". The suite takes about 1.6 s and needs no network. If craftCore.js or
craftModifiers.js is missing, every test is skipped.

Live smoke run (network: Hypixel bazaar, lb.tricked.pro, NEU/jsDelivr, Hypixel items, 2 Coflnet calls cached 10 min
in `notes/cache/smoke_*.json`):
```
"C:/Program Files/nodejs/node.exe" C:/Users/Lenovo/Bazaar-Analyzer-dev/craft/craftSmoke.mjs [TAG ...] [--neu] [--depth N]
```
Defaults to HYPERION and TERMINATOR, then prices one real live HYPERION BIN listing (the most upgraded of Coflnet's 10
cheapest). It reads recipes from `SITE/app/data/recipes.json` when that exists, else `notes/samples/recipes-compact.json`,
served as `data/recipes.json` the same way the browser gets it. `--neu` switches to the per-item NEU path.

## Fixtures (craft/fixtures): deterministic, recorded 2026-09-21
Source snapshots: bazaar 18:48 UTC, lb.tricked.pro lowestbins 18:52 UTC, Hypixel items, NEU repo zip @ a332242, plus
real Coflnet rows. `record.mjs` rebuilds them from `notes/cache` + `notes/samples`. `reference.mjs` rebuilds `expected.json`.
```
node craft/fixtures/record.mjs && node craft/fixtures/reference.mjs
```
| File | Shape |
|---|---|
| `bazaar.json` | raw Hypixel `/v2/skyblock/bazaar` `{success,lastUpdated,products:{ID:{product_id,sell_summary[0..1],buy_summary[0..1],quick_status}}}`, trimmed to the needed ids + every level of every listed enchant |
| `lowestbins.json` | raw lb.tricked.pro `{KEY: price}` (incl. `ENCHANTED_BOOK-X-L`, `PET-ENDER_DRAGON-*`, `RUNE-*`, and 3 bazaar ids it also lists) |
| `recipes.json` | bulk compact recipe file (craft-spec 2b), 13 KB, all recipes reachable from the roots + modifier ids |
| `neu-items.json` | `{ID: raw NEU item JSON}` (271 files, slimmed to recipe fields; NPC files keep only the needed npc_shop offers) for the per-item loader |
| `hypixel-items.json` | raw Hypixel items resource `{success,items:[...]}` trimmed (upgrade_costs, gemstone_slots, dungeon_item_conversion_cost, tier...) |
| `constants-reforgestones.json`, `constants-enchants.json` | NEU constants (enchants has `max_xp_table_levels`) |
| `craft-profit.json` | Coflnet `/api/craft/profit` rows for the needed ids (sanity only) |
| `listings/*.json` | 10 Coflnet rows: hyperion-full (active, 25 enchants, 10 stars, scrolls, gems), hyperion-sold (sold row: `flattenedNbt`, no `reforge`), terminator-full, terminator-sold-aow, divan-chestplate-gems (5 perfect gems), divan-drill (parts, Glacial, tier UNKNOWN), necron-chestplate, crimson-chestplate (no recipe), pet-ender-dragon (EPIC, held item), synthetic-extras (terminator + rune, dye, wood singularity, ethermerge) |
| `convert.mjs` | fixture to contract Maps: `toContractRecipes`, `toContractBazaar`, `toContractLowestBin` |
| `refmods.mjs` + `reference.mjs` | the independent reference (spec 3, 5, 5a, 6). It does not import the engine. |
| `expected.json` | `roots{ID:{fromScratch,easy,lowestBin}}` (chosen recipe + children), `items{ID:{fromScratch,easy}}`, `listings{key:{rootId,base,marketBase,lines,modifierSum,total}}`, `easyNpcDiff` |

Roots: HYPERION, TERMINATOR, ASPECT_OF_THE_VOID, JUJU_SHORTBOW, DIVAN_DRILL and TITANIUM_DRILL_1 (forge), ENCHANTED_DIAMOND_BLOCK
(recipe makes 9 / cycles), REFINED_MITHRIL_PICKAXE (NPC shop with items), TARANTULA_SILK (FLINT via NPC), DIVAN_CHESTPLATE,
POWER_WITHER_CHESTPLATE, CRIMSON_CHESTPLATE (no recipe), ENDER_DRAGON;4 (Kat upgrade). The reference reproduces every
number in craft-spec section 4 and 4b. The easy side of 4b needs the anvil rule in both modes, which reference.mjs does.

## Tests (42)
- `tests/fx.mjs`: helper, not a test. It loads the fixtures, gives a fresh engine module per test (`import(url + '?fresh=N')`
  resets CC_MEM / CC_BULK / CC_INFLIGHT / CM_DATA), provides `fixtureCtx(core)` (offline ctx), and `fakeFetch(opts)`. fakeFetch routes
  the bazaar, lowestbins, `data/recipes.json`, NEU raw and jsDelivr, Coflnet `/item/price/X/bin` and Hypixel items URLs to
  the fixtures. It records `calls` and `maxInFlight`, and accepts `opts.fail = {bazaar|lbin|bulk|neu|cofl|items: status, network: url => bool}`
  and `opts.delay`. `memoryStorage()` is a stand-in for localStorage.
- `core.test.mjs` (22 tests):
  - Every root's total and root method in both modes, plus each direct child's qty, total and method, compared with the reference.
  - fromScratch <= easy. The root is forced to craft even when the lbin is cheaper.
  - Every node satisfies total = unitCost*qty = sum of its children. The easy tree never sub-crafts buyable ingredients.
  - priceItem at 29 key ids. NPC coin shops count in easy mode (15 ids). Id forms are equivalent. lbin is ignored for bazaar ids. Vanilla cycles are handled.
  - Hand-computed synthetic graphs: output count > 1, memo vs. cycle-cut, pure cycles, coins child, pseudo-currencies, missing leaf,
    easy fallback flag, thin books, junk prices, weird ids, malformed recipes, flattenTree, memo reset, and pricing the whole set in under 2 s.
  - Tree `method` has no 'other': Kat upgrade and trade show as `craft` with a note, so the tests map the reference's 'other' to 'craft'.
- `fetch.test.mjs` (12 tests):
  - Bulk path: 3 requests total, and the bulk file is reused by a second ctx.
  - Concurrent calls on one ctx share the price loading.
  - Per-item NEU path: no duplicate requests, only reachable ids (checked with my own BFS over neu-items), memory cache.
  - Per-item caps: in flight <= 6, maxNodes, maxDepth (DIVAN_DRILL at maxDepth 1 fetches exactly 3 files).
  - Failures: a NEU network failure is reported and retried while a 404 is cached; bulk 404 falls back to per-item; lowestbins 502 falls back to Coflnet (<= 20 calls, no bazaar ids, each once); bazaar 500.
  - loadPrices field mapping for every product. Abort before and during a run. localStorage cache, including a throwing storage.
- `modifiers.test.mjs` (8 tests):
  - listModifiers parts and coins equal the reference multiset for all 10 listings, and the spec's key cases are present.
  - Unknown keys come back as `unknown`. modifierItemIds contents.
  - priceModifiers: every non-enchant line matches the reference id, qty, total and method. Enchant lines are checked as upper bounds, because the engine may use the enchanting table (0, XP only) where the reference buys the book.
  - listingCraftCost: base = clean craft, and the total lies between base + non-enchant and base + non-enchant + reference enchants. fromScratch <= easy.
  - 18 odd or empty items never throw, including with a throwing priceItem, no priceItem, or no core.

## Results
Run on 2026-09-22 against the craftCore.js (01:26) and craftModifiers.js (01:29) on disk: **40 pass, 2 fail**.
1. `malformed preloaded recipes never throw`: `cleanCraftCost('BAD')` on a ctx whose preloaded `recipes` Map holds a `null`
   entry (or `inputs: null`) throws `TypeError: Cannot read properties of null (reading 'inputs')`. The throw is in the
   `loadRecipeTree` BFS worker (`for (const r of rs) for (const inp of r.inputs)`). `ccRecipesOf` filters bad entries, but the loader
   doesn't. The sync API (priceItem / craftTree) handles the same data fine. Only reachable through preloaded recipes, since parsed
   recipes are always well-formed, but the contract says nothing throws on odd data.
2. `concurrent cleanCraftCost calls on one ctx share the price requests`: two concurrent `cleanCraftCost` calls on a
   fresh ctx with no prices each call `loadPrices`. The bazaar (3.6 MB) and lowestbins are then downloaded twice. The fix is an
   in-flight promise on ctx. It doesn't matter if the site always passes `bazaar` from its own query (`$t().byId`).
Everything else matches the reference exactly. Listing totals equal the spec numbers, for example hyperion-full
1,546,642,485 / 1,661,235,894.

Live smoke run on 2026-09-22 (bulk recipes file, 4 engine requests, 1.3 s):

| Item | From scratch | Easy |
|---|---:|---:|
| HYPERION | 505,437,414 | 550,675,475 |
| TERMINATOR | 391,937,919 | 474,748,684 |
| Live Heroic 5-star HYPERION listing (listed 542.5M) | 528,585,386 | 593,968,332 |

Coflnet's `/api/craft/profit` doesn't list HYPERION or TERMINATOR. Cross-checking all of its 891 overlapping items against ours:
816 fall between fromScratch and easy, 26 below fromScratch and 49 above easy (differences expected from its insta-buy-only pricing and staleness).
Oddity seen live, for the modifier owner: in easy mode Dragon Hunter 5 is priced as 16 x DRAGON_HUNTER_1 bought from an NPC plus anvil, which comes to 16,000,000 coins.

## Update 2026-09-22 (integration pass, see craft-engine.md)
Both failures above were engine bugs and are fixed in craftCore.js; the tests were not changed. The fixtures were re-recorded after
the EMERALD trade min/max fix (record.mjs + reference.mjs; only expected synthetic-extras Wood Singularity changed).
New test in fetch.test.mjs: trade min/max = average count on the bulk and per-item paths. Now **43 pass, 0 fail**.

## Update 2026-09-22 (review round 1 engine fixes)
- New `tests/fixes-r1.test.mjs`: 10 regression tests (anvil cap, recipe-priced lower books, easy exact book, dead buy
  orders, Shiny / colour-code reforge, easy fallback loop, book trades, lowestbins outage request counts, per-item errors +
  modData not cached on failure, pet market base). Suite: **53 pass**.
- `fixtures/convert.mjs` now keeps `note: 'trade' | 'Kat pet upgrade'` on contract recipes (like the engine's bulk parser).
- `fixtures/reference.mjs` follows the new rules (dead buy order 1%, book trades ignored, anvil cap + market-only lower
  books, easy exact book first, easy fallback with the root on the stack). Regenerate expected.json with
  `"C:/Program Files/nodejs/node.exe" craft/fixtures/reference.mjs`.
- Always run with the quoted glob; the directory form fails on Node 24.
