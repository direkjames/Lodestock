package io.github.direkjames.lodestock.core.events;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketEventTest {
    private static final List<String> ITEMS = List.of("coal", "iron_ingot", "diamond");
    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private static MarketEvent event(List<String> times, Set<DayOfWeek> days) {
        return new MarketEvent("e", "E", MarketEvent.Type.CRASH, 10, List.of(),
                times.stream().map(LocalTime::parse).toList(), days, 100, 5);
    }

    @Test
    void aFullEventIsRead() {
        List<String> problems = new ArrayList<>();
        var e = MarketEventParser.parse("rush", Map.of("name", "Ore Rush", "type", "SURGE", "percent", 25,
                "items", List.of("Coal", "minecraft:iron_ingot"), "times", List.of("18:00", "21:30"),
                "days", List.of("Friday", "sat"), "chance", 50, "warn-minutes", 10), ITEMS, problems).orElseThrow();
        assertTrue(problems.isEmpty());
        assertEquals("Ore Rush", e.name());
        assertEquals(25.0, e.signedPercent(), 0.0001);
        assertEquals(List.of("coal", "iron_ingot"), e.items());
        assertEquals(List.of(LocalTime.of(18, 0), LocalTime.of(21, 30)), e.times());
        assertEquals(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), e.days());
        assertEquals(10, e.warnMinutes());
    }

    @Test
    void aCrashIsNegativeAndMissingItemsMeansEverything() {
        var e = MarketEventParser.parse("c", Map.of("type", "crash", "percent", 20, "times", List.of("20:00")),
                ITEMS, new ArrayList<>()).orElseThrow();
        assertEquals(-20.0, e.signedPercent(), 0.0001);
        assertTrue(e.allItems());
        assertEquals("c", e.name());
        assertEquals(100.0, e.chance(), 0.0001);
        assertEquals(5, e.warnMinutes());
    }

    @Test
    void badEventsAreSkippedWithAReason() {
        List<String> problems = new ArrayList<>();
        assertTrue(MarketEventParser.parse("a", Map.of("type", "boom", "percent", 10), ITEMS, problems).isEmpty());
        assertTrue(MarketEventParser.parse("b", Map.of("type", "crash", "percent", 100), ITEMS, problems).isEmpty());
        assertTrue(MarketEventParser.parse("c", Map.of("type", "surge", "percent", 5000), ITEMS, problems).isEmpty());
        assertTrue(MarketEventParser.parse("d", Map.of("type", "crash", "percent", 10, "items", List.of("dirt")), ITEMS, problems).isEmpty());
        assertEquals(5, problems.size()); // the last one is reported twice: a bad item, then no items left
        assertTrue(MarketEventParser.parse("off", Map.of("enabled", false, "type", "crash", "percent", 10), ITEMS, problems).isEmpty());
        assertEquals(5, problems.size()); // turned off on purpose is not a problem
    }

    @Test
    void singleBadValuesAreIgnoredNotTheWholeEvent() {
        List<String> problems = new ArrayList<>();
        var e = MarketEventParser.parse("x", Map.of("type", "crash", "percent", 10,
                "items", List.of("coal", "dirt"), "times", List.of("20:00", "late"), "days", List.of("Funday", "Monday"),
                "chance", 250), ITEMS, problems).orElseThrow();
        assertEquals(List.of("coal"), e.items());
        assertEquals(List.of(LocalTime.of(20, 0)), e.times());
        assertEquals(Set.of(DayOfWeek.MONDAY), e.days());
        assertEquals(100.0, e.chance(), 0.0001);
        assertEquals(4, problems.size());
    }

    @Test
    void unquotedTimesAreExplained() {
        List<String> problems = new ArrayList<>();
        MarketEventParser.parse("x", Map.of("type", "crash", "percent", 10, "times", 1080), ITEMS, problems);
        assertTrue(problems.stream().anyMatch(p -> p.contains("in quotes")));
    }

    @Test
    void anEventWithoutTimesNeverRunsByItself() {
        var e = event(List.of(), Set.of());
        assertFalse(e.scheduled());
        assertTrue(EventTimes.next(e, MANILA, Instant.parse("2026-10-07T00:00:00Z")).isEmpty());
    }

    @Test
    void nextUsesTheTimeZoneAndSkipsToTheRightDay() {
        // 20:00 Manila is 12:00 UTC. It is Wednesday 2026-10-07 10:00 UTC.
        var daily = event(List.of("20:00"), Set.of());
        assertEquals(Instant.parse("2026-10-07T12:00:00Z"), EventTimes.next(daily, MANILA, Instant.parse("2026-10-07T10:00:00Z")).orElseThrow());
        // already past today: tomorrow
        assertEquals(Instant.parse("2026-10-08T12:00:00Z"), EventTimes.next(daily, MANILA, Instant.parse("2026-10-07T12:00:00Z")).orElseThrow());
        // only Saturdays: 2026-10-10 is a Saturday
        var saturdays = event(List.of("20:00"), Set.of(DayOfWeek.SATURDAY));
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), EventTimes.next(saturdays, MANILA, Instant.parse("2026-10-07T10:00:00Z")).orElseThrow());
    }

    @Test
    void betweenListsEveryTimeInOrder() {
        var twice = event(List.of("21:00", "08:00"), Set.of());
        List<Instant> due = EventTimes.between(twice, MANILA, Instant.parse("2026-10-07T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"));
        // Manila 08:00 = 00:00Z, 21:00 = 13:00Z
        assertEquals(List.of(Instant.parse("2026-10-07T00:00:00Z"), Instant.parse("2026-10-07T13:00:00Z"),
                Instant.parse("2026-10-08T00:00:00Z")), due);
    }
}
