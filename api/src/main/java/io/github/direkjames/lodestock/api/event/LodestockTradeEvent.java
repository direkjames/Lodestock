package io.github.direkjames.lodestock.api.event;

import io.github.direkjames.lodestock.api.TradeType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called after a player's trade went through: the money and items have moved and the market has updated.
 * It can't be cancelled. Use it for rewards, logs, Discord messages, quests and the like.
 *
 * <p>A sell-all fires one event per item type.
 */
public final class LodestockTradeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final TradeType type;
    private final String itemId;
    private final int amount;
    private final double total;
    private final double newPrice;
    private final int newStock;

    public LodestockTradeEvent(@NotNull Player player, @NotNull TradeType type, @NotNull String itemId,
                               int amount, double total, double newPrice, int newStock) {
        this.player = player;
        this.type = type;
        this.itemId = itemId;
        this.amount = amount;
        this.total = total;
        this.newPrice = newPrice;
        this.newStock = newStock;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull TradeType getType() {
        return type;
    }

    /** The item ID, such as {@code diamond}. */
    public @NotNull String getItemId() {
        return itemId;
    }

    /** How many items were traded. */
    public int getAmount() {
        return amount;
    }

    /** The money: what the player paid when buying, or received after tax when selling. */
    public double getTotal() {
        return total;
    }

    /** The item's price after this trade. */
    public double getNewPrice() {
        return newPrice;
    }

    /** The item's stock after this trade. */
    public int getNewStock() {
        return newStock;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
