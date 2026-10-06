# Lodestock

A stock market for ores in Minecraft. Prices move with every trade, so what players buy and sell actually matters. Built for Paper and Purpur servers (OneBlock servers especially).

> **Status: public beta (0.3.0-beta.1).** All planned features are in, but expect small bugs and the occasional config change before 1.0.0.

Lodestock is a rework of **[OreMarket](https://github.com/OllieJW/Ore-Market) by OllieJW**, rebuilt for modern Minecraft with the original author's permission. Thank you, OllieJW, for the idea and the original plugin.

## Features

- A market window where players left-click to buy and right-click to sell, with live prices and stock.
- Prices rise when items are bought and fall when they are sold. Tax is taken on sales.
- Each item has a limited stock: the market can run out, and stops buying from players when it is full.
- `/lodestock sellhand` and `/lodestock sellall` for fast selling, with a confirmation step and a cooldown on `sellall`.
- A fully configurable window: title, rows, item slots, fill item, page buttons, and items pinned to exact slots.
- Admin tools: set prices and stock, reset, market crash and surge, stats, and a per-player trade history.
- Leaderboards (`/lodestock top`): top sellers, biggest spenders, most active traders, biggest single trade and net earners, for the last 24 hours, 7 days, 30 days or all time, for every item or one item. PlaceholderAPI placeholders for holograms and scoreboards.
- Discord webhook messages (optional) for big trades, admin changes and a daily summary. See [Discord](#discord).
- A [public API and events](docs/API.md) for other plugins: read prices and stats, react to trades, cancel them, and change the market.
- Economy safety tools: `/lodestock audit` finds settings that let players make free money, `/lodestock economy` shows how much money the market created, and [a guide with ready-made price presets](docs/ECONOMY.md) for low, balanced and high-income servers.
- Prices that drift back toward their base price and stock that regenerates, so crashes and surges fade on their own (even while the server is off).
- Price history: press Q on an item for a bar chart of its price (24 hours, 7 days or all time), with a 24h trend line in each item's description and `/lodestock chart` for chat.
- Daily buy and sell limits per player and item, with a configurable reset time and time zone.
- Every item has its own permission, `lodestock.ore.<item>`, that everyone has by default, so you can lock items for ranks.
- Market windows that update by themselves when prices, stock or limits change.
- Crash-safe storage: prices, stock and history are saved to a SQLite database as they change, so a server crash loses almost nothing.
- MiniMessage language file with a configurable message prefix.
- One jar for Paper and Purpur 1.21.11, 26.1, 26.2 and 26.3.

## Requirements

- **Paper or Purpur** 1.21.11, 26.1, 26.2 or 26.3. Other Paper forks may work, but they are untested.
- **Java**: whatever your Minecraft version needs (Java 21 for 1.21.11, Java 25 for 26.x).
- **[Vault](https://www.spigotmc.org/resources/vault.34315/)** and an economy plugin that works with it, such as EssentialsX. Lodestock does not have its own money.
- Optional: **[PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)** for the leaderboard and stats placeholders.

## Installation

1. Put `Lodestock-Paper-<version>.jar` in your server's `plugins` folder, along with Vault and your economy plugin.
2. Start the server. Lodestock creates `plugins/Lodestock/` with its config files.
3. Edit the files to taste (see [Configuration](#configuration)), then run `/lodestock reload`.

## Using the market

Open it with `/market`, `/openmarket` or `/lodestock`.

| Click | What it does |
|---|---|
| Q (drop key) on an item | Open the price history chart |
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
| `/lodestock limits` | Show what you can still buy and sell today | `lodestock.use` |
| `/lodestock chart <item> [24h\|7d\|all]` | Price history as a small text chart with low, high, average and change | `lodestock.chart` |
| `/lodestock top <board> [item] [24h\|7d\|30d\|all]` | Leaderboards: `sellers`, `spenders`, `active`, `biggest` and `net` (net needs `lodestock.top.net`) | `lodestock.top` |
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
| `/lodestock audit` | Checks your settings and prices for ways to make free money (buy-and-sell loops, crafting loops, risky values) and estimates the most the market can pay out per day | `lodestock.admin.audit` |
| `/lodestock economy [24h\|7d\|30d]` | Money the players spent and the market paid out, the money created (per active player, and compared with the period before), the biggest net earners and the items that paid out the most | `lodestock.admin.economy` |
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
| `lodestock.chart` | everyone | Price history charts (`/lodestock chart` and Q in the market window) |
| `lodestock.top` | everyone | `/lodestock top` |
| `lodestock.top.net` | op | The net earners board |
| `lodestock.sell.*` | nobody | All four selling permissions |
| `lodestock.ore.<item>` | everyone | Trade that item, for example `lodestock.ore.diamond`. Created automatically for every item in `items.yml` |
| `lodestock.ore.*` | everyone | Trade every item |
| `lodestock.limit.bypass` | op | Not affected by daily limits |
| `lodestock.admin.reload`, `.setprice`, `.setstock`, `.reset`, `.crash`, `.surge`, `.stats`, `.history`, `.audit`, `.economy` | op | One per admin command |
| `lodestock.admin.*` | op | All admin permissions |

Each selling method has its own permission, so you can, for example, allow the window but not `sellall` for new players (with a permissions plugin such as LuckPerms).

### Per-item permissions

Every item in `items.yml` gets its own permission automatically, `lodestock.ore.<item>` (for example `lodestock.ore.diamond` or `lodestock.ore.raw_iron`). **Everyone has all of them by default**, so nothing changes until you take one away. To lock an item, set its permission to `false` for a group or player in your permissions plugin, or set `lodestock.ore.*` to `false` and give back only the items you want. Locked items are shown greyed out in the window, and can't be bought or sold. The permissions are created when the server starts and on `/lodestock reload`, so a new item is covered straight away.

## Leaderboards and placeholders

### In chat

`/lodestock top <board> [item] [24h|7d|30d|all]` shows a leaderboard (7 days by default). Add an item to rank one item only, for example `/lodestock top sellers diamond 30d`.

| Board | Ranks players by |
|---|---|
| `sellers` | Money earned from selling |
| `spenders` | Money spent buying |
| `active` | Number of trades |
| `biggest` | Their single biggest trade (shows what it was) |
| `net` | Money earned minus money spent. **Operators only by default**, see below |

`24h`, `7d` and `30d` look at the trade history, so they can only go back as far as `history.keep-days` (30 by default). `all` uses lifetime stats that are kept separately and are **never deleted**, so all-time boards survive the history cleanup. When you upgrade, the stats are built from the trade history you already have.

The net board is for admins by default, because a public money board encourages farming. To open it up, give players `lodestock.top.net`.

### PlaceholderAPI

If [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) is installed, Lodestock registers these placeholders by itself. It works fine without PlaceholderAPI.

**Per player** (all time, as plain numbers, so other plugins can rank them). Add `_formatted` at the end for money in your server's currency format, and `_<item>` for one item.

| Placeholder | Value |
|---|---|
| `%lodestock_earned%` | Money earned from selling |
| `%lodestock_spent%` | Money spent buying |
| `%lodestock_net%` | Earned minus spent |
| `%lodestock_trades%` | Number of trades |
| `%lodestock_items_sold%` / `%lodestock_items_bought%` | Items sold or bought |
| `%lodestock_best_trade%` | Their single biggest trade |
| `%lodestock_earned_diamond%`, `%lodestock_items_sold_diamond%`, ... | The same, for one item |
| `%lodestock_net_formatted%` | Money with the currency format, such as `$1,234.50` |

**Leaderboard lines** (for holograms, scoreboards and signs): `%lodestock_top_<board>_<period>_<rank>_<field>%`, and for one item `%lodestock_top_<item>_<board>_<period>_<rank>_<field>%`.

- `<board>`: `sellers`, `spenders`, `active`, `biggest` or `net`
- `<period>`: `24h`, `7d`, `30d` or `all`
- `<rank>`: 1 to 50
- `<field>`: `name`, `value` (plain number), `formatted` (currency format), and for the biggest board also `action`, `amount` and `item`

Examples: `%lodestock_top_sellers_7d_1_name%`, `%lodestock_top_sellers_7d_1_formatted%`, `%lodestock_top_iron_ingot_sellers_all_3_name%`. An empty rank shows `-`.

Leaderboard placeholders read a saved copy that refreshes every `leaderboards.cache-seconds` (30 by default), so a hologram that updates every second adds no load to the server or the database.

### Holograms

Lodestock does not draw holograms itself. Use a hologram plugin that understands PlaceholderAPI, such as [DecentHolograms](https://www.spigotmc.org/resources/decentholograms-1-8-1-21-1-papi-support-no-dependencies.96927/) or [FancyHolograms](https://modrinth.com/plugin/fancyholograms) (with its PlaceholderAPI support). Put the placeholders straight into the hologram lines, one line per rank. For example, a weekly top-3 sellers hologram:

```
&6&lTop sellers (7 days)
&e1. &f%lodestock_top_sellers_7d_1_name% &7- &a%lodestock_top_sellers_7d_1_formatted%
&e2. &f%lodestock_top_sellers_7d_2_name% &7- &a%lodestock_top_sellers_7d_2_formatted%
&e3. &f%lodestock_top_sellers_7d_3_name% &7- &a%lodestock_top_sellers_7d_3_formatted%
```

You do **not** need ajLeaderboards for this. If you already use it, point it at a per-player placeholder such as `%lodestock_earned%` and it builds its own daily, weekly and monthly boards from that number.

## Discord

Lodestock can post to a Discord channel: big trades, market changes made by hand (crash, surge, reset, setprice, setstock) and a daily summary. It is off by default.

1. In Discord, open the channel's settings, then **Integrations > Webhooks > New Webhook > Copy Webhook URL**.
2. Open `plugins/Lodestock/discord.yml`, paste the address into `webhook-url` and set `enabled: true`.
3. Run `/lodestock reload`, then `/lodestock discord test`. A test message should appear in the channel.

Other settings in `discord.yml`: `big-trades.min-total` (the smallest trade, in money, that gets a message), `admin-actions`, `daily-summary` (time and time zone), and the name and picture shown on the messages.

- `/lodestock discord test` sends a test message and `/lodestock discord summary` sends the last 24 hours now (permission `lodestock.admin.discord`, operators by default).
- Treat the webhook address like a password. Anyone with it can post in your channel. Lodestock only accepts real `discord.com` webhook addresses, never prints the address in the console, and switches mentions off, so a player name can't ping `@everyone`.
- Messages are sent in the background. If Discord is slow or down, the server is not affected, and the console shows at most one warning a minute.
- `discord.yml` is created on the first start of this version, with the webhook off.

## Protecting your economy

A stock market plugin pays out money that did not exist before, so careless settings can wreck a server's economy. Lodestock gives you the tools to avoid that, and the [economy guide](docs/ECONOMY.md) explains how to use them:

- Ready-to-copy `items.yml` presets for a [conservative](docs/presets/items-conservative.yml), [balanced](docs/presets/items-balanced.yml) or [generous](docs/presets/items-generous.yml) economy, with the matching `config.yml` lines.
- How economies break (endless item sources, buy-and-sell loops, crafting loops, other money sources, alt accounts) and the setting that fixes each.
- `/lodestock audit` to check your setup after every change, and `/lodestock economy` to watch how much money the market creates.

Lodestock only sets the tools. The prices, taxes and limits are yours, and so is how they play out on your server.

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
| `price-floor` | `0.25` | Prices never fall below this. Keep it at about 5 to 10 percent of your cheapest item |
| `gui.bulk-amount` | `16` | How many items shift + left-click buys (2 to 64) |
| `gui.refresh-ticks` | `20` | How often (in ticks, 20 = one second) open windows check for changes. Only changed icons are redrawn, and nothing happens when nothing changed. 0 = windows only update when their player clicks |
| `sell-all.confirm` | `true` | Require `/lodestock sellall confirm` |
| `sell-all.confirm-seconds` | `15` | How long the confirmation stays valid (minimum 5) |
| `sell-all.cooldown-seconds` | `30` | Time before the same player can sell everything again (0 turns it off) |
| `recovery.interval-minutes` | `10` | Minutes between recovery steps (minimum 1) |
| `recovery.catch-up-hours` | `24` | Also apply the time the server was offline, up to this many hours (0 turns it off) |
| `drift.enabled` | `true` | Prices drift back toward their base price |
| `drift.percent` | `2.0` | Each step, the price closes this percent of its gap to the base price |
| `regen.enabled` | `true` | Stock moves back toward the starting stock |
| `regen.percent` | `2.0` | Each step, stock moves this percent of max-stock toward start-stock (at least 1 item) |
| `price-history.enabled` | `true` | Save price samples for charts and the 24h trend line |
| `price-history.interval-minutes` | `10` | Minutes between samples. Only items that changed are saved |
| `price-history.keep-days` | `14` | Delete samples older than this (0 keeps everything) |
| `limits.daily-buy` | `0` | The most one player can buy of one item per day (0 = no limit) |
| `limits.daily-sell` | `0` | The most one player can sell of one item per day (0 = no limit) |
| `limits.reset-time` | `"00:00"` | When a new limit day starts, as `HH:mm` |
| `limits.timezone` | `"server"` | Time zone for the reset: `server`, or a name such as `Asia/Manila` or `UTC` |
| `leaderboards.size` | `10` | How many lines `/lodestock top` shows (1 to 50) |
| `leaderboards.cache-seconds` | `30` | How often the leaderboard placeholders refresh their saved copy (minimum 5) |
| `admin.broadcast` | `true` | Announce crash and surge to everyone |
| `history.keep-days` | `30` | Delete trade history older than this at startup (0 keeps everything) |

**Keep `tax-percent` high enough for your `multiplier`.** If tax is too low, players can make money by buying and selling the same item in a loop, and big stacks make it worse than single items. Lodestock warns you in the console and on `/lodestock reload`, and `/lodestock audit` shows the details. The [economy guide](docs/ECONOMY.md) has the lowest safe tax for each multiplier.

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
| `daily-buy` | no | This item's daily buy limit per player. Overrides `limits.daily-buy` (0 = no limit) |
| `daily-sell` | no | This item's daily sell limit per player. Overrides `limits.daily-sell` (0 = no limit) |
| `drift` | no | `false` means this item's price never drifts back toward its base price |
| `regen` | no | `false` means this item's stock never regenerates |

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
- **Recovery:** every `recovery.interval-minutes`, prices drift back toward `base-price` and stock moves back toward `start-stock`, so crashes, surges and heavy selling fade on their own. The pull is stronger when the price is far from the base and gentle when it is close.
- Time the server was offline is caught up on startup (up to `recovery.catch-up-hours`).
- A price set with `/lodestock setprice` (or stock set with `setstock`) is left alone until the next trade on that item. `crash` and `surge` do fade.
- **Daily limits:** with `limits.daily-buy` / `limits.daily-sell` (or the per-item `daily-buy` / `daily-sell`), each player can only buy or sell so many of an item per day. Bulk trades, `sellhand` and `sellall` go up to what is left and keep the rest in the inventory. The window and `/lodestock limits` show what is left. Counts are saved, so relogging or restarting doesn't reset them. Operators (and anyone with `lodestock.limit.bypass`) are never limited, so test with a normal account.
- If the economy plugin refuses a payment (for example a money cap), the trade is cancelled and nothing is taken.

## Data and history

- Everything is stored in one SQLite file, `plugins/Lodestock/lodestock.db`: live prices and stock, every trade, and every admin action.
- Each change is written to the database as it happens, in the background, so it never slows the server down. If the server **crashes**, at most the last few milliseconds of changes can be lost.
- While the server runs you will also see `lodestock.db-wal` and `lodestock.db-shm`. That is normal.
- **Backups:** copy `lodestock.db` while the server is **stopped**, and copy the `-wal` and `-shm` files with it if they exist.
- **Reset the whole market:** stop the server and delete the `lodestock.db` files, or use `/lodestock reset all confirm`.
- Trade history older than `history.keep-days` is deleted at startup. The lifetime stats behind the all-time leaderboards are small (one row per player and item) and are never deleted.
- `/lodestock history` finds players who are online or that the server has seen before.

## Known limits (alpha)

- Single server only. There is no proxy or network support, and no MySQL or MariaDB.
- No PlaceholderAPI placeholders. They'll be added if people ask for them.
- Trade results are sent as chat messages, so they can be hard to read while the window is open.
- Vanilla items only.

## Roadmap

1. **Done:** the Paper and Purpur plugin, crash-safe SQLite storage, price drift, daily limits, per-item permissions and live windows (the current beta).
2. **Next:** price history and charts, a public API, a Discord webhook, and scheduled crash and surge events.
3. **Then:** polish and the 1.0.0 release.

Fabric and NeoForge versions, and ItemsAdder/Oraxen support, are postponed.

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
