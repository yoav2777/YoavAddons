# Craft cost spec (clean item + listing modifiers, "from scratch" and "easy way")

Researched 2026-09-21 ~18:50-19:10 UTC with live endpoints. Every number below comes from the snapshots in
`notes/cache/` (bazaar.json 18:48 UTC, lowestbins.json 18:52 UTC, NEU repo zip @ commit a332242 2026-09-21).
Reference implementation (node, dev only, uses those snapshots): `notes/ref-craft.js` (clean item) and
`notes/ref-listing.js` (listing modifiers). They are the executable version of this spec; worked outputs are in
`notes/samples/worked-*.txt`. The browser engine should reproduce those numbers when fed the same snapshots.

```
node notes/ref-craft.js HYPERION DIVAN_DRILL          # both trees + totals
node notes/ref-listing.js notes/samples/listing-hyperion-full.json
```
(node is at "C:/Program Files/nodejs/node.exe", not on the bash PATH.)

---------------------------------------------------------------------------------------------------------
## 1. Data sources (all verified CORS `access-control-allow-origin: *` unless noted)

| What | URL | Cache hdr | Notes |
|---|---|---|---|
| Bazaar (all products) | `https://api.hypixel.net/v2/skyblock/bazaar` | max-age=60 | 2197 products, 3.6 MB. The site already loads it (fn `gt` in the bundle maps it to `{instaBuy,instaSell,...}`). |
| Hypixel item metadata | `https://api.hypixel.net/v2/resources/skyblock/items` | s-maxage=604800 | 5655 items. Needed for `upgrade_costs` (star essence), `gemstone_slots` (slot unlock costs), `dungeon_item_conversion_cost`, `tier`, `soulbound`, `can_auction`. Site already fetches it (const `mt`). |
| Lowest BIN, ALL items, one request | `https://lb.tricked.pro/lowestbins` | max-age=30, last-modified = now | 6522 keys, ~200 KB. Third-party (tricked), current: TERMINATOR 462,000,000 / TITANIUM_DRILL_4 327,000,000 / NECRON_HANDLE 470,999,999 matched Coflnet `/item/price/{tag}/bin` `lowest` exactly at the same minute. |
| Lowest BIN, one item (fallback) | `https://sky.coflnet.com/api/item/price/{TAG}/bin` | max-age=300 | `{"lowest":504000000,"uuid":"3544c9...","secondLowest":505000000}`. |
| Active BIN listings (with modifiers) | `https://sky.coflnet.com/api/auctions/tag/{TAG}/active/bin?{filters}` | max-age=300 | 10 cheapest rows matching filters (site: `BA_LowestBin`). Row has `reforge`, `tier`, `enchantments`, `flatNbt`. |
| Sold auctions | `https://sky.coflnet.com/api/auctions/tag/{TAG}/sold?page=0&pageSize=500` | | Row has `enchantments`, **`flattenedNbt`** (not flatNbt), `tier`, **no `reforge` field** (reforge only in `itemName` prefix, e.g. `"Heroic Hyperion ✪✪✪✪✪"`). |
| One auction | `https://sky.coflnet.com/api/auction/{auctionUuidNoDashes}` | | Same as active row + `nbtData.data` (raw typed NBT: arrays, nested `gems`). |
| NEU item JSON (recipes) | `https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/{ID}.json` | max-age=300 | `;` must be URL-encoded (`SHARPNESS%3B5`). 404 = no such item. Mirror with CORS: `https://cdn.jsdelivr.net/gh/NotEnoughUpdates/NotEnoughUpdates-REPO@master/items/{ID}.json` (max-age 7 d, s-maxage 12 h). |
| NEU constants | `.../master/constants/reforgestones.json`, `.../constants/enchants.json` | | Reforge stone -> name + apply cost per rarity; enchant table max levels + id aliases. |
| NEU whole repo (dev only) | `https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/refs/heads/master.zip` (22 MB) | | codeload **CORS is `https://render.githubusercontent.com` only -> NOT usable from the site**. Use it in a dev script to generate a recipes file. |
| Coflnet recipe | `https://sky.coflnet.com/api/craft/recipe/{TAG}` | max-age=43200 | Only the 3x3 crafting grid: `{"A1":"GIANT_FRAGMENT_LASER:1",...}`. Forge items return all-null grid (`DIVAN_DRILL`), no recipe = HTTP 204. **Not enough -> use NEU.** |
| Coflnet craft profit | `https://sky.coflnet.com/api/craft/profit` | max-age=300 | 905 rows, only *profitable* crafts, all `type:"crafting"`; no HYPERION/TERMINATOR/DIVAN_DRILL. Useful only as a sanity comparison. |
| Coflnet NEU-keyed prices | `https://sky.coflnet.com/api/prices/neu` | max-age=600 | CORS only when an `Origin` header is sent (browsers always do). **Not lowest BIN** (smoothed: TERMINATOR 504,655,502 vs real lowest 462,000,000; TITANIUM_DRILL_4 185,985,326 vs 327,000,000). Do not use. |
| Moulberry lowestbin | `https://moulberry.codes/lowestbin.json` | | Dead (Cloudflare 525). |

