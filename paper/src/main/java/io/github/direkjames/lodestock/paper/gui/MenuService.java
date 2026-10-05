package io.github.direkjames.lodestock.paper.gui;

import io.github.direkjames.lodestock.core.market.ItemState;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.Quote;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.config.ConfigLoader;
import io.github.direkjames.lodestock.paper.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds and refreshes the market menus. Trading itself stays in TradeService. */
public final class MenuService {
    private static final String OTHER = "__other__";
    private static final int PAGE_SLOTS = 45; // 5 rows of items, the 6th row is the button bar

    private record CategoryEntry(String id, Component name, Material icon, int count) {}

    private final LodestockPlugin plugin;

    public MenuService(LodestockPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- opening ----------

    public void openMain(Player player) {
        List<CategoryEntry> entries = categories();
        if (entries.isEmpty()) {
            plugin.messages().send(player, "list-empty");
            return;
        }
        int rows = entries.size() <= 9 ? 3 : Math.min(6, (entries.size() + 8) / 9);
        MarketMenu menu = new MarketMenu(null);
        Inventory inventory = Bukkit.createInventory(menu, rows * 9, plugin.messages().get("gui.title-main"));
        menu.setInventory(inventory);
        populate(menu);
        player.openInventory(inventory);
    }

    public void openCategory(Player player, String categoryId) {
        CategoryEntry entry = categories().stream().filter(c -> c.id().equals(categoryId)).findFirst().orElse(null);
        if (entry == null) {
            openMain(player);
            return;
        }
        MarketMenu menu = new MarketMenu(categoryId);
        Inventory inventory = Bukkit.createInventory(menu, 54,
                plugin.messages().get("gui.title-category", Placeholder.component("category", entry.name())));
        menu.setInventory(inventory);
        populate(menu);
        player.openInventory(inventory);
    }

    /** Redraws an open menu with the latest prices and stock. */
    public void refresh(MarketMenu menu) {
        populate(menu);
    }

    /** Closes every open Lodestock menu (used after a reload, when categories may have changed). */
    public void closeAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MarketMenu) {
                player.closeInventory();
            }
        }
    }

    // ---------- drawing ----------

    private void populate(MarketMenu menu) {
        if (menu.isMain()) populateMain(menu);
        else populateCategory(menu);
    }

    private void populateMain(MarketMenu menu) {
        Inventory inventory = menu.getInventory();
        inventory.clear();
        menu.clearButtons();

        List<CategoryEntry> entries = categories();
        int start = entries.size() <= 9 ? 9 + (9 - entries.size()) / 2 : 0; // centred in the middle row
        for (int i = 0; i < entries.size(); i++) {
            int slot = start + i;
            if (slot >= inventory.getSize()) break;
            CategoryEntry entry = entries.get(i);
            List<Component> lore = plugin.messages().list("gui.category-lore",
                    Placeholder.unparsed("count", String.valueOf(entry.count())));
            inventory.setItem(slot, icon(entry.icon(), entry.name(), lore));
            menu.put(slot, new MarketMenu.Button(MarketMenu.Kind.CATEGORY, entry.id()));
        }
        fillEmpty(inventory);
    }

    private void populateCategory(MarketMenu menu) {
        Messages msg = plugin.messages();
        Inventory inventory = menu.getInventory();
        inventory.clear();
        menu.clearButtons();

        List<MarketItem> items = itemsIn(menu.categoryId());
        int pages = Math.max(1, (items.size() + PAGE_SLOTS - 1) / PAGE_SLOTS);
        menu.setPage(Math.max(0, Math.min(menu.page(), pages - 1)));

        int from = menu.page() * PAGE_SLOTS;
        for (int i = 0; i < PAGE_SLOTS && from + i < items.size(); i++) {
            MarketItem item = items.get(from + i);
            Material material = Material.matchMaterial(item.id());
            if (material == null) continue;
            inventory.setItem(i, itemIcon(item, material));
            menu.put(i, new MarketMenu.Button(MarketMenu.Kind.ITEM, item.id()));
        }

        // Button bar (bottom row)
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, filler());
        inventory.setItem(45, icon(Material.ARROW, msg.get("gui.back"), List.of()));
        menu.put(45, new MarketMenu.Button(MarketMenu.Kind.BACK, null));
        if (menu.page() > 0) {
            inventory.setItem(48, icon(Material.ARROW, msg.get("gui.prev-page"), List.of()));
            menu.put(48, new MarketMenu.Button(MarketMenu.Kind.PREV, null));
        }
        inventory.setItem(49, icon(Material.PAPER, msg.get("gui.page",
                Placeholder.unparsed("page", String.valueOf(menu.page() + 1)),
                Placeholder.unparsed("pages", String.valueOf(pages))), List.of()));
        if (menu.page() < pages - 1) {
            inventory.setItem(50, icon(Material.ARROW, msg.get("gui.next-page"), List.of()));
            menu.put(50, new MarketMenu.Button(MarketMenu.Kind.NEXT, null));
        }
        inventory.setItem(53, icon(Material.BARRIER, msg.get("gui.close"), List.of()));
        menu.put(53, new MarketMenu.Button(MarketMenu.Kind.CLOSE, null));
    }

    private ItemStack itemIcon(MarketItem item, Material material) {
        Market market = plugin.market();
        Messages msg = plugin.messages();
        ItemState state = market.state(item.id()).orElseThrow();
        Quote buy = market.quoteBuy(item.id());
        Quote sell = market.quoteSell(item.id());
        Component unavailable = msg.get("gui.unavailable");
        int bulk = plugin.getConfig().getInt("gui.bulk-amount", 16);
        bulk = Math.max(2, Math.min(64, bulk));

        TagResolver[] placeholders = {
                Placeholder.component("buy", buy.ok() ? Component.text(plugin.economy().format(buy.price())) : unavailable),
                Placeholder.component("sell", sell.ok() ? Component.text(plugin.economy().format(sell.price())) : unavailable),
                Placeholder.unparsed("stock", String.valueOf(state.stock())),
                Placeholder.unparsed("max", String.valueOf(item.maxStock())),
                Placeholder.unparsed("bulk", String.valueOf(bulk))
        };
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(noItalic(msg.list("gui.item-lore", placeholders)));
        stack.setItemMeta(meta);
        return stack;
    }

    // ---------- helpers ----------

    private List<CategoryEntry> categories() {
        Market market = plugin.market();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MarketItem item : market.items()) counts.merge(item.category(), 1, Integer::sum);

        List<CategoryEntry> result = new ArrayList<>();
        for (ConfigLoader.Category category : plugin.categories().values()) {
            int count = counts.getOrDefault(category.id(), 0);
            if (count > 0) {
                result.add(new CategoryEntry(category.id(), Messages.mini(category.name()),
                        materialOr(category.icon(), Material.CHEST), count));
            }
        }
        // Items whose category isn't defined in items.yml end up in "Other".
        int orphans = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!plugin.categories().containsKey(entry.getKey())) orphans += entry.getValue();
        }
        if (orphans > 0) {
            result.add(new CategoryEntry(OTHER, plugin.messages().get("gui.other-category"), Material.CHEST, orphans));
        }
        return result;
    }

    private List<MarketItem> itemsIn(String categoryId) {
        boolean other = OTHER.equals(categoryId);
        return plugin.market().items().stream()
                .filter(item -> other ? !plugin.categories().containsKey(item.category())
                        : item.category().equals(categoryId))
                .toList();
    }

    private static Material materialOr(String id, Material fallback) {
        Material material = Material.matchMaterial(id);
        return material == null ? fallback : material;
    }

    private static List<Component> noItalic(List<Component> lines) {
        return lines.stream()
                .map(line -> line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                .toList();
    }

    private static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        meta.lore(noItalic(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack filler() {
        return icon(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
    }

    private static void fillEmpty(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, filler());
        }
    }
}