// 24/7 Auction House tracker from the Hypixel API (no chat, no game, no API key).
// Every 30 s reads /v2/skyblock/auctions_ended (auctions that ended in the last ~60 s)
// and appends the ones you sold to ah-log.jsonl (one JSON object per line).
//   node tracker/ah-watch.mjs <minecraft name or uuid>
// Only sold listings: buys, listings, cancels and expired claims happen in game, so the mod's chat log has them.
// ponytail: a minute of downtime = that minute's sales missed; Coflnet /player/{uuid}/auctions can backfill.
import { appendFileSync, existsSync, readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const HYPIXEL = 'https://api.hypixel.net/v2/skyblock/auctions_ended';
const COFL = 'https://sky.coflnet.com/api';
const LOG = new URL('./ah-log.jsonl', import.meta.url);
// auctions_ended covers the last ~60 s; polling every 30 s overlaps batches (deduped by `seen`) so the time spent on
// requests never opens a gap between them.
const POLL_MS = 30000;

const dashless = (u) => u.replace(/-/g, '').toLowerCase();

/** Auction ids already in the log (so a restart doesn't log the last minute's sales twice). */
export function logged(text) {
  return new Set(text.split('\n').filter(Boolean).map((l) => JSON.parse(l).auctionId));
}

/** Your sold auctions in one auctions_ended batch that are not in `seen` (auction ids). */
export function mine(auctions, uuid, seen) {
  return auctions
    .filter((a) => a.seller === uuid && !seen.has(a.auction_id))
    .map((a) => ({ ts: a.timestamp, auctionId: a.auction_id, price: a.price, bin: !!a.bin, buyer: a.buyer }));
}

async function json(url) {
  const r = await fetch(url, { signal: AbortSignal.timeout(15000) });
  if (!r.ok) throw new Error(`${r.status} ${url}`);
  return r.json();
}

async function resolveUuid(arg) {
  if (/^[0-9a-f-]{32,36}$/i.test(arg)) return dashless(arg);
  return dashless((await json(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(arg)}`)).id);
}

async function main() {
  const arg = process.argv[2];
  if (!arg) throw new Error('usage: node tracker/ah-watch.mjs <minecraft name or uuid>');
  const uuid = await resolveUuid(arg);
  console.log(`watching ${uuid}, log: ${LOG.pathname}`);
  let seen = existsSync(LOG) ? logged(readFileSync(LOG, 'utf8')) : new Set();
  for (;;) {
    try {
      const { auctions = [] } = await json(HYPIXEL);
      for (const e of mine(auctions, uuid, seen)) {
        // Name + tag from Coflnet (best effort; item_bytes is gzipped NBT).
        const d = await json(`${COFL}/auction/${e.auctionId}`).catch(() => null);
        const row = { ...e, tag: d?.tag ?? null, name: d?.itemName ?? null };
        appendFileSync(LOG, JSON.stringify(row) + '\n');
        console.log(new Date(row.ts).toISOString(), 'sold', row.name ?? row.auctionId, row.price);
      }
      seen = new Set(auctions.map((a) => a.auction_id));
    } catch (err) {
      console.warn(new Date().toISOString(), String(err));
    }
    await new Promise((r) => setTimeout(r, POLL_MS));
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main();
