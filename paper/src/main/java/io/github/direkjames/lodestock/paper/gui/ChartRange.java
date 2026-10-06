package io.github.direkjames.lodestock.paper.gui;

import java.util.Locale;
import java.util.Optional;

/** How far back a price chart looks. */
public enum ChartRange {
    DAY("24h", 86_400_000L, "chart-range-day"),
    WEEK("7d", 7 * 86_400_000L, "chart-range-week"),
    ALL("all", Long.MAX_VALUE, "chart-range-all");

    private final String code;
    private final long millis;
    private final String messageKey;

    ChartRange(String code, long millis, String messageKey) {
        this.code = code;
        this.millis = millis;
        this.messageKey = messageKey;
    }

    public String code() { return code; }
    public String messageKey() { return messageKey; }

    /** The earliest time (epoch millis) this range includes. 0 means everything that was saved. */
    public long since(long now) {
        return millis == Long.MAX_VALUE ? 0L : now - millis;
    }

    public static Optional<ChartRange> parse(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        for (ChartRange range : values()) if (range.code.equals(t)) return Optional.of(range);
        return Optional.empty();
    }
}
