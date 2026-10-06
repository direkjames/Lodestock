# Keeping your economy healthy

Lodestock pays players money for items. That money did not exist before, so a stock market plugin is a money faucet by design. With sensible settings that is fine. With careless settings, players can drain your economy in an afternoon, and nothing in the plugin can stop that for you.

This guide shows how to set Lodestock up safely, how to spot trouble early, and what to do when something goes wrong. You do not need to know anything about economics. If you only read one part, read **Quick start**.

> Lodestock gives you the tools. The prices, taxes and limits are your choice, and so is the result on your server. Run `/lodestock audit` after every change.

## Contents

1. [Quick start](#quick-start)
2. [Pick a preset](#pick-a-preset)
3. [Install a preset](#install-a-preset)
4. [How economies break, and the fix for each](#how-economies-break-and-the-fix-for-each)
5. [Watching your economy](#watching-your-economy)
6. [Something is going wrong](#something-is-going-wrong)
7. [Settings cheat sheet](#settings-cheat-sheet)

## Quick start

1. Pick the preset that fits your server (below).
2. Copy its `items.yml` and its `config.yml` lines.
3. Run `/lodestock reload`, then `/lodestock audit`. Fix anything it reports as a warning.
4. For the first week, run `/lodestock economy` once a day and watch the numbers.
5. Before you add other ways to earn money (jobs, quests, daily rewards), check they do not pay more for the same item than Lodestock sells it for.

## Pick a preset

Prices on their own mean nothing. What matters is how they compare with everything else on your server, such as what players earn from jobs and what ranks, kits and land cost. The presets are three sets that already work together. Every price, tax, limit and stock size in a preset was checked with `/lodestock audit`, and none of them has a buy-and-sell loop or a crafting loop.

| | Conservative | Balanced | Generous |
|---|---|---|---|
| For | Skyblock, OneBlock, prison, small economies | Survival, SMP, most servers | Rich economies with expensive ranks and land |
| Coal | 1.5 | 3 | 12 |
| Iron ingot | 4 | 8 | 32 |
| Gold ingot | 10 | 20 | 80 |
| Emerald | 20 | 40 | 160 |
| Diamond | 40 | 80 | 320 |
| Netherite scrap | 75 | 150 | 600 |
| Selling tax (`tax-percent`) | 15 | 10 | 7 |
| Price move per trade (`multiplier`) | 0.02 | 0.01 | 0.01 |
| Stock refill (`regen.percent`) | 1 | 2 | 3 |
| Diamonds one player can sell per day | 25 | 50 | 100 |
| Most one player can earn from diamonds per day | 1,000 | 4,000 | 32,000 |
| Rough most the whole market can pay out per day | about 21,000 | about 100,000 | about 714,000 |

The last row comes from `/lodestock audit`. It is a ceiling for the long run when players sell everything as fast as the market takes it, so real numbers are usually lower. Use it to compare presets, not as a forecast.

**Which one for which game mode?**

| Game mode | Start with | Why |
|---|---|---|
| Survival / SMP | Balanced | Players mine by hand, so supply is naturally limited. |
| Skyblock / OneBlock | Conservative | Items come from generators with no end. Keep the daily limits, and think about lowering them. |
| Factions | Balanced, and keep the daily limits | Raiding moves items between players, and a rich faction can dump a whole vault. The limits stop one player cashing in everything at once. |
| Anarchy | Conservative | Duplicated items are the real danger. Cheap prices and limits make a dupe worth less. Consider `allow-sell: false` on your most valuable items. |
| Prison | Conservative | Mines refill forever, just like a generator. |
| Rich / "OP economy" servers | Generous | Everything costs more, so everything pays more. Keep the limits. |

If your server is none of these, start with Balanced. You can change prices later without breaking anything.

## Install a preset

The files are in this repository under [`docs/presets/`](presets):

- [`items-conservative.yml`](presets/items-conservative.yml)
- [`items-balanced.yml`](presets/items-balanced.yml)
- [`items-generous.yml`](presets/items-generous.yml)

**1. Replace `items.yml`.** Open `plugins/Lodestock/items.yml` and replace everything in it with the preset file. Back up the old one first if you changed it.

**2. Update `config.yml`.** Change these lines (they already exist in your file, so edit them rather than adding new ones). Pick the block for your preset:

Conservative:

```yaml
tax-percent: 15.0
multiplier: 0.02
price-floor: 0.25
drift:
  enabled: true
  percent: 2.0
regen:
  enabled: true
  percent: 1.0
```

Balanced:

```yaml
tax-percent: 10.0
multiplier: 0.01
price-floor: 0.25
drift:
  enabled: true
  percent: 2.0
regen:
  enabled: true
  percent: 2.0
```

Generous:

```yaml
tax-percent: 7.0
multiplier: 0.01
price-floor: 0.5
drift:
  enabled: true
  percent: 3.0
regen:
  enabled: true
  percent: 3.0
```

**3. Reload and check.** Run `/lodestock reload`, then `/lodestock audit`. You should see "No warnings".

**4. Fresh start (optional).** The market remembers the prices and stock it had. After you change base prices, those saved prices drift toward the new base prices on their own, over hours. To jump straight to the new prices, run `/lodestock reset all confirm`.

Changing numbers in a preset is fine. Whenever you do, run the audit again, and keep these two rules, because they are what keeps crafting from making money:

- A block costs exactly 9 times its ingot (4 times for quartz and amethyst blocks).
- A raw ore costs the same as, or more than, the ingot it smelts into.

## How economies break, and the fix for each

### 1. Endless item sources

**The problem.** If players can get items for free forever (a OneBlock, a skyblock cobble generator, an iron farm, a prison mine, a dupe), then selling them to Lodestock turns time into money with no end. The market's stock refills by itself (`regen`), so it keeps buying.

**The fix.**
- Use **daily limits** so one player can only sell so much per day. The presets set a `daily-sell` for every item. If you build your own `items.yml`, add `limits.daily-sell` in `config.yml` or `daily-sell` on each item.
- Keep prices low for items that come from generators. A cobble generator should never be worth much money.
- Lower `regen.percent`. A lower number means the market takes fewer items per day.
- Turn off `allow-sell` for anything players can produce without effort, or for anything you cannot guard against dupes.

### 2. Buy-and-sell loops (tax and multiplier)

**The problem.** Every trade moves the price. If the move is large and the tax is small, a player can sell a big stack, buy the same stack back for less, and keep the difference. The plugin only costs them tax, so the tax has to be higher than the move.

You might think a single trade is the only thing to check. It is not: one item at a time can lose money while a stack of hundreds makes it. `/lodestock audit` tests real trades of many sizes, up to your max stock.

**The fix.** Keep `tax-percent` above the minimum for your `multiplier`. These numbers come from the same test the audit uses, with stock room up to 10,000 items. Add a few points of margin on top.

| `multiplier` | Lowest `tax-percent` with no loop |
|---|---|
| 0.005 | 3 |
| 0.01 | 5 |
| 0.02 | 12 |
| 0.03 | 16 |
| 0.05 | 24 |
| 0.08 | 28 |
| 0.1 | 35 |

If you are not sure, use `tax-percent: 10` with `multiplier: 0.01`. That is the default.

### 3. Crafting loops

**The problem.** Players can turn items into other items: 9 iron ingots into an iron block and back, raw iron into an ingot by smelting. If the prices do not match the recipes, there is free money. For example, if an ingot sells for 8 but a block sells for 90 after tax, a player buys 9 ingots, crafts a block and sells it.

**The fix.** Keep to the two rules in [Install a preset](#install-a-preset). The audit checks the common pairs (ingot and block, nugget and ingot, raw ore and ingot, raw ore and block, quartz, amethyst, glowstone, ancient debris) whenever both items are in your market.

Be careful with crash and surge. If you crash only one item of a pair, for example with `/lodestock crash 50 iron_ingot`, the pair is out of line until the price drifts back, and players can use that gap. Crash or surge the whole group together, or use `/lodestock crash <percent>` for everything. The audit reports gaps like this as "Right now".

### 4. Other ways to earn the same item

**The problem.** Lodestock does not exist on its own. If a jobs plugin pays 5 for breaking a diamond ore, a quest gives 500 for 10 diamonds and Lodestock pays 80 for a diamond, players will stack all three. If another shop *sells* an item cheaper than Lodestock *buys* it, players buy there and sell here.

**The fix.**
- Make a short list of every way to earn money on your server and compare them for your top five items.
- Make sure no shop (ChestShop, admin shops, shop GUIs, auction houses) sells an item for less than Lodestock's **sell** price. The sell price is the buy price minus tax.
- When you add a new money source, run `/lodestock economy` for a few days afterwards.

### 5. Prices that recover on their own

**The problem.** Drift pulls prices back to the base price and regen refills stock, so a dumped item becomes worth money again after a while. That is a good thing for players and a faucet for you.

**The fix.** This is why daily limits matter. The audit prints a rough figure for the most the whole market can pay out per day. If that number is larger than you are happy with, lower `regen.percent`, lower prices, or add daily limits.

### 6. Alt accounts

**The problem.** Daily limits are per player, so a player with five accounts has five limits.

**The fix.**
- Require something before players can sell, such as a playtime requirement or a rank. In your permissions plugin, set `lodestock.sell.gui`, `lodestock.sell.hand` and `lodestock.sell.all` to `false` for new players and give them back once the requirement is met.
- Lock the expensive items behind ranks. Every item has a permission, `lodestock.ore.<item>`. Deny it for new players and give it to older ones. See the README for details.
- Use a plugin that links accounts on the same IP, and consider whether you allow more than one account.

### 7. Admin mistakes

**The problem.** A price set far too high or too low is an instant exploit. `/lodestock setprice` holds the value until the next trade, so a typo stays in place.

**The fix.**
- Try changes on a test server first.
- Run `/lodestock audit` after every change.
- `/lodestock stats` shows the items whose prices moved the most, which is a quick way to spot a bad change. Put a price back with `/lodestock reset <item>`.
- Keep `price-floor` at about 5 to 10 percent of your cheapest item. A floor near zero lets an item crash to almost nothing, and someone buys the whole stock for pennies. The audit warns you when it drops below 2 percent.

### 8. Warnings before events

**The problem.** If an event is announced before a surge or crash, everybody buys or sells in advance and the event pays them for it.

**The fix.** Scheduled events (`events.yml`) are off by default. When you turn them on:

- Keep them small, 10 to 25 percent. Lodestock warns you on startup and `/lodestock reload` when an event is bigger than your `tax-percent`, because that is when acting on the warning pays.
- Keep daily limits on. They cap how much one player can buy cheap during a crash or stock up before a surge.
- Use a `chance` below 100 and a short `warn-minutes` (or 0) if you want a surprise. Lodestock decides whether an event happens when the warning goes out, so players are only warned about events that really happen.
- A crash followed by price recovery is a free profit window for anyone who buys during it. Choose items that have `drift` on and plenty of stock, and watch `/lodestock economy` after the first few events.

## Watching your economy

Lodestock has these tools for this:

| Command | What it tells you |
|---|---|
| `/lodestock audit` | Checks your settings for loops and risky values. It never changes anything. |
| `/lodestock economy [24h\|7d\|30d]` | How much money the market paid out, how much players spent, the difference, the biggest net earners and the items that paid out the most. |
| `/lodestock history <player>` | Every trade one player made. |
| `/lodestock top biggest [item] [period]` | The biggest single trades. A trade far above the rest is worth a look. |

Permissions: `lodestock.admin.audit` and `lodestock.admin.economy` (operators by default).

**How to read `/lodestock economy`**

- *Compared with the period before* shows whether money created is rising or falling, and *per active player* shows it per person, which is a fairer health number than the total.
- *Money created* is what the market paid out minus what players spent. It is money that did not exist before. A positive number is normal, because that is how players earn. What matters is the trend: compare it each day with how many players were online.
- If it keeps rising faster than your sinks (rank shops, land claims, repairs, taxes), prices across your server will drift up. That is inflation.
- Look at the **biggest net earners**. One player far ahead of everyone else usually means a farm, a dupe or an exploit. Open their `/lodestock history`.
- Look at the items that **paid out the most**. If the top one is something that should be hard to get, find out why.

Lodestock only sees its own trades. Money from jobs, quests and other plugins is not in these numbers.

**Sinks.** A selling tax removes money for good, since it is never paid to anyone. Other ways to remove money are rank and kit shops, land claims, repair and enchant costs, teleport fees and, if you have one, a server tax. A healthy server removes about as much as it creates.

## Something is going wrong

1. **Stop the bleeding.** Fast options: set `lodestock.sell.gui`, `lodestock.sell.hand` and `lodestock.sell.all` to `false` for your default group in your permissions plugin (this stops all selling), or set `allow-sell: false` on one item in `items.yml` and run `/lodestock reload`.
2. **Find out what happened.** Run `/lodestock economy 24h` and open the history of the top earners.
3. **Fix the cause.** Common causes are a loop (fix tax, multiplier or prices), an endless source (add limits, lower prices) or a dupe (disable selling that item, then deal with the player).
4. **Put prices back.** `/lodestock reset all confirm` returns every price and stock to the base values. Money players already earned is still in your economy plugin. If it is too much, restore it from your economy plugin's backups, or use your economy plugin's own tools to adjust balances.
5. **Check again.** Run `/lodestock audit` before you open trading again.

## Settings cheat sheet

| Setting | Safer | Riskier |
|---|---|---|
| `tax-percent` | 10 or more | 0 to 5 |
| `multiplier` | 0.01 to 0.02 | 0.05 or more |
| `price-floor` | 5 to 10 percent of your cheapest item | 0.01 |
| `limits.daily-sell` / item `daily-sell` | set, with money-sized caps | 0 (no limit) |
| `regen.percent` | 1 to 2 | 5 or more |
| Block, ingot and raw prices | Block = 9 x ingot, raw = ingot | Anything else |
| `allow-sell` on dupe-able items | `false` | `true` |
| Operators with `lodestock.limit.bypass` | Few | Many |
