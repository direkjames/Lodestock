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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TradeService {
    private static final int MAX_BREAKDOWN_LINES = 12;

    private record Entry(Material material, String id, int count, double total) {}

    private record Plan(List<Entry> entries, List<String> skipped, double total, int items) {}

    private final LodestockPlugin plugin;
    private final Map<UUID, Long> pendingSellAll = new HashMap<>();  // when the confirmation expires
    private final Map<UUID, Long> sellAllCooldown = new HashMap<>(); // when /sellall works again

    public TradeService(LodestockPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- buying and selling one item type (used by the GUI) ----------

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
        plugin.tradeLog().trade(player, "BUY", itemId, count, quote.total());

        msg.send(player, "buy-success",
                Placeholder.unparsed("amount", String.valueOf(count)),
                Placeholder.unparsed("item", ItemNames.pretty(itemId)),
                Placeholder.unparsed("money", eco.format(quote.total())));
    }

    /** @param amount how many to sell, or -1 for everything of this item the player carries */
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
        plugin.tradeLog().trade(player, "SELL", itemId, count, quote.total());

        msg.send(player, "sell-success",
                Placeholder.unparsed("amount", String.valueOf(count)),
                Placeholder.unparsed("item", ItemNames.pretty(itemId)),
                Placeholder.unparsed("money", eco.format(quote.total())));
    }

    // ---------- /lodestock sellhand ----------

    public void sellHand(Player player) {
        Messages msg = plugin.messages();
        Market market = plugin.market();
        EconomyHook eco = plugin.economy();

        if (!eco.available()) {
            msg.send(player, "economy-missing");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            msg.send(player, "sellhand-empty");
            return;
        }
        Material material = hand.getType();
        String id = material.getKey().getKey();
        if (market.item(id).isEmpty()) {
            msg.send(player, "unknown-item", Placeholder.unparsed("item", ItemNames.pretty(id)));
            return;
        }
        // Renamed, enchanted or otherwise modified items are not the plain item, so they can't be sold.
        if (!hand.isSimilar(new ItemStack(material))) {
            msg.send(player, "no-items", Placeholder.unparsed("item", ItemNames.pretty(id)));
            return;
        }

        int amount = hand.getAmount();
        BulkQuote quote = market.previewSell(id, amount);
        if (!quote.ok()) {
            failure(player, quote.status());
            return;
        }
        int count = quote.count();

        // Pay first. If the payment is refused (e.g. money cap), nothing is taken.
        if (!eco.deposit(player, quote.total())) {
            msg.send(player, "transaction-failed");
            return;
        }
        if (count >= amount) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            ItemStack rest = hand.clone();
            rest.setAmount(amount - count);
            player.getInventory().setItemInMainHand(rest);
        }
        market.recordSell(id, count);
        plugin.tradeLog().trade(player, "SELL", id, count, quote.total());

        msg.send(player, "sell-success",
                Placeholder.unparsed("amount", String.valueOf(count)),
                Placeholder.unparsed("item", ItemNames.pretty(id)),
                Placeholder.unparsed("money", eco.format(quote.total())));
        if (count < amount) {
            msg.send(player, "sell-partial", Placeholder.unparsed("count", String.valueOf(count)));
        }
    }

    // ---------- /lodestock sellall ----------

    /** First step: shows what would be sold and asks for confirmation (or sells right away if confirmation is off). */
    public void sellAllRequest(Player player) {
        Messages msg = plugin.messages();
        if (!plugin.economy().available()) {
            msg.send(player, "economy-missing");
            return;
        }
        long now = System.currentTimeMillis();
        purgeExpired(now);
        if (onCooldown(player, now)) return;

        Plan plan = plan(player);
        if (plan.entries().isEmpty()) {
            msg.send(player, "sellall-nothing");
            sendSkipped(player, plan);
            return;
        }
        if (!plugin.getConfig().getBoolean("sell-all.confirm", true)) {
            executeSellAll(player);
            return;
        }

        int seconds = Math.max(5, plugin.getConfig().getInt("sell-all.confirm-seconds", 15));
        pendingSellAll.put(player.getUniqueId(), now + seconds * 1000L);
        msg.send(player, "sellall-confirm",
                Placeholder.unparsed("items", String.valueOf(plan.items())),
                Placeholder.unparsed("money", plugin.economy().format(plan.total())),
                Placeholder.unparsed("seconds", String.valueOf(seconds)));
        sendSkipped(player, plan);
    }

    /** Second step: only works shortly after sellAllRequest. */
    public void sellAllConfirm(Player player) {
        long now = System.currentTimeMillis();
        purgeExpired(now);
        Long expires = pendingSellAll.remove(player.getUniqueId());
        if (expires == null || now > expires) {
            plugin.messages().send(player, "sellall-no-pending");
            return;
        }
        if (onCooldown(player, now)) return;
        executeSellAll(player);
    }

    private void executeSellAll(Player player) {
        Messages msg = plugin.messages();
        EconomyHook eco = plugin.economy();

        // Prices are worked out again now, so the player gets what the market pays at this moment.
        Plan plan = plan(player);
        if (plan.entries().isEmpty()) {
            msg.send(player, "sellall-nothing");
            return;
        }
        // One payment for everything. If it is refused, nothing is taken.
        if (!eco.deposit(player, plan.total())) {
            msg.send(player, "transaction-failed");
            return;
        }
        for (Entry entry : plan.entries()) {
            // The plan was counted from this same inventory a moment ago, so this always succeeds.
            removeItems(player, entry.material(), entry.count());
            plugin.market().recordSell(entry.id(), entry.count());
            plugin.tradeLog().trade(player, "SELL", entry.id(), entry.count(), entry.total());
        }

        int cooldown = plugin.getConfig().getInt("sell-all.cooldown-seconds", 30);
        if (cooldown > 0) sellAllCooldown.put(player.getUniqueId(), System.currentTimeMillis() + cooldown * 1000L);

        msg.send(player, "sellall-success",
                Placeholder.unparsed("items", String.valueOf(plan.items())),
                Placeholder.unparsed("money", eco.format(plan.total())));
        List<Entry> entries = plan.entries();
        for (int i = 0; i < Math.min(MAX_BREAKDOWN_LINES, entries.size()); i++) {
            Entry e = entries.get(i);
            msg.send(player, "sellall-line",
                    Placeholder.unparsed("amount", String.valueOf(e.count())),
                    Placeholder.unparsed("item", ItemNames.pretty(e.id())),
                    Placeholder.unparsed("money", eco.format(e.total())));
        }
        if (entries.size() > MAX_BREAKDOWN_LINES) {
            msg.send(player, "sellall-more", Placeholder.unparsed("more", String.valueOf(entries.size() - MAX_BREAKDOWN_LINES)));
        }
        sendSkipped(player, plan);
    }

    /** Works out what a sellall would do right now. Only the hotbar and main inventory count. */
    private Plan plan(Player player) {
        Market market = plugin.market();
        Map<Material, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            Material material = stack.getType();
            if (market.item(material.getKey().getKey()).isEmpty()) continue;
            if (!stack.isSimilar(new ItemStack(material))) continue; // renamed or modified items are skipped
            counts.merge(material, stack.getAmount(), Integer::sum);
        }

        List<Entry> entries = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        double total = 0;
        int items = 0;
        for (Map.Entry<Material, Integer> e : counts.entrySet()) {
            String id = e.getKey().getKey().getKey();
            BulkQuote quote = market.previewSell(id, e.getValue());
            if (!quote.ok()) {
                skipped.add(ItemNames.pretty(id));
                continue;
            }
            entries.add(new Entry(e.getKey(), id, quote.count(), quote.total()));
            total += quote.total();
            items += quote.count();
            if (quote.count() < e.getValue()) skipped.add(ItemNames.pretty(id));
        }
        return new Plan(entries, skipped, Math.round(total * 100.0) / 100.0, items);
    }

    private boolean onCooldown(Player player, long now) {
        Long until = sellAllCooldown.get(player.getUniqueId());
        if (until == null || now >= until) return false;
        long seconds = (until - now + 999) / 1000;
        plugin.messages().send(player, "sellall-cooldown", Placeholder.unparsed("seconds", String.valueOf(seconds)));
        return true;
    }

    private void purgeExpired(long now) {
        pendingSellAll.values().removeIf(expires -> now > expires);
        sellAllCooldown.values().removeIf(until -> now >= until);
    }

    private void sendSkipped(Player player, Plan plan) {
        if (plan.skipped().isEmpty()) return;
        plugin.messages().send(player, "sellall-skipped",
                Placeholder.unparsed("items", String.join(", ", plan.skipped())));
    }

    // ---------- shared helpers ----------

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