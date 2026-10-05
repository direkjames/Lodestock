package io.github.direkjames.lodestock.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingTest {
    @Test
    void taxIsRemoved() {
        assertEquals(90.0, Pricing.afterTax(100.0, 10.0), 0.0001);
    }

    @Test
    void priceNeverDropsBelowFloor() {
        assertEquals(0.01, Pricing.nextPrice(0.02, 5.0, 1.0, false, 0.01), 0.0001);
    }

    @Test
    void buyingRaisesPrice() {
        assertEquals(10.1, Pricing.nextPrice(10.0, 10.0, 0.01, true, 0.01), 0.0001);
    }
}