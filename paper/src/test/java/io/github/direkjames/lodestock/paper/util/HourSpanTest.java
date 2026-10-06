package io.github.direkjames.lodestock.paper.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HourSpanTest {
    @Test
    void hoursAndDaysAreUnderstood() {
        assertEquals(24, HourSpan.parse("24h"));
        assertEquals(168, HourSpan.parse("7d"));
        assertEquals(12, HourSpan.parse(" 12H "));
    }

    @Test
    void nonsenseZeroAndHugeSpansGiveZero() {
        assertEquals(0, HourSpan.parse("abc"));
        assertEquals(0, HourSpan.parse("0h"));
        assertEquals(0, HourSpan.parse("-5d"));
        assertEquals(0, HourSpan.parse("5w"));
        assertEquals(0, HourSpan.parse("9999d"));
        assertEquals(365 * 24, HourSpan.parse("365d"));
    }
}
