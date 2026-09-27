// ===== Craft price that follows the AH item page filters (parts/15-filter-craft.js) =====
// The "Craft price" stat / tab on the AH item page (parts/10-craft-tab.js) = clean craft + what the selected filter
// chips add. BA_filterListing turns the chips into the cheapest item that matches them (the lowest level / count /
// stars they allow) in the Coflnet row shape that the engine already prices for real listings
// (BA_craft.listingCraftCost, craft/craftModifiers.js), so both use the same prices and rules.
// BA_filterListing is pure (only BA_craft.listModifiers), tested by patch/tests/filter-craft.test.mjs.
// Docs: notes/craft-ui.md "Craft price follows the filters".
//
// chip (bundle Ii / ud)                    -> synthetic listing
//   enchant {type,min,max}                 -> enchantments [{type, level: max(1, min)}]
//   stars {min,max}                        -> upgrade_level = min (6-10 = master stars) + dungeon_item (conversion)
//   hpc {min,max}                          -> hpc = min (1-10 Hot Potato, 11-15 Fuming)
//   recomb yes                             -> rarity_upgrades 1          (recomb "no": nothing to add)
//   reforge <name>                         -> reforge (stone + apply cost; a blacksmith reforge is free -> skipped)
//   rarity (pets)                          -> the pet tier (picked by BA_craftPetTier, like the clean price)
//   rarity (other items)                   -> one tier above the item's own tier = recombobulated; its own tier =
//                                             nothing to add; also the tier for the reforge apply cost. Else skipped.
//   nbt <key> = value | range min          -> flatNbt[key]; kept only when the engine prices that key
//                                             (ability_scroll = Wither Impact, power_ability_scroll, gems, unlocked_slots,
//                                             art of war, books, runes, dye, drill parts, pet held item / skin ...)
//     gem <SLOT>_<n> = quality             -> + its slot in unlocked_slots (slot unlock cost); a universal slot
//                                             (COMBAT_0...) also needs the <SLOT>_<n>_gem chip for the gem type
//   clean                                  -> nothing (that is the clean craft)
//   price, bin, anything else              -> skipped ("not a modifier")

var BA_FC_TYPED_GEMS = new Set([`RUBY`, `AMBER`, `TOPAZ`, `JADE`, `SAPPHIRE`, `AMETHYST`, `JASPER`, `OPAL`, `ONYX`, `AQUAMARINE`, `CITRINE`, `PERIDOT`]);
var BA_FC_KIND_LABEL = { price: `Sale price`, bin: `Buy It Now / Auction`, rarity: `Rarity` };
var BA_FC_TIERS = [`COMMON`, `UNCOMMON`, `RARE`, `EPIC`, `LEGENDARY`, `MYTHIC`, `DIVINE`, `SPECIAL`, `VERY_SPECIAL`];
// nbt keys the engine prices only with the Hypixel item data (not known here): keep them
var BA_FC_DATA_KEYS = new Set([`dungeon_item`, `unlocked_slots`]);

// same wording as the chip labels (bundle Zi): ability_scroll -> Ability Scroll, COMBAT_0 -> Combat 0
function BA_fcPretty(k) {
  let t = String(k ?? ``).replace(/_/g, ` `).replace(/([a-z])([A-Z])/g, `$1 $2`);
  return (/[a-z]/.test(t) ? t : t.toLowerCase()).replace(/\b\w/g, (c) => c.toUpperCase());
}
// lowest whole value a min/max chip allows, never below `floor`
function BA_fcMin(f, floor) {
  let n = f && f.min != null && f.min !== `` ? Number(f.min) : NaN;
  return Number.isFinite(n) ? Math.max(floor, Math.ceil(n)) : floor;
}
function BA_fcMods(item) {
  try {
    return BA_craft.listModifiers(item, { items: new Map() }) || [];
  } catch {
    return [];
  }
}

