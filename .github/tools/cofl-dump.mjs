// Dumps Coflnet / Hypixel market data for working on the mod's price prediction offline (the dev sandbox can't reach
// them). Run by .github/workflows/data.yml; writes into out/. Tags + probes: .github/data-tags.txt.
import { writeFileSync, mkdirSync, readFileSync } from 'node:fs';

const COFL = 'https://sky.coflnet.com/api';
const OUT = 'out';
const DAY = 86400000;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
mkdirSync(OUT, { recursive: true });
const log = [];
const note = (s) => { console.log(s); log.push(s); };

async function get(url, tries = 4) {
  for (let i = 0; i < tries; i++) {
    const t0 = Date.now();
    try {
      const r = await fetch(url, { headers: { 'User-Agent': 'yoav-addons-data/1.0' } });
      const body = await r.text();
      note(`${r.status} ${Date.now() - t0}ms ${body.length}B ${url}`);
      if (r.status === 429) { await sleep(10000 * (i + 1)); continue; }
      if (r.status === 204) return [];
      if (!r.ok) return { error: r.status, body: body.slice(0, 500) };
      return JSON.parse(body);
    } catch (e) {
      note(`ERR ${e.message} ${url}`);
      await sleep(3000);
    }
  }
  return { error: 'failed' };
}

// what the backtests read; drops per-gem uuids and other ids (half the size), as sales-history.mjs does
function slim(r) {
  const flat = {};
  for (const [k, v] of Object.entries(r.flattenedNbt ?? r.flatNbt ?? {})) {
    if (!/(^|\.)(uuid|uid|timestamp)$|Uuid$|^uniqueId$/.test(k)) flat[k] = v;
  }
  const { uuid, tag, itemName, end, start, highestBidAmount, startingBid, bin, count, tier, enchantments } = r;
  return { uuid, tag, itemName, end, start, highestBidAmount, startingBid, bin, count, tier, enchantments, flattenedNbt: flat };
}

const cfg = readFileSync('.github/data-tags.txt', 'utf8').split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('#'));
const now = Date.now();

writeFileSync(`${OUT}/bazaar.json`, JSON.stringify(await get('https://api.hypixel.net/v2/skyblock/bazaar')));
writeFileSync(`${OUT}/lowestbins.json`, JSON.stringify(await get('https://lb.tricked.pro/lowestbins')));
writeFileSync(`${OUT}/items.json`, JSON.stringify(await get('https://api.hypixel.net/v2/resources/skyblock/items')));

for (const line of cfg) {
  const [kind, tag, ...rest] = line.split(/\s+/);
  if (kind === 'sold') {
    // every sold auction of the tag back to 90 days (or maxPages pages of 500)
    const maxPages = Number(rest[0] ?? 20);
    const all = [];
    for (let p = 0; p < maxPages; p++) {
      const page = await get(`${COFL}/auctions/tag/${tag}/sold?page=${p}&pageSize=500`);
      await sleep(1500);
      if (!Array.isArray(page) || page.length === 0) break;
      all.push(...page);
      const oldest = Math.min(...page.map((a) => Date.parse(a.end)));
      if (oldest < now - 90 * DAY || page.length < 500) break;
    }
    writeFileSync(`${OUT}/sold-${tag}.json`, JSON.stringify(all.map(slim)));
    const oldest = all.length ? Math.min(...all.map((a) => Date.parse(a.end))) : now;
    note(`sold ${tag}: ${all.length} rows, ${((now - oldest) / DAY).toFixed(1)} days`);
    const bin = await get(`${COFL}/auctions/tag/${tag}/active/bin`);
    await sleep(1500);
    writeFileSync(`${OUT}/bin-${tag}.json`, JSON.stringify(bin));
  } else if (kind === 'url') {
    // url <name> <path> : a raw probe
    const [name, path] = [tag, rest[0]];
    const res = await get(path.startsWith('http') ? path : COFL + path);
    await sleep(1500);
    writeFileSync(`${OUT}/probe-${name}.json`, JSON.stringify(res, null, 1));
  }
}

writeFileSync(`${OUT}/log.txt`, log.join('\n') + '\n');
