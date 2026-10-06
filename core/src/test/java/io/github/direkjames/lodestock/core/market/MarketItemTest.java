package io.github.direkjames.lodestock.core.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarketItemTest {
    @Test
    void oldConstructorsUseTheDefaultLimits() {
        MarketItem item = new MarketItem("diamond", 100.0, 5, 50, true, true);
        assertEquals(-1, item.dailyBuy());
        assertEquals(-1, item.dailySell());
        assertTrue(item.drift());
        assertTrue(item.regen());
    }

    @Test
    void dailyLimitsCanBeZeroButNotBelowMinusOne() {
        MarketItem free = new MarketItem("diamond", 100.0, 5, 50, true, true, true, true, 0, 0);
        assertEquals(0, free.dailyBuy());
        assertThrows(IllegalArgumentException.class,
                () -> new MarketItem("diamond", 100.0, 5, 50, true, true, true, true, -2, -1));
    }
}