Coflnet rate limit headers: `x-rate-limit-limit: 1m`, `x-rate-limit-remaining: 93` after ~7 calls -> roughly
100 requests/minute/IP. The craft feature must make **zero** per-ingredient Coflnet calls in the normal path
(bulk lowestbins + bazaar + a recipes file). Per-item Coflnet `/bin` only as a fallback for a handful of ids.

### 1a. Bazaar field semantics (verified; the names are inverted)
ENCHANTED_DIAMOND at 18:48 UTC:
```
buy_summary[0]  = {amount:1128,   pricePerUnit:1278.4, orders:1}   // cheapest SELL OFFER  -> INSTA-BUY price (ascending list)
buy_summary[1]  = {amount:77,     pricePerUnit:1278.5}
sell_summary[0] = {amount:785424, pricePerUnit:1275,   orders:11}  // highest BUY ORDER    -> INSTA-SELL price (descending list)
sell_summary[1] = {pricePerUnit:1163.7}
quick_status    = {sellPrice:1275, buyPrice:1290.02, buyVolume:4814617, sellVolume:6894290, buyMovingWeek:..., sellMovingWeek:...}
```
- **instaBuy = `buy_summary[0].pricePerUnit`** (what "easy way" pays).
- **buy-order price = `sell_summary[0].pricePerUnit`** (= site's `instaSell`; what "from scratch" pays; optionally +0.1 to outbid, like the site's `outbid` setting).
- `quick_status.buyPrice/sellPrice` are volume-weighted averages of the top ~2% (1290.02 != 1278.4): do not use them for this feature.
- Either summary can be empty (359 products have no asks, 371 no bids, e.g. ENCHANTMENT_SHARPNESS_5 has both empty and all-zero quick_status). Empty => that side is unavailable (null), never 0.
- Thin books give silly buy orders: ENCHANTMENT_CRITICAL_6 top buy order is 0.1 coins (184 units) while insta-buy is 3,600. Review round 1 changed the rule: a top buy order below 1% of insta-buy is treated as dead and insta-buy is used instead (note "buy order too low, using insta-buy"); orders at or above 1% are used as-is.
- Bazaar ids with `:` : `LOG:1 LOG:2 LOG:3 LOG_2:1 INK_SACK:3 INK_SACK:4 RAW_FISH:1 RAW_FISH:2 RAW_FISH:3 SAND:1` (NEU writes these as `LOG-2`, `INK_SACK-4`, ...).

### 1b. lowestbins key formats (lb.tricked.pro)
- Normal items: the AH tag (`HYPERION`, `DIVAN_ALLOY`, `SAPPHIRE_POWER_SCROLL`, `DYE_LAVA`, `GEMSTONE_CHAMBER`).
- Pets: `PET-{TYPE}-{TIER}` plus level buckets `-100`, `-200` (`PET-GOLDEN_DRAGON-LEGENDARY=617777769`, `...-100=710000000`, `...-200=1105700000`).
- Books: `ENCHANTED_BOOK-{NAME}-{LVL}` (`ENCHANTED_BOOK-ULTIMATE_WISE-5`).
- Runes: `RUNE-{NAME}-{LVL}` (`RUNE-SOULTWIST-3=4640000`). Potions: `POTION-{NAME}-{LVL}[-ENHANCED]`. Some `-PERFECT` suffixes.
- **It also contains ~1412 bazaar product ids** with the bazaar insta-buy (e.g. `GIANT_FRAGMENT_LASER=1598149.8` = bazaar insta-buy, `BANE_OF_ARTHROPODS-6=0.1`...). Rule: **if an id is a bazaar product, ignore lowestbins for it.**
- Values can be `0.1`-style junk for dead markets; treat `<= 0` / missing as unavailable.

---------------------------------------------------------------------------------------------------------
## 2. Recipes (NEU repo)

### 2a. NEU item JSON shapes (samples: `samples/neu-item-*.json`)
Two places hold recipes:
1. `recipe` (legacy, 2007 items): 3x3 grid object, cells `"ID:count"` or `""`. Optional `"count"` key inside the
   `recipe` object = output count (159 items). Example HYPERION:
   `{"A1":"GIANT_FRAGMENT_LASER:1",...,"B2":"NECRON_BLADE:1",...}` (8 lasers + 1 blade, output 1).
