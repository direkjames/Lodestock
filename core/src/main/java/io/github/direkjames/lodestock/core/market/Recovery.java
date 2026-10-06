package io.github.direkjames.lodestock.core.market;

/** The maths behind price drift and stock regeneration. No Minecraft code. */
public final class Recovery {
    private Recovery() {}

    /**
     * The price after {@code steps} drift steps. Each step closes {@code percent} of the gap to
     * {@code base}, so it moves fast when far away and gently when close. Snaps to the base once
     * the gap is tiny. Never below the floor.
     */
    public static double driftPrice(double price, double base, double percent, int steps, double floor) {
        if (steps < 1 || percent <= 0) return price;
        double remaining = (price - base) * Math.pow(1.0 - percent / 100.0, steps);
        double result = base + remaining;
        if (Math.abs(result - base) < Math.max(floor, base * 0.001)) result = base;
        return Math.max(result, floor);
    }

    /**
     * The stock after {@code steps} regeneration steps. Each step moves {@code percent} of max-stock
     * (at least 1 item) toward {@code target}, and never past it.
     */
    public static int regenStock(int stock, int target, int maxStock, double percent, int steps) {
        if (steps < 1 || percent <= 0 || stock == target) return stock;
        long perStep = Math.max(1L, (long) Math.ceil(maxStock * percent / 100.0));
        long move = perStep * steps;
        if (stock < target) return (int) Math.min(target, stock + move);
        return (int) Math.max(target, stock - move);
    }
}
