package io.github.direkjames.lodestock.paper.history;

/**
 * Price history settings. A sample of every changed item is saved every {@code intervalMinutes}.
 * Samples older than {@code keepDays} are deleted (0 keeps everything).
 */
public record HistorySettings(boolean enabled, int intervalMinutes, int keepDays) {
    public static final HistorySettings DEFAULT = new HistorySettings(true, 10, 14);
}
