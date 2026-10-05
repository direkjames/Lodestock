package io.github.direkjames.lodestock.paper.log;

import io.github.direkjames.lodestock.paper.storage.Database;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Trade history and the admin log, stored in the database. */
public final class TradeLog {
    public record Entry(long time, String player, String action, String item, int amount, double money) {}

    private final Database db;

    public TradeLog(Database db) {
        this.db = db;
    }

    public void trade(Player player, String action, String itemId, int amount, double money) {
        recordTrade(System.currentTimeMillis(), player.getUniqueId(), player.getName(), action, itemId, amount, money);
    }

    /** Same as trade(), but without needing a Player. */
    public void recordTrade(long time, UUID uuid, String name, String action, String itemId, int amount, double money) {
        String id = uuid.toString();
        db.write("log trade", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO trades (time, player_uuid, player_name, action, item, amount, money) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, time);
                ps.setString(2, id);
                ps.setString(3, name);
                ps.setString(4, action);
                ps.setString(5, itemId);
                ps.setInt(6, amount);
                ps.setDouble(7, money);
                ps.executeUpdate();
            }
        });
    }

    public void admin(String who, String action, String details) {
        long time = System.currentTimeMillis();
        db.write("log admin action", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO admin_log (time, who, action, details) VALUES (?, ?, ?, ?)")) {
                ps.setLong(1, time);
                ps.setString(2, who);
                ps.setString(3, action);
                ps.setString(4, details);
                ps.executeUpdate();
            }
        });
    }

    /** A player's trades, newest first. Gives an empty list if the database can't be read. */
    public CompletableFuture<List<Entry>> readAsync(UUID uuid) {
        String id = uuid.toString();
        return db.<List<Entry>>query(c -> {
            List<Entry> entries = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT time, player_name, action, item, amount, money FROM trades "
                            + "WHERE player_uuid = ? ORDER BY time DESC, id DESC")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new Entry(rs.getLong(1), rs.getString(2), rs.getString(3),
                                rs.getString(4), rs.getInt(5), rs.getDouble(6)));
                    }
                }
            }
            return entries;
        }).exceptionally(error -> List.of());
    }

    /** Deletes lines older than the given number of days. 0 or less keeps everything. */
    public void prune(int keepDays) {
        if (keepDays <= 0) return;
        long cutoff = System.currentTimeMillis() - keepDays * 86_400_000L;
        db.write("clean up old history", c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM trades WHERE time < ?")) {
                ps.setLong(1, cutoff);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM admin_log WHERE time < ?")) {
                ps.setLong(1, cutoff);
                ps.executeUpdate();
            }
        });
    }
}