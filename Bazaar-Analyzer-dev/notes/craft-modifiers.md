# craftModifiers.js (engine part 2): listing modifiers

File: `Bazaar-Analyzer-dev/craft/craftModifiers.js` (~34 KB). Plain ES module, no imports. Internal names use the `cm` / `CM_` prefix, so there are no collisions with craftCore (`cc` / `CC_`). Inline check: concatenating `craftCore.js` and then `craftModifiers.js` with `^export ` stripped, wrapped in `new Function(...)`, parses.

## Exports
- `listModifiers(item, data?)` returns `[{ kind, label, parts:[{id,qty}], coins?, note?, enchant? }]`. It is a pure parse.
  - `data` is the `loadModifierData` result. It is needed for star costs, gem-slot costs, dungeon conversion and the tier fallback. Without it, the module-level data from the last load is used, or else the lines carry a "item data not loaded" note.
  - Kinds: `enchant, stars, masterStar, dungeonize, recomb, potatoBook, fumingBook, upgrade, abilityScroll, powerScroll, gemSlot, gem, reforge, drillPart, dye, rune, petItem, petSkin, skin, unknown`.
- `modifierItemIds(item, data?)` returns the part ids, excluding `ENCHANTMENT_*`, `RUNE-*` and coins. These are the ids whose recipes may need loading.
- `priceModifiers(item, mode, ctx, priceItem)` returns `[{ kind, label, id, qty, unitCost, total, method, note? }]`.
  - One line per part. A coins part becomes `{id:'SKYBLOCK_COIN', method:'coins'}`.
  - It never throws. Unknown or unpriceable parts get `total:null` and `method:'unavailable'`.
  - A blacksmith reforge (no stone) produces no line.
  - Extra enchant methods: `<method>+anvil`, `table` (0 coins, XP only) and `noMarket` (0 coins, counted as 0).
- `async loadModifierData(ctx, {signal})` builds and returns `ctx.modData = { items: Map<id, hypixelItem>, stones, tableMax, note? }`.
  - The Hypixel items API is fetched once per page with `ctx.fetchFn || fetch`, with an in-flight promise and a module cache.
  - Optional overrides: `ctx.hyItems` (the items API JSON, an items array or a Map). **The site already fetches this API (const `mt` in the bundle), so pass it in to avoid a second 5 MB request.** Also `ctx.reforgeStones` (NEU reforgestones.json) and `ctx.enchantTable` (NEU enchants.json).
  - If the fetch fails, it returns empty items plus a `note`, and the note ends up in `missing`. It rejects only on abort.
- `async listingCraftCost(tag, item, ctx, core, {signal})` returns `{ baseId, craftable, base:{fromScratch,easy}, modifiers:{fromScratch,easy}, totals:{fromScratch,easy}, missing }`.
  - `core = { loadRecipeTree, cleanCraftCost, priceItem }`.
  - `base` holds the craftCore Trees, or `null` when there is no recipe. In that case `craftable:false` and the totals are **modifiers only**, the spec's rule (Crimson 4,022,692 / 4,647,305).
  - `totals[mode]` is `null` when a recipe exists but can't be priced.
  - Pets: tag `PET_X` plus the base tier (petInfo `tier`, or the row tier minus 1 when `PET_ITEM_TIER_BOOST` is held) is passed as `{tier}` to `cleanCraftCost`. `baseId` is then `X;n`.
  - `missing` = craftCore `missing` + ids/labels without a price + the item-data note.

## Input shapes accepted
- Active BIN or auction detail: `flatNbt` + `reforge` + `tier` + `enchantments[{type,level}]`.
- Sold row: `flattenedNbt`, with no `reforge`. The reforge is taken from `itemName` as the words before the Hypixel item `name`, so `hy` is needed.
- Raw `nbtData.data` only: typed NBT, flattened here. `gems{}` becomes slot keys, `runes{}` becomes `RUNE_*`, `petInfo` (JSON string or object) is flattened, and arrays become space-joined strings.
- `enchantments` may also be an object `{name: lvl}`.

