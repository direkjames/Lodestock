package io.github.direkjames.lodestock.api;

import java.util.OptionalDouble;

/**
 * A snapshot of one item in the market. It does not update by itself: ask the API again for fresh numbers.
 *
 * @param id         the Minecraft item ID without a namespace, such as {@code diamond}
 * @param basePrice  the price the market is pulled back toward (from items.yml)
 * @param price      the current price
 * @param stock      how many the market has now
 * @param startStock the stock the market starts and regenerates toward
 * @param maxStock   the market stops buying from players at this much stock
 * @param buyPrice   what the next single item costs a player, empty if it can't be bought right now
 * @param sellPrice  what the next single item pays a player after tax, empty if it can't be sold right now
 * @param allowBuy   false if players can't buy this item
 * @param allowSell  false if players can't sell this item
 * @param priceHeld  true if an admin set the price by hand and it stays until the next trade
 * @param stockHeld  true if an admin set the stock by hand and it stays until the next trade
 */
public record ItemInfo(String id, double basePrice, double price, int stock, int startStock, int maxStock,
                       OptionalDouble buyPrice, OptionalDouble sellPrice,
                       boolean allowBuy, boolean allowSell, boolean priceHeld, boolean stockHeld) {}
