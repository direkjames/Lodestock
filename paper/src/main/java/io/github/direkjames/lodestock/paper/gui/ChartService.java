package io.github.direkjames.lodestock.paper.gui;

import io.github.direkjames.lodestock.core.history.ChartMath;
import io.github.direkjames.lodestock.core.history.PricePoint;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.config.Messages;
import io.github.direkjames.lodestock.paper.util.ItemNames;
import io.github.direkjames.lodestock.paper.util.TrendFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** Price charts: the bar chart window and the text chart for chat. */
public final class ChartService {
    private static final int BARS = 9;       // time slices, one per column
    private static final int LEVELS = 5;     // rows of bars
    private static final int SIZE = 54;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_DAY = 46;
    private static final int SLOT_WEEK = 47;
    private static final int SLOT_ALL = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_CLOSE = 53;
    private static final int SPARK_BUCKETS = 24;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

    private final LodestockPlugin plugin;

    public ChartService(LodestockPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- the chart window ----------

    public void open(Player player, String itemId, ChartRange range, int returnPage) {
        if (!plugin.priceHistory().enabled()) {
            plugin.messages().send(player, "chart-disabled");
            return;
        }
        ChartMenu menu = new ChartMenu(itemId, range, returnPage, player);
        Messages msg = plugin.messages();
        Inventory inventory = Bukkit.createInventory(menu, SIZE,
                msg.get("gui.chart-title", Placeholder.unparsed("item", ItemNames.pretty(itemId))));
        menu.setInventory(inventory);
        load(menu, points -> {
            if (!player.isOnline()) return;
            render(menu, points);
            player.openInventory(inventory);
        });
    }

    public void handleClick(Player player, ChartMenu menu, int slot) {
        ChartMenu.Action action = menu.action(slot);
        if (action == null) return;
        switch (action) {
            case CLOSE -> later(player::closeInventory);
            case BACK -> later(() -> plugin.menus().open(player, menu.returnPage()));
            case RANGE_DAY -> changeRange(menu, ChartRange.DAY);
            case RANGE_WEEK -> changeRange(menu, ChartRange.WEEK);
            case RANGE_ALL -> changeRange(menu, ChartRange.ALL);
        }
    }

    private void changeRange(ChartMenu menu, ChartRange range) {
        if (menu.range() == range) return;
        menu.setRange(range);
        load(menu, points -> {
            Player viewer = menu.viewer();
            if (!viewer.isOnline() || viewer.getOpenInventory().getTopInventory().getHolder() != menu) return;
            render(menu, points);
        });
    }

    private void load(ChartMenu menu, java.util.function.Consumer<List<PricePoint>> then) {
        long now = System.currentTimeMillis();
        plugin.priceHistory().read(menu.itemId(), menu.range().since(now))
                .thenAccept(points -> onMain(() -> then.accept(points)));
    }

    /** Draws the bars and buttons. */
    private void render(ChartMenu menu, List<PricePoint> points) {
        Messages msg = plugin.messages();
        Inventory inventory = menu.getInventory();
        inventory.clear();
        menu.clearActions();

        ItemStack background = pane(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < SIZE; slot++) inventory.setItem(slot, background);

        long now = System.currentTimeMillis();
        long from = menu.range() == ChartRange.ALL
                ? (points.isEmpty() ? now - 3_600_000L : points.get(0).time())
                : menu.range().since(now);
        if (now - from < 60_000L) from = now - 60_000L;
        double[] values = ChartMath.buckets(points, from, now, BARS);

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : values) {
            if (Double.isNaN(v)) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }

        double previous = Double.NaN;
        for (int i = 0; i < BARS; i++) {
            double value = values[i];
            long end = from + (now - from) * (i + 1) / BARS;
            if (Double.isNaN(value)) {
                inventory.setItem((LEVELS - 1) * 9 + i, pane(Material.GRAY_STAINED_GLASS_PANE,
                        msg.get("gui.chart-no-data"), List.of()));
                continue;
            }
            Material color = Double.isNaN(previous) ? Material.WHITE_STAINED_GLASS_PANE
                    : value >= previous ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            ItemStack bar = pane(color,
                    Component.text(TIME.format(Instant.ofEpochMilli(end)), net.kyori.adventure.text.format.NamedTextColor.WHITE),
                    msg.list("gui.chart-bar", Placeholder.unparsed("price", plugin.economy().format(value))));
            int level = ChartMath.level(value, min, max, LEVELS);
            for (int row = 0; row < level; row++) inventory.setItem((LEVELS - 1 - row) * 9 + i, bar);
            previous = value;
        }

        inventory.setItem(SLOT_BACK, button(Material.ARROW, msg.get("gui.chart-back"), false));
        menu.put(SLOT_BACK, ChartMenu.Action.BACK);
        inventory.setItem(SLOT_DAY, button(Material.CLOCK, msg.get("gui.chart-range-day"), menu.range() == ChartRange.DAY));
        menu.put(SLOT_DAY, ChartMenu.Action.RANGE_DAY);
        inventory.setItem(SLOT_WEEK, button(Material.COMPASS, msg.get("gui.chart-range-week"), menu.range() == ChartRange.WEEK));
        menu.put(SLOT_WEEK, ChartMenu.Action.RANGE_WEEK);
        inventory.setItem(SLOT_ALL, button(Material.BOOK, msg.get("gui.chart-range-all"), menu.range() == ChartRange.ALL));
        menu.put(SLOT_ALL, ChartMenu.Action.RANGE_ALL);
        inventory.setItem(SLOT_CLOSE, button(Material.BARRIER, msg.get("gui.chart-close"), false));
        menu.put(SLOT_CLOSE, ChartMenu.Action.CLOSE);
        inventory.setItem(SLOT_INFO, info(menu, points));
    }

