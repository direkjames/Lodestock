package io.github.direkjames.lodestock.core.market;

import io.github.direkjames.lodestock.core.Pricing;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

/**
 * Market logic with no Minecraft code. The platform checks the player's money and
 * inventory, then calls recordBuy / recordSell once the trade really happened.
 */
public final class Market {
    private final MarketSettings settings;
    private final RecoverySettings recovery;
    private final MarketStorage storage;
    private final Map<String, MarketItem> items = new LinkedHashMap<>();
    private final Map<String, ItemState> states = new HashMap<>();

    public Market(MarketSettings settings, Collection<MarketItem> itemList, MarketStorage storage) {
        this(settings, RecoverySettings.OFF, itemList, storage);
    }

    public Market(MarketSettings settings, RecoverySettings recovery,
                  Collection<MarketItem> itemList, MarketStorage storage) {
        this.settings = settings;
        this.recovery = recovery;
        this.storage = storage;
        for (MarketItem item : itemList) {
            items.put(item.id(), item);
            states.put(item.id(), storage.load(item.id())
                    .orElse(new ItemState(item.basePrice(), item.startStock())));
        }
    }

    public synchronized Optional<MarketItem> item(String id) { return Optional.ofNullable(items.get(id)); }
    public synchronized Optional<ItemState> state(String id) { return Optional.ofNullable(states.get(id)); }
    public synchronized Collection<MarketItem> items() { return List.copyOf(items.values()); }

    /** What the player pays to buy one. No tax is taken on buying. */
    public synchronized Quote quoteBuy(String id) {
        MarketItem item = items.get(id);
        if (item == null) return Quote.fail(Quote.Status.UNKNOWN_ITEM);
        ItemState s = states.get(id);
        if (!item.allowBuy()) return Quote.fail(Quote.Status.BUY_DISABLED);
        if (s.stock() <= 0) return Quote.fail(Quote.Status.OUT_OF_STOCK);
        if (s.price() <= 0) return Quote.fail(Quote.Status.PRICE_TOO_LOW);
        return new Quote(Quote.Status.OK, s.price());
    }

    /** What the player receives for selling one, after tax. */
    public synchronized Quote quoteSell(String id) {
        MarketItem item = items.get(id);
        if (item == null) return Quote.fail(Quote.Status.UNKNOWN_ITEM);
        ItemState s = states.get(id);
        if (!item.allowSell()) return Quote.fail(Quote.Status.SELL_DISABLED);
        if (s.stock() >= item.maxStock()) return Quote.fail(Quote.Status.STOCK_FULL);
        if (s.price() <= 0) return Quote.fail(Quote.Status.PRICE_TOO_LOW);
        return new Quote(Quote.Status.OK, afterTax(s.price()));
    }

    /**
     * Cost of buying up to {@code amount}, with the price rising after each item.
     * Stops early if the stock runs out. Changes nothing.
     */
    public synchronized BulkQuote previewBuy(String id, int amount) {
        requirePositive(amount);
        Quote first = quoteBuy(id);
        if (!first.ok()) return BulkQuote.fail(first.status());
        ItemState s = states.get(id);
        double price = s.price();
        int stock = s.stock();
        double total = 0;
        int count = 0;
        while (count < amount && stock > 0 && price > 0) {
            total += price;
            price = Pricing.nextPrice(price, afterTax(price), settings.multiplier(), true, settings.priceFloor());
            stock--;
            count++;
        }
        return new BulkQuote(Quote.Status.OK, count, round2(total));
    }

    /**
     * Payout for selling up to {@code amount}, with the price falling after each item.
     * Stops early if the market's stock is full. Changes nothing.
     */
    public synchronized BulkQuote previewSell(String id, int amount) {
        requirePositive(amount);
        Quote first = quoteSell(id);
        if (!first.ok()) return BulkQuote.fail(first.status());
        MarketItem item = items.get(id);
        ItemState s = states.get(id);
        double price = s.price();
        int stock = s.stock();
        double total = 0;
        int count = 0;
        while (count < amount && stock < item.maxStock() && price > 0) {
            total += afterTax(price);
            price = Pricing.nextPrice(price, afterTax(price), settings.multiplier(), false, settings.priceFloor());
            stock++;
            count++;
        }
        return new BulkQuote(Quote.Status.OK, count, round2(total));
    }

    public synchronized void recordBuy(String id) { recordBuy(id, 1); }
    public synchronized void recordSell(String id) { recordSell(id, 1); }

    public synchronized void recordBuy(String id, int amount) {
        requirePositive(amount);
        ItemState s = require(id);
        double price = s.price();
        int stock = s.stock();
        for (int i = 0; i < amount && stock > 0; i++) {
            price = Pricing.nextPrice(price, afterTax(price), settings.multiplier(), true, settings.priceFloor());
            stock--;
        }
        update(id, new ItemState(price, stock));
    }

