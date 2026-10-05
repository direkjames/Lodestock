package io.github.direkjames.lodestock.paper.command;

import io.github.direkjames.lodestock.core.market.ItemState;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.Quote;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.config.Messages;
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
    private static final int MAX_WARNINGS_SHOWN = 10;

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
            case "help" -> msg.send(sender, "help");
            case "open", "gui" -> open(sender);
            case "price" -> price(sender, args);
            case "reload" -> reload(sender);
            default -> msg.send(sender, "unknown-subcommand");
        }
        return true;
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

    private void reload(CommandSender sender) {
        Messages msg = plugin.messages();
        if (!sender.hasPermission("lodestock.admin.reload")) {
            msg.send(sender, "no-permission");
            return;
        }
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

    /** "Minecraft:Diamond" and "diamond" both become "diamond". */
    static String normalizeId(String input) {
        String id = input.trim().toLowerCase(Locale.ROOT);
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help"));
            if (sender.hasPermission("lodestock.use")) options.addAll(List.of("open", "price"));
            if (sender.hasPermission("lodestock.admin.reload")) options.add("reload");
            return options.stream().filter(o -> o.startsWith(typed)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("price")) {
            return plugin.market().items().stream()
                    .map(MarketItem::id)
                    .filter(id -> id.startsWith(typed))
                    .limit(30)
                    .toList();
        }
        return List.of();
    }
}