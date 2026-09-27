# yoav-addons — Bazaar Analyzer (website) + Yoav Addons (Minecraft mod, was "Bazaar Analyzer Companion")

Personal tools for Hypixel SkyBlock by yoav3577. Private repo. Read this first, then the notes it points to.

GitHub: https://github.com/yoav2777/YoavAddons (public, branch `main`; `gh` CLI is installed and logged in).
Data collection (sales history, market dumps) and the dev-only workflows (dev shots, MC API) run in the private repo
yoav2777/yoav-addons, not here; bring its data over when the price model is updated.
On another PC: `gh repo clone yoav2777/YoavAddons`. The absolute `C:\Users\Lenovo\...` paths below are the main PC's.
`README.md` = the user-facing setup page. Downloads = GitHub Releases, made by `.github/workflows/release.yml`
(Actions > Release > Run workflow: builds the mod jar + zips `Bazaar-Analyzer-v12/` as `Bazaar-Analyzer.zip`, tag
`v<version>` from `Bazaar-Mod/gradle.properties`; re-running the same version replaces the files). Bump `version` for a new release.

## Layout (repo root = C:\Users\Lenovo\yoav-addons)

| Folder | What | Old path (junction on the main PC, still works) |
|---|---|---|
| `Bazaar-Mod/` | Fabric client mod, Gradle project (rebuilt from a decompile of 0.5.0, now 0.7.1) | `C:\Users\Lenovo\Bazaar-Mod` |
| `Bazaar-Analyzer-dev/` | Website source of truth: `patch/patch_v12.mjs` + `patch/parts/*.js` + `craft/*.js`, notes, tests | `C:\Users\Lenovo\Bazaar-Analyzer-dev` |
| `tracker/` | `ah-watch.mjs`: standalone 24/7 AH sold-listing logger from the Hypixel API (`auctions_ended`), no game needed. `node tracker/ah-watch.mjs <name>` → `tracker/ah-log.jsonl`. Test: `node --test tracker/ah-watch.test.mjs` | — |
| `Bazaar-Analyzer-v12/` | The built v12 site (output of the patch script; `Start Bazaar Analyzer.bat` runs it). Rebuild + commit after each site change | `C:\Users\Lenovo\Bazaar-Analyzer` |

