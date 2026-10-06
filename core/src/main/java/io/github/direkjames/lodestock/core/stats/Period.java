package io.github.direkjames.lodestock.core.stats;

import java.util.Locale;
import java.util.Optional;

/** How far back a leaderboard looks. {@code ALL} uses the lifetime stats, which are never deleted. */
public enum Period {
    DAY("24h", 24),
    WEEK("7d", 24 * 7),
    MONTH("30d", 24 * 30),
    ALL("all", 0);

    private final String key;
    private final int hours;

    Period(String key, int hours) {
        this.key = key;
        this.hours = hours;
    }

    public String key() {
        return key;
    }

    /** Hours covered, or 0 for ALL. */
    public int hours() {
        return hours;
    }

    public static Optional<Period> parse(String text) {
        if (text == null) return Optional.empty();
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        for (Period period : values()) {
            if (period.key.equals(wanted)) return Optional.of(period);
        }
        return Optional.empty();
    }
}
