# Listing craft cost (UI part B): component + where to mount it

Part file: `patch/parts/20-listing-craft.js` (plain JS, module scope, all top-level names `BA_CraftL*` / `BA_CraftListing*`).
Public component: **`BA_CraftListingCost({ tag, item, price, lazy?, compact?, className? })`**
- `tag` AH tag of the page (pets: `PET_X`), falls back to `item.tag`; `item` = the Coflnet row as-is; `price` = listing / sold price (whole stack).
- `lazy:!0` = compute only when first hovered/focused (sold rows). `compact:!0` = short trigger `craft ⓘ` then `craft 1.10B`.
- Trigger: `Craft cost: <from-scratch total>` (text-sell). `*` / "(mods only, no recipe)" when the item has no recipe.
- Hover, keyboard focus (Tab, Enter/Space) or click opens a `position:fixed` popover (z-index 60, width `min(700px, 100vw-16px)`):
  it goes below the trigger if it fits, otherwise above if there is more room, is clamped 8px inside the viewport horizontally, gets
  max-height = room on that side (scrolls inside), and repositions on scroll/resize. It closes 150 ms after the mouse leaves, on blur out
  of the wrapper, or on Escape (focus returns to the trigger). Clicks inside the wrapper call `preventDefault + stopPropagation`, so it is
  safe inside the `<a>` rows/cards (does not navigate). `title=""` on the wrapper hides the parent cell's native title tooltip.
- Popover content: summary (listing price, from scratch, easy way, each with "listing is X% cheaper/dearer"; no % when there is no recipe),
  then two columns "From scratch (cheapest)" / "Easy way (insta-buy + lowest BIN)": one expandable "Clean item" line (the expand toggle is
  shared by both columns and shows `flattenTree` rows, max 150), each modifier line (label · qty× item, method badge, total, note as title),
  the total (×count for stacks), then "No price for: ..." from `res.missing`.
