import { createRequire } from 'node:module';
const req = createRequire('C:/Users/Lenovo/bazaar graphs/package.json');
const y = req('react'); const b = req('react/jsx-runtime'); const { renderToString } = req('react-dom/server');
import * as core from 'file:///C:/Users/Lenovo/Bazaar-Analyzer-dev/craft/craftCore.js';
import * as mods from 'file:///C:/Users/Lenovo/Bazaar-Analyzer-dev/craft/craftModifiers.js';
import { fixtureCtx, HYITEMS, STONES, ENCH, LISTING_KEYS, listing } from 'file:///C:/Users/Lenovo/Bazaar-Analyzer-dev/craft/tests/fx.mjs';
const BA_craft = { ...core, ...mods };
function en(e){if(e==null||!Number.isFinite(e))return'—';let n=Math.abs(e);if(n>=1e9)return(e/1e9).toFixed(2)+'B';if(n>=1e7)return(e/1e6).toFixed(2)+'M';return n>=1e3?e.toLocaleString('en-US',{maximumFractionDigits:1}):n>=100?e.toFixed(1):e.toFixed(2)}
function tn(e,t=1){return e==null||!Number.isFinite(e)?'—':e.toLocaleString('en-US',{minimumFractionDigits:0,maximumFractionDigits:t})}
function Pt(e){return e}
function Ft(e,t){return t?.[e]?.name??Pt(e)}
function It(e){return e.replace(/§./g,'')}
function Wi(e){return e.replace(/§./g,'').replace(/[✪➊➋➌➍➎]/g,'').trim()}
function Oa(){return undefined}
function useCraftCtx(){return globalThis.__ctx}
function Mt(e){return e?e[0]+e.slice(1).toLowerCase():e}
