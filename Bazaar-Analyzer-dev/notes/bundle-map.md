# v11 bundle map (for the "craft price" work)

Bundle: `Bazaar-Analyzer-dev/patch/base/index-KsTXbicp-v11.js` (was `Bazaar-Analyzer-v11-backup/app/assets/`) (710,519 chars, 9 physical lines, one ES module, no `import`/`export`, React **19.3.0**).
All anchors below were counted in the minified file with node `indexOf` loops: **count = 1** unless stated.
Readable copy for browsing: `npm i prettier@3` in a scratch dir, then
`node node_modules/prettier/bin/prettier.cjs --parser babel index-KsTXbicp-v11.js > pretty.js` (41k lines; line numbers below refer to that).

## Quick reference

| What | Minified id | Unique anchor / definition (minified text) | Notes |
|---|---|---|---|
| React namespace | `y` | `y=c(u(),1),b=v(),x=y.createContext(void 0),S=` | `(0,y.useState)`, `useMemo`, `useEffect`, `useRef`, `useCallback`, `y.Component`, `y.Fragment` |
| JSX runtime | `b` | same anchor | `(0,b.jsx)(type,props,key)`, `(0,b.jsxs)(type,{children:[...]})`, `b.Fragment` |
| useQueryClient | `S` | same anchor (`S=(e)=>{let t=y.useContext(x)...`) | `let qc=S()` then `qc.fetchQuery({...})` |
| useQuery | `at` | `function at(e,t){return it(e,Fe,t)}` | `at({queryKey,queryFn:({signal})=>...,staleTime,retry})` → `{data,isPending,isError,error,refetch,isFetching}` |
| QueryClient instance | `_f` | `var _f=new Ke({defaultOptions:{queries:{refetchOnWindowFocus:!1}}});` | defined at the very end; use `S()` inside components instead |
| Coflnet GET | `_i(path,signal)` | `async function _i(e,t){` | base `https://sky.coflnet.com/api` (`gi`), throws `Coflnet rate limit reached...` on 429, `Coflnet request failed (N)` otherwise |
| Coflnet auction detail | `Ei(uuid,signal)` | ``Ei=(e,t)=>_i(`/auction/${e}`,t)`` | |
| Coflnet item details | `Di(tag,signal)` | same var block | `/item/{tag}/details` → `{name,tag,tier,category,flags,npcSellPrice,iconUrl}` |
| Coflnet sold page (60 s cache) | `xi(tag,page,signal)` | `async function xi(e,t,n){` | `/auctions/tag/{tag}/sold?page=N&pageSize=500` |
| Coflnet price history | `wi(tag,tf,params,signal)` | `async function wi(e,t,n,r){` | `/item/price/{tag}/history/{day|week|month|full}?<filter params>` |
| Hypixel bazaar fetch | `_t(signal)` | `async function _t(e){` | returns `{lastUpdated,fetchedAt,products:[...]}` |
| Bazaar product mapper | `gt(id,raw)` | `instaBuy:t.buy_summary[0]?.pricePerUnit??null` | see semantics below |
| Bazaar query hook | `Xt()` | `function Xt(){` | `queryKey:['bazaar']`, refetch every `pollSec` (30 s) |
| Bazaar rows hook | `$t()` | `function $t(){` | `{rows,byId:Map(id→row),snapshot,loading,error}`; row = `{id,name,category,product,m,info,...}` |
| Bazaar item info (names) | `Zt()` | `function Zt(){` | only bazaar ids; `{[id]:{name,material,durability,tier,skin}}` |
| All Hypixel items info | `Oa()` | `function Oa(){` | `{[id]:{name,material,durability,tier,category,skin}}`, 24 h localStorage `bz.itemsAll.v1`. **No npc_sell_price kept** (see §1) |
| AH item list | `ka()` | `function ka(){` | Coflnet `/api/items`, AH-able tags |
| Settings store (zustand) | `Kt` | `Kt=Ht()(` | `Kt(s=>s.tax)`, keys: tax,outbid,pollSec,iconStyle,tf,chartType,... localStorage `bz.settings.v1` |
| Format "1.17B" | `en(v,{compact})` | `function en(e,t={}){` | ≥1e9 `x.xxB`, ≥1e7 `xx.xxM`, else `1,234.5` / `123.4` / `1.23`; `{compact:!1}` = never B/M |
| Format commas | `tn(v,maxFrac=1)` | `function tn(e,t=1){` | `tn(1148000000,0)` → `1,148,000,000` |
| Format short k/M/B | `nn(v)` | top-level `function nn(e){` (2nd match is React-internal, nested) | `12.3k`, `4.5M` |
| Signed format | `an(v)` | top-level `function an(e){` (2nd match nested) | `+1.2M` style via `en` |
| Percent | `rn(v,dp=2)` | `function rn(e,t=2){` | |
| "x min ago" | `on(ms)` | `function on(e){` | |
| Item icon | `Jr` | `function Jr({id:e,info:t,size:n=30}){` | `(0,b.jsx)(Jr,{id:tag,info:Oa()?.[tag],size:24})` |
| Display name from id | `Ft(id,info)` / `Pt(id)` | `function Ft(e,t){` | `Ft` = info name or `Pt` fallback (`ENCHANTED_DIAMOND`→`Enchanted Diamond`, `ENCHANTMENT_X_5`→`X V`); wrap in `It()` |
| Strip § codes | `It(s)` | top-level `function It(e){` (2nd match nested) | |
| Clean AH item name | `Wi(s)` | `function Wi(e){` | strips §, ✪ and ➊-➎ |
| Enchant / nbt key label | `$i(type)` = `Zi(key)` | `$i=e=>Zi(e)`, `function Zi(e){` | `ultimate_wise`→`Ultimate Wise` |
| Rarity colors | `Kr` | `Kr={COMMON:` | `{COMMON:'#9aa3b5',UNCOMMON,RARE,EPIC,LEGENDARY:'#fbbf24',MYTHIC,DIVINE,SPECIAL,VERY_SPECIAL,ULTIMATE}` |
| Chart colors | `X` | `var X={ask:` | `ask #fbbf24` (insta-buy, yellow), `bid #38bdf8` (insta-sell, blue), `up #22c55e`, `down #ef4444` |
| Stars | `Aa`, `Ri`, `Vi`, `Bi` | `function Aa({stars:e,className:t=``}){` | `Bi(Ri(flatNbt)||Vi(itemName))` = star count 0-15; `(0,b.jsx)(Aa,{stars})` |
| Player names | `la(uuids)` | top-level `function la(e){` (2nd nested) | hook → `{[uuid]:name}` |
| Card wrapper | `BA_Box` | `function BA_Box({title,right,children}){` | section with header row; body = children (add own padding) |
| Section card (auction page) | `Na` | `function Na({title:e,children:t}){` | |
| Stat card | `Ma` | `function Ma({label:e,value:t,sub:n}){` | used in the auction page 4-card grid |
| Chart hover tooltip | `BA_Tip` | `function BA_Tip({chart,rows}){` | klinecharts-only (subscribes to crosshair). For a listing hover use the CSS classes `ba-tip` / `ba-tip-row` with your own state (see §3) |
| Routes | `st` | `st={market:` | `st.ahItem(tag)`, `st.ahAuction(uuid,fromTag)`, `st.ahPlayer(uuid)`, `st.item(id)` |
| Navigate | `ft(hash)` | `ft=e=>{window.location.hash=e}` | |
| Router hook | `dt()` / parser `ct()` | `function dt(){` | `{page:'ahItem',tag}` / `{page:'ahAuction',uuid}` ... |
| Hash query param | `lt(hash,key)` | top-level `function lt(e,t){` (2nd nested) | `lt(window.location.hash,'f')` |
| Date parse (Coflnet UTC w/o Z) | `ha(s)` | top-level `function ha(e){` (2nd nested) | |
| Filter matcher | `Ji(auction,filters,{reforges})` | `function Ji(e,t,n){` | expects `flattenedNbt` + `highestBidAmount` |
| Filters → Coflnet params | `Xi(filters)` | `function Xi(e){` | `{params,ignored}` |
| AH item page | `gd` | `function gd({tag:e}){` | §2 |
| AH price chart | `id` | `function id({tag:e,params:t,paramsKey:n}){` | §2 |
| Recent sales list | `Sa` | `function Sa({` | §3 |
| Auction (listing) page | `ja` | `function ja({uuid:e}){` | §3 |
| Lowest BIN card | `BA_LowestBin` | `function BA_LowestBin({tag,params,paramsKey,filters,reforges}){` | §3 |
| App / route switch | `gf` | `e.page===\`ahItem\`&&(0,b.jsx)(gd,{tag:e.tag},e.tag)` | |