## Rules (craft-spec section 5, identical to notes/ref-listing.js and craft/fixtures/reference.mjs)
- **Enchant (changed in review round 1, see craft-engine.md fix 1):** from scratch = the cheaper of the book itself and anvil-combining 2^(L-l) copies of the highest lower level with a MARKET price (buyOrder/instaBuy/lowestBin). Combining is allowed for ultimates at any level, for other enchants only up to `min(5, tableMax[type] + 1)` and never when the table can't roll the enchant (`cmCanAnvil`). Easy = the book's own easy price when it can be bought; only when it can't, the cheaper of an anvil from buyable lower books and the own from-scratch fallback, note starting `can't be bought`.
  - Neither available: `table` if level <= the NEU `max_xp_table_levels`, else `noMarket` (0).
  - `champion/compact/cultivating/expertise/hecatomb/toxophilite` above level 1 are priced as the level-1 book.
  - Efficiency 6+ = (lvl-5) x `SIL_EX`.
  - Aliases: `ultimate_duplex→ULTIMATE_REITERATE`, `dragon_tracer→AIMING`, `turbo_cocoa→TURBO_COCO`, `turbo_cacti→TURBO_CACTUS`.
  - The anvil uses only the highest priced lower level (a `break`), not every level. Taking the minimum over all levels would be cheaper (e.g. Ultimate Wise 5 = 4 x UW3 100k vs 2 x UW4 360k), but the spec and the test reference use the highest level. If that changes, remove the `break` in `cmEnchantLine` and in `fixtures/reference.mjs` at the same time.
- **Reforge from a sold row's itemName:** `cmClean` strips `§x` colour codes and symbols; a leading `Shiny ` is dropped when `is_shiny` is set; a multi-word prefix that is not a stone reforge falls back to its longest trailing part that is one.
- **No recipe (craftable false):** listingCraftCost prices the clean item at market (`baseSource: 'market'`), result also has `notes` (pets: exp/level not priced).
- **loadModifierData:** `ctx.modData` is only cached when the Hypixel items request worked; failures are retried at most once a minute.
- **Stars:** `upgrade_level`, falling back to `dungeon_item_level`. Costs are the items API `upgrade_costs[0..n)` summed per essence, item and coins. Dungeon conversion essence (`dungeon_item`=1 plus `dungeon_item_conversion_cost`) is merged into the same line. Above the list length, when there are exactly 5 entries, master stars `FIRST..FIFTH_MASTER_STAR` are added.
- **Books and upgrades:**
  - `hpc` / `hot_potato_count`: HPB x min(n,10) plus fuming x min(n-10,5).
  - `rarity_upgrades`: recomb.
  - Counted keys: `art_of_war_count, wood_singularity_count, farming_for_dummies_count, tuned_transmission, polarvoid, bookworm_books, jalapeno_count, mana_disintegrator_count`.
  - Flags (1 each): `artOfPeaceApplied, stats_book, divan_powder_coating`.
  - `ethermerge`: `ETHERWARP_MERGER` + `ETHERWARP_CONDUIT`.
- **Scrolls:** `ability_scroll` (space- or comma-separated) and `power_ability_scroll`. The power scroll goes through priceItem, so it resolves to lbin or craft, never bazaar.
- **Gem slots and gems:**
  - `unlocked_slots` `TYPE_N` is the N-th `gemstone_slots` entry of that type. Its costs are coins and items; slots with no costs are free.
  - A slot missing from the API (seen: Necron `SAPPHIRE_0`, `DEFENSIVE_0`) gets an `unavailable` line.
  - Gems: `SLOT_N: QUALITY` (+ `SLOT_N_gem` type) becomes `QUALITY_TYPE_GEM`.
- **Reforge:**
  - The stone is looked up in `CM_STONES` (a compact copy of the NEU reforgestones, commit a332242) by normalized reforgeName. If that fails, it is looked up by stone id (Coflnet sends `aote_stone` for Warped).
  - The apply cost uses the tier: the row tier, or when it is `UNKNOWN`, the items API tier +1 if recombobulated.
  - Not a stone reforge (Heroic, Hasty, ...): free, no line.
- **Other parts:**
  - Drill parts: `drill_part_engine`/`fuel_tank`/`upgrade_module` (uppercased).
  - `dye_item`.
  - Runes: `RUNE_X:"3"` becomes `RUNE-X-3`, which priceItem looks up as a lowestbins key.
  - Pets: `heldItem`; `skin` becomes `PET_SKIN_X` (unverified).
  - Non-pet `skin` uses the id as-is (unverified).
  - `exp` and `candyUsed` are ignored.
- **Unknown flat keys:** any key that is not ignored and not in the tables gets an `unknown` line (not priced, not in `missing`). Across 299 cached real rows no `unknown` line appeared.

## Verified
- The harness in the scratch folder (not shipped) fed the fixtures to craftCore + craftModifiers, with priceItem taken from craftCore. It covers the 10 listings in `craft/fixtures/listings/`.
- **Totals and lines:** all 20 totals (fromScratch and easy) equal `craft/fixtures/expected.json` (the tests agent's reference) to under 1 coin. That includes the spec's numbers: Hyperion 1,546,642,485 / 1,661,235,894, Divan drill 1,902,359,494 / 2,240,919,174 and Terminator 724,709,852 / 827,560,627. Per-line id/qty/total also match, with one exception: dungeon conversion essence now shares the stars line, the same as the reference.
- **Built-in tables:** they give the same results as the NEU constants, so `ctx.enchantTable` and `ctx.reforgeStones` are optional.
- **Odd inputs** (null, numbers, strings, `{}`, bad enchant entries, a bad petInfo JSON, no priceItem, no core, a failing items fetch): nothing throws. An aborted signal rejects with `AbortError`.
- **Live run (2026-09-22):** real bazaar + lb.tricked.pro + items API, with the bulk recipes file. Three listings priced in 0.56 s total: Hyperion 1.538B / 1.675B, Divan drill 1.928B / 2.241B, Terminator 710M / 843M.

## Site usage
```js
const core = { loadRecipeTree, cleanCraftCost, priceItem };
ctx.hyItems = itemsApiJson;            // optional, reuse the site's items request
const r = await listingCraftCost(row.tag, row, ctx, core, { signal });
// r.base.fromScratch -> flattenTree() for the clean-craft part; r.modifiers.fromScratch -> the modifier lines; r.totals
```
