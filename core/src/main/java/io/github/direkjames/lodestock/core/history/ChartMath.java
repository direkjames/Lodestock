package io.github.direkjames.lodestock.core.history;

import java.util.List;
import java.util.Optional;

/** The maths behind price charts and trends. No Minecraft code. Samples must be sorted oldest first. */
public final class ChartMath {
    private static final char[] SPARK = {'\u2581', '\u2582', '\u2583', '\u2584', '\u2585', '\u2586', '\u2587', '\u2588'};

    private ChartMath() {}

    /** Summary of a list of samples. {@code average} is the plain average of the samples. */
    public record Stats(double low, double high, double average, double first, double last,
                        double changePercent, int samples) {}

    public static Optional<Stats> stats(List<PricePoint> points) {
        if (points.isEmpty()) return Optional.empty();
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        double sum = 0;
        for (PricePoint p : points) {
            low = Math.min(low, p.price());
            high = Math.max(high, p.price());
            sum += p.price();
        }
        double first = points.get(0).price();
        double last = points.get(points.size() - 1).price();
        return Optional.of(new Stats(low, high, sum / points.size(), first, last,
                changePercent(first, last), points.size()));
    }

    /** Percent change from {@code from} to {@code to}. 0 if {@code from} is not above 0. */
    public static double changePercent(double from, double to) {
        return from > 0 ? (to - from) / from * 100.0 : 0.0;
    }

    /**
     * Cuts the time from {@code from} to {@code to} into {@code buckets} equal slices and gives the
     * price at the end of each slice: the latest sample at or before that moment. A slice that ends
     * before the first sample has no data and is NaN.
     */
    public static double[] buckets(List<PricePoint> points, long from, long to, int buckets) {
        if (buckets < 1) throw new IllegalArgumentException("buckets must be at least 1");
        if (to <= from) throw new IllegalArgumentException("the range must be longer than 0");
        double[] values = new double[buckets];
        int next = 0;
        double current = Double.NaN;
        for (int i = 0; i < buckets; i++) {
            long end = from + (to - from) * (i + 1) / buckets;
            while (next < points.size() && points.get(next).time() <= end) {
                current = points.get(next).price();
                next++;
            }
            values[i] = current;
        }
        return values;
    }

    /** How many panes tall a bar is, from 1 to {@code levels}. A flat chart gets bars of middle height. */
    public static int level(double value, double min, double max, int levels) {
        if (levels < 1) throw new IllegalArgumentException("levels must be at least 1");
        if (max <= min) return (levels + 1) / 2;
        double share = (value - min) / (max - min);
        return 1 + (int) Math.round(Math.max(0.0, Math.min(1.0, share)) * (levels - 1));
    }

    /** A one-line chart made of block characters, from low to high. Slices with no data are shown as a space. */
    public static String spark(double[] values) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : values) {
            if (Double.isNaN(v)) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        StringBuilder out = new StringBuilder();
        for (double v : values) {
            out.append(Double.isNaN(v) ? ' ' : SPARK[level(v, min, max, SPARK.length) - 1]);
        }
        return out.toString();
    }
}
