# Changelog

All notable changes to Lodestock are listed here. Lodestock uses [semantic versioning](https://semver.org/).

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
