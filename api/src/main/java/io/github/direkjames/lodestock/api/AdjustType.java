package io.github.direkjames.lodestock.api;

/** The kinds of change an admin (or another plugin) can make to the market. */
public enum AdjustType {
    /** One item's price is set to a number. */
    SET_PRICE,
    /** One item's stock is set to a number. */
    SET_STOCK,
    /** A crash or a surge: prices change by a percentage (negative for a crash). */
    PERCENT_CHANGE,
    /** One item goes back to its base price and starting stock. */
    RESET,
    /** Every item goes back to its base price and starting stock. */
    RESET_ALL
}
