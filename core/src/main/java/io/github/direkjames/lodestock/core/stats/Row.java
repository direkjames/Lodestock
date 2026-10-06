package io.github.direkjames.lodestock.core.stats;

/**
 * One line of a leaderboard. {@code value} is money, or a trade count for the active board.
 * For the biggest-trade board the other fields describe that trade ({@code action} is BUY or SELL);
 * for the other boards they are null or 0.
 */
public record Row(String player, double value, String action, String item, int amount, long time) {
    public static Row simple(String player, double value) {
        return new Row(player, value, null, null, 0, 0);
    }
}
