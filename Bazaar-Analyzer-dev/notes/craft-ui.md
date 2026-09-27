# Craft price UI (AH item tabs + listing craft cost) + patch script

Final state after integration (2026-09-22): parts `00-shared.js`, `10-craft-tab.js` and `20-listing-craft.js` are all
inlined and mounted (5 REPLACEMENTS). SITE runs `index-KsTXbicp-v12.js`.

## Build / ship (idempotent, always starts from the v11 backup)
```
"C:/Program Files/nodejs/node.exe" C:/Users/Lenovo/Bazaar-Analyzer-dev/patch/patch_v12.mjs
sh C:/Users/Lenovo/Bazaar-Analyzer-dev/patch/tests/run-listing-craft.sh      # SSR test of the listing part -> ALL OK
"C:/Program Files/nodejs/node.exe" C:/Users/Lenovo/Bazaar-Analyzer-dev/patch/check_classes.mjs <part.js>
```
Steps it runs:
1. Reads `Bazaar-Analyzer-v11-backup/app/assets/index-KsTXbicp-v11.js` and applies `REPLACEMENTS`. Each must match exactly once or the script throws.
2. Inlines `craft/craftCore.js` + `craft/craftModifiers.js`, in that order, as `const BA_craft=(()=>{...;return{12 exports}})();`.
3. Inlines every `patch/parts/*.js`, sorted by name. Imports and exports are rejected.
4. Inserts 2 and 3 before `var _f=new Ke({defaultOptions:{queries:{refetchOnWindowFocus:!1}}});`.
5. Runs `node --check` on a temp `.mjs` copy BEFORE writing.
6. Writes `SITE/app/assets/index-KsTXbicp-v12.js`.
7. Copies `patch/ba-craft.css` -> `SITE/app/assets/ba-craft.css`, `patch/theme-claude.css` -> `SITE/app/assets/theme-claude.css`
   (patch/ is now the source of truth for BOTH css files: edit there, re-run) and `notes/samples/recipes-compact.json` ->
   `SITE/app/data/recipes.json` (JSON.parse-checked first). Throws if any source is missing.
8. index.html: script src becomes `./assets/index-KsTXbicp-v12.js?v=<sha1 of bundle, 10 hex>`; the theme-claude.css and ba-craft.css
   links get `?v=<sha1 of css>` (inserted once if missing: theme after the technical-v3.css link, ba-craft after theme); favicon
   colours %23121722/%23fbbf24 -> %231f1e1d/%23d97757. Checks `<title>Bazaar Analyzer</title>` (mod SiteFinder).
   Tested: starting from the v11 backup's index.html or the current one gives byte-identical output; a second run changes nothing.
   recipes.json has no ?v= (the engine fetches `data/recipes.json` itself; serve.ps1 lets it be cached for a day).
9. Moves `SITE/app/assets/index-KsTXbicp-v11.js` to `patch/prev-bundles/` if present.

- `--out <file>` writes only the bundle (SITE untouched). `--noop --out <file>` is the pipeline self-test.
- **Browser cache:** serve.ps1 sends `max-age=86400` for .js/.css and `no-cache` for .html, so the `?v=` hash is the cache
  buster: a normal reload picks up a rebuild. serve.ps1 ignores the query string (tested 200). `tools/shot.sh` reuses the Edge
  profile `%TMP%/ba-shot-profile-<port>`; the old one (had a stale pre-hash v12) was renamed to `...-47844.old-<time>`.
- The checker only reads SITE css, so `ba-cr-*` classes are flagged there; `MISSING: <= 0` for 10-craft-tab.js is a false positive.

