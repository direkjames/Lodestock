package io.github.direkjames.lodestock.paper.command;

import io.github.direkjames.lodestock.core.market.ItemState;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.config.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
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
                    Placeholder.parsed("item", prettyName(item.id())),
                    Placeholder.parsed("price", String.format(Locale.ROOT, "%.2f", s.price())),
                    Placeholder.parsed("stock", String.valueOf(s.stock())),
                    Placeholder.parsed("max", String.valueOf(item.maxStock())));
        }
    }

    private static String prettyName(String id) {
        String name = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        return name.isEmpty() ? id : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        List<String> options = new ArrayList<>(List.of("help", "list"));
        if (sender.hasPermission("lodestock.admin")) options.add("reload");
        String typed = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(typed)).toList();
    }
}