package io.github.direkjames.lodestock.core.market;

/** One thing the market trades. Settings only; live price and stock are in ItemState. */
public record MarketItem(String id, String category, double basePrice,
                         int startStock, int maxStock,
                         boolean allowBuy, boolean allowSell) {
    public MarketItem {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("item id is empty");
        if (basePrice <= 0) throw new IllegalArgumentException(id + ": base-price must be above 0");
        if (startStock < 0) throw new IllegalArgumentException(id + ": start-stock can't be negative");
        if (maxStock < startStock) throw new IllegalArgumentException(id + ": max-stock must be at least start-stock");
    }
}