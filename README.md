# Yoav Addons + Bazaar Analyzer

Tools for Hypixel SkyBlock by yoav3577.

**Use the website online:** https://yoav2777.github.io/YoavAddons/ (nothing to install).

**Download:** [latest release](https://github.com/yoav2777/YoavAddons/releases/latest) has two files:

| File | What |
|---|---|
| `yoav-addons-<version>.jar` | **Yoav Addons**, the Minecraft mod (the website is built in) |
| `Bazaar-Analyzer.zip` | **Bazaar Analyzer**, the website to run on your PC without the mod (optional) |

The folders in this repo are the source code. You don't need them to use the tools.

## Setup: mod

Needs Minecraft **26.1.2** with **Fabric Loader 0.19.3+**, **Fabric API** and **Java 25**.

1. Close the game.
2. Download the `.jar` from the release and put it in your game profile's `mods` folder.
3. Start the game. Press **I** to open the price graph, `/ya` for the settings, `/ya site` to open the website.

The mod has the website built in: `/ya site`, the trade window button and AH links open it in your browser.
The online website's **Activity** tab also shows your trades while the game is running (the browser may ask to allow
access to your local network: allow it).

### Lowball tracker on more than one PC

The lowball tracker's data is saved on the PC you play on. To share it between your PCs (desktop + laptop):

1. On github.com: **Settings > Developer settings > Personal access tokens > Tokens (classic) > Generate new token (classic)**,
   tick only **gist**, set **No expiration**, generate, and copy the token.
2. In game: `/ya` > **Cloud sync** > paste the token in **GitHub token**. Do the same on every PC (same token).

The mod keeps the data in a secret gist on your GitHub and syncs it on start, every few minutes and when the game
closes (`/ya sync` syncs now). Nothing is ever deleted by the sync, so PCs can't overwrite each other.

## Setup: website on your PC (optional)

1. Download `Bazaar-Analyzer.zip` from the release and extract it (right-click > Extract All).
2. Open the `Bazaar-Analyzer` folder and double-click **Start Bazaar Analyzer.bat**.
3. The site opens in your browser. Keep the black window open while you use it; close it to stop.

Needs Windows 10/11 and internet. Nothing to install.
If Windows says "Windows protected your PC": **More info** > **Run anyway**.
More details: `README.txt` in that folder. When this copy is running, the mod uses it instead of its built-in one.