## Mounts (REPLACEMENTS in patch_v12.mjs; exact strings are in the script)
| name | where | inserted |
|---|---|---|
| AH item tabs | `gd`: `(0,b.jsxs)(`div`,{children:[(0,b.jsx)(id,{tag:e,params:d.params,paramsKey:f}),` | `BA_AhTabs{tag:e,name:a,tier:o,filters:l,params:d.params,paramsKey:f}` replaces `id` |
| chart craft line | `id`: before `(0,b.jsx)(BA_VolumeBox,{tag:e,tf:u,...})` | `BA_CraftChartLine{tag:e,chart:BA_ac,ready:m}` |
| auction page | `ja`: after the `coins · en(r.highestBidAmount\|\|r.startingBid)` div | `BA_CraftListingCost{tag:r.tag,item:r,price,className:'mt-1'}` |
| lowest-BIN card | `BA_LowestBin`: after the `coins · en(best.startingBid)` div | `BA_CraftListingCost{tag,item:best,price:best.startingBid,className:'mt-1'}` |
| sold rows | `Sa`: the `block text-[11px] text-mute` span with `en(t.highestBidAmount)` | same span + `BA_CraftListingCost{tag:e,item:t,price,lazy:!0,compact:!0}` |
Details of the three listing mounts (variables, data shapes): notes/listing-craft-mounts.md.

## parts/00-shared.js (shared API)
- `BA_el(type, props, ...kids)`; `BA_lsGet/BA_lsSet` (try/catch localStorage).
- `class BA_CraftBoundary` (props `label`, `inline`, `resetKey`).
- `useCraftCtx()`: shared engine ctx or null while the `['bazaar']` query loads; a NEW object on every bazaar change
  (`{instaBuy: p.instaBuy, buyOrder: p.instaSell}`); lowestBin/recipes/items carry over. Store `BA_craftStore {ctx, prod, subs}`.
- `BA_craftRefresh(qc)`, `BA_craftSiteId(id, info, petBase)`, `BA_craftName(id, info, petBase)`,
  `BA_craftFmt(v)` (0 -> `0`, 1e3..1e7 whole coins, else `en`), `BA_craftQty(q)`.
