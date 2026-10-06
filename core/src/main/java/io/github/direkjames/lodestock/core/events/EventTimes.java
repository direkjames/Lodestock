package io.github.direkjames.lodestock.core.events;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Works out when a scheduled event is due. */
public final class EventTimes {
    private EventTimes() {}

    /** Every moment the event is due in {@code [from, to]}, in order. */
    public static List<Instant> between(MarketEvent event, ZoneId zone, Instant from, Instant to) {
        List<Instant> result = new ArrayList<>();
        if (!event.scheduled() || to.isBefore(from)) return result;
        LocalDate first = from.atZone(zone).toLocalDate().minusDays(1);
        LocalDate last = to.atZone(zone).toLocalDate().plusDays(1);
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            if (!event.days().isEmpty() && !event.days().contains(day.getDayOfWeek())) continue;
            for (LocalTime time : event.times()) {
                Instant at = ZonedDateTime.of(day, time, zone).toInstant();
                if (!at.isBefore(from) && !at.isAfter(to)) result.add(at);
            }
        }
        result.sort(null);
        return result;
    }

    /** The first moment after {@code after} that the event is due, looking up to 8 days ahead. */
    public static Optional<Instant> next(MarketEvent event, ZoneId zone, Instant after) {
        List<Instant> due = between(event, zone, after.plusMillis(1), after.plusSeconds(8L * 86_400L));
        return due.isEmpty() ? Optional.empty() : Optional.of(due.get(0));
    }
}
