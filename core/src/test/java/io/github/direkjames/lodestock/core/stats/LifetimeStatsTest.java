package io.github.direkjames.lodestock.core.stats;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LifetimeStatsTest {
    private static final UUID ANN = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAT = UUID.randomUUID();

    private static LifetimeStats sample() {
        LifetimeStats s = new LifetimeStats();
        s.record(ANN, "Ann", "BUY", "diamond", 1, 80, 1000);
        s.record(ANN, "Ann", "SELL", "diamond", 10, 700, 2000);
        s.record(ANN, "Ann", "SELL", "coal", 100, 250, 3000);
        s.record(BOB, "Bob", "SELL", "coal", 200, 500, 2500);
        s.record(CAT, "Cat", "BUY", "diamond", 5, 410, 2600);
        return s;
    }

    @Test
    void sellersRankByMoneyEarnedAndSkipPeopleWhoNeverSold() {
        List<Row> rows = sample().top(Board.SELLERS, null, 10);
        assertEquals(List.of("Ann", "Bob"), rows.stream().map(Row::player).toList());
        assertEquals(950.0, rows.get(0).value(), 1e-9);
    }

    @Test
    void spendersActiveAndNetAreRanked() {
        LifetimeStats s = sample();
        assertEquals(List.of("Cat", "Ann"), s.top(Board.SPENDERS, null, 10).stream().map(Row::player).toList());
        assertEquals(List.of("Ann", "Bob", "Cat"), s.top(Board.ACTIVE, null, 10).stream().map(Row::player).toList());
        assertEquals(3.0, s.top(Board.ACTIVE, null, 10).get(0).value(), 1e-9);
        // net: Ann 950-80=870, Bob 500, Cat -410 (left out, nothing earned)
        assertEquals(List.of("Ann", "Bob"), s.top(Board.NET, null, 10).stream().map(Row::player).toList());
        assertEquals(870.0, s.top(Board.NET, null, 10).get(0).value(), 1e-9);
    }

    @Test
    void anItemFilterOnlyCountsThatItem() {
        List<Row> coal = sample().top(Board.SELLERS, "coal", 10);
        assertEquals(List.of("Bob", "Ann"), coal.stream().map(Row::player).toList());
        assertEquals(500.0, coal.get(0).value(), 1e-9);
        assertTrue(sample().top(Board.SELLERS, "emerald", 10).isEmpty());
    }

    @Test
    void biggestTradeShowsWhatTheTradeWas() {
        List<Row> rows = sample().top(Board.BIGGEST, null, 10);
        assertEquals("Ann", rows.get(0).player());
        assertEquals(700.0, rows.get(0).value(), 1e-9);
        assertEquals("SELL", rows.get(0).action());
        assertEquals("diamond", rows.get(0).item());
        assertEquals(10, rows.get(0).amount());
        assertEquals(2000L, rows.get(0).time());
        assertEquals("Bob", rows.get(1).player());
        assertEquals("Cat", rows.get(2).player());
    }

    @Test
    void limitCutsTheListAndTiesAreAlphabetical() {
        LifetimeStats s = new LifetimeStats();
        s.record(BOB, "Bob", "SELL", "coal", 1, 10, 1);
        s.record(ANN, "Ann", "SELL", "coal", 1, 10, 2);
        s.record(CAT, "Cat", "SELL", "coal", 1, 10, 3);
        assertEquals(List.of("Ann", "Bob"), s.top(Board.SELLERS, null, 2).stream().map(Row::player).toList());
        assertTrue(s.top(Board.SELLERS, null, 0).isEmpty());
    }

    @Test
    void theNewestNameWins() {
        LifetimeStats s = new LifetimeStats();
        s.record(ANN, "Ann", "SELL", "coal", 1, 10, 1);
        s.record(ANN, "AnnNew", "SELL", "coal", 1, 10, 5);
        s.record(ANN, "AnnOld", "SELL", "coal", 1, 10, 3); // an older trade must not rename her back
        assertEquals("AnnNew", s.top(Board.SELLERS, null, 1).get(0).player());
    }

    @Test
    void totalsAndPerItemStatsAddUp() {
        LifetimeStats s = sample();
        ItemStats total = s.total(ANN);
        assertEquals(110, total.soldItems());
        assertEquals(950.0, total.soldMoney(), 1e-9);
        assertEquals(1, total.boughtItems());
        assertEquals(3, total.trades());
        assertEquals(700.0, total.bestMoney(), 1e-9);
        assertEquals(10, s.item(ANN, "diamond").soldItems());
        assertEquals(ItemStats.EMPTY, s.item(ANN, "emerald"));
        assertEquals(ItemStats.EMPTY, s.total(UUID.randomUUID()));
    }

    @Test
    void loadedStatsMergeWithNewTrades() {
        LifetimeStats s = new LifetimeStats();
        s.load(ANN, "Ann", "coal", new ItemStats(10, 100, 0, 0, 2, 60, "SELL", 6, 50, 60));
        s.record(ANN, "Ann", "SELL", "coal", 5, 70, 100);
        ItemStats coal = s.item(ANN, "coal");
        assertEquals(15, coal.soldItems());
        assertEquals(170.0, coal.soldMoney(), 1e-9);
        assertEquals(3, coal.trades());
        assertEquals(70.0, coal.bestMoney(), 1e-9);
        assertEquals(5, coal.bestAmount());
    }

    @Test
    void boardAndPeriodNamesAreParsedLeniently() {
        assertEquals(Board.NET, Board.parse(" Net ").orElseThrow());
        assertTrue(Board.parse("nope").isEmpty());
        assertEquals(Period.WEEK, Period.parse("7D").orElseThrow());
        assertEquals(Period.ALL, Period.parse("all").orElseThrow());
        assertTrue(Period.parse("12h").isEmpty());
        assertEquals(24 * 30, Period.MONTH.hours());
    }
}