// filters = the page's filter chips. opts.tier = for pets the tier the clean price uses (BA_craftPetTier);
// opts.baseTier = the item's own rarity (page header) to read a Rarity chip one tier higher as a recombobulator.
// -> { item: Coflnet-like row, or null when no chip adds anything (then the plain clean craft is shown),
//      key: stable string of item, skipped: [{label, why}] chips that are not in the craft price }
function BA_filterListing(tag, filters, opts = {}) {
  tag = String(tag || ``).toUpperCase();
  let pet = /^PET_/.test(tag),
    tier = opts && opts.tier ? String(opts.tier).toUpperCase() : null,
    baseTier = opts && opts.baseTier ? String(opts.baseTier).toUpperCase() : null;
  let ench = new Map(),
    nbt = {},
    chipKeys = new Map(), // nbt key -> chip label (only keys that came from nbt chips are checked with the engine)
    reforge = null,
    rarity = null,
    skipped = [];
  let skip = (label, why) => skipped.push({ label, why });
  for (let f of Array.isArray(filters) ? filters : []) {
    if (!f || typeof f !== `object`) continue;
    switch (f.kind) {
      case `enchant`: {
        let type = String(f.type || ``).trim().toLowerCase();
        if (type) ench.set(type, Math.max(ench.get(type) || 0, BA_fcMin(f, 1)));
        break;
      }
      case `stars`: {
        let n = Math.min(10, BA_fcMin(f, 0));
        if (n > 0) (nbt.upgrade_level = String(n)), (nbt.dungeon_item = `1`);
        break;
      }
      case `hpc`: {
        let n = Math.min(15, BA_fcMin(f, 0));
        if (n > 0) nbt.hpc = String(n);
        break;
      }
      case `recomb`:
        if (f.value === true || f.value === `true`) nbt.rarity_upgrades = `1`;
        break;
      case `reforge`: {
        let v = String(f.value || ``).trim();
        if (v && v.toLowerCase() !== `none`) reforge = v;
        break;
      }
      case `rarity`:
        rarity = f.value ? String(f.value).toUpperCase() : null;
        break;
      case `clean`:
        break;
      case `nbt`: {
        let key = String(f.key || ``).trim(),
          v = f.mode === `range` ? (BA_fcMin(f, 0) > 0 ? String(BA_fcMin(f, 0)) : ``) : String(f.value ?? ``).trim();
        if (!key || !v) break; // chip not filled in yet, or "0 or more"
        nbt[key] = v;
        chipKeys.set(key, BA_fcPretty(key));
        break;
      }
      default:
        skip(BA_FC_KIND_LABEL[f.kind] || BA_fcPretty(f.kind), `not a modifier`);
    }
  }

  // gems: a gem needs its slot unlocked; a universal slot (COMBAT_0, DEFENSIVE_0 ...) needs the gem type chip too
  let slots = new Set(String(nbt.unlocked_slots || ``).split(/[\s,]+/).filter(Boolean));
  for (let k of Object.keys(nbt)) {
    let q = /^([A-Z]+)_(\d+)$/.exec(k),
      g = /^([A-Z]+_\d+)_gem$/.exec(k);
    if (q) {
      if (!BA_FC_TYPED_GEMS.has(q[1]) && !nbt[k + `_gem`]) {
        skip(BA_fcPretty(k), `gem type unknown: add the ${BA_fcPretty(k)} Gem filter`);
        delete nbt[k];
        chipKeys.delete(k);
        continue;
      }
      slots.add(k);
      chipKeys.delete(k); // priced below as a gem
    } else if (g) {
      if (!nbt[g[1]]) skip(`${BA_fcPretty(g[1])} Gem`, `gem quality unknown: add the ${BA_fcPretty(g[1])} filter`), delete nbt[k];
      chipKeys.delete(k);
    }
  }
  if (slots.size) nbt.unlocked_slots = [...slots].join(`,`);
  if (chipKeys.has(`unlocked_slots`)) chipKeys.delete(`unlocked_slots`);

  // every other nbt chip: kept only if the engine turns it into a priced modifier
  let petNbt = pet ? { type: tag.slice(4), tier: tier || `LEGENDARY` } : {};
  for (let [k, label] of chipKeys) {
    if (BA_FC_DATA_KEYS.has(k)) continue;
    let mods = BA_fcMods({ tag, flatNbt: { ...petNbt, [k]: nbt[k] } }).filter((m) => m && m.kind !== `unknown`);
    if (!mods.length) skip(label, `not priced by the craft engine`), delete nbt[k];
  }
  if (reforge && !pet) {
    let m = BA_fcMods({ tag, reforge, flatNbt: {} }).find((x) => x && x.kind === `reforge`);
    if (m && !(m.parts && m.parts.length) && !m.coins) skip(`Reforge ${reforge}`, `blacksmith reforge (random roll), no stone to price`), (reforge = null);
  } else if (reforge && pet) skip(`Reforge ${reforge}`, `pets have no reforge`), (reforge = null);
  // rarity on gear: one tier up is what a recombobulator does; the item's own tier adds nothing
  let rUsed = !1;
  if (rarity && !pet) {
    let d = BA_FC_TIERS.indexOf(rarity) - BA_FC_TIERS.indexOf(baseTier);
    if (baseTier && BA_FC_TIERS.includes(baseTier) && BA_FC_TIERS.includes(rarity) && (d === 0 || d === 1)) {
      if (d === 1) nbt.rarity_upgrades = `1`;
      rUsed = !0;
    }
    if (!rUsed && !reforge) skip(`Rarity ${BA_fcPretty(rarity)}`, baseTier ? `not reachable from ${BA_fcPretty(baseTier)} with a recombobulator` : `not a modifier`);
  }

  let enchantments = [...ench].sort((a, t) => (a[0] < t[0] ? -1 : 1)).map(([type, level]) => ({ type, level }));
  let flat = Object.fromEntries(Object.keys(nbt).sort().map((k) => [k, nbt[k]]));
  if (!enchantments.length && !reforge && !Object.keys(flat).length) return { item: null, key: ``, skipped };
  let item = { tag, enchantments, flatNbt: pet ? { ...petNbt, ...flat } : flat };
  if (pet) item.tier = petNbt.tier;
  else if (rarity && reforge) item.tier = rarity; // reforge apply cost is per tier
  if (reforge) item.reforge = reforge;
  let key = JSON.stringify(item);
  item.uuid = `filters:` + key; // cache key for BA_CraftLstGet (one computation per chip set and bazaar update)
  return { item, key, skipped };
}