2. `recipes` array (1387 items), `type` counts: `npc_shop 1097, crafting 548, drops 430, katgrade 217, forge 120, trade 78`.
   - crafting: `{"type":"crafting","A1":"",..,"C3":"","count":9,"overrideOutputId":"X"}`
     e.g. ENCHANTED_DIAMOND: `[{grid DIAMOND:32 x5, count:1}, {grid DIAMOND_BLOCK:32 x5, count:9}]`.
   - forge: `{"type":"forge","inputs":["TITANIUM_DRILL_4:1","SKYBLOCK_COIN:50000000","DIVAN_ALLOY:1"],"count":1,"overrideOutputId":"DIVAN_DRILL","duration":30}`
     (duration in seconds; coins are the pseudo-item `SKYBLOCK_COIN`).
   - npc_shop: stored **on the NPC's file**, not the product: `ADVENTURER_NPC.recipes[] = {"type":"npc_shop","cost":["SKYBLOCK_COIN:8.0"],"result":"ROTTEN_FLESH:1"}`.
     139 NPC files, 1097 offers, 529 coin-only. Counts can be floats (`8.0`). Some shops take items too
     (e.g. `MITHRIL_PICKAXE` + items -> `REFINED_MITHRIL_PICKAXE`, which has no crafting recipe).
   - trade: `{"type":"trade","cost":"EMERALD:25","result":"ANVIL"}` (cost is a string, on the product's file).
   - katgrade (pets): `{"type":"katgrade","coins":400000000,"time":1728000,"input":"ENDER_DRAGON;3","output":"ENDER_DRAGON;4","items":["SUMMONING_EYE:8"]}`.
   - drops: mob drop tables. Ignore.
3. 6 items have both `recipe` and `recipes`: take all of them as alternatives.
4. `overrideOutputId` never differs from the file id today (checked), but index by it anyway.
5. Other fields: `slayer_req` ("EMAN_7"), `crafttext` ("Requires: HotM 7"), `vanilla`, `parent`. Unlock requirements are informational only.

Parsing an ingredient string: split on the **last** `:`; the part after it is a number (may be float). Sum counts of
the same id across grid cells. `unit cost = sum(ingredient cost) / outputCount`.

Pseudo-currencies (not buyable with coins -> recipe unavailable), seen as ingredients:
`SKYBLOCK_COIN` (= coins, cost 1 each, the only priceable one), `SKYBLOCK_BIT, SKYBLOCK_COPPER, SKYBLOCK_MOTE,
SKYBLOCK_NORTH_STAR, SKYBLOCK_PELT, SKYBLOCK_PEST, SKYBLOCK_GEM, SKYBLOCK_KERNEL, SKYBLOCK_BINGO_POINT,
SKYBLOCK_CARNIVAL_POINT, SKYBLOCK_BRONZE_MEDAL, SKYBLOCK_SILVER_MEDAL, SKYBLOCK_GOLD_MEDAL, SKYBLOCK_FLY,
SKYBLOCK_SPIDER, SKYBLOCK_SILVERFISH`. Every other ingredient id exists as an item file, a bazaar product or an lbin key (checked: 0 dangling ids).

### 2b. How the site should get recipes
Per-item fetching cannot see NPC-shop routes (they live on NPC files) and would need up to ~60 fetches for DIVAN_DRILL.
**Recommended: ship one generated file** `app/data/recipes.json` built by a dev script from the NEU zip, output-indexed
and compact. A ready one is `notes/samples/recipes-compact.json` (3726 outputs, 292 KB, ~50 KB gzip; generator = the
`RECIPES` build loop in `ref-craft.js` + the 3-line compaction in section 8). Format:
```
{ "HYPERION":[["c",1,[["GIANT_FRAGMENT_LASER",8],["NECRON_BLADE",1]]]],
  "ENCHANTED_DIAMOND":[["c",1,[["DIAMOND",160]]],["c",9,[["DIAMOND_BLOCK",160]]]],
  "DIVAN_DRILL":[["f",1,[["TITANIUM_DRILL_4",1],["SKYBLOCK_COIN",50000000],["DIVAN_ALLOY",1]],30]],
  "FLINT":[["n",10,[["SKYBLOCK_COIN",60]]]],
  "ENDER_DRAGON;4":[["k",1,[["ENDER_DRAGON;3",1],["SUMMONING_EYE",8],["SKYBLOCK_COIN",400000000]],1728000]] }
// [type c=craft f=forge n=npc_shop t=trade k=katgrade, outputCount, [[id,count]...], optional duration seconds]
```
Staleness: recipes change rarely; regenerate with the dev script when needed. Optional runtime fallback for an id that is
missing from the file: fetch its NEU JSON from raw.githubusercontent (crafting/forge/trade/katgrade only).

### 2c. Id mapping (NEU id <-> bazaar id <-> AH tag <-> lowestbins key)
| Kind | NEU id | Bazaar | AH tag (Coflnet) | lowestbins |
|---|---|---|---|---|
| normal | `NULL_OVOID` | same | same | same (ignored if bazaar) |
| vanilla variant | `LOG-2`, `INK_SACK-4`, `WOOL-14` | `LOG:2`, `INK_SACK:4` (only the 10 listed above) | - | `INK_SACK-15` style |
| enchant book | `SHARPNESS;5`, `ULTIMATE_WISE;5` | `ENCHANTMENT_SHARPNESS_5`, `ENCHANTMENT_ULTIMATE_WISE_5` | `ENCHANTED_BOOK` | `ENCHANTED_BOOK-SHARPNESS-5` |
| pet | `ENDER_DRAGON;4` (0=COMMON..5=MYTHIC) | - | `PET_ENDER_DRAGON` + row `tier` | `PET-ENDER_DRAGON-LEGENDARY[-100/-200]` |
| rune | (in NEU as runes items) | - | `RUNE_*` | `RUNE-SOULTWIST-3` |
| attribute shard | `ATTRIBUTE_SHARD_X;1` | `SHARD_*` (new hunting shards) | | |
Enchant name aliases (NBT/Coflnet name -> bazaar name), from NEU `enchants.json` + observed:
`ultimate_duplex -> ULTIMATE_REITERATE` (Duplex was renamed; NBT still says duplex, bazaar only has REITERATE_1..5),
`dragon_tracer -> AIMING`, `turbo_cocoa -> TURBO_COCO`, `turbo_cacti -> TURBO_CACTUS`. Uppercase everything else.
`ENCHANTMENT_ULTIMATE_ONE_FOR_ALL_0` exists on bazaar (junk product) next to `_1`.

---------------------------------------------------------------------------------------------------------
## 3. Pricing rules (pseudo-code; `ref-craft.js` is the runnable version)

```
bzOrder(id)    = sell_summary[0].pricePerUnit   | null      // top buy order
bzInsta(id)    = buy_summary[0].pricePerUnit    | null      // insta-buy
lbin(id)       = isBazaar(id) ? null : lowestbins[lbKey(id)] (>0) | null
npcCoins(id)   = min over npc_shop offers whose cost is only SKYBLOCK_COIN of coins/outCount | null

recipeCost(r, stack, price):                       // null = recipe unusable
  total = 0
  for (ing, n) in r.inputs:
    if ing == SKYBLOCK_COIN: total += n; continue
    if ing starts with SKYBLOCK_: return null        // bits, copper, motes...
    if ing in stack: return CYCLE                      // see section 6
    c = price(ing, stack); if c.unit == null: return null
    total += c.unit * n
  return total / r.outCount

// A) FROM SCRATCH: cheapest route, recursively
scratch(id, stack=[]):
  cands = []
  if bzOrder(id) != null:          cands += {bzOrder, "bz-order"}
  else if bzInsta(id) != null:     cands += {bzInsta, "bz-instabuy (no buy orders)"}   // only asks exist
  if lbin(id) != null:             cands += {lbin, "lbin"}
  for r in recipes[id] (craft, forge, npc_shop incl. item-cost shops, trade, katgrade):
     cands += {recipeCost(r, stack+[id], scratch), r.type}   // npc coin-only offers are just recipes with coins
  return strict min (market wins ties); null if no candidate
clean craft cost (scratch) = min over recipes[item] of recipeCost(r, [item], scratch)   // top node is FORCED to craft

// B) EASY WAY: no sub-crafting
easyUnit(id) = min(bzInsta(id), lbin(id), npcCoins(id))           // any that exist
               ?? {scratch(id), flagged "can't be bought, used from-scratch cost"}
clean craft cost (easy) = min over recipes[item] of recipeCost(r, [item], easyUnit)
```
Notes
- Top node of both variants is always crafted (that is the point of the tab); show the item's own lbin/bazaar
  next to it for comparison.
- Forge time is ignored (show `duration` in the breakdown).
- Katgrade (pets) is a valid recipe: `ENDER_DRAGON;4` = EPIC dragon lbin 142.5M + 8 SUMMONING_EYE + 400M coins.
  AH pet page -> NEU id `TYPE;tierIndex` from the tag `PET_TYPE` + tier.
- Recipes with pseudo-currency inputs are dropped entirely (not priced at 0).
- Memoize per id; see section 6 for the cycle-safe rule. Whole DB (3726 outputs) prices in ~130 ms in node.

---------------------------------------------------------------------------------------------------------
## 4. Worked examples = test cases (snapshot prices; outputs in `samples/worked-clean-trees.txt`)

| Item | lbin | From scratch | Easy way | Coflnet /craft/profit craftCost |
|---|---:|---:|---:|---:|
| HYPERION | 504,000,000 | **509,326,963** | **550,785,199** | not listed |
| TERMINATOR | 460,000,000 | **401,869,945** | **471,196,333** | not listed |
| ASPECT_OF_THE_VOID | 7,149,000 | **4,016,816** | **5,954,947** | 5,075,605 |
| JUJU_SHORTBOW | 14,750,000 | **10,088,960** | **12,999,936** | 12,044,804 |
| DIVAN_DRILL (forge) | 1,885,000,000 | **1,568,427,721** | **1,777,000,000** | not listed |
| TITANIUM_DRILL_1 (forge) | 25,542,643 | **7,622,086** | **21,799,263** | not listed |
| REFINED_MITHRIL_PICKAXE (npc shop w/ items) | 2,499,999 | **627,999** | **1,615,419** | not listed |
| DIVAN_CHESTPLATE | 27,000,000 | **14,708,947** | **23,282,465** | |
| CRIMSON_CHESTPLATE (no recipe) | 1,850,000 | n/a (no recipe) | n/a | |
| ENDER_DRAGON;4 (katgrade) | 520,000,000 | 553,060,120 | 553,467,411 | |

Coflnet's craftCost = min(insta-buy, craft) recursively with insta-buy prices, so it sits between our two
variants (AOTV: NULL_OVOID crafted at 149.6k each via insta-buy vs our 124.6k via buy orders / 175.2k insta-buy).

HYPERION
```
scratch 509,326,963 = 8x GIANT_FRAGMENT_LASER bz-order 1,461,042 (11,688,337)
                    + NECRON_BLADE craft 497,638,626 = 24x WITHER_CATALYST bz-order 1,109,943 + NECRON_HANDLE lbin 470,999,999
                      (blade lbin 538,000,000 loses to crafting it)
easy    550,785,199 = 8x GIANT_FRAGMENT_LASER insta 1,598,150 + NECRON_BLADE lbin 538,000,000
```
TERMINATOR (recipe: 8 TESSELLATED_ENDER_PEARL, 3 NULL_BLADE, 128 TARANTULA_SILK, 1 JUDGEMENT_CORE, 4 BRAIDED_GRIFFIN_FEATHER)
```
scratch: TESSELLATED_ENDER_PEARL craft 2,833,920 (vs insta 4,536,000) <- 32 ENCHANTED_LAPIS_LAZULI_BLOCK craft + 80 ABSOLUTE_ENDER_PEARL craft
         NULL_BLADE craft 20,945,683 (vs insta 30,000,000) <- 64 NULL_OVOID craft 124,621 + 3 NULL_EDGE craft + 64 ENCHANTED_QUARTZ_BLOCK craft
         TARANTULA_SILK craft 146,957 <- 128 TARANTULA_WEB bz-order + 32 ENCHANTED_FLINT <- FLINT via NPC (60 coins / 10)
         JUDGEMENT_CORE lbin 174,000,000 (no recipe)
         BRAIDED_GRIFFIN_FEATHER craft 30,887,766 (vs insta 33,799,999)
easy:    36,287,998 + 89,999,999 + 35,708,339 + 174,000,000 + 135,199,996 = 471,196,333
```
DIVAN_DRILL: forge `TITANIUM_DRILL_4 + 50,000,000 coins + DIVAN_ALLOY` -> scratch walks 51 ids, depth 10
(TD4 forge 118.4M <- TD3 <- TD2 <- TD1, GEMSTONE_MIXTURE, MITHRIL_PLATE, REFINED_* ...); DIVAN_ALLOY lbin 1.4B dominates.
Easy = TD4 lbin 327,000,000 + 50,000,000 + DIVAN_ALLOY lbin 1,400,000,000.

### 4b. Listing with modifiers (outputs in `samples/worked-listing-*.txt`)
`samples/listing-hyperion-full.json` (active BIN 1,710,000,000, Heroic, MYTHIC, 10 stars, 3 scrolls, 2 perfect sapphires):

| Line | scratch | easy |
|---|---:|---:|
| clean craft | 509,326,963 | 550,785,199 |
| enchants (25; Prosecute 6 alone = 126.0M / 142.3M) | 133,495,097 | 154,166,473 |
| stars: 3350 ESSENCE_WITHER | 8,212,860 | 8,405,150 |
| master stars 1-5 | 250,590,925 | 269,289,760 |
| recomb | 10,501,551 | 10,699,996 |
| 10 HPB + 5 fuming | 563,230 (HPB crafted 56,323) + 8,040,012 | 871,875 + 8,539,429 |
| art of war, stats book | 11,499,598 + 4,290,021 | 11,909,418 + 8,105,104 |
| 3 ability scrolls | 561,666,627 | 579,785,845 |
| power scroll (AH item) | 367,616 (crafted) | 1,199,998 (lbin) |
| gem slot unlocks COMBAT_0 + SAPPHIRE_0 | 15,399,200 | 23,395,626 |
| 2x PERFECT_SAPPHIRE_GEM | 32,688,787 | 34,082,021 |
| Heroic reforge | 0 (blacksmith reforge) | 0 |
| **total** | **1,546,642,485** | **1,661,235,894** |

Other listing totals (clean + modifiers): TERMINATOR 10-star Precise 724,709,852 / 827,560,627 (listed 809M);
DIVAN_DRILL Glacial full parts 1,902,359,494 / 2,240,919,174 (listed 2.08B); DIVAN_CHESTPLATE 5 perfect gems
153,878,000 / 175,271,815 (listed 169M); Necron chest 10-star 376,904,625 / 417,346,262 (listed 290M);
CRIMSON 10-star: no recipe -> modifiers only 4,022,692 / 4,647,305; ENDER_DRAGON pet: held item 590,000.

---------------------------------------------------------------------------------------------------------
## 5. Listing modifiers (added on top of the clean craft cost)

Input = a Coflnet row. Active rows: `reforge`, `tier`, `enchantments:[{type,level}]`, `flatNbt:{k:"string"}`.
Sold rows: `flattenedNbt` instead of `flatNbt`, no `reforge` (take the words before the Hypixel item `name` in
`itemName`, stripping leading symbols like `✿ ` and trailing `✪➎`). All flat values are **strings**.
Each line is priced with the same two functions: scratch(id) for "from scratch", easyUnit(id) for "easy".

| Modifier | flat key(s) (Coflnet) | raw NBT key (in-game/mod) | Item(s) and amount | Where |
|---|---|---|---|---|
| Enchantments | `enchantments[]` `{type,level}` | `enchantments{name:lvl}` | `ENCHANTMENT_{ALIAS(type)}_{lvl}`, see 5a | bazaar (a few only as AH `ENCHANTED_BOOK-X-L`) |
| Hot potato books | `hpc` | `hot_potato_count` | min(n,10) x `HOT_POTATO_BOOK` + max(0,n-10) x `FUMING_POTATO_BOOK` | bazaar (HPB also craftable: 56,323 < 77,781 order) |
| Recombobulator | `rarity_upgrades` ("1") | `rarity_upgrades` | 1 x `RECOMBOBULATOR_3000` | bazaar |
| Stars 1..k (essence) | `upgrade_level` (fallback `dungeon_item_level`; both can be present, upgrade_level wins and already includes master stars: "10") | same | Hypixel items API `upgrade_costs[i]` for i < min(stars, len): `ESSENCE` -> `ESSENCE_{essence_type}` x amount; `ITEM` -> item_id x amount (e.g. `HEAVY_PEARL`, `KUUDRA_TEETH`); `COINS` | bazaar |
| Master stars | same key, when `upgrade_costs.length == 5 && stars > 5` | | star 6..10 -> `FIRST_MASTER_STAR`..`FIFTH_MASTER_STAR` (1 each, cumulative). Crimson/Kuudra armor has 10 `upgrade_costs` entries instead (no master stars). | bazaar |
| Dungeonized | `dungeon_item` ("1") and item has `dungeon_item_conversion_cost` | `dungeon_item` | `ESSENCE_{type}` x amount (TERMINATOR: 300 DRAGON). Items that are natively dungeon (`dungeon_item:true` in items API, e.g. HYPERION) have no conversion cost. | bazaar |
| Art of War | `art_of_war_count` | same | n x `THE_ART_OF_WAR` | bazaar |
| Art of Peace | `artOfPeaceApplied` | same | 1 x `THE_ART_OF_PEACE` | bazaar |
| Book of Stats | `stats_book` (value = kill counter, not a count) | same | 1 x `BOOK_OF_STATS` | bazaar |
| Ability scrolls | `ability_scroll` = `"IMPLOSION_SCROLL SHADOW_WARP_SCROLL WITHER_SHIELD_SCROLL"` (space-separated) | list tag | 1 each | bazaar |
| Power scroll | `power_ability_scroll` (`SAPPHIRE_POWER_SCROLL`, `AMBER_POWER_SCROLL`) | same | 1 | **AH only** (lbin ~1.2M; craftable) - the mod prices it via bazaar -> NaN |
| Gemstones | slot keys `COMBAT_0:"PERFECT"`, `SAPPHIRE_0:"PERFECT"`, `JADE_1:"FINE"`; universal-type slots add `COMBAT_0_gem:"SAPPHIRE"` (also `MINING_0_gem`, `UNIVERSAL_0_gem`, `DEFENSIVE_0_gem`, `OFFENSIVE_0_gem`, `CHISEL_0_gem`); ignore `X_0.uuid` keys | `gems{...}` (values may be `{quality,uuid}` objects) | `{QUALITY}_{TYPE}_GEM`, TYPE = `k+"_gem"` value else prefix of k | bazaar |
| Gem slot unlocks | `unlocked_slots:"COMBAT_0,SAPPHIRE_0"` | `gems.unlocked_slots` list | for `TYPE_N`: the N-th entry with `slot_type==TYPE` in items API `gemstone_slots`, sum its `costs` (`COINS` coins, `ITEM` item_id x amount). Slots with no `costs` are free and never listed. HYPERION: COMBAT = 250k + 1 FLAWLESS jasper/sapphire/ruby/amethyst; SAPPHIRE = 250k + 4 FLAWLESS_SAPPHIRE. DIVAN_CHESTPLATE: each slot 1 `GEMSTONE_CHAMBER` (AH 8.1M). DIVAN_DRILL: JADE_0 50k+20 FINE_JADE, JADE_1 100k+40 FINE_JADE, MINING_0 250k+flawless jade/amber/topaz. | bazaar / AH |
| Reforge | row `reforge` ("Heroic", "withered", "stellar" - case varies; "None") | `modifier` | NEU `reforgestones.json`: find entry with `reforgeName` equal (case-insensitive): stone = its key (`DRAGON_CLAW` Fabled, `PRECURSOR_GEAR` Ancient, `OPTICAL_LENS` Precise, `JADERALD` Jaded, `FRIGID_HUSK` Glacial, `WITHER_BLOOD` Withered...) + `reforgeCosts[tier]` coins. Not in the file (Heroic, Hasty, Sharp, Spicy...) = blacksmith reforge, count 0. Tier = row tier; Coflnet sends `UNKNOWN` for drills -> items API tier, +1 step if recombobulated. | stones mostly bazaar |
| Wood singularity | `wood_singularity_count` | same | n x `WOOD_SINGULARITY` | bazaar |
| Farming for Dummies | `farming_for_dummies_count` | same | n x `FARMING_FOR_DUMMIES` | bazaar |
| Transmission tuner | `tuned_transmission` | same | n x `TRANSMISSION_TUNER` | bazaar |
| Polarvoid | `polarvoid` | same | n x `POLARVOID_BOOK` | bazaar |
| Bookworm | `bookworm_books` | same | n x `BOOKWORM_BOOK` | bazaar |
| Jalapeno | `jalapeno_count` | same | n x `JALAPENO_BOOK` | bazaar |
| Mana disintegrator | `mana_disintegrator_count` | same | n x `MANA_DISINTEGRATOR` | bazaar |
| Divan powder coating | `divan_powder_coating` | same | 1 x `DIVAN_POWDER_COATING` (forge route 57.8M beats order 62M) | bazaar |
| Etherwarp (AOTV) | `ethermerge` | same | 1 x `ETHERWARP_MERGER` (AH 170k, Voidgloom drop, no recipe) + 1 x `ETHERWARP_CONDUIT` (AH 15.5M; craftable: 24 NULL_OVOID + 16 REFINED_TITANIUM). Same pair SkyHanni uses; not verified on a live row. | AH |
| Drill parts | `drill_part_engine`, `drill_part_fuel_tank`, `drill_part_upgrade_module` (lowercase ids, e.g. `amber_polished_drill_engine`, `perfectly_cut_fuel_tank`, `goblin_omelette_spicy`); duplicates as `engine.id` etc. | same | uppercase value, 1 each | AH (all forgeable) |
| Dye | `dye_item` (`DYE_LAVA`) | same | 1 | AH |
| Runes | `RUNE_{NAME}: "{lvl}"` (e.g. `RUNE_SOULTWIST:"3"`) | `runes{NAME:lvl}` | lbin key `RUNE-{NAME}-{lvl}` | AH |
| Pet held item | `heldItem` (`CROCHET_TIGER_PLUSHIE`) | `petInfo.heldItem` | 1 | AH / bazaar |
| Pet skin | `skin` (pets) | `petInfo.skin` | `PET_SKIN_{skin}` (not verified on a live row) | AH |
| Pet level / candy | `exp`, `candyUsed` | petInfo | not buyable; ignore (candy only lowers value). Use lbin bucket `-100/-200` only for display. | - |
| Armor skins | `skin` (non-pets) | `skin` | unverified mapping; skip or show as "not priced" | AH |
| Kuudra attributes | none seen on any current row (Crimson 10-star has no attributes key) | `attributes{}` legacy | Attributes were replaced by the attribute-shard system; ignore if present. | - |
| Ignore | `uid, uuid, color, cc, boss_tier, compact_blocks, drill_fuel, *_combat_xp, champion_combat_xp, hideInfo...` | | | |

### 5a. Enchantment value
```
name = ALIAS[type] ?? type.toUpperCase()
if type in USE_LEVELED (champion, compact, cultivating, expertise, hecatomb, toxophilite) and lvl>1:
     price ENCHANTMENT_{name}_1  (levels come from use; bazaar has CHAMPION_2..10 with empty books)
if type == efficiency and lvl > 5:  (lvl-5) x SIL_EX   (1-5 from the table)
own   = price of ENCHANTMENT_{name}_{lvl} (bazaar; if not a bazaar product -> lbin ENCHANTED_BOOK-{name}-{lvl})
anvil = if name starts ULTIMATE_, or lvl <= min(5, max_xp_table_levels[type] + 1):   (review round 1: Chance IV+IV != V)
          highest lower level L with a MARKET price (buyOrder/instaBuy/lowestBin) -> 2^(lvl-L) x price(L)
value = fromScratch: min(own, anvil) of the available ones
        easy: own if it can be bought; else min(anvil from buyable books, own from-scratch fallback), flagged
else if lvl <= enchants.json max_xp_table_levels[type]: 0 ("enchanting table, XP only")
else 0 and label "no market" (do not fail the total)
```
Real effect (scratch): Ultimate Wise 5 = 2 x UW4 order 180,001 = 360,001 < UW5 order 514,524; Looting 5 = 2 x L4 11,261 = 22,522
(heuristic! Looting V is a dungeon drop and cannot be anviled from IV; keep the combine only up to V as the approximation
but the level-5-from-4 case is where it is least reliable). Levels above V are never combined (Sharpness 6/7,
Critical 6/7, etc. are all separate bazaar products). No-market examples on the captured rows: smite 7, venomous 7,
dragon_hunter 5, impaling 3/5, bane 7, magmarizer 5, prismatic 5, aiming 5, snipe 3, flame 2, piercing 1.
Mod `ModPricer.enchantValue` doubles up to 4 levels down with no level cap (would price Sharpness 7 as 2 x Sharpness 6
if 7 were missing): replace with the rule above.

### 5b. Prior art in the mod (decompiled 0.5.0, `core/ModPricer.java`, `ItemReader.java`, `Gems.java`)
Reusable and correct: master star ids, gem id `{QUALITY}_{TYPE}_GEM` with `_gem` override (Gems.parse), HPB 10/15 split,
`COUNTED` map (art_of_war_count, wood_singularity_count, farming_for_dummies_count, tuned_transmission), ability scroll list/string
handling, `upgrade_level` then `dungeon_item_level`.
Wrong / missing (fix in the site engine; the mod can adopt later): prices everything at bazaar insta-buy
(`buy_summary[0]`, correctly identified as insta-buy); power scroll looked up on bazaar (AH-only -> NaN); no essence
for stars 1-5; no gem slot unlock costs; no dungeon conversion; no reforge; enchant doubling without cap and no
`ultimate_duplex -> ULTIMATE_REITERATE` alias; missing polarvoid, bookworm, jalapeno, art of peace, stats book,
mana disintegrator, powder coating, ethermerge, drill parts, dye, runes, pet held item. The mod reads raw NBT, so its
keys differ from Coflnet's flat keys (`hot_potato_count` vs `hpc`, `gems` compound vs flat, lists vs space/comma strings,
`modifier` vs `reforge`, `runes{}` vs `RUNE_*`).

