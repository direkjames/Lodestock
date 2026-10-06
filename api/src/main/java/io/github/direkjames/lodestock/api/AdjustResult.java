package io.github.direkjames.lodestock.api;

/** What happened to a change requested through {@link LodestockApi}. */
public enum AdjustResult {
    /** The change was made. */
    DONE,
    /** A listener cancelled the {@code LodestockMarketAdjustEvent}, so nothing changed. */
    CANCELLED
}
