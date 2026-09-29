# Book Flips sub-tab (Bazaar page)

Files: `patch/parts/40-book-flips.js` (UI + pure math), `patch/tests/book-flips.test.mjs` (node test, 11 cases),
fixture `patch/tests/fixtures/bazaar-enchants-2026-09-21.json` (raw Hypixel bazaar, ENCHANTMENT_* products only),
css `.ba-bf-search`, `.ba-bf-bad` in `patch/ba-craft.css`.

Run: `"C:/Program Files/nodejs/node.exe" --test C:/Users/Lenovo/Bazaar-Analyzer-dev/patch/tests/book-flips.test.mjs`
(the test evals the part file in a vm sandbox with `BA_craft = { canAnvilEnchant }` injected and pulls out
`BA_bookFlips`, `BA_bookCanAnvil`, `BA_bookAnvilMax`).

## Mounts (patch_v12.mjs REPLACEMENTS)
- Route switch gf: `e.page===\`market\`&&(0,b.jsx)(af,{mode:\`all\`})` -> `(0,b.jsx)(BA_BazaarPage,{})`.
  BA_BazaarPage renders `af({mode:'all'})` (Market, unchanged) or `BA_BookFlips` (inside BA_CraftBoundary) when
  `lt(location.hash,'sub')==='books'`.
- af header: `BA_BazaarSubTabs({sub:'market'})` inserted before the `Bazaar market` h1, only when `e==='all'`
  (Watchlist page has no sub-tabs). Hash: `#/` = Market, `#/?sub=books` = Book Flips (survives reload).

## Which levels the anvil can make: `BA_bookCanAnvil(name, level)`
Two rules, in this order (the tab uses this; craft prices keep using the engine rule alone):
1. `BA_bookAnvilMax[name]` — hand-kept caps in the part file for books the engine cannot judge (not ultimates, not
   enchanting-table enchants). `level > 1 && level <= cap`.
2. otherwise `BA_craft.canAnvilEnchant(name, level)` (craft/craftModifiers.js: ultimates any level, table enchants
   up to min(V, table max + 1), unknown table max -> never).

Rule 1 exists because rule 2 alone left the tab showing **only ULTIMATE_\* rows** (72 rows / 23 enchants on live
data), missing the classic "16x level I -> level V" flips. How the map was derived (scratch script, not shipped):
for every `ENCHANTMENT_<NAME>_<L>` bazaar product, read `notes/cache/neurepo/repo/items/<NAME>;<L>.json`, take the
lore block after the `Source:` **or `Sources:`** line (`§a` colour codes stripped; lines look like `I: Grand Bakery`,
`I-V: Bazaar`, continuation lines have no roman prefix), and expand it to level -> sources. An enchant gets a cap
when every level above its lowest listed level, up to its top bazaar level, has **no source but the Bazaar** (or no
source at all): those books exist and can only be player-made, i.e. anvil-combined. The cap is the last such level,
so a level with a real source stops it (Dedication IV = garden visitors, Charm VI = Chain of the End Times, Turbo
VI/VII = Turbo Gourd, Pesterminator VI, Stealth VI = Distant Echo).

Map (28 entries): `FEAST ICE_COLD KARMA PETALFALL CORRUPTION SUGAR_RUSH DIVINE_GIFT BUG_BLENDER CHARM PESTERMINATOR
PALEONTOLOGIST STEALTH QUANTUM` = 5, `DEDICATION TIDAL` = 3, all 13 `TURBO_*` = 5.
Deliberately **not** in the map:
- level-up enchants `CHAMPION COMPACT CULTIVATING EXPERTISE HECATOMB` — lore lists only level I (Community Shop) but
  they gain levels by use, not in an anvil (their levels II-X have 0 bazaar volume, so they cost nothing today).
- books whose higher levels have another source: `REJUVENATE OVERLOAD PRISTINE GREEN_THUMB SMOLDERING SMARTY_PANTS
  PROSPERITY QUICK_BITE REFLECTION LAPIDARY RESPITE` (lore `I-V: <drop>`; Respite also `I-V: Experiments`),
  the Kuudra mana books (`I-X: Kuudra`), `BIG_BRAIN VICIOUS SMALL_BRAIN CAYENNE TABASCO` (Dark Auction / collection).
  Add one only after confirming it really combines — it changes what the tab tells the user to buy.
- `TRANSYLVANIAN`: V is bazaar-only (so IV+IV -> V is real) but IV comes from the Hemovibe Collection, and a single
  cap cannot say "V yes, IV no", so it stays out. Bazaar only lists IV and V, so the tab shows nothing for it.
