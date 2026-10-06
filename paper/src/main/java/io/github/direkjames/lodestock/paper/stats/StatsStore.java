package io.github.direkjames.lodestock.paper.stats;

import io.github.direkjames.lodestock.core.stats.ItemStats;
import io.github.direkjames.lodestock.core.stats.LifetimeStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Saves and loads the lifetime stats (table player_item_stats). Everything here runs on the database thread. */
public final class StatsStore {
    private StatsStore() {}

    public static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS player_item_stats (player_uuid TEXT NOT NULL, item TEXT NOT NULL, "
                    + "player_name TEXT NOT NULL, sold_items INTEGER NOT NULL DEFAULT 0, sold_money REAL NOT NULL DEFAULT 0, "
                    + "bought_items INTEGER NOT NULL DEFAULT 0, bought_money REAL NOT NULL DEFAULT 0, "
                    + "trades INTEGER NOT NULL DEFAULT 0, best_money REAL NOT NULL DEFAULT 0, best_action TEXT, "
                    + "best_amount INTEGER NOT NULL DEFAULT 0, best_time INTEGER NOT NULL DEFAULT 0, "
                    + "last_time INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (player_uuid, item))";

    private static final String UPSERT =
            "INSERT INTO player_item_stats (player_uuid, item, player_name, sold_items, sold_money, bought_items, "
                    + "bought_money, trades, best_money, best_action, best_amount, best_time, last_time) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(player_uuid, item) DO UPDATE SET "
                    + "player_name = CASE WHEN excluded.last_time >= last_time THEN excluded.player_name ELSE player_name END, "
                    + "sold_items = sold_items + excluded.sold_items, sold_money = sold_money + excluded.sold_money, "
                    + "bought_items = bought_items + excluded.bought_items, bought_money = bought_money + excluded.bought_money, "
                    + "trades = trades + 1, "
                    + "best_action = CASE WHEN excluded.best_money > best_money THEN excluded.best_action ELSE best_action END, "
                    + "best_amount = CASE WHEN excluded.best_money > best_money THEN excluded.best_amount ELSE best_amount END, "
                    + "best_time = CASE WHEN excluded.best_money > best_money THEN excluded.best_time ELSE best_time END, "
                    + "best_money = MAX(best_money, excluded.best_money), "
                    + "last_time = MAX(last_time, excluded.last_time)";

    /** Adds one trade to the saved stats. {@code action} is BUY or SELL. */
    public static void upsert(Connection c, UUID player, String name, String action, String item,
                              int amount, double money, long time) throws SQLException {
        boolean sell = action.equals("SELL");
        try (PreparedStatement ps = c.prepareStatement(UPSERT)) {
            ps.setString(1, player.toString());
            ps.setString(2, item);
            ps.setString(3, name);
            ps.setInt(4, sell ? amount : 0);
            ps.setDouble(5, sell ? money : 0);
            ps.setInt(6, sell ? 0 : amount);
            ps.setDouble(7, sell ? 0 : money);
            ps.setDouble(8, money);
            ps.setString(9, action);
            ps.setInt(10, amount);
            ps.setLong(11, time);
            ps.setLong(12, time);
            ps.executeUpdate();
        }
    }

    /** Reads every saved row into memory. */
    public static void loadInto(Connection c, LifetimeStats stats) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT player_uuid, item, player_name, sold_items, sold_money, bought_items, "
                     + "bought_money, trades, best_money, best_action, best_amount, best_time, last_time FROM player_item_stats")) {
            while (rs.next()) {
                UUID id;
                try {
                    id = UUID.fromString(rs.getString(1));
                } catch (IllegalArgumentException e) {
                    continue; // a damaged row is skipped, not fatal
                }
                stats.load(id, rs.getString(3), rs.getString(2), new ItemStats(
                        rs.getInt(4), rs.getDouble(5), rs.getInt(6), rs.getDouble(7), rs.getInt(8),
                        rs.getDouble(9), rs.getString(10), rs.getInt(11), rs.getLong(12), rs.getLong(13)));
            }
        }
    }

    /**
     * Builds the stats from the trades already in the database, oldest first. Used once, when the table
     * is first created, so trades made before the upgrade still count.
     */
    public static void backfill(Connection c) throws SQLException {
        record Key(String player, String item) {}
        Map<Key, ItemStats> totals = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT time, player_uuid, player_name, action, item, amount, money "
                     + "FROM trades ORDER BY time, id")) {
            while (rs.next()) {
                Key key = new Key(rs.getString(2), rs.getString(5));
                totals.merge(key, ItemStats.EMPTY.after(rs.getString(4), rs.getInt(6), rs.getDouble(7), rs.getLong(1)),
                        ItemStats::plus);
                names.put(rs.getString(2), rs.getString(3)); // oldest first, so the newest name ends up here
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR REPLACE INTO player_item_stats (player_uuid, item, player_name, sold_items, sold_money, "
                        + "bought_items, bought_money, trades, best_money, best_action, best_amount, best_time, last_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (Map.Entry<Key, ItemStats> e : totals.entrySet()) {
                ItemStats s = e.getValue();
                ps.setString(1, e.getKey().player());
                ps.setString(2, e.getKey().item());
                ps.setString(3, names.get(e.getKey().player()));
                ps.setInt(4, s.soldItems());
                ps.setDouble(5, s.soldMoney());
                ps.setInt(6, s.boughtItems());
                ps.setDouble(7, s.boughtMoney());
                ps.setInt(8, s.trades());
                ps.setDouble(9, s.bestMoney());
                ps.setString(10, s.bestAction());
                ps.setInt(11, s.bestAmount());
                ps.setLong(12, s.bestTime());
                ps.setLong(13, s.lastTime());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
