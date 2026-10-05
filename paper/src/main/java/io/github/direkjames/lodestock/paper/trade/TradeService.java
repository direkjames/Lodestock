package io.github.direkjames.lodestock.paper.trade;

import io.github.direkjames.lodestock.core.market.BulkQuote;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.Quote;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.config.Messages;
import io.github.direkjames.lodestock.paper.economy.EconomyHook;
import io.github.direkjames.lodestock.paper.util.ItemNames;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class TradeService {
    /** Most items one command can move (a full inventory of 64-stacks). */
    public static final int MAX_AMOUNT = 2304;

    private final LodestockPlugin plugin;

    public TradeService(LodestockPlugin plugin) {
        this.plugin = plugin;
    }

    public void buy(Player player, String itemId, int amount) {
        Messages msg = plugin.messages();
        Market market = plugin.market();
        EconomyHook eco = plugin.economy();

        if (!eco.available()) {
            msg.send(player, "economy-missing");
            return;
        }
        Material material = Material.matchMaterial(itemId);
        if (material == null || market.item(itemId).isEmpty()) {
            msg.send(player, "unknown-item", Placeholder.unparsed("item", itemId));
            return;
        }

        BulkQuote quote = market.previewBuy(itemId, amount);
        if (!quote.ok()) {
            failure(player, quote.status());
            return;
        }

        int count = Math.min(quote.count(), roomFor(player, material));
        if (count == 0) {
            msg.send(player, "not-enough-room");
            return;
        }
        if (count < quote.count()) quote = market.previewBuy(itemId, count);

        if (!eco.has(player, quote.total())) {
            msg.send(player, "not-enough-money", Placeholder.unparsed("money", eco.format(quote.total())));
            return;
        }
        if (!eco.withdraw(player, quote.total())) {
            msg.send(player, "transaction-failed");
            return;
        }

        // Safety net: anything that somehow doesn't fit is dropped, never lost.
        player.getInventory().addItem(new ItemStack(material, count)).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        market.recordBuy(itemId, count);

        msg.send(player, "buy-success",
                Placeholder.unparsed("amount", String.valueOf(count)),
                Placeholder.unparsed("item", ItemNames.pretty(itemId)),
                Placeholder.unparsed("money", eco.format(quote.total())));
    }

    /** @param amount how many to sell, or -1 for everything the player carries */
    public void sell(Player player, String itemId, int amount) {
        Messages msg = plugin.messages();
        Market market = plugin.market();
        EconomyHook eco = plugin.economy();

        if (!eco.available()) {
            msg.send(player, "economy-missing");
            return;
        }
        Material material = Material.matchMaterial(itemId);
        if (material == null || market.item(itemId).isEmpty()) {
            msg.send(player, "unknown-item", Placeholder.unparsed("item", itemId));
            return;
        }

        int have = countItems(player, material);
        if (have == 0) {
            msg.send(player, "no-items", Placeholder.unparsed("item", ItemNames.pretty(itemId)));
            return;
        }
        if (amount > have) {
            msg.send(player, "not-enough-items",
                    Placeholder.unparsed("item", ItemNames.pretty(itemId)),
                    Placeholder.unparsed("have", String.valueOf(have)));
            return;
        }
        int want = amount < 0 ? have : amount;

        BulkQuote quote = market.previewSell(itemId, want);
        if (!quote.ok()) {
            failure(player, quote.status());
            return;
        }
        int count = quote.count();

        // Take the items first, pay second. If the payment is refused (e.g. money cap), give them back.
        if (!removeItems(player, material, count)) {
            msg.send(player, "no-items", Placeholder.unparsed("item", ItemNames.pretty(itemId)));
            return;
        }
        if (!eco.deposit(player, quote.total())) {
            player.getInventory().addItem(new ItemStack(material, count)).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            msg.send(player, "transaction-failed");
            return;
        }
        market.recordSell(itemId, count);

        msg.send(player, "sell-success",
                Placeholder.unparsed("amount", String.valueOf(count)),
                Placeholder.unparsed("item", ItemNames.pretty(itemId)),
                Placeholder.unparsed("money", eco.format(quote.total())));
    }

    private void failure(Player player, Quote.Status status) {
        Messages msg = plugin.messages();
        switch (status) {
            case UNKNOWN_ITEM -> msg.send(player, "unknown-item", Placeholder.unparsed("item", "?"));
            case BUY_DISABLED -> msg.send(player, "buy-disabled");
            case SELL_DISABLED -> msg.send(player, "sell-disabled");
            case OUT_OF_STOCK -> msg.send(player, "out-of-stock");
            case STOCK_FULL -> msg.send(player, "stock-full");
            case PRICE_TOO_LOW -> msg.send(player, "price-too-low");
            default -> msg.send(player, "transaction-failed");
        }
    }

    /** How many of this item fit in the player's main inventory. */
    private int roomFor(Player player, Material material) {
        ItemStack plain = new ItemStack(material);
        int max = material.getMaxStackSize();
        int room = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) room += max;
            else if (stack.isSimilar(plain)) room += Math.max(0, max - stack.getAmount());
        }
        return room;
    }

    /** Counts only plain items, so renamed or enchanted ones can't be sold as normal ones. */
    private int countItems(Player player, Material material) {
        ItemStack plain = new ItemStack(material);
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.isSimilar(plain)) total += stack.getAmount();
        }
        return total;
    }

    private boolean removeItems(Player player, Material material, int count) {
        if (countItems(player, material) < count) return false;
        PlayerInventory inventory = player.getInventory();
        ItemStack plain = new ItemStack(material);
        ItemStack[] contents = inventory.getStorageContents();
        int left = count;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !stack.isSimilar(plain)) continue;
            int take = Math.min(left, stack.getAmount());
            if (take == stack.getAmount()) contents[i] = null;
            else stack.setAmount(stack.getAmount() - take);
            left -= take;
        }
        inventory.setStorageContents(contents);
        return left == 0;
    }
}