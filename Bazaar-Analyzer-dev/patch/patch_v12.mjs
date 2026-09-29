// Reproducible patch: v11 bundle (read-only backup) -> v12 bundle for the live site.
// Usage:
//   node patch_v12.mjs                 -> writes SITE/app/assets/index-KsTXbicp-v12.js, copies ba-craft.css,
//                                         theme-claude.css and the recipe file (app/data/recipes.json), points
//                                         SITE/app/index.html at v12 (+ both css links, favicon colours),
//                                         moves the v11 bundle out of SITE assets
//   node patch_v12.mjs --out <file>    -> only writes the bundle somewhere else (scratch / tests), SITE untouched
//   node patch_v12.mjs --noop --out <file> -> skip REPLACEMENTS/engine/parts (pipeline self-test)
// Idempotent: always starts from the v11 backup, so running it twice gives the same output.
//
// What goes into the bundle (inserted right before INSERT_BEFORE, i.e. after every original declaration):
//   1. craft/craftCore.js + craft/craftModifiers.js as `const BA_craft=(()=>{...;return{...exports}})();`
//   2. every patch/parts/*.js, sorted by file name (plain JS, (0,b.jsx) calls, top-level names BA_* only)
// Then the exact-match REPLACEMENTS below mount the new components (each must match exactly once).
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import os from 'node:os';
import crypto from 'node:crypto';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DEV = path.join(HERE, '..');
// v11 bundle (read-only): patch/base/ in the repo.
const SRC = path.join(HERE, 'base', 'index-KsTXbicp-v11.js');
// The built site lives in the repo (yoav-addons/Bazaar-Analyzer-v12); C:/Users/Lenovo/Bazaar-Analyzer points at it.
const SITE_APP = path.join(DEV, '..', 'Bazaar-Analyzer-v12', 'app');
const DEFAULT_OUT = SITE_APP + '/assets/index-KsTXbicp-v12.js';
const ENGINE_FILES = [path.join(DEV, 'craft', 'craftCore.js'), path.join(DEV, 'craft', 'craftModifiers.js')];
const PARTS_DIR = path.join(HERE, 'parts');
const CSS_SRC = path.join(HERE, 'ba-craft.css');           // copied to SITE/app/assets/ba-craft.css
const THEME_SRC = path.join(HERE, 'theme-claude.css');    // copied to SITE/app/assets/theme-claude.css (notes/theme.md)
const RECIPES_SRC = path.join(DEV, 'notes', 'samples', 'recipes-compact.json'); // -> SITE/app/data/recipes.json (craft/buildRecipes.mjs)
const OLD_BUNDLES = path.join(HERE, 'prev-bundles');      // where the old v11 bundle is moved to

const args = process.argv.slice(2);
const noop = args.includes('--noop');
const outIdx = args.indexOf('--out');
const OUT = outIdx >= 0 ? args[outIdx + 1] : DEFAULT_OUT;
if (!OUT) throw new Error('--out needs a path');
const shipping = path.resolve(OUT) === path.resolve(DEFAULT_OUT);

