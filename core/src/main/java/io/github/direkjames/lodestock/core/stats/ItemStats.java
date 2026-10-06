package io.github.direkjames.lodestock.core.stats;

/**
 * What one player has done with one item, for as long as the server has recorded it.
 * {@code best*} is the single trade that moved the most money.
 */
public record ItemStats(int soldItems, double soldMoney, int boughtItems, double boughtMoney, int trades,
                        double bestMoney, String bestAction, int bestAmount, long bestTime, long lastTime) {
    public static final ItemStats EMPTY = new ItemStats(0, 0, 0, 0, 0, 0, null, 0, 0, 0);

    /** These stats after one more trade. {@code action} is BUY or SELL. */
    public ItemStats after(String action, int amount, double money, long time) {
        boolean sell = action.equals("SELL");
        boolean best = money > bestMoney;
        return new ItemStats(
                soldItems + (sell ? amount : 0), soldMoney + (sell ? money : 0),
                boughtItems + (sell ? 0 : amount), boughtMoney + (sell ? 0 : money),
                trades + 1,
                best ? money : bestMoney, best ? action : bestAction,
                best ? amount : bestAmount, best ? time : bestTime,
                Math.max(lastTime, time));
    }

    public double net() {
        return soldMoney - boughtMoney;
    }

    /** Both sets of stats added together, keeping the better single trade. */
    public ItemStats plus(ItemStats other) {
        boolean otherBest = other.bestMoney > bestMoney;
        return new ItemStats(
                soldItems + other.soldItems, soldMoney + other.soldMoney,
                boughtItems + other.boughtItems, boughtMoney + other.boughtMoney,
                trades + other.trades,
                otherBest ? other.bestMoney : bestMoney, otherBest ? other.bestAction : bestAction,
                otherBest ? other.bestAmount : bestAmount, otherBest ? other.bestTime : bestTime,
                Math.max(lastTime, other.lastTime));
    }
}
