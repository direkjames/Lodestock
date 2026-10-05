package io.github.direkjames.lodestock.core.market;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketTest {
    private final MarketSettings settings = new MarketSettings(10.0, 0.01, 0.01);
    private final MarketItem diamond = new MarketItem("minecraft:diamond", "overworld", 100.0, 2, 3, true, true);

    private Market market(MarketStorage storage) {
        return new Market(settings, List.of(diamond), storage);
    }

    @Test
    void buyingRaisesPriceAndLowersStock() {
        Market m = market(new InMemoryMarketStorage());
        assertEquals(100.0, m.quoteBuy("minecraft:diamond").price(), 1e-9);
        m.recordBuy("minecraft:diamond");
        ItemState s = m.state("minecraft:diamond").orElseThrow();
        assertEquals(100.9, s.price(), 1e-9);
        assertEquals(1, s.stock());
    }

    @Test
    void sellingLowersPriceAndRaisesStockUntilFull() {
        Market m = market(new InMemoryMarketStorage());
        assertEquals(90.0, m.quoteSell("minecraft:diamond").price(), 1e-9);
        m.recordSell("minecraft:diamond");
        assertEquals(99.1, m.state("minecraft:diamond").orElseThrow().price(), 1e-9);
        assertEquals(Quote.Status.STOCK_FULL, m.quoteSell("minecraft:diamond").status());
    }

    @Test
    void outOfStockCantBeBought() {
        MarketItem empty = new MarketItem("minecraft:diamond", "overworld", 100.0, 0, 3, true, true);
        Market m = new Market(settings, List.of(empty), new InMemoryMarketStorage());
        assertEquals(Quote.Status.OUT_OF_STOCK, m.quoteBuy("minecraft:diamond").status());
    }

    @Test
    void unknownItemIsRejected() {
        assertEquals(Quote.Status.UNKNOWN_ITEM, market(new InMemoryMarketStorage()).quoteBuy("minecraft:dirt").status());
    }

    @Test
    void stateSurvivesRestart() {
        InMemoryMarketStorage storage = new InMemoryMarketStorage();
        Market first = market(storage);
        first.recordBuy("minecraft:diamond");
        Market second = market(storage);
        assertEquals(1, second.state("minecraft:diamond").orElseThrow().stock());
    }

    @Test
    void lowTaxIsFlaggedAsLoop() {
        assertTrue(new MarketSettings(0.0, 0.01, 0.01).hasBuySellLoop());
        assertFalse(new MarketSettings(10.0, 0.01, 0.01).hasBuySellLoop());
    }
}