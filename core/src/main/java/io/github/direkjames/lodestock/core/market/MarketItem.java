package io.github.direkjames.lodestock.core.market;

/**
 * One thing the market trades. Settings only; live price and stock are in ItemState.
 * {@code drift} / {@code regen} let one item opt out of recovery. {@code dailyBuy} / {@code dailySell}
 * override the default daily limits per player: -1 uses the default, 0 means unlimited.
 */
public record MarketItem(String id, double basePrice, int startStock, int maxStock,
                         boolean allowBuy, boolean allowSell, boolean drift, boolean regen,
                         int dailyBuy, int dailySell) {
    public MarketItem {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("item id is empty");
        if (basePrice <= 0) throw new IllegalArgumentException(id + ": base-price must be above 0");
        if (startStock < 0) throw new IllegalArgumentException(id + ": start-stock can't be negative");
        if (maxStock < startStock) throw new IllegalArgumentException(id + ": max-stock must be at least start-stock");
        if (dailyBuy < -1) throw new IllegalArgumentException(id + ": daily-buy can't be negative");
        if (dailySell < -1) throw new IllegalArgumentException(id + ": daily-sell can't be negative");
    }

    public MarketItem(String id, double basePrice, int startStock, int maxStock,
                      boolean allowBuy, boolean allowSell, boolean drift, boolean regen) {
        this(id, basePrice, startStock, maxStock, allowBuy, allowSell, drift, regen, -1, -1);
    }

    public MarketItem(String id, double basePrice, int startStock, int maxStock,
                      boolean allowBuy, boolean allowSell) {
        this(id, basePrice, startStock, maxStock, allowBuy, allowSell, true, true, -1, -1);
    }
}
