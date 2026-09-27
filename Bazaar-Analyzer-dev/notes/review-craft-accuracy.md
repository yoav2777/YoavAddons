# Review round 1: craft-accuracy (2026-09-22)

Independent reference solver: `notes/cache/rv-craft/scripts/ref.mjs`. It parses the raw NEU repo (`notes/cache/neurepo/repo/items`) itself
and value-iterates the prices to a fixed point, with no engine code involved. Data snapshot 08:25: `rv-craft/bazaar.json`, `lbin.json`,
`profit.json` (Coflnet craft/profit), plus `sold_{HYPERION,TERMINATOR,DIVAN_CHESTPLATE}.json` (Coflnet sold rows, pageSize=100).
Run: `cd notes/cache/rv-craft/scripts; node all.mjs` (whole DB), `node run1.mjs HYPERION TERMINATOR ...`, `node trees.mjs HYPERION#4`,
`node lst.mjs` (3 listings), `node hand.mjs` (per-line check), `node cofl.mjs`, `node sums.mjs`.

## Clean craft: matches
- All 3726 recipe outputs: the engine's `priceItem` fromScratch equals the reference exactly (0 diffs, no NaN or negative values).
  Root trees (both modes) also match. The only 9 differences are bazaar raw items (SLIME_BALL, DIAMOND...) where the reference allows a
  cycle through the root and the engine correctly forbids it.
- Checked items: HYPERION 482,813,734 / 550,785,186. TERMINATOR 414,349,722 / 481,480,496. AOTV 4,829,040 / 6,397,766.
  JUJU 10,531,840 / 12,355,002. DIVAN_DRILL 1,592,260,469 / 1,783,000,000. TITANIUM_DRILL_1 9,463,219 / 22,059,753.
  PET_ENDER_DRAGON@LEGENDARY 547,999,761 / 548,548,294. CRIMSON_CHESTPLATE is uncraftable. Random picks: PRESSURE_TALISMAN,
  FRESHLY_BAKED_TALISMAN (SKYBLOCK_KERNEL, can't be priced), PET_ITEM_HARDENED_SCALES_UNCOMMON (npc), ZOMBIE_LEGGINGS, RAILS
  (makes 16), ASPECT_OF_THE_END (BLAZE_POWDER makes 2), ALL_SKILLS_SUPER_BOOST (npc ingredient).
- 61,569 tree nodes: each node's total equals the sum of its children, except easy WHEAT (below).
- Coflnet craft/profit (891 items): 850 fall inside [fromScratch, easy], 16 below and 24 above. All the gaps come from data: NPC offers
  missing from NEU (OBSIDIAN 14, SNOW_BLASTER 75k), Coflnet insta-buy depth or odd prices (SMALL_BACKPACK LEATHER 192.9 each), and
  lowest BIN vs Coflnet's price.

## Listing modifiers: problems found
1. The anvil route in `cmEnchantLine` combines every non-ultimate enchant up to V and can beat a real book price. Chance 5 comes out
   as 2 x Chance 4 at buy order 1,000 = 2,000, while ENCHANTMENT_CHANCE_5 has a buy order of 14,559,287. Scavenger 5 = 3,800 vs 248,755.
   Life Steal 4 = 4 x LIFE_STEAL;2 = 1,546 (from a NEU paper+golden-apple "recipe") vs 310,004. In easy mode it also takes the anvil
   route with from-scratch fallback prices even when the exact book can be insta-bought (Life Steal 4 insta-buy is 740,993, Overload 5 is 24.65M).
2. From scratch takes the top buy order even when it is a dead lowball order: ULTIMATE_REITERATE_5 has a buy order of 2.8 against an
   insta-buy of 23.37M. 142 of 1797 products have a buy order below 5% of insta-buy.
3. Sold-row reforge parsing: "Shiny Withered Hyperion" gives the reforge "Shiny Withered", so no stone is found and the WITHER_BLOOD
   stone (1.54M) + 60k apply cost are dropped silently. `§6` colour codes are not stripped, which produces labels like "6dimensional reforge" and "6 reforge".
4. In easy mode, root ingredients that fall back to from-scratch are priced with `ccScratch(x, [])`, so the root is not on the stack.
   WHEAT easy: the root total is 2.3 while its only child HAY_BLOCK is unavailable (null).
