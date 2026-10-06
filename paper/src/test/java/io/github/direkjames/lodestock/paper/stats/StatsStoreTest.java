package io.github.direkjames.lodestock.paper.stats;

import io.github.direkjames.lodestock.core.stats.Board;
import io.github.direkjames.lodestock.core.stats.ItemStats;
import io.github.direkjames.lodestock.core.stats.LifetimeStats;
import io.github.direkjames.lodestock.paper.log.TradeLog;
import io.github.direkjames.lodestock.paper.storage.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsStoreTest {
    @TempDir
    Path dir;

    private static final UUID ANN = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private Database open() throws Exception {
        return new Database(dir.resolve("test.db").toFile(), Logger.getLogger("test"));
    }

    private static LifetimeStats load(Database db) {
        LifetimeStats loaded = new LifetimeStats();
        db.<Boolean>query(c -> {
            StatsStore.loadInto(c, loaded);
            return true;
        }).join();
        return loaded;
    }

    @Test
    void tradesAreSavedAndSurviveARestart() throws Exception {
        Database db = open();
        LifetimeStats live = new LifetimeStats();
        TradeLog log = new TradeLog(db, live);
        log.recordTrade(1000, ANN, "Ann", "BUY", "diamond", 1, 80);
        log.recordTrade(2000, ANN, "Ann", "SELL", "diamond", 10, 700);
        log.recordTrade(3000, ANN, "AnnNew", "SELL", "coal", 100, 250);
        log.recordTrade(2500, BOB, "Bob", "SELL", "coal", 200, 500);
        db.close();

        Database again = open();
        LifetimeStats loaded = load(again);
        ItemStats total = loaded.total(ANN);
        assertEquals(live.total(ANN), total);
        assertEquals(950.0, total.soldMoney(), 1e-9);
        assertEquals(700.0, total.bestMoney(), 1e-9);
        assertEquals("SELL", total.bestAction());
        assertEquals("AnnNew", loaded.top(Board.SELLERS, null, 5).get(0).player());
        assertEquals(live.top(Board.NET, null, 5), loaded.top(Board.NET, null, 5));
        again.close();
    }

    @Test
    void backfillRebuildsStatsFromTradesAlreadyInTheDatabase() throws Exception {
        Database db = open();
        // Trades written straight into the table, as an older version would have left them.
        String sql = "INSERT INTO trades (time, player_uuid, player_name, action, item, amount, money) VALUES (?, ?, ?, ?, ?, ?, ?)";
        Object[][] rows = {
                {1000L, ANN.toString(), "AnnOld", "BUY", "diamond", 1, 80.0},
                {2000L, ANN.toString(), "Ann", "SELL", "diamond", 10, 700.0},
                {2500L, BOB.toString(), "Bob", "SELL", "coal", 200, 500.0}};
        db.<Boolean>query(c -> {
            for (Object[] r : rows) {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setLong(1, (Long) r[0]);
                    ps.setString(2, (String) r[1]);
                    ps.setString(3, (String) r[2]);
                    ps.setString(4, (String) r[3]);
                    ps.setString(5, (String) r[4]);
                    ps.setInt(6, (Integer) r[5]);
                    ps.setDouble(7, (Double) r[6]);
                    ps.executeUpdate();
                }
            }
            StatsStore.backfill(c);
            return true;
        }).join();

        LifetimeStats loaded = load(db);
        assertEquals(620.0, loaded.top(Board.NET, null, 5).get(0).value(), 1e-9); // Ann: 700 - 80
        assertEquals("Ann", loaded.top(Board.SELLERS, null, 5).get(0).player()); // the newest name
        assertEquals(10, loaded.item(ANN, "diamond").soldItems());
        assertEquals(2, loaded.total(ANN).trades());
        assertEquals(500.0, loaded.item(BOB, "coal").soldMoney(), 1e-9);
        db.close();
    }
}
