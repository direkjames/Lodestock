package io.github.direkjames.lodestock.paper.gui;

import io.github.direkjames.lodestock.core.market.ItemState;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.Quote;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.config.Messages;
import io.github.direkjames.lodestock.paper.limits.DailyLimits;
import io.github.direkjames.lodestock.paper.permission.OrePermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.List;
import java.util.Map;

/** Builds and refreshes the market window. Trading itself stays in TradeService. */
public final class MenuService {
    private final LodestockPlugin plugin;

    public MenuService(LodestockPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        GuiLayout layout = plugin.layout();
        MarketMenu menu = new MarketMenu();
        Inventory inventory = Bukkit.createInventory(menu, layout.rows() * 9, Messages.mini(layout.title()));
        menu.setInventory(inventory);
        menu.setViewer(player);
        populate(menu);
        player.openInventory(inventory);
    }

    /** Redraws an open menu with the latest prices and stock. */
    public void refresh(MarketMenu menu) {
        populate(menu);
    }

    /** Redraws every open Lodestock menu (used after admin changes). */
    public void refreshOpen() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MarketMenu menu) {
                refresh(menu);
            }
        }
    }

    /** Closes every open Lodestock menu (used after a reload, when the layout may have changed). */
    public void closeAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MarketMenu) {
                player.closeInventory();
            }
        }
    }

    private void populate(MarketMenu menu) {
        GuiLayout layout = plugin.layout();
        Market market = plugin.market();
        Inventory inventory = menu.getInventory();
        inventory.clear();
        menu.clearButtons();

        int pages = layout.pageCount();
        menu.setPage(Math.max(0, Math.min(menu.page(), pages - 1)));
        int page = menu.page();

        if (layout.fill().enabled()) {
            ItemStack filler = icon(layout.fill().material(), Messages.mini(layout.fill().name()), List.of());
            for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        }

        for (Map.Entry<Integer, String> entry : layout.page(page).entrySet()) {
            Material material = Material.matchMaterial(entry.getValue());
            MarketItem item = market.item(entry.getValue()).orElse(null);
            if (material == null || item == null || entry.getKey() >= inventory.getSize()) continue;
            inventory.setItem(entry.getKey(), itemIcon(item, material, menu.viewer()));
            menu.put(entry.getKey(), new MarketMenu.Button(MarketMenu.Kind.ITEM, item.id()));
        }

        TagResolver[] pageTags = {
                Placeholder.unparsed("page", String.valueOf(page + 1)),
                Placeholder.unparsed("pages", String.valueOf(pages))
        };
        GuiLayout.Control previous = layout.previous();
        if (previous.enabled() && page > 0) {
            inventory.setItem(previous.slot(), icon(previous.material(), Messages.mini(previous.name(), pageTags), List.of()));
            menu.put(previous.slot(), new MarketMenu.Button(MarketMenu.Kind.PREV, null));
        }
        GuiLayout.Control next = layout.next();
        if (next.enabled() && page < pages - 1) {
            inventory.setItem(next.slot(), icon(next.material(), Messages.mini(next.name(), pageTags), List.of()));
            menu.put(next.slot(), new MarketMenu.Button(MarketMenu.Kind.NEXT, null));
        }
        GuiLayout.Control close = layout.close();
        if (close.enabled()) {
            inventory.setItem(close.slot(), icon(close.material(), Messages.mini(close.name(), pageTags), List.of()));
            menu.put(close.slot(), new MarketMenu.Button(MarketMenu.Kind.CLOSE, null));
        }
    }

    private ItemStack itemIcon(MarketItem item, Material material, Player viewer) {
        Market market = plugin.market();
        Messages msg = plugin.messages();
        ItemState state = market.state(item.id()).orElseThrow();
        Quote buy = market.quoteBuy(item.id());
        Quote sell = market.quoteSell(item.id());
        Component unavailable = msg.get("gui.unavailable");
        int bulk = Math.max(2, Math.min(64, plugin.getConfig().getInt("gui.bulk-amount", 16)));

        TagResolver[] placeholders = {
                Placeholder.component("buy", buy.ok() ? Component.text(plugin.economy().format(buy.price())) : unavailable),
                Placeholder.component("sell", sell.ok() ? Component.text(plugin.economy().format(sell.price())) : unavailable),
                Placeholder.unparsed("stock", String.valueOf(state.stock())),
                Placeholder.unparsed("max", String.valueOf(item.maxStock())),
                Placeholder.unparsed("bulk", String.valueOf(bulk))
        };
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        // An item this player has no permission for is shown greyed out, and can't be traded.
        if (viewer != null && !OrePermissions.can(viewer, item.id())) {
            meta.displayName(Component.translatable(material.translationKey()).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(noItalic(msg.list("gui.item-locked", placeholders)));
            stack.setItemMeta(meta);
            return stack;
        }

        List<Component> lore = new ArrayList<>(msg.list("gui.item-lore", placeholders));
        if (viewer != null) {
            DailyLimits limits = plugin.limits();
            int buyLimit = limits.limitFor(item, true);
            int sellLimit = limits.limitFor(item, false);
            if (buyLimit > 0 || sellLimit > 0) {
                TagResolver[] limitTags = {
                        Placeholder.unparsed("buy-left", limits.describe(limits.remainingBuy(viewer, item), buyLimit)),
                        Placeholder.unparsed("sell-left", limits.describe(limits.remainingSell(viewer, item), sellLimit)),
                        Placeholder.unparsed("reset", limits.resetsIn())
                };
                lore.addAll(msg.list("gui.item-limits", limitTags));
            }
        }
        meta.lore(noItalic(lore));
        stack.setItemMeta(meta);
        return stack;
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
}