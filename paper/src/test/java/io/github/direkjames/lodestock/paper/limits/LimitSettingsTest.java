package io.github.direkjames.lodestock.paper.limits;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LimitSettingsTest {
    private static LimitSettings at(String time, String zone) {
        return new LimitSettings(0, 0, LocalTime.parse(time), ZoneId.of(zone));
    }

    @Test
    void midnightResetUsesTheCalendarDay() {
        LimitSettings s = at("00:00", "UTC");
        assertEquals("2026-10-06", s.dayKey(Instant.parse("2026-10-06T00:00:00Z")));
        assertEquals("2026-10-06", s.dayKey(Instant.parse("2026-10-06T23:59:59Z")));
        assertEquals("2026-10-07", s.dayKey(Instant.parse("2026-10-07T00:00:00Z")));
        assertEquals(Instant.parse("2026-10-07T00:00:00Z"), s.nextReset(Instant.parse("2026-10-06T13:00:00Z")));
    }

    @Test
    void aLaterResetTimeKeepsTheEarlyHoursInThePreviousDay() {
        LimitSettings s = at("04:00", "UTC");
        assertEquals("2026-10-05", s.dayKey(Instant.parse("2026-10-06T03:59:00Z")));
        assertEquals("2026-10-06", s.dayKey(Instant.parse("2026-10-06T04:00:00Z")));
        assertEquals(Instant.parse("2026-10-06T04:00:00Z"), s.nextReset(Instant.parse("2026-10-06T03:00:00Z")));
        assertEquals(Instant.parse("2026-10-07T04:00:00Z"), s.nextReset(Instant.parse("2026-10-06T05:00:00Z")));
    }

    @Test
    void theTimeZoneDecidesWhereTheDayStarts() {
        Instant now = Instant.parse("2026-10-06T17:00:00Z"); // already Oct 7, 01:00 in Manila
        assertEquals("2026-10-06", at("00:00", "UTC").dayKey(now));
        assertEquals("2026-10-07", at("00:00", "Asia/Manila").dayKey(now));
        assertNotEquals(at("00:00", "UTC").nextReset(now), at("00:00", "Asia/Manila").nextReset(now));
    }

    @Test
    void resetsInIsReadable() {
        LimitSettings s = at("00:00", "UTC");
        assertEquals("5h 12m", s.resetsIn(Instant.parse("2026-10-06T18:48:00Z")));
        assertEquals("9m", s.resetsIn(Instant.parse("2026-10-06T23:51:00Z")));
        assertEquals("1m", s.resetsIn(Instant.parse("2026-10-06T23:59:30Z")));
    }
}
