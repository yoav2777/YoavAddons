// Sales history (.github/workflows/sales-history.yml): appends every new sold auction of the `sold` tags in
// .github/data-tags.txt to <out>/<TAG>/<YYYY-MM>.jsonl (slimmed Coflnet rows, deduped by auction uuid), plus a
// lowest-BIN and Bazaar snapshot (<out>/_prices/<day>.json). Coflnet keeps only ~7 days of single sales; this keeps all.
// Usage: node .github/tools/sales-history.mjs <out dir>
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const COFL = 'https://sky.coflnet.com/api';
const OUT = process.argv[2] ?? 'hist';
const PAGE = 500;
const MAX_PAGES = 6;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function get(url, tries = 4) {
  for (let i = 0; i < tries; i++) {
    try {
      const r = await fetch(url, { headers: { 'User-Agent': 'yoav-addons-history/1.0' } });
      if (r.status === 429) { await sleep(10000 * (i + 1)); continue; }
      if (r.status === 204) return [];
      if (r.ok) return await r.json();
      console.log(`${r.status} ${url}`);
    } catch (e) {
      console.log(`ERR ${e.message} ${url}`);
    }
    await sleep(3000);
  }
  return null;
}

// what the price backtest and the mod's estimate read; drop per-gem uuids and other ids (half the size)
function slim(r) {
  const flat = {};
  for (const [k, v] of Object.entries(r.flattenedNbt ?? r.flatNbt ?? {})) {
    if (!/(^|\.)(uuid|uid|timestamp)$|Uuid$|^uniqueId$/.test(k)) flat[k] = v;
  }
  const { uuid, tag, itemName, end, start, highestBidAmount, startingBid, bin, count, tier, enchantments } = r;
  return { uuid, tag, itemName, end, start, highestBidAmount, startingBid, bin, count, tier, enchantments, flattenedNbt: flat };
}

function seenUuids(dir, months) {
  const seen = new Set();
  for (const m of months) {
    const f = `${dir}/${m}.jsonl`;
    if (existsSync(f)) for (const line of readFileSync(f, 'utf8').split('\n')) if (line) seen.add(JSON.parse(line).uuid);
  }
  return seen;
}

const tags = readFileSync('.github/data-tags.txt', 'utf8').split('\n')
  .map((l) => l.trim().split(/\s+/)).filter((w) => w[0] === 'sold').map((w) => w[1]);
const now = new Date();
const month = (d) => d.toISOString().slice(0, 7);
const months = [month(new Date(now.getTime() - 8 * 86400000)), month(now)].filter((m, i, a) => a.indexOf(m) === i);
let total = 0;

for (const tag of tags) {
  const dir = `${OUT}/${tag}`;
  mkdirSync(dir, { recursive: true });
  const seen = seenUuids(dir, months);
  let added = 0;
  for (let p = 0; p < MAX_PAGES; p++) {
    const rows = await get(`${COFL}/auctions/tag/${tag}/sold?page=${p}&pageSize=${PAGE}`);
    await sleep(1500);
    if (!Array.isArray(rows) || !rows.length) break;
    let fresh = 0;
    for (const r of rows) {
      if (!r.uuid || seen.has(r.uuid)) continue;
      seen.add(r.uuid);
      fresh++;
      appendFileSync(`${dir}/${String(r.end).slice(0, 7)}.jsonl`, JSON.stringify(slim(r)) + '\n');
    }
    added += fresh;
    if (fresh < rows.length / 2 || rows.length < PAGE) break; // reached what earlier runs saved
  }
  total += added;
  console.log(`${tag}: +${added}`);
}

// prices of the day, for valuing modifiers and the clean anchor at that time
const day = now.toISOString().slice(0, 10);
mkdirSync(`${OUT}/_prices`, { recursive: true });
const lbin = await get('https://lb.tricked.pro/lowestbins');
const bz = await get('https://api.hypixel.net/v2/skyblock/bazaar');
const bazaar = {};
for (const [id, p] of Object.entries(bz?.products ?? {})) {
  bazaar[id] = [p.buy_summary?.[0]?.pricePerUnit ?? null, p.sell_summary?.[0]?.pricePerUnit ?? null];
}
writeFileSync(`${OUT}/_prices/${day}.json`, JSON.stringify({ lowestbins: lbin, bazaar }));
console.log(`total +${total} sales; prices ${day}`);
