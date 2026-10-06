package io.github.direkjames.lodestock.paper.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/** Marks an inventory as a price chart and remembers what each button does. */
public final class ChartMenu implements InventoryHolder {
    public enum Action { BACK, RANGE_DAY, RANGE_WEEK, RANGE_ALL, CLOSE }

    private final String itemId;
    private final int returnPage;
    private final Player viewer;
    private final Map<Integer, Action> actions = new HashMap<>();
    private ChartRange range;
    private Inventory inventory;

    public ChartMenu(String itemId, ChartRange range, int returnPage, Player viewer) {
        this.itemId = itemId;
        this.range = range;
        this.returnPage = returnPage;
        this.viewer = viewer;
    }

    public String itemId() { return itemId; }
    public ChartRange range() { return range; }
    public void setRange(ChartRange range) { this.range = range; }
    public int returnPage() { return returnPage; }
    public Player viewer() { return viewer; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    void clearActions() { actions.clear(); }
    void put(int slot, Action action) { actions.put(slot, action); }
    public Action action(int slot) { return actions.get(slot); }
}
