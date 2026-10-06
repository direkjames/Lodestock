package io.github.direkjames.lodestock.api;

/** How far back a leaderboard looks. */
public enum LeaderboardPeriod {
    /** The last 24 hours. */
    DAY,
    /** The last 7 days. */
    WEEK,
    /** The last 30 days. Limited by {@code history.keep-days} in config.yml (30 by default). */
    MONTH,
    /** Everything the server has ever recorded. */
    ALL
}