- Enchants with no `Source:` block at all in the NEU repo (`SCUBA FOREST_PLEDGE ARCANE STRONG_MANA ABSORB ...`).

## Math: `BA_bookFlips(products, {tax, instaIn, instaOut, tick, canAnvil})`
- products = site rows `{id, instaBuy, instaSell, soldWeek, boughtWeek}` (snapshot.products from `$t()`).
  Naming inversion: instaBuy = buy_summary[0] = lowest SELL OFFER; instaSell = sell_summary[0] = top BUY ORDER;
  soldWeek = sellMovingWeek (players insta-selling -> fills buy orders); boughtWeek = buyMovingWeek (insta-buys -> fills sell offers).
- Target per enchant: climb L=2.. while canAnvil(name,L); target = highest such L that is a bazaar product. If the
  target has no price the enchant is skipped (never a lower target). Sources: every k<target with a price; books = 2^(target-k).
- `tick` = order price step, **not** a constant: the UI passes `Kt(s=>s.outbid) ? 0.1 : 0`, the same Settings switch
  ("Outbid / undercut by 0.1 coins", default off) the Market tab's `Et(e,t,n,r)` uses via `Tt=.1`. Anything that is
  not a finite positive number falls back to 0, so prices can never go NaN.
- Input price: orders = instaSell + tick (rate soldWeek/168); insta = instaBuy (rate boughtWeek/168).
  Output price: orders = instaBuy - tick (rate boughtWeek/168); insta = instaSell (rate soldWeek/168). net = price*(1-tax), tax = `Kt(s=>s.tax)`.
- flips/h = min(inRate/books, outRate); limit = 'input'|'output'; coinsHr = profit>0 ? flips*profit : 0; pct = profit/cost.
- Row fields: key,name,from,target,books,srcId,outId,inPrice,cost,outPrice,net,taxPaid,profit,pct,inRate,outRate,flips,limit,coinsHr.

## Wide spreads
`BA_bookWide(r)` = `r.pct > 1`, the Market tab's "very wide spread" threshold. The ⚠ and `BA_bookWideTip` show in
**both** the Margin and the Coins/hr cell (coins/hr is the default sort, so the warning has to be there too).
The header checkbox "Hide ⚠ wide spreads" is **off by default**: unlike a single bazaar item, a >100% margin over
16 books is often just what the levels are worth (Turbo Wheat I -> V is 280% and real), so hiding them by default
would drop 112 of 170 live rows including good ones. Flag, don't hide.

## Live data reality (2026-09-22, after the cap map)
170 rows with buy/sell orders (was 72), 28 non-ultimate enchants. Top of the coins/hr list: Ultimate Sunset I->V
~70M/h, Ultimate Legion I->V ~45M/h, **Feast I->V x16 cost 48.20M, profit 4.42M (9.2%), 6.6 flips/h output-limited,
~29.2M/h**, Pesterminator I->V ~27M/h, Turbo Moonflower/Sunflower/Karma 7-10M/h. 112 of the 170 rows are ⚠ wide.
Low-level enchanting-table books (Sharpness I-V, Looting I-III, ...) still have no bazaar market, so table enchants
produce no rows at all. Hand-checked Ultimate Legion 16x I -> V against the live API: cost 16 x 2,500,515.2,
sale 45,999,995.3 x 0.9875, profit ~5.42M, flips/h min(22292/168/16=8.29, 1457/168=8.67) = 8.29 input, ~44.9M/h — matches UI.

## Browser check (port 47849, cache gotcha)
`serve.ps1 -Port 47849 -NoBrowser` sends cacheable responses, and the built-in browser kept running the **previous**
bundle even after ctrl+shift+R (`performance.getEntriesByType('resource')` showed transferSize 0 and the old
decodedBodySize). Navigating to `http://127.0.0.1:47849/index.html?cb=<n>#/?sub=books` (cache-busting query on the
*document*) reloads index.html, whose `?v=<hash>` on the bundle then changes per patch run and pulls the new file.

## Watchlist (Book Flips)
Star column on every row; stars live in their own store `BA_bfWatch` (localStorage `bz.bookwatch.v1`, row keys) so the
Market watchlist is untouched. Watchlist page = `BA_WatchlistPage`: `#/watchlist` = Market (af), `#/watchlist?sub=books`
= `BA_BookFlips({watch:true})` (starred flips only). Mounts: route `Watchlist route: Market | Book Flips`; af header
sub-tabs now show on both modes (`base` = Bazaar or Watchlist hash).
