# Changelog

All notable changes to Lodestock are listed here. Lodestock uses [semantic versioning](https://semver.org/).

## Unreleased

### Market
- Price history: every 10 minutes, the price and stock of every item that changed are saved (kept for 14 days by default).
- Press Q on an item in the market window to open a bar chart of its price: 9 time slices, green when the price went up and red when it went down, with low, high, average and change. Buttons switch between the last 24 hours, 7 days and all time.
- New `/lodestock chart <item> [24h|7d|all]` command with a one-line text chart and the same numbers.
- A "Trend (24h)" line and a "Press Q to open the price chart" hint in each item's description.
- New permission `lodestock.chart` (everyone by default).
- New `price-history` settings in `config.yml`.

### Leaderboards
- New `/lodestock top <board> [item] [24h|7d|30d|all]` command (`lodestock.top`, everyone by default) with five boards: `sellers` (money earned), `spenders` (money spent), `active` (trades), `biggest` (single biggest trade) and `net` (earned minus spent). Add an item to rank one item.
- The `net` board needs `lodestock.top.net` (operators by default), because a public money board encourages farming.
- Lifetime stats (one row per player and item) are saved separately and never deleted, so `all` works even after `history.keep-days` removes old trades. The database upgrades itself (schema version 5) and builds the stats from the trade history you already have.
- PlaceholderAPI support (optional): per-player numbers such as `%lodestock_earned%` and `%lodestock_net_formatted%`, and leaderboard lines such as `%lodestock_top_sellers_7d_1_name%` for holograms. Leaderboard placeholders read a cache and never wait for the database. See the README.
- `/lodestock economy` now also shows money created per active player and compares with the period before.
- New `leaderboards` settings in `config.yml`.

### Economy safety
- New guide, [docs/ECONOMY.md](docs/ECONOMY.md), with three ready-to-copy price presets (conservative, balanced, generous) in `docs/presets/`, the config lines that go with them, and advice by game mode.
- New `/lodestock audit` command (`lodestock.admin.audit`): checks for buy-and-sell loops (tested with real trades of many sizes, up to each item's max stock), crafting loops (ingot and block, raw ore and ingot, and similar pairs), a zero or very low tax, a very high multiplier, a very low price floor, and recovery with no daily limits. It also estimates the most money the market can pay out per day.
- New `/lodestock economy [24h|7d|30d]` command (`lodestock.admin.economy`): money spent and paid out, money created, the biggest net earners and the items that paid out the most.
- The "tax too low" warning on startup and reload is now the economy audit, so it also catches a high multiplier that is exploitable with big stacks even when single trades lose money, and selling then buying back.
- The default `price-floor` in a new `config.yml` is now `0.25` (was `0.01`), so an item cannot crash to almost nothing. Existing config files are not changed.

### Public API
- New `lodestock-api` module and [docs/API.md](docs/API.md): other plugins can read prices, stock, quotes, price history, player stats and leaderboards, and change prices and stock with a named `source`. Get it with `LodestockApi.find()`; it is also registered with Bukkit's services manager.
- New events: `LodestockPreTradeEvent` (cancellable), `LodestockTradeEvent` and `LodestockMarketAdjustEvent` (cancellable, for hand-made changes).
- All admin changes (`setprice`, `setstock`, `reset`, `crash`, `surge`) now go through one place, so the commands and the API behave the same and are logged the same way.
- New messages `trade-cancelled` and `admin-cancelled`. Existing `lang/en.yml` files are not changed: the built-in text is used until you add them.
- New `jitpack.yml` so the API can be used from JitPack.

### Discord
- New optional Discord webhook (`discord.yml`, off by default): a message for big trades (`big-trades.min-total`), for market changes made by hand (including ones made through the API), and a daily summary at a time and time zone you choose. See the README.
- New `/lodestock discord test` and `/lodestock discord summary` commands (`lodestock.admin.discord`, operators by default).
- Only real Discord webhook addresses are accepted, the address is never logged, mentions are switched off, and sending happens in the background with Discord's rate limits respected.
- `discord.yml` is created on the first start of this version. New messages `discord-*` and `usage-discord` use the built-in text until you add them to your `lang/en.yml`.

### Scheduled events
- New `events.yml` (off by default): crashes and surges that run by themselves at times and days you choose, for all items or a list, with a chance, a warning before they start (`warn-minutes`) and a time zone. Two sample events are included. See the README.
- Whether an event happens is decided when the warning goes out, so players are only warned about events that really happen. Events that were due more than 2 minutes ago are skipped.
- New `/lodestock events` (shows every event and when it runs next) and `/lodestock events run <id>` (runs one now) commands (`lodestock.admin.events`, operators by default).
- Events are logged, shown in the Discord webhook and cancellable through the API like any other hand-made change. An event on several items is one log entry.
- Startup and `/lodestock reload` warn about events that are bigger than your tax, and about mistakes in `events.yml`. One bad event never stops the others.
- `events.yml` is created on the first start of this version, with scheduling off. The new `event-*` and `events-*` messages use the built-in text until you add them to your `lang/en.yml`.

### Storage
- The database file is upgraded automatically (schema version 5: adds the price history table and the lifetime stats table).

### Upgrading from 0.3.0-beta.1
Everything works with defaults. To see and change the new settings, add this to your `config.yml`:

```yaml
price-history:
  enabled: true
  interval-minutes: 10   # minutes between samples
  keep-days: 14          # delete samples older than this (0 = keep everything)
```

The leaderboards work with no config changes. To change how many lines they show or how often placeholders refresh, add this to your `config.yml`:

```yaml
leaderboards:
  size: 10             # lines shown by /lodestock top (1 to 50)
  cache-seconds: 30    # how often placeholders refresh their saved copy (minimum 5)
```

The new commands and warnings work with no config changes. If you want the safer defaults, raise `price-floor` to about 5 to 10 percent of your cheapest item (the audit tells you when it is too low). The wording of the audit and economy messages falls back to built-in English text.

The "Trend (24h)" line and the "Press Q to open the price chart" hint are added by the plugin itself, so they show up even with an older `lang/en.yml`. To change their wording, copy `item-trend` and `item-chart-hint` from the bundled `lang/en.yml` into the `gui:` section of yours.

## 0.3.0-beta.1 - 2026-10-06

Second public beta. Adds price drift, daily limits, per-item permissions and live market windows. Read "Upgrading from 0.2.0-beta.1" below before updating.

### Market
- Price drift: prices slowly move back toward their base price, so crashes and surges fade.
- Stock regeneration: stock moves back toward the starting stock (low stock refills, piled-up stock drains).
- The server's offline time is caught up on startup (up to 24 hours by default).
- Prices and stock set by an admin are left alone until the next trade on that item.
- New `recovery`, `drift` and `regen` settings in `config.yml`, and optional `drift` / `regen` per item in `items.yml`.

- Daily limits: cap how much one player can buy or sell of an item per day, with a default in `config.yml` and optional per-item `daily-buy` / `daily-sell` in `items.yml`. The reset time and time zone are configurable.
- New `/lodestock limits` command, and the market window shows what is left.
- Every item automatically gets a permission, `lodestock.ore.<item>`, that everyone has by default (and `lodestock.ore.*`). Deny it to lock an item for a group or player. Locked items are greyed out and can't be traded.
- New permission `lodestock.limit.bypass` (operators by default).

- Open market windows now update by themselves when a price, stock or daily limit changes (by another player's trade, drift, regeneration or an admin command), instead of only when you click.
- Windows redraw only the icons that changed, and do nothing at all when nothing changed. Trading is smoother and cheaper on busy servers.
- New `gui.refresh-ticks` setting (default 20, 0 = only update on click).

### Storage
- The database file is upgraded automatically (schema version 3). Existing data is kept.

### Upgrading from 0.2.0-beta.1
Your prices, stock and history are kept: the database is upgraded automatically the first time the new version starts. Make a copy of `plugins/Lodestock/` first if you want a backup.

Lodestock never overwrites your existing config files, so the new settings are **not** added to them. Everything has a safe default and works without any changes, but to see and change the new settings, add these lines to your `config.yml`:

```yaml
gui:
  bulk-amount: 16
  # How often (in ticks, 20 = one second) open windows check for changes. 0 = only update on click.
  refresh-ticks: 20

recovery:
  interval-minutes: 10   # minutes between recovery steps
  catch-up-hours: 24     # also apply offline time, up to this many hours (0 = off)

drift:
  enabled: true
  percent: 2.0           # each step, the price closes this percent of its gap to the base price

regen:
  enabled: true
  percent: 2.0           # each step, stock moves this percent of max-stock toward start-stock

limits:
  daily-buy: 0           # most one player can buy of one item per day (0 = no limit)
  daily-sell: 0          # most one player can sell of one item per day (0 = no limit)
  reset-time: "00:00"    # when a new limit day starts
  timezone: "server"     # "server", or a name such as Asia/Manila or UTC
```

(`gui.bulk-amount` is already in your file. Only add `refresh-ticks` under it.)

Optional per-item settings for `items.yml`: `drift: false`, `regen: false`, `daily-buy: <number>`, `daily-sell: <number>`.

To see the new messages (limits, locked items, the new help line), copy the new lines from the bundled `lang/en.yml`, or delete your `lang/en.yml` to get a fresh one (back it up first if you edited it). Without that, the English defaults are used automatically for anything missing.

New permissions: `lodestock.ore.<item>` and `lodestock.ore.*` (everyone, by default) and `lodestock.limit.bypass` (operators). Behaviour that is on by default after updating: price drift and stock regeneration. Set `drift.enabled` and `regen.enabled` to `false` to switch them off.

## 0.2.0-beta.1 - 2026-10-06

First public beta. Lodestock is a rework of [OreMarket](https://github.com/OllieJW/Ore-Market) by OllieJW, rebuilt for modern Minecraft with the original author's permission.

### Market
- Market window: left-click to buy, right-click to sell, shift-click for bulk buy or sell-all of one item.
- Prices rise when items are bought and fall when they are sold. Tax is taken on sales.
- Limited stock per item: the market can run out, and stops buying from players when it is full.
- Only plain items can be sold. Renamed, enchanted or modified items are refused.
- Default market sells ingots and finished gems, so players smelt ores first. Storage blocks are optional.
- Items are configured by plain Minecraft item ID. No categories.

### Selling commands
- `/lodestock sellhand` sells the stack in your main hand.
- `/lodestock sellall` sells everything the market buys in your hotbar and main inventory, with a confirmation step and a cooldown.
- Separate permissions for GUI selling, `sellhand` and `sellall`.

### Configurable window
- New `gui.yml`: title, rows, item slots and ranges, fill item, previous/next/close buttons.
- Items can be pinned to an exact slot and page from `items.yml`.
- Config problems are shown to admins when they run `/lodestock reload`.

### Admin tools
- `setprice`, `setstock`, `reset`, `crash`, `surge`, `stats` and `reload`, each with its own permission.
- `/lodestock history <player>` shows a player's trades.

### Storage
- Prices, stock, trade history and the admin log are saved to a SQLite database (`lodestock.db`) as they change, in the background. A server crash loses at most the last few milliseconds.

### Language and messages
- MiniMessage language file (`lang/en.yml`) with a configurable `prefix`.

### Compatibility
- One jar for Paper and Purpur 1.21.11, 26.1, 26.2 and 26.3.
- Requires Vault and an economy plugin (tested with EssentialsX).

### Known limits
- Single server only. No proxy or network support.
- Requires Paper or a Paper fork. It will not work on plain Spigot.
- No PlaceholderAPI placeholders.
- Trade results are sent as chat messages.