    public synchronized void recordSell(String id, int amount) {
        requirePositive(amount);
        ItemState s = require(id);
        double price = s.price();
        int stock = s.stock();
        for (int i = 0; i < amount; i++) {
            price = Pricing.nextPrice(price, afterTax(price), settings.multiplier(), false, settings.priceFloor());
            stock++;
        }
        update(id, new ItemState(price, stock));
    }

    public synchronized MarketSettings settings() { return settings; }
    public synchronized RecoverySettings recovery() { return recovery; }

    /** Admin: sets the current price. Must be at least the price floor. */
    public synchronized void setPrice(String id, double price) {
        ItemState s = require(id);
        if (price < settings.priceFloor()) {
            throw new IllegalArgumentException("price must be at least " + settings.priceFloor());
        }
        update(id, new ItemState(price, s.stock(), true, s.stockHeld())); // drift leaves it alone until the next trade
    }

    /** Admin: sets the current stock, from 0 up to the item's max stock. */
    public synchronized void setStock(String id, int stock) {
        ItemState s = require(id);
        MarketItem item = items.get(id);
        if (stock < 0 || stock > item.maxStock()) {
            throw new IllegalArgumentException("stock must be from 0 to " + item.maxStock());
        }
        update(id, new ItemState(s.price(), stock, s.priceHeld(), true)); // regeneration leaves it alone until the next trade
    }

    /** Admin: back to the base price and starting stock. */
    public synchronized void reset(String id) {
        require(id);
        MarketItem item = items.get(id);
        update(id, new ItemState(item.basePrice(), item.startStock()));
    }

    public synchronized void resetAll() {
        for (MarketItem item : items.values()) {
            states.put(item.id(), new ItemState(item.basePrice(), item.startStock()));
        }
        storage.saveAll(new HashMap<>(states));
    }

    /**
     * Admin: changes prices by a percentage (-20 cuts 20%, +50 raises 50%).
     * {@code id} null means every item. Prices never go below the floor.
     * @return how many items were changed
     */
    public synchronized int adjustPrices(String id, double percent) {
        if (percent <= -100) throw new IllegalArgumentException("percent must be above -100");
        List<String> targets = new ArrayList<>();
        if (id == null) targets.addAll(states.keySet());
        else {
            require(id);
            targets.add(id);
        }
        double factor = 1 + percent / 100.0;
        for (String target : targets) {
            ItemState s = states.get(target);
            // A crash or surge is an event that fades, so the price is free to drift again.
            states.put(target, new ItemState(Math.max(settings.priceFloor(), s.price() * factor),
                    s.stock(), false, s.stockHeld()));
        }
        storage.saveAll(new HashMap<>(states));
        return targets.size();
    }

    /**
     * Lets prices drift toward their base price and stock regenerate toward the starting stock,
     * as if {@code steps} recovery intervals had passed. Items with an admin-set (held) value, or
     * with drift / regen turned off, are left alone. Saves only what changed.
     * @return how many items changed
     */
    public synchronized int applyRecovery(int steps) {
        if (steps < 1 || !recovery.active()) return 0;
        Map<String, ItemState> changed = new HashMap<>();
        for (MarketItem item : items.values()) {
            ItemState s = states.get(item.id());
            double price = s.price();
            int stock = s.stock();
            if (recovery.driftEnabled() && item.drift() && !s.priceHeld()) {
                price = Recovery.driftPrice(price, item.basePrice(), recovery.driftPercent(), steps, settings.priceFloor());
            }
            if (recovery.regenEnabled() && item.regen() && !s.stockHeld()) {
                stock = Recovery.regenStock(stock, item.startStock(), item.maxStock(), recovery.regenPercent(), steps);
            }
            if (price != s.price() || stock != s.stock()) {
                ItemState next = new ItemState(price, stock, s.priceHeld(), s.stockHeld());
                states.put(item.id(), next);
                changed.put(item.id(), next);
            }
        }
        if (!changed.isEmpty()) storage.saveAll(changed);
        return changed.size();
    }

    /** Writes everything to storage. */
    public synchronized void flush() { storage.saveAll(new HashMap<>(states)); }

    private double afterTax(double price) {
        return Pricing.afterTax(price, settings.taxPercent());
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static void requirePositive(int amount) {
        if (amount < 1) throw new IllegalArgumentException("amount must be at least 1");
    }

    private ItemState require(String id) {
        ItemState s = states.get(id);
        if (s == null) throw new IllegalArgumentException("unknown item: " + id);
        return s;
    }

    private void update(String id, ItemState next) {
        states.put(id, next);
        storage.save(id, next);
    }
}