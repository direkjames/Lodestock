package io.github.direkjames.lodestock.core.market;

/** Result of a multi-item trade preview. {@code total} is for {@code count} items only. */
public record BulkQuote(Quote.Status status, int count, double total) {
    public boolean ok() { return status == Quote.Status.OK && count > 0; }

    static BulkQuote fail(Quote.Status status) { return new BulkQuote(status, 0, 0); }
}