package io.github.direkjames.lodestock.core.market;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Market logic with no Minecraft code. The platform checks the player's money and
 * inventory, then calls recordBuy / recordSell once the trade really happened.
 */
public final class Market {
    private final MarketSettings settings;
    private final MarketStorage storage;
    private final Map<String, MarketItem> items = new LinkedHashMap<>();
    private final Map<String, ItemState> states = new HashMap<>();

    public Market(MarketSettings settings, Collection<MarketItem> itemList, MarketStorage storage) {
        this.settings = settings;
        this.storage = storage;
        for (MarketItem item : itemList) {
            items.put(item.id(), item);
            states.put(item.id(), storage.load(item.id())
                    .orElse(new ItemState(item.basePrice(), item.startStock())));
        }
    }

    public synchronized Optional<MarketItem> item(String id) { return Optional.ofNullable(items.get(id)); }
    public synchronized Optional<ItemState> state(String id) { return Optional.ofNullable(states.get(id)); }
    public synchronized Collection<MarketItem> items() { return java.util.List.copyOf(items.values()); }

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
        return new Quote(Quote.Status.OK, Pricing_afterTax(s.price()));
    }

    public synchronized void recordBuy(String id) {
        ItemState s = require(id);
        double next = io.github.direkjames.lodestock.core.Pricing.nextPrice(
                s.price(), Pricing_afterTax(s.price()), settings.multiplier(), true, settings.priceFloor());
        update(id, new ItemState(next, Math.max(0, s.stock() - 1)));
    }

    public synchronized void recordSell(String id) {
        ItemState s = require(id);
        double next = io.github.direkjames.lodestock.core.Pricing.nextPrice(
                s.price(), Pricing_afterTax(s.price()), settings.multiplier(), false, settings.priceFloor());
        update(id, new ItemState(next, s.stock() + 1));
    }

    /** Writes everything to storage. */
    public synchronized void flush() { storage.saveAll(new HashMap<>(states)); }

    private double Pricing_afterTax(double price) {
        return io.github.direkjames.lodestock.core.Pricing.afterTax(price, settings.taxPercent());
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