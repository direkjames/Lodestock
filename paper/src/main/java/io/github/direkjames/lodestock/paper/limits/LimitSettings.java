package io.github.direkjames.lodestock.paper.limits;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Daily limit settings. {@code dailyBuy} / {@code dailySell} are the defaults per player and item
 * (0 = unlimited). A "day" starts at {@code resetTime} in {@code zone}.
 */
public record LimitSettings(int dailyBuy, int dailySell, LocalTime resetTime, ZoneId zone) {
    public static LimitSettings off() {
        return new LimitSettings(0, 0, LocalTime.MIDNIGHT, ZoneId.systemDefault());
    }

    /** Names the limit day that {@code now} belongs to, such as 2026-10-06. */
    public String dayKey(Instant now) {
        return keyDate(now).toString();
    }

    /** When the current limit day ends. */
    public Instant nextReset(Instant now) {
        return keyDate(now).plusDays(1).atTime(resetTime).atZone(zone).toInstant();
    }

    /** Time left until the next reset, as text such as "5h 12m" or "9m". Never shorter than 1 minute. */
    public String resetsIn(Instant now) {
        long minutes = Math.max(1, (Duration.between(now, nextReset(now)).getSeconds() + 59) / 60);
        long hours = minutes / 60;
        return hours > 0 ? hours + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    private LocalDate keyDate(Instant now) {
        ZonedDateTime time = now.atZone(zone);
        LocalDate date = time.toLocalDate();
        return time.isBefore(date.atTime(resetTime).atZone(zone)) ? date.minusDays(1) : date;
    }
}