- `BA_CraftMethodBadge({method, note})` (hex colours; Buy order sky, Insta-buy yellow, Lowest BIN purple, Craft green/Trade/Kat, Forge orange, NPC, Coins, Ench. table, No market, Can't buy; `<m>+anvil`).
- `BA_CraftHover({content, children, className, as})`: fixed popover (`.ba-cr-pop`, pointer-events none), closes on scroll.
- `BA_CraftTwoLists({fromScratch, easy, kind:'tree'|'lines', maxDepth, maxRows, totals, petBase, footer})`.

## parts/10-craft-tab.js
- `BA_AhTabs`: "Price graph | Craft price" (localStorage `ba.ahTab`, URL untouched so the mod's `?f=` links work), pet tier select, `BA_CraftStat`. Chart mounts on first show, then only hidden.
- `BA_CraftStat`: "CRAFT PRICE (CLEAN) From scratch X · Easy Y", vs lowest BIN / vs avg sale 3d, `line` checkbox (`ba.craftLine`), `details →`. Hover = header "Clean craft price breakdown" + `BA_CraftTwoLists` (tree).
- `BA_CraftChartLine`: klinecharts `simpleTag`, `groupId:'ba-craft'`, locked, dashed #d97757.
- `BA_CraftPanel` (Craft tab): stat cards, Updated/Refresh, warnings, two `BA_CraftTree`s, skeletons, "no recipe" message.
- Hooks `BA_useCleanCraft(tag, tier)`, `BA_useAvgSale(tag)` (query `['ah-hist', tag, '1M', '{}']`).

## parts/20-listing-craft.js
- `BA_CraftListingCost({tag,item,price,lazy?,compact?,className?})`: trigger "Craft cost: X" (compact: "craft ⓘ" -> "craft 1.18B"), hover/focus/click opens a fixed dialog; placement below if it fits else the side with more room, clamped 8px, inner scroll, follows scroll/resize; closes 150 ms after mouse leave, on blur out, on Escape (focus back to trigger). Clicks are stopped (safe inside `<a>` rows/cards). One promise per `TAG|uuid` per ctx.
- Same look as the clean hover: box `.ba-cr-pop` (+ inline `pointer-events:auto; overflow:auto`), orange uppercase title
  "Craft cost breakdown", summary box (listing price vs both totals, "listing is X% cheaper/dearer"), columns `.ba-cr-two`
  (1 column under 640px) with `.ba-cr-col-h` headers "From scratch (cheapest)" #38bdf8 / "Easy way (insta-buy + lowest BIN)" #fbbf24
  and total, rows `.ba-cr-line` (name, shared badge, amount): an expandable "Clean item" row (flattenTree) then one row per
  modifier. Enchant rows show the book only when it isn't 1 book of that level (anvil: "Scavenger 5 · 2× Scavenger IV").
- Fixed during integration: dialog `aria-label` had been swallowed by a comment; Escape re-opened the popover (focus back on the
  trigger fired onFocus -> show; now a `skipFocus` ref); zero shown as "0.00"; missing badge broke the 3-column row grid (now an empty span).

## Verified in the built-in browser (serve.ps1 -Port 47844, 2026-09-22)
Console: no errors from the app (the only two entries came from my own synthetic `document.dispatchEvent(new KeyboardEvent(...))`,
which klinecharts' key handler can't take since its target is `document`).
| Page | Result |
|---|---|
| HYPERION | stat 510.44M / 550.x M, +1.3% vs lbin 504M; lbin card "Craft cost: 510.44M", popover (above, more room), Clean item expands to LASR eyes / Necron's Blade / Wither Catalyst / Necron's Handle; clicking inside the card doesn't navigate |
| TERMINATOR | 41 triggers (card + 40 lazy sold rows); sold-row popover flips above, clamped to top 8px with inner scroll; click trigger -> Tab to "Clean item" stays open -> Escape closes, focus on trigger, hash unchanged |
| auction `#/ah/auction/92543e910cab4e3684ab60078fdb32fb?from=TERMINATOR` | "Craft cost: 709.x M", popover clamped to right edge, 14 enchants + stars + recomb + HPB rows both sides |
| DIVAN_DRILL (forge) | stat 1.58B / 1.77B; lbin card 1.68B (clean + enchants); Craft tab tree |
| CRIMSON_CHESTPLATE | "not craftable (no recipe)"; listings "Craft cost: 229,620 (mods only, no recipe)" |
| PET_ENDER_DRAGON | stat 553.64M / 554.2M (Legendary via Kat); Epic listings (incl. "Legendary" = Epic + Tier Boost) priced as mods only |
| `#/ah/item/HYPERION?f=[{"kind":"recomb","value":true}]` | filter applied, tabs + stat fine |
| market, bazaar item ENCHANTED_DIAMOND, watchlist, alerts, activity, settings, AH index | render normally |
| 375px mobile | popover 8..367px, single column |

Screens: `notes/screens/craft/ahitem-graph-{DIVAN_DRILL,HYPERION}.png`, `auction-craft-cost.png`, `ahitem-lbin-craft-DIVAN_DRILL.png` (headless Edge, no hover possible).

## Fixes, UI review round 1 (these override the older descriptions above)
- Mount change: `BA_CraftChartLine{tag:e,chart:BA_ac,ready:m,fit:ie}` (ie = id's own Fit callback).
- `useCraftCtx()`: the ctx is rebuilt only when the bazaar's `lastUpdated` changes (`BA_craftCtxFor(products, stamp)`,
  `BA_craftStore.prod` = stamp), not on every new products array from a poll.
- `BA_useCleanCraft`: loading is true only until the first result per `tag|tier`; a recompute on a new ctx keeps the old result
  (no "Loading prices…" flash, Refresh not disabled). Refresh shows "Refreshing…" through its own busy flag.
- `BA_useAvgSale(tag, tier)`: query `['ah-hist', tag, '1M', JSON.stringify(params)]`, params `{Rarity: tier}` on PET_ pages else `{}`
  (same key the chart uses with only that filter). PET_ENDER_DRAGON Legendary 491M (was 328M over all tiers).
- `BA_CraftStat` shows only "vs lowest BIN (no filters)"; the avg-sale diff is gone. Craft tab cards: "Lowest BIN (no filters)"
  (with the craft-vs-this diff), "Avg sale 3d (all sales)" / "(all <Tier> sales)" with NO diff, sub "incl. upgraded items, no filters"
  / "this tier only, incl. upgraded pets".
- `BA_CraftHover`: trigger tabIndex 0, aria-expanded, aria-describedby -> popover (role tooltip, tabIndex -1). Opens on hover and
  focus; closes 150 ms after mouse leave (the mouse can move onto it; it has pointer-events auto, overflow auto, maxHeight = room on
  its side), on blur out, Escape, page scroll (scroll inside it is ignored), resize. Click toggles; a click within 400 ms of opening
  doesn't close it (a tap = mouseenter + focus + click).
- Tabs: role=tab + aria-selected. Tree rows with children: role=button, tabIndex 0, aria-expanded, Enter/Space toggle.
- Hidden chart: showing the graph tab calls every `BA_craftLine.shown` listener; BA_CraftChartLine remembers when the chart turned
  `ready` at 0 width (`chart.getSize(Ku,'main').width` falsy, i.e. loaded while hidden, e.g. filters changed on the Craft tab) and
  then runs resize() + fit() in a rAF. Normal tab switches don't touch the user's zoom.
- 20-listing-craft.js keys on `res.baseSource`: 'market' -> base row "Clean item (no recipe, bought)" + "No recipe: the clean item is
  counted at its market price.", % vs listing shown; null -> "(mods only, no recipe)" (compact "*"); null with total 0 -> muted
  trigger "no recipe" and popover "No recipe and no market price for this item, and nothing on it to price." (no totals).
  `res.notes` -> "Not counted: ...". Dead fallbacks removed (BA_CE, BA_CraftLstMethods, y.Fragment boundary, {ctx} wrapper);
  the component is wrapped in `BA_CraftBoundary` inline. SSR test: run-listing-craft.sh also inlines `class BA_CraftBoundary`,
  and the tail checks the market base and the empty base.
- Verified on serve.ps1 -Port 47846: PET_ENDER_DRAGON stat "+37.7M (+7.4%)" vs lbin 509M; Epic lbin card "Craft cost: 136.2M"
  (was 0). HYPERION: tree Enter 6 -> 8 rows; clean hover opens on focusin, Escape/focusout close, mouse can move onto it.
  TERMINATOR: graph -> Craft tab -> add Recombobulated filter -> graph: chart full width and fitted. No app errors in the console.
- Heads-up: during this run something else navigated and typed in the built-in browser pane (hash changes, "c;" typed into the
  filter search). Short script-driven checks were more reliable than long waits. The AH item page does not poll the bazaar
  (0 bazaar fetches in 60 s), so the 30 s poll only matters on pages that run Xt().

## Known limits
- Pet listings: pet exp/level isn't priced (listed under "Not counted"). A no-recipe listing's "clean item" is the tag's lowest
  BIN, which can be this very listing.
- Engine (not UI): easy-way enchant rows can show "Buy order + anvil" when that level has no insta-buy price; books without a market count as 0 ("No market").
- Site header nav (original v11 `nav.flex.gap-1`) is ~515px wide, so at 375px the page scrolls sideways. Pre-existing, not craft UI.

## Packaging (2026-09-22 19:05, packaging agent)
- SITE checked: files = README.txt, serve.ps1, bat, app/index.html, app/assets/{index-CZorzv8u.css, technical-v3.css, theme-claude.css, ba-craft.css,
  index-KsTXbicp-v12.js}, app/data/recipes.json, app/textures/** (same 346 textures as v11). No v11 bundle. grep for Lenovo/recanati/gmail/C:/Users/C:\Users: none.
  `?v=` hashes in index.html = sha1[0:10] of the files. `node patch_v12.mjs --out <scratch>` is byte-identical to the shipped bundle. 53/53 engine tests, run-listing-craft.sh ALL OK.
- serve.ps1 -Port 47847 -NoBrowser: 200 for /, bundle, 4 css, data/recipes.json, a texture; 404 for the v11 bundle and /../README.txt; stopped after.
- README.txt got a "NEW IN v12" section + data sources line. Zip: `Downloads/Bazaar-Analyzer-v12.zip` (356 file entries, no dir entries, top folder
  `Bazaar-Analyzer/`, forward slashes, deflate; made with .NET ZipFile, script kept only in scratch). `unzip -tq` OK; extracted tree `diff -r` identical to SITE.

## Craft price follows the filters (2026-09-25, parts/15-filter-craft.js)
Owner: on an item page (e.g. Hyperion) with modifier filters picked (e.g. "Wither Impact" = Ability Scroll chip with
Implosion + Shadow Warp + Wither Shield) the craft price still showed the CLEAN item. Now:
- `BA_filterListing(tag, filters, {tier, baseTier})` (pure) turns the page's debounced chips (`l` in `gd`) into the cheapest
  item that matches them, in the Coflnet row shape the engine already prices: `{tag, enchantments, flatNbt, reforge?, tier?, uuid}`.
  Mapping (also in the file header):
  | chip | synthetic listing |
  |---|---|
  | enchant {type,min} | `enchantments` level max(1, min); two chips of one enchant keep the higher |
  | stars {min} | `upgrade_level` = min (capped 10; 6-10 = master stars) + `dungeon_item` (conversion essence when needed) |
  | hpc {min} | `hpc` = min (capped 15: 10 Hot Potato + 5 Fuming) |
  | recomb yes | `rarity_upgrades` 1 (recomb "no" adds nothing) |
  | reforge X | `reforge` X (stone + apply cost). Blacksmith reforges (Heroic...) have no stone -> skipped |
  | rarity (pets) | the pet tier (BA_craftPetTier, same as the clean price) |
  | rarity (gear) | one tier above the item's own tier (page header) = recombobulated; own tier = nothing; else skipped. Also the reforge apply-cost tier |
  | nbt key = value / range min | `flatNbt[key]`, kept only when the engine prices that key alone (ability_scroll, power_ability_scroll, runes, dye, art of war, books, drill parts, pet held item / skin, ...) |
  | nbt gem `SLOT_n` = quality | + the slot in `unlocked_slots` (unlock cost). Universal slots (COMBAT_0...) also need the `SLOT_n_gem` chip, else skipped |
  | clean | nothing (that is the clean craft) |
  | price, bin | skipped ("not a modifier") |
  No chip that adds anything -> `item: null` -> the page shows exactly the old clean craft (same hook, same label).
- `BA_useFilterCraft(tag, tier, filters, baseTier)` = `BA_useCleanCraft` + `listingCraftCost` of the synthetic listing through
  `BA_CraftLstGet` (one promise per chip set per bazaar ctx, shared by the stat and the tab; key = `filters:<json>`).
- `BA_CraftStat`: label "Craft price (+ filters)", totals = clean + modifiers, hover = `BA_CraftFilterBreakdown` (the listing
  popover's two columns: expandable "Clean item" + one line per modifier). The "vs lowest BIN (no filters)" diff is hidden
  while modifiers are counted (that BIN is the clean item's). "not in craft price: ..." note (hover = why) for skipped chips.
  The dashed chart line follows (it takes the stat's from-scratch total).
- Craft tab (`BA_CraftPanel`): cards "clean X + filters Y", a "Selected filters" box (two line lists), clean trees titled
  "Clean item: ...", Lowest BIN card says "clean item, not compared".
- Tests: `node --test patch/tests/filter-craft.test.mjs` (mapping + Hyperion/Wither Impact priced by the engine on the craft
  fixtures: from scratch +561,666,627 = the three scrolls' buy orders, easy +579,785,845 = their insta-buys).
- Checked in headless Chromium (mocked Coflnet/Hypixel with the craft fixtures): HYPERION 509.33M / 550.79M clean; Add filter >
  "wimp" > Wither Impact -> "Craft price (+ filters)" 1,070,993,590 / 1,130,571,044 (= clean + the three scrolls), chart line
  "Craft 1.07B", Sale price chip -> "not in craft price: Sale price", Craft tab section, chips removed -> back to 509.33M / 550.79M;
  a mod `?f=` link with Wither Impact + Ultimate Wise 5 + 5 stars + Perfect Sapphire + Rarity Mythic + BIN -> 1.11B / 1.18B with
  UW book (anvil from 2x IV), Wither essence, recomb, 3 scrolls, SAPPHIRE_0 unlock, gem; BIN listed as not counted. Console clean.
