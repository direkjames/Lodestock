package io.github.direkjames.lodestock.core.audit;

import io.github.direkjames.lodestock.core.audit.EconomyAudit.Finding;
import io.github.direkjames.lodestock.core.audit.EconomyAudit.Level;
import io.github.direkjames.lodestock.core.audit.EconomyAudit.Report;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.MarketSettings;
import io.github.direkjames.lodestock.core.market.RecoverySettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EconomyAuditTest {
    private static final MarketSettings SAFE = new MarketSettings(10.0, 0.01, 0.25);

    private static MarketItem item(String id, double price) {
        return new MarketItem(id, price, 100, 1000, true, true);
    }

    private static boolean has(Report report, String code) {
        return report.findings().stream().anyMatch(f -> f.code().equals(code));
    }

    private static Report run(MarketSettings settings, RecoverySettings recovery, int dailyBuy, List<MarketItem> items,
                              Map<String, Double> live) {
        return EconomyAudit.run(settings, recovery, dailyBuy, 0, items, live);
    }

    @Test
    void aSafeSetupHasNoWarnings() {
        Report report = run(SAFE, RecoverySettings.DEFAULT, 64,
                List.of(item("iron_ingot", 8), item("iron_block", 72), item("diamond", 80)), null);
        assertEquals(0L, report.count(Level.WARN), report.findings().toString());
    }

    @Test
    void zeroTaxIsAWarningAndOpensARoundTripLoop() {
        MarketSettings noTax = new MarketSettings(0.0, 0.01, 0.25);
        Report report = run(noTax, RecoverySettings.DEFAULT, 64, List.of(item("diamond", 80)), null);
        assertTrue(has(report, "tax-zero"));
        assertTrue(has(report, "round-trip"));
    }

    @Test
    void sellingFirstAndBuyingBackIsCaught() {
        // Buy-then-sell loses money here, but sell-then-buy-back makes it.
        MarketSettings sneaky = new MarketSettings(5.0, 0.055, 0.25);
        assertTrue(sneaky.hasBuySellLoop());
        Report report = run(sneaky, RecoverySettings.OFF, 0, List.of(item("diamond", 80)), null);
        Finding loop = report.findings().stream().filter(f -> f.code().equals("round-trip")).findFirst().orElseThrow();
        assertEquals("sell-first", loop.args().get(1));
    }

    @Test
    void theDefaultSettingsHaveNoLoopAtAnyTradeSize() {
        MarketSettings defaults = new MarketSettings(10.0, 0.01, 0.25);
        List<MarketItem> items = List.of(
                new MarketItem("coal", 3, 200, 2000, true, true), new MarketItem("iron_ingot", 8, 150, 1500, true, true),
                new MarketItem("diamond", 80, 50, 500, true, true), new MarketItem("netherite_scrap", 150, 20, 200, true, true));
        assertEquals(List.of(), EconomyAudit.roundTrips(defaults, items));
    }

    @Test
    void aBigMultiplierIsExploitableInBulkEvenWhenSingleTradesLoseMoney() {
        MarketSettings big = new MarketSettings(10.0, 0.05, 0.25);
        assertFalse(big.hasBuySellLoop()); // one item at a time loses money...
        MarketItem diamond = new MarketItem("diamond", 80, 50, 500, true, true);
        List<Finding> found = EconomyAudit.roundTrips(big, List.of(diamond));
        assertEquals(1, found.size()); // ...but selling a big stack and buying it back does not
        assertEquals("sell-first", found.get(0).args().get(1));
    }

    @Test
    void whenTheQuickCheckSaysLoopTheRealTradesAgree() {
        MarketItem diamond = new MarketItem("diamond", 80, 50, 500, true, true);
        for (double tax = 0; tax <= 40; tax += 0.5) {
            for (double multiplier = 0; multiplier <= 0.2001; multiplier += 0.005) {
                MarketSettings settings = new MarketSettings(tax, multiplier, 0.25);
                if (!settings.hasBuySellLoop()) continue;
                double margin = Math.max(Math.pow(1 - tax / 100, 2) * multiplier - tax / 100,
                        (1 - tax / 100) * multiplier - tax / 100);
                if (margin < 0.002) continue; // too close to the edge to show up in whole cents
                assertEquals(1, EconomyAudit.roundTrips(settings, List.of(diamond)).size(),
                        "tax " + tax + " multiplier " + multiplier);
            }
        }
    }

    @Test
    void tradeSizesStartSmallGrowFastAndEndOnTheLimit() {
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 12, 17, 24, 34, 48, 68, 96, 100), EconomyAudit.amountsUpTo(100));
        assertEquals(List.of(1), EconomyAudit.amountsUpTo(1));
        assertEquals(List.of(1, 2, 3), EconomyAudit.amountsUpTo(3));
    }

    @Test
    void lowTaxMultiplierFloorAndRenewableNotesAreRaised() {
        Report report = run(new MarketSettings(2.0, 0.08, 0.01), RecoverySettings.DEFAULT, 0,
                List.of(item("coal", 3)), null);
        assertTrue(has(report, "tax-low"));
        assertTrue(has(report, "multiplier-high"));
        assertTrue(has(report, "floor-low"));
        assertTrue(has(report, "renewable-no-limits"));
    }

    @Test
    void aDailyLimitOnOneItemSilencesTheRenewableNote() {
        MarketItem limited = new MarketItem("coal", 3, 100, 1000, true, true, true, true, 64, -1);
        assertFalse(has(run(SAFE, RecoverySettings.DEFAULT, 0, List.of(limited), null), "renewable-no-limits"));
    }

    @Test
    void blocksPricedWrongOpenCraftingLoops() {
        // 9 ingots at 8 = 72. A block that sells for 81 after tax beats it.
        Report craft = run(SAFE, RecoverySettings.OFF, 64, List.of(item("iron_ingot", 8), item("iron_block", 90)), null);
        Finding f = craft.findings().stream().filter(x -> x.code().equals("crafting-loop")).findFirst().orElseThrow();
        assertEquals(List.of("iron_ingot", "iron_block", "craft"), f.args().subList(0, 3));

        // A block priced low can be bought, uncrafted into 9 ingots and sold: 9 * 7.2 = 64.8 > 50.
        Report uncraft = run(SAFE, RecoverySettings.OFF, 64, List.of(item("iron_ingot", 8), item("iron_block", 50)), null);
        Finding g = uncraft.findings().stream().filter(x -> x.code().equals("crafting-loop")).findFirst().orElseThrow();
        assertEquals(List.of("iron_block", "iron_ingot", "uncraft"), g.args().subList(0, 3));
    }

    @Test
    void smeltingOnlyWorksOneWay() {
        // Raw iron priced far below the ingot: buy raw, smelt, sell the ingot.
        Report cheapRaw = run(SAFE, RecoverySettings.OFF, 64, List.of(item("raw_iron", 4), item("iron_ingot", 8)), null);
        assertTrue(has(cheapRaw, "crafting-loop"));
        // Raw iron priced above the ingot cannot be un-smelted, so no loop.
        Report dearRaw = run(SAFE, RecoverySettings.OFF, 64, List.of(item("raw_iron", 12), item("iron_ingot", 8)), null);
        assertFalse(has(dearRaw, "crafting-loop"));
    }

    @Test
    void aCrashOnOneItemShowsUpAsARightNowLoop() {
        List<MarketItem> items = List.of(item("iron_ingot", 8), item("iron_block", 72));
        Report calm = run(SAFE, RecoverySettings.OFF, 64, items, Map.of("iron_ingot", 8.0, "iron_block", 72.0));
        assertEquals(0, calm.findings().size());
        Report crashed = run(SAFE, RecoverySettings.OFF, 64, items, Map.of("iron_ingot", 4.0, "iron_block", 72.0));
        Finding f = crashed.findings().stream().filter(x -> x.code().equals("crafting-loop-now")).findFirst().orElseThrow();
        assertEquals(Level.NOTE, f.level());
        assertEquals("iron_ingot", f.args().get(0));
    }

    @Test
    void ceilingsFollowRegenAndSkipItemsThatCantRegenOrSell() {
        MarketItem noRegen = new MarketItem("emerald", 40, 50, 500, true, true, true, false);
        MarketItem noSell = new MarketItem("netherite_scrap", 150, 20, 200, true, false);
        Report report = run(SAFE, RecoverySettings.DEFAULT, 64,
                List.of(item("diamond", 80), noRegen, noSell), null);
        assertEquals(1, report.ceilings().size());
        // 20 items a step are sold back. Selling pushes the price down and drift pulls it up, so it
        // settles near 11% of the base price: about 20,928 a day, far below the naive 20 * 144 * 72.
        assertEquals(20928.1, report.ceilings().get(0).perDay(), 1.0);
        assertEquals(report.ceilings().get(0).perDay(), report.totalCeiling(), 1e-6);
        // Without drift the price sinks to the floor and stays there.
        RecoverySettings noDrift = new RecoverySettings(10, 24, false, 2.0, true, 2.0);
        Report flat = run(SAFE, noDrift, 64, List.of(item("diamond", 80)), null);
        assertEquals(595.5, flat.ceilings().get(0).perDay(), 1.0);
        assertTrue(run(SAFE, RecoverySettings.OFF, 64, List.of(item("diamond", 80)), null).ceilings().isEmpty());
    }
}
