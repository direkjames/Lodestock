package io.github.direkjames.lodestock.paper.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads time spans such as 24h or 7d. */
public final class HourSpan {
    private static final Pattern SPAN = Pattern.compile("(\\d{1,4})([hd])");
    private static final long MAX_HOURS = 365L * 24;

    private HourSpan() {}

    /** "24h" gives 24 and "7d" gives 168. Gives 0 for anything else, for zero, or for more than a year. */
    public static long parse(String text) {
        Matcher m = SPAN.matcher(text.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) return 0;
        long value = Long.parseLong(m.group(1));
        long hours = m.group(2).equals("d") ? value * 24 : value;
        return hours < 1 || hours > MAX_HOURS ? 0 : hours;
    }
}
