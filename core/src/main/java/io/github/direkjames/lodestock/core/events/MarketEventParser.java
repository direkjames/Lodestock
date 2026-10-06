package io.github.direkjames.lodestock.core.events;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads one event from the plain values of its section in events.yml. Anything wrong is reported in
 * {@code problems} and the event is skipped (or the single bad value is ignored), so one typo never
 * stops the others.
 */
public final class MarketEventParser {
    public static final double MAX_CRASH = 99;
    public static final double MAX_SURGE = 1000;

    private MarketEventParser() {}

    public static Optional<MarketEvent> parse(String id, Map<String, ?> raw, Collection<String> knownItems, List<String> problems) {
        String where = "events." + id + ": ";
        if (Boolean.FALSE.equals(raw.get("enabled"))) return Optional.empty();

        MarketEvent.Type type;
        String typeText = text(raw.get("type")).toLowerCase(Locale.ROOT);
        if (typeText.equals("crash")) type = MarketEvent.Type.CRASH;
        else if (typeText.equals("surge")) type = MarketEvent.Type.SURGE;
        else {
            problems.add(where + "type must be crash or surge, so the event was skipped");
            return Optional.empty();
        }

        double max = type == MarketEvent.Type.CRASH ? MAX_CRASH : MAX_SURGE;
        double percent = number(raw.get("percent"), Double.NaN);
        if (!(percent >= 1 && percent <= max)) {
            problems.add(where + "percent must be from 1 to " + (int) max + ", so the event was skipped");
            return Optional.empty();
        }

        List<String> items = new ArrayList<>();
        Object rawItems = raw.get("items");
        boolean all = rawItems == null || (rawItems instanceof String s && s.trim().equalsIgnoreCase("all"));
        if (!all) {
            Set<String> seen = new LinkedHashSet<>();
            for (String item : strings(rawItems)) {
                String key = item.toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "");
                if (key.equals("all")) {
                    seen.clear();
                    all = true;
                    break;
                }
                if (knownItems.contains(key)) seen.add(key);
                else problems.add(where + "\"" + item + "\" is not an item in the market, so it was ignored");
            }
            if (!all) {
                if (seen.isEmpty()) {
                    problems.add(where + "none of the items are in the market, so the event was skipped");
                    return Optional.empty();
                }
                items.addAll(seen);
            }
        }

        List<LocalTime> times = new ArrayList<>();
        Object rawTimes = raw.get("times");
        if (rawTimes instanceof Number) {
            problems.add(where + "times must be in quotes, like \"18:00\" (without quotes YAML reads it as a number)");
        }
        for (String t : strings(rawTimes)) {
            try {
                LocalTime time = LocalTime.parse(t.trim());
                if (!times.contains(time)) times.add(time);
            } catch (DateTimeParseException e) {
                problems.add(where + "\"" + t + "\" is not a time like 18:00, so it was ignored");
            }
        }

        Set<DayOfWeek> days = new LinkedHashSet<>();
        for (String d : strings(raw.get("days"))) {
            DayOfWeek day = day(d);
            if (day == null) problems.add(where + "\"" + d + "\" is not a day of the week, so it was ignored");
            else days.add(day);
        }

        double chance = number(raw.get("chance"), 100);
        if (chance < 0 || chance > 100) {
            problems.add(where + "chance must be from 0 to 100, using 100");
            chance = 100;
        }
        int warn = (int) Math.round(number(raw.get("warn-minutes"), 5));
        if (warn < 0 || warn > 1440) {
            problems.add(where + "warn-minutes must be from 0 to 1440, using 5");
            warn = 5;
        }

        String name = text(raw.get("name"));
        if (name.isBlank()) name = id;
        if (times.isEmpty()) {
            problems.add(where + "has no times, so it never runs by itself (you can still run it with /lodestock events run " + id + ")");
        }
        return Optional.of(new MarketEvent(id, name, type, percent, items, times, days, chance, warn));
    }

    private static DayOfWeek day(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.length() < 3) return null;
        for (DayOfWeek d : DayOfWeek.values()) {
            String name = d.name().toLowerCase(Locale.ROOT);
            if (name.equals(t) || (t.length() == 3 && name.startsWith(t))) return d;
        }
        return null;
    }

    private static String text(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static double number(Object o, double fallback) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private static List<String> strings(Object o) {
        List<String> result = new ArrayList<>();
        if (o instanceof Collection<?> c) {
            for (Object item : c) if (item != null) result.add(item.toString());
        } else if (o instanceof String s && !s.isBlank()) {
            result.add(s);
        }
        return result;
    }
}
