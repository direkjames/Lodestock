package io.github.direkjames.lodestock.core.market;

public record MarketSettings(double taxPercent, double multiplier, double priceFloor) {
    public MarketSettings {
        if (taxPercent < 0 || taxPercent >= 100) throw new IllegalArgumentException("tax must be from 0 to below 100");
        if (multiplier < 0) throw new IllegalArgumentException("multiplier can't be negative");
        if (priceFloor <= 0) throw new IllegalArgumentException("price floor must be above 0");
    }

    /**
     * True if a player can make free money by looping a single item: buying then selling it,
     * or selling then buying it back. Tax that is too low for the multiplier causes it.
     * (A quick check on the settings. {@code EconomyAudit} also tests real bulk trades.)
     */
    public boolean hasBuySellLoop() {
        double keep = 1 - taxPercent / 100.0;
        boolean buyFirst = keep * (1 + keep * multiplier) > 1.0;
        boolean sellFirst = keep * multiplier > taxPercent / 100.0;
        return buyFirst || sellFirst;
    }
}