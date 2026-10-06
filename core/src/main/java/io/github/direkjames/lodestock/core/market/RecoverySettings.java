package io.github.direkjames.lodestock.core.market;

/**
 * How the market heals itself over time. Every {@code intervalMinutes}, prices close
 * {@code driftPercent} of their gap to the base price, and stock moves {@code regenPercent}
 * of max-stock toward the starting stock.
 */
public record RecoverySettings(int intervalMinutes, int catchUpHours,
                               boolean driftEnabled, double driftPercent,
                               boolean regenEnabled, double regenPercent) {
    public static final RecoverySettings DEFAULT = new RecoverySettings(10, 24, true, 2.0, true, 2.0);
    public static final RecoverySettings OFF = new RecoverySettings(10, 0, false, 0.0, false, 0.0);

    public RecoverySettings {
        if (intervalMinutes < 1) throw new IllegalArgumentException("interval must be at least 1 minute");
        if (catchUpHours < 0) throw new IllegalArgumentException("catch-up hours can't be negative");
        if (driftPercent < 0 || driftPercent > 100) throw new IllegalArgumentException("drift percent must be from 0 to 100");
        if (regenPercent < 0 || regenPercent > 100) throw new IllegalArgumentException("regen percent must be from 0 to 100");
    }

    public boolean active() {
        return (driftEnabled && driftPercent > 0) || (regenEnabled && regenPercent > 0);
    }
}
