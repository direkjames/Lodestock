package io.github.direkjames.lodestock.api;

/**
 * What one player has traded, for as long as the server has recorded it (these totals are never deleted).
 *
 * @param earned      money received from selling, after tax
 * @param spent       money paid for buying
 * @param trades      how many trades (a sell-all counts one trade per item type)
 * @param itemsSold   how many items the player sold
 * @param itemsBought how many items the player bought
 * @param bestTrade   the most money moved by one single trade
 */
public record PlayerStats(double earned, double spent, int trades, int itemsSold, int itemsBought, double bestTrade) {
    /** Money earned minus money spent. Negative if the player spent more than they earned. */
    public double net() {
        return earned - spent;
    }
}
