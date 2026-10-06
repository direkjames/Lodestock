package io.github.direkjames.lodestock.api;

/** The leaderboards, the same as {@code /lodestock top}. */
public enum LeaderboardType {
    /** Money earned from selling. */
    SELLERS,
    /** Money spent buying. */
    SPENDERS,
    /** Number of trades. The value is a count, not money. */
    ACTIVE,
    /** The single biggest trade. */
    BIGGEST,
    /** Money earned minus money spent. */
    NET
}
