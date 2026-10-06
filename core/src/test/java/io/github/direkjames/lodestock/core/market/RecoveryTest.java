package io.github.direkjames.lodestock.core.market;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryTest {
    private static final MarketSettings SETTINGS = new MarketSettings(10.0, 0.01, 0.01);
    private static final RecoverySettings RECOVERY = new RecoverySettings(10, 24, true, 10.0, true, 10.0);

    private static Market market(MarketItem item, MarketStorage storage) {
        return new Market(SETTINGS, RECOVERY, List.of(item), storage);
    }

    @Test
    void priceClosesAPercentOfTheGapEachStep() {
        assertEquals(90.0 + 10.0 * 0.9, Recovery.driftPrice(100.0, 90.0, 10.0, 1, 0.01), 1e-9);
        assertEquals(90.0 + 10.0 * 0.81, Recovery.driftPrice(100.0, 90.0, 10.0, 2, 0.01), 1e-9);
    }

    @Test
    void severalStepsAtOnceEqualOneStepRepeated() {
        double price = 150.0;
        for (int i = 0; i < 5; i++) price = Recovery.driftPrice(price, 100.0, 7.0, 1, 0.01);
        assertEquals(price, Recovery.driftPrice(150.0, 100.0, 7.0, 5, 0.01), 1e-9);
    }

    @Test
    void driftWorksBothWaysAndSnapsToBase() {
        assertTrue(Recovery.driftPrice(50.0, 100.0, 10.0, 1, 0.01) > 50.0);
        assertEquals(100.0, Recovery.driftPrice(100.01, 100.0, 10.0, 1, 0.01), 0.0);
        assertEquals(100.0, Recovery.driftPrice(150.0, 100.0, 10.0, 500, 0.01), 0.0);
    }

    @Test
    void driftNeverGoesBelowTheFloor() {
        assertEquals(0.5, Recovery.driftPrice(0.5, 0.2, 0.0, 3, 0.5), 0.0);
        assertTrue(Recovery.driftPrice(0.2, 0.1, 50.0, 1, 0.5) >= 0.5);
    }

    @Test
    void stockMovesTowardTheTargetWithoutPassingIt() {
        assertEquals(110, Recovery.regenStock(100, 200, 1000, 1.0, 1)); // 1% of 1000 = 10
        assertEquals(200, Recovery.regenStock(195, 200, 1000, 1.0, 1));
        assertEquals(200, Recovery.regenStock(300, 200, 1000, 10.0, 1)); // 10% of 1000 = 100, stops at the target
        assertEquals(290, Recovery.regenStock(300, 200, 1000, 1.0, 1));
        assertEquals(200, Recovery.regenStock(300, 200, 1000, 10.0, 5));
        assertEquals(200, Recovery.regenStock(200, 200, 1000, 1.0, 3)); // already at target
    }

    @Test
    void regenMovesAtLeastOneItem() {
        assertEquals(6, Recovery.regenStock(5, 100, 10, 1.0, 1)); // 1% of 10 rounds up to 1
    }

    @Test
    void marketRecoversAfterACrash() {
        MarketItem diamond = new MarketItem("diamond", 100.0, 50, 500, true, true);
        Market m = market(diamond, new InMemoryMarketStorage());
        m.adjustPrices("diamond", -50);
        m.setStock("diamond", 0);
        m.recordBuy("diamond", 1); // a trade clears the hold, stock stays 0
        m.recordSell("diamond", 1);
        ItemState before = m.state("diamond").orElseThrow();
        assertEquals(1, m.applyRecovery(1));
        ItemState after = m.state("diamond").orElseThrow();
        assertTrue(after.price() > before.price());
        assertTrue(after.stock() > before.stock());
    }

    @Test
    void adminSetValuesAreHeldUntilTheNextTrade() {
        MarketItem diamond = new MarketItem("diamond", 100.0, 50, 500, true, true);
        Market m = market(diamond, new InMemoryMarketStorage());
        m.setPrice("diamond", 10.0);
        m.setStock("diamond", 5);
        assertEquals(0, m.applyRecovery(3));
        assertEquals(10.0, m.state("diamond").orElseThrow().price(), 0.0);
        assertEquals(5, m.state("diamond").orElseThrow().stock());

        m.recordBuy("diamond");
        assertFalse(m.state("diamond").orElseThrow().priceHeld());
        assertFalse(m.state("diamond").orElseThrow().stockHeld());
        assertEquals(1, m.applyRecovery(1));
    }

    @Test
    void settingOnlyThePriceKeepsStockRegenerating() {
        MarketItem diamond = new MarketItem("diamond", 100.0, 50, 500, true, true);
        Market m = market(diamond, new InMemoryMarketStorage());
        m.setStock("diamond", 10);
        m.setPrice("diamond", 10.0);
        m.recordSell("diamond", 1); // releases both
        m.setPrice("diamond", 10.0); // holds the price only
        m.applyRecovery(1);
        ItemState s = m.state("diamond").orElseThrow();
        assertEquals(10.0, s.price(), 0.0);
        assertTrue(s.stock() > 11);
    }

    @Test
    void crashAndSurgeFadeEvenAfterSetPrice() {
        MarketItem diamond = new MarketItem("diamond", 100.0, 50, 500, true, true);
        Market m = market(diamond, new InMemoryMarketStorage());
        m.setPrice("diamond", 100.0);
        m.adjustPrices("diamond", -50);
        assertFalse(m.state("diamond").orElseThrow().priceHeld());
        m.applyRecovery(1);
        assertEquals(50.0 + 50.0 * 0.1, m.state("diamond").orElseThrow().price(), 1e-9);
    }

    @Test
    void itemsCanOptOut() {
        MarketItem steady = new MarketItem("diamond", 100.0, 50, 500, true, true, false, false);
        Market m = market(steady, new InMemoryMarketStorage());
        m.adjustPrices("diamond", -50);
        m.recordBuy("diamond", 1);
        assertEquals(0, m.applyRecovery(10));
    }

    @Test
    void recoveryIsOffByDefaultWithTheOldConstructor() {
        MarketItem diamond = new MarketItem("diamond", 100.0, 50, 500, true, true);
        Market m = new Market(SETTINGS, List.of(diamond), new InMemoryMarketStorage());
        m.adjustPrices("diamond", -50);
        assertEquals(0, m.applyRecovery(10));
    }

    @Test
    void resetClearsHolds() {
        MarketItem diamond = new MarketItem("diamond", 100.0, 50, 500, true, true);
        Market m = market(diamond, new InMemoryMarketStorage());
        m.setPrice("diamond", 10.0);
        m.reset("diamond");
        assertFalse(m.state("diamond").orElseThrow().priceHeld());
    }

    @Test
    void invalidSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RecoverySettings(0, 24, true, 2, true, 2));
        assertThrows(IllegalArgumentException.class, () -> new RecoverySettings(10, 24, true, 101, true, 2));
        assertThrows(IllegalArgumentException.class, () -> new RecoverySettings(10, -1, true, 2, true, 2));
    }
}
