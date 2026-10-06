package io.github.direkjames.lodestock.api;

/**
 * One saved price sample.
 *
 * @param time  when it was taken, in milliseconds since 1970 (like {@link System#currentTimeMillis()})
 * @param price the price at that time
 * @param stock the stock at that time
 */
public record PricePoint(long time, double price, int stock) {}
