# Claude-like theme (v12)

Files:
- `C:/Users/Lenovo/Bazaar-Analyzer/app/assets/theme-claude.css` (~70 lines), loaded by one extra line in
  `app/index.html` right after technical-v3.css: `<link rel="stylesheet" href="./assets/theme-claude.css">`.
- index.html favicon: rect fill `%231f1e1d`, stroke `%23d97757` (was `%23121722` / `%23fbbf24`).
- Bundle JS untouched. SOURCE OF TRUTH is now `C:/Users/Lenovo/Bazaar-Analyzer-dev/patch/theme-claude.css`: patch_v12.mjs
  copies it to SITE, adds/updates its link with `?v=<sha1>` right after technical-v3.css and swaps the favicon colours
  (idempotent; see craft-ui.md "Build / ship"). Don't edit the SITE copy, it is overwritten on every run.

## Palette tokens (defined on :root in theme-claude.css)
| token | value | use |
|---|---|---|
| --color-bg | #1a1918 | page, header, chart plot area |
| --color-panel | #232220 | cards / tables (`bg-panel`) |
| --color-panel2 | #2c2b28 | inputs, raised chips, popovers (`bg-panel2`) |
| --color-line | #3a3935 | borders (`border-line`) |
| --color-ink | #ece9e1 | body text (`text-ink`) |
| --color-mute | #a09d93 | secondary text (`text-mute`), ~6.5:1 on panel |
| --color-accent | #d97757 | Claude orange (`text-accent`, `bg-accent`, `border-accent`) |
| --ba-accent-deep | #c15f3c | primary button hover, scrollbar hover |
| --ba-accent-soft | rgba(217,119,87,.14) | selected/active tint |
| --ba-accent-line | rgba(217,119,87,.45) | selected/active border |
| --ba-hover | rgba(236,233,225,.05) | row / button hover |

Untouched on purpose (data colors): --color-buy #fbbf24 (insta-buy), --color-sell #38bdf8 (insta-sell),
--color-up #22c55e, --color-down #ef4444, rarity colors (JS const `Kr`), chart colors (JS const
`X={ask,bid,up,down,text,grid,line,bg,label}` and klinecharts styles in `Ju(...)`, indicator palettes
`Q`/`Ld`). Charts never read CSS vars, so the accent change cannot reach them.

## How new UI (e.g. craft-price components) should match
Use the existing Tailwind utilities that are in the prebuilt CSS; they pick up the palette automatically:
- card: `rounded-lg border border-line bg-panel p-4`; inner box: `rounded border border-line bg-panel2 px-3 py-2`
- panel title: `<h2>` (technical-v3 makes it small uppercase muted)
- text: `text-ink`, `text-mute`, `text-xs`/`text-sm`/`text-[11px]`; prices: `text-buy` (insta-buy), `text-sell`
  (insta-sell / buy-order price), `text-up`/`text-down` for profit/loss
- selected tab/pill: `bg-accent/20 text-accent`; unselected: `text-mute hover:bg-white/5 hover:text-ink`
- primary button: `rounded-md bg-accent px-3 py-1.5 text-sm font-medium text-bg hover:opacity-90`
- secondary button: `rounded-md bg-accent/20 px-3 py-1 text-accent hover:bg-accent/30`
- link: `text-accent hover:underline`
- popover / hover breakdown: `rounded-lg border border-line bg-panel2 shadow-xl` (technical-v3 turns any
  `shadow-*` into one dark shadow); for a cursor-following tooltip reuse `.ba-tip` + `.ba-tip-row`.
- Only utilities already compiled in index-CZorzv8u.css exist (no Tailwind build step). Check with
  `grep -o '\.bg-accent[^{]*' app/assets/index-CZorzv8u.css`. For anything else add a small `ba-*` class to
  theme-claude.css (or a new css) using the vars above, never hard-coded blues.

## Selectors theme-claude.css relies on (if the bundle changes, recheck)
- header logo mark: `header > div > a:first-child > span:first-child`
- active nav tab: `header nav a.bg-white\/10`
- selected segmented buttons: `button.bg-white\/10` get an inset orange bottom edge
- compiled hex fallbacks re-pointed: `.bg-accent\/15`, `.bg-accent\/20`, `.hover\:bg-accent\/30:hover`,
  `.border-accent\/60`, `.hover\:border-accent\/50:hover`

## Screenshots (1440x900, headless Edge; *-v11 = backup, *-v12 = themed)
`C:/Users/Lenovo/Bazaar-Analyzer-dev/notes/screens/theme/`: market, item (ENCHANTED_DIAMOND), ah, ahitem
(HYPERION), ahitem-tall-v12 (1440x2200 incl. sales list), auction (sold listing detail), alerts, settings, activity.
Helper: `sh C:/Users/Lenovo/Bazaar-Analyzer-dev/tools/shot.sh <port> <hash-route> <out.png> [w h]`
(headless Edge, throwaway profile per port; don't run two shots on the same port in parallel, the profile locks).
Serve: `powershell -NoProfile -ExecutionPolicy Bypass -File C:/Users/Lenovo/Bazaar-Analyzer/serve.ps1 -Port 47842 -NoBrowser`.
