package io.github.direkjames.lodestock.core.market;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketTest {
    private final MarketSettings settings = new MarketSettings(10.0, 0.01, 0.01);
    private final MarketItem diamond = new MarketItem("diamond", 100.0, 2, 3, true, true);

    private Market market(MarketStorage storage) {
        return new Market(settings, List.of(diamond), storage);
    }

    @Test
    void buyingRaisesPriceAndLowersStock() {
        Market m = market(new InMemoryMarketStorage());
        assertEquals(100.0, m.quoteBuy("diamond").price(), 1e-9);
        m.recordBuy("diamond");
        ItemState s = m.state("diamond").orElseThrow();
        assertEquals(100.9, s.price(), 1e-9);
        assertEquals(1, s.stock());
    }

    @Test
    void sellingLowersPriceAndRaisesStockUntilFull() {
        Market m = market(new InMemoryMarketStorage());
        assertEquals(90.0, m.quoteSell("diamond").price(), 1e-9);
        m.recordSell("diamond");
        assertEquals(99.1, m.state("diamond").orElseThrow().price(), 1e-9);
        assertEquals(Quote.Status.STOCK_FULL, m.quoteSell("diamond").status());
    }

    @Test
    void outOfStockCantBeBought() {
        MarketItem empty = new MarketItem("diamond", 100.0, 0, 3, true, true);
        Market m = new Market(settings, List.of(empty), new InMemoryMarketStorage());
        assertEquals(Quote.Status.OUT_OF_STOCK, m.quoteBuy("diamond").status());
    }

    @Test
    void unknownItemIsRejected() {
        assertEquals(Quote.Status.UNKNOWN_ITEM, market(new InMemoryMarketStorage()).quoteBuy("dirt").status());
    }

    @Test
    void stateSurvivesRestart() {
        InMemoryMarketStorage storage = new InMemoryMarketStorage();
        market(storage).recordBuy("diamond");
        assertEquals(1, market(storage).state("diamond").orElseThrow().stock());
    }

    @Test
    void lowTaxIsFlaggedAsLoop() {
        assertTrue(new MarketSettings(0.0, 0.01, 0.01).hasBuySellLoop());
        assertFalse(new MarketSettings(10.0, 0.01, 0.01).hasBuySellLoop());
    }

    @Test
    void sellingFirstAndBuyingBackCountsAsALoopToo() {
        // Buy-then-sell loses money at 5% tax and a 5.5% multiplier, but sell-then-buy-back does not.
        assertTrue(new MarketSettings(5.0, 0.055, 0.01).hasBuySellLoop());
    }

    @Test
    void bulkBuyIsLimitedByStockAndPricesEachItem() {
        Market m = market(new InMemoryMarketStorage());
        BulkQuote q = m.previewBuy("diamond", 5);
        assertEquals(2, q.count());
        assertEquals(200.9, q.total(), 1e-9);
        assertEquals(2, m.state("diamond").orElseThrow().stock()); // preview changes nothing
    }

    @Test
    void bulkSellIsLimitedByMaxStock() {
        Market m = market(new InMemoryMarketStorage());
        BulkQuote q = m.previewSell("diamond", 5);
        assertEquals(1, q.count());
        assertEquals(90.0, q.total(), 1e-9);
    }

    @Test
    void recordBuyManyMovesPriceAndStock() {
        Market m = market(new InMemoryMarketStorage());
        m.recordBuy("diamond", 2);
        ItemState s = m.state("diamond").orElseThrow();
        assertEquals(0, s.stock());
        assertEquals(101.8081, s.price(), 1e-6);
    }

    @Test
    void adminSetPriceAndStockAreValidated() {
        Market m = market(new InMemoryMarketStorage());
        m.setPrice("diamond", 50.0);
        m.setStock("diamond", 3);
        assertEquals(50.0, m.state("diamond").orElseThrow().price(), 1e-9);
        assertEquals(3, m.state("diamond").orElseThrow().stock());
        assertThrows(IllegalArgumentException.class, () -> m.setPrice("diamond", 0.001));
        assertThrows(IllegalArgumentException.class, () -> m.setStock("diamond", 4));
        assertThrows(IllegalArgumentException.class, () -> m.setStock("diamond", -1));
    }

    @Test
    void resetRestoresBaseValues() {
        Market m = market(new InMemoryMarketStorage());
        m.recordBuy("diamond");
        m.reset("diamond");
        ItemState s = m.state("diamond").orElseThrow();
        assertEquals(100.0, s.price(), 1e-9);
        assertEquals(2, s.stock());
    }

    @Test
    void crashAndSurgeChangePricesButRespectTheFloor() {
        Market m = market(new InMemoryMarketStorage());
        assertEquals(1, m.adjustPrices(null, -20));
        assertEquals(80.0, m.state("diamond").orElseThrow().price(), 1e-9);
        m.adjustPrices("diamond", 50);
        assertEquals(120.0, m.state("diamond").orElseThrow().price(), 1e-9);
        m.adjustPrices(null, -99.99999);
        assertEquals(0.01, m.state("diamond").orElseThrow().price(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> m.adjustPrices(null, -100));
    }
}