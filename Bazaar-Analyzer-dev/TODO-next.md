# Bazaar Analyzer - leftover fixes

Hand this file to a new chat. Projects: site source of truth = C:/Users/Lenovo/Bazaar-Analyzer-dev (patch/patch_v12.mjs + patch/parts/*.js + craft/*.js; re-run `node patch/patch_v12.mjs`, never hand-edit the bundle), live site = C:/Users/Lenovo/Bazaar-Analyzer, mod = C:/Users/Lenovo/Bazaar-Mod (JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot, gradlew build). Notes: Bazaar-Analyzer-dev/notes/*.md. After fixing: re-zip to Downloads/Bazaar-Analyzer-v12.zip (`powershell -File tools/mkzip.ps1 -Src C:/Users/Lenovo/Bazaar-Analyzer -Out <new zip>`, top folder Bazaar-Analyzer/; it refuses to overwrite), copy the jar to Downloads and the Skyblocker Modpack profile mods folder.

## Not tested in a browser or in game yet (quick check)
- Mod 0.7.1 (2026-09-27 bug sweep, below): core compiled + 122 JUnit tests with JDK 21; the Minecraft-side files
  (Capture, AhLookup, LocalServer, Esp) only compile in the GitHub Build workflow. Not run in game.
- Auto fill time: AH > Create Auction > Auction Duration > Custom Duration should show the number (default 336 h);
  the Starting bid sign must stay empty.
- Chart colors: grid/axes/crosshair/drawing default now use warm grays (patch entries named Polish: chart ...).
- AH filter chips opened from a mod ?f= link: removing/editing one chip should only affect that chip.
- Mod trade tracking: cancel detection (only Hypixel lines, not player chat), stamps reset per trade window.
- Install: copy the new jar into `%APPDATA%\ModrinthApp\profiles\Skyblocker Modpack\mods\` (replacing the older
  yoav-addons jar) with the game CLOSED; copy the rebuilt site into the Downloads copy and re-zip.

## Changed 2026-09-27 (cloud session: bug sweep)
- Site: a page that throws shows "This page failed: ..." instead of blanking the whole site (BA_CraftBoundary around
  <main>, reset on navigation). Activity stops polling while the browser tab is hidden. "Net per day" uses the mod's
  per-day totals over its whole trade book (`summary.days` in /trades, mod 0.7.1+), rows only while searching.
  Mod version text says "latest version". BIN fee 2.5% from exactly 100M (site + mod).
- Mod: capture.log rolls over to `capture-<date>.log` files that are kept (was one `.log.1`, older history deleted);
  startup reads them newest first until the book (now 50,000 trades, was 5,000) is full.
  /lowballs reuses its last answer while its inputs are unchanged (max 1 min). AH lookup stops re-asking Coflnet
  for auctions whose item has no uid. ESP blocks: nearest chunks first (1024 cap keeps the nearest, common blocks
  stop early). Lowball AH net subtracts the duration fee (AhFees.durationFee: 20 coins for 1 h ... 55,200 for 14 days;
  between the wiki preset points it's a straight line, not exact).
- Tracker: ah-watch skips auctions already in ah-log.jsonl after a restart.

## Changed 2026-09-27 (cloud session: sales history)
- Sales history (`.github/workflows/sales-history.yml`, branch `sales-history`: `<TAG>/<YYYY-MM>.jsonl` one sale per line +
  `_prices/<day>.json`) tracks 782 items: every weapon / armor / tool / equipment item with a lowest BIN of 1M+,
  accessories of 5M+, pets whose Legendary is 3M+ (`.github/data-tags.txt`). Runs every 2 days (Coflnet keeps ~7 days of
  sales), ~30 min of Actions each. No free archive of single sales exists (Hypixel keeps ~60 s, Coflnet free ~7 days).
- Price evaluation experiments are only in the old private repo yoav2777/yoav-addons (draft PR 5, not
  on main): learned per-upgrade values + bundles (worse than 0.7 x craft cost with one week of sales), Tune.java
  parameter search. Tune on 412 item types the mod was never tuned on (21.5k sales): 5.5% median error with the current
  settings; half-life 0.5 h -> 5.3%, anchor cap 2 -> 5.3% (old 94 items: 6.3% -> 6.2%). Not applied yet; re-check with
  more sales history. An AI model (gradient-boosted trees on the sales history) is an idea for later, not started.

## Changed 2026-09-26 (cloud session: fixes + profit chart)
- Mod: Lowball sell-offer claims only match the offer with their "at X each" price (was FIFO per item; a claim of another
  offer of the same gem could mark this one sold). Test in LowballGemTest.
- Mod: trade window craft prices reuse the parsed recipes / Hypixel items while their JSON is unchanged (CraftCtx.of(..., prev)).
- Mod: Auto fill time capped at 336 hours (14 days, Hypixel's longest auction); saved 9999 clamps to 336. Not checked in game.
- Mod: item worth for stat-tracked items: no 1.5 x anchor cap when the item has a tracker, sales moved by coins per
  tracker unit. Backtest: Final Destination 34% -> 12%, Midas Sword 40% -> 19% error (notes/price-prediction.md).
- Mod changes compiled + 113 core tests run with plain javac (JDK 21); CraftPrices.java (Minecraft side) not compiled here.
- Mod item worth, round 3 (notes/price-prediction.md): sales half-life 1 h, mispriced lowest BIN ignored, 1-2 comparable
  sales used (capped at the anchor), anchor = clean + 90% of mods. Backtest on 94 item types 7.2% -> 6.3%; items with
  few sales ~15.6% -> ~10%. Per-modifier resale factors tested: no gain. Waiting on the owner's valuations of the 15
  items in notes/value-quiz.md (answer key: real sale prices, not in the repo) to compare with the owner's method.
- Site: header search: Tab / Shift+Tab walk the suggestion list (wrapping), Enter opens. Activity: "Lowball profit per
  day" (gained + pending) and "Net per day" (AH & Bazaar) bar charts, hover = day + numbers, follow the tab's search
  (parts/95-profit-chart.js, test patch/tests/profit-chart.test.mjs). Checked in headless Chromium with a mocked mod.
  Still to do on the main PC: copy the rebuilt app into the Downloads copy and re-zip.

## Changed 2026-09-26 (cloud session: MoulConfig look, commands)
- Mod: settings rebuilt in SkyHanni's MoulConfig style (SettingsScreen + MoulUi): header bar, Categories list, option
  cards (name + control left, description right), red/green toggles, green-knob sliders with value boxes, dropdowns,
  light grey buttons, search (magnifier), About page with the commands and "Reset everything". Graph screen (search box,
  range buttons, gear, panel edges) and the button placer use the same look. Trade button untouched.
- Commands: /ba, /ya, /yoavaddons, /bazaaranalyzer, /bazaaranalyzer settings, /bazaaranalyzer open all open the settings
  (next tick, so closing chat doesn't close them); /bazaaranalyzer open <n> unchanged. Checked in the dev client
  (dev.moul=1: /ba, pages, dropdown, search, graph, placer).

## Changed 2026-09-26 (cloud session: price prediction)
- Mod: item worth rebuilt on a backtest of 2126 real Coflnet sales (notes/price-prediction.md; tool
  tools/price-backtest/): median error 5.7% (was 10.1%), overvalued by >20% on 9.9% of sales (was 25%).
  core.Appraiser + Appraisal: recent sales meeting the important modifiers, moved to this item's modifier cost,
  newest weigh most; fewer than 3 -> Coflnet 30-day history, then 90 days (never above clean lowest BIN + 70% of mods);
  a live listing of the same item caps it. Pets: own tier + level band + Tier Boost (tier-boosted pets now filter by
  the rarity they show). Craft "!" compares with this worth and shows even when some craft parts have no price.
- Mod settings: new "Item worth" page, "Debug on hover" adds the full breakdown to every item tooltip (worth, recent
  sales count, 30/90-day sales, lowest BIN with filters, clean BIN, mods cost, craft, filters). Checked in the dev
  client (Hyperion 1.50B from 5 comparable sales, the old way said 2.27B; Ghoul lvl 1 80k).
- Known weak spots: stat-tracked items (Final Destination kills, Midas bid), cheap noisy items, items with no sales.
  Coflnet only keeps ~7 days of single sales; its "full" history lags ~7 weeks (90-day window has a gap).

## Changed 2026-09-25 night (cloud session: ESP, settings, trade worth, parts)
- Mod ESP (settings page "ESP", config/bazaaranalyzer/esp.json, `Esp` + `core/EspPrefs`): players by exact name, mobs by a
  part of their name tag or by type (`zombie` / `minecraft:zombie`), blocks by id (`diamond_ore`), all comma separated.
  Style Outline = vanilla glowing outline (mixins `MinecraftGlowMixin` + `EntityGlowColorMixin`, `bazaaranalyzer.mixins.json`),
  Box = always-on-top cuboid gizmos. Blocks always get a box, scanned every 2 s within the block range (8-64), max 1024.
  SkyBlock mobs match through the name tag armor stand above them. Checked in the dev client on GitHub (Dev shots workflow).
- Mod settings: page buttons are a column on the left of the panel (was a row on top); no orange line on top of the panel.
  Checked in the dev client (ESP outline + box through a wall, settings ESP page, trade worth + craft "!" hovers).
- Trade window: under the trade button, the other player's items' worth (recent sales + lowest BIN with the item's important
  modifiers, Coflnet), hover = per-item breakdown. Inflation warning when an item's price jumped vs its month history
  (skipped for pets and runes: their history mixes rarities/levels). "!" when the craft cost (from scratch or easy, Java port
  of the site's craft engine = `core/craft`, same numbers) is cheaper than that lowest BIN; hover = details.
- Lowball tracker follows drill parts, pet held items/skins, item skins, dyes and rod parts off bought items too (not only gems);
  sold on the Bazaar or the AH within a day of the removal = part of the lowball. Only coins paid for the item count as its cost
  (parts take no share). Lowballing tab: newest lowball first in each day.
- Dev tools: `.github/workflows/devshots.yml` (commit message with `[shots]` = dev client under xvfb, screenshots + logs pushed to
  the `dev-shots` branch) and `mcapi.yml` (`[api]` = javap of the classes in `.github/mc-api-classes.txt` -> `mc-api` branch).
- From the review (both fixed 2026-09-26): sell-offer claims matched FIFO per item; the trade window craft ctx re-parsed
  the items/recipes JSON every minute.

## Changed 2026-09-25 (cloud session, mayor bands + craft price with filters)
- Site: mayor bands stay locked to the candles/bars on both charts while dragging, wheel-zooming, pinching, swiping, Fit and
  resizing (the repaint was skipped whenever klinecharts' whole-bar, clamped visible range looked unchanged: up to ~190 px off
  mid-drag, ~145 px stuck after it; now < 1 px every frame). Bands are much quieter (4% mayor tint, faint fading separators,
  small half-transparent labels). Still behind the "Mayors" toggle, off by default, hover "Beta". notes/mayor-bands.md §6.
- Site: the AH item page craft price follows the modifier filters (e.g. Hyperion + Wither Impact = clean craft + the three
  scrolls), stat + hover + Craft tab + chart line; chips that can't be priced (sale price, BIN/auction...) are listed as
  "not in craft price". parts/15-filter-craft.js, notes/craft-ui.md "Craft price follows the filters". Test: patch/tests/filter-craft.test.mjs.
- Both checked in headless Chromium with mocked data, console clean. Still to do on the main PC: copy the rebuilt
  `app/assets/index-KsTXbicp-v12.js` + `app/index.html` into the Downloads copy and re-zip.

## Changed 2026-09-25 (cloud session, auto-start site)
- Mod: trade button (AhTabs.run) starts the website when it is not running: serve.ps1 saves its folder to
  %LOCALAPPDATA%\BazaarAnalyzer\site-folder.txt on every run; SiteFinder.launch() runs that serve.ps1 -NoBrowser in its own
  window and waits up to 15 s. Needs the new serve.ps1 in the Downloads copy + one manual start. NOT run on Windows yet.

## Changed 2026-09-25 (cloud session, page scroll)
- Site: Market / Watchlist table, AH item list and Book Flips no longer scroll inside a fixed-height box (it left dead space
  under the box once the AH chips were gone). The box is as tall as its rows and the page scrolls (parts/90-page-scroll.js:
  BA_usePageVirt = the same virtualizer with the window as scroll element). Table headers stick under the site header; below
  ~1200 px the table scrolls sideways and the header stops sticking. Checked in headless Chromium (mocked data, 1894 + 900 px wide,
  console clean). Still to do on the main PC: copy the rebuilt app into the Downloads copy and re-zip.

## Changed 2026-09-25 (cloud session, lowball gems)
- Mod: lowball gem profit never matched because Hypixel gem names start with an icon glyph ("<glyph> Fine Ruby Gemstone") and the
  tracker compared exact names. Now `Gems.nameKey` (letters/digits only). New `GemWatch`: scans the inventory every second for
  items bought in a lowball (by uuid); gems that came with the item and are gone get logged to `config/bazaaranalyzer/gem-log.jsonl`
  (+ a gray chat line). `Lowball` counts a removed gem when it is insta-sold or put in a sell offer within a day of the removal
  (offer = sold once claimed, listed until then, held "removed, not sold yet" during that day). Trades from before the first
  gem-log START line keep the old guess (gem sold within a day of the trade). Tests: `LowballGemTest` (13). Core compiled + tested
  locally with JDK 21; the Minecraft side (GemWatch, ItemReader.tag/gems) only compiled in the GitHub Build workflow, NOT run in game.
  In game check: buy a gemmed item in a trade with coins, take a gem off at the Gemstone Grinder, close the menu -> chat line;
  sell the gem on the Bazaar -> Activity > Lowballing shows "1x Fine X Gemstone · removed, sold on the Bazaar".
- Site: Lowballing tab shows the mod's note on gem lines + updated help text (patch entries "Lowball: ...").

## Changed 2026-09-25 (cloud session)
- Site: on an auction page the item name links to the item's AH page (sales + price graph). Checked in headless Chromium.
- AH filters: Add filter menu has a "Reforges" section (every reforge, click = Reforge chip with it; plain "Reforge" entry kept). Reforge + modifier chips use a search box (BA_Pick, parts/70-ah-tweaks.js) instead of <select>: typing only filters the list. Checked in headless Chromium.
- New `tracker/ah-watch.mjs`: AH sold listings from the Hypixel API, runs without the game (for a server later). Hypixel/Mojang were blocked in the cloud sandbox, so NOT run against the live API yet. Not hooked to the site's Activity tab yet. Bazaar orders + player trades stay chat/trade-window only (no Hypixel API for them).

## Changed 2026-09-24 (search, parts/80-search.js; checked in headless Chromium with mocked Hypixel/Coflnet/mod, console clean)
- Header search = Bazaar items + AH item types + players (players via Coflnet /search/player, 2+ letters). AH page's own search box removed; the AH list follows the header query (like the Market list).
- Activity: own search box per tab (Trades / Lowballing / AH & Bazaar), matches item names and players; kept per tab while switching. Day totals and Lowballing cards re-sum from the matches; the AH & Bazaar summary cards stay the mod's full totals. Test: patch/tests/activity-search.test.mjs.

## Changed 2026-09-24 (later, parts/70-ah-tweaks.js; checked in headless Chromium with mocked Coflnet data, console clean)
- Auction House item list: the category and rarity chips (and their "All" / "Any rarity" buttons) are gone; it always shows every item.
- AH price chart: hover box at the cursor (BA_Tip, like the Bazaar chart) with Time / Average / Low-High / Sales (+ Mayor when on) of the bar under the cursor, whatever the cursor height.
- AH price chart: Fit button removed (Mayors toggle now sits in its place). The Bazaar chart still has its own Fit button.
- AH filters on Hyperion / Astraea / Scylla / Valkyrie / Necron's Blade: "Wither Impact" entry in Add filter (search "wimp" or "wither impact") = Ability Scroll filter with Implosion + Shadow Warp + Wither Shield.
- Still to do on the main PC: copy the rebuilt `app/assets/index-KsTXbicp-v12.js` + `app/index.html` into the Downloads copy and re-zip.

## Fixed 2026-09-24 (the full write-ups are in notes/TODO-2026-09-23-findings.md)

Site (patch/patch_v12.mjs + parts, rebuilt, checked in the browser, console clean):
- Mayor bands (now behind a "Mayors" toolbar toggle, off by default, hover = "Beta"; still to be fixed properly later): also on the Bazaar price chart (`Hd`, period = `ma[tf].barMs`) + a `Mayor` row in its BA_Tip tooltip. Labels moved to the bottom of the plot (at the top they covered klinecharts' legend once it wrapped, e.g. at 1000 px). Bands tinted with the mayor's colour. The term list refetches (max every 10 min) once the current term has ended, so a page left open keeps getting new terms. Tests: 22.
- Market search: one `Ir` over all rows per (rows, query debounced 150 ms) shared by the list and the fuzzy flag (parts/60-market-search.js). Old vs new compared on the real 2197 bazaar ids: 70 query/filter cases, 0 differences.
- Book Flips virtualized (`so`, 46 px rows, overscan 14): ~28 rows in the DOM instead of ~170.
- BA_useAvgSale: 15 min staleTime (it is always its own request; comment corrected).
- Sold-list craft cost: a closed lazy row no longer recomputes on every new bazaar ctx.
- Tab title: the App effect leaves `ahItem` / `ahPlayer` titles alone.
- craftSmoke.mjs keeps the first tag when --depth is absent.

Mod (C:/Users/Lenovo/Bazaar-Mod, also synced into BA-preview/src):
- PriceScreen: suggestion hit box = drawn row (top()+27); init() only reloads history when item/range changed or nothing is loaded (and no load is running); bazaar refresh at most every 5 s from init; loadToken / ahAsked / triedNameSearch volatile.
- ModIo (single-thread executor): AhTabs.append, TradeTracker.writeDebug and PlayerTradeStore's file write run off the render/tick thread.
- Capture history loads on the capture IO thread, parsing newest-first only until the book (5000 trades) is full (`TradeBook.loadCaptureTail`); history is put in front of trades captured meanwhile; the log reports the book size.
- TradeTracker tick failures: first one with stack trace, then a summary every 600 (core/FailureLimiter). ItemIcons logs its first scan failure once.
