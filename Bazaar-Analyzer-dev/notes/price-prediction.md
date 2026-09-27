# Item price prediction (mod: trade window worth + "Debug on hover")

Code: `core/Appraiser.java` (math, tested in `AppraiserTest`), `Appraisal.java` (fetching, caches), `PriceDebug.java`
(tooltip), `TradeWorth.java` (trade window). Backtest: `tools/price-backtest/` (see run.sh).

## Data Coflnet gives (free API, probed 2026-09-25)
- `/auctions/tag/{TAG}/sold?page=N&pageSize=500`: single sales with full item data, **only ~7 days back**; ignores
  filter params (client-side matching with `CoflFilters.matches`).
- `/item/price/{TAG}/history/month?{filters}`: 31 daily buckets {min, max, avg, volume}, filtered server-side
  ("at least" semantics: Stars=>4, PetLevel=90-100, Rarity, PetItem, AbilityScroll, enchant names...).
- `/item/price/{TAG}/history/full?{filters}`: daily since 2020, but it **lags ~7 weeks** (ended 2026-08-06 on 09-25),
  so "90 days" = full history (older part) + month history; the gap in between has no data.
- `/auctions/tag/{TAG}/active/bin?{filters}`: the 10 cheapest matching BIN listings with full item data.
- `/filter/options?itemTag=X`: every filter Coflnet accepts for that item.
- Pets: `flattenedNbt.tier` is the pet's own tier; the row's `tier` (and Coflnet's Rarity filter) includes a Tier
  Boost held item. A boosted Epic Ender Dragon (355M) is not a Legendary one (460M): compared separately.

## Backtest (2026-09-25, 34 item types, 2126 sales from the last 36 h, each predicted from the sales before it)
| method | median error | bias | >20% over | >20% under |
|---|---|---|---|---|
| old: median of the 20 newest sales meeting the filters | 10.1% | +2.7% | 25.2% | 10.0% |
| daily history buckets: median of daily avg | 29.6% | +25% | 55% | 5% |
| daily history buckets: median of daily min | 23.3% | -21% | 4% | 51% |
| clean lowest BIN + 0.7 x modifier cost | 9.8% | -3% | 8% | 19% |
| adjusted recent sales, 6 h half-life, capped at 1.5 x the anchor | 5.7% | 0.0% | 9.9% | 10.0% |
| + tracker slope, no anchor cap for tracked items (2026-09-26) | 5.7% | 0.0% | 10.4% | 8.5% |
| **+ half-life 1 h, mispriced BIN ignored, 1-2 sales, anchor 0.9 (round 3)** | **5.4%** | **0.0%** | **11.0%** | **9.2%** |

Findings:
- Modifier cost (craft engine, from scratch) explains most of the spread: price ~ clean + 0.55-1.0 x mods, lower for
  big mod stacks. ALPHA 0.6-0.8 all do about the same; 0.7 used.
- Recency matters most: half-life 6 h beats 3 days by 3 points (prices drift within a week).
- The history buckets alone are poor ("at least" filters mix in better items); they're only the fallback for items
  with fewer than 3 matching recent sales, and never go above the anchor then.
- Capping by the cheapest matching *sold* BIN hurt a lot (-17% bias: snipes + minimal items). Only a *live* listing
  of the same item (modifier cost within 5%, moved to this item) caps now.
- The easy craft cost caps the worth too (2026-09-27, Appraiser.capByCraft; owner's rule, not backtested): easy
  craft 460M -> worth at most 460M. Skipped when a craft part has no price (the quote is then too low). The price
  debug tooltip says "capped by the easy craft".
- Stat-tracked items (2026-09-26): Final Destination 34% -> 12%, Midas Sword 40% -> 19%. The 1.5 x anchor cap was
  the main error (clean BIN 1.4M vs 30M+ for 40k-kill FD chestplates); now skipped when the item has a tracker. Each
  sale is also moved by coins-per-unit (Theil-Sen median of pairwise slopes, e.g. ~500 coins per FD kill): 18% -> 12%
  FD, 23% -> 19% Midas. Adding additional_coins into the Midas bid tested no better (not done).

## Round 3 (2026-09-26 evening): 94 item types, the thin market, per-modifier factors
Data: 60 more item types dumped (8100 test sales). Backtest on it before this round: 7.2%.
- **Half-life 1 h** (was 6 h): 7.1% -> 6.3% on the 94 items, 5.7% -> 5.4% on the old 34 (0.5 h same, 2-3 h 6.7%).
- **Mispriced lowest BIN ignored** when under 0.5 x the lower quartile of the comparable sales' price minus 70% of
  their mods: the lowest-BIN list had Aspect of the Dragon at 100k (sales 1.6M) and the 1.5 x anchor cap took the
  estimate down with it (90% error -> 25%).
- **Thin market** (tools/price-backtest/Probe.java + Fall.java): 258 sales with the real 30-day history for the mod's
  filters (days before the sale only). Old path (month history capped at the anchor, else the anchor): 15.6% median
  error, 31% of items >20% under. The 1-2 comparable sales that exist beat the history (history alone 20.8%: "at
  least" filters mix in better items). New: 1-2 sales -> their weighted median, never above the anchor
  (**10.3%**, >20% over stays 11.6%, >20% under 31% -> 22%); anchor = clean + **0.9** x mods (was 0.7; 0.9-1.0 best
  on sales without comps: 15.0% -> 12.9%). Caveat: the test sales are from liquid items, so their 1-2 sales are
  fresh; for truly rare items those sales can be days old.
- **Per-modifier-kind factors** (enchant, stars, gems, scrolls, recomb...) fitted on half the item types and tested on
  the other half: no gain (recent path 5.0% -> 5.0%; anchor path 12.7% -> 12.7%, train side only improved =
  overfitting). Comparable sales already have similar modifiers, so the factor barely matters there. Not done.
- Still weak: cheap noisy items (Aspect of the Dragon/End, level-1 pets), rarely sold items.

## Owner's valuations (notes/value-quiz.md, 15 real sales of 24-26 Sep)
Method: lowest BIN with the few modifiers that matter (stars, recomb, gem slots, a rare enchant tier like Ender Slayer 7,
the ultimate) + removable parts at what they sell for (gems on the Bazaar, drill parts); ignores Legion, dyes, pet level
on Ender Dragons, Titan Killer level (but its presence = no Giant Killer 7). Buy-side, so a bit under the sale prices.
Median error vs the real sale: owner 15%, mod 7% (after the fixes below). Fixed from it:
- **Rare enchant tiers** (a level 6+ that isn't a Bazaar product, e.g. Ender Slayer 7, Venomous 7): was priced as 2 x
  the level below (ES7 = 312k; Atomsplits sold 145M with it, 83M with ES6), so never a filter. Now no price and always
  matched (`Modifier.Group.MUST_MATCH`); use-leveled enchants (Champion, Compact, Toxophilite, Hecatomb, Efficiency
  via Silex...) excluded. Atomsplit 85M -> 139M (sold 145M).
- **Gem slots holding a gem count as unlocked** (craft engine, site + mod): Hypixel leaves 39% of them out of
  unlocked_slots, so their unlock cost was missing. Necron's Chestplate 37M -> 44M (sold 51.5M).
- **Hecatomb at any level always matched** (`ModPricer.PRESENCE`): Wither Goggles 45M -> 30M (sold 33M, owner 32M).
  Backtest neutral (6.3% / 5.4%). Tested and left out: Giant Killer / Titan Killer presence (6.3% -> 6.5%; Titan Killer
  alone neutral overall but it narrowed the quiz Gauntlet to 2 sales and the Chimera 4 Claymore to Chimera 5 sales,
  1.13B -> 1.26B). Dark Claymore is 1.13B without it (sold 1.05B, owner 1.1B).
- Still off: Gemstone Gauntlet -42% and Ragnarock -57% (both sales look like outliers: the owner said 75M and 1M).
  Idea not done: ultimate enchants match "at least" the level, so Chimera 5 sales count for a Chimera 4 item (moved
  by only 70% of the cost difference); the owner matches the exact ultimate level.

## Round 4 (2026-09-26 night)
- **Ultimates match the exact level** (`Filter.enchantExact`, site links min = max): a Chimera 5 sale is no comp for a
  Chimera 4 item. Fewer than 3 sales at that level -> widened to "at least" (`CoflFilters.relaxed`). Necron's Chestplate
  (Legion 3) 44M -> 55M (sold 51.5M).
- **Auction (bid) sales** only count when fewer than 3 BIN sales match: 1.4% of sales, priced all over (median 0.83 x
  the BIN price; 1-coin pets, bid-up FD armor). Backtest 5.4% -> 5.3% (old 34 items), 6.3% (94 items, fewer >20% under).
- **Sales history**: .github/workflows/sales-history.yml keeps every sale of the data-tags items on the `sales-history`
  branch (daily, `<TAG>/<YYYY-MM>.jsonl` + `_prices/<day>.json` lowest BINs and Bazaar), starting 2026-09-26.
- Owner: pets by exact XP instead of level is pointless; don't propose it again.
- **Ender Slayer 7 = Ender Slayer 6 + End Stone Idol** (the owner; `CraftEngine.UPGRADE_ITEM`, site craft engine too):
  was "no price"; Idol 53M on the Bazaar, matching the ~62M the market pays for ES7 over ES6.
- **Half-life stretches to the 3rd newest comparable sale** (`Appraiser.K_NEWEST`): with 1 h fixed, an item that sells
  a few times a day was valued at its last sale (quiz Atomsplit 168M from one 230M sale; sold 145M). Tested 1/3/5/8:
  34 items 5.3% -> 5.0% (>20% under 9.0% -> 7.9%), 94 items 6.3% -> 6.3% (>20% over 12.8% -> 12.3%).

