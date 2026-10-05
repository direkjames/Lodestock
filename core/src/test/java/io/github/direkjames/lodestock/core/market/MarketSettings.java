package io.github.direkjames.lodestock.core.market;

public record MarketSettings(double taxPercent, double multiplier, double priceFloor) {
    public MarketSettings {
        if (taxPercent < 0 || taxPercent >= 100) throw new IllegalArgumentException("tax must be from 0 to below 100");
        if (multiplier < 0) throw new IllegalArgumentException("multiplier can't be negative");
        if (priceFloor <= 0) throw new IllegalArgumentException("price floor must be above 0");
    }

    /** True if buying then selling the same item makes free money (tax too low for the multiplier). */
    public boolean hasBuySellLoop() {
        double t = taxPercent / 100.0;
        return (1 - t) * (1 + (1 - t) * multiplier) > 1.0;
    }
}