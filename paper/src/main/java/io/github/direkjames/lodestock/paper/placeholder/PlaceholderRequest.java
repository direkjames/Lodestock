package io.github.direkjames.lodestock.paper.placeholder;

import io.github.direkjames.lodestock.core.stats.Board;
import io.github.direkjames.lodestock.core.stats.Period;

import java.util.List;
import java.util.Locale;

/**
 * Reads the text after "%lodestock_" in a placeholder. No Minecraft or PlaceholderAPI code, so it can be tested.
 * <ul>
 *   <li>{@code earned}, {@code spent}, {@code net}, {@code trades}, {@code items_sold}, {@code items_bought},
 *       {@code best_trade}: one player's all-time numbers. Add {@code _<item>} for one item, and
 *       {@code _formatted} at the end for money in the server's currency format.</li>
 *   <li>{@code top_[<item>_]<board>_<period>_<rank>_<field>}: one line of a leaderboard.</li>
 * </ul>
 */
public sealed interface PlaceholderRequest {
    /** The stats a placeholder can show for a player, most specific names first. */
    List<String> STATS = List.of("items_bought", "items_sold", "best_trade", "earned", "spent", "net", "trades");
    List<String> FIELDS = List.of("name", "value", "formatted", "action", "amount", "item");

    /** {@code item} is null for all items. */
    record PlayerStat(String stat, String item, boolean formatted) implements PlaceholderRequest {}

    /** {@code item} is null for all items. */
    record Ranked(Board board, Period period, String item, int rank, String field) implements PlaceholderRequest {}

    /** Returns null if the text is not a Lodestock placeholder. */
    static PlaceholderRequest parse(String params, int maxRank) {
        if (params == null) return null;
        String text = params.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("top_")) return parseRanked(text.substring(4), maxRank);

        boolean formatted = false;
        if (text.endsWith("_formatted")) {
            formatted = true;
            text = text.substring(0, text.length() - "_formatted".length());
        }
        for (String stat : STATS) {
            if (text.equals(stat)) return new PlayerStat(stat, null, formatted);
            if (text.startsWith(stat + "_") && text.length() > stat.length() + 1) {
                return new PlayerStat(stat, text.substring(stat.length() + 1), formatted);
            }
        }
        return null;
    }

    private static PlaceholderRequest parseRanked(String text, int maxRank) {
        // board_period_rank_field, with an item (which may contain underscores) in front.
        String[] parts = text.split("_");
        if (parts.length < 4) return null;
        int n = parts.length;
        String field = parts[n - 1];
        if (!FIELDS.contains(field)) return null;
        int rank;
        try {
            rank = Integer.parseInt(parts[n - 2]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (rank < 1 || rank > maxRank) return null;
        Period period = Period.parse(parts[n - 3]).orElse(null);
        Board board = Board.parse(parts[n - 4]).orElse(null);
        if (period == null || board == null) return null;
        String item = n > 4 ? String.join("_", java.util.Arrays.copyOfRange(parts, 0, n - 4)) : null;
        return new Ranked(board, period, item, rank, field);
    }
}
