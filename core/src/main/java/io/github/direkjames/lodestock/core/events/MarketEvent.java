package io.github.direkjames.lodestock.core.events;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * A scheduled crash or surge.
 *
 * @param items   item ids it affects; empty means every item
 * @param times   times of day it happens
 * @param days    days of the week it happens; empty means every day
 * @param chance  0 to 100: how likely it is to really happen each time it is due
 * @param warnMinutes minutes before it starts that players are warned (0 = no warning)
 */
public record MarketEvent(String id, String name, Type type, double percent, List<String> items,
                          List<LocalTime> times, Set<DayOfWeek> days, double chance, int warnMinutes) {

    public enum Type { CRASH, SURGE }

    public MarketEvent {
        items = List.copyOf(items);
        times = List.copyOf(times);
        days = Set.copyOf(days);
    }

    public boolean allItems() {
        return items.isEmpty();
    }

    /** The percent as the market takes it: negative for a crash. */
    public double signedPercent() {
        return type == Type.CRASH ? -percent : percent;
    }

    /** True if the event has a time of day, so it runs by itself. Events with none can only be run by hand. */
    public boolean scheduled() {
        return !times.isEmpty();
    }
}
