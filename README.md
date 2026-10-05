# Lodestock

A stock market for ores in Minecraft. Prices move with every trade, so what players buy and sell actually matters. Built for Paper and Purpur servers (OneBlock servers especially).

> **Status: early development (0.1.0-alpha.1).** Lodestock is not publicly released yet. Expect small bugs and config changes between alpha versions.

Lodestock is a rework of **[OreMarket](https://github.com/OllieJW/Ore-Market) by OllieJW**, rebuilt for modern Minecraft with the original author's permission. Thank you, OllieJW, for the idea and the original plugin.

## Features

- A market window where players left-click to buy and right-click to sell, with live prices and stock.
- Prices rise when items are bought and fall when they are sold. Tax is taken on sales.
- Each item has a limited stock: the market can run out, and stops buying from players when it is full.
- `/lodestock sellhand` and `/lodestock sellall` for fast selling, with a confirmation step and a cooldown on `sellall`.
- A fully configurable window: title, rows, item slots, fill item, page buttons, and items pinned to exact slots.
- Admin tools: set prices and stock, reset, market crash and surge, stats, and a per-player trade history.
- Crash-safe storage: prices, stock and history are saved to a SQLite database as they change, so a server crash loses almost nothing.
- MiniMessage language file with a configurable message prefix.
- One jar for Paper and Purpur 1.21.11, 26.1, 26.2 and 26.3.

## Requirements

- **Paper or Purpur** 1.21.11, 26.1, 26.2 or 26.3. Other Paper forks may work, but they are untested.
- **Java**: whatever your Minecraft version needs (Java 21 for 1.21.11, Java 25 for 26.x).
- **[Vault](https://www.spigotmc.org/resources/vault.34315/)** and an economy plugin that works with it, such as EssentialsX. Lodestock does not have its own money.

## Installation

1. Put `Lodestock-Paper-<version>.jar` in your server's `plugins` folder, along with Vault and your economy plugin.
2. Start the server. Lodestock creates `plugins/Lodestock/` with its config files.
3. Edit the files to taste (see [Configuration](#configuration)), then run `/lodestock reload`.

## Using the market

Open it with `/market`, `/openmarket` or `/lodestock`.

| Click | What it does |
|---|---|
| Left-click an item | Buy 1 |
| Shift + left-click | Buy several (16 by default, set by `gui.bulk-amount`) |
| Right-click | Sell 1 |
| Shift + right-click | Sell all of that item you are carrying |

Only **plain** items can be sold. Renamed, enchanted or otherwise modified items are refused, so players can't sell a custom item as a normal one.

## Commands

### Players

| Command | Description | Permission |
|---|---|---|
| `/lodestock` (or `/market`, `/openmarket`) | Open the market | `lodestock.use` |
| `/lodestock price <item>` | Buy price, sell price and stock of one item | `lodestock.use` |
| `/lodestock sellhand` | Sell the whole stack in your main hand | `lodestock.sell.hand` |
| `/lodestock sellall` | Show what everything in your inventory would sell for | `lodestock.sell.all` |
| `/lodestock sellall confirm` | Confirm the sale (within 15 seconds) | `lodestock.sell.all` |
| `/lodestock help` | List the commands you can use | none |

`sellall` only looks at the hotbar and main inventory. Armor and the off-hand are never touched. Items the market can't buy right now (stock full, selling turned off) stay in the inventory, and you are told which.

### Admins

All of these also work from the console.

| Command | Description | Permission |
|---|---|---|
| `/lodestock setprice <item> <price>` | Set an item's current price (not below the price floor) | `lodestock.admin.setprice` |
| `/lodestock setstock <item> <amount>` | Set an item's current stock (0 to its max stock) | `lodestock.admin.setstock` |
| `/lodestock reset <item>` | Reset an item to its base price and starting stock | `lodestock.admin.reset` |
| `/lodestock reset all` | Reset every item (asks you to confirm with `reset all confirm`) | `lodestock.admin.reset` |
| `/lodestock crash <percent> [item]` | Cut prices by 1 to 99%, for one item or all | `lodestock.admin.crash` |
| `/lodestock surge <percent> [item]` | Raise prices by 1 to 1000%, for one item or all | `lodestock.admin.surge` |
| `/lodestock stats` | Biggest price movers and lowest stock | `lodestock.admin.stats` |
| `/lodestock history <player> [page]` | A player's trades, newest first | `lodestock.admin.history` |
| `/lodestock reload` | Reload all config files and show any problems found | `lodestock.admin.reload` |

`crash` and `surge` announce themselves to the whole server unless you set `admin.broadcast: false`. Admin actions are recorded in the database.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `lodestock.use` | everyone | Open the market, `/lodestock price` |
| `lodestock.buy` | everyone | Buy in the market window |
| `lodestock.sell.gui` | everyone | Sell in the market window |
| `lodestock.sell.hand` | everyone | `/lodestock sellhand` |
| `lodestock.sell.all` | everyone | `/lodestock sellall` |
| `lodestock.sell.*` | nobody | All four selling permissions |
| `lodestock.admin.reload`, `.setprice`, `.setstock`, `.reset`, `.crash`, `.surge`, `.stats`, `.history` | op | One per admin command |
| `lodestock.admin.*` | op | All admin permissions |

Each selling method has its own permission, so you can, for example, allow the window but not `sellall` for new players (with a permissions plugin such as LuckPerms).

## Configuration

Everything lives in `plugins/Lodestock/`:

| File | What it holds |
|---|---|
| `config.yml` | Tax, price movement, limits, cooldowns |
| `items.yml` | What the market sells and for how much |
| `gui.yml` | The look and layout of the market window |
| `lang/en.yml` | Every message and the item description text |
| `lodestock.db` | Live prices, stock, trade history and the admin log (a SQLite database). Managed by the plugin |

### config.yml

| Setting | Default | Description |
|---|---|---|
| `language` | `en` | Which `lang/<name>.yml` to use. Missing messages fall back to English |
| `tax-percent` | `10.0` | Percent taken off the price when a player **sells** |
| `multiplier` | `0.01` | How much each trade moves the price (0.01 is about 1%) |
| `price-floor` | `0.01` | Prices never fall below this |
| `gui.bulk-amount` | `16` | How many items shift + left-click buys (2 to 64) |
| `sell-all.confirm` | `true` | Require `/lodestock sellall confirm` |
| `sell-all.confirm-seconds` | `15` | How long the confirmation stays valid (minimum 5) |
| `sell-all.cooldown-seconds` | `30` | Time before the same player can sell everything again (0 turns it off) |
| `admin.broadcast` | `true` | Announce crash and surge to everyone |
| `history.keep-days` | `30` | Delete trade history older than this at startup (0 keeps everything) |

**Keep `tax-percent` high enough for your `multiplier`.** If tax is too low, players can make money by buying and selling the same item in a loop. Lodestock warns you in the console and on `/lodestock reload` when it detects this.

### items.yml

Each key is a Minecraft item ID. The `minecraft:` prefix is optional.

```yaml
diamond:
  base-price: 80.0
  start-stock: 50
  max-stock: 500
```

| Setting | Required | Description |
|---|---|---|
| `base-price` | yes | Price when the market is fresh (above 0) |
| `start-stock` | yes | How many the market starts with |
| `max-stock` | yes | The market stops buying from players at this amount |
| `allow-buy` | no | `false` means players can't buy it |
| `allow-sell` | no | `false` means players can't sell it |
| `slot` | no | Pin the item to an exact slot in the window (counting from 0) |
| `page` | no | The page for that slot (default 1) |

- **The item ID is also how prices are saved.** If you rename a key, that item starts over at its base price and stock.
- Each item can only appear once. Duplicates and unknown item IDs are skipped with a warning.
- The default list sells ingots and finished gems, so players smelt ores first. Storage blocks (such as `iron_block`) are included as comments. To enable one, remove the `#` signs and price it at about 9 times the ingot, so players can't profit from crafting.

### gui.yml

| Setting | Description |
|---|---|
| `title` | Window title (MiniMessage) |
| `rows` | Number of rows, 1 to 6 |
| `item-slots` | Where items go, such as `["10-16", "19-25"]`. Items continue onto new pages when full |
| `fill` | Decoration for empty slots: `enabled`, `material`, `name` |
| `previous-page`, `next-page` | Page buttons: `enabled`, `slot`, `material`, `name`. In the names, `<page>` and `<pages>` are filled in |
| `close` | Close button: `enabled`, `slot`, `material`, `name` |

The page buttons only appear when there is a page to go to. Bad slots and overlaps are reported as warnings when you reload.

### Language and messages

`lang/en.yml` holds every message. Text uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html), such as `<red>` and `<gold>`.

- **`prefix`** is shown wherever a message contains `<prefix>`. Change it, or remove `<prefix>` from a message to hide it.
- To add a language, copy `en.yml` to `lang/<name>.yml`, translate it, and set `language: <name>` in `config.yml`.

## How prices work

- **Buying** costs the current price, with no tax. It lowers the stock by 1 and raises the price a little.
- **Selling** pays the current price minus `tax-percent`. It raises the stock by 1 and lowers the price a little.
- Buying stops when the stock hits 0. Selling stops when the stock reaches `max-stock`.
- Bulk trades work out the price change after every single item, so buying 64 costs more than 64 times the first price.
- If the economy plugin refuses a payment (for example a money cap), the trade is cancelled and nothing is taken.

## Data and history

- Everything is stored in one SQLite file, `plugins/Lodestock/lodestock.db`: live prices and stock, every trade, and every admin action.
- Each change is written to the database as it happens, in the background, so it never slows the server down. If the server **crashes**, at most the last few milliseconds of changes can be lost.
- While the server runs you will also see `lodestock.db-wal` and `lodestock.db-shm`. That is normal.
- **Backups:** copy `lodestock.db` while the server is **stopped**, and copy the `-wal` and `-shm` files with it if they exist.
- **Reset the whole market:** stop the server and delete the `lodestock.db` files, or use `/lodestock reset all confirm`.
- Trade history older than `history.keep-days` is deleted at startup.
- `/lodestock history` finds players who are online or that the server has seen before.

## Known limits (alpha)

- Single server only. There is no proxy or network support, and no MySQL or MariaDB.
- No PlaceholderAPI placeholders. They'll be added if people ask for them.
- Trade results are sent as chat messages, so they can be hard to read while the window is open.
- Vanilla items only.

## Roadmap

1. **Done:** the Paper and Purpur plugin, with crash-safe SQLite storage (this version).
2. **Next:** performance checks and extra features such as price history and daily limits.
3. **Later:** Fabric and NeoForge versions.

## Building from source

You need JDK 21 or newer.

```
./gradlew build
```

The plugin jar is in `paper/build/libs/`. On Windows, use `gradlew.bat build`.

The project has two modules: `core` (the market logic, with no Minecraft code and unit tests) and `paper` (the plugin).

## Statistics

Lodestock uses [bStats](https://bstats.org/plugin/bukkit/Lodestock/34522) to collect anonymous usage statistics. Server owners can opt out in `plugins/bStats/config.yml`.

## License and credits

Lodestock is licensed under the **GNU General Public License v3.0**. See the `LICENSE` file.

It is based on [OreMarket](https://github.com/OllieJW/Ore-Market) by **OllieJW**, also GPL-3.0, used and reworked with the author's permission.
