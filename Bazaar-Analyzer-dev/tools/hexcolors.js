// One-off readability pass over the decompiled mod: turn inlined ARGB color ints back into hex / named constants.
const fs = require('fs');
const dir = process.argv[2];
const hex = v => '0x' + ((v >>> 0).toString(16).toUpperCase().padStart(8, '0'));
const named = {
  'PriceScreen.java': { '-278748': 'BUY', '-13058568': 'SELL', '-2564375': 'TEXT', '-8616295': 'MUTED', '-267579113': 'PANEL', '-14538185': 'EDGE_COLOR', '-10781776': 'EDGE_HOT', '-14998992': 'GRID' },
  'SettingsScreen.java': { '-2564375': 'TEXT', '-8616295': 'MUTED' },
};
for (const f of ['PriceScreen.java', 'SettingsScreen.java', 'TradeAhUi.java']) {
  const p = dir + '/' + f;
  const names = named[f] || {};
  const out = fs.readFileSync(p, 'utf8').split('\n').map(line => {
    const decl = /static final int \w+ = (-?\d+);/.exec(line);
    if (decl) return Math.abs(+decl[1]) > 100000 ? line.replace(decl[1], hex(+decl[1])) : line;
    return line.replace(/(?<![\w.])(-\d{6,}|1728053247)(?![\w.])/g, m => names[m] || hex(+m));
  });
  fs.writeFileSync(p, out.join('\n'));
}
