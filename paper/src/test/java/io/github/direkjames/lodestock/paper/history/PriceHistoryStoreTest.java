package io.github.direkjames.lodestock.paper.history;

import io.github.direkjames.lodestock.core.history.PricePoint;
import io.github.direkjames.lodestock.paper.storage.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceHistoryStoreTest {
    @TempDir
    Path dir;

    private Database open() throws Exception {
        return new Database(dir.resolve("test.db").toFile(), Logger.getLogger("test"));
    }

    private static PriceHistoryStore.Sample sample(String item, long time, double price, int stock) {
        return new PriceHistoryStore.Sample(item, new PricePoint(time, price, stock));
    }

    @Test
    void samplesComeBackOldestFirstAndOnlyForTheAskedItemAndTime() throws Exception {
        Database db = open();
        PriceHistoryStore store = new PriceHistoryStore(db);
        store.add(List.of(sample("diamond", 3000, 3.0, 3), sample("diamond", 1000, 1.0, 1),
                sample("gold_ingot", 2000, 9.0, 9)));
        store.add(List.of(sample("diamond", 2000, 2.0, 2)));

        List<PricePoint> all = store.read("diamond", 0).join();
        assertEquals(List.of(1.0, 2.0, 3.0), all.stream().map(PricePoint::price).toList());
        List<PricePoint> recent = store.read("diamond", 2000).join();
        assertEquals(List.of(2.0, 3.0), recent.stream().map(PricePoint::price).toList());
        assertTrue(store.read("emerald", 0).join().isEmpty());
        db.close();
    }

    @Test
    void samplesSurviveARestartAndReadAllGroupsThemByItem() throws Exception {
        Database db = open();
        new PriceHistoryStore(db).add(List.of(sample("diamond", 1000, 1.0, 1), sample("gold_ingot", 1000, 5.0, 5),
                sample("diamond", 2000, 2.0, 2)));
        db.close();

        Database again = open();
        var map = new PriceHistoryStore(again).readAll(0).join();
        assertEquals(2, map.get("diamond").size());
        assertEquals(1, map.get("gold_ingot").size());
        again.close();
    }

    @Test
    void pruneDeletesOnlyOldSamplesAndZeroKeepsEverything() throws Exception {
        Database db = open();
        PriceHistoryStore store = new PriceHistoryStore(db);
        long now = System.currentTimeMillis();
        store.add(List.of(sample("diamond", now - 20L * 86_400_000L, 1.0, 1), sample("diamond", now - 1000, 2.0, 2)));

        store.prune(0);
        assertEquals(2, store.read("diamond", 0).join().size());
        store.prune(14);
        List<PricePoint> left = store.read("diamond", 0).join();
        assertEquals(1, left.size());
        assertEquals(2.0, left.get(0).price(), 1e-9);
        db.close();
    }
}