- Dedupe: a module `WeakMap(ctx -> Map('TAG|uuid' -> Promise))` shares one `listingCraftCost` per listing per ctx across all mounted
  copies. No abort signal on the shared promise (an unmounting row can't cancel it for others), results are dropped after unmount.
  A rejected promise is removed so a later hover retries. Sold rows use `lazy`, so a sales page view fires **no** craft requests until the
  first hover; after the first one (recipes + lowestbins + 5 MB items API, once per ctx) each hover is sub-millisecond.
- Keeps the previous result on screen while a new ctx (bazaar refresh) recomputes; shows "Refresh failed" if that fails.

## Dependencies on other parts (reconcile when integrating)
| Name | Expected | Fallback in 20-listing-craft.js |
|---|---|---|
| `useCraftCtx()` | hook returning the engine ctx (object with `fetchFn`) **or** a wrapper `{ctx}`; null/undefined while loading | none, must exist (called unconditionally) |
| engine namespace | `BA_craft` (contract) | `BA_CE` (patch script default), else the popover shows "craft engine not loaded" |
| `BA_CraftBoundary` | class error boundary, used as `(0,b.jsx)(BA_CraftBoundary,{children},key)` | `y.Fragment` (no protection) |
| `BA_CraftMethodBadge({method})` | method pill | own small badge (`BA_CraftLstBadge`) |
`BA_CraftTwoLists` is NOT used: the listing popover needs an expandable base line + modifier lines + totals, so it renders its own columns.
Engine calls used: `listingCraftCost(tag,item,ctx,{loadRecipeTree,cleanCraftCost,priceItem})`, `flattenTree(tree)`.
Bundle ids used: `y`, `b`, `en`, `tn`, `Ft`, `It`, `Wi`, `Oa` (only inside the open popover).
Classes: all checked with `node patch/check_classes.mjs patch/parts/20-listing-craft.js` -> "all classes present" (tone classes in
template strings checked separately: text-[10px] text-[11px] text-[13px] text-sell text-buy text-accent text-up text-ink text-mute
text-down hover:bg-white/5 shadow-xl mt-1). NOT in the CSS (avoid): `text-[12px] py-px space-y-px space-y-0.5 space-y-1 pt-1.5 cursor-help`.

## Mounts (REPLACEMENTS entries for patch_v12.mjs)
Each `find` was counted in `C:/Users/Lenovo/Bazaar-Analyzer-v11-backup/app/assets/index-KsTXbicp-v11.js` with an indexOf loop:
**count = 1** for all three. v11 + the three replacements + this part inserted before the `_f` anchor (with a stub `useCraftCtx`) passes
`node --check` as .mjs. Machine-readable copy of find/replace: build it from the table below (strings are exact, backticks included).

### 1. Auction / listing detail page (`function ja({uuid:e})`), under the big price at the top right
Listing data: **`r`** (= `/auction/{uuid}` detail: `flatNbt`, `nbtData`, `reforge`, `tier`, `enchantments`, `count`), tag `r.tag`.
```
find:    (0,b.jsxs)(`div`,{className:`text-xs text-mute`,children:[`coins · `,en(r.highestBidAmount||r.startingBid)]})
replace: (0,b.jsxs)(`div`,{className:`text-xs text-mute`,children:[`coins · `,en(r.highestBidAmount||r.startingBid)]}),(0,b.jsx)(BA_CraftListingCost,{tag:r.tag,item:r,price:r.highestBidAmount||r.startingBid,className:`mt-1`})
```
(Parent is `ml-auto text-right`, so the trigger sits right-aligned on its own line; the popover clamps to the right viewport edge.)

### 2. Lowest BIN card (`function BA_LowestBin(...)`), under the cheapest listing's price
Listing data: **`best`** (active BIN row mapped to `{...a, flattenedNbt:a.flatNbt, highestBidAmount:a.startingBid}`; has `uuid`,
`reforge`, `tier`, `enchantments`), tag variable **`tag`**, price `best.startingBid`. The card is an `<a>`; the component stops the click.
```
find:    (0,b.jsxs)(`div`,{className:`text-xs text-mute`,children:[`coins · `,en(best.startingBid)]})
replace: (0,b.jsxs)(`div`,{className:`text-xs text-mute`,children:[`coins · `,en(best.startingBid)]}),(0,b.jsx)(BA_CraftListingCost,{tag,item:best,price:best.startingBid,className:`mt-1`})
```

### 3. Sold-list rows (`function Sa({tag:e,...})`), inline after the compact "Sold for" value (no new column, no extra row line)
Listing data: **`t`** (sold row: `flattenedNbt`, `itemName`, `tier`, `enchantments`, `uuid`, `count`, no `reforge`; the engine
derives the reforge from `itemName`), tag **`e`**, price `t.highestBidAmount`. Must be `lazy` (40 rows per page, more on scroll).
```
find:    (0,b.jsx)(`span`,{className:`block text-[11px] text-mute`,children:en(t.highestBidAmount)})
replace: (0,b.jsxs)(`span`,{className:`block text-[11px] text-mute`,children:[en(t.highestBidAmount),` `,(0,b.jsx)(BA_CraftListingCost,{tag:e,item:t,price:t.highestBidAmount,lazy:!0,compact:!0})]})
```
Renders e.g. `1.15B craft ⓘ` under `1,148,000,000`; after the first hover `1.15B craft 1.10B`. The cell is `minmax(120px,1.1fr)`
of a `min-w-[760px]` grid; at the narrowest width the second line may wrap, which is fine (text-right, no truncate).
If the integrator finds it cluttered, drop mount 3: the other two do not depend on it.

## Verification done (no browser; the integrator must check hover/placement in the browser)
- SSR harness (`patch/tests/listing-craft.head.mjs` + part file + `listing-craft.tail.mjs`, concatenated into one .mjs; uses React 19.3
  from `C:/Users/Lenovo/bazaar graphs/node_modules` and the engine fixtures): all 10 fixture listings render with the expected totals,
  e.g. hyperion-full 1,546,642,485 / 1,661,235,894, terminator-full 724,709,852 / 827,560,627, crimson (no recipe) 4,022,692 / 4,647,305;
  both expanded and collapsed; loading / error / no-ctx states; the same listing twice returns the same promise (dedupe).
  Run: `cat patch/tests/listing-craft.head.mjs patch/parts/20-listing-craft.js patch/tests/listing-craft.tail.mjs > <scratch>/run.mjs && node <scratch>/run.mjs` -> `ALL OK`.
- Browser checks still to do: popover flip near the bottom of the viewport (sold rows), clamp at the right edge (auction page), hover gap
  from trigger into popover keeps it open, Tab into the "Clean item" button keeps it open, Escape closes, clicking inside a sold row/lowest-BIN
  card does not navigate, no console errors.

## Integrated (2026-09-22)
All 3 mounts are in patch_v12.mjs REPLACEMENTS and verified in the browser (see notes/craft-ui.md). The part now uses the shared helpers BA_craftName/BA_craftQty/BA_craftFmt and the ba-cr-* look of BA_CraftTwoLists. SSR test: `sh patch/tests/run-listing-craft.sh` (adds BA_el + the ids/names/badge section of 00-shared.js) -> ALL OK.
