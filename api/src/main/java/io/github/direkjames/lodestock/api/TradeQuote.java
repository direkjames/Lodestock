package io.github.direkjames.lodestock.api;

/**
 * What a trade would cost or pay right now. Nothing is traded.
 *
 * @param status whether the trade is possible
 * @param amount how many items the market would really trade. It can be less than you asked for, when the
 *               stock runs out (buying) or fills up (selling). 0 if the trade is not possible.
 * @param total  the money for those items: what the player pays when buying, or receives after tax when selling.
 *               The price moves after every single item, so this is not {@code amount * price}.
 */
public record TradeQuote(QuoteStatus status, int amount, double total) {
    public boolean ok() {
        return status == QuoteStatus.OK && amount > 0;
    }
}
