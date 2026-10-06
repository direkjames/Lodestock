package io.github.direkjames.lodestock.core.market;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Many threads trading, previewing, adjusting and recovering at once: nothing may be lost, torn or thrown. */
class MarketConcurrencyTest {
    private static final int THREADS = 16;
    private static final int ROUNDS = 3000;

    @Test
    void manyTradersAtOnceKeepTheCountsAndPricesSane() throws Exception {
        MarketSettings settings = new MarketSettings(10.0, 0.01, 0.5);
        List<MarketItem> items = List.of(
                new MarketItem("coal", 3.0, 1_000_000, 2_000_000, true, true),
                new MarketItem("diamond", 100.0, 1_000_000, 2_000_000, true, true));
        Market market = new Market(settings, items, new InMemoryMarketStorage());
        long versionBefore = market.version();

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            final int seed = t;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < ROUNDS; i++) {
                        String id = ((i + seed) & 1) == 0 ? "coal" : "diamond";
                        market.quoteBuy(id);
                        market.previewBuy(id, 1 + (i % 64));
                        market.previewSell(id, 1 + (i % 64));
                        market.recordBuy(id, 1 + (i % 3));
                        market.recordSell(id, 1 + (i % 3)); // every buy is matched by a sell of the same size
                        if (seed == 0 && i % 500 == 0) market.applyRecovery(1);
                        if (seed == 1 && i % 700 == 0) market.state(id);
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }, "trader-" + t);
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join(60_000);

        assertEquals(null, failure.get() == null ? null : failure.get().toString());
        for (MarketItem item : items) {
            ItemState s = market.state(item.id()).orElseThrow();
            assertEquals(item.startStock(), s.stock(), item.id() + " stock");
            assertTrue(Double.isFinite(s.price()), item.id() + " price is finite");
            assertTrue(s.price() >= settings.priceFloor(), item.id() + " price is above the floor");
        }
        // two changes (buy and sell) per round per thread, plus the recovery steps that changed something
        assertTrue(market.version() - versionBefore >= (long) THREADS * ROUNDS * 2, "every change was counted");
    }

    @Test
    void adminChangesAndTradingAtOnceDoNotBreakAnything() throws Exception {
        MarketSettings settings = new MarketSettings(10.0, 0.01, 0.5);
        Market market = new Market(settings, List.of(new MarketItem("iron_ingot", 5.0, 500_000, 1_000_000, true, true)),
                new InMemoryMarketStorage());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            final boolean admin = t == 0;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 2000; i++) {
                        if (admin) {
                            if (i % 3 == 0) market.adjustPrices(null, i % 2 == 0 ? -20 : 25);
                            else if (i % 3 == 1) market.setPrice("iron_ingot", 5.0 + (i % 10));
                            else market.setStock("iron_ingot", 400_000 + i);
                        } else {
                            market.recordBuy("iron_ingot", 1);
                            market.recordSell("iron_ingot", 2);
                            market.previewSell("iron_ingot", 16);
                        }
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join(60_000);
        assertEquals(null, failure.get() == null ? null : failure.get().toString());
        ItemState s = market.state("iron_ingot").orElseThrow();
        assertTrue(Double.isFinite(s.price()) && s.price() >= settings.priceFloor());
        assertTrue(s.stock() >= 0);
    }
}
