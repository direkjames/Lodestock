package io.github.direkjames.lodestock.api;

/**
 * One line of a leaderboard.
 *
 * @param rank   1 for the top player
 * @param player the player's last known name
 * @param value  money, or a number of trades for {@link LeaderboardType#ACTIVE}
 */
public record LeaderboardEntry(int rank, String player, double value) {}