    private ItemStack info(ChartMenu menu, List<PricePoint> points) {
        Messages msg = plugin.messages();
        Material material = Optional.ofNullable(Material.matchMaterial(menu.itemId())).orElse(Material.PAPER);
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(Component.text(ItemNames.pretty(menu.itemId()), net.kyori.adventure.text.format.NamedTextColor.GOLD)));
        Optional<ChartMath.Stats> stats = ChartMath.stats(points);
        if (stats.isPresent()) {
            ChartMath.Stats s = stats.get();
            TagResolver[] tags = {
                    Placeholder.component("range", msg.get(menu.range().messageKey())),
                    Placeholder.unparsed("low", plugin.economy().format(s.low())),
                    Placeholder.unparsed("high", plugin.economy().format(s.high())),
                    Placeholder.unparsed("average", plugin.economy().format(s.average())),
                    Placeholder.component("change", TrendFormat.of(s.changePercent())),
                    Placeholder.unparsed("samples", String.valueOf(s.samples()))
            };
            meta.lore(msg.list("gui.chart-info", tags).stream().map(ChartService::noItalic).toList());
        } else {
            meta.lore(msg.list("gui.chart-info-empty").stream().map(ChartService::noItalic).toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    // ---------- the text chart for chat ----------

    /** Sends a one-line chart and its numbers to a player or the console. */
    public void sendText(CommandSender sender, String itemId, ChartRange range) {
        Messages msg = plugin.messages();
        if (!plugin.priceHistory().enabled()) {
            msg.send(sender, "chart-disabled");
            return;
        }
        long now = System.currentTimeMillis();
        plugin.priceHistory().read(itemId, range.since(now)).thenAccept(points -> onMain(() -> {
            if (points.isEmpty()) {
                msg.send(sender, "chart-none", Placeholder.unparsed("item", ItemNames.pretty(itemId)));
                return;
            }
            long from = range == ChartRange.ALL ? points.get(0).time() : range.since(now);
            if (now - from < 60_000L) from = now - 60_000L;
            ChartMath.Stats s = ChartMath.stats(points).orElseThrow();
            msg.send(sender, "chart-header",
                    Placeholder.unparsed("item", ItemNames.pretty(itemId)),
                    Placeholder.component("range", msg.get(range.messageKey())));
            msg.send(sender, "chart-spark", Placeholder.unparsed("spark",
                    ChartMath.spark(ChartMath.buckets(points, from, now, SPARK_BUCKETS))));
            msg.send(sender, "chart-stats",
                    Placeholder.unparsed("low", plugin.economy().format(s.low())),
                    Placeholder.unparsed("high", plugin.economy().format(s.high())),
                    Placeholder.unparsed("average", plugin.economy().format(s.average())),
                    Placeholder.component("change", TrendFormat.of(s.changePercent())));
        }));
    }

    // ---------- helpers ----------

    private void onMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else plugin.getServer().getScheduler().runTask(plugin, task);
    }

    // Opening or closing an inventory from inside a click event is safer one tick later.
    private void later(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static ItemStack pane(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(name));
        meta.lore(lore.stream().map(ChartService::noItalic).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack button(Material material, Component name, boolean selected) {
        ItemStack stack = pane(material, name, List.of());
        if (selected) {
            ItemMeta meta = stack.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
