# Yoav Addons mod (was Bazaar Analyzer Companion): build, run, architecture

Status (2026-09-23): version **0.6.0** adds the in-game settings UI (see "Settings (0.6.0)" below). 33 JUnit tests. The
drag-to-place bug the user reported is fixed, see "Fix round 2" below.

Earlier status (2026-09-21): `C:/Users/Lenovo/Bazaar-Mod` is a working Fabric Loom project built from the Vineflower decompile of
`bazaar-analyzer-companion-0.5.0.jar`. It behaves like 0.5.0, but the version is `0.5.1-dev` and it needs
`fabricloader >=0.19.3`, so it loads on the user's profile (loader 0.19.3). No features were added.
`gradlew build` passes, with 18 JUnit tests. The dev client launched, and screenshots confirmed that the settings screen,
the bazaar graph and the trade-window button all work.

## Settings (0.6.0)
Open with: `/bazaaranalyzer settings`, key `key.bazaaranalyzer.settings` "Open settings" (UNBOUND by default, Controls > Bazaar Analyzer),
the gear button (⚙) left of 1D/1W/1M/1Y in the graph, or Mod Menu "Configure" (entrypoint `modmenu` -> `ModMenuEntry`; Mod Menu is
`compileOnly` + dev-only `localRuntime`, version 18.0.0 = the one in the user's profile; not a dependency, the jar has no Mod Menu classes).
`SettingsScreen`: 4 tabs (GRAPH / TRADE / LINKS / FILL), 2-column grid of vanilla CycleButton / AbstractSliderButton (inner class `Slider`) / Button,
each with a Tooltip, footer Reset page / Reset all (click twice) / Done. Claude-orange accent 0xFFD97757 on a 0xF01C1B19 panel. Fits 320x240
(max GUI scale). Changes apply immediately through the holders' `live(p)` (memory only); `removed()` writes all three files.
`PlaceButtonScreen`: mock trade window + the real button; drag it anywhere (the nearest corner is only the anchor the offsets are measured from, `ModPrefs.placedAt`), arrow keys = 1 px, Reset, Done. See "Fix round 2".

| Setting | Control | File / key | Default | Clamp |
|---|---|---|---|---|
| Default range | cycle 1 day/1 week/1 month/1 year | graph.json `range` | day | day/week/month/year else day |
| Remember last range | on/off | graph.json `rememberRange` | true | when off, 1D..1Y in the graph no longer changes the default |
| Lines | cycle Both / Buy-lowest / Sell-average | graph.json `lines` both/buy/sell | both | unknown -> both |
| Grid lines | on/off | graph.json `grid` | true | |
| Background opacity | slider 20-100 step 5 | graph.json `opacity` | 94 | 20..100 |
| Width / Height | sliders 300-1000 / 170-600 step 10, "Reset size" | graph.json `width`,`height` | 460 / 260 | file: 300..4000 / 170..4000; graph also clamps to the screen |
| Trade button on/off | on/off | mod-settings.json `tradeButton` | true | |
| Corner | cycle Top left/Top right/Bottom left/Bottom right | mod-settings.json `corner` top_left.. | top_left | unknown -> top_left |
| X / Y offset | sliders 0..max(200, half the screen) (+ drag-to-place) | mod-settings.json `offsetX`,`offsetY` | 6 / 6 | file 0..4000; button always kept on screen (`ModPrefs.buttonXY`) |
| Link after cancel | on/off | mod-settings.json `cancelLink` | true | gates `AhTabs.offerAfterCancel` |
| Record trades | on/off | mod-settings.json `recordTrades` | true | gates `TradeTracker.complete` -> PlayerTradeStore |
| Website port | cycle Auto / 47831..47850 (+ Test connection button) | mod-settings.json `sitePort` | 0 = Auto (scan) | other values -> 0; `SiteFinder.find` scans only that port |
| Enchants over | stepped slider `LinkSettings.ENCHANT_STEPS` (any,1M..5B) | link-settings.json `minEnchantValue` | 10M | 0..1e12 (hand-edited values kept until moved) |
| Max enchants | slider 0-10 | link-settings.json `maxEnchants` | 3 | file 0..20 |
| Other mods over | slider 0-100 % | link-settings.json `minSharePercent` | 10 | 0..100 |
| Pet rarity filter | on/off | link-settings.json `petRarity` | true | ModSelector drops the IDENTITY modifier when off |
| Auto fill time | on/off + number box (FILL tab) | mod-settings.json `fillTime`,`fillHours` | true / 9999 | hours 1..999999; `DurationFill` types it into the sign opened within 2 s of the AH "Auction Duration" menu closing (any other menu in between cancels) |

All files are in `config/bazaaranalyzer/`. Old 0.5 files load unchanged (missing keys = defaults; tested). Parsing is lenient (`core/Cfg`):
a broken/wrong-typed key falls back to its default, a broken file = all defaults. Pure logic: `core/GraphPrefs`, `core/ModPrefs`, `core/LinkSettings`,
`core/Cfg` (toSlider/fromSlider); holders `GraphSettings`/`ModSettings`/`AhSettings` (get/live/set|save) use `ConfigFiles` (atomic tmp+move write).
Tests: `src/test/.../core/SettingsTest.java` (round trips, 0.5 compat, clamping, corner math, drop snapping, slider math).

## Fix round 1 (2026-09-22, still version 0.6.0, 26 tests)
- Graph size from the gear button: `PriceScreen` keeps `appliedW/appliedH` (the GraphSettings size it last took over). `init()` re-reads
  `GraphSettings.width()/height()` and applies them when they differ, so Width/Height/Reset size show on the same graph after Done/Esc
  (position kept, then clampGeometry). After an edge drag, applied = the saved size.
- Drag-to-place: `ModPrefs.MAX_OFFSET` is now 4000 (file sanity clamp only; `buttonXY` keeps the button on screen). New
  `ModPrefs.SLIDER_OFFSET = 200`; the X/Y offset sliders go 0..max(200, screen width/2 or height/2). Test `dropFarFromACornerLandsWhereItWasDropped`.
- Trade-window mock: `SettingsScreen.TRADE_W/TRADE_H` = 176x222 (6-row chest + inventory, like DevHarness `ChestMenu.sixRows`), used by
  the settings preview and by `PlaceButtonScreen`, which also draws a faint slot grid (chest rows at y 18, inventory 140, hotbar 198).
  Not verified against a real Hypixel trade window (a 5-row menu would be 176x204; the 222 mock is the conservative choice).
- Chart tooltip lists only the lines the Lines setting shows (+ Sold on AH); height = 16 + 11 * lines.
- Also fixed: date labels under the chart overlapped the legend line; `cy1()` is now `py + ph - 30` (was -24).
- New dev flags: `dev.gear=1` (with dev.world + dev.openGraph=ID): graph -> `devGear()` presses the gear -> sets size 700x300 + Lines BUY via
  `GraphSettings.live` -> Done -> logs `Dev: gear after geometry=[px,py,pw,ph,chartMidX,chartMidY]`, puts the mouse on the chart (reflection on
  MouseHandler.xpos/ypos every tick, glfwSetCursorPos did not work) and saves `gear-after.png`; log ends with `Dev: gear done`.
  `dev.placeAt=x,y` (with dev.place=1): where the dragged button's top-left is dropped; the log prints `lands at [x, y, w, h]`.
- Shots: notes/shots/fix1/ (gear-after, place-after at 960x540 dropped at 250,10, settings-trade-2, graph-day).
- Real mouse/keyboard input still reaches the dev window sometimes (one run ended in the inventory screen); just rerun.

## Fix round 2 (2026-09-23): drag-to-place ("it is not really working"), 33 tests

**Root cause** (reproduced in the dev client, `dev.placeHuman=1`): `PlaceButtonScreen.mouseClicked` offered the press to the
vanilla widgets first (`if (super.mouseClicked(event, doubleClick)) return true;`). `ContainerEventHandler.mouseClicked`
returns true for **any** child under the cursor, so as soon as the blue button was dropped near the middle of the screen - where
the mock trade window, the hint text and the Done/Reset buttons are, i.e. exactly where you drag it to try the feature out -
the next press on it went to the widget underneath: the drag never started and **Done closed the screen**, keeping the position
you did not want. Log of the old code, second press on the button at 163,154 (it was sitting on Done):
`Dev: second press on the button ... -> [159, 150, 0]` (0 = not dragging) then `Dev: the place screen is gone, now SettingsScreen`.
The user's `BA-preview/run/config/bazaaranalyzer/mod-settings.json` (`top_right, 0, 0`) fits that story: a drag that ran into an
edge/dead end, or the sliders used instead.

**Fix** (`PlaceButtonScreen`):
- `mouseClicked` hit-tests the preview button FIRST and only then calls super, so the button can always be picked up again.
- `dragTo(mx, my)` = `ModPrefs.dragXY` (mouse - grab point, clamped on screen) + `placeAt`, used by `mouseDragged`, the new
  `mouseMoved` override and `mouseReleased`. Dragging now commits through `ModSettings.live` on every move, so the position
  survives a lost mouse-release; the corner/offset readout updates live while dragging.
- `mouseMoved` also drags: Minecraft only dispatches `mouseDragged` while the window is focused **and** it saw the press itself
  (`MouseHandler.handleAccumulatedMovement`: `if (!minecraft.isWindowActive()) return;` ... `if (activeButton != null && mousePressedTime > 0)`),
  while `mouseMoved` arrives for every movement. Proven: with the dev window unfocused the button did not move at all
  (`windowActive=false`, button stayed at [6,6] for the whole drag); with `Window.focused` forced true it followed the mouse.
- Do NOT gate the drag on `GLFW.glfwGetMouseButton(...)`: tried it as a "lost release" guard and it killed the drag after one
  move in the harness (synthetic presses are not physical) - and it would do the same whenever GLFW reports the button up.
- Hint line is now "Arrow keys: 1 pixel, Esc keeps it"; arrow keys and Esc/Done are unchanged (`placeAt` / `removed()` writes the file).
- New pure helper `ModPrefs.dragXY(mouseX, mouseY, grabX, grabY, w, h, screenW, screenH)` + `src/test/.../core/PlaceDragTest.java`
  (7 tests): drop = no jump, every corner keeps the drop point, the grab point stays under the mouse, no drift when dragging
  past an edge and back, a drop past the right edge = `top_right 0/0`, arrow nudges, and the same spot on a bigger screen.

**Verified in the dev client** (1280x720, GUI scale 3 = 427x240): drag from [6,6] to [159,150] on top of Done -> dropped there
(`bottom_right 122/74`, `buttonXY` = 159,150), pressed again *on the Done button* -> drag started, moved to the top-left corner,
dropped at [0,0], written to mod-settings.json. Then `dev.trade=button` with `bottom_right 122/74` logged
`Dev: trade button at x=159 y=150 w=146 h=16 (screen 427x240)` - the real TradeAhUi button is exactly where the preview was dropped.
Shots: notes/shots/drag-fix/placehuman-drop1.png (dropped over Done, readout "Bottom right, 122 / 74 px in"), placehuman-drop2.png.

**New dev flags** (both need `dev.world=1`; they force `Window.focused=true` by reflection because the dev client usually runs
in the background, and drive `MouseHandler.onButton/onMove` + `handleAccumulatedMovement` by reflection = the real mouse path):
- `dev.placeDrag=x,y`: opens PlaceButtonScreen and drags the button so its top-left lands at x,y. Logs `Dev: drag press ...`,
  `Dev: drag step <f> -> [x, y, dragging]`, `Dev: drag release -> ... prefs {...}`, shot `placedrag-after.png`, ends with `Dev: placeDrag done`.
- `dev.placeHuman=1`: the whole player flow - settings -> GUI page -> clicks the "Drag to place..." widget -> drags the button to the
  middle (over Done/Reset) -> drops -> presses it again there and drags it to the top-left -> Esc. Logs `Dev: drag1/drag2 ...`,
  shots `placehuman-drop1/2.png`, ends with `Dev: placeHuman done, mod-settings.json = {...}`.
  `PlaceButtonScreen.devXY()` returns `{x, y, dragging?1:0}`. Note the first `onMove` after a fake press is swallowed by
  `MouseHandler.ignoreFirstMove`.

MC 26.1 mouse facts worth keeping: coordinates in `MouseButtonEvent`/`mouseMoved` are GUI-scaled doubles (`getScaledXPos` =
raw * guiScaledWidth / windowWidth); `Screen` does not override mouseClicked/mouseDragged/mouseReleased, it inherits the
`ContainerEventHandler` defaults (`mouseDragged` there needs `getFocused() != null && isDragging()`); `MouseHandler.onButton`
runs on press and release, `runTick` -> `handleAccumulatedMovement` dispatches `mouseMoved` + `mouseDragged` once per frame.
The version was left at 0.6.0: the installed jar must be rebuilt and reinstalled for this fix.

## Layout

```
Bazaar-Mod/
  build.gradle, settings.gradle, gradle.properties, gradlew(.bat), gradle/wrapper/   (Gradle 9.5.1 wrapper)
  LICENSE                          (MIT, copied into the jar as LICENSE_yoav-addons)
  src/main/java/com/yoav3577/bazaaranalyzer/        client classes (MC + Fabric API)
  src/main/java/com/yoav3577/bazaaranalyzer/core/   pure Java logic (no Minecraft imports except Gson), unit-tested
  src/main/resources/fabric.mod.json               "version": "${version}" (expanded from gradle.properties)
  src/main/resources/assets/bazaaranalyzer/        icon.png, lang/en_us.json
  src/test/java/com/yoav3577/bazaaranalyzer/core/  JUnit 5 tests
  decompiled/                      untouched Vineflower output + original fabric.mod.json/MANIFEST (reference only)
  run/                             dev client game dir (run/options.txt pre-seeded, run/config/bazaaranalyzer/ = dev config)
```
The original used `splitEnvironmentSourceSets` (core/ in main, everything else in client). This project uses one normal
`main` source set, as the task asked. The mod is `"environment": "client"` either way. The only visible difference is that the jar
manifest has no `Fabric-Loom-Client-Only-Entries` line. The jar's file list is identical to 0.5.0 (checked with `unzip -Z1` + diff).

## Versions (gradle.properties)
minecraft_version=26.1.2, loader_version=0.19.3, loom_version=1.17.21 (plugin id `net.fabricmc.fabric-loom`; 26.1 is
unobfuscated, so there's no `mappings` line and plain `implementation`, as in fabric-example-mod branch 26.1),
fabric_api_version=0.155.2+26.1.2, junit_version=5.12.2, Java release 25. The original 0.5.0 was built with Loom 1.17.21 /
Gradle 9.5.1 / loader 0.19.5 (MANIFEST). Nothing in the mod needs loader 0.19.5: the mod only uses `FabricLoader.getInstance()`
getConfigDir/getModContainer, and every fabric-api 0.155.2 module declares `fabricloader >=0.18.4`. The compile against 0.19.3 also proves it.

## Commands (Git Bash)
```
cd /c/Users/Lenovo/Bazaar-Mod
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
./gradlew build            # compile + tests + jar  -> build/libs/bazaar-analyzer-companion-<version>.jar (0.6.0 now; + -sources.jar)
./gradlew test             # tests only; reports: build/reports/tests/test/index.html, xml: build/test-results/test/
./gradlew runClient        # dev client (run it with run_in_background; takes about 60 s to reach the title screen)
```
First build downloads Gradle + MC + deps (about 4 min). Later builds take about 45 s. Gradle caches are in `~/.gradle`.
The version comes from `version=` in gradle.properties. Bump it there, not in fabric.mod.json.

## Dev client + DevHarness flags
Pass flags through the environment. Loom's forked client JVM picks up `JAVA_TOOL_OPTIONS`:
```
export JAVA_TOOL_OPTIONS="-Dbazaaranalyzer.dev.world=1 -Dbazaaranalyzer.dev.settings=1 -Dbazaaranalyzer.dev.noBrowser=1"
./gradlew runClient > /c/Users/Lenovo/Bazaar-Analyzer-dev/tools/run.log 2>&1      # with run_in_background
until grep -q "joined the game" /c/Users/Lenovo/Bazaar-Analyzer-dev/tools/run.log; do sleep 2; done
```
DevHarness (`DevHarness.java`, active when any `bazaaranalyzer.dev.*` system property exists; init is called from GraphKey.init).
Ticks count END_CLIENT_TICK events at 20 per second:
- `dev.world=1`: at title-screen tick 100, creates a fresh flat survival world "BazaarDev<n>". All the in-world steps below need it.
  Without it, the graph opens on the title screen at tick 120.
- `dev.settings=1`: at play tick 200, runs `/bazaaranalyzer settings`, which opens the SettingsScreen. This flag wins over the others.
- `dev.trade=button|cancel`: at tick 150, opens a fake trade window titled `You<18 spaces>TestPartner` with a Withered Hyperion, an Enchanted
  Diamond (a bazaar item, so not counted), a Ghoul pet and an Endersnake rune on the partner side. At 320, `button` fires `TradeAhUi.devClick` (log line
  `Dev: button click handled = true`, then `Would open tab: ...` per item when noBrowser is set). `cancel` injects "You cancelled the trade!",
  closes the screen (which produces the chat offer), runs `/bazaaranalyzer open 1` at 460 and opens chat at 640.
- `dev.hover=<SKYBLOCK_ID>`: puts a diamond sword with that id into inventory slot 9, opens the inventory and presses the graph key on it (ticks 120/150).
- `dev.openGraph=<BAZAAR_ID>` / `dev.openAh=<AH_TAG>`: the item the graph opens with at play tick 200 (default: the remembered Enchanted Diamond).
- `dev.range=day|week|month|year`: saved to graph.json before the graph opens.
- `dev.gestures=<zoomNotches>,<cornerDx>,<cornerDy>`: at tick 320 with the graph open, scroll-zooms and drags the bottom-right corner.
- `dev.query=<text>`: pre-fills the graph search box when there's no item.
- `dev.icon=<ID>`: ItemIcons.forId returns a diamond for that id (icon rendering test).
- `dev.settingsClick=1` (with dev.settings=1): from tick 240, per page (200 ticks each): show page, log widget labels + screenshot
  `settings-<page>-<guiScale>.png`, click every control (sliders at 75%), log + screenshot `...-after-...`; then closes and logs the three JSON files.
  `dev.settingsPage=graph|trade|links` picks the first page.
- `dev.corners=1` (with dev.trade=button): ticks 170..290 cycle the button through the 4 corners (log `Dev: corner ... x= y=` + screenshot `trade-corner-<c>.png`), the click at 320 then hits the last corner.
- `dev.place=1`: opens PlaceButtonScreen, drags the button to the bottom-right, logs + saves (screenshots place-before/after).
- `dev.modmenu=1`: opens our screen through Mod Menu's `ModMenu.getConfigScreen` (reflection).
- `dev.shots=1`: graph mode screenshot `graph-<range>.png` at tick 300.
- `dev.noBrowser=1`: AhTabs logs `Would open tab: <url>` instead of opening the browser. ALWAYS set this.

`run/options.txt` is pre-seeded with `onboardAccessibility:false`, `pauseOnLostFocus:false` and master volume 0. Without
onboardAccessibility:false, the first launch shows the accessibility screen instead of TitleScreen and the harness never starts.

Stop the client (PowerShell): kill every java process whose command line contains Bazaar-Mod. That covers the gradlew launcher and the MC client:
```
Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object { $_.CommandLine -like '*Bazaar-Mod*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false }
```
(The Gradle daemon's command line doesn't contain Bazaar-Mod, so it survives. That's fine.)

## Screenshots (preferred: in-game)
`DevHarness.shot(mc, name)` = `Screenshot.grab` -> `run/screenshots/<name>.png` (framebuffer; works with the window hidden or the monitor off).
On 2026-09-22 `tools/shot.ps1` (PrintWindow) returned all-white images, probably because the display was asleep. Use in-game shots.
Window/GUI size for tests: set `overrideWidth:1280`, `overrideHeight:720`, `guiScale:2` (normal, 640x360) or `guiScale:0` (auto = 3, 426x240, the
"large" case) in run/options.txt (restore it afterwards; a copy of the seeded file is tools/options.backup.txt).
`tools/devrun.sh "<-D flags>" "<log pattern>" [timeoutSec]` runs runClient with the flags (+noBrowser), waits for the pattern, kills the client and prints the mod log lines.

## Screenshot helper (PrintWindow)
`C:/Users/Lenovo/Bazaar-Analyzer-dev/tools/shot.ps1 -Out <png> [-Match Bazaar-Mod]` finds the java process whose command line
contains `-Match`, takes its MainWindowHandle and captures it with PrintWindow(PW_RENDERFULLCONTENT). It works while the window is covered,
and it restores the window first if it's minimized. From bash:
```
powershell -NoProfile -ExecutionPolicy Bypass -File "C:/Users/Lenovo/Bazaar-Analyzer-dev/tools/shot.ps1" -Out "C:/Users/Lenovo/Bazaar-Analyzer-dev/notes/shots/x.png"
```
Then Read the PNG. Reference shots: notes/shots/settings.png, graph-bazaar.png, trade-button.png.

## Gotchas
- `node` isn't on the bash PATH: use "/c/Program Files/nodejs/node.exe".
- Don't `grep -q Exception` to wait for readiness: Realms logs a harmless "Failed to parse into SignedJWT" exception at startup.
  Wait for `joined the game` (world ready) or `Yoav Addons .* loaded` (mod init done).
- The dev client's LocalServer binds 127.0.0.1:47860 (the first free port in 47860-47869). If the user's real game is running, the dev client takes the next port.
- SiteFinder scans 47831-47850 with GET "/". In the test run it found the user's own site on 47831. Only GET / is sent there.
- During the trade test, the dev window was maximized and the button was clicked more than once from outside the harness (the user
  was probably still at the PC). Expect real mouse input to reach the dev window.
- In a bash heredoc with many quotes/backslashes, the whole command failed to parse ("unexpected EOF"). Use the Write tool for Java files.
- MC 26.1 API names used here: `GuiGraphicsExtractor` (g.fill/outline/text/centeredText/item/horizontalLine/verticalLine),
  `Screen.extractRenderState(g, mx, my, a)` (the old render()), `MouseButtonEvent(x, y, MouseButtonInfo(button, mods))`, `KeyEvent.key()`,
  `KeyMapping.Category.register(Identifier)`, fabric `KeyMappingHelper.registerKeyMapping`, `ScreenEvents.afterExtract(screen)`,
  `ClientCommands.literal/argument` (fabric command v2), `CompoundTag.getString(k)` returns Optional<String>, `getCompoundOrEmpty`, `getListOrEmpty`.

## Decompiler fixes applied (src vs decompiled/)
- Real compile bug: in AhLookup.refresh, a lambda used the out-of-scope loop variable `b`. Fixed to `bx.tag()`.
- Readability: records use compact canonical constructors (ItemMods, LinkSettings, Modifier, TradeItem, PlayerTrade,
  TradeWindow.RawStack). Removed redundant lambda/`(String)`/`(CustomData)` casts and the nested-type imports they needed. Colors are hex ARGB with the existing
  named constants (BUY/SELL/TEXT/MUTED/PANEL/EDGE_COLOR/EDGE_HOT/GRID) restored in PriceScreen and SettingsScreen. Inlined constants are back
  (ports, MAX_OFFERS, TAB_GAP_MS, WATCH_TICKS, *_WINDOW_MS, DAY_MS, COFL/BASE URLs, DevHarness `P` prefix). GLFW key constants
  (graph key = `GLFW_KEY_I`). `indexOf('_')` / `indexOf('\t')` instead of char codes. AhCommands was rewritten without raw casts.
  `tools/hexcolors.js` was the one-off color pass.

## Architecture (package com.yoav3577.bazaaranalyzer)
Entry point: `BazaarClient` (ClientModInitializer). It inits Capture, TradeTracker, GraphKey (+DevHarness), TradeAhUi+AhCommands, ItemIcons
and LocalServer, each in its own try/catch so one failure doesn't disable the rest. Constants: `MOD_ID`, `LOG` (slf4j "bazaaranalyzer"),
`VERSION` (from mod metadata), `BOOK` = `TradeBook(5000)` (the in-memory Bazaar/AH chat trade log).

Client classes:
- `Capture`: listens to GAME chat. Lines that pass `CaptureFilter.interesting` get parsed by `TradeParser` into BOOK and appended to
  `config/bazaaranalyzer/capture.log` (`<ms>\t<line>`, rotates to capture.log.1 at 5 MB). History reloads at startup.
- `TradeTracker`: tracks player-to-player trade windows (`TradeWindow.isTradeTitle`: title `You {5,}<partner>`). Each tick it snapshots
  both sides (`TradeWindow.parse`) and the partner-side AH items (`ahNow`, used by the button). After the window closes, it watches the
  inventory and chat for up to 80 ticks (`decide`: "Trade completed"/"cancelled" chat, or received items appearing) and
  then either records a `PlayerTrade` (`PlayerTradeStore` -> player-trades.jsonl) or, if cancelled, calls `AhTabs.offerAfterCancel`
  (a clickable chat link that runs `/bazaaranalyzer open <n>`). Debug dump goes to trade-debug.log (2 MB rotation).
- `TradeAhUi`: in trade windows, draws the "Open N AH item(s) on website" button at fixed GUI position X=6,Y=6,H=16 (top-left;
  colors 0xFF1B3358 normal / 0xFF2B4F86 hover, outline 0xFF5B7BB0). A left click collects the partner-side items
  (`TradeWindow.theirSlotIndexes`: rows 0-3, columns 5-8), keeps the AH items (`AhLink.isAhItem`, not a bazaar product) and calls `AhTabs.open`.
  `devClick` is the harness hook. 0.6.0: position/on-off come from ModSettings (see Settings).
- `AhTabs`: on its own thread, finds the site (`SiteFinder`), refreshes bazaar prices, and prices each item's modifiers
  (`ModPricer.price` with the insta-buy PriceBook), keeping only Coflnet-known enchants (`ModSelector.keepKnownEnchants`). It gets the lowest BIN (`PriceData.lowestBin`),
  picks modifiers (`ModSelector.select` with `AhSettings`), builds `AhLink.url(site, tag, chosen)` = `<site>/#/ah/item/<TAG>?f=<json filters>`
  and opens the tabs 350 ms apart (or logs them with dev.noBrowser). Also: chat helpers `say`, `openOffer(id)`
  (the last 20 cancelled-trade offers), and `append` to ah-link-debug.log (deleted at 1 MB).
- `SiteFinder`: GETs http://127.0.0.1:47831..47850/ and looks for `<title>Bazaar Analyzer</title>` plus the bundle version from
  `index-XXXX-v(\d+).js`. The highest version wins. `FILTER_VERSION = 11`: older sites get a warning that filters aren't applied.
- `AhCommands`: `/bazaaranalyzer open <n>` and `/bazaaranalyzer settings` (opens SettingsScreen).
- `SettingsScreen` (0.5.0 description, replaced in 0.6.0, see above): the only settings UI then, titled "AH link settings" (a 182 px panel, max 360 wide). It edits LinkSettings:
  min enchant value (coins, accepts 10m/1.5b), max enchants, and the other-modifier share %. Buttons: Save / Defaults / Cancel.
  It is not reachable from ModMenu (there's no modmenu integration), only through the command.
- `AhSettings`: loads and saves `config/bazaaranalyzer/link-settings.json`:
  `{"minEnchantValue":10000000,"maxEnchants":3,"minSharePercent":10.0000}`. Defaults are `LinkSettings.DEFAULT` = (1e7, 3, 0.10).
- `GraphSettings`: `config/bazaaranalyzer/graph.json` `{"width":460,"height":260,"range":"day"}`. PriceScreen saves the size
  after a resize-drag and the range when a range button is pressed. There's no other graph setting yet (no colors, position etc.).
- `GraphKey`: registers the key mapping `key.bazaaranalyzer.graph` (default I, category `bazaaranalyzer:main` "Bazaar Analyzer").
  Pressing it with no screen open opens PriceScreen on the remembered item. Pressing it in a container screen while hovering a slot
  reads the hovered slot (reflection on `AbstractContainerScreen.hoveredSlot`) and opens the graph for it: bazaar product -> BAZAAR choice,
  other skyblock id -> AH choice (with a name-search fallback), no id or a generic id (PET/RUNE/ENCHANTED_BOOK/POTION/ATTRIBUTE_SHARD) -> name search.
- `PriceScreen`: the in-game graph. It's a movable/resizable panel (drag edges/corners, min 300x170) with a search box (bazaar product names
  plus Coflnet `/item/search`, suggestions tagged BZ/AH), range buttons 1D/1W/1M/1Y, an item icon, live insta-buy/sell (bazaar) or last lowest/avg
  (AH), a two-series line chart (BUY 0xFFFBBF24 amber, SELL 0xFF38BDF8 sky blue), scroll zoom, drag pan, double-click reset and a hover tooltip.
  `open(mc, choice, query, hoveredStack)`, `remembered()`, `fmt(double)`. It doesn't pause the game.
- `PriceData`: HTTP data. Hypixel `/v2/skyblock/bazaar` (cached 20 s: `products()`, `live(id)` -> `Live(instaBuy=buy_summary[0], instaSell=sell_summary[0])`),
  Coflnet bazaar history (`/bazaar/<id>/history/<day|week>` or `?start&end` for month/year), AH history (`/item/price/<tag>/history/<range|full>`,
  min/avg/volume), `lowestBin` (`/item/price/<tag>/current` .buy), `searchAh`, `knownEnchants` (`/filter/options`). There's a 2-thread executor `async`.
- `ItemReader`: ItemStack -> `ItemMods` (for AH links) / `TradeWindow.RawStack` (for trades), read from CUSTOM_DATA (id, uuid, enchantments,
  ability_scroll, gems, petInfo, runes, modifier, upgrade_level/dungeon_item_level, rarity_upgrades, hot_potato_count, power_ability_scroll, counts).
- `ItemIcons`: remembers ItemStacks seen in containers/inventory (up to 3000 ids) and falls back to Skyblocker's
  `ItemRepository.getItemStack` through reflection (NEU ids like `GHOUL;4`, `ENDERSNAKE_RUNE;1`, `ULTIMATE_WISE;5`).
- `LocalServer`: JDK HttpServer on 127.0.0.1:47860-47869 for the website. `GET /health`, `GET /trades?limit=N` (TradeBook JSON + AhLedger),
  `GET /lowballs` (Lowball analysis of player trades vs. the player's Coflnet auctions, which come from `AhLookup`). It's guarded by `RequestGuard`
  (Host must be 127.0.0.1/localhost:<port>, Origin must be http://127.0.0.1|localhost:47831-47850 or absent). GET only.
- `AhLookup`: background fetch of the player's own auctions from Coflnet (`/player/<uuid>/auctions?page=`, detail `/auction/<id>` for uid), refreshed once a minute.
- `Http`: `get(url, timeoutMs)` (UA `BazaarAnalyzerCompanion/<ver>`, non-200 -> IOException), `parseUtc`.
- `PlayerTradeStore`: player-trades.jsonl (append-only, deduped with `TradeDedupe` on load and add).

core/ (pure, tested): `TradeChat` (cancel/complete regexes), `Coins.parse` ("1.5m", "1,234" -> double, NaN if none), `Filter` (site filter
JSON: enchant/stars/rarity/recomb/hpc/nbt value|range), `AhLink` (isAhItem, tag, url), `ModPricer` (modifier values from a `PriceBook`;
enchant value doubles per missing level up to 4 levels down), `ModSelector`, `Modifier(+Group)`, `LinkSettings`, `ItemMods`, `Gems`,
`TradeParser` (Bazaar/AH chat lines -> `Trade(+Kind)`), `TradeBook`, `CaptureFilter`, `TradeWindow`, `TradeItem`, `PlayerTrade(Json)`,
`TradeDedupe`, `AhLedger` (listing lifecycle from chat + auctions), `AhAuction`, `AhFees`, `Lowball`, `Days` (day key, Israel time,
day starts at 12:00), `Json` (string/number encoding), `RequestGuard`, `PriceBook`.

Config dir `config/bazaaranalyzer/` (in the dev client: `Bazaar-Mod/run/config/bazaaranalyzer/`): link-settings.json, graph.json,
capture.log(.1), player-trades.jsonl, trade-debug.log(.1), ah-link-debug.log.

## Tests
src/test/java/.../core: TradeChatTest, CoinsTest, FilterTest, AhLinkTest, ModPricerTest (includes ModSelector.select and the LinkSettings clamp),
TradeParserTest (includes TradeBook.loadCaptureLines), SettingsTest (0.6.0). 25 tests, all green. They're plain JUnit 5 with no Minecraft on the test path.

## Packaging (2026-09-22 19:05, packaging agent)
- Settings tab `Page.TRADE` label is now "GUI" (user request); DevHarness `SKIP_CLICK` has "GUI". `./gradlew clean build --rerun-tasks`: BUILD SUCCESSFUL,
  26 tests (7 classes), 0 failures. Dev client run (`tools/devrun.sh "-Dbazaaranalyzer.dev.world=1 -Dbazaaranalyzer.dev.settings=1 -Dbazaaranalyzer.dev.settingsClick=1" "Dev: screenshot settings-trade-"`)
  logged `[Graph] [GUI] [Website links]` tabs; shot: notes/shots/settings-gui-0.6.0.png. Note `dev.settingsPage` does not change the order of settingsClick (always GRAPH, TRADE, LINKS).
- Installed: `bazaar-analyzer-companion-0.6.0.jar` (sha1 5c178958f5a27f0912aeadffc847dea6bb3cf0b2, 217,257 bytes, 99 entries, class major 69 = Java 25, no nested jars)
  copied to `Downloads/` and to the profile `Skyblocker Modpack/mods/`. `bazaar-analyzer-companion-0.5.0.jar.disabled` left as is.
- Profile check: logs/latest.log = "Loading Minecraft 26.1.2 with Fabric Loader 0.19.3", java 25, fabric-api 0.155.2+26.1.2, modmenu 18.0.0;
  Modrinth runtime `meta/java_versions/zulu25.36.205-ca-jre25.0.4.1-win_x64`. No mod jar in mods/ has id `bazaaranalyzer` (checked every fabric.mod.json).
  Skyblocker is `.disabled` in that profile, so `ItemIcons.fromSkyblocker` logs "Skyblocker's item repository is not available" and uses seen items only.
- Jar scanned (unzip + grep -a) for Lenovo/recanati/gmail/C:/Users/AppData: nothing.
