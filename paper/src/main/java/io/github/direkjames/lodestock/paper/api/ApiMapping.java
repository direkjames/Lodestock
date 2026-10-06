package io.github.direkjames.lodestock.paper.api;

import io.github.direkjames.lodestock.api.LeaderboardEntry;
import io.github.direkjames.lodestock.api.LeaderboardPeriod;
import io.github.direkjames.lodestock.api.LeaderboardType;
import io.github.direkjames.lodestock.api.PlayerStats;
import io.github.direkjames.lodestock.api.QuoteStatus;
import io.github.direkjames.lodestock.core.market.Quote;
import io.github.direkjames.lodestock.core.stats.Board;
import io.github.direkjames.lodestock.core.stats.ItemStats;
import io.github.direkjames.lodestock.core.stats.Period;
import io.github.direkjames.lodestock.core.stats.Row;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns Lodestock's inner types into the public API types. The public enums use the same names as the
 * inner ones, and a test checks that every name has a partner, so adding one without the other fails the build.
 */
final class ApiMapping {
    private ApiMapping() {}

    static QuoteStatus status(Quote.Status status) {
        return QuoteStatus.valueOf(status.name());
    }

    static Board board(LeaderboardType type) {
        return Board.valueOf(type.name());
    }

    static Period period(LeaderboardPeriod period) {
        return Period.valueOf(period.name());
    }

    static PlayerStats stats(ItemStats s) {
        return new PlayerStats(s.soldMoney(), s.boughtMoney(), s.trades(), s.soldItems(), s.boughtItems(), s.bestMoney());
    }

    static List<LeaderboardEntry> entries(List<Row> rows) {
        List<LeaderboardEntry> out = new ArrayList<>(rows.size());
        int rank = 1;
        for (Row row : rows) out.add(new LeaderboardEntry(rank++, String.valueOf(row.player()), row.value()));
        return out;
    }
}