/* ---------------- React side ---------------- */

// Clean craft (BA_useCleanCraft) + the filter listing priced by the engine (shared per ctx through BA_CraftLstGet).
// -> clean hook state + { syn, mods: listingCraftCost result | null, modsErr, modsLoading }
function BA_useFilterCraft(tag, tier, filters, baseTier) {
  let clean = BA_useCleanCraft(tag, tier),
    ctx = useCraftCtx();
  let fk = JSON.stringify(Array.isArray(filters) ? filters.map((f) => (f && typeof f === `object` ? { ...f, id: void 0 } : f)) : []);
  let syn = (0, y.useMemo)(() => BA_filterListing(tag, filters, { tier, baseTier }), [tag, tier, baseTier, fk]);
  let [st, setSt] = (0, y.useState)({ key: ``, res: null, err: null });
  (0, y.useEffect)(() => {
    if (!syn.item || !ctx) return;
    let alive = !0;
    BA_CraftLstGet(ctx, tag, syn.item).then(
      (res) => { if (alive) setSt({ key: syn.key, res, err: null }); },
      (err) => { if (alive && err?.name !== `AbortError`) setSt({ key: syn.key, res: null, err }); },
    );
    return () => { alive = !1; };
  }, [ctx, tag, syn.key]);
  if (!syn.item) return { ...clean, syn, mods: null, modsErr: null, modsLoading: !1 };
  let cur = st.key === syn.key ? st : null;
  return { ...clean, syn, mods: cur?.res ?? null, modsErr: cur?.err ?? null, modsLoading: !cur };
}

// "Not in the craft price: Sale price, Buy It Now / Auction" (hover = why each one was left out)
function BA_CraftSkipNote({ skipped, className }) {
  if (!skipped?.length) return null;
  return BA_el(`span`, { className: className ?? `text-xs text-mute`, title: skipped.map((s) => `${s.label}: ${s.why}`).join(`\n`) },
    `not in craft price: ${skipped.map((s) => s.label).join(`, `)}`);
}

// Two columns (clean item, expandable, + one line per modifier), same look as the listing craft cost popover.
function BA_CraftFilterBreakdown({ res, petBase, skipped, title }) {
  let [open, setOpen] = (0, y.useState)(!1),
    info = Oa();
  let col = (mode, t, sub, color) => (0, b.jsx)(BA_CraftLstColumn, { res, mode, title: t, sub, color, open, onToggle: () => setOpen((v) => !v), info, petBase, n: 1 });
  let miss = (res?.missing ?? []).filter(Boolean);
  return BA_el(`div`, null,
    title ? BA_el(`div`, { className: `mb-1.5 text-[11px] font-semibold uppercase text-accent` }, title) : null,
    BA_el(`div`, { className: `ba-cr-two` },
      col(`fromScratch`, `From scratch`, `(cheapest)`, `#38bdf8`),
      col(`easy`, `Easy way`, `(insta-buy + lowest BIN)`, `#fbbf24`)),
    BA_el(`div`, { className: `mt-2 text-mute`, style: { fontSize: 11 } },
      `Clean item + what the selected filters add, at the lowest level / amount they allow. From scratch = buy orders, cheapest of buy/craft/forge/NPC/lowest BIN at every step. Easy = insta-buy + lowest BIN, no sub-crafting.`),
    skipped?.length ? BA_el(`div`, { className: `mt-1 text-mute`, style: { fontSize: 11 } },
      `Not in the craft price: ${skipped.map((s) => `${s.label} (${s.why})`).join(`, `)}.`) : null,
    miss.length ? BA_el(`p`, { className: `mt-1 text-[11px] text-down` },
      `No price for: ${miss.slice(0, 8).map((x) => (/^[A-Z0-9_;:-]+$/.test(x) ? BA_craftName(x, info, petBase) : x)).join(`, `)}${miss.length > 8 ? ` (+${miss.length - 8} more)` : ``}`) : null);
}
