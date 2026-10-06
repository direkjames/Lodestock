package io.github.direkjames.lodestock.paper.history;

import io.github.direkjames.lodestock.core.history.PricePoint;
import io.github.direkjames.lodestock.paper.storage.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Saved price samples, in the database. Writes are queued, reads see every earlier write. */
public final class PriceHistoryStore {
    public record Sample(String item, PricePoint point) {}

    private final Database db;

    public PriceHistoryStore(Database db) {
        this.db = db;
    }

    /** Queues the samples for saving. Returns immediately. */
    public void add(List<Sample> samples) {
        if (samples.isEmpty()) return;
        List<Sample> copy = List.copyOf(samples);
        db.write("save price history", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO price_history (item, time, price, stock) VALUES (?, ?, ?, ?)")) {
                for (Sample s : copy) {
                    ps.setString(1, s.item());
                    ps.setLong(2, s.point().time());
                    ps.setDouble(3, s.point().price());
                    ps.setInt(4, s.point().stock());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    /** One item's samples since {@code since} (epoch millis), oldest first. Empty if the database can't be read. */
    public CompletableFuture<List<PricePoint>> read(String item, long since) {
        return db.<List<PricePoint>>query(c -> {
            List<PricePoint> points = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT time, price, stock FROM price_history WHERE item = ? AND time >= ? ORDER BY time, rowid")) {
                ps.setString(1, item);
                ps.setLong(2, since);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) points.add(new PricePoint(rs.getLong(1), rs.getDouble(2), rs.getInt(3)));
                }
            }
            return points;
        }).exceptionally(error -> List.of());
    }

    /** Every item's samples since {@code since}, oldest first. Used once at startup to fill the memory cache. */
    public CompletableFuture<Map<String, List<PricePoint>>> readAll(long since) {
        return db.<Map<String, List<PricePoint>>>query(c -> {
            Map<String, List<PricePoint>> map = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT item, time, price, stock FROM price_history WHERE time >= ? ORDER BY time, rowid")) {
                ps.setLong(1, since);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        map.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                                .add(new PricePoint(rs.getLong(2), rs.getDouble(3), rs.getInt(4)));
                    }
                }
            }
            return map;
        }).exceptionally(error -> Map.of());
    }

    /** Deletes samples older than the given number of days. 0 or less keeps everything. */
    public void prune(int keepDays) {
        if (keepDays <= 0) return;
        long cutoff = System.currentTimeMillis() - keepDays * 86_400_000L;
        db.write("clean up old price history", c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM price_history WHERE time < ?")) {
                ps.setLong(1, cutoff);
                ps.executeUpdate();
            }
        });
    }
}
