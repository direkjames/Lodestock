package io.github.direkjames.lodestock.core.market;

/** The answer to "can this trade happen, and for how much?" */
public record Quote(Status status, double price) {
    public enum Status { OK, UNKNOWN_ITEM, BUY_DISABLED, SELL_DISABLED, OUT_OF_STOCK, STOCK_FULL, PRICE_TOO_LOW }

    public boolean ok() { return status == Status.OK; }
    static Quote fail(Status status) { return new Quote(status, 0); }
}