**Name-collision rule:** module scope already uses every short name (`e,t,n,r,i,a,o,s,c,u,y,b,x,S,C,X,...`). Only add top-level names starting with `BA_`. Inside functions never shadow `y`, `b`, `S`, `at`, `_i`, `X`, `Kr`, `st`, `ft` if you still need them. `node --check` on the output as `.mjs` catches top-level clashes (verified: `Identifier 'gd' has already been declared`).

## 1. Bazaar semantics (exact)

`gt(id, raw)` in the bundle:
```
instaBuy:  raw.buy_summary[0]?.pricePerUnit   // lowest sell offer = what you PAY to insta-buy (yellow, X.ask)
instaSell: raw.sell_summary[0]?.pricePerUnit  // highest buy order = what you GET insta-selling (blue, X.bid)
weightedBuy/weightedSell = quick_status.buyPrice/sellPrice, askVolume/bidVolume, boughtWeek/soldWeek (moving week)
asks = buy_summary mapped {amount,price,orders}, bids = sell_summary mapped
```
Real sample (ENCHANTED_DIAMOND, `samples/hypixel_bazaar_trimmed.json`): buy_summary[0]=1277.7 (instaBuy), sell_summary[0]=1275 (instaSell). instaBuy >= instaSell always.
So for the craft engine: **"from scratch" (buy order) price = `instaSell`** (optionally +0.1 when `Kt(s=>s.outbid)`, like `Et()` does); **"easy way" price = `instaBuy`**. Either can be `null` (empty side of the book) → treat as unavailable.
Access in a component: `let {byId}=$t(); byId.get('ENCHANTED_DIAMOND')?.product.instaSell` (or call `Xt()` directly and use `data.products`). Outside React: `qc.fetchQuery({queryKey:['bazaar'],queryFn:({signal})=>_t(signal),staleTime:5e3})` shares the same cache.

