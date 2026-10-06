package io.github.direkjames.lodestock.core.audit;

import io.github.direkjames.lodestock.core.Pricing;
import io.github.direkjames.lodestock.core.market.InMemoryMarketStorage;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.MarketSettings;
import io.github.direkjames.lodestock.core.market.RecoverySettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/**
 * Checks a market setup for ways players could make free money, and estimates how much money
 * the market can create. No Minecraft code. The platform turns each finding's {@code code} into text.
 */
public final class EconomyAudit {
    private EconomyAudit() {}

    public enum Level { WARN, NOTE }

    /** One thing worth telling the owner. {@code args} fill the message's placeholders. */
    public record Finding(Level level, String code, List<String> args) {}

    /** A rough most-money-per-day figure for one item. */
    public record Ceiling(String item, double perDay) {}

    public record Report(List<Finding> findings, List<Ceiling> ceilings, double totalCeiling) {
        public long count(Level level) {
            return findings.stream().filter(f -> f.level() == level).count();
        }
    }

    /** The largest trade size tried in a loop check. Prices change fast, so bigger sizes add nothing. */
    static final int LOOP_MAX_AMOUNT = 5000;

    /** A loop must beat this much (a cent or so) to count, so rounding does not raise false alarms. */
    static final double LOOP_TOLERANCE = 0.011;

    /** Items that can be crafted, uncrafted or smelted into each other. */
    record Recipe(String input, int inputQty, String output, int outputQty, boolean reversible) {}

    static final List<Recipe> RECIPES = List.of(
            block("iron_ingot", "iron_block"), block("gold_ingot", "gold_block"),
            block("copper_ingot", "copper_block"), block("diamond", "diamond_block"),
            block("emerald", "emerald_block"), block("lapis_lazuli", "lapis_block"),
            block("redstone", "redstone_block"), block("coal", "coal_block"),
            block("netherite_ingot", "netherite_block"),
            block("raw_iron", "raw_iron_block"), block("raw_gold", "raw_gold_block"),
            block("raw_copper", "raw_copper_block"),
            block("iron_nugget", "iron_ingot"), block("gold_nugget", "gold_ingot"),
            new Recipe("quartz", 4, "quartz_block", 1, false),
            new Recipe("amethyst_shard", 4, "amethyst_block", 1, false),
            new Recipe("glowstone_dust", 4, "glowstone", 1, false),
            new Recipe("raw_iron", 1, "iron_ingot", 1, false),
            new Recipe("raw_gold", 1, "gold_ingot", 1, false),
            new Recipe("raw_copper", 1, "copper_ingot", 1, false),
            new Recipe("ancient_debris", 1, "netherite_scrap", 1, false));

    private static Recipe block(String small, String big) {
        return new Recipe(small, 9, big, 1, true);
    }

    /**
     * @param livePrices current prices by item ID, or null to skip the "right now" crafting check
     */
    public static Report run(MarketSettings settings, RecoverySettings recovery,
                             int defaultDailyBuy, int defaultDailySell,
                             Collection<MarketItem> items, Map<String, Double> livePrices) {
        List<Finding> out = new ArrayList<>();

        double tax = settings.taxPercent();
        if (tax == 0) out.add(new Finding(Level.WARN, "tax-zero", List.of()));
        else if (tax < 5) out.add(new Finding(Level.NOTE, "tax-low", List.of(num(tax))));

        if (settings.multiplier() > 0.05) {
            out.add(new Finding(Level.NOTE, "multiplier-high", List.of(num(settings.multiplier()))));
        }

        double cheapest = items.stream().mapToDouble(MarketItem::basePrice).min().orElse(0);
        if (cheapest > 0 && settings.priceFloor() * 50 < cheapest) {
            out.add(new Finding(Level.NOTE, "floor-low", List.of(num(settings.priceFloor()), num(cheapest))));
        }

        boolean recovers = (recovery.driftEnabled() && recovery.driftPercent() > 0)
                || (recovery.regenEnabled() && recovery.regenPercent() > 0);
        boolean anyLimit = defaultDailyBuy > 0 || defaultDailySell > 0
                || items.stream().anyMatch(i -> i.dailyBuy() > 0 || i.dailySell() > 0);
        if (recovers && !anyLimit) out.add(new Finding(Level.NOTE, "renewable-no-limits", List.of()));

        out.addAll(roundTrips(settings, items));

        Map<String, Double> base = new HashMap<>();
        for (MarketItem item : items) base.put(item.id(), item.basePrice());
        List<Finding> craftBase = craftingLoops(base, tax, Level.WARN, "crafting-loop");
        out.addAll(craftBase);
        if (livePrices != null) {
            for (Finding f : craftingLoops(livePrices, tax, Level.NOTE, "crafting-loop-now")) {
                boolean alreadyReported = craftBase.stream().anyMatch(
                        b -> b.args().get(0).equals(f.args().get(0)) && b.args().get(1).equals(f.args().get(1)));
                if (!alreadyReported) out.add(f);
            }
        }

        List<Ceiling> ceilings = ceilings(settings, recovery, items);
        double total = ceilings.stream().mapToDouble(Ceiling::perDay).sum();
        return new Report(List.copyOf(out), ceilings, total);
    }

