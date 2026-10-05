package io.github.direkjames.lodestock.paper.storage;

import io.github.direkjames.lodestock.core.market.ItemState;
import io.github.direkjames.lodestock.paper.log.TradeLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseTest {
    @TempDir
    Path dir;

    private final Logger logger = Logger.getLogger("test");

    private Database open() throws Exception {
        return new Database(dir.resolve("test.db").toFile(), logger);
    }

    @Test
    void pricesAndStockSurviveARestart() throws Exception {
        Database db = open();
        new SqlMarketStorage(db).save("diamond", new ItemState(123.45, 7));
        db.close();

        Database again = open();
        ItemState state = new SqlMarketStorage(again).load("diamond").orElseThrow();
        assertEquals(123.45, state.price(), 1e-9);
        assertEquals(7, state.stock());
        again.close();
    }

    @Test
    void theLatestSaveWins() throws Exception {
        Database db = open();
        SqlMarketStorage storage = new SqlMarketStorage(db);
        storage.save("diamond", new ItemState(1.0, 1));
        storage.save("diamond", new ItemState(2.0, 2));
        storage.save("diamond", new ItemState(3.0, 3));
        db.close();

        Database again = open();
        ItemState state = new SqlMarketStorage(again).load("diamond").orElseThrow();
        assertEquals(3.0, state.price(), 1e-9);
        assertEquals(3, state.stock());
        again.close();
    }

    @Test
    void manyQuickWritesAllArrive() throws Exception {
        Database db = open();
        SqlMarketStorage storage = new SqlMarketStorage(db);
        for (int i = 0; i < 1000; i++) {
            storage.save("item" + i, new ItemState(i + 1, i));
        }
        db.close();

        Database again = open();
        SqlMarketStorage reloaded = new SqlMarketStorage(again);
        assertEquals(1000.0, reloaded.load("item999").orElseThrow().price(), 1e-9);
        assertEquals(500, reloaded.load("item500").orElseThrow().stock());
        assertEquals(0, reloaded.load("item0").orElseThrow().stock());
        again.close();
    }

    @Test
    void historyIsNewestFirstAndPerPlayer() throws Exception {
        Database db = open();
        TradeLog tradeLog = new TradeLog(db);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        tradeLog.recordTrade(1000, alice, "Alice", "BUY", "diamond", 1, 80.0);
        tradeLog.recordTrade(2000, alice, "Alice", "SELL", "iron_ingot", 5, 36.0);
        tradeLog.recordTrade(3000, bob, "Bob", "BUY", "coal", 2, 6.0);

        List<TradeLog.Entry> entries = tradeLog.readAsync(alice).join();
        assertEquals(2, entries.size());
        assertEquals("SELL", entries.get(0).action());
        assertEquals("BUY", entries.get(1).action());
        assertEquals(5, entries.get(0).amount());
        assertEquals(1, tradeLog.readAsync(bob).join().size());
        db.close();
    }

    @Test
    void pruneRemovesOnlyOldLines() throws Exception {
        Database db = open();
        TradeLog tradeLog = new TradeLog(db);
        UUID alice = UUID.randomUUID();
        long now = System.currentTimeMillis();
        tradeLog.recordTrade(now - 40L * 86_400_000L, alice, "Alice", "BUY", "diamond", 1, 80.0);
        tradeLog.recordTrade(now - 1000L, alice, "Alice", "SELL", "diamond", 1, 70.0);
        tradeLog.prune(30);

        List<TradeLog.Entry> entries = tradeLog.readAsync(alice).join();
        assertEquals(1, entries.size());
        assertEquals("SELL", entries.get(0).action());
        db.close();
    }
}