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
        return new MarketEvent("e", "E", MarketEvent.Type.CRASH, 10, 10, List.of(), 0,
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
        assertEquals(25.0, e.signed(e.percent()), 0.0001);
        assertEquals(List.of("coal", "iron_ingot"), e.items());
        assertEquals(List.of(LocalTime.of(18, 0), LocalTime.of(21, 30)), e.times());
        assertEquals(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), e.days());
        assertEquals(10, e.warnMinutes());
    }

    @Test
    void aCrashIsNegativeAndMissingItemsMeansEverything() {
        var e = MarketEventParser.parse("c", Map.of("type", "crash", "percent", 20, "times", List.of("20:00")),
                ITEMS, new ArrayList<>()).orElseThrow();
        assertEquals(-20.0, e.signed(e.percent()), 0.0001);
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
    void aPercentRangeAndAPickAreRead() {
        List<String> problems = new ArrayList<>();
        var e = MarketEventParser.parse("r", Map.of("type", "surge", "percent", "10-25", "items", List.of("coal", "iron_ingot", "diamond"),
                "pick", 2, "times", List.of("20:00")), ITEMS, problems).orElseThrow();
        assertTrue(problems.isEmpty());
        assertEquals(10.0, e.percent(), 0.0001);
        assertEquals(25.0, e.percentMax(), 0.0001);
        assertEquals(2, e.pick());
        assertTrue(e.random());
        // a bad range, a range above the cap, and a pick as large as the list
        assertTrue(MarketEventParser.parse("b", Map.of("type", "crash", "percent", "30-10"), ITEMS, problems).isEmpty());
        assertTrue(MarketEventParser.parse("c", Map.of("type", "crash", "percent", "10-100"), ITEMS, problems).isEmpty());
        assertEquals(2, problems.size());
        var all = MarketEventParser.parse("p", Map.of("type", "crash", "percent", 10, "items", List.of("coal", "diamond"), "pick", 2, "times", List.of("20:00")),
                ITEMS, problems).orElseThrow();
        assertEquals(0, all.pick());
        assertEquals(3, problems.size());
    }

    @Test
    void aRollPicksFromThePoolAndStaysInTheRange() {
        var e = new MarketEvent("e", "E", MarketEvent.Type.SURGE, 10, 25, List.of("coal", "iron_ingot", "diamond"), 2,
                List.of(LocalTime.of(20, 0)), Set.of(), 100, 5);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            var roll = e.roll(new java.util.Random(i), ITEMS);
            assertEquals(2, roll.items().size());
            assertTrue(roll.items().stream().allMatch(ITEMS::contains));
            assertTrue(roll.percent() >= 10 && roll.percent() <= 25);
            seen.add(String.join(",", roll.items()));
        }
        assertEquals(3, seen.size()); // every pair turns up, so it really is random
    }

    @Test
    void anEverythingPoolPicksFromTheWholeMarketAndFixedEventsDoNotChange() {
        var pool = new MarketEvent("e", "E", MarketEvent.Type.CRASH, 15, 15, List.of(), 1, List.of(), Set.of(), 100, 5);
        var roll = pool.roll(new java.util.Random(1), ITEMS);
        assertEquals(1, roll.items().size());
        assertTrue(ITEMS.contains(roll.items().get(0)));
        assertEquals(15.0, roll.percent(), 0.0001);

        var fixed = event(List.of("20:00"), Set.of());
        var same = fixed.roll(new java.util.Random(1), ITEMS);
        assertTrue(same.allItems());
        assertEquals(10.0, same.percent(), 0.0001);
        assertFalse(fixed.random());
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
