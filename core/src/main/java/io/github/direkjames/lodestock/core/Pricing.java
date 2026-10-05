package io.github.direkjames.lodestock.core;

/** Platform-independent price maths. No Minecraft code belongs here. */
public final class Pricing {
    private Pricing() {}

    /** Price after taking off a percentage tax. */
    public static double afterTax(double price, double taxPercent) {
        return price - ((price / 100.0) * taxPercent);
    }

    /** Next price after one trade. The price never drops below the floor. */
    public static double nextPrice(double current, double afterTax, double multiplier,
                                   boolean bought, double floor) {
        double next = bought ? current + (afterTax * multiplier)
                : current - (afterTax * multiplier);
        return Math.max(next, floor);
    }
}