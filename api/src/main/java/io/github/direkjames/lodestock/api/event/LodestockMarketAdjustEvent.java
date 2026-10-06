package io.github.direkjames.lodestock.api.event;

import io.github.direkjames.lodestock.api.AdjustType;
import net.kyori.adventure.text.Component;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called before an admin command or another plugin changes the market by hand: {@code /lodestock setprice},
 * {@code setstock}, {@code reset}, {@code crash}, {@code surge}, or the matching methods on
 * {@code LodestockApi}. Cancel it to stop the change.
 *
 * <p>It is not called for the market's own drift and regeneration, or for trades.
 */
public final class LodestockMarketAdjustEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final AdjustType type;
    private final String itemId;
    private final double value;
    private final String source;
    private boolean cancelled;
    private Component cancelMessage;

    public LodestockMarketAdjustEvent(@NotNull AdjustType type, @Nullable String itemId, double value, @NotNull String source) {
        this.type = type;
        this.itemId = itemId;
        this.value = value;
        this.source = source;
    }

    public @NotNull AdjustType getType() {
        return type;
    }

    /** The item ID, or null when the change applies to every item. */
    public @Nullable String getItemId() {
        return itemId;
    }

    /**
     * The new price for {@link AdjustType#SET_PRICE}, the new stock for {@link AdjustType#SET_STOCK}, or the percent
     * for {@link AdjustType#PERCENT_CHANGE} (negative for a crash). {@code NaN} for resets.
     */
    public double getValue() {
        return value;
    }

    /** Who is making the change: a player or "CONSOLE" for commands, or the name a plugin gave to the API. */
    public @NotNull String getSource() {
        return source;
    }

    /** The message shown to whoever asked, if it is cancelled. Null for the default one. */
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
