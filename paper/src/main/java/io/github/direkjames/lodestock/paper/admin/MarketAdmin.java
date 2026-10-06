package io.github.direkjames.lodestock.paper.admin;

import io.github.direkjames.lodestock.api.AdjustType;
import io.github.direkjames.lodestock.api.event.LodestockMarketAdjustEvent;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import net.kyori.adventure.text.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Every hand-made change to the market goes through here: the admin commands, the public API and, later,
 * scheduled events. Each change is checked, offered to other plugins as a cancellable event, applied,
 * written to the admin log and shown to players who have the window open. Main thread only.
 */
public final class MarketAdmin {
    /** {@code done} is false if a listener cancelled the change. */
    public record Outcome(boolean done, Component cancelMessage) {
        static final Outcome DONE = new Outcome(true, null);
    }

    private static final DecimalFormat PERCENT = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final LodestockPlugin plugin;

    public MarketAdmin(LodestockPlugin plugin) {
        this.plugin = plugin;
    }

    private static void mainThread() {
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Lodestock market changes must be made on the main server thread");
        }
    }

    /** @throws IllegalArgumentException if the item is unknown or the price is below the price floor */
    public Outcome setPrice(String source, String id, double price) {
        mainThread();
        Market market = plugin.market();
        requireItem(market, id);
        if (!Double.isFinite(price) || price < market.settings().priceFloor()) {
            throw new IllegalArgumentException("price must be at least " + market.settings().priceFloor());
        }
        Outcome outcome = ask(AdjustType.SET_PRICE, id, price, source);
        if (!outcome.done()) return outcome;
        market.setPrice(id, price);
        applied(source, "SETPRICE", id + " " + price);
        return outcome;
    }

    /** @throws IllegalArgumentException if the item is unknown or the stock is out of range */
    public Outcome setStock(String source, String id, int stock) {
        mainThread();
        Market market = plugin.market();
        int max = requireItem(market, id).maxStock();
        if (stock < 0 || stock > max) {
            throw new IllegalArgumentException("stock must be from 0 to " + max);
        }
        Outcome outcome = ask(AdjustType.SET_STOCK, id, stock, source);
        if (!outcome.done()) return outcome;
        market.setStock(id, stock);
        applied(source, "SETSTOCK", id + " " + stock);
        return outcome;
    }

    /** @throws IllegalArgumentException if the item is unknown */
    public Outcome reset(String source, String id) {
        mainThread();
        Market market = plugin.market();
        requireItem(market, id);
        Outcome outcome = ask(AdjustType.RESET, id, Double.NaN, source);
        if (!outcome.done()) return outcome;
        market.reset(id);
        applied(source, "RESET", id);
        return outcome;
    }

    public Outcome resetAll(String source) {
        mainThread();
        Outcome outcome = ask(AdjustType.RESET_ALL, null, Double.NaN, source);
        if (!outcome.done()) return outcome;
        plugin.market().resetAll();
        applied(source, "RESET", "all items");
        return outcome;
    }

    /**
     * A crash (negative percent) or a surge (positive).
     * @param id an item ID, or null for every item
     * @throws IllegalArgumentException if the item is unknown or the percent is -100 or lower
     */
    public Outcome adjust(String source, String id, double percent) {
        mainThread();
        Market market = plugin.market();
        if (id != null) requireItem(market, id);
        if (!Double.isFinite(percent) || percent <= -100) {
            throw new IllegalArgumentException("percent must be above -100");
        }
        Outcome outcome = ask(AdjustType.PERCENT_CHANGE, id, percent, source);
        if (!outcome.done()) return outcome;
        market.adjustPrices(id, percent);
        applied(source, percent < 0 ? "CRASH" : "SURGE",
                PERCENT.format(Math.abs(percent)) + "% " + (id == null ? "all items" : id));
        return outcome;
    }

    /**
     * Same as {@link #adjust} for a list of items, written to the log as one change. Items another plugin
     * cancels are left out; the result is done if at least one item changed.
     * @throws IllegalArgumentException if an item is unknown or the percent is -100 or lower
     */
    public Outcome adjustItems(String source, java.util.Collection<String> ids, double percent) {
        mainThread();
        Market market = plugin.market();
        for (String id : ids) requireItem(market, id);
        if (ids.isEmpty()) throw new IllegalArgumentException("no items given");
        if (!Double.isFinite(percent) || percent <= -100) {
            throw new IllegalArgumentException("percent must be above -100");
        }
        java.util.List<String> allowed = new java.util.ArrayList<>();
        Component message = null;
        for (String id : ids) {
            Outcome outcome = ask(AdjustType.PERCENT_CHANGE, id, percent, source);
            if (outcome.done()) allowed.add(id);
            else message = outcome.cancelMessage();
        }
        if (allowed.isEmpty()) return new Outcome(false, message);
        for (String id : allowed) market.adjustPrices(id, percent);
        applied(source, percent < 0 ? "CRASH" : "SURGE", PERCENT.format(Math.abs(percent)) + "% " + String.join(", ", allowed));
        return Outcome.DONE;
    }

    private static io.github.direkjames.lodestock.core.market.MarketItem requireItem(Market market, String id) {
        return market.item(id).orElseThrow(() -> new IllegalArgumentException("unknown item: " + id));
    }

    private Outcome ask(AdjustType type, String id, double value, String source) {
        LodestockMarketAdjustEvent event = new LodestockMarketAdjustEvent(type, id, value, source);
        plugin.getServer().getPluginManager().callEvent(event);
        return event.isCancelled() ? new Outcome(false, event.getCancelMessage()) : Outcome.DONE;
    }

    private void applied(String source, String action, String details) {
        plugin.tradeLog().admin(source, action, details);
        plugin.menus().refreshOpen();
        plugin.discord().adminAction(source, action, details);
    }
}
