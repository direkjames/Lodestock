package io.github.direkjames.lodestock.paper.limits;

import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.storage.Database;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How much each player has bought and sold today, per item. Counts live in memory while a player
 * is online (loaded when they log in) and are saved to the database on every trade, so they
 * survive restarts and relogging.
 */
public final class DailyLimits implements Listener {
    /** Returned by remaining...() when there is no limit. */
    public static final int UNLIMITED = Integer.MAX_VALUE;
    public static final String BYPASS = "lodestock.limit.bypass";

    private static final class PlayerData {
        String day;
        final Map<String, int[]> items = new HashMap<>(); // item -> {bought, sold}
    }

    private final LodestockPlugin plugin;
    private final Database db;
    private volatile LimitSettings settings = LimitSettings.off();
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public DailyLimits(LodestockPlugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void configure(LimitSettings settings) {
        this.settings = settings;
    }

    public LimitSettings settings() {
        return settings;
    }

    /** Loads players who are already online (for example after a plugin reload) and removes old rows. */
    public void start() {
        String oldest = LocalDate.parse(settings.dayKey(Instant.now())).minusDays(3).toString();
        db.write("clean up old daily usage", c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM daily_usage WHERE day < ?")) {
                ps.setString(1, oldest);
                ps.executeUpdate();
            }
        });
        for (Player player : plugin.getServer().getOnlinePlayers()) load(player.getUniqueId());
    }

    /** The limit for this item and direction. 0 means unlimited. */
    public int limitFor(MarketItem item, boolean buy) {
        int own = buy ? item.dailyBuy() : item.dailySell();
        if (own >= 0) return own;
        return buy ? settings.dailyBuy() : settings.dailySell();
    }

    public int remainingBuy(Player player, MarketItem item) {
        return remaining(player, item, true);
    }

    public int remainingSell(Player player, MarketItem item) {
        return remaining(player, item, false);
    }

    /** How many more the player may buy or sell today, or {@link #UNLIMITED}. */
    public int remaining(Player player, MarketItem item, boolean buy) {
        int limit = limitFor(item, buy);
        if (limit <= 0 || player.hasPermission(BYPASS)) return UNLIMITED;
        int[] used = data(player.getUniqueId()).items.get(item.id());
        return Math.max(0, limit - (used == null ? 0 : used[buy ? 0 : 1]));
    }

    public void recordBuy(Player player, MarketItem item, int count) {
        record(player, item, count, true);
    }

    public void recordSell(Player player, MarketItem item, int count) {
        record(player, item, count, false);
    }

    /** "12/50" for a limited item, "unlimited" otherwise. */
    public String describe(int left, int limit) {
        return left == UNLIMITED || limit <= 0 ? "unlimited" : left + "/" + limit;
    }

    public String resetsIn() {
        return settings.resetsIn(Instant.now());
    }

    private void record(Player player, MarketItem item, int count, boolean buy) {
        if (count <= 0 || limitFor(item, buy) <= 0) return;
        UUID uuid = player.getUniqueId();
        PlayerData data = data(uuid);
        int[] used = data.items.computeIfAbsent(item.id(), k -> new int[2]);
        used[buy ? 0 : 1] += count;

        String id = uuid.toString();
        String day = data.day;
        String itemId = item.id();
        int bought = used[0];
        int sold = used[1];
        db.write("save daily usage", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO daily_usage (player_uuid, item, day, bought, sold) VALUES (?, ?, ?, ?, ?) "
                            + "ON CONFLICT(player_uuid, item, day) DO UPDATE SET "
                            + "bought = excluded.bought, sold = excluded.sold")) {
                ps.setString(1, id);
                ps.setString(2, itemId);
                ps.setString(3, day);
                ps.setInt(4, bought);
                ps.setInt(5, sold);
                ps.executeUpdate();
            }
        });
    }

    private PlayerData data(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) data = load(uuid);
        String today = settings.dayKey(Instant.now());
        if (!today.equals(data.day)) { // a new limit day began: everybody starts again from zero
            data.day = today;
            data.items.clear();
        }
        return data;
    }

    private PlayerData load(UUID uuid) {
        PlayerData data = new PlayerData();
        data.day = settings.dayKey(Instant.now());
        String id = uuid.toString();
        String day = data.day;
        try {
            Map<String, int[]> rows = db.query(c -> {
                Map<String, int[]> map = new HashMap<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT item, bought, sold FROM daily_usage WHERE player_uuid = ? AND day = ?")) {
                    ps.setString(1, id);
                    ps.setString(2, day);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) map.put(rs.getString(1), new int[]{rs.getInt(2), rs.getInt(3)});
                    }
                }
                return map;
            }).join();
            data.items.putAll(rows);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not read the daily limits of " + id + ": " + e.getMessage());
        }
        cache.put(uuid, data);
        return data;
    }

    // Loading happens before the player joins, on a background thread, so the server never waits for it.
    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        load(event.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }
}
