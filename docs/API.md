# Lodestock API

Lodestock has a public API so other plugins can read the market, react to trades and change prices. It is a separate module (`lodestock-api`) with no code from the plugin itself, so you compile against it and Lodestock provides the real thing at runtime.

The API is in beta together with the plugin. `LodestockApi.API_VERSION` is `1`. Within one version, methods and events are only added, never removed or changed. A breaking change raises the version and is listed in the [changelog](../CHANGELOG.md).

## Add it to your project

Lodestock is built on JitPack. Use the tag of the release you want (for example `v0.4.0-beta.1`).

**Gradle (Kotlin)**
```kotlin
repositories {
    maven("https://jitpack.io")
}
dependencies {
    compileOnly("com.github.direkjames.Lodestock:lodestock-api:<tag>")
}
```

**Maven**
```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>
<dependency>
  <groupId>com.github.direkjames.Lodestock</groupId>
  <artifactId>lodestock-api</artifactId>
  <version>TAG</version>
  <scope>provided</scope>
</dependency>
```

> The exact JitPack coordinates are shown on the project's page at jitpack.io after the first build of a tag. If the line above does not resolve, copy the one from that page.

Always use `compileOnly` / `provided`. Do not shade the API into your jar.

Then tell the server to load Lodestock first, in your `plugin.yml`:
```yaml
depend: [Lodestock]        # or softdepend, if Lodestock is optional for you
```

## Get the API

```java
LodestockApi api = LodestockApi.find();   // null if Lodestock is not running
if (api == null) return;
```

If Lodestock is a soft dependency, keep every line that touches `LodestockApi` in a separate class that you only load when the plugin is present, so your plugin does not fail to load without it.

## Reading the market

Item ids are lowercase Minecraft names, for example `iron_ingot`. A `minecraft:` prefix and capital letters are accepted and cleaned up.

```java
api.itemIds();                         // all items in the market
api.item("diamond").ifPresent(info -> {
    info.price();                      // current market price
    info.stock();                      // current stock
    info.buyPrice();                   // OptionalDouble: empty if buying is not possible right now
    info.sellPrice();                  // price after tax, empty if selling is not possible right now
});

TradeQuote quote = api.previewSell("gold_ingot", 64);
if (quote.ok()) {
    double money = quote.total();      // what 64 would pay, with tax and the price moving as it sells
} else {
    QuoteStatus why = quote.status();  // OUT_OF_STOCK, STOCK_FULL, SELL_DISABLED, ...
}
```

A preview can be smaller than what you asked for (`quote.amount()`), for example when the stock only has room for part of it. Nothing is changed by a preview.

### Stats, history and leaderboards

```java
PlayerStats stats = api.playerStats(player.getUniqueId());              // lifetime numbers
PlayerStats iron  = api.playerStats(player.getUniqueId(), "iron_ingot");

api.priceHistory("diamond", Duration.ofDays(7)).thenAccept(points -> { /* PricePoint(time, price, stock) */ });

api.leaderboard(LeaderboardType.SELLERS, LeaderboardPeriod.WEEK, null, 10)
   .thenAccept(rows -> { /* LeaderboardEntry(rank, player, value) */ });
```

## Threads

- Everything that only reads the market (`item`, `buyPrice`, `previewBuy`, `playerStats`, ...) is quick and safe to call from the main thread.
- `priceHistory` and `leaderboard` return a `CompletableFuture`. The future may finish on another thread. Do not touch Bukkit objects in the callback without going back to the main thread (`Bukkit.getScheduler().runTask(...)`).
- Changing the market (`setPrice`, `setStock`, `adjustPrices`, `reset`, `resetAll`) and all events must happen on the main thread.

## Changing the market

Every change needs a `source`: a short name for who did it (usually your plugin's name). It is written to the admin log, shown in `/lodestock stats`, and given to the event, so server owners can see where a price change came from.

```java
api.setPrice("diamond", 120.0, "MyEventPlugin");
api.setStock("diamond", 500, "MyEventPlugin");
api.adjustPrices("iron_ingot", -25, "MyEventPlugin");   // 25% down; null for every item
api.reset("diamond", "MyEventPlugin");
```

These throw `IllegalArgumentException` for an unknown item or a value that is not allowed (a price below the price floor, stock above the maximum, a drop of 100% or more). Each returns `AdjustResult.DONE`, or `CANCELLED` if another plugin cancelled the `LodestockMarketAdjustEvent`.

Changes made through the API are handled exactly like the `/lodestock setprice`, `crash` and `surge` commands.

## Events

All events run on the main thread.

| Event | Cancellable | When |
|---|---|---|
| `LodestockPreTradeEvent` | yes | A player is about to buy or sell. Nothing has been taken or paid yet. |
| `LodestockTradeEvent` | no | A trade finished. Gives the new price and stock. |
| `LodestockMarketAdjustEvent` | yes | A price or stock is about to be changed by hand (command, API). Not fired for normal trading or recovery. |

```java
@EventHandler
public void onPreTrade(LodestockPreTradeEvent event) {
    if (event.getType() == TradeType.SELL && event.getAmount() > 1000) {
        event.setCancelled(true);
        event.setCancelMessage(Component.text("Too many at once!"));  // optional
    }
}

@EventHandler
public void onTrade(LodestockTradeEvent event) {
    getLogger().info(event.getPlayer().getName() + " " + event.getType() + " "
            + event.getAmount() + " " + event.getItemId() + " for " + event.getTotal());
}
```

Notes:
- With `/sellall`, a pre-trade event is fired for each item. Cancelled items are left out and the rest is sold. Trade events are fired for each item that sold.
- The event shows the total the player pays or receives, tax included.
- If you cancel without a message, the player sees the `trade-cancelled` message from `lang/en.yml`. For adjust events the sender sees `admin-cancelled`.
- `LodestockMarketAdjustEvent#getValue()` is `NaN` for resets.
