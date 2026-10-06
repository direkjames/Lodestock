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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiMappingTest {
    @Test
    void everyInnerNameHasAPublicPartnerAndTheOtherWayRound() {
        for (Quote.Status s : Quote.Status.values()) assertEquals(s.name(), ApiMapping.status(s).name());
        for (QuoteStatus s : QuoteStatus.values()) assertEquals(s.name(), Quote.Status.valueOf(s.name()).name());
        for (LeaderboardType t : LeaderboardType.values()) assertEquals(t.name(), ApiMapping.board(t).name());
        for (Board b : Board.values()) assertEquals(b.name(), LeaderboardType.valueOf(b.name()).name());
        for (LeaderboardPeriod p : LeaderboardPeriod.values()) assertEquals(p.name(), ApiMapping.period(p).name());
        for (Period p : Period.values()) assertEquals(p.name(), LeaderboardPeriod.valueOf(p.name()).name());
    }

    @Test
    void statsAndLeaderboardLinesKeepTheirNumbers() {
        PlayerStats stats = ApiMapping.stats(new ItemStats(10, 700, 1, 80, 2, 700, "SELL", 10, 5, 6));
        assertEquals(700.0, stats.earned(), 1e-9);
        assertEquals(80.0, stats.spent(), 1e-9);
        assertEquals(620.0, stats.net(), 1e-9);
        assertEquals(2, stats.trades());
        assertEquals(10, stats.itemsSold());
        assertEquals(1, stats.itemsBought());
        assertEquals(700.0, stats.bestTrade(), 1e-9);

        List<LeaderboardEntry> entries = ApiMapping.entries(List.of(Row.simple("Ann", 9), Row.simple("Bob", 4)));
        assertEquals(new LeaderboardEntry(1, "Ann", 9), entries.get(0));
        assertEquals(new LeaderboardEntry(2, "Bob", 4), entries.get(1));
    }
}