// Every entry must match EXACTLY once in the (progressively patched) source, or the script aborts.
// `find` strings are copied verbatim from the minified v11 bundle (see notes/bundle-map.md, notes/craft-ui.md).
const REPLACEMENTS = [
  {
    // AH item page (gd): "Price graph | Craft price" tabs + clean craft price stat around the price chart.
    // Locals of gd: e=tag, a=display name, o=tier, l=debounced filters, d={params,ignored}, f=paramsKey.
    name: 'AH item: craft tabs around the price chart',
    find: '(0,b.jsxs)(`div`,{children:[(0,b.jsx)(id,{tag:e,params:d.params,paramsKey:f}),',
    replace: '(0,b.jsxs)(`div`,{children:[(0,b.jsx)(BA_AhTabs,{tag:e,name:a,tier:o,filters:l,params:d.params,paramsKey:f}),',
  },
  {
    // AH price chart (id): dashed "Craft" line at the from-scratch craft price (own overlay group, locked).
    // Locals of id: e=tag, BA_ac=klinecharts instance (state), m=load state (`loading|ready|empty|error`), ie=fit-to-range callback.
    name: 'AH chart: craft price line',
    find: '(0,b.jsx)(BA_VolumeBox,{tag:e,tf:u,params:t,paramsKey:n,nonce:v,chartA:BA_ac})',
    replace: '(0,b.jsx)(BA_CraftChartLine,{tag:e,chart:BA_ac,ready:m,fit:ie}),(0,b.jsx)(BA_VolumeBox,{tag:e,tf:u,params:t,paramsKey:n,nonce:v,chartA:BA_ac})',
  },
  // Listing craft cost (parts/20-listing-craft.js, notes/listing-craft-mounts.md): "Craft cost: X" + hover breakdown.
  {
    // Auction detail page (ja): r = /auction/{uuid} detail, under the big price (parent is ml-auto text-right).
    name: 'Auction page: listing craft cost',
    find: '(0,b.jsxs)(`div`,{className:`text-xs text-mute`,children:[`coins · `,en(r.highestBidAmount||r.startingBid)]})',
    replace: '(0,b.jsxs)(`div`,{className:`text-xs text-mute`,children:[`coins · `,en(r.highestBidAmount||r.startingBid)]}),(0,b.jsx)(BA_CraftListingCost,{tag:r.tag,item:r,price:r.highestBidAmount||r.startingBid,className:`mt-1`})',
  },
  {
    // Lowest BIN card (BA_LowestBin): best = cheapest active BIN row, tag = page tag. The card is an <a>; the component stops clicks.
    name: 'Lowest BIN card: listing craft cost',
    find: '(0,b.jsxs)(`div`,{className:`text-xs text-mute`,children:[`coins · `,en(best.startingBid)]})',
    replace: '(0,b.jsxs)(`div`,{className:`text-xs text-mute`,children:[`coins · `,en(best.startingBid)]}),(0,b.jsx)(BA_CraftListingCost,{tag,item:best,price:best.startingBid,className:`mt-1`})',
  },
  {
    // Sold-list rows (Sa): t = sold row, e = tag. lazy: no craft work until the first hover/focus.
    name: 'Sold rows: listing craft cost',
    find: '(0,b.jsx)(`span`,{className:`block text-[11px] text-mute`,children:en(t.highestBidAmount)})',
    replace: '(0,b.jsxs)(`span`,{className:`block text-[11px] text-mute`,children:[en(t.highestBidAmount),` `,(0,b.jsx)(BA_CraftListingCost,{tag:e,item:t,price:t.highestBidAmount,lazy:!0,compact:!0})]})',
  },
  // Book Flips (parts/40-book-flips.js, notes/book-flips.md): Bazaar page sub-tabs "Market | Book Flips" (#/?sub=books).
  {
    // App route switch (gf): the market route renders BA_BazaarPage, which renders the original af or BA_BookFlips.
    name: 'Bazaar route: Market | Book Flips',
    find: 'e.page===`market`&&(0,b.jsx)(af,{mode:`all`})',
    replace: 'e.page===`market`&&(0,b.jsx)(BA_BazaarPage,{})',
  },
  {
    // Watchlist route: Market (af) | Book Flips watchlist (#/watchlist?sub=books).
    name: 'Watchlist route: Market | Book Flips',
    find: 'e.page===`watchlist`&&(0,b.jsx)(af,{mode:`watchlist`})',
    replace: 'e.page===`watchlist`&&(0,b.jsx)(BA_WatchlistPage,{})',
  },
  {
    // Market page (af, e = mode): sub-tab switch before the "Bazaar market"/"Watchlist" title.
    name: 'Market header: sub-tab switch',
    find: '(0,b.jsx)(`h1`,{className:`text-lg font-semibold`,children:e===`watchlist`?`Watchlist`:`Bazaar market`}),',
    replace: '(0,b.jsx)(BA_CraftBoundary,{label:`Book Flips tabs`,children:(0,b.jsx)(BA_BazaarSubTabs,{sub:`market`,base:e===`all`?st.market:st.watchlist})}),(0,b.jsx)(`h1`,{className:`text-lg font-semibold`,children:e===`watchlist`?`Watchlist`:`Bazaar market`}),',
  },
  // Mayor bands (parts/50-mayor-bands.js, notes/mayor-bands.md): one grey band per elected mayor
  // behind the AH price chart, plus the mayor in the chart's hover tooltip.
  {
    // AH price chart (id): FIRST child of the `relative min-w-0 flex-1` wrapper, so it paints
    // under the (transparent) klinecharts canvases. Locals: BA_ac=chart instance, m=load state, u=timeframe.
    name: 'AH chart: mayor band layer',
    find: '(0,b.jsx)(`div`,{ref:i,className:`absolute inset-0`}),m===`loading`&&',
    replace: '(0,b.jsx)(BA_MayorBands,{chart:BA_ac,ready:m,tf:u}),(0,b.jsx)(`div`,{ref:i,className:`absolute inset-0`}),m===`loading`&&',
  },
  {
    // Legend factory `nd` (used only by the AH chart `id`): add a "Mayor: " row to the crosshair tooltip.
    name: 'AH chart: mayor row in the tooltip',
    find: '{title:`Sales: `,value:{text:nn(t.volume),color:X.bid}}]:[];',
    replace: '{title:`Sales: `,value:{text:nn(t.volume),color:X.bid}},...BA_mayorLegendRow(t.timestamp)]:[];',
  },
  {
    // Bazaar price chart (Hd): same band layer, FIRST child of its `relative min-w-0 flex-1` wrapper.
    // Locals: BA_ca=chart instance, d=load state, i=settings store (i.tf), ma[tf].barMs = the chart's bar length.
    name: 'Bazaar chart: mayor band layer',
    find: '(0,b.jsx)(`div`,{ref:a,className:`absolute inset-0`}),(0,b.jsx)(BA_Tip,{chart:BA_ca,rows:BA_rowsA})',
    replace: '(0,b.jsx)(BA_MayorBands,{chart:BA_ca,ready:d,tf:i.tf,periodMs:ma[i.tf].barMs}),(0,b.jsx)(`div`,{ref:a,className:`absolute inset-0`}),(0,b.jsx)(BA_Tip,{chart:BA_ca,rows:BA_rowsA})',
  },
  {
    // Bazaar price chart hover tooltip (BA_rowsA -> BA_Tip rows {k,v,c}): add a "Mayor" row.
    name: 'Bazaar chart: mayor row in the tooltip',
    find: '{k:`High / Low`,v:`${en(bar.high,{compact:!1})} / ${en(bar.low,{compact:!1})}`}]};',
    replace: '{k:`High / Low`,v:`${en(bar.high,{compact:!1})} / ${en(bar.low,{compact:!1})}`},...BA_mayorTipRow(bar.timestamp)]};',
  },
  {
    // "Mayors" on/off button (off by default, hover says Beta) in the AH chart toolbar, where the Fit button was
    // (Fit is gone: the chart already fits the range on every load; `ie` itself stays for that and BA_CraftChartLine).
    name: 'AH chart: Mayors toggle (replaces Fit)',
    find: '(0,b.jsx)(`button`,{onClick:ie,title:`Zoom to fit the whole range`,className:`rounded-md px-2 py-1 text-mute hover:bg-white/5 hover:text-ink`,children:`Fit`}),(0,b.jsx)(`span`,{className:`ml-auto text-xs ${te?',
    replace: '(0,b.jsx)(BA_MayorToggle,{}),(0,b.jsx)(`span`,{className:`ml-auto text-xs ${te?',
  },
  {
    // Same button in the Bazaar chart toolbar, just before the right-hand camera / fullscreen group.
    name: 'Bazaar chart: Mayors toggle',
    find: '(0,b.jsxs)(`div`,{className:`ml-auto flex items-center gap-0.5 text-mute`,children:[(0,b.jsx)(`button`,{title:`Save chart as image`',
    replace: '(0,b.jsx)(BA_MayorToggle,{}),(0,b.jsxs)(`div`,{className:`ml-auto flex items-center gap-0.5 text-mute`,children:[(0,b.jsx)(`button`,{title:`Save chart as image`',
  },
  // AH tweaks (parts/70-ah-tweaks.js).
  {
    // AH price chart (id): hover box at the cursor with that bar's price (BA_Tip, like the Bazaar chart), instead of
    // only the y-axis label at the cursor height. u = timeframe (1M/ALL bars are UTC days, td(u); else local `ed`).
    name: 'AH chart: hover tooltip at the cursor',
    find: '(0,b.jsx)(`div`,{ref:i,className:`absolute inset-0`}),m===`loading`&&',
    replace: '(0,b.jsx)(`div`,{ref:i,className:`absolute inset-0`}),(0,b.jsx)(BA_Tip,{chart:BA_ac,rows:BA_ahTipRows(td(u)?`UTC`:ed)}),m===`loading`&&',
  },
  {
    // Auction House item list (fo): drop the category and rarity chips; the list always shows every item.
    name: 'AH list: no category / rarity chips',
    find: '(0,b.jsx)(po,{items:[`all`,...u],value:o,onChange:s,text:e=>e===`all`?`All`:uo(e)}),(0,b.jsx)(po,{items:[`all`,...d],value:c,onChange:l,text:e=>e===`all`?`Any rarity`:uo(e),color:e=>Kr[e]}),',
    replace: '',
  },
  {
    // Auction page (ja): the item name links to its AH item page (all sales + price graph).
    name: 'Auction page: item name -> item page',
    find: 'style:{color:l},children:[Wi(r.itemName),',
    replace: 'style:{color:l},children:[(0,b.jsx)(`a`,{href:st.ahItem(r.tag),className:`hover:underline`,children:Wi(r.itemName)}),',
  },
  {
    // FilterPanel (fd) needs the item tag for the item-specific "Wither Impact" entry.
    name: 'AH filters: pass the tag',
    find: '(0,b.jsx)(fd,{filters:s,onChange:c,',
    replace: '(0,b.jsx)(fd,{tag:e,filters:s,onChange:c,',
  },
  {
    // fd: "Common" entries of the Add-filter menu (+ Wither Impact on the wither blades, found by "wimp" too).
    name: 'AH filters: Wither Impact menu entry',
    find: 'l=dd.filter(e=>e.label.toLowerCase().includes(s)||e.hint.includes(s))',
    replace: 'l=BA_addFilterCommon(e.tag,s)',
  },
  {
    // fd add handler (r=kind, a=arg; t=filters, n=onChange, i=setOpen, o=setQuery): Wither Impact = an ability_scroll filter.
    name: 'AH filters: Wither Impact add',
    find: 'd=(r,a)=>{let s=Ii(r,a);',
    replace: 'd=(r,a)=>{if(r===`wimp`){n(BA_wimpApply(t,e.modifiers)),i(!1),o(``);return}let s=Ii(r,a);',
  },
  {
    // modifier chip (ud, nbt): search box picker instead of <select>.
    name: 'AH filters: modifier chip search picker',
    find: '(0,b.jsxs)(`select`,{value:e.value,onChange:e=>i({value:e.target.value}),className:od,children:[!e.value&&(0,b.jsx)(`option`,{value:``,children:`choose…`}),(t?.values??[]).map(e=>(0,b.jsx)(`option`,{children:e},e))]})',
    replace: '(0,b.jsx)(BA_Pick,{value:e.value,options:t?.values??[],onChange:e=>i({value:e})})',
  },
  {
    // reforge chip (ud): search box picker instead of <select>.
    name: 'AH filters: reforge chip search picker',
    find: '(0,b.jsxs)(`select`,{value:e.value,onChange:e=>i({value:e.target.value}),className:od,children:[!e.value&&(0,b.jsx)(`option`,{value:``,children:`choose…`}),r.reforges.map(e=>(0,b.jsx)(`option`,{children:e},e))]})',
    replace: '(0,b.jsx)(BA_Pick,{value:e.value,options:r.reforges,onChange:e=>i({value:e})})',
  },
  {
    // fd: reforges matching the search (all of them when empty) for the "Reforges" menu section.
    name: 'AH filters: reforge list match',
    find: 'u=e.modifiers.filter(e=>Zi(e.key).toLowerCase().includes(s)',
    replace: 'BA_rf=BA_reforgeMatch(e.reforges,s),u=e.modifiers.filter(e=>Zi(e.key).toLowerCase().includes(s)',
  },
  {
    // fd add handler: d(`reforge`, name) keeps the picked reforge (plain "Reforge" still defaults to the first).
    name: 'AH filters: add a picked reforge',
    find: 's.kind===`reforge`&&(s.value=e.reforges.find(e=>e!==`None`)??``)',
    replace: 's.kind===`reforge`&&!a&&(s.value=e.reforges.find(e=>e!==`None`)??``)',
  },
  {
    // fd menu: "Reforges" section right after Common, one entry per reforge.
    name: 'AH filters: reforges section',
    find: 'l.map(e=>(0,b.jsx)(md,{onClick:()=>d(e.kind),label:e.label,hint:e.hint},e.kind)),',
    replace: 'l.map(e=>(0,b.jsx)(md,{onClick:()=>d(e.kind),label:e.label,hint:e.hint},e.kind)),BA_rf.length>0&&(0,b.jsx)(pd,{title:`Reforges`}),BA_rf.map(r=>(0,b.jsx)(md,{onClick:()=>d(`reforge`,r),label:r,hint:`reforge`},`rf:`+r)),',
  },
  {
    name: 'AH filters: reforges count for "nothing matches"',
    find: '!l.length&&!c.length&&!u.length&&',
    replace: '!l.length&&!BA_rf.length&&!c.length&&!u.length&&',
  },
  // "Clean" AH filter (parts/50-mayor-bands.js): Coflnet `Clean=yes` for the price graph + lowest-BIN
  // card, BA_isCleanListing() for the client-side sold list.
  {
    name: 'Clean filter: single-instance kind',
    find: 'Fi=[`stars`,`reforge`,`rarity`,`recomb`,`hpc`,`bin`,`price`];',
    replace: 'Fi=[`stars`,`reforge`,`rarity`,`recomb`,`hpc`,`bin`,`price`,`clean`];',
  },
  {
    // filter factory Ii(kind,arg) — `clean` has no options, it is on while the chip is there.
    name: 'Clean filter: factory',
    find: 'case`bin`:return{id:n,kind:e,value:!0};',
    replace: 'case`bin`:return{id:n,kind:e,value:!0};case`clean`:return{id:n,kind:e,value:!0};',
  },
  {
    // "Common" entries of the Add-filter menu (dd) — Clean first, it is the broadest one.
    name: 'Clean filter: add-filter menu entry',
    find: 'var dd=[{kind:`stars`,label:`Stars`,hint:`dungeon stars`}',
    replace: 'var dd=[{kind:`clean`,label:`Clean`,hint:`no modifiers at all`},{kind:`stars`,label:`Stars`,hint:`dungeon stars`}',
  },
  {
    // active-filter chip (ud)
    name: 'Clean filter: chip',
    find: 'case`bin`:return(0,b.jsx)(ld,{label:`Sold as`,onRemove:n,',
    replace: 'case`clean`:return(0,b.jsx)(ld,{label:`Clean`,onRemove:n,children:(0,b.jsx)(`span`,{className:`text-xs text-mute`,children:`no modifiers`})});case`bin`:return(0,b.jsx)(ld,{label:`Sold as`,onRemove:n,',
  },
  {
    // filters -> Coflnet query params (Xi): the price history and the active-BIN list honour Clean server side.
    name: 'Clean filter: Coflnet param',
    find: 'case`bin`:t.Bin=String(i.value);break;',
    replace: 'case`bin`:t.Bin=String(i.value);break;case`clean`:t.Clean=`yes`;break;',
  },
  {
    // client-side matcher (qi), used for the sold list and to re-check BIN rows. n={reforges}.
    name: 'Clean filter: client-side match',
    find: 'case`bin`:return e.bin===t.value;',
    replace: 'case`bin`:return e.bin===t.value;case`clean`:return BA_isCleanListing(e,n&&n.reforges);',
  },
  {
    // ?f= links (the mod builds them) must be able to carry Clean.
    name: 'Clean filter: ?f= URL kind',
    find: 'ok=[`enchant`,`stars`,`reforge`,`rarity`,`recomb`,`hpc`,`bin`,`nbt`]',
    replace: 'ok=[`enchant`,`stars`,`reforge`,`rarity`,`recomb`,`hpc`,`bin`,`nbt`,`clean`]',
  },
  {
    // adding a filter (fd): Clean and the modifier filters clear each other instead of fighting.
    name: 'Clean filter: add-filter conflict handling',
    find: 'n([...Fi.includes(r)?t.filter(e=>e.kind!==r):t,s])',
    replace: 'n(BA_cleanApply([...Fi.includes(r)?t.filter(e=>e.kind!==r):t,s],r))',
  },
  {
    name: 'Clean filter: menu hint (common)',
    find: 'l.map(e=>(0,b.jsx)(md,{onClick:()=>d(e.kind),label:e.label,hint:e.hint},e.kind))',
    replace: 'l.map(e=>(0,b.jsx)(md,{onClick:()=>d(e.kind),label:e.label,hint:BA_cleanHint(t,e.kind)||e.hint},e.kind))',
  },
  {
    name: 'Clean filter: menu hint (enchantments)',
    find: 'c.map(e=>(0,b.jsx)(md,{onClick:()=>d(`enchant`,e),label:$i(e)},e))',
    replace: 'c.map(e=>(0,b.jsx)(md,{onClick:()=>d(`enchant`,e),label:$i(e),hint:BA_cleanHint(t,`enchant`)},e))',
  },
  {
    name: 'Clean filter: menu hint (modifiers)',
    find: 'u.map(e=>(0,b.jsx)(md,{onClick:()=>d(`nbt`,e.key),label:Zi(e.key),hint:`${e.count} sales`},e.key))',
    replace: 'u.map(e=>(0,b.jsx)(md,{onClick:()=>d(`nbt`,e.key),label:Zi(e.key),hint:BA_cleanHint(t,`nbt`)||`${e.count} sales`},e.key))',
  },
  // Market search (parts/60-market-search.js): one search per (rows, query typed 150 ms ago) instead of up to
  // three per keystroke. The list memo and the "closest items" flag (x) now share BA_sq; x is no longer a hook.
  {
    name: 'Market search: debounced query + one shared search',
    find: '[g,_]=(0,y.useState)({key:`coinsPerHour`,dir:`desc`,user:!1}),v=(0,y.useMemo)(()=>{',
    replace: '[g,_]=(0,y.useState)({key:`coinsPerHour`,dir:`desc`,user:!1}),BA_dq=hd(a,150),BA_sq=(0,y.useMemo)(()=>BA_mktSearch(t,BA_dq),[t,BA_dq]),v=(0,y.useMemo)(()=>{',
  },
  {
    name: 'Market search: list uses the shared search',
    find: 'let l=a.trim().length>0;if(l&&(r=Fr(r,a)),l&&!g.user)return r;',
    replace: 'let l=BA_sq!=null;if(l&&(r=BA_mktPick(BA_sq,r,BA_dq)),l&&!g.user)return r;',
  },
  {
    name: 'Market search: fuzzy flag without a second search',
    find: '},[t,o,e,c,u,f,m,a,g]),x=(0,y.useMemo)(()=>a.trim()?Ir(t,a,1).fuzzy:!1,[t,a]),',
    replace: '},[t,o,e,c,u,f,m,BA_sq,BA_dq,g]),x=BA_sq!=null&&BA_sq.fuzzy,',
  },
  {
    // Item names (Pt): Hypixel renamed the ultimate Duplex to Reiterate in the Bazaar, the game still says Duplex.
    // The search text still has the bazaar id, so "reiterate" keeps finding it.
    name: 'Names: Ultimate Reiterate shown as Ultimate Duplex',
    find: 'i=Mt((r==null?t:t.slice(0,-1)).join(`_`));',
    replace: 'i=Mt((r==null?t:t.slice(0,-1)).join(`_`).replace(/^ULTIMATE_REITERATE$/,`ULTIMATE_DUPLEX`));',
  },
  {
    // Search ranking (Ir): ignore a leading "Ultimate " so "soul eater" ranks Ultimate Soul Eater like a name that starts with it.
    name: 'Search: ultimate books rank by their name without "Ultimate"',
    find: 'let e=t.name.toLowerCase(),n=0;e===r?n=100',
    replace: 'let e=t.name.toLowerCase().replace(/^ultimate /,``),n=0;e===r?n=100',
  },
  {
    // Market page (af, Bazaar tab only): AH item types matching the search, above the list (parts/80-search.js).
    name: 'Market search: AH matches above the list',
    find: 'x&&(0,b.jsxs)(`div`,{className:`mb-3 rounded-lg border border-buy/30 bg-buy/10 px-3 py-2 text-sm text-buy`',
    replace: 'e===`all`&&(0,b.jsx)(BA_CraftBoundary,{label:`AH matches`,children:(0,b.jsx)(BA_MarketAhHits,{q:BA_dq})}),x&&(0,b.jsxs)(`div`,{className:`mb-3 rounded-lg border border-buy/30 bg-buy/10 px-3 py-2 text-sm text-buy`',
  },
  {
    // Settings "Clear watchlist" also clears the Book Flips stars (parts/40-book-flips.js BA_bfWatch).
    name: 'Settings: Clear watchlist clears Book Flips stars too',
    find: 'onConfirm:()=>L.setState({ids:[]})',
    replace: 'onConfirm:()=>(L.setState({ids:[]}),BA_bfWatch.setState({ids:[]}))',
  },
  // Search (parts/80-search.js): one header search for Bazaar items, AH item types and players; the AH page's own
  // box is gone and its list follows the header query; each Activity tab gets its own search box.
  {
    name: 'Search: header box = Bazaar + AH + players',
    find: '(0,b.jsx)($r,{})',
    replace: '(0,b.jsx)(BA_TopSearch,{})',
  },
  {
    name: 'Search: drop the AH page search box',
    find: '(0,b.jsx)(BA_AhSearch,{items:e??[],value:i,onChange:a}),',
    replace: '',
  },
  {
    name: 'Search: AH list follows the header query',
    find: 'refetch:r}=ka(),[i,a]=(0,y.useState)(``)',
    replace: 'refetch:r}=ka(),i=qt(e=>e.query)',
  },
  {
    name: 'Search: Activity tabs get their own box',
    find: 'tab===`trades`?(0,b.jsx)(BA_TradesTab,{mod,low}):tab===`lowball`?(0,b.jsx)(BA_LowballTab,{mod,low}):(0,b.jsx)(BA_MarketTab,{s,rows})',
    replace: '(0,b.jsx)(BA_ActSearch,{tab,mod,low,s,rows},tab)',
  },
  {
    // Lowballing tab: gem lines come from the mod's gem-removal watch (0.6.0+) with their own note
    // ("removed, sold on the Bazaar" / "sell offer not claimed yet" / "removed, not sold yet"); older mods send none.
    name: 'Lowball: gem lines show the mod note',
    find: 'l.kind===`GEM`?(0,b.jsx)(`span`,{className:`text-mute`,children:` · sold on the Bazaar`}):l.note&&',
    replace: 'l.kind===`GEM`&&!l.note?(0,b.jsx)(`span`,{className:`text-mute`,children:` · sold on the Bazaar`}):l.note&&',
  },
  {
    name: 'Lowball: gem help text',
    find: 'Gemstones that were on the item count when you sold them on the Bazaar within a day. The coins you paid are split across the parts by their proceeds.',
    replace: 'Parts you take off a bought item (gemstones, drill parts, pet items and skins, item skins, dyes, rod parts) count when you sell or list them on the Bazaar or the AH within a day of taking them off (the mod watches the item in your inventory; for trades from before that, gems sold within a day of the trade count). Parts take no share of the coins you paid: their profit is their proceeds. The coins you paid are split across the items by their proceeds.',
  },
  // Page scroll (parts/90-page-scroll.js): the Market/Watchlist table and the AH item list were boxes of fixed height
  // (calc(100vh - N px)) scrolling on their own, with dead space under them. Now the box is as tall as its rows and
  // the page scrolls (window virtualizer); the Market header sticks under the site header.
  {
    name: 'Page scroll: Market virtualizer on the window',
    find: 'C=so({count:v.length,getScrollElement:()=>S.current,estimateSize:()=>nf,overscan:14})',
    replace: 'C=BA_usePageVirt({count:v.length,listRef:S,head:39,minW:1180,estimateSize:()=>nf,overscan:14})',
  },
  {
    name: 'Page scroll: Market box grows with its rows',
    find: 'ref:S,className:`h-[calc(100vh-190px)] min-h-[420px] overflow-auto rounded-xl border border-line bg-panel`',
    replace: 'ref:S,style:C.boxStyle,className:`rounded-xl border border-line bg-panel`',
  },
  {
    name: 'Page scroll: Market header sticks under the site header',
    find: 'className:`sticky top-0 z-10 grid items-center border-b border-line bg-panel2 text-xs font-medium text-mute`,style:{gridTemplateColumns:tf,height:38}',
    replace: 'className:`sticky top-0 z-10 grid items-center border-b border-line bg-panel2 text-xs font-medium text-mute`,style:{gridTemplateColumns:tf,height:38,top:C.stickyTop}',
  },
  {
    name: 'Page scroll: AH item list virtualizer on the window',
    find: 'm=so({count:f.length,getScrollElement:()=>p.current,estimateSize:()=>co,overscan:14})',
    replace: 'm=BA_usePageVirt({count:f.length,listRef:p,estimateSize:()=>co,overscan:14})',
  },
  {
    name: 'Page scroll: AH item list box grows with its rows',
    find: 'ref:p,className:`h-[calc(100vh-290px)] min-h-[360px] overflow-auto rounded-xl border border-line bg-panel`',
    replace: 'ref:p,style:m.boxStyle,className:`rounded-xl border border-line bg-panel`',
  },
  {
    // App (gf) re-ran its title effect on every bazaar poll (rows identity) and, being the parent, also right after
    // the page's own effect: the AH item and player pages set their own title, so leave those alone.
    name: 'Tab title: keep the AH item / player page titles',
    find: 'document.title=n?`${n} · Bazaar Analyzer`:`Bazaar Analyzer`},[e,t])',
    replace: 'e.page!==`ahItem`&&e.page!==`ahPlayer`&&(document.title=n?`${n} · Bazaar Analyzer`:`Bazaar Analyzer`)},[e,t])',
  },
  {
    // ?f= filters from the mod had no id, so FilterPanel's id-keyed remove/update hit every url chip at once.
    name: 'Polish: ?f= filters get ids',
    find: 'let g={kind:f.kind};',
    replace: 'let g={id:Pi(),kind:f.kind};',
  },
  {
    // chart chrome (grid/axes/crosshair label/PNG export bg) re-tinted warm; series colours untouched.
    name: 'Polish: chart palette chrome',
    find: 'var X={ask:`#fbbf24`,bid:`#38bdf8`,up:`#22c55e`,down:`#ef4444`,text:`#8b95ad`,grid:`#182031`,line:`#232b3d`,bg:`#0d111a`,label:`#2a3350`}',
    replace: 'var X={ask:`#fbbf24`,bid:`#38bdf8`,up:`#22c55e`,down:`#ef4444`,text:`#a09d93`,grid:`#2a2926`,line:`#3a3935`,bg:`#1a1918`,label:`#3a3935`}',
  },
  {
    name: 'Polish: chart crosshair + label text',
    find: 'a={color:`#e6e9f2`,backgroundColor:X.label,borderColor:X.label,family:BA_font}',
    replace: 'a={color:`#ece9e1`,backgroundColor:X.label,borderColor:X.label,family:BA_font}',
  },
  {
    name: 'Polish: chart crosshair lines',
    find: 'crosshair:{horizontal:{line:{color:`#4b5675`},text:a},vertical:{line:{color:`#4b5675`},text:a}}',
    replace: 'crosshair:{horizontal:{line:{color:`#5a564f`},text:a},vertical:{line:{color:`#5a564f`},text:a}}',
  },
  {
    name: 'Polish: chart overlay default colour',
    find: 'overlay:{line:{color:`#e6e9f2`},text:{color:`#e6e9f2`},point:{color:`#e6e9f2`,borderColor:`rgba(230,233,242,0.35)`,activeColor:`#e6e9f2`,',
    replace: 'overlay:{line:{color:`#ece9e1`},text:{color:`#ece9e1`},point:{color:`#ece9e1`,borderColor:`rgba(236,233,225,0.35)`,activeColor:`#ece9e1`,',
  },
  {
    name: "Polish: indicator zero line 1",
    find: "{key:`zero`,title:``,type:`line`,styles:()=>({color:`#4b5675`,size:1})}],calc:e=>e.map",
    replace: "{key:`zero`,title:``,type:`line`,styles:()=>({color:`#5a564f`,size:1})}],calc:e=>e.map",
  },
  {
    name: "Polish: indicator zero line 2",
    find: "{key:`zero`,title:``,type:`line`,styles:()=>({color:`#4b5675`,size:1})}],calc:(e,t)=>",
    replace: "{key:`zero`,title:``,type:`line`,styles:()=>({color:`#5a564f`,size:1})}],calc:(e,t)=>",
  },
  // A page that throws (e.g. an odd Coflnet reply) shows an error box instead of blanking the whole site; n = page key.
  {
    name: 'App: page crash guard (open)',
    find: '(0,b.jsxs)(`main`,{children:[e.page===`market`',
    replace: '(0,b.jsx)(BA_CraftBoundary,{label:`This page`,resetKey:n,children:(0,b.jsxs)(`main`,{children:[e.page===`market`',
  },
  {
    name: 'App: page crash guard (close)',
    find: '(0,b.jsx)(BA_Activity,{})]})]})}',
    replace: '(0,b.jsx)(BA_Activity,{})]})})]})}',
  },
  {
    // Activity: no polling (and no mod work) while the browser tab is hidden.
    name: 'Activity: pause while hidden',
    find: 'let alive=!0,timer=0,run=async()=>{let found=null;',
    replace: 'let alive=!0,timer=0,run=async()=>{if(document.hidden){timer=setTimeout(run,1e3);return}let found=null;',
  },
  {
    name: 'Activity: mod version text 1',
    find: 'Start Minecraft with the Bazaar Analyzer Companion mod (version 0.2.0 or newer)',
    replace: 'Start Minecraft with the latest Yoav Addons mod',
  },
  {
    name: 'Activity: mod name (looking)',
    find: '`Looking for the companion mod…`',
    replace: '`Looking for the Yoav Addons mod…`',
  },
  {
    name: 'Activity: mod name (not detected)',
    find: '`Companion mod not detected`',
    replace: '`Yoav Addons mod not detected`',
  },
  {
    name: 'Activity: mod version text 2',
    find: 'Replace it with version 0.2.0 or newer',
    replace: 'Replace it with the latest version',
  },
  {
    // same boundary as the mod's AhFees: 2.5% from 100M on
    name: 'AH fees: 100M boundary',
    find: 'price*(price>1e8?.025:',
    replace: 'price*(price>=1e8?.025:',
  },
];

