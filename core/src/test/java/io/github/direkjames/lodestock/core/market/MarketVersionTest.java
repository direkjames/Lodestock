package io.github.direkjames.lodestock.core.market;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketVersionTest {
    private static final MarketSettings SETTINGS = new MarketSettings(10.0, 0.01, 0.01);
    private static final RecoverySettings RECOVERY = new RecoverySettings(10, 24, true, 10.0, true, 10.0);
    private final MarketItem diamond = new MarketItem("diamond", 100.0, 5, 50, true, true);

    private Market market() {
        return new Market(SETTINGS, RECOVERY, List.of(diamond), new InMemoryMarketStorage());
    }

    @Test
    void readingNeverChangesTheVersion() {
        Market m = market();
        long before = m.version();
        m.quoteBuy("diamond");
        m.previewSell("diamond", 3);
        m.state("diamond");
        assertEquals(before, m.version());
    }

    @Test
    void everyKindOfChangeRaisesTheVersion() {
        Market m = market();
        long v = m.version();
        m.recordBuy("diamond");
        assertTrue(m.version() > v);
        v = m.version();
        m.recordSell("diamond");
        assertTrue(m.version() > v);
        v = m.version();
        m.setPrice("diamond", 50.0);
        assertTrue(m.version() > v);
        v = m.version();
        m.setStock("diamond", 10);
        assertTrue(m.version() > v);
        v = m.version();
        m.adjustPrices(null, -10);
        assertTrue(m.version() > v);
        v = m.version();
        m.reset("diamond");
        assertTrue(m.version() > v);
        v = m.version();
        m.resetAll();
        assertTrue(m.version() > v);
    }

    @Test
    void recoveryOnlyRaisesTheVersionWhenSomethingChanged() {
        Market m = market();
        long v = m.version();
        assertEquals(0, m.applyRecovery(1)); // already at base price and start stock
        assertEquals(v, m.version());

        m.adjustPrices("diamond", -50);
        v = m.version();
        assertEquals(1, m.applyRecovery(1));
        assertTrue(m.version() > v);
    }
}
