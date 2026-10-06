package io.github.direkjames.lodestock.core.history;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChartMathTest {
    private static PricePoint p(long time, double price) {
        return new PricePoint(time, price, 10);
    }

    @Test
    void statsSummariseTheSamples() {
        ChartMath.Stats s = ChartMath.stats(List.of(p(1, 100), p(2, 120), p(3, 90), p(4, 110))).orElseThrow();
        assertEquals(90, s.low(), 1e-9);
        assertEquals(120, s.high(), 1e-9);
        assertEquals(105, s.average(), 1e-9);
        assertEquals(100, s.first(), 1e-9);
        assertEquals(110, s.last(), 1e-9);
        assertEquals(10.0, s.changePercent(), 1e-9);
        assertEquals(4, s.samples());
    }

    @Test
    void noSamplesMeansNoStats() {
        assertTrue(ChartMath.stats(List.of()).isEmpty());
    }

    @Test
    void changePercentGoesBothWaysAndSurvivesZero() {
        assertEquals(-50.0, ChartMath.changePercent(100, 50), 1e-9);
        assertEquals(0.0, ChartMath.changePercent(0, 50), 1e-9);
    }

    @Test
    void bucketsCarryTheLatestPriceForwardAndMarkMissingStartAsNaN() {
        // range 0..100 in 4 slices ending at 25, 50, 75, 100
        double[] b = ChartMath.buckets(List.of(p(30, 10), p(60, 20)), 0, 100, 4);
        assertTrue(Double.isNaN(b[0]));   // before the first sample
        assertEquals(10, b[1], 1e-9);     // sample at 30 is the latest by 50
        assertEquals(20, b[2], 1e-9);     // sample at 60 by 75
        assertEquals(20, b[3], 1e-9);     // carried forward
    }

    @Test
    void aSampleExactlyAtTheEndOfASliceCountsInThatSlice() {
        double[] b = ChartMath.buckets(List.of(p(50, 7)), 0, 100, 2);
        assertEquals(7, b[0], 1e-9);
    }

    @Test
    void badRangesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ChartMath.buckets(List.of(), 0, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> ChartMath.buckets(List.of(), 100, 100, 3));
    }

    @Test
    void barHeightsSpanOneToTheMaximum() {
        assertEquals(1, ChartMath.level(10, 10, 20, 5));
        assertEquals(5, ChartMath.level(20, 10, 20, 5));
        assertEquals(3, ChartMath.level(15, 10, 20, 5));
        assertEquals(3, ChartMath.level(10, 10, 10, 5)); // flat chart
    }

    @Test
    void sparkUsesLowToHighBlocksAndSpacesForMissingData() {
        String spark = ChartMath.spark(new double[]{Double.NaN, 1, 5, 10});
        assertEquals(4, spark.length());
        assertEquals(' ', spark.charAt(0));
        assertEquals('\u2581', spark.charAt(1));
        assertEquals('\u2588', spark.charAt(3));
        assertTrue(spark.charAt(2) > '\u2581' && spark.charAt(2) < '\u2588');
    }
}