// New module-scope code is inserted right before this anchor (after every original declaration,
// before the QueryClient is created and the app is rendered).
const INSERT_BEFORE = 'var _f=new Ke({defaultOptions:{queries:{refetchOnWindowFocus:!1}}});';

function count(hay, needle) {
  let n = 0;
  for (let i = hay.indexOf(needle); i >= 0; i = hay.indexOf(needle, i + 1)) n++;
  return n;
}

function replaceOnce(src, { name, find, replace }) {
  const n = count(src, find);
  if (n !== 1) throw new Error(`replacement "${name}": expected 1 match, found ${n}`);
  const i = src.indexOf(find);
  return src.slice(0, i) + replace + src.slice(i + find.length);
}

// Turn `export function foo` / `export const x` into plain declarations and collect exported
// names, then wrap the whole file in an IIFE so its internals can't clash with minified names.
function inlineEngine(code, nsName) {
  const names = [];
  const body = code.replace(/^export\s+(async\s+function|function|const|let|var|class)\s+([A-Za-z_$][\w$]*)/gm,
    (_, kw, id) => { names.push(id); return `${kw} ${id}`; });
  if (/^\s*export\b/m.test(body)) throw new Error('engine: unsupported export form (use `export function|const ...`)');
  if (/^\s*import\b/m.test(body)) throw new Error('engine: imports are not allowed in the inlined engine');
  if (!names.length) throw new Error('engine: nothing exported');
  if (new Set(names).size !== names.length) throw new Error('engine: duplicate export names');
  return `const ${nsName}=(()=>{\n${body}\nreturn{${names.join(',')}};})();\n`;
}