---------------------------------------------------------------------------------------------------------
## 6. Edge cases
- **Cycles**: only vanilla block/ingot pairs form cycles (22 ids: IRON_INGOT<->IRON_BLOCK, GOLD, DIAMOND, EMERALD, COAL,
  REDSTONE, LAPIS/INK_SACK-4, SLIME, WHEAT<->HAY_BLOCK, WOOL, and the pseudo medals). Enchanted items are not cyclic.
  Rule: while pricing X, a recipe that uses any id already on the stack is invalid (not "priced at market") -> otherwise
  IRON_INGOT = 1/9 x IRON_BLOCK = 1/9 x 9 IRON_INGOT (a no-op loop). Results that never hit a stack cut are memoized globally,
  the rest recomputed. Depth guard 15 (deepest real tree: DIVAN_DRILL = 10).
- **No recipe and no price**: 478 of 3726 recipe outputs are unpriceable from scratch (their inputs need bits/copper/motes or
  untradeable drops). Show "can't be priced" + which leaf failed; never show 0. CRIMSON_CHESTPLATE, JUDGEMENT_CORE,
  NECRON_HANDLE, GIANT_FRAGMENT_LASER have no recipe at all (drops) -> market price only; clean craft tab says "not craftable".
- **Soulbound/untradeable ingredients** (items API `soulbound`, `can_auction:false`) simply have no bazaar/lbin entry -> the
  recipe fails unless the ingredient is itself craftable. Flag in the easy variant as "can't be bought".
