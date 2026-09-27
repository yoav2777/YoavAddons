// Tailwind v4 only emitted the classes the original React build used. New components can only use
// classes that already exist in the shipped CSS (or must add rules to technical-v3.css / a new css).
// Usage: node check_classes.mjs <file.js | "class1 class2 ..."> [cssDir]
import fs from 'node:fs';
const CSS_DIR = process.argv[3] ?? 'C:/Users/Lenovo/Bazaar-Analyzer/app/assets/';
const css = fs.readdirSync(CSS_DIR).filter((f) => f.endsWith('.css'))
  .map((f) => fs.readFileSync(CSS_DIR + '/' + f, 'utf8')).join('\n');
const arg = process.argv[2] ?? '';
let classes;
if (fs.existsSync(arg)) {
  const src = fs.readFileSync(arg, 'utf8');
  classes = [...src.matchAll(/className:\s*[`'"]([^`'"]*)[`'"]/g)]
    .flatMap((m) => m[1].replace(/\$\{[^}]*\}/g, ' ').split(/\s+/));
} else classes = arg.split(/\s+/);
const BS = String.fromCharCode(92);
const esc = (c) => c.replace(/[^a-zA-Z0-9_-]/g, (ch) => BS + ch);
// tokens from ${...} expressions are skipped (check those classes by passing them as a list)
const missing = [...new Set(classes.filter((c) => c && !/[${}?()`'"]/.test(c)))].filter((c) => {
  const e = '.' + esc(c);
  for (let i = css.indexOf(e); i >= 0; i = css.indexOf(e, i + 1)) {
    if (!/[\w-]/.test(css[i + e.length] ?? '')) return false;
  }
  return true;
});
console.log(missing.length ? 'MISSING: ' + missing.join(' ') : 'all classes present');
process.exitCode = missing.length ? 1 : 0;
