package io.github.direkjames.lodestock.core.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player, per-item totals for the whole life of the server, kept in memory and saved by the platform.
 * It is small (one entry per player and item they traded) and it is never deleted, so all-time leaderboards
 * do not need the full trade history. No Minecraft code.
 */
public final class LifetimeStats {
    private static final class PlayerEntry {
        String name;
        long nameTime = Long.MIN_VALUE;
        final Map<String, ItemStats> items = new HashMap<>();
    }

    private final Map<UUID, PlayerEntry> players = new HashMap<>();

    /** Adds one trade. {@code action} is BUY or SELL. */
    public synchronized void record(UUID player, String name, String action, String item,
                                    int amount, double money, long time) {
        PlayerEntry entry = players.computeIfAbsent(player, k -> new PlayerEntry());
        rename(entry, name, time);
        entry.items.merge(item, ItemStats.EMPTY.after(action, amount, money, time), ItemStats::plus);
    }

    /** Loads saved stats at startup. */
    public synchronized void load(UUID player, String name, String item, ItemStats stats) {
        PlayerEntry entry = players.computeIfAbsent(player, k -> new PlayerEntry());
        rename(entry, name, stats.lastTime());
        entry.items.merge(item, stats, ItemStats::plus);
    }

    private static void rename(PlayerEntry entry, String name, long time) {
        if (name != null && time >= entry.nameTime) {
            entry.name = name;
            entry.nameTime = time;
        }
    }

    public synchronized int playerCount() {
        return players.size();
    }

    /** Everything one player did, across all items. {@link ItemStats#EMPTY} if they never traded. */
    public synchronized ItemStats total(UUID player) {
        PlayerEntry entry = players.get(player);
        if (entry == null) return ItemStats.EMPTY;
        ItemStats sum = ItemStats.EMPTY;
        for (ItemStats stats : entry.items.values()) sum = sum.plus(stats);
        return sum;
    }

    /** What one player did with one item. */
    public synchronized ItemStats item(UUID player, String item) {
        PlayerEntry entry = players.get(player);
        if (entry == null) return ItemStats.EMPTY;
        return entry.items.getOrDefault(item, ItemStats.EMPTY);
    }

    /**
     * The best players on a board, highest first. {@code item} limits it to one item (null means all items).
     * Players with nothing to show (a value of 0 or less) are left out. Ties go in alphabetical order.
     */
    public synchronized List<Row> top(Board board, String item, int limit) {
        List<Row> rows = new ArrayList<>();
        for (PlayerEntry entry : players.values()) {
            ItemStats stats = item == null ? sum(entry) : entry.items.getOrDefault(item, ItemStats.EMPTY);
            double value = switch (board) {
                case SELLERS -> stats.soldMoney();
                case SPENDERS -> stats.boughtMoney();
                case ACTIVE -> stats.trades();
                case NET -> stats.net();
                case BIGGEST -> stats.bestMoney();
            };
            if (value <= 0) continue;
            if (board == Board.BIGGEST) {
                rows.add(new Row(entry.name, value, stats.bestAction(), bestItem(entry, item, value),
                        stats.bestAmount(), stats.bestTime()));
            } else {
                rows.add(Row.simple(entry.name, value));
            }
        }
        rows.sort(Comparator.comparingDouble(Row::value).reversed()
                .thenComparing(Row::player, String.CASE_INSENSITIVE_ORDER));
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, Math.max(0, limit))) : rows;
    }

    private static ItemStats sum(PlayerEntry entry) {
        ItemStats sum = ItemStats.EMPTY;
        for (ItemStats stats : entry.items.values()) sum = sum.plus(stats);
        return sum;
    }

    /** The item of a player's single biggest trade. */
    private static String bestItem(PlayerEntry entry, String item, double bestMoney) {
        if (item != null) return item;
        String found = null;
        for (Map.Entry<String, ItemStats> e : entry.items.entrySet()) {
            if (e.getValue().bestMoney() == bestMoney && (found == null || e.getKey().compareTo(found) < 0)) {
                found = e.getKey();
            }
        }
        return found;
    }
}
