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

    private final Map<Integer, Button> buttons = new HashMap<>();
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

    void clearButtons() { buttons.clear(); }
    void put(int slot, Button button) { buttons.put(slot, button); }
    public Button button(int slot) { return buttons.get(slot); }
}