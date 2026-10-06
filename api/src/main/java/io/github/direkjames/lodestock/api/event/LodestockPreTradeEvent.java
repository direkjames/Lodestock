package io.github.direkjames.lodestock.api.event;

import io.github.direkjames.lodestock.api.TradeType;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called just before a player's trade goes through, after Lodestock has checked permissions, limits, stock and
 * that the player can afford it, and before any money or items change hands. Cancel it to stop the trade.
 *
 * <p>Use it for your own rules, such as "no selling in the nether" or "ranks X and up only". If you cancel it,
 * set a message with {@link #setCancelMessage(Component)} so the player knows why, or they get a general one.
 *
 * <p>A sell-all fires one event per item type. Cancelling one skips that item and sells the rest.
 * You can't change the amount or the money here, only allow or cancel.
 */
public final class LodestockPreTradeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final TradeType type;
    private final String itemId;
    private final int amount;
    private final double total;
    private boolean cancelled;
    private Component cancelMessage;

    public LodestockPreTradeEvent(@NotNull Player player, @NotNull TradeType type, @NotNull String itemId,
                                  int amount, double total) {
        this.player = player;
        this.type = type;
        this.itemId = itemId;
        this.amount = amount;
        this.total = total;
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

    /** How many items would be traded. */
    public int getAmount() {
        return amount;
    }

    /** The money: what the player would pay when buying, or receive after tax when selling. */
    public double getTotal() {
        return total;
    }

    /** The message shown to the player if the trade is cancelled, or null for the default one. */
    public @Nullable Component getCancelMessage() {
        return cancelMessage;
    }

    public void setCancelMessage(@Nullable Component cancelMessage) {
        this.cancelMessage = cancelMessage;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