The v11 bundle the patch starts from is `Bazaar-Analyzer-dev/patch/base/index-KsTXbicp-v11.js` (read-only).
Removed 2026-09-24 (still in git history, e.g. `git checkout 2d64fbe -- bazaar-graphs`): `bazaar-graphs/` (old React
source, readable reference for the minified bundle) and `Bazaar-Analyzer-v11-base/` (rest of the v11 site = same as v12's).

Not in the repo: the user's preview copy `C:\Users\Lenovo\BA-preview` (the user's own; only sync `Bazaar-Mod/src`
into it while it is NOT running), the packaged copies in `Downloads\`, and the mod's config/trade logs in the game profile.

## Website (Bazaar Analyzer)

- Local static site served by `serve.ps1` on `127.0.0.1:47831-47850`. The user actually runs the extracted zip
  `C:\Users\Lenovo\Downloads\Bazaar-Analyzer-v12\Bazaar-Analyzer` — after a rebuild, copy
  `app/assets/index-KsTXbicp-v12.js` + `app/index.html` there too, and re-zip to `Downloads\Bazaar-Analyzer-v12.zip`
  (top folder `Bazaar-Analyzer/`).
- Build: `node Bazaar-Analyzer-dev/patch/patch_v12.mjs` (writes the built site in `Bazaar-Analyzer-v12/`; `--out <file>` = scratch only).
  **Never hand-edit the bundle.** New code goes in `patch/parts/*.js` (plain JS, `(0,b.jsx)`, top-level names `BA_*` only)
  and is mounted by exact-match `REPLACEMENTS` (each `find` must match exactly once in the minified v11 bundle).
- Tests: `node Bazaar-Analyzer-dev/patch/tests/mayor-bands.test.mjs`, `node .../patch/tests/book-flips.test.mjs`, `node --test .../patch/tests/activity-search.test.mjs`, `node --test .../patch/tests/filter-craft.test.mjs`, `node --test .../patch/tests/profit-chart.test.mjs`,
  `node --test Bazaar-Analyzer-dev/craft/tests/*.test.mjs` (craft engine, uses `craft/fixtures`).
- Map of the minified bundle (which letter is which component, anchors): `Bazaar-Analyzer-dev/notes/bundle-map.md`.
  Readable copy: prettier the bundle into a scratch dir. Tailwind classes only exist if the original build used
  them — check with `node Bazaar-Analyzer-dev/patch/check_classes.mjs "cls1 cls2"`.
- The mod finds the site by `<title>Bazaar Analyzer</title>` + bundle name `index-XXXX-v(\d+).js` (needs v>=11).
  Keep both, and keep the `#/ah/item/TAG?f=<json>` route.
- Verify in a browser (the in-app browser pane works; it must be visible or requestAnimationFrame is paused).

## Mod (Yoav Addons 0.8.0, mod id still `bazaaranalyzer` so configs keep working)

- Minecraft 26.1.2 (unobfuscated), Fabric Loader >=0.19.3, Fabric API 0.155.2+26.1.2, Java 25, Loom 1.17.21, Gradle 9.5.1.
- Build: `cd Bazaar-Mod && JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot" ./gradlew build`
  → `build/libs/yoav-addons-0.8.0.jar` (121 JUnit tests). Stop the daemon after (`./gradlew --stop`) — RAM is tight.
- Install: copy the jar to `Downloads\` and to `%APPDATA%\ModrinthApp\profiles\Skyblocker Modpack\mods\`
  **only while the game is closed** (old 0.5.0 stays there as `.disabled`).
- Dev client: `./gradlew runClient` with `-Dbazaaranalyzer.dev.*` flags (DevHarness.java; list + screenshot tools in
  `Bazaar-Analyzer-dev/notes/mod-build.md`, runner `Bazaar-Analyzer-dev/tools/devrun.sh`). Needs ~3 GB free RAM —
  don't run it while the user's game is open.
- **Never** launch the real game or the Modrinth app, and never touch `%APPDATA%\.minecraft`.
- Code map (`com.yoav3577.bazaaranalyzer`): `BazaarClient` entry point; `core/*` pure tested logic (TradeParser,
  TradeChat, ModPricer, ModSelector, AhLink, Filter, Lowball, AhLedger, TradeBook, settings math); `PriceScreen`
  graph (key I); `TradeAhUi` trade-window button; `TradeTracker`/`Capture` trade logging; `GemWatch` (gems + other parts taken off lowball items, `gem-log.jsonl`); `Appraisal` + `core/Appraiser` (item worth: recent sales / 30-90 day history / BIN, notes/price-prediction.md), `TradeWorth` + `CraftPrices` + `core/craft` (trade window worth / craft "!"); `PriceDebug` (worth breakdown in tooltips); `LocalServer`
  (127.0.0.1:47860-47869, `/health` `/trades` `/lowballs` for the site's Activity tab); `SettingsScreen` (MoulConfig / SkyHanni look, drawing kit `MoulUi`; other mods add pages through `api/*` + the "yoavaddons" entrypoint, `Addons`; commands /ba /ya /yoavaddons /bazaaranalyzer in `AhCommands`);
  `SiteFinder`; `AhTabs`; `PriceData`/`AhLookup`/`Http`; `ModIo` shared IO thread. Config in `config/bazaaranalyzer/`.

## Where things stand

- Open items and what was fixed last: `Bazaar-Analyzer-dev/TODO-next.md`. User-facing summary: `Bazaar-Analyzer-dev/REPORT.md`.
- Feature notes: `Bazaar-Analyzer-dev/notes/*.md` (craft engine, book flips, mayor bands, theme, mod build, site perf).
- Mayor bands are behind a "Mayors" toolbar toggle, off by default, hover says "Beta" — the owner wants them
  properly fixed later before removing the Beta label.

## Working with the user

- Plain, short explanations.
- Commit with a clear message after a finished change; push only when asked.