    /**
     * Buys then sells (and sells then buys back) each item with real market maths, at many trade sizes up to
     * the item's max stock, and reports any profit. Big trades matter: selling a lot drops the price, and
     * buying the same amount back can cost less than was paid out when the multiplier is high.
     */
    static List<Finding> roundTrips(MarketSettings settings, Collection<MarketItem> items) {
        List<Finding> out = new ArrayList<>();
        for (MarketItem item : items) {
            if (!item.allowBuy() || !item.allowSell()) continue;
            double worst = 0;
            String worstWay = "";
            int worstAmount = 0;
            for (int amount : amountsUpTo(Math.min(item.maxStock(), LOOP_MAX_AMOUNT))) {
                for (boolean buyFirst : new boolean[]{true, false}) {
                    double profit = loopProfit(settings, item.basePrice(), item.maxStock(), amount, buyFirst);
                    if (profit > worst) {
                        worst = profit;
                        worstWay = buyFirst ? "buy-first" : "sell-first";
                        worstAmount = amount;
                    }
                }
            }
            if (worst > LOOP_TOLERANCE) {
                out.add(new Finding(Level.WARN, "round-trip", List.of(
                        item.id(), worstWay, String.valueOf(worstAmount), num(worst))));
            }
        }
        return out;
    }

    /** 1 to 8, then about 40% more each time, ending exactly on {@code limit}. */
    static List<Integer> amountsUpTo(int limit) {
        List<Integer> out = new ArrayList<>();
        int amount = 1;
        while (amount < limit) {
            out.add(amount);
            amount = amount < 8 ? amount + 1 : (int) Math.ceil(amount * 1.4);
        }
        if (limit >= 1) out.add(limit);
        return out;
    }

    /**
     * Money made by buying then selling {@code amount} of an item (or the reverse), starting at its base
     * price. Stock never changes prices, only how much can be traded, so the market is set up at the stock
     * level that allows the biggest trade: full when buying first, empty when selling first.
     */
    static double loopProfit(MarketSettings settings, double basePrice, int maxStock, int amount, boolean buyFirst) {
        MarketItem scratch = new MarketItem("loop", basePrice, buyFirst ? maxStock : 0, maxStock, true, true, false, false);
        Market m = new Market(settings, List.of(scratch), new InMemoryMarketStorage());
        if (buyFirst) {
            var bought = m.previewBuy("loop", amount);
            if (!bought.ok()) return 0;
            m.recordBuy("loop", bought.count());
            var sold = m.previewSell("loop", bought.count());
            return sold.ok() ? sold.total() - bought.total() : 0;
        }
        var sold = m.previewSell("loop", amount);
        if (!sold.ok()) return 0;
        m.recordSell("loop", sold.count());
        var bought = m.previewBuy("loop", sold.count());
        return bought.ok() ? sold.total() - bought.total() : 0;
    }

    /** Finds block/ingot/smelting pairs where crafting makes money. Only pairs with both items listed count. */
    static List<Finding> craftingLoops(Map<String, Double> prices, double taxPercent, Level level, String code) {
        List<Finding> out = new ArrayList<>();
        for (Recipe r : RECIPES) {
            Double in = prices.get(r.input());
            Double outPrice = prices.get(r.output());
            if (in == null || outPrice == null) continue;
            double forward = Pricing.afterTax(outPrice, taxPercent) * r.outputQty() - in * r.inputQty();
            if (forward > LOOP_TOLERANCE) {
                out.add(new Finding(level, code, List.of(r.input(), r.output(), "craft", num(forward))));
            }
            if (r.reversible()) {
                double backward = Pricing.afterTax(in, taxPercent) * r.inputQty() - outPrice * r.outputQty();
                if (backward > LOOP_TOLERANCE) {
                    out.add(new Finding(level, code, List.of(r.output(), r.input(), "uncraft", num(backward))));
                }
            }
        }
        return out;
    }

    /**
     * A rough most-money-per-day each item can pay out in the long run, if players sell it as fast as stock
     * regenerates. Every step the market takes back {@code regen} items; the sales push the price down and
     * drift pulls it back up, so the price settles at a share of the base price (worked out below). Real
     * payouts are usually lower: this assumes players always have the items and always sell.
     */
    static List<Ceiling> ceilings(MarketSettings settings, RecoverySettings recovery, Collection<MarketItem> items) {
        List<Ceiling> out = new ArrayList<>();
        if (!recovery.regenEnabled() || recovery.regenPercent() <= 0) return out;
        double stepsPerDay = 1440.0 / recovery.intervalMinutes();
        double keep = 1 - settings.taxPercent() / 100.0;
        double move = keep * settings.multiplier();   // each sale lowers the price by this share of itself
        double q = 1 - move;
        for (MarketItem item : items) {
            if (!item.allowSell() || !item.regen()) continue;
            double sales = Math.max(1.0, Math.ceil(item.maxStock() * recovery.regenPercent() / 100.0));
            double afterSales = Math.pow(q, sales);                       // price share left after one step of selling
            double sumOfShares = move > 0 ? (1 - afterSales) / move : sales; // 1 + q + q^2 ... over the step
            double settled;                                                // price share the market settles at
            if (recovery.driftEnabled() && item.drift() && recovery.driftPercent() > 0) {
                double d = recovery.driftPercent() / 100.0;
                settled = d / (1 - (1 - d) * afterSales);
            } else {
                settled = Math.min(1.0, settings.priceFloor() / item.basePrice());
            }
            double perStep = keep * item.basePrice() * settled * sumOfShares;
            out.add(new Ceiling(item.id(), perStep * stepsPerDay));
        }
        out.sort(Comparator.comparingDouble(Ceiling::perDay).reversed());
        return out;
    }

    private static String num(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        if (text.contains(".")) text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return text;
    }
}
