package io.github.direkjames.lodestock.paper.log;

import io.github.direkjames.lodestock.paper.storage.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeLogTest {
    @TempDir
    Path dir;

    private static final UUID ANN = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID OLD = UUID.randomUUID();

    @Test
    void summaryAddsUpMoneyInAndOutAndRanksEarnersAndItems() throws Exception {
        Database db = new Database(dir.resolve("test.db").toFile(), Logger.getLogger("test"));
        TradeLog log = new TradeLog(db);
        log.recordTrade(1000, ANN, "Ann", "BUY", "diamond", 1, 80);
        log.recordTrade(2000, ANN, "Ann", "SELL", "diamond", 10, 700);
        log.recordTrade(2500, BOB, "Bob", "SELL", "coal", 100, 200);
        log.recordTrade(10, OLD, "Old", "SELL", "coal", 1, 999); // before the cutoff

        TradeLog.Summary s = log.summaryAsync(100, 5).join();
        assertEquals(3, s.trades());
        assertEquals(2, s.players());
        assertEquals(80.0, s.paidIn(), 1e-9);
        assertEquals(900.0, s.paidOut(), 1e-9);
        assertEquals(820.0, s.created(), 1e-9);
        assertEquals("Ann", s.topEarners().get(0).player());
        assertEquals(620.0, s.topEarners().get(0).net(), 1e-9);
        assertEquals("Bob", s.topEarners().get(1).player());
        assertEquals("diamond", s.topItems().get(0).item());
        assertEquals(700.0, s.topItems().get(0).paidOut(), 1e-9);
        db.close();
    }

    @Test
    void anEmptyPeriodGivesZeroes() throws Exception {
        Database db = new Database(dir.resolve("empty.db").toFile(), Logger.getLogger("test"));
        TradeLog.Summary s = new TradeLog(db).summaryAsync(0, 5).join();
        assertEquals(0, s.trades());
        assertTrue(s.topEarners().isEmpty());
        assertTrue(s.topItems().isEmpty());
        db.close();
    }
}
