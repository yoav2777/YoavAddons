// Dev script (not shipped): builds the compact output-indexed recipe file the site loads as app/data/recipes.json
// (format: notes/craft-spec.md 2b) from an unzipped NEU repo.
//   "C:/Program Files/nodejs/node.exe" craft/buildRecipes.mjs [NEU_REPO_DIR] [OUT_FILE]
// Defaults: notes/cache/neurepo/repo -> notes/samples/recipes-compact.json. Get a fresh repo from
// https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/refs/heads/master.zip
// Trade entries with min/max (EMERALD.json: "65-76 logs for 1 emerald") use the average as the cost count.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = process.argv[2] || path.join(HERE, '../notes/cache/neurepo/repo');
const OUT = process.argv[3] || path.join(HERE, '../notes/samples/recipes-compact.json');

const parseStack = s => { s = String(s); const i = s.lastIndexOf(':'); if (i < 0) return [s, 1]; const n = Number(s.slice(i + 1)); return Number.isFinite(n) ? [s.slice(0, i), n] : [s, 1]; };
const grid = g => { const t = {}; for (const k of ['A1', 'A2', 'A3', 'B1', 'B2', 'B3', 'C1', 'C2', 'C3']) if (g[k]) { const [id, n] = parseStack(g[k]); t[id] = (t[id] || 0) + n; } return Object.entries(t); };
const tradeCost = r => { const [id, n] = parseStack(r.cost); const mm = [Number(r.min), Number(r.max)].filter(x => x > 0); return [id, mm.length ? n * mm.reduce((a, b) => a + b) / mm.length : n]; };

const REC = {};
const add = (out, r) => { (REC[out] = REC[out] || []).push(r); };
let files = 0;
for (const f of fs.readdirSync(path.join(REPO, 'items'))) {
  if (!f.endsWith('.json')) continue;
  const id = f.slice(0, -5);
  let j;
  try { j = JSON.parse(fs.readFileSync(path.join(REPO, 'items', f), 'utf8')); } catch (e) { console.warn('skip ' + f + ': ' + e.message); continue; }
  files++;
  if (j.recipe) add(id, ['c', Number(j.recipe.count) || 1, grid(j.recipe)]);
  for (const r of j.recipes || []) {
    try {
      if (r.type === 'crafting') add(r.overrideOutputId || id, ['c', Number(r.count) || 1, grid(r)]);
      else if (r.type === 'forge') add(r.overrideOutputId || id, ['f', Number(r.count) || 1, r.inputs.map(parseStack), r.duration]);
      else if (r.type === 'npc_shop') { const [o, n] = parseStack(r.result); add(o, ['n', n, r.cost.map(parseStack)]); }
      else if (r.type === 'trade') { const [o, n] = parseStack(r.result); add(o, ['t', n, [tradeCost(r)]]); }
      else if (r.type === 'katgrade') add(r.output, ['k', 1, [[r.input, 1], ...(r.items || []).map(parseStack), ['SKYBLOCK_COIN', Number(r.coins) || 0]], r.time]);
    } catch (e) { console.warn('skip recipe in ' + f + ': ' + e.message); }
  }
}
for (const id in REC) for (const r of REC[id]) if (!(r[3] > 0)) r.length = 3;   // drop empty duration
fs.writeFileSync(OUT, JSON.stringify(REC));
console.log(`${files} NEU files -> ${Object.keys(REC).length} outputs, ${fs.statSync(OUT).size} bytes -> ${OUT}`);