let src = fs.readFileSync(SRC, 'utf8');
const before = src.length;
if (count(src, INSERT_BEFORE) !== 1) throw new Error('insertion anchor not unique');

let inserted = '';
let partNames = [];
if (!noop) {
  for (const r of REPLACEMENTS) src = replaceOnce(src, r);
  inserted += inlineEngine(ENGINE_FILES.map((f) => fs.readFileSync(f, 'utf8')).join('\n'), 'BA_craft');
  if (fs.existsSync(PARTS_DIR)) {
    partNames = fs.readdirSync(PARTS_DIR).filter((f) => f.endsWith('.js')).sort();
    for (const f of partNames) {
      const code = fs.readFileSync(path.join(PARTS_DIR, f), 'utf8');
      if (/^\s*(import|export)\b/m.test(code)) throw new Error(`part ${f}: import/export not allowed`);
      inserted += `\n/* ---- part ${f} ---- */\n${code}\n`;
    }
  }
} else {
  // Self-test: exercise both mechanisms with harmless code.
  src = replaceOnce(src, { name: 'noop', find: 'function BA_Credits(){', replace: 'function BA_Credits(){/*v12*/' });
  inserted += inlineEngine('export function add(a,b){return a+b}\nexport const K=1;\nconst hidden=2;', 'BA_CE_TEST');
  inserted += 'function BA_NoopTest(){return(0,b.jsx)(`span`,{children:BA_CE_TEST.add(1,BA_CE_TEST.K)})}\n';
}
src = replaceOnce(src, { name: 'insert new module code', find: INSERT_BEFORE, replace: inserted + INSERT_BEFORE });

