package io.github.direkjames.lodestock.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The Lodestock API. Get it with {@link #find()}, and add {@code depend: [Lodestock]} (or {@code softdepend})
 * to your plugin.yml so Lodestock loads first.
 *
 * <p>Call everything on the server's main thread unless a method says otherwise. Methods that return a
 * {@link CompletableFuture} do their database work in the background and complete on another thread, so
 * move back to the main thread (for example with the Bukkit scheduler) before you touch players or worlds.
 *
 * <p>Item IDs are Minecraft IDs without the namespace, in lower case: {@code diamond}, {@code iron_ingot}.
 * {@code minecraft:diamond} is accepted too.
 *
 * <p>To react to trades and changes, listen to the events in {@code io.github.direkjames.lodestock.api.event}.
 */
public interface LodestockApi {
    /**
     * The version of this API. It goes up by one when something is added, and the major plugin version
     * changes if something is removed or changed, so you can check it if you rely on newer methods.
     */
    int API_VERSION = 1;

    /** The API, or empty if Lodestock is not installed or not enabled. */
    static Optional<LodestockApi> find() {
        RegisteredServiceProvider<LodestockApi> provider = Bukkit.getServicesManager().getRegistration(LodestockApi.class);
        return provider == null ? Optional.empty() : Optional.of(provider.getProvider());
    }

    /** The version of this API as running on the server, see {@link #API_VERSION}. */
    int apiVersion();

    // ---------- reading the market ----------

    /** The IDs of every item in the market. */
    Collection<String> itemIds();

    /** The current numbers of one item, or empty if the market does not trade it. */
    Optional<ItemInfo> item(String id);

    /** The tax taken off the price when a player sells, in percent (the tax-percent setting). */
    double taxPercent();

    /** What the next single item costs a player, or empty if it can't be bought right now. */
    OptionalDouble buyPrice(String id);

    /** What the next single item pays a player after tax, or empty if it can't be sold right now. */
    OptionalDouble sellPrice(String id);

    /**
     * What buying {@code amount} items would cost, with the price rising after each item. Nothing is bought.
     *
     * @throws IllegalArgumentException if amount is below 1
     */
    TradeQuote previewBuy(String id, int amount);

    /**
     * What selling {@code amount} items would pay, after tax, with the price falling after each item.
     * Nothing is sold.
     *
     * @throws IllegalArgumentException if amount is below 1
     */
    TradeQuote previewSell(String id, int amount);

    // ---------- history and statistics ----------

    /**
     * Saved price samples of an item for the last {@code span}, oldest first. The list is empty if price history
     * is turned off or has no data yet. Samples older than {@code price-history.keep-days} are deleted.
     * Completes on another thread.
     */
    CompletableFuture<List<PricePoint>> priceHistory(String id, Duration span);

    /** Everything one player has traded, all time. All zeros if they never traded. */
    PlayerStats playerStats(UUID player);

    /** Everything one player has traded of one item, all time. */
    PlayerStats playerStats(UUID player, String item);

    /**
     * A leaderboard, highest first, with at most {@code limit} entries (1 to 50).
     * Completes on another thread.
     *
     * @param item an item ID to rank one item only, or null for all items
     */
    CompletableFuture<List<LeaderboardEntry>> leaderboard(LeaderboardType type, LeaderboardPeriod period,
                                                          String item, int limit);

    // ---------- changing the market ----------

    /**
     * Sets an item's current price. It stays until the next trade on that item. Fires a cancellable
     * {@code LodestockMarketAdjustEvent} and is saved in the admin log under {@code source}.
     *
     * @param source who is doing it, for the log and the event, such as your plugin's name
     * @throws IllegalArgumentException if the item is unknown or the price is below the price floor
     */
    AdjustResult setPrice(String id, double price, String source);

    /**
     * Sets an item's current stock, from 0 to its max stock. It stays until the next trade on that item.
     *
     * @throws IllegalArgumentException if the item is unknown or the stock is out of range
     */
    AdjustResult setStock(String id, int stock, String source);

    /**
     * Changes prices by a percentage: negative is a crash, positive is a surge. The change fades on its own.
     * Prices never go below the price floor. This does not touch stock.
     *
     * @param id      an item ID, or null for every item
     * @param percent more than -100, for example -20 cuts prices by 20 percent and 50 raises them by 50 percent
     * @throws IllegalArgumentException if the item is unknown or the percent is -100 or lower
     */
    AdjustResult adjustPrices(String id, double percent, String source);

    /**
     * Puts one item back to its base price and starting stock.
     *
     * @throws IllegalArgumentException if the item is unknown
     */
    AdjustResult reset(String id, String source);

    /** Puts every item back to its base price and starting stock. */
    AdjustResult resetAll(String source);
}