- **Collection/slayer/HotM requirements** (`crafttext`, `slayer_req`): informational only, show as a small note.
- **NPC-sold vanilla items**: FLINT (NPC 60 coins / 10), STRING 10, OBSIDIAN 50, BOOK 20, ROTTEN_FLESH 8 ... they win "from scratch"
  when cheaper than the bazaar order (FLINT in TARANTULA_SILK) and are allowed in "easy" too (buying from an NPC is not crafting).
- **Output count > 1**: `ENCHANTED_DIAMOND` via 160 DIAMOND_BLOCK makes 9; BLAZE_POWDER makes 2 per rod; divide by outCount.
- **Thin/junk markets**: 0.1-coin buy orders are real but misleading; show the route label so the user can see it. Values <= 0 are unavailable.
- **Pets**: tag `PET_X` + row tier -> NEU `X;tierIndex`; katgrade chain is the only "recipe"; level/exp ignored.
- **Enchanted books as the AH item** (tag `ENCHANTED_BOOK`): craft = anvil rule of 5a, not a recipe.
- **Huge trees / request limits**: with the bundled recipes file + bazaar + lowestbins (3 requests total, all bulk) there are no
  per-node requests. Full DB prices in ~130 ms; a single item in < 5 ms.
- **Lowestbins down**: fall back to Coflnet `/item/price/{TAG}/bin` for the (few) non-bazaar leaves actually needed, max ~20
  calls, 300-ms spacing; otherwise mark those leaves unavailable.

