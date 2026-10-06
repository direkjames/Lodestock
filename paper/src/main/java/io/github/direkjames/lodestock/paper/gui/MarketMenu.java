package io.github.direkjames.lodestock.paper.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/** Marks an inventory as the Lodestock menu and remembers what each slot does. */
public final class MarketMenu implements InventoryHolder {
    public enum Kind { ITEM, PREV, NEXT, CLOSE }

    public record Button(Kind kind, String target) {}

    /** What an item icon currently shows for this viewer. If it has not changed, the icon is not redrawn. */
    public record View(double price, int stock, boolean locked, int buyLeft, int sellLeft) {}

    private final Map<Integer, Button> buttons = new HashMap<>();
    private final Map<Integer, View> shown = new HashMap<>();
    private long marketVersion = -1;
    private long limitsVersion = -1;
    private String day = "";
    private int page = 0;
    private Inventory inventory;
    private Player viewer;

    public int page() { return page; }
    public void setPage(int page) { this.page = page; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public Player viewer() { return viewer; }
    public void setViewer(Player viewer) { this.viewer = viewer; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    void clearButtons() {
        buttons.clear();
        shown.clear();
    }

    /** True if nothing that the window shows has changed since it was last drawn. */
    public boolean upToDate(long marketVersion, long limitsVersion, String day) {
        return this.marketVersion == marketVersion && this.limitsVersion == limitsVersion && this.day.equals(day);
    }

    public void markSynced(long marketVersion, long limitsVersion, String day) {
        this.marketVersion = marketVersion;
        this.limitsVersion = limitsVersion;
        this.day = day;
    }

    public View shown(int slot) { return shown.get(slot); }
    void setShown(int slot, View view) { shown.put(slot, view); }

    /** Slots that hold market items, with the item ID in each. */
    public Map<Integer, String> itemSlots() {
        Map<Integer, String> slots = new HashMap<>();
        buttons.forEach((slot, button) -> {
            if (button.kind() == Kind.ITEM) slots.put(slot, button.target());
        });
        return slots;
    }
    void put(int slot, Button button) { buttons.put(slot, button); }
    public Button button(int slot) { return buttons.get(slot); }
}