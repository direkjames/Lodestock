package io.github.direkjames.lodestock.paper.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarketMenuTest {
    @Test
    void aNewMenuNeedsDrawingAndASyncedOneDoesNot() {
        MarketMenu menu = new MarketMenu();
        assertFalse(menu.upToDate(0, 0, 0, "2026-10-06"));
        menu.markSynced(7, 3, 5, "2026-10-06");
        assertTrue(menu.upToDate(7, 3, 5, "2026-10-06"));
    }

    @Test
    void anyChangedVersionOrANewDayMeansRedraw() {
        MarketMenu menu = new MarketMenu();
        menu.markSynced(7, 3, 5, "2026-10-06");
        assertFalse(menu.upToDate(8, 3, 5, "2026-10-06")); // a price or stock changed
        assertFalse(menu.upToDate(7, 4, 5, "2026-10-06")); // someone's daily counts changed
        assertFalse(menu.upToDate(7, 3, 5, "2026-10-07")); // the limits reset
        assertFalse(menu.upToDate(7, 3, 6, "2026-10-06")); // a new price sample was saved
    }

    @Test
    void shownViewsAreRememberedPerSlotAndForgottenWhenTheMenuIsRebuilt() {
        MarketMenu menu = new MarketMenu();
        MarketMenu.View view = new MarketMenu.View(10.0, 5, false, 3, 3, 12);
        menu.setShown(10, view);
        assertEquals(view, menu.shown(10));
        assertNull(menu.shown(11));
        assertNotEquals(view, new MarketMenu.View(10.5, 5, false, 3, 3, 12)); // a changed price is a different view

        menu.clearButtons();
        assertNull(menu.shown(10));
    }

    @Test
    void itemSlotsListsOnlyItemButtons() {
        MarketMenu menu = new MarketMenu();
        menu.put(10, new MarketMenu.Button(MarketMenu.Kind.ITEM, "diamond"));
        menu.put(45, new MarketMenu.Button(MarketMenu.Kind.PREV, null));
        menu.put(49, new MarketMenu.Button(MarketMenu.Kind.CLOSE, null));
        assertEquals(java.util.Map.of(10, "diamond"), menu.itemSlots());
    }
}
