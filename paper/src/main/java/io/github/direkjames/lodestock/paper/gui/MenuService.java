package io.github.direkjames.lodestock.paper.gui;

import io.github.direkjames.lodestock.core.market.ItemState;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.Quote;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.config.Messages;
import io.github.direkjames.lodestock.paper.limits.DailyLimits;
import io.github.direkjames.lodestock.paper.permission.OrePermissions;
import io.github.direkjames.lodestock.paper.util.TrendFormat;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds and refreshes the market window. Trading itself stays in TradeService.
 * <p>
 * The whole window is only drawn when it opens, when the page changes or after a reload. After
 * that, a window is only touched when something it shows has changed (a price, stock, a daily
 * limit), and then only the icons that changed are replaced.
 */
public final class MenuService {
    private final LodestockPlugin plugin;
    private final Set<MarketMenu> open = new HashSet<>(); // main thread only
    private BukkitTask task;

    public MenuService(LodestockPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, 0);
    }

    /** Opens the market on the given page (0-based). */
    public void open(Player player, int page) {
        GuiLayout layout = plugin.layout();
        MarketMenu menu = new MarketMenu();
        Inventory inventory = Bukkit.createInventory(menu, layout.rows() * 9, Messages.mini(layout.title()));
        menu.setInventory(inventory);
        menu.setViewer(player);
        menu.setPage(page);
        populate(menu);
        player.openInventory(inventory);
        open.add(menu);
    }

    /** Redraws the whole menu. Used when the page changes. */
    public void refresh(MarketMenu menu) {
        populate(menu);
    }

    /** Replaces only the icons that changed since the menu was last drawn. Does nothing if nothing changed. */
    public void sync(MarketMenu menu) {
        syncIfChanged(menu, plugin.market().version(), plugin.limits().version(),
                plugin.priceHistory().version(), plugin.limits().currentDay());
    }

    /** Updates every open menu whose contents changed. Cheap when nothing changed. */
    public void syncAll() {
        if (open.isEmpty()) return;
        long marketVersion = plugin.market().version();
        long limitsVersion = plugin.limits().version();
        long historyVersion = plugin.priceHistory().version();
        String day = plugin.limits().currentDay();
        Iterator<MarketMenu> it = open.iterator();
        while (it.hasNext()) {
            MarketMenu menu = it.next();
            Player viewer = menu.viewer();
            if (viewer == null || !viewer.isOnline() || viewer.getOpenInventory().getTopInventory().getHolder() != menu) {
                it.remove(); // closed, or the player left
                continue;
            }
            syncIfChanged(menu, marketVersion, limitsVersion, historyVersion, day);
        }
    }

    /** Admin commands call this after changing the market. */
    public void refreshOpen() {
        syncAll();
    }

    /** (Re)starts the timer that keeps open windows up to date. Set {@code gui.refresh-ticks} to 0 to turn it off. */
    public void start() {
        stop();
        int ticks = plugin.getConfig().getInt("gui.refresh-ticks", 20);
        if (ticks <= 0) return;
        ticks = Math.max(5, ticks);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::syncAll, ticks, ticks);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    /** Closes every open Lodestock menu (used after a reload, when the layout may have changed). */
    public void closeAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MarketMenu) {
                player.closeInventory();
            }
        }
        open.clear();
    }

    private void syncIfChanged(MarketMenu menu, long marketVersion, long limitsVersion, long historyVersion, String day) {
        if (menu.upToDate(marketVersion, limitsVersion, historyVersion, day)) return;
        update(menu);
        menu.markSynced(marketVersion, limitsVersion, historyVersion, day);
    }

    /** Compares what each item icon should show now with what it shows, and replaces only the ones that differ. */
    private void update(MarketMenu menu) {
        Market market = plugin.market();
        Inventory inventory = menu.getInventory();
        Player viewer = menu.viewer();
        for (Map.Entry<Integer, String> slot : menu.itemSlots().entrySet()) {
            MarketItem item = market.item(slot.getValue()).orElse(null);
            Material material = Material.matchMaterial(slot.getValue());
            if (item == null || material == null) continue;
            MarketMenu.View view = viewOf(item, viewer);
            if (view.equals(menu.shown(slot.getKey()))) continue;
            inventory.setItem(slot.getKey(), itemIcon(item, material, viewer));
            menu.setShown(slot.getKey(), view);
        }
    }

    private MarketMenu.View viewOf(MarketItem item, Player viewer) {
        ItemState state = plugin.market().state(item.id()).orElseThrow();
        boolean locked = viewer != null && !OrePermissions.can(viewer, item.id());
        int buyLeft = DailyLimits.UNLIMITED;
        int sellLeft = DailyLimits.UNLIMITED;
        if (viewer != null && !locked) {
            buyLeft = plugin.limits().remainingBuy(viewer, item);
            sellLeft = plugin.limits().remainingSell(viewer, item);
        }
        int trend = TrendFormat.tenths(plugin.priceHistory().trend24h(item.id()));
        return new MarketMenu.View(state.price(), state.stock(), locked, buyLeft, sellLeft, trend);
    }

    private void populate(MarketMenu menu) {
        GuiLayout layout = plugin.layout();
        Market market = plugin.market();
        Inventory inventory = menu.getInventory();
        inventory.clear();
        menu.clearButtons();
        // Read the versions first, so a change that happens later is noticed by the next check.
        menu.markSynced(market.version(), plugin.limits().version(), plugin.priceHistory().version(),
                plugin.limits().currentDay());

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
            menu.setShown(entry.getKey(), viewOf(item, menu.viewer()));
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
                Placeholder.unparsed("bulk", String.valueOf(bulk)),
                Placeholder.component("trend", TrendFormat.of(plugin.priceHistory().trend24h(item.id())))
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
        // Trend and the chart hint are separate message lists, so servers that kept an older
        // lang/en.yml (with an old gui.item-lore) still get them from the built-in English text.
        if (plugin.priceHistory().enabled()) {
            // Insert the trend right after the stock line (3rd line), or at the end if the list is short.
            lore.addAll(Math.min(3, lore.size()), msg.list("gui.item-trend", placeholders));
            if (viewer == null || viewer.hasPermission("lodestock.chart")) {
                lore.addAll(msg.list("gui.item-chart-hint", placeholders));
            }
        }
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