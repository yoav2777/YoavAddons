BAZAAR ANALYZER
===============
Made by yoav3577.

HOW TO START
1. Extract this whole zip to a folder (right-click the zip > Extract All).
2. Double-click  "Start Bazaar Analyzer.bat".
3. Your browser opens the site. Leave the black window open while you use it.
   Close that window to stop.

Nothing needs to be installed. It only needs Windows 10/11 and an internet connection
(prices come live from Hypixel, history from Coflnet; craft prices also use the NEU recipe
repo and lb.tricked.pro for lowest BINs).

NEW IN v12
- Craft price: on an AH item page, switch between "Price graph" and "Craft price" at the top.
  The graph view shows the clean item's craft price (tick "line" to draw it on the chart).
  Listings (lowest BIN card, auction page, sold list) show their craft cost; hover it to see
  a breakdown in two lists:
    From scratch = the cheapest way (bazaar buy orders, lowest BIN, crafting parts, forge, NPC).
    Easy way     = insta-buy on the bazaar and lowest BIN on the AH, no crafting of parts.
  These are estimates: only the top bazaar order is used, and pet level/exp is not priced.
- New look: dark gray with orange accents. Chart and price colors are unchanged.
- Book Flips (Bazaar > Book Flips): profit from buying low-level enchanted books and combining
  them into the highest level you can make on an anvil, then selling it. Buy/sell orders by
  default; filter by coins per hour (estimated from the bazaar's hourly volume and the margin).
- AH price graph: the background shows which mayor was in office, in alternating grays, and
  the hover tooltip names the mayor.
- AH filters: new "Clean" filter shows only items with no modifiers (no enchants, reforge,
  stars, recomb, gems, etc.).

IF WINDOWS WARNS "Windows protected your PC"
Click "More info" > "Run anyway". To avoid it next time: right-click the zip file >
Properties > tick "Unblock" > OK, and only then extract it.

GOOD TO KNOW
- Price alerts only work while the black window AND the browser tab are open.
- Your watchlist, alerts, chart drawings and Discord webhook are saved in the browser you
  use on THIS PC. They don't come with the zip, so set them up again here. They are tied to
  the address http://127.0.0.1:47831 - keep using the launcher, not a different port.
- The Activity tab (your trades and lowball profit) only fills in if you also run the separate
  Yoav Addons Minecraft mod. Without it that tab just says "Yoav Addons mod not
  detected" and everything else works as normal.
- The Yoav Addons mod's "Open AH items on website" button and chat link open pages of this site, so keep the
  launcher (the black window) running while you play.
- The site only listens on this PC (127.0.0.1); nobody else on your network can open it.
- To update, extract a newer zip over the old folder (or into a new one).
