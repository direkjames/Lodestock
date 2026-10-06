# FAQ and troubleshooting

Short answers to the most common problems. If yours is not here, [open an issue](https://github.com/direkjames/Lodestock/issues/new/choose) or ask in [GitHub Discussions](https://github.com/direkjames/Lodestock/discussions).

## Contents

- [Installing and updating](#installing-and-updating)
- [The market does not work](#the-market-does-not-work)
- [Prices and stock look wrong](#prices-and-stock-look-wrong)
- [Backups, the database and resetting](#backups-the-database-and-resetting)
- [Discord, events and placeholders](#discord-events-and-placeholders)
- [What to include in a bug report](#what-to-include-in-a-bug-report)

## Installing and updating

**What do I need?** Paper or Purpur (see [supported versions](SUPPORT.md)), Vault, and an economy plugin that works with Vault, such as EssentialsX. Lodestock has no money of its own.

**How do I update?**
1. Stop the server.
2. Copy the `plugins/Lodestock/` folder somewhere safe (this is your backup).
3. Replace the old jar with the new one, and start the server.

The database upgrades itself when the server starts. It cannot be moved back to an older Lodestock version without your backup. Your `config.yml`, `items.yml`, `gui.yml` and `lang/en.yml` are never overwritten, so new settings use built-in defaults until you add them. Each release's "Upgrading" notes in the [changelog](../CHANGELOG.md) list the new settings you can copy in.

**The help text does not list new commands.** `lang/en.yml` is yours and is never overwritten. Copy the `help` and `help-admin` lines from the bundled file (open the jar, or rename your `lang/en.yml` and let Lodestock create a fresh one on the next start, then put your changes back).

## The market does not work

**"No Vault economy found" in the console.** Install Vault and an economy plugin. Without an economy, nobody can buy or sell.

**`/market` says I do not have permission.** Players need `lodestock.use` (everyone has it by default). Selling needs `lodestock.sell.gui`, `lodestock.sell.hand` or `lodestock.sell.all`, and buying needs `lodestock.buy`. Check your permissions plugin.

**One item is greyed out.** Its permission, `lodestock.ore.<item>` (for example `lodestock.ore.diamond`), is denied for that player. Everyone has them by default, so someone has set it to false.

**A player cannot buy or sell any more today.** That is a daily limit (`limits` in `config.yml`, or `daily-buy` and `daily-sell` on an item). `/lodestock limits` shows what is left and when it resets. Operators and players with `lodestock.limit.bypass` are never limited, so test with a normal account.

**I cannot sell my item.** Only the plain item can be sold. Renamed, enchanted or otherwise modified items are refused. Selling also stops when the market's stock of that item is full (`max-stock`), and buying stops when it is empty.

**The trade failed with "transaction failed".** The economy plugin refused the payment, for example because of a money cap. Nothing was taken.

**Config problems.** Run `/lodestock reload`. It lists every problem it found in `config.yml`, `items.yml`, `gui.yml` and the others. If the config is invalid, Lodestock keeps the previous working setup.

## Prices and stock look wrong

**Prices move back by themselves.** That is price drift (`drift` in `config.yml`, and per item). Prices drift toward `base-price`, and stock regenerates toward `start-stock`, even while the server is off. Turn it off in `config.yml` or per item with `drift: false` and `regen: false`.

**A price I set did not change after trading.** A price set with `/lodestock setprice` is held until the next trade on that item. Crashes and surges fade.

**Players are getting rich.** Run `/lodestock audit` (it finds settings that allow buy-and-sell loops and crafting loops), then `/lodestock economy 7d` to see how much money the market created. The [economy guide](ECONOMY.md) has ready-made price presets and the fix for each problem.

## Backups, the database and resetting

Everything (prices, stock, trade history, admin log, price history, lifetime stats, daily limits) is in one SQLite file: `plugins/Lodestock/lodestock.db`.

- While the server runs you will also see `lodestock.db-wal` and `lodestock.db-shm`. That is normal.
- **Backup:** stop the server, then copy `lodestock.db` (and the `-wal` and `-shm` files if they exist). Copying while it is running can give you a damaged copy.
- **Restore:** stop the server, put the files back, start it.
- **Reset the whole market:** `/lodestock reset all confirm`, or stop the server and delete the `lodestock.db` files. Config files are not touched.
- **Move to another server:** copy the whole `plugins/Lodestock/` folder (with the server stopped).
- **It is crash safe.** Every change is written as it happens, in the background. If the server crashes, at most the last few milliseconds are lost, and Lodestock catches up price drift for the time the server was off.
- Trade history older than `history.keep-days` (30 by default) is deleted at startup. Lifetime stats for the all-time leaderboards are never deleted.

## Discord, events and placeholders

**The Discord webhook does nothing.** Set `enabled: true` and a real webhook address in `discord.yml`, run `/lodestock reload`, then `/lodestock discord test`. The command tells you what is wrong (for example, a deleted webhook). Only `https://discord.com/api/webhooks/...` addresses are accepted. Big trades are only posted if they are at least `big-trades.min-total` in money.

**A scheduled event never runs.** Check that `enabled: true` is set at the top of `events.yml`, that times are in quotes (`"18:00"`), and that `timezone` is right. `/lodestock events` shows each event's next run time. An event that was due while the server was off is skipped, and an event with a `chance` below 100 only happens that often. `/lodestock events run <id>` runs one right now to test it.

**Placeholders show up as text.** Install PlaceholderAPI. Lodestock registers its placeholders by itself. See the [placeholder list](../README.md#placeholderapi). Leaderboard placeholders refresh every `leaderboards.cache-seconds` (30 by default).

## What to include in a bug report

- The Lodestock version (`/version Lodestock`), your server software and version (`/version`), the Vault version and your economy plugin.
- What you did, what you expected, and what happened.
- Any error from `logs/latest.log`, in full (paste it into the form; please do not send screenshots of text).
- The relevant part of your config files, if it is about settings. Remove your Discord webhook address first.
