# Bazaar Analyzer v12 + Companion mod 0.6.0

## Start the new version
Website:
1. Close the black "Bazaar Analyzer" window that is running now. That is the old v11, started from Downloads\Bazaar-Analyzer-v11\Bazaar-Analyzer.
2. Double-click C:\Users\Lenovo\Bazaar-Analyzer\Start Bazaar Analyzer.bat. It uses the same address (http://127.0.0.1:47831), so your watchlist, alerts, drawings and webhook carry over.
   If the old window is still open, the new one moves to port 47832 and your saved data looks gone. Close the old one and start again.
   You can also extract Downloads\Bazaar-Analyzer-v12.zip and start it from there.

Mod: bazaar-analyzer-companion-0.6.0.jar is already in the mods folder of the Modrinth profile "Skyblocker Modpack". Start that profile as usual.
- The old 0.5.0 jar stays .disabled.
- A spare copy of the new jar is in Downloads.
- Your 0.5 settings files load as they are.

## New on the website
- AH item page, "Price graph | Craft price" tabs:
  - The graph view shows the clean item's craft price: from scratch and easy way.
  - Hover it for the breakdown. Tick "line" to draw it on the chart as a dashed orange line.
  - The Craft price tab shows both full recipe trees.
- Listings show their craft cost: the lowest-BIN card, the auction page and every row of the sold list.
  - Hover, click or tab to it for two lists:
    - From scratch: the cheapest way. It uses bazaar buy-order prices and picks the cheapest of buy order, lowest BIN, craft, forge or NPC at every step.
    - Easy way: insta-buy on the bazaar, or lowest BIN on the AH, for each ingredient, with no crafting of parts.
  - Both lists cover the clean item plus the listing's extras (enchants, stars, recomb, books, gems, reforge stone, scrolls...).
  - It also says how much cheaper or dearer the listing is than the craft cost.
- New look: dark gray and off-white with orange accents. Chart and price colors are unchanged.

## New in the mod
- A settings screen with 3 tabs. Changes apply right away and are saved when you close it.
  - Graph: default range, remember last range, lines (both, buy or sell), grid, background opacity, width/height, preview.
  - GUI (renamed from "Trade button" as you asked): trade button on/off, corner, X/Y offset, "Drag to place...", link after a cancelled trade, record trades.
  - Website links: enchant value threshold, max enchants, "other mods over %", pet rarity filter, website port with Test connection.
- Four ways to open it:
  - /bazaaranalyzer settings
  - the gear button in the in-game graph
  - Mod Menu > Configure
  - a key you bind yourself (Controls > Bazaar Analyzer > Open settings, unbound by default)
- It now loads on your profile's Fabric Loader 0.19.3. 0.5.0 needed 0.19.5, which is why it was disabled.

## Final update
- **Book Flips** sub-tab on the Bazaar page: combine level-1..n books into the highest anvil-craftable level. Inputs are buy orders and the output is a sell offer by default (toggles for insta-buy/insta-sell). Coins/hour = min(input volume / books needed, output volume) x profit, with a coins/hour filter.
- **Mayor bands** behind the AH price graph (alternating grays, mayor name on the band and in the tooltip; data from Coflnet, cached for 24h).
- **Clean** AH filter (only items with no modifiers). It also works in the mod's links.
- **Mod:** drag-to-place on the GUI tab fixed (the button can always be picked up again and stays where you drop it).
- **Polish fixes:** filter chips from mod links can be removed one by one; the charts use the warm theme grays; recipes.json is no longer cached for a day; trade tracking no longer mistakes a player's chat for a cancelled trade and ignores leftover chat from the previous trade; the trade window is only re-read when it changes; failed price requests wait 30s before retrying.
- Still open: see TODO-next.md.

## What was checked
- Craft math:
  - 53 automated tests pass.
  - A separate reference calculator, written independently, got exactly the same clean craft price for all 3,726 craftable items.
  - Coflnet's own craft-profit numbers were compared for 891 items: 850 fall between our from-scratch and easy prices. The rest differ because the data differs.
- A review found 23 problems and all were fixed. For example, enchant books came out far too cheap through an anvil shortcut, a dead 3-coin buy order priced a 23M book at 3 coins, the hovers did not work with the keyboard, and the gear button ignored graph size changes.
- Website:
  - Hyperion, Terminator, the Ender Dragon pet and a real auction page were checked in a browser. The console showed no errors.
  - Today it was confirmed that the launcher serves the new files, that the folder and zip contain no personal data, and that the zip unpacks to exactly the site folder.
- Mod:
  - A clean build passes and the 26 automated tests pass.
  - A test Minecraft client (not your game) confirmed the settings screen, the graph, the trade button, drag-to-place, Mod Menu and the new "GUI" tab.
  - Its requirements match your profile (MC 26.1.2, Loader 0.19.3, Fabric API 0.155.2, Java 25), and no other mod uses its id.

## Known limitations
- Craft prices are estimates:
  - Only the top bazaar order is used, so large amounts come out a bit cheap.
  - Pet level/exp, candy, attributes and untradeable parts are not priced. They are listed under "Not counted".
  - Enchants with no market price count as 0.
  - Recipes come from a NEU snapshot of 2026-09-21.
- Easy-way enchant rows can show "Buy order + anvil" when that exact book has no insta-buy price.
- At phone width the site's top menu scrolls sideways. This was already the case in v11.
- Mod:
  - It has not been run inside your real Skyblocker profile (on purpose). If the game won't start, rename the 0.6.0 jar to .disabled.
  - The trade-button preview assumes a 6-row trade window. It has not been checked against a real Hypixel trade.
  - Skyblocker is disabled in that profile, so graph icons come from items you've already seen in menus.

## Where things live
- C:\Users\Lenovo\Bazaar-Analyzer: the v12 website you run.
- Downloads\Bazaar-Analyzer-v12.zip and Downloads\bazaar-analyzer-companion-0.6.0.jar: packed copies.
- C:\Users\Lenovo\Bazaar-Mod: the mod source, decompiled from 0.5.0 and then edited to 0.6.0.
  - Copy this folder to your other PC, because the Bazaar-Mod folder there is now behind.
  - Build it with gradlew build. The jar goes to build\libs.
- C:\Users\Lenovo\Bazaar-Analyzer-dev:
  - craft\ = the craft engine and its tests
  - patch\patch_v12.mjs = rebuilds the site's v12 file from the v11 backup
  - notes\ = technical notes
- C:\Users\Lenovo\Bazaar-Analyzer-v11-backup: an untouched v11 backup.