// Syntax check as an ES module BEFORE writing anything (module = strict mode, like the browser).
// Also catches name clashes: a new top-level `function gd` / `const X` is a redeclaration SyntaxError in a module.
const check = path.join(os.tmpdir(), `ba-bundle-check-${process.pid}.mjs`);
fs.writeFileSync(check, src);
try {
  execFileSync(process.execPath, ['--check', check], { stdio: 'inherit' });
} finally {
  fs.unlinkSync(check);
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, src);

if (shipping && !noop) {
  for (const p of [CSS_SRC, THEME_SRC, RECIPES_SRC]) if (!fs.existsSync(p)) throw new Error('missing ' + p);
  JSON.parse(fs.readFileSync(RECIPES_SRC, 'utf8')); // never ship a broken recipe file
  fs.copyFileSync(CSS_SRC, SITE_APP + '/assets/ba-craft.css');
  fs.copyFileSync(THEME_SRC, SITE_APP + '/assets/theme-claude.css');
  fs.mkdirSync(SITE_APP + '/data', { recursive: true });
  fs.copyFileSync(RECIPES_SRC, SITE_APP + '/data/recipes.json');
  const htmlPath = SITE_APP + '/index.html';
  let html = fs.readFileSync(htmlPath, 'utf8');
  // serve.ps1 lets browsers cache .js/.css for a day (index.html is no-cache), so the file name alone would keep
  // serving stale files after a rebuild: ?v=<content hash> on the script and both css links makes every rebuild load fresh.
  const ver = (buf) => crypto.createHash('sha1').update(buf).digest('hex').slice(0, 10);
  const scriptRe = /(<script type="module"[^>]*src="\.\/assets\/)index-KsTXbicp-v\d+\.js(?:\?v=[0-9a-f]*)?(")/;
  if (!scriptRe.test(html)) throw new Error('index.html: bundle <script> not found');
  html = html.replace(scriptRe, `$1index-KsTXbicp-v12.js?v=${ver(src)}$2`);
  // stylesheet link: update its ?v=, or add it once right after `after` (a file name of an existing link line)
  const cssLink = (file, srcPath, after) => {
    const v = ver(fs.readFileSync(srcPath));
    const links = [...html.matchAll(/^.*<link rel="stylesheet"[^>]*>.*$/gm)];
    const own = links.filter((m) => m[0].includes(`./assets/${file}`));
    if (own.length > 1) throw new Error(`index.html: ${file} linked twice`);
    if (own.length) {
      const m = own[0], fixed = m[0].replace(/href="[^"]*"/, `href="./assets/${file}?v=${v}"`);
      html = html.slice(0, m.index) + fixed + html.slice(m.index + m[0].length);
      return;
    }
    const line = links.find((m) => m[0].includes(after));
    if (!line) throw new Error(`index.html: stylesheet link ${after} not found`);
    const at = line.index + line[0].length;
    html = html.slice(0, at) + `\n${line[0].match(/^\s*/)[0]}<link rel="stylesheet" href="./assets/${file}?v=${v}">` + html.slice(at);
  };
  cssLink('theme-claude.css', THEME_SRC, 'technical-v3.css');   // palette overrides load after technical-v3
  cssLink('ba-craft.css', CSS_SRC, 'theme-claude.css');         // craft UI uses the palette tokens
  // favicon: v11 navy/yellow -> Claude dark/orange (no-op when already swapped)
  html = html.replace(/(<link rel="icon"[^>]*)%23121722([^>]*)%23fbbf24/, '$1%231f1e1d$2%23d97757');
  if (!html.includes('<title>Bazaar Analyzer</title>')) throw new Error('index.html: title changed (the mod needs it)');
  fs.writeFileSync(htmlPath, html);
  // move the old bundle out of the served folder (the v11 backup keeps its own copy)
  const oldBundle = SITE_APP + '/assets/index-KsTXbicp-v11.js';
  if (fs.existsSync(oldBundle)) {
    fs.mkdirSync(OLD_BUNDLES, { recursive: true });
    let dest = path.join(OLD_BUNDLES, 'index-KsTXbicp-v11.js');
    if (fs.existsSync(dest) && !fs.readFileSync(dest).equals(fs.readFileSync(oldBundle))) {
      dest = path.join(OLD_BUNDLES, `index-KsTXbicp-v11.${Date.now()}.js`);
    }
    fs.renameSync(oldBundle, dest);
    console.log(`moved old bundle -> ${dest}`);
  }
}
console.log(`ok: ${OUT} (${before} -> ${src.length} chars, ${noop ? 'noop' : `${REPLACEMENTS.length} replacements, parts: ${partNames.join(', ') || 'none'}`})`);
