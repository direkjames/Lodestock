package io.github.direkjames.lodestock.paper.command;

import io.github.direkjames.lodestock.core.market.ItemState;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.Quote;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.config.Messages;
import io.github.direkjames.lodestock.paper.log.TradeLog;
import io.github.direkjames.lodestock.paper.util.ItemNames;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class LodestockCommand implements TabExecutor {
    private static final int MAX_WARNINGS_SHOWN = 10;
    private static final int HISTORY_PAGE_SIZE = 8;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final DecimalFormat PERCENT = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final List<String> ADMIN_COMMANDS =
            List.of("setprice", "setstock", "reset", "crash", "surge", "stats", "history", "reload");

    private final LodestockPlugin plugin;

    public LodestockCommand(LodestockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Messages msg = plugin.messages();
        String sub = args.length == 0 ? (sender instanceof Player ? "open" : "help") : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(sender);
            case "open", "gui" -> open(sender);
            case "price" -> price(sender, args);
            case "sellhand" -> sellHand(sender);
            case "sellall" -> sellAll(sender, args);
            case "setprice" -> setPrice(sender, args);
            case "setstock" -> setStock(sender, args);
            case "reset" -> reset(sender, args);
            case "crash" -> adjust(sender, args, true);
            case "surge" -> adjust(sender, args, false);
            case "stats" -> stats(sender);
            case "history" -> history(sender, args);
            case "reload" -> reload(sender);
            default -> msg.send(sender, "unknown-subcommand");
        }
        return true;
    }

    // ---------- player commands ----------

    private void help(CommandSender sender) {
        plugin.messages().send(sender, "help");
        if (ADMIN_COMMANDS.stream().anyMatch(name -> sender.hasPermission("lodestock.admin." + name))) {
            plugin.messages().send(sender, "help-admin");
        }
    }

    private void open(CommandSender sender) {
        Messages msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("lodestock.use")) {
            msg.send(sender, "no-permission");
            return;
        }
        plugin.menus().open(player);
    }

    private void price(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!allowed(sender, "lodestock.use")) return;
        if (args.length < 2) {
            msg.send(sender, "usage-price");
            return;
        }
        String id = knownItem(sender, args[1]);
        if (id == null) return;
        Market market = plugin.market();
        MarketItem item = market.item(id).orElseThrow();
        ItemState state = market.state(id).orElseThrow();
        Quote buy = market.quoteBuy(id);
        Quote sell = market.quoteSell(id);
        msg.send(sender, "price-line",
                Placeholder.unparsed("item", ItemNames.pretty(id)),
                Placeholder.unparsed("buy", buy.ok() ? plugin.economy().format(buy.price()) : "-"),
                Placeholder.unparsed("sell", sell.ok() ? plugin.economy().format(sell.price()) : "-"),
                Placeholder.unparsed("stock", String.valueOf(state.stock())),
                Placeholder.unparsed("max", String.valueOf(item.maxStock())));
    }

    private void sellHand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!allowed(sender, "lodestock.sell.hand")) return;
        plugin.trades().sellHand(player);
    }

    private void sellAll(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!allowed(sender, "lodestock.sell.all")) return;
        if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) plugin.trades().sellAllConfirm(player);
        else plugin.trades().sellAllRequest(player);
    }

    // ---------- admin commands ----------

    private void setPrice(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!allowed(sender, "lodestock.admin.setprice")) return;
        if (args.length < 3) {
            msg.send(sender, "usage-setprice");
            return;
        }
        String id = knownItem(sender, args[1]);
        if (id == null) return;
        Double price = parseNumber(args[2]);
        if (price == null) {
            msg.send(sender, "invalid-number");
            return;
        }
        try {
            plugin.market().setPrice(id, price);
        } catch (IllegalArgumentException e) {
            msg.send(sender, "admin-error", Placeholder.unparsed("error", e.getMessage()));
            return;
        }
        plugin.tradeLog().admin(sender.getName(), "SETPRICE", id + " " + price);
        plugin.menus().refreshOpen();
        msg.send(sender, "setprice-success",
                Placeholder.unparsed("item", ItemNames.pretty(id)),
                Placeholder.unparsed("price", plugin.economy().format(price)));
    }

    private void setStock(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!allowed(sender, "lodestock.admin.setstock")) return;
        if (args.length < 3) {
            msg.send(sender, "usage-setstock");
            return;
        }
        String id = knownItem(sender, args[1]);
        if (id == null) return;
        int stock;
        try {
            stock = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            msg.send(sender, "invalid-number");
            return;
        }
        try {
            plugin.market().setStock(id, stock);
        } catch (IllegalArgumentException e) {
            msg.send(sender, "admin-error", Placeholder.unparsed("error", e.getMessage()));
            return;
        }
        plugin.tradeLog().admin(sender.getName(), "SETSTOCK", id + " " + stock);
        plugin.menus().refreshOpen();
        msg.send(sender, "setstock-success",
                Placeholder.unparsed("item", ItemNames.pretty(id)),
                Placeholder.unparsed("stock", String.valueOf(stock)));
    }

    private void reset(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!allowed(sender, "lodestock.admin.reset")) return;
        if (args.length < 2) {
            msg.send(sender, "usage-reset");
            return;
        }
        if (args[1].equalsIgnoreCase("all")) {
            if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                msg.send(sender, "reset-all-confirm");
                return;
            }
            plugin.market().resetAll();
            plugin.tradeLog().admin(sender.getName(), "RESET", "all items");
            plugin.menus().refreshOpen();
            msg.send(sender, "reset-all-success");
            return;
        }
        String id = knownItem(sender, args[1]);
        if (id == null) return;
        plugin.market().reset(id);
        plugin.tradeLog().admin(sender.getName(), "RESET", id);
        plugin.menus().refreshOpen();
        msg.send(sender, "reset-success", Placeholder.unparsed("item", ItemNames.pretty(id)));
    }

    /** crash lowers prices, surge raises them. */
    private void adjust(CommandSender sender, String[] args, boolean crash) {
        Messages msg = plugin.messages();
        if (!allowed(sender, crash ? "lodestock.admin.crash" : "lodestock.admin.surge")) return;
        if (args.length < 2) {
            msg.send(sender, crash ? "usage-crash" : "usage-surge");
            return;
        }
        double max = crash ? 99 : 1000;
        Double percent = parseNumber(args[1]);
        if (percent == null || percent < 1 || percent > max) {
            msg.send(sender, "invalid-percent", Placeholder.unparsed("max", PERCENT.format(max)));
            return;
        }
        String id = null;
        if (args.length > 2) {
            id = knownItem(sender, args[2]);
            if (id == null) return;
        }

        plugin.market().adjustPrices(id, crash ? -percent : percent);
        plugin.tradeLog().admin(sender.getName(), crash ? "CRASH" : "SURGE",
                PERCENT.format(percent) + "% " + (id == null ? "all items" : id));
        plugin.menus().refreshOpen();

        String key = (crash ? "crash" : "surge") + "-broadcast" + (id == null ? "" : "-item");
        TagResolver[] tags = {
                Placeholder.unparsed("percent", PERCENT.format(percent)),
                Placeholder.unparsed("item", id == null ? "" : ItemNames.pretty(id))
        };
        if (plugin.getConfig().getBoolean("admin.broadcast", true)) plugin.getServer().broadcast(msg.get(key, tags));
        else msg.send(sender, key, tags);
    }

    private void stats(CommandSender sender) {
        Messages msg = plugin.messages();
        if (!allowed(sender, "lodestock.admin.stats")) return;
        Market market = plugin.market();

        record Row(MarketItem item, ItemState state, double change) {}
        List<Row> rows = new ArrayList<>();
        for (MarketItem item : market.items()) {
            ItemState state = market.state(item.id()).orElseThrow();
            rows.add(new Row(item, state, (state.price() / item.basePrice() - 1) * 100));
        }
        msg.send(sender, "stats-header", Placeholder.unparsed("count", String.valueOf(rows.size())));

        List<Row> byChange = new ArrayList<>(rows);
        byChange.sort(Comparator.comparingDouble(Row::change).reversed());
        byChange.stream().limit(3).filter(r -> r.change() > 0).forEach(r -> msg.send(sender, "stats-up",
                Placeholder.unparsed("item", ItemNames.pretty(r.item().id())),
                Placeholder.unparsed("percent", PERCENT.format(r.change())),
                Placeholder.unparsed("price", plugin.economy().format(r.state().price()))));
        List<Row> falling = new ArrayList<>(byChange);
        falling.sort(Comparator.comparingDouble(Row::change));
        falling.stream().limit(3).filter(r -> r.change() < 0).forEach(r -> msg.send(sender, "stats-down",
                Placeholder.unparsed("item", ItemNames.pretty(r.item().id())),
                Placeholder.unparsed("percent", PERCENT.format(-r.change())),
                Placeholder.unparsed("price", plugin.economy().format(r.state().price()))));

        List<Row> byStock = new ArrayList<>(rows);
        byStock.sort(Comparator.comparingDouble(r -> (double) r.state().stock() / r.item().maxStock()));
        byStock.stream().limit(3).forEach(r -> msg.send(sender, "stats-stock",
                Placeholder.unparsed("item", ItemNames.pretty(r.item().id())),
                Placeholder.unparsed("stock", String.valueOf(r.state().stock())),
                Placeholder.unparsed("max", String.valueOf(r.item().maxStock()))));
    }

    private void history(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!allowed(sender, "lodestock.admin.history")) return;
        if (args.length < 2) {
            msg.send(sender, "usage-history");
            return;
        }
        // Online players, or players the server has seen before. No slow lookups on the main thread.
        OfflinePlayer target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) target = plugin.getServer().getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            msg.send(sender, "player-not-found", Placeholder.unparsed("player", args[1]));
            return;
        }
        int page = 1;
        if (args.length > 2) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                // stays on page 1
            }
        }
        String name = target.getName() != null ? target.getName() : args[1];
        int requested = page;
        plugin.tradeLog().readAsync(target.getUniqueId()).thenAccept(entries ->
                plugin.getServer().getScheduler().runTask(plugin, () -> showHistory(sender, name, entries, requested)));
    }

    private void showHistory(CommandSender sender, String name, List<TradeLog.Entry> entries, int requestedPage) {
        Messages msg = plugin.messages();
        if (entries.isEmpty()) {
            msg.send(sender, "history-empty", Placeholder.unparsed("player", name));
            return;
        }
        int pages = (entries.size() + HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE;
        int page = Math.max(1, Math.min(requestedPage, pages));
        msg.send(sender, "history-header",
                Placeholder.unparsed("player", name),
                Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("pages", String.valueOf(pages)),
                Placeholder.unparsed("total", String.valueOf(entries.size())));
        int from = (page - 1) * HISTORY_PAGE_SIZE;
        for (TradeLog.Entry e : entries.subList(from, Math.min(entries.size(), from + HISTORY_PAGE_SIZE))) {
            msg.send(sender, e.action().equals("BUY") ? "history-buy" : "history-sell",
                    Placeholder.unparsed("time", TIME.format(Instant.ofEpochMilli(e.time()))),
                    Placeholder.unparsed("amount", String.valueOf(e.amount())),
                    Placeholder.unparsed("item", ItemNames.pretty(e.item())),
                    Placeholder.unparsed("money", plugin.economy().format(e.money())));
        }
    }

    private void reload(CommandSender sender) {
        Messages msg = plugin.messages();
        if (!allowed(sender, "lodestock.admin.reload")) return;
        if (!plugin.loadMarket()) {
            msg.send(sender, "reload-failed");
            return;
        }
        msg.send(sender, "reloaded", Placeholder.unparsed("items", String.valueOf(plugin.market().items().size())));

        List<String> warnings = plugin.warnings();
        if (warnings.isEmpty()) return;
        msg.send(sender, "reload-warnings", Placeholder.unparsed("count", String.valueOf(warnings.size())));
        for (int i = 0; i < Math.min(MAX_WARNINGS_SHOWN, warnings.size()); i++) {
            msg.send(sender, "warning-line", Placeholder.unparsed("warning", warnings.get(i)));
        }
        if (warnings.size() > MAX_WARNINGS_SHOWN) {
            msg.send(sender, "warning-more", Placeholder.unparsed("more", String.valueOf(warnings.size() - MAX_WARNINGS_SHOWN)));
        }
    }

    // ---------- helpers ----------

    private boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    /** Returns the item ID if the market trades it, otherwise tells the sender and returns null. */
    private String knownItem(CommandSender sender, String raw) {
        String id = normalizeId(raw);
        if (plugin.market().item(id).isEmpty()) {
            plugin.messages().send(sender, "unknown-item", Placeholder.unparsed("item", raw));
            return null;
        }
        return id;
    }

    private static Double parseNumber(String text) {
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "Minecraft:Diamond" and "diamond" both become "diamond". */
    static String normalizeId(String input) {
        String id = input.trim().toLowerCase(Locale.ROOT);
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    // ---------- tab completion ----------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help"));
            if (sender.hasPermission("lodestock.use")) options.addAll(List.of("open", "price"));
            if (sender.hasPermission("lodestock.sell.hand")) options.add("sellhand");
            if (sender.hasPermission("lodestock.sell.all")) options.add("sellall");
            for (String name : ADMIN_COMMANDS) {
                if (sender.hasPermission("lodestock.admin." + name)) options.add(name);
            }
            return filter(options, typed);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("price") || sub.equals("setprice") || sub.equals("setstock")) return itemIds(typed);
            if (sub.equals("reset")) {
                List<String> options = new ArrayList<>(itemIds(typed));
                if ("all".startsWith(typed)) options.add(0, "all");
                return options;
            }
            if (sub.equals("crash") || sub.equals("surge")) return filter(List.of("10", "25", "50"), typed);
            if (sub.equals("sellall")) return filter(List.of("confirm"), typed);
            if (sub.equals("history")) {
                return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), typed);
            }
        }
        if (args.length == 3) {
            if (sub.equals("crash") || sub.equals("surge")) return itemIds(typed);
            if (sub.equals("reset") && args[1].equalsIgnoreCase("all")) return filter(List.of("confirm"), typed);
        }
        return List.of();
    }

    private List<String> itemIds(String typed) {
        return plugin.market().items().stream()
                .map(MarketItem::id)
                .filter(id -> id.startsWith(typed))
                .limit(30)
                .toList();
    }

    private static List<String> filter(List<String> options, String typed) {
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}