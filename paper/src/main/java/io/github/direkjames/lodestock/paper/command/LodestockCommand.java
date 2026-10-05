package io.github.direkjames.lodestock.paper.command;

import io.github.direkjames.lodestock.core.market.ItemState;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.Quote;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.config.Messages;
import io.github.direkjames.lodestock.paper.trade.TradeService;
import io.github.direkjames.lodestock.paper.util.ItemNames;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LodestockCommand implements TabExecutor {
    private static final int PAGE_SIZE = 8;

    private final LodestockPlugin plugin;

    public LodestockCommand(LodestockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Messages msg = plugin.messages();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> msg.send(sender, "help");
            case "list" -> list(sender, args);
            case "price" -> price(sender, args);
            case "buy" -> buy(sender, args);
            case "sell" -> sell(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("lodestock.admin")) {
                    msg.send(sender, "no-permission");
                    return true;
                }
                if (plugin.loadMarket()) {
                    msg.send(sender, "reloaded", Placeholder.parsed("items", String.valueOf(plugin.market().items().size())));
                } else {
                    msg.send(sender, "reload-failed");
                }
            }
            default -> msg.send(sender, "unknown-subcommand");
        }
        return true;
    }

    private void buy(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("lodestock.buy")) {
            msg.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "usage-buy");
            return;
        }
        int amount = 1;
        if (args.length > 2) {
            Integer parsed = parseAmount(args[2]);
            if (parsed == null) {
                msg.send(sender, "invalid-amount", Placeholder.parsed("max", String.valueOf(TradeService.MAX_AMOUNT)));
                return;
            }
            amount = parsed;
        }
        plugin.trades().buy(player, normalizeId(args[1]), amount);
    }

    private void sell(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("lodestock.sell")) {
            msg.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "usage-sell");
            return;
        }
        int amount = 1;
        if (args.length > 2) {
            if (args[2].equalsIgnoreCase("all")) {
                amount = -1;
            } else {
                Integer parsed = parseAmount(args[2]);
                if (parsed == null) {
                    msg.send(sender, "invalid-amount", Placeholder.parsed("max", String.valueOf(TradeService.MAX_AMOUNT)));
                    return;
                }
                amount = parsed;
            }
        }
        plugin.trades().sell(player, normalizeId(args[1]), amount);
    }

    private void price(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!sender.hasPermission("lodestock.use")) {
            msg.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "usage-price");
            return;
        }
        Market market = plugin.market();
        String id = normalizeId(args[1]);
        MarketItem item = market.item(id).orElse(null);
        if (item == null) {
            msg.send(sender, "unknown-item", Placeholder.unparsed("item", args[1]));
            return;
        }
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

    private void list(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!sender.hasPermission("lodestock.use")) {
            msg.send(sender, "no-permission");
            return;
        }
        Market market = plugin.market();
        List<MarketItem> items = new ArrayList<>(market.items());
        if (items.isEmpty()) {
            msg.send(sender, "list-empty");
            return;
        }
        int pages = (items.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        page = Math.max(1, Math.min(page, pages));

        msg.send(sender, "list-header",
                Placeholder.parsed("page", String.valueOf(page)),
                Placeholder.parsed("pages", String.valueOf(pages)));
        int from = (page - 1) * PAGE_SIZE;
        for (MarketItem item : items.subList(from, Math.min(items.size(), from + PAGE_SIZE))) {
            ItemState s = market.state(item.id()).orElseThrow();
            msg.send(sender, "list-line",
                    Placeholder.unparsed("item", ItemNames.pretty(item.id())),
                    Placeholder.unparsed("price", plugin.economy().format(s.price())),
                    Placeholder.unparsed("stock", String.valueOf(s.stock())),
                    Placeholder.unparsed("max", String.valueOf(item.maxStock())));
        }
    }

    /** "diamond" becomes "minecraft:diamond". IDs with a namespace are left alone. */
    private static String normalizeId(String input) {
        String id = input.toLowerCase(Locale.ROOT);
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static Integer parseAmount(String input) {
        try {
            int amount = Integer.parseInt(input);
            return amount >= 1 && amount <= TradeService.MAX_AMOUNT ? amount : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help", "list", "price", "buy", "sell"));
            if (sender.hasPermission("lodestock.admin")) options.add("reload");
            return options.stream().filter(o -> o.startsWith(typed)).toList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean tradeCommand = sub.equals("buy") || sub.equals("sell") || sub.equals("price");
        if (args.length == 2 && tradeCommand) {
            return plugin.market().items().stream()
                    .map(i -> i.id().startsWith("minecraft:") ? i.id().substring("minecraft:".length()) : i.id())
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(typed))
                    .limit(30)
                    .toList();
        }
        if (args.length == 3 && sub.equals("buy")) return List.of("1", "16", "64");
        if (args.length == 3 && sub.equals("sell")) return List.of("1", "16", "64", "all");
        return List.of();
    }
}