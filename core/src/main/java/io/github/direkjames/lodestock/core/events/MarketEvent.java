package io.github.direkjames.lodestock.core.events;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A scheduled crash or surge.
 *
 * @param percent    how big it is; with {@code percentMax} above it, a random size between the two each time
 * @param items   item ids it affects (or the pool to pick from); empty means every item
 * @param pick    how many items to pick at random from {@code items} (or from every item); 0 = use them all
 * @param times   times of day it happens
 * @param days    days of the week it happens; empty means every day
 * @param chance  0 to 100: how likely it is to really happen each time it is due
 * @param warnMinutes minutes before it starts that players are warned (0 = no warning)
 */
public record MarketEvent(String id, String name, Type type, double percent, double percentMax, List<String> items, int pick,
                          List<LocalTime> times, Set<DayOfWeek> days, double chance, int warnMinutes) {

    public enum Type { CRASH, SURGE }

    public MarketEvent {
        items = List.copyOf(items);
        times = List.copyOf(times);
        days = Set.copyOf(days);
    }

    /** What one run will do: its size and the items hit. {@code items} empty means every item. */
    public record Roll(double percent, List<String> items) {
        public Roll {
            items = List.copyOf(items);
        }

        public boolean allItems() {
            return items.isEmpty();
        }
    }

    public boolean allItems() {
        return items.isEmpty();
    }

    /** True if the size or the items change from run to run. */
    public boolean random() {
        return pick > 0 || percentMax > percent;
    }

    /** The percent as the market takes it: negative for a crash. */
    public double signed(double size) {
        return type == Type.CRASH ? -size : size;
    }

    /** Decides the size and the items for one run. {@code marketItems} is every item in the market. */
    public Roll roll(Random random, Collection<String> marketItems) {
        double size = percent;
        if (percentMax > percent) {
            size = Math.round((percent + random.nextDouble() * (percentMax - percent)) * 10.0) / 10.0;
        }
        if (pick <= 0) return new Roll(size, items);
        List<String> pool = new ArrayList<>(allItems() ? marketItems : items);
        Collections.shuffle(pool, random);
        List<String> chosen = new ArrayList<>(pool.subList(0, Math.min(pick, pool.size())));
        Collections.sort(chosen);
        return new Roll(size, chosen);
    }

    /** True if the event has a time of day, so it runs by itself. Events with none can only be run by hand. */
    public boolean scheduled() {
        return !times.isEmpty();
    }
}
