package io.github.direkjames.lodestock.paper.command;

import io.github.direkjames.lodestock.core.audit.EconomyAudit;
import io.github.direkjames.lodestock.core.market.ItemState;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.Quote;
import io.github.direkjames.lodestock.core.stats.Board;
import io.github.direkjames.lodestock.core.stats.Period;
import io.github.direkjames.lodestock.core.stats.Row;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.admin.MarketAdmin;
import io.github.direkjames.lodestock.paper.config.Messages;
import io.github.direkjames.lodestock.paper.log.TradeLog;
import io.github.direkjames.lodestock.paper.util.HourSpan;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LodestockCommand implements TabExecutor {
    private static final int MAX_WARNINGS_SHOWN = 10;
    private static final int HISTORY_PAGE_SIZE = 8;
    private static final int MAX_AUDIT_LINES = 12;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final DecimalFormat PERCENT = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final List<String> ADMIN_COMMANDS =
            List.of("setprice", "setstock", "reset", "crash", "surge", "stats", "history", "audit", "economy", "reload");

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
            case "limits" -> limits(sender);
            case "chart" -> chart(sender, args);
            case "top" -> top(sender, args);
            case "sellhand" -> sellHand(sender);
            case "sellall" -> sellAll(sender, args);
            case "setprice" -> setPrice(sender, args);
            case "setstock" -> setStock(sender, args);
            case "reset" -> reset(sender, args);
            case "crash" -> adjust(sender, args, true);
            case "surge" -> adjust(sender, args, false);
            case "stats" -> stats(sender);
            case "history" -> history(sender, args);
            case "audit" -> audit(sender);
            case "economy" -> economy(sender, args);
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

    private void chart(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!allowed(sender, "lodestock.chart")) return;
        if (args.length < 2) {
            msg.send(sender, "usage-chart");
            return;
        }
        String id = knownItem(sender, args[1]);
        if (id == null) return;
        var range = args.length > 2 ? io.github.direkjames.lodestock.paper.gui.ChartRange.parse(args[2])
                : java.util.Optional.of(io.github.direkjames.lodestock.paper.gui.ChartRange.DAY);
        if (range.isEmpty()) {
            msg.send(sender, "usage-chart");
            return;
        }
        plugin.charts().sendText(sender, id, range.get());
    }

    /** /lodestock top [board] [item] [24h|7d|30d|all] */
    private void top(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!allowed(sender, "lodestock.top")) return;
        List<Board> boards = Arrays.stream(Board.values()).filter(b -> canSeeBoard(sender, b)).toList();
        if (args.length < 2) {
            sendTopUsage(sender, boards);
            return;
        }
        Board board = Board.parse(args[1]).orElse(null);
        if (board == null) {
            sendTopUsage(sender, boards);
            return;
        }
        if (!canSeeBoard(sender, board)) {
            msg.send(sender, "no-permission");
            return;
        }
        Period period = Period.WEEK;
        String item = null;
        for (int i = 2; i < args.length; i++) {
            var parsedPeriod = Period.parse(args[i]);
            if (parsedPeriod.isPresent()) {
                period = parsedPeriod.get();
                continue;
            }
            String id = normalizeId(args[i]);
            if (item != null || plugin.market().item(id).isEmpty()) {
                msg.send(sender, "top-unknown", Placeholder.unparsed("input", args[i]));
                return;
            }
            item = id;
        }
        Period shownPeriod = period;
        String shownItem = item;
        plugin.leaderboards().fetch(board, period, item, plugin.leaderboards().size()).thenAccept(rows ->
                plugin.getServer().getScheduler().runTask(plugin, () -> showTop(sender, board, shownPeriod, shownItem, rows)));
    }

    private boolean canSeeBoard(CommandSender sender, Board board) {
        return board != Board.NET || sender.hasPermission("lodestock.top.net");
    }

    private void sendTopUsage(CommandSender sender, List<Board> boards) {
        plugin.messages().send(sender, "top-usage",
                Placeholder.unparsed("boards", String.join(", ", boards.stream().map(Board::key).toList())));
    }

    private void showTop(CommandSender sender, Board board, Period period, String item, List<Row> rows) {
        Messages msg = plugin.messages();
        var eco = plugin.economy();
        msg.send(sender, "top-header",
                Placeholder.component("title", msg.get("top-board-" + board.key())),
                Placeholder.component("period", msg.get("top-period-" + period.key())),
                Placeholder.unparsed("item", item == null ? "" : " - " + ItemNames.pretty(item)));
        if (rows.isEmpty()) {
            msg.send(sender, "top-empty");
            return;
        }
        int rank = 1;
        for (Row row : rows) {
            String value = board.isMoney() ? eco.format(row.value()) : String.valueOf((long) row.value());
            if (board == Board.BIGGEST) {
                msg.send(sender, "top-line-biggest",
                        Placeholder.unparsed("rank", String.valueOf(rank++)),
                        Placeholder.unparsed("player", String.valueOf(row.player())),
                        Placeholder.unparsed("value", value),
                        Placeholder.component("action", msg.get("top-action-" + String.valueOf(row.action()).toLowerCase(Locale.ROOT))),
                        Placeholder.unparsed("amount", String.valueOf(row.amount())),
                        Placeholder.unparsed("item", row.item() == null ? "?" : ItemNames.pretty(row.item())));
            } else {
                msg.send(sender, "top-line",
                        Placeholder.unparsed("rank", String.valueOf(rank++)),
                        Placeholder.unparsed("player", String.valueOf(row.player())),
                        Placeholder.unparsed("value", value));
            }
        }
    }

    private void limits(CommandSender sender) {
        Messages msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return;
        }
        if (!allowed(sender, "lodestock.use")) return;
        var limits = plugin.limits();
        List<MarketItem> limited = plugin.market().items().stream()
                .filter(item -> limits.limitFor(item, true) > 0 || limits.limitFor(item, false) > 0)
                .toList();
        if (limited.isEmpty()) {
            msg.send(sender, "limits-none");
            return;
        }
        msg.send(sender, "limits-header", Placeholder.unparsed("reset", limits.resetsIn()));
        for (MarketItem item : limited) {
            msg.send(sender, "limits-line",
                    Placeholder.unparsed("item", ItemNames.pretty(item.id())),
                    Placeholder.unparsed("buy", limits.describe(limits.remainingBuy(player, item), limits.limitFor(item, true))),
                    Placeholder.unparsed("sell", limits.describe(limits.remainingSell(player, item), limits.limitFor(item, false))));
        }
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
        if (!done(sender, () -> plugin.admin().setPrice(sender.getName(), id, price))) return;
        msg.send(sender, "setprice-success",
                Placeholder.unparsed("item", ItemNames.pretty(id)),
                Placeholder.unparsed("price", plugin.economy().format(price)));
    }

    /** Runs a change through MarketAdmin. False (after telling the sender why) if it failed or another plugin cancelled it. */
    private boolean done(CommandSender sender, java.util.function.Supplier<MarketAdmin.Outcome> change) {
        Messages msg = plugin.messages();
        MarketAdmin.Outcome outcome;
        try {
            outcome = change.get();
        } catch (IllegalArgumentException e) {
            msg.send(sender, "admin-error", Placeholder.unparsed("error", e.getMessage()));
            return false;
        }
        if (outcome.done()) return true;
        if (outcome.cancelMessage() != null) sender.sendMessage(outcome.cancelMessage());
        else msg.send(sender, "admin-cancelled");
        return false;
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
        final int newStock = stock;
        if (!done(sender, () -> plugin.admin().setStock(sender.getName(), id, newStock))) return;
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
            if (!done(sender, () -> plugin.admin().resetAll(sender.getName()))) return;
            msg.send(sender, "reset-all-success");
            return;
        }
        String id = knownItem(sender, args[1]);
        if (id == null) return;
        if (!done(sender, () -> plugin.admin().reset(sender.getName(), id))) return;
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

        final String target = id;
        final double signed = crash ? -percent : percent;
        if (!done(sender, () -> plugin.admin().adjust(sender.getName(), target, signed))) return;

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

    private void audit(CommandSender sender) {
        Messages msg = plugin.messages();
        if (!allowed(sender, "lodestock.admin.audit")) return;
        Market market = plugin.market();
        Map<String, Double> live = new HashMap<>();
        for (MarketItem item : market.items()) {
            market.state(item.id()).ifPresent(state -> live.put(item.id(), state.price()));
        }
        var limits = plugin.limits().settings();
        EconomyAudit.Report report = EconomyAudit.run(market.settings(), market.recovery(),
                limits.dailyBuy(), limits.dailySell(), market.items(), live);

        long warns = report.count(EconomyAudit.Level.WARN);
        long notes = report.count(EconomyAudit.Level.NOTE);
        msg.send(sender, "audit-header",
                Placeholder.unparsed("warnings", String.valueOf(warns)),
                Placeholder.unparsed("notes", String.valueOf(notes)));
        if (warns == 0) msg.send(sender, "audit-ok");

        List<EconomyAudit.Finding> sorted = new ArrayList<>(report.findings());
        sorted.sort(Comparator.comparing(EconomyAudit.Finding::level)); // warnings first
        for (int i = 0; i < Math.min(MAX_AUDIT_LINES, sorted.size()); i++) {
            EconomyAudit.Finding f = sorted.get(i);
            msg.send(sender, f.level() == EconomyAudit.Level.WARN ? "audit-warn-line" : "audit-note-line",
                    Placeholder.component("text", auditText(f)));
        }
        if (sorted.size() > MAX_AUDIT_LINES) {
            msg.send(sender, "audit-more", Placeholder.unparsed("more", String.valueOf(sorted.size() - MAX_AUDIT_LINES)));
        }

        if (!report.ceilings().isEmpty()) {
            msg.send(sender, "audit-ceiling", Placeholder.unparsed("total", plugin.economy().format(report.totalCeiling())));
            report.ceilings().stream().limit(3).forEach(c -> msg.send(sender, "audit-ceiling-line",
                    Placeholder.unparsed("item", ItemNames.pretty(c.item())),
                    Placeholder.unparsed("money", plugin.economy().format(c.perDay()))));
        }
        msg.send(sender, "audit-footer");
    }

    /** Turns one finding into its sentence from the language file (audit.<code>). */
    private net.kyori.adventure.text.Component auditText(EconomyAudit.Finding f) {
        List<String> a = f.args();
        var eco = plugin.economy();
        List<TagResolver> tags = new ArrayList<>();
        switch (f.code()) {
            case "tax-low" -> tags.add(Placeholder.unparsed("tax", a.get(0)));
            case "multiplier-high" -> tags.add(Placeholder.unparsed("value", a.get(0)));
            case "floor-low" -> {
                tags.add(Placeholder.unparsed("floor", a.get(0)));
                tags.add(Placeholder.unparsed("cheapest", a.get(1)));
            }
            case "round-trip" -> {
                tags.add(Placeholder.unparsed("item", ItemNames.pretty(a.get(0))));
                tags.add(Placeholder.unparsed("way", plugin.messages().plain("audit.way-" + a.get(1))));
                tags.add(Placeholder.unparsed("amount", a.get(2)));
                tags.add(Placeholder.unparsed("profit", eco.format(Double.parseDouble(a.get(3)))));
            }
            case "crafting-loop", "crafting-loop-now" -> {
                tags.add(Placeholder.unparsed("from", ItemNames.pretty(a.get(0))));
                tags.add(Placeholder.unparsed("to", ItemNames.pretty(a.get(1))));
                tags.add(Placeholder.unparsed("profit", eco.format(Double.parseDouble(a.get(3)))));
            }
            default -> { }
        }
        return plugin.messages().get("audit." + f.code(), tags.toArray(new TagResolver[0]));
    }

    private void economy(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!allowed(sender, "lodestock.admin.economy")) return;
        String rangeText = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "24h";
        long hours = HourSpan.parse(rangeText);
        if (hours <= 0) {
            msg.send(sender, "usage-economy");
            return;
        }
        long span = hours * 3_600_000L;
        long since = System.currentTimeMillis() - span;
        var current = plugin.tradeLog().summaryAsync(since, 5);
        var before = plugin.tradeLog().summaryAsync(since - span, since, 1);
        current.thenCombine(before, (now, earlier) -> new TradeLog.Summary[]{now, earlier}).thenAccept(both ->
                plugin.getServer().getScheduler().runTask(plugin, () -> showEconomy(sender, rangeText, both[0], both[1])));
    }

    private void showEconomy(CommandSender sender, String range, TradeLog.Summary summary, TradeLog.Summary earlier) {
        Messages msg = plugin.messages();
        var eco = plugin.economy();
        TagResolver rangeTag = Placeholder.unparsed("range", range);
        if (summary.trades() == 0) {
            msg.send(sender, "economy-none", rangeTag);
            return;
        }
        msg.send(sender, "economy-header", rangeTag);
        msg.send(sender, "economy-totals",
                Placeholder.unparsed("trades", String.valueOf(summary.trades())),
                Placeholder.unparsed("players", String.valueOf(summary.players())));
        msg.send(sender, "economy-flow",
                Placeholder.unparsed("in", eco.format(summary.paidIn())),
                Placeholder.unparsed("out", eco.format(summary.paidOut())));
        double created = summary.created();
        msg.send(sender, created >= 0 ? "economy-net-up" : "economy-net-down",
                Placeholder.unparsed("net", eco.format(Math.abs(created))));
        if (summary.players() > 0) {
            double each = created / summary.players();
            msg.send(sender, "economy-per-player",
                    Placeholder.unparsed("value", (each >= 0 ? "+" : "-") + eco.format(Math.abs(each))));
        }
        if (earlier.trades() > 0) {
            double was = earlier.created();
            String change;
            if (Math.abs(was) < 0.005) change = msg.plain("economy-change-new");
            else {
                double percent = (created - was) / Math.abs(was) * 100.0;
                change = msg.plain(percent >= 0 ? "economy-change-up" : "economy-change-down")
                        .replace("<percent>", PERCENT.format(Math.abs(percent)));
            }
            msg.send(sender, "economy-compare", rangeTag,
                    Placeholder.unparsed("change", change),
                    Placeholder.unparsed("was", (was >= 0 ? "+" : "-") + eco.format(Math.abs(was))));
        }
        if (!summary.topEarners().isEmpty()) {
            msg.send(sender, "economy-top-header");
            int rank = 1;
            for (TradeLog.PlayerNet p : summary.topEarners()) {
                msg.send(sender, p.net() >= 0 ? "economy-top-up" : "economy-top-down",
                        Placeholder.unparsed("rank", String.valueOf(rank++)),
                        Placeholder.unparsed("player", p.player()),
                        Placeholder.unparsed("net", eco.format(Math.abs(p.net()))));
            }
        }
        if (!summary.topItems().isEmpty()) {
            msg.send(sender, "economy-items-header");
            for (TradeLog.ItemPayout i : summary.topItems().stream().limit(3).toList()) {
                msg.send(sender, "economy-items-line",
                        Placeholder.unparsed("item", ItemNames.pretty(i.item())),
                        Placeholder.unparsed("money", eco.format(i.paidOut())));
            }
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
            if (sender.hasPermission("lodestock.use")) options.addAll(List.of("open", "price", "limits", "chart"));
            if (sender.hasPermission("lodestock.top")) options.add("top");
            if (sender.hasPermission("lodestock.sell.hand")) options.add("sellhand");
            if (sender.hasPermission("lodestock.sell.all")) options.add("sellall");
            for (String name : ADMIN_COMMANDS) {
                if (sender.hasPermission("lodestock.admin." + name)) options.add(name);
            }
            return filter(options, typed);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("price") || sub.equals("chart") || sub.equals("setprice") || sub.equals("setstock")) return itemIds(typed);
            if (sub.equals("reset")) {
                List<String> options = new ArrayList<>(itemIds(typed));
                if ("all".startsWith(typed)) options.add(0, "all");
                return options;
            }
            if (sub.equals("crash") || sub.equals("surge")) return filter(List.of("10", "25", "50"), typed);
            if (sub.equals("sellall")) return filter(List.of("confirm"), typed);
            if (sub.equals("economy")) return filter(List.of("24h", "7d", "30d"), typed);
            if (sub.equals("top") && sender.hasPermission("lodestock.top")) {
                return filter(Arrays.stream(Board.values()).filter(b -> canSeeBoard(sender, b)).map(Board::key).toList(), typed);
            }
            if (sub.equals("history")) {
                return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), typed);
            }
        }
        if (args.length >= 3 && sub.equals("top") && sender.hasPermission("lodestock.top")) {
            List<String> options = new ArrayList<>(List.of("24h", "7d", "30d", "all"));
            options.addAll(itemIds(typed));
            return filter(options, typed);
        }
        if (args.length == 3) {
            if (sub.equals("chart")) return filter(List.of("24h", "7d", "all"), typed);
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