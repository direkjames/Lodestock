package io.github.direkjames.lodestock.core.market;

/**
 * The live price and stock of an item.
 * {@code priceHeld} / {@code stockHeld}: an admin set that value by hand, so drift / regeneration
 * leaves it alone until the next trade on the item.
 */
public record ItemState(double price, int stock, boolean priceHeld, boolean stockHeld) {
    public ItemState(double price, int stock) {
        this(price, stock, false, false);
    }
}