NPC prices: `Oa()`/`Da()` drop `npc_sell_price` from the Hypixel items resource. If the engine needs NPC buy/sell values, either fetch `https://api.hypixel.net/v2/resources/skyblock/items` itself (it is ~MBs; cache it) or patch `Da` to keep it: anchor `category:e.category},n=St(e.skin?.value);` (count 1) → `category:e.category,npc:e.npc_sell_price},n=St(e.skin?.value);` **and** bump the cache key anchor ``Ta=`bz.itemsAll.v1` `` (count 1) to `v2` so old cached entries are not reused. Hypixel gives NPC *sell* price only; NPC *buy* prices are in the NEU repo.

## 2. AH item page `gd` (route `#/ah/item/{TAG}`)

Mounted as `(0,b.jsx)(gd,{tag:e.tag},e.tag)` (keyed by tag → a remount per item). Locals inside `gd`:
`t=Oa()` (items info), `n=aa()` (filter options: `{ready,enchants,reforges,rarities}`), `r` = `ah-details` query, `i=t?.[e]`, **`a` = display name**, `o` = tier,
`[s,c]` = filter list state, `l=hd(s,450)` (debounced filters), `u=ca(e,l,n.reforges)` (sold-scan hook), `d=Xi(l)` (`{params,ignored}`), **`f=JSON.stringify(d.params)`** (paramsKey), `p` = modifier list.

