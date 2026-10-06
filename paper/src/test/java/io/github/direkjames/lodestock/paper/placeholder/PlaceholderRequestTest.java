package io.github.direkjames.lodestock.paper.placeholder;

import io.github.direkjames.lodestock.core.stats.Board;
import io.github.direkjames.lodestock.core.stats.Period;
import io.github.direkjames.lodestock.paper.placeholder.PlaceholderRequest.PlayerStat;
import io.github.direkjames.lodestock.paper.placeholder.PlaceholderRequest.Ranked;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlaceholderRequestTest {
    @Test
    void playerStatsWithAnItemAndTheFormattedSuffix() {
        assertEquals(new PlayerStat("earned", null, false), PlaceholderRequest.parse("earned", 50));
        assertEquals(new PlayerStat("net", null, true), PlaceholderRequest.parse("net_formatted", 50));
        assertEquals(new PlayerStat("items_sold", "diamond", false), PlaceholderRequest.parse("items_sold_diamond", 50));
        assertEquals(new PlayerStat("earned", "netherite_scrap", true), PlaceholderRequest.parse("Earned_Netherite_Scrap_formatted", 50));
        assertEquals(new PlayerStat("best_trade", null, false), PlaceholderRequest.parse("best_trade", 50));
    }

    @Test
    void rankedLinesWithAndWithoutAnItem() {
        assertEquals(new Ranked(Board.SELLERS, Period.WEEK, null, 1, "name"),
                PlaceholderRequest.parse("top_sellers_7d_1_name", 50));
        assertEquals(new Ranked(Board.NET, Period.ALL, null, 10, "formatted"),
                PlaceholderRequest.parse("top_net_all_10_formatted", 50));
        assertEquals(new Ranked(Board.SELLERS, Period.DAY, "iron_ingot", 3, "value"),
                PlaceholderRequest.parse("top_iron_ingot_sellers_24h_3_value", 50));
        assertEquals(new Ranked(Board.BIGGEST, Period.MONTH, "diamond", 2, "amount"),
                PlaceholderRequest.parse("top_diamond_biggest_30d_2_amount", 50));
    }

    @Test
    void anythingElseIsNotOurs() {
        assertNull(PlaceholderRequest.parse(null, 50));
        assertNull(PlaceholderRequest.parse("", 50));
        assertNull(PlaceholderRequest.parse("netherite_scrap", 50));
        assertNull(PlaceholderRequest.parse("earned_", 50));
        assertNull(PlaceholderRequest.parse("top_sellers_7d_0_name", 50));   // ranks start at 1
        assertNull(PlaceholderRequest.parse("top_sellers_7d_51_name", 50));  // past the deepest rank
        assertNull(PlaceholderRequest.parse("top_sellers_7d_x_name", 50));
        assertNull(PlaceholderRequest.parse("top_nope_7d_1_name", 50));
        assertNull(PlaceholderRequest.parse("top_sellers_12h_1_name", 50));
        assertNull(PlaceholderRequest.parse("top_sellers_7d_1_color", 50));
        assertNull(PlaceholderRequest.parse("top_sellers_7d", 50));
    }
}
