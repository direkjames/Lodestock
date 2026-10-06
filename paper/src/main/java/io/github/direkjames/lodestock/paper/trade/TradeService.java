package io.github.direkjames.lodestock.paper.trade;

import io.github.direkjames.lodestock.api.TradeType;
import io.github.direkjames.lodestock.api.event.LodestockPreTradeEvent;
import io.github.direkjames.lodestock.api.event.LodestockTradeEvent;
import io.github.direkjames.lodestock.core.market.BulkQuote;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.Quote;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.config.Messages;
import io.github.direkjames.lodestock.paper.economy.EconomyHook;
import io.github.direkjames.lodestock.paper.limits.DailyLimits;
import io.github.direkjames.lodestock.paper.permission.OrePermissions;
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

        if (!OrePermissions.can(player, itemId)) {
            locked(player, itemId);
            return;
        }
        MarketItem item = market.item(itemId).orElseThrow();
        int left = plugin.limits().remainingBuy(player, item);
        if (left == 0) {
            limitReached(player, item, true);
            return;
        }

        BulkQuote quote = market.previewBuy(itemId, Math.min(amount, left));
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
        if (!allowTrade(player, TradeType.BUY, itemId, count, quote.total())) return;
        if (!eco.withdraw(player, quote.total())) {
            msg.send(player, "transaction-failed");
            return;
        }

        // Safety net: anything that somehow doesn't fit is dropped, never lost.
        player.getInventory().addItem(new ItemStack(material, count)).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        market.recordBuy(itemId, count);
        plugin.limits().recordBuy(player, item, count);
        plugin.tradeLog().trade(player, "BUY", itemId, count, quote.total());
        announce(player, TradeType.BUY, itemId, count, quote.total());

        msg.send(player, "buy-success",
                Placeholder.unparsed("amount", String.valueOf(count)),
                Placeholder.unparsed("item", ItemNames.pretty(itemId)),
                Placeholder.unparsed("money", eco.format(quote.total())));
        limitNotice(player, item, true);
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

        if (!OrePermissions.can(player, itemId)) {
            locked(player, itemId);
            return;
        }
        MarketItem item = market.item(itemId).orElseThrow();
        int left = plugin.limits().remainingSell(player, item);
        if (left == 0) {
            limitReached(player, item, false);
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
        int want = Math.min(amount < 0 ? have : amount, left);

        BulkQuote quote = market.previewSell(itemId, want);
        if (!quote.ok()) {
            failure(player, quote.status());
            return;
        }
        int count = quote.count();

        if (!allowTrade(player, TradeType.SELL, itemId, count, quote.total())) return;

        // Take the items first, pay second. If the payment is refused (e.g. money cap), give them back.
        if (!removeItems(player, material, count)) {
            msg.send(player, "no-items", Placeholder.unparsed("item", ItemNames.pretty(itemId)));
            return;
        }
        if (!eco.deposit(player, quote.total())) {
            player.getInventory().addItem(new ItemStack(material, count)).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            msg.send(player, "transaction-failed");
            return;
        }
        market.recordSell(itemId, count);
        plugin.limits().recordSell(player, item, count);
        plugin.tradeLog().trade(player, "SELL", itemId, count, quote.total());
        announce(player, TradeType.SELL, itemId, count, quote.total());

        msg.send(player, "sell-success",
                Placeholder.unparsed("amount", String.valueOf(count)),
                Placeholder.unparsed("item", ItemNames.pretty(itemId)),
                Placeholder.unparsed("money", eco.format(quote.total())));
        limitNotice(player, item, false);
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

        if (!OrePermissions.can(player, id)) {
            locked(player, id);
            return;
        }
        MarketItem item = market.item(id).orElseThrow();
        int left = plugin.limits().remainingSell(player, item);
        if (left == 0) {
            limitReached(player, item, false);
            return;
        }

        int amount = hand.getAmount();
        BulkQuote quote = market.previewSell(id, Math.min(amount, left));
        if (!quote.ok()) {
            failure(player, quote.status());
            return;
        }
        int count = quote.count();

        if (!allowTrade(player, TradeType.SELL, id, count, quote.total())) return;

        // Another plugin may have changed what is in the hand while it looked at the trade: check again.
        ItemStack current = player.getInventory().getItemInMainHand();
        if (current.getType() != material || !current.isSimilar(new ItemStack(material)) || current.getAmount() < count) {
            msg.send(player, "no-items", Placeholder.unparsed("item", ItemNames.pretty(id)));
            return;
        }
        amount = current.getAmount();

        // Pay first. If the payment is refused (e.g. money cap), nothing is taken.
        if (!eco.deposit(player, quote.total())) {
            msg.send(player, "transaction-failed");
            return;
        }
        if (count >= amount) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            ItemStack rest = current.clone();
            rest.setAmount(amount - count);
            player.getInventory().setItemInMainHand(rest);
        }
        market.recordSell(id, count);
        plugin.limits().recordSell(player, item, count);
        plugin.tradeLog().trade(player, "SELL", id, count, quote.total());
        announce(player, TradeType.SELL, id, count, quote.total());

        msg.send(player, "sell-success",
                Placeholder.unparsed("amount", String.valueOf(count)),
                Placeholder.unparsed("item", ItemNames.pretty(id)),
                Placeholder.unparsed("money", eco.format(quote.total())));
        if (count < amount) {
            msg.send(player, "sell-partial", Placeholder.unparsed("count", String.valueOf(count)));
        }
        limitNotice(player, item, false);
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
        // Other plugins may veto single items; the rest still sells.
        plan = withoutCancelled(player, plan);
        if (plan.entries().isEmpty()) {
            return;
        }
        // Take the items first. Another plugin may have changed the inventory while it looked at the trade,
        // so if anything is missing, everything taken so far is given back and nothing is paid.
        List<Entry> taken = new ArrayList<>();
        for (Entry entry : plan.entries()) {
            if (!removeItems(player, entry.material(), entry.count())) {
                for (Entry back : taken) give(player, back.material(), back.count());
                msg.send(player, "transaction-failed");
                return;
            }
            taken.add(entry);
        }
        // One payment for everything. If it is refused, the items go back.
        if (!eco.deposit(player, plan.total())) {
            for (Entry back : taken) give(player, back.material(), back.count());
            msg.send(player, "transaction-failed");
            return;
        }
        for (Entry entry : plan.entries()) {
            plugin.market().recordSell(entry.id(), entry.count());
            plugin.limits().recordSell(player, plugin.market().item(entry.id()).orElseThrow(), entry.count());
            plugin.tradeLog().trade(player, "SELL", entry.id(), entry.count(), entry.total());
        }
        // Other plugins hear about it only once everything is done.
        for (Entry entry : plan.entries()) announce(player, TradeType.SELL, entry.id(), entry.count(), entry.total());

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
    /** Puts items back into the inventory; whatever does not fit is dropped at the player's feet, never lost. */
    private void give(Player player, Material material, int count) {
        player.getInventory().addItem(new ItemStack(material, count)).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    /** Asks other plugins whether a trade may go ahead. Tells the player if one says no. */
    private boolean allowTrade(Player player, TradeType type, String itemId, int count, double total) {
        LodestockPreTradeEvent event = new LodestockPreTradeEvent(player, type, itemId, count, total);
        plugin.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled()) return true;
        if (event.getCancelMessage() != null) player.sendMessage(event.getCancelMessage());
        else plugin.messages().send(player, "trade-cancelled");
        return false;
    }

    /** Tells other plugins a trade went through. */
    private void announce(Player player, TradeType type, String itemId, int count, double total) {
        var state = plugin.market().state(itemId).orElse(null);
        double price = state == null ? 0 : state.price();
        int stock = state == null ? 0 : state.stock();
        plugin.getServer().getPluginManager().callEvent(new LodestockTradeEvent(player, type, itemId, count, total, price, stock));
    }

    /** Drops sell-all entries another plugin cancelled and works out the totals again. */
    private Plan withoutCancelled(Player player, Plan plan) {
        List<Entry> kept = new ArrayList<>();
        double total = 0;
        int items = 0;
        boolean told = false;
        for (Entry e : plan.entries()) {
            LodestockPreTradeEvent event = new LodestockPreTradeEvent(player, TradeType.SELL, e.id(), e.count(), e.total());
            plugin.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                if (!told) {
                    if (event.getCancelMessage() != null) player.sendMessage(event.getCancelMessage());
                    else plugin.messages().send(player, "trade-cancelled");
                    told = true;
                }
                continue;
            }
            kept.add(e);
            total += e.total();
            items += e.count();
        }
        if (kept.size() == plan.entries().size()) return plan;
        return new Plan(kept, plan.skipped(), Math.round(total * 100.0) / 100.0, items);
    }

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
            int left = OrePermissions.can(player, id) ? plugin.limits().remainingSell(player, market.item(id).orElseThrow()) : 0;
            if (left == 0) { // locked for this player, or today's sell limit is used up
                skipped.add(ItemNames.pretty(id));
                continue;
            }
            BulkQuote quote = market.previewSell(id, Math.min(e.getValue(), left));
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

    private void locked(Player player, String itemId) {
        plugin.messages().send(player, "ore-locked", Placeholder.unparsed("item", ItemNames.pretty(itemId)));
    }

    private void limitReached(Player player, MarketItem item, boolean buy) {
        plugin.messages().send(player, buy ? "limit-buy-reached" : "limit-sell-reached",
                Placeholder.unparsed("item", ItemNames.pretty(item.id())),
                Placeholder.unparsed("reset", plugin.limits().resetsIn()));
    }

    /** Tells the player when a trade used up the last of today's allowance. */
    private void limitNotice(Player player, MarketItem item, boolean buy) {
        DailyLimits limits = plugin.limits();
        if (limits.remaining(player, item, buy) != 0) return;
        plugin.messages().send(player, buy ? "limit-now-buy" : "limit-now-sell",
                Placeholder.unparsed("item", ItemNames.pretty(item.id())),
                Placeholder.unparsed("reset", limits.resetsIn()));
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