Render order (children of `mx-auto max-w-[1500px] space-y-4 px-4 py-4`):
1. back link `← Auction House`
2. header: `Jr` icon, `h1` name, tag `code`, rarity pill, category pill
3. Filters section (`fd` = FilterPanel)
4. `BA_LowestBin` — anchor ``(0,b.jsx)(BA_LowestBin,{tag:e,params:d.params,paramsKey:f,filters:l,reforges:n.reforges})``
5. chart block — anchor ``(0,b.jsxs)(`div`,{children:[(0,b.jsx)(id,{tag:e,params:d.params,paramsKey:f}),`` (+ "graph can't apply" note)
6. sold list — anchor ``(0,b.jsx)(Sa,{tag:e,...u,filtersActive:l.length>0})``

The AH item page has **no stat cards** today (the 4-card `Ma` grid is on the auction page). Price chart `id` renders a 460px box: toolbar (timeframes `Ci` 1D/1W/1M/ALL, Line/Candles, Fit (removed in v12, replaced by the Mayors toggle), note span anchor ``te||`Average sale price · bars show sales per period` ``), drawing toolbar `Z`, chart canvas, then `BA_VolumeBox` (anchor ``(0,b.jsx)(BA_VolumeBox,{tag:e,tf:u,params:t,paramsKey:n,nonce:v,chartA:BA_ac})``).

**Recommended insertion (tab bar "Price graph | Craft price"):** replace anchor 5's opening
``(0,b.jsxs)(`div`,{children:[(0,b.jsx)(id,{tag:e,params:d.params,paramsKey:f}),``
with ``(0,b.jsxs)(`div`,{children:[(0,b.jsx)(BA_AhTabs,{tag:e,name:a,params:d.params,paramsKey:f}),``
where `BA_AhTabs` renders a small tab row + a "Craft price (clean)" stat in the tab row (visible on both tabs), then either `(0,b.jsx)(id,{tag,params,paramsKey})` or the craft view. Keep the chart **mounted** (hide with `style:{display:'none'}`) if switching back should not refetch/redraw; klinecharts needs a `resize()` after being unhidden, which its own ResizeObserver already does. Clean craft price = the item's base recipe cost (ignore listing modifiers) so it does not depend on filters.

`?f=` filter flow: `#/ah/item/TAG?f=<urlencoded JSON array>` → `BA_initFilters(tag,info)` reads `lt(location.hash,'f')` → `BA_parseFilters` keeps kinds `enchant,stars,reforge,rarity,recomb,hpc,bin,nbt` and fields `type,key,mode,value,min,max` → initial state `s` (only on mount; `BA_keep.url` stops re-applying the same hash; `BA_keep` also carries filters between armor pieces of one set). Changing only `?f=` on the same tag does not re-init (component keyed by tag). Filter object shapes: `Ii(kind,arg)` (`{id,kind,type|key|value|mode,min,max}`).

## 3. Listing data: auction page `ja`, `BA_LowestBin`, sold rows `Sa`

Three sources, slightly different shapes (real samples in `notes/samples/`):

| Source | Endpoint | nbt field | reforge | price | extra |
|---|---|---|---|---|---|
| Sold list rows (`Sa`, via `xi`) | `/auctions/tag/{TAG}/sold?page=N&pageSize=500` | `flattenedNbt` | **absent** (derived from name by `Gi(a,reforges)`) | `highestBidAmount` | `enchantments:[{type,level}]`, `tier`, `bin`, `count`, `id` |
| Active BINs (`BA_LowestBin`) | `/auctions/tag/{TAG}/active/bin?<params>` (max 10 rows) | `flatNbt` (+ raw `nbtData.data`) | `reforge` (`"None"` if none) | `startingBid` | `category`, `anvilUses`, `itemCreatedAt`; BA_LowestBin maps to `{...a,flattenedNbt:a.flatNbt,highestBidAmount:a.startingBid}` before `Ji` |
| Auction detail (`ja`, via `Ei`) | `/auction/{uuid}` | `flatNbt` + `nbtData.data` (typed: numbers, arrays) | `reforge` | `highestBidAmount \|\| startingBid` | `enchantments:[{color,value,type,level}]` (`value` = Coflnet estimate, -1/0 unknown), `bids[]` |

Flattened nbt keys seen (all values are **strings** in `flatNbt/flattenedNbt`):
- `rarity_upgrades:"1"` recombobulated · `hpc:"10"` hot potato count (10 HPB + up to 5 fuming = 15) · `upgrade_level` / `dungeon_item_level` stars (0-10; >5 = master stars) · `dungeon_item:"1"`
- `ability_scroll:"IMPLOSION_SCROLL SHADOW_WARP_SCROLL WITHER_SHIELD_SCROLL"` (space-joined) · `power_ability_scroll:"SAPPHIRE_POWER_SCROLL"`
- gems: `unlocked_slots:"COMBAT_0,JASPER_0"`, `<SLOT>_<n>:"FINE"` = quality, `<SLOT>_<n>_gem:"ONYX"` = gem type for universal slots (COMBAT/OFFENSIVE/DEFENSIVE/UNIVERSAL...), typed slots (`JASPER_0`, `SAPPHIRE_0`) have the gem implied by the slot name; `<SLOT>_<n>.uuid` noise
- `art_of_war_count:"1"`, `stats_book`, `wood_singularity_count`, `RUNE_<NAME>:"3"`, `dye_item`, `color`, `*_combat_xp` (ignore), `uid`/`uuid` (ignore)
- pets: `type`, `exp`, `tier`, `heldItem`, `candyUsed`, ... (see `coflnet_sold_PET_ENDER_DRAGON.json`)
- `modifier` (reforge) is NOT in flatNbt on these endpoints; use `reforge` field or `Gi(name)`.

Auction page `ja` locals: `r` = auction detail, `t=Oa()`, `u` stars, `d` = sorted flatNbt entries, `l` = rarity color. Layout: back link, header (icon, name h1, pills, price at right: anchor `children:tn(r.highestBidAmount||r.startingBid,0)`), 4 `Ma` stat cards (anchor ``(0,b.jsxs)(`div`,{className:`grid grid-cols-2 gap-3 md:grid-cols-4`,children:[(0,b.jsx)(Ma,{label:`Seller`,value:p(r.auctioneerId)}),``), Enchantments `Na` (anchor ``(0,b.jsx)(Na,{title:`Enchantments (${r.enchantments?.length??0})`,``), Modifiers `Na`, Bids `Na`, raw-data toggle.
**Best spot for "Craft cost" on a listing:** insert `(0,b.jsx)(BA_ListingCraft,{a:r}),` right before the Enchantments anchor (a full-width card with the total and hover breakdown), and/or a 5th stat card (the grid is `md:grid-cols-4`; `md:grid-cols-5` exists in technical-v3.css).
Sold rows: each row is an `<a>` — anchor ``(0,b.jsxs)(`a`,{href:st.ahAuction(t.uuid,e),onClick:n=>{n.preventDefault(),ft(st.ahAuction(t.uuid,e))},``; columns from `va=`minmax(200px,2.4fr) minmax(120px,1.1fr) 84px minmax(110px,1fr) 150px`` (count 1), header `(0,b.jsx)(`span`,{children:`Seller`})`. Adding a column = change `va` + header + row children. Don't compute craft cost for hundreds of rows eagerly; compute on hover or only for visible rows.
Lowest BIN card: `best` = cheapest matching row; anchor for its class ``className:`ba-lowbin flex flex-wrap items-center gap-x-6 gap-y-3 rounded-lg border border-line bg-panel2 px-4 py-3 hover:border-accent/50` ``; "Next cheapest" line anchor `` `Next cheapest: ` ``.

Hover breakdown: `BA_Tip` is tied to klinecharts; write a small `BA_HoverTip` (state on `onMouseEnter/Leave` of a `relative` wrapper, render an absolutely positioned `div` with class `ba-tip` and rows `ba-tip-row`; those classes exist in technical-v3.css, `.ba-tip` is `position:absolute;z-index:15;pointer-events:none`). For two lists ("From scratch" / "Easy way") put two columns inside one tooltip.

## 4. Versions and the mod

- `index.html` loads `<script type="module" crossorigin src="./assets/index-KsTXbicp-v11.js">` + `index-CZorzv8u.css` + `technical-v3.css`; `<title>Bazaar Analyzer</title>`.
- Mod `SiteFinder.find()` (decompiled `SiteFinder.java`): GETs `http://127.0.0.1:{47831..47850}/`, requires the HTML to contain `<title>Bazaar Analyzer</title>`, then regex `index-[A-Za-z0-9]+-v(\d+)\.js` → version; `FILTER_VERSION = 11`; `AhTabs` warns if `site.version() < 11`. Links opened: `base + "#/ah/item/" + TAG + "?f=" + urlencoded JSON` (`AhLink.url`).
  → A v12 bundle **must** be named `index-<alnum>-v12.js` (e.g. `index-KsTXbicp-v12.js`), referenced in index.html, and the title must stay exactly `<title>Bazaar Analyzer</title>`. Renaming is also required for cache busting: serve.ps1 sends `Cache-Control: public, max-age=86400` for non-html files, `no-cache` for html.
  → Keep the `#/ah/item/TAG?f=[...]` route and the filter JSON shape (`BA_parseFilters`) unchanged.
- Site → mod: `BA_findMod()` probes `http://127.0.0.1:47860..47869/health` expecting `{mod:'bazaaranalyzer',version}` (mod `LocalServer`), `BA_verCmp(a,b)` semver compare; `BA_OldMod(mod,'0.3.0'|'0.4.0')` gates Trades/Lowball tabs; endpoints used `/trades?limit=300`, `/lowballs`.

## 5. Error handling

No error boundary in app code (the only `getDerivedStateFromError`/`componentDidCatch` hits are React internals). Root: `(0,ot.createRoot)(document.getElementById('root')).render(y.StrictMode > C(QueryClientProvider client=_f) > gf)`. In React 19 an uncaught render error **unmounts the whole app → blank page** (error only in console). Query errors are handled per component via `isError` (no `throwOnError`). New craft UI must be wrapped:
```js
class BA_Boundary extends y.Component{constructor(p){super(p);this.state={err:null}}
static getDerivedStateFromError(err){return{err}}componentDidCatch(e){console.error(`[craft]`,e)}
render(){return this.state.err?(0,b.jsx)(`div`,{className:`rounded-xl border border-line bg-panel p-4 text-sm text-down`,children:`Craft price failed: `+(this.state.err?.message??`error`)}):this.props.children}}
```
(`y.Component` exists; React 19.3.0.) Put a `key` on it if it should reset per item/listing.

## 6. Patch approach (implemented + proven)

`Bazaar-Analyzer-dev/patch/patch_v12.mjs`:
- reads the **v11 backup** bundle, applies `REPLACEMENTS` (`{name,find,replace}`, each `find` must match exactly once in the progressively patched text, else abort),
- inlines `Bazaar-Analyzer-dev/craft/engine.js` if present: strips `export` from `export function|async function|const|let|var|class NAME`, rejects other `export`/`import` forms, wraps it as `const BA_CE=(()=>{...;return{exported names}})();`,
- appends `patch/components.js` if present (plain JS, `(0,b.jsx)` calls, no JSX, top-level names `BA_*`),
- inserts engine + components right before `var _f=new Ke({defaultOptions:{queries:{refetchOnWindowFocus:!1}}});` (count 1; after all original declarations, before render),
- writes `SITE/app/assets/index-KsTXbicp-v12.js` (or `--out <file>`), then runs `node --check` on a temp `.mjs` copy (module = strict mode; also catches top-level name clashes).
- `--noop` self-test mode (one harmless replacement + a tiny inlined engine + one component).
- `node --check` only proves syntax + no duplicate top-level names. Typos in identifiers (e.g. using `Kr` inside a function that shadowed it) only show at runtime: smoke-test the page in a browser (console errors) before shipping.

Verified: `node patch_v12.mjs --noop --out <scratch>/index-KsTXbicp-v12.js` → ok (710519 → 710717 chars); with an empty REPLACEMENTS list and no engine/components the output is byte-identical to v11; injecting `function gd(){}` / `const X=1;` before the anchor fails `node --check` with "Identifier ... has already been declared".
Run: `"C:/Program Files/nodejs/node.exe" C:/Users/Lenovo/Bazaar-Analyzer-dev/patch/patch_v12.mjs [--out file] [--noop]`. Then edit `SITE/app/index.html` script src to `./assets/index-KsTXbicp-v12.js` (keep v11 file there, harmless).

**CSS gotcha:** the Tailwind build only contains classes the original React build used. Hand-written classes that don't exist silently do nothing (e.g. `gap-x-6 gap-y-3 hover:border-accent/50` in BA_LowestBin are missing and were patched via `.ba-lowbin` in technical-v3.css). Check new classes with
`node C:/Users/Lenovo/Bazaar-Analyzer-dev/patch/check_classes.mjs "class1 class2 ..."` (or pass a .js file), and add missing ones as `ba-*` rules in technical-v3.css (or a new css linked from index.html). Theme tokens (CSS vars): `--color-bg,panel,panel2,line,ink,mute,accent(#6ea8fe),buy(#fbbf24),sell(#38bdf8),up,down` → classes `bg-bg bg-panel bg-panel2 border-line text-ink text-mute text-accent text-buy text-sell text-up text-down` (+ `/20`-style opacity variants only where already used, e.g. `bg-accent/20`).

## Samples (`notes/samples/`, fetched 2026-09-21; raw copies in `notes/cache/`)
- `coflnet_sold_HYPERION_first3.json` — sold rows (`flattenedNbt`, scrolls, hpc, recomb, gems)
- `coflnet_auction_detail_HYPERION.json` — `/auction/{uuid}` (flatNbt + nbtData, enchant values, bids)
- `coflnet_active_bin_HYPERION_first3.json` — `/auctions/tag/HYPERION/active/bin` (clean ones, `reforge:"None"`)
- `coflnet_sold_NECRON_CHEST_gems.json` (POWER_WITHER_CHESTPLATE: gem slots, stars, dye), `coflnet_sold_TERMINATOR_aow_runes.json`, `coflnet_sold_PET_ENDER_DRAGON.json`
- `coflnet_craft_recipe_HYPERION.json` — `/craft/recipe/HYPERION` → `{"A1":"GIANT_FRAGMENT_LASER:1",...,"B2":"NECRON_BLADE:1",...}` (3x3 grid, `ID:count`)
- `coflnet_item_details_HYPERION.json`, `hypixel_bazaar_trimmed.json` (2 products, top 3 of each summary)
