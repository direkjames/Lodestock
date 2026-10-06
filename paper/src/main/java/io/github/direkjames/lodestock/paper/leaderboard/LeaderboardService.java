package io.github.direkjames.lodestock.paper.leaderboard;

import io.github.direkjames.lodestock.core.stats.Board;
import io.github.direkjames.lodestock.core.stats.LifetimeStats;
import io.github.direkjames.lodestock.core.stats.Period;
import io.github.direkjames.lodestock.core.stats.Row;
import io.github.direkjames.lodestock.paper.LodestockPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Leaderboards for chat and for placeholders. All time comes from the lifetime stats in memory; 24h, 7d
 * and 30d come from the trade history. Placeholders are asked for many times a second, so they read a
 * cache that refreshes in the background and never wait for the database.
 */
public final class LeaderboardService {
    /** The deepest rank a placeholder can show, and how many rows the cache keeps. */
    public static final int MAX_RANK = 50;

    public record Key(Board board, Period period, String item) {}

    private record Cached(List<Row> rows, long loadedAt) {}

    private final LodestockPlugin plugin;
    private final LifetimeStats lifetime;
    private final Map<Key, Cached> cache = new ConcurrentHashMap<>();
    private final Set<Key> loading = ConcurrentHashMap.newKeySet();

    public LeaderboardService(LodestockPlugin plugin, LifetimeStats lifetime) {
        this.plugin = plugin;
        this.lifetime = lifetime;
    }

    /** How many lines the chat command shows (leaderboards.size, 1 to 50). */
    public int size() {
        return Math.max(1, Math.min(MAX_RANK, plugin.getConfig().getInt("leaderboards.size", 10)));
    }

    private long cacheMillis() {
        return Math.max(5, plugin.getConfig().getInt("leaderboards.cache-seconds", 30)) * 1000L;
    }

    /** Fresh rows, for the chat command. {@code item} is null for all items. */
    public CompletableFuture<List<Row>> fetch(Board board, Period period, String item, int limit) {
        if (period == Period.ALL) return CompletableFuture.completedFuture(lifetime.top(board, item, limit));
        long since = System.currentTimeMillis() - period.hours() * 3_600_000L;
        return plugin.tradeLog().topAsync(board, since, item, limit);
    }

    /**
     * Rows for a placeholder: whatever was loaded last, never waiting. The first call for a board returns an
     * empty list while it loads (all time loads at once). Old rows are refreshed in the background.
     */
    public List<Row> cached(Key key) {
        long now = System.currentTimeMillis();
        Cached entry = cache.get(key);
        boolean stale = entry == null || now - entry.loadedAt() > cacheMillis();
        if (stale && loading.add(key)) {
            if (key.period() == Period.ALL) {
                try {
                    entry = new Cached(lifetime.top(key.board(), key.item(), MAX_RANK), now);
                    cache.put(key, entry);
                } finally {
                    loading.remove(key);
                }
            } else {
                fetch(key.board(), key.period(), key.item(), MAX_RANK).whenComplete((rows, error) -> {
                    if (error == null) cache.put(key, new Cached(rows, System.currentTimeMillis()));
                    loading.remove(key);
                });
            }
        }
        return entry == null ? List.of() : entry.rows();
    }
}
