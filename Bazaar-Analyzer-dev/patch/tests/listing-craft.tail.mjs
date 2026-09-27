const ctx = fixtureCtx(core, {}); ctx.hyItems = HYITEMS; ctx.reforgeStones = STONES; ctx.enchantTable = ENCH;
globalThis.__ctx = ctx;
let fails = 0;
for (const k of LISTING_KEYS) {
  const L = listing(k);
  const p1 = BA_CraftLstGet(ctx, L.tag, L), p2 = BA_CraftLstGet(ctx, L.tag, L);
  if (L.uuid && p1 !== p2) { console.log('DEDUPE FAIL', k); fails++; }
  const res = await p1;
  const price = L.highestBidAmount || L.startingBid;
  for (const open of [false, true]) {
    const html = renderToString(b.jsx(BA_CraftListingPopover, { st: { res, err: null }, ctxReady: true, item: L, price, open, onToggle(){}, n: 1 }));
    const want = res.totals.fromScratch == null ? "can&#x27;t be priced" : tn(res.totals.fromScratch, 0);
    if (!html.includes(want)) { console.log('MISSING TOTAL', k, want); fails++; }
    if (open) console.log(k.padEnd(24), 'scratch', want, 'easy', tn(res.totals.easy,0), 'price', tn(price,0), 'lines', res.modifiers.fromScratch.length, 'html', html.length);
  }
}
// wrapper SSR (effects don't run): lazy + eager, with and without ctx
for (const [lazy, c] of [[true, ctx], [false, ctx], [false, null]]) {
  globalThis.__ctx = c;
  const html = renderToString(b.jsx(BA_CraftListingCost, { tag: 'HYPERION', item: listing('hyperion-full'), price: 1, lazy, compact: lazy }));
  console.log('wrapper', lazy, !!c, html.replace(/<[^>]+>/g, ''));
}
// states
for (const st of [{ res: null, err: null }, { res: null, err: new Error('boom') }]) console.log(renderToString(b.jsx(BA_CraftListingPopover, { st, ctxReady: true, item: {}, price: 0, n: 1 })).replace(/<[^>]+>/g, ' '));
console.log(renderToString(b.jsx(BA_CraftListingPopover, { st: { res: null }, ctxReady: false, item: null, price: 0, n: 1 })).replace(/<[^>]+>/g, ' '));
console.log(JSON.stringify([BA_CraftLstDiff(90, 100), BA_CraftLstDiff(0, 100), BA_CraftLstDiff(110, 100), BA_CraftLstDiff(1, null)]));
// baseSource (engine review round 1): 'market' base row + notes; null base with nothing priced -> no totals
{
  const L = listing('pet-ender-dragon'), res = await BA_CraftLstGet(ctx, L.tag, L);
  const html = renderToString(b.jsx(BA_CraftListingPopover, { st: { res, err: null }, ctxReady: true, item: L, price: 1, open: false, onToggle(){}, n: 1 }));
  if (res.baseSource !== 'market' || !html.includes('Clean item (no recipe, bought)') || !html.includes('pet exp / level not priced')) { console.log('MARKET BASE FAIL', res.baseSource); fails++; }
  const r0 = { baseId: 'X', craftable: false, baseSource: null, base: { fromScratch: null, easy: null }, modifiers: { fromScratch: [], easy: [] }, totals: { fromScratch: 0, easy: 0 }, missing: [], notes: [] };
  const h0 = renderToString(b.jsx(BA_CraftListingPopover, { st: { res: r0, err: null }, ctxReady: true, item: {}, price: 5, open: false, onToggle(){}, n: 1 }));
  if (!h0.includes('nothing on it to price') || h0.includes('Craft from scratch')) { console.log('EMPTY BASE FAIL'); fails++; }
}
console.log(fails ? 'FAILS ' + fails : 'ALL OK');
