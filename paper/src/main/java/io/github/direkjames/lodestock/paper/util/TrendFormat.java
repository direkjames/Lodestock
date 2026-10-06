package io.github.direkjames.lodestock.paper.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formats a percent change as a coloured arrow and number. Green for up, red for down. */
public final class TrendFormat {
    private static final String UP = "▲";
    private static final String DOWN = "▼";

    private TrendFormat() {}

    private static DecimalFormat format() {
        return new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ROOT)); // not thread-safe, so one per call
    }

    /** NaN (no data yet) shows as a dash. */
    public static Component of(double percent) {
        if (Double.isNaN(percent)) return Component.text("-", NamedTextColor.GRAY);
        if (percent > 0.049) return Component.text(UP + " +" + format().format(percent) + "%", NamedTextColor.GREEN);
        if (percent < -0.049) return Component.text(DOWN + " " + format().format(percent) + "%", NamedTextColor.RED);
        return Component.text("0.0%", NamedTextColor.GRAY);
    }

    /** The percent in tenths, so two views can be compared. NaN gives a value no real change can have. */
    public static int tenths(double percent) {
        return Double.isNaN(percent) ? Integer.MIN_VALUE : (int) Math.round(percent * 10.0);
    }
}
