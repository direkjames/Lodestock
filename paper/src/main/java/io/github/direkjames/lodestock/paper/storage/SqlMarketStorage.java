package io.github.direkjames.lodestock.paper.storage;

import io.github.direkjames.lodestock.core.market.ItemState;
import io.github.direkjames.lodestock.core.market.MarketStorage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Live prices and stock. Kept in memory for speed, and written to the database on every change. */
public final class SqlMarketStorage implements MarketStorage {
    private final Database db;
    private final Map<String, ItemState> cache = new HashMap<>();

    /** Reads everything once at startup (blocks briefly). */
    public SqlMarketStorage(Database db) {
        this.db = db;
        cache.putAll(db.query(SqlMarketStorage::loadAll).join());
    }

    private static Map<String, ItemState> loadAll(Connection c) throws SQLException {
        Map<String, ItemState> map = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, price, stock FROM items")) {
            while (rs.next()) {
                map.put(rs.getString(1), new ItemState(rs.getDouble(2), rs.getInt(3)));
            }
        }
        return map;
    }

    @Override
    public synchronized Optional<ItemState> load(String itemId) {
        return Optional.ofNullable(cache.get(itemId));
    }

    @Override
    public synchronized void save(String itemId, ItemState state) {
        cache.put(itemId, state);
        db.write("save " + itemId, c -> upsert(c, itemId, state));
    }

    @Override
    public synchronized void saveAll(Map<String, ItemState> states) {
        cache.putAll(states);
        Map<String, ItemState> copy = new HashMap<>(states);
        db.write("save all items", c -> {
            for (Map.Entry<String, ItemState> entry : copy.entrySet()) {
                upsert(c, entry.getKey(), entry.getValue());
            }
        });
    }

    private static void upsert(Connection c, String id, ItemState state) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO items (id, price, stock) VALUES (?, ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET price = excluded.price, stock = excluded.stock")) {
            ps.setString(1, id);
            ps.setDouble(2, state.price());
            ps.setInt(3, state.stock());
            ps.executeUpdate();
        }
    }
}