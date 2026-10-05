package io.github.direkjames.lodestock.paper.gui;

import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.paper.config.ConfigLoader;
import io.github.direkjames.lodestock.paper.config.ConfigWarnings;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads gui.yml and works out which item goes in which slot on which page. */
public final class GuiLayoutLoader {
    private static final Pattern SLOT = Pattern.compile("(\\d+)(?:\\s*-\\s*(\\d+))?");
    private static final int MAX_PAGE = 50;

    private GuiLayoutLoader() {}

    public static GuiLayout load(JavaPlugin plugin, List<MarketItem> items,
                                 Map<String, ConfigLoader.Pin> pins, ConfigWarnings warn) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "gui.yml"));

        int rows = cfg.getInt("rows", 4);
        if (rows < 1 || rows > 6) {
            warn.add("gui.yml: rows must be from 1 to 6, using 4.");
            rows = 4;
        }
        int size = rows * 9;
        String title = cfg.getString("title", "<dark_gray>Lodestock Market");

        GuiLayout.Fill fill = new GuiLayout.Fill(
                cfg.getBoolean("fill.enabled", true),
                material(cfg.getString("fill.material"), Material.GRAY_STAINED_GLASS_PANE, "fill.material", warn),
                cfg.getString("fill.name", " "));

        GuiLayout.Control previous = control(cfg, "previous-page", Material.ARROW,
                "<yellow>Previous page <gray>(<page>/<pages>)", size - 9, size, warn);
        GuiLayout.Control next = control(cfg, "next-page", Material.ARROW,
                "<yellow>Next page <gray>(<page>/<pages>)", size - 1, size, warn);
        GuiLayout.Control close = control(cfg, "close", Material.BARRIER, "<red>Close", size - 5, size, warn);

        Set<Integer> reserved = new HashSet<>();
        for (GuiLayout.Control c : List.of(previous, next, close)) {
            if (c.enabled() && !reserved.add(c.slot())) {
                warn.add("gui.yml: two buttons share slot " + c.slot() + ", move one of them.");
            }
        }

        List<Integer> itemSlots = parseItemSlots(cfg.getList("item-slots"), size, rows, reserved, warn);

        // Pinned items go to their exact slot; everything else flows through the free item slots.
        Map<Integer, Map<Integer, String>> pinned = new TreeMap<>();
        List<MarketItem> flowing = new ArrayList<>();
        for (MarketItem item : items) {
            ConfigLoader.Pin pin = pins.get(item.id());
            if (pin == null) {
                flowing.add(item);
                continue;
            }
            String problem = null;
            if (pin.slot() >= size) problem = "slot " + pin.slot() + " is outside the window";
            else if (reserved.contains(pin.slot())) problem = "slot " + pin.slot() + " is used by a button";
            else if (pin.page() > MAX_PAGE) problem = "page " + pin.page() + " is above the limit of " + MAX_PAGE;
            else if (pinned.computeIfAbsent(pin.page(), p -> new LinkedHashMap<>()).containsKey(pin.slot())) {
                problem = "slot " + pin.slot() + " on page " + pin.page() + " is already taken";
            }
            if (problem != null) {
                warn.add("items.yml: " + item.id() + ": " + problem + ", it will be placed automatically.");
                flowing.add(item);
            } else {
                pinned.get(pin.page()).put(pin.slot(), item.id());
            }
        }

        int maxPinnedPage = 0;
        for (Map.Entry<Integer, Map<Integer, String>> e : pinned.entrySet()) {
            if (!e.getValue().isEmpty()) maxPinnedPage = Math.max(maxPinnedPage, e.getKey());
        }

        List<Map<Integer, String>> pages = new ArrayList<>();
        int index = 0;
        int page = 1;
        while (index < flowing.size() || page <= maxPinnedPage) {
            Map<Integer, String> slots = new LinkedHashMap<>(pinned.getOrDefault(page, Map.of()));
            for (int slot : itemSlots) {
                if (index >= flowing.size()) break;
                if (slots.containsKey(slot)) continue;
                slots.put(slot, flowing.get(index++).id());
            }
            pages.add(slots);
            page++;
            if (itemSlots.isEmpty() && page > maxPinnedPage) {
                if (index < flowing.size()) warn.add("gui.yml: item-slots has no usable slots, so " + (flowing.size() - index) + " items can't be shown.");
                break;
            }
        }
        if (pages.isEmpty()) pages.add(new LinkedHashMap<>());

        return new GuiLayout(title, rows, fill, previous, next, close, List.copyOf(pages));
    }

    private static List<Integer> parseItemSlots(List<?> raw, int size, int rows, Set<Integer> reserved, ConfigWarnings warn) {
        List<Integer> slots = new ArrayList<>();
        if (raw == null) { // default: everything except the bottom row
            int limit = rows == 1 ? size : size - 9;
            for (int i = 0; i < limit; i++) if (!reserved.contains(i)) slots.add(i);
            return slots;
        }
        for (Object entry : raw) {
            String text = String.valueOf(entry).trim();
            Matcher m = SLOT.matcher(text);
            if (!m.matches()) {
                warn.add("gui.yml: item-slots entry '" + text + "' is not a number or a range like 10-16.");
                continue;
            }
            try {
                int from = Integer.parseInt(m.group(1));
                int to = m.group(2) == null ? from : Integer.parseInt(m.group(2));
                if (to < from) {
                    warn.add("gui.yml: item-slots range '" + text + "' goes backwards, skipped.");
                    continue;
                }
                for (int slot = from; slot <= to; slot++) {
                    if (slot >= size) {
                        warn.add("gui.yml: item-slots '" + text + "' goes past the last slot (" + (size - 1) + ").");
                        break;
                    }
                    if (!reserved.contains(slot) && !slots.contains(slot)) slots.add(slot);
                }
            } catch (NumberFormatException e) {
                warn.add("gui.yml: item-slots entry '" + text + "' is too large.");
            }
        }
        return slots;
    }

    private static GuiLayout.Control control(YamlConfiguration cfg, String path, Material defaultMaterial,
                                             String defaultName, int defaultSlot, int size, ConfigWarnings warn) {
        boolean enabled = cfg.getBoolean(path + ".enabled", true);
        int slot = cfg.getInt(path + ".slot", defaultSlot);
        if (enabled && (slot < 0 || slot >= size)) {
            warn.add("gui.yml: " + path + ".slot " + slot + " is outside the window (0-" + (size - 1) + "), button turned off.");
            enabled = false;
        }
        return new GuiLayout.Control(enabled, slot,
                material(cfg.getString(path + ".material"), defaultMaterial, path + ".material", warn),
                cfg.getString(path + ".name", defaultName));
    }

    private static Material material(String name, Material fallback, String path, ConfigWarnings warn) {
        if (name == null) return fallback;
        Material material = Material.matchMaterial(name);
        if (material == null || material.isLegacy() || !material.isItem() || material.isAir()) {
            warn.add("gui.yml: " + path + " '" + name + "' is not a valid item, using " + fallback.name().toLowerCase() + ".");
            return fallback;
        }
        return material;
    }
}