---------------------------------------------------------------------------------------------------------
## 7. Files
- `notes/ref-craft.js`, `notes/ref-listing.js`: reference implementation (reads `notes/cache/*`).
- `notes/samples/listing-*.json`: real Coflnet rows (active: `flatNbt`+`reforge`; `sold-hyperion-row.json`: `flattenedNbt`,
  no reforge; `auction-detail-hyperion.json`: typed `nbtData.data` incl. nested `gems` and arrays).
- `notes/samples/neu-item-*.json`: NEU shapes (grid recipe, crafting w/ count 9, forge, katgrade, npc_shop on NPC file, trade).
- `notes/samples/recipes-compact.json`: generated output-indexed recipe DB (see 2b).
- `notes/samples/worked-clean-trees.txt`, `worked-listing-*.txt`: expected outputs for the test cases above.
- `notes/cache/`: bazaar.json, lowestbins.json (+lowestbins_now.json 19:00), hyitems.json, neu-slim.json, neurepo/ (unzipped repo),
  craft_profit.json, bin_*.json/bin2_*.json (raw listing captures), filters_HYPERION.json (Coflnet filter names).

## 8. Generator snippet (dev script -> app/data/recipes.json)
```js
// after building RECIPES exactly like ref-craft.js (recipe + recipes[] crafting/forge/npc_shop/trade/katgrade, indexed by output)
const T = { craft: "c", forge: "f", npc: "n", trade: "t", kat: "k" }, o = {};
for (const id in RECIPES) o[id] = RECIPES[id].map(r => { const a = [T[r.type], r.out, r.inputs.map(i => [i.id, i.n])]; if (r.duration) a.push(r.duration); return a; });
fs.writeFileSync("recipes.json", JSON.stringify(o));
```
