/* ---- 60-market-search.js ------------------------------------------------------------------
 * Market page (af) search: one search over all rows per (rows, debounced query) instead of
 * up to three per keystroke. Mounted by patch/patch_v12.mjs ("Market search: ...").
 * Ir(rows, q) = the bundle's searchRowsEx -> {items, fuzzy}; Fr(rows, q) = Ir(...).items.
 * ------------------------------------------------------------------------------------------ */

// null when there is no query, else Ir's {items, fuzzy} over ALL rows (the Fuse index stays cached per rows array).
function BA_mktSearch(rows, q) {
  return typeof q === `string` && q.trim() ? Ir(rows, q) : null;
}

// The rows of `subset` (the Market's filtered list, in the rows' order) that the search matched, in search order.
// Same result as the old Fr(subset, q): literal scores and Fuse scores are per row, so filtering after searching
// keeps both the set and the order. One exception: when the literal hits are all outside the filters, Fr fell back
// to a fuzzy search inside the filters, so that case still goes through Fr.
function BA_mktPick(res, subset, q) {
  if (!res) return subset;
  let keep = new Set(subset),
    out = res.items.filter((r) => keep.has(r));
  if (!out.length && !res.fuzzy && res.items.length) return Fr(subset, q);
  return out;
}
