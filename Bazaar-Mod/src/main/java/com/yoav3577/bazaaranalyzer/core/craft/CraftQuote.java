package com.yoav3577.bazaaranalyzer.core.craft;

import java.util.List;

/**
 * Craft price of one listing = clean item + its modifiers. A total is NaN when the item's recipe can't be priced.
 * missing = ids / labels that had no price (not counted in the totals).
 */
public record CraftQuote(double fromScratch, double easy, List<String> missing) {
}
