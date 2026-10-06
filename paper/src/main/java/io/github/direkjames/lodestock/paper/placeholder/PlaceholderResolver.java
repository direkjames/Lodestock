package io.github.direkjames.lodestock.paper.placeholder;

import io.github.direkjames.lodestock.core.stats.ItemStats;
import io.github.direkjames.lodestock.core.stats.Row;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.leaderboard.LeaderboardService;
import io.github.direkjames.lodestock.paper.util.ItemNames;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Turns a placeholder into text. It never waits for the database: leaderboard lines come from the
 * cache, and player numbers come from the lifetime stats in memory. No PlaceholderAPI code, so the
 * plugin loads fine without it.
 */
public final class PlaceholderResolver {
    private static final String NONE = "-";

    private final LodestockPlugin plugin;

    public PlaceholderResolver(LodestockPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return the text, or null if this is not a Lodestock placeholder */
    public String resolve(UUID player, String params) {
        PlaceholderRequest request = PlaceholderRequest.parse(params, LeaderboardService.MAX_RANK);
        if (request instanceof PlaceholderRequest.PlayerStat stat) return playerStat(player, stat);
        if (request instanceof PlaceholderRequest.Ranked ranked) return ranked(ranked);
        return null;
    }

    private String playerStat(UUID player, PlaceholderRequest.PlayerStat request) {
        if (player == null) return "";
        ItemStats stats = request.item() == null
                ? plugin.lifetime().total(player)
                : plugin.lifetime().item(player, request.item());
        double value;
        boolean money = true;
        switch (request.stat()) {
            case "earned" -> value = stats.soldMoney();
            case "spent" -> value = stats.boughtMoney();
            case "net" -> value = stats.net();
            case "best_trade" -> value = stats.bestMoney();
            case "trades" -> {
                value = stats.trades();
                money = false;
            }
            case "items_sold" -> {
                value = stats.soldItems();
                money = false;
            }
            case "items_bought" -> {
                value = stats.boughtItems();
                money = false;
            }
            default -> {
                return null;
            }
        }
        return show(value, money, request.formatted());
    }

    private String ranked(PlaceholderRequest.Ranked r) {
        if (r.item() != null && plugin.market().item(r.item()).isEmpty()) return NONE;
        List<Row> rows = plugin.leaderboards().cached(new LeaderboardService.Key(r.board(), r.period(), r.item()));
        if (r.rank() > rows.size()) return r.field().equals("value") ? "0" : NONE;
        Row row = rows.get(r.rank() - 1);
        return switch (r.field()) {
            case "name" -> row.player() == null ? NONE : row.player();
            case "value" -> show(row.value(), r.board().isMoney(), false);
            case "formatted" -> show(row.value(), r.board().isMoney(), true);
            case "action" -> row.action() == null ? NONE : row.action().toLowerCase(Locale.ROOT);
            case "amount" -> row.action() == null ? NONE : String.valueOf(row.amount());
            case "item" -> row.item() == null ? NONE : ItemNames.pretty(row.item());
            default -> NONE;
        };
    }

    /** Plain numbers for scripts and leaderboard plugins, or the server's currency format for display. */
    private String show(double value, boolean money, boolean formatted) {
        if (!money) return String.valueOf((long) value);
        if (formatted) return plugin.economy().format(value);
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
