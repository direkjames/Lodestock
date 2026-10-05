package io.github.direkjames.lodestock.paper.gui;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/** The window design read from gui.yml, with items already assigned to slots on each page. */
public record GuiLayout(String title, int rows, Fill fill, Control previous, Control next, Control close,
                        List<Map<Integer, String>> pages) {

    public record Fill(boolean enabled, Material material, String name) {}

    public record Control(boolean enabled, int slot, Material material, String name) {}

    public int pageCount() { return pages.size(); }

    /** Slot to item ID for the given page (0-based). */
    public Map<Integer, String> page(int index) { return pages.get(index); }
}