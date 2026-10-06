package io.github.direkjames.lodestock.api;

/** Whether a trade can happen right now. */
public enum QuoteStatus {
    OK,
    UNKNOWN_ITEM,
    /** The item is set to {@code allow-buy: false}. */
    BUY_DISABLED,
    /** The item is set to {@code allow-sell: false}. */
    SELL_DISABLED,
    /** The market has none left to sell. */
    OUT_OF_STOCK,
    /** The market is full and won't buy more. */
    STOCK_FULL,
    /** The price is at or below zero, so no trade is possible. */
    PRICE_TOO_LOW
}
