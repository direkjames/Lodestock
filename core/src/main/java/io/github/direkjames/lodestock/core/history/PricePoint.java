package io.github.direkjames.lodestock.core.history;

/** One saved moment of an item's price and stock. {@code time} is epoch milliseconds. */
public record PricePoint(long time, double price, int stock) {}
