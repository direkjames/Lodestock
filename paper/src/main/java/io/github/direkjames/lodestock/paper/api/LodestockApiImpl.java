package io.github.direkjames.lodestock.paper.api;

import io.github.direkjames.lodestock.api.AdjustResult;
import io.github.direkjames.lodestock.api.ItemInfo;
import io.github.direkjames.lodestock.api.LeaderboardEntry;
import io.github.direkjames.lodestock.api.LeaderboardPeriod;
import io.github.direkjames.lodestock.api.LeaderboardType;
import io.github.direkjames.lodestock.api.LodestockApi;
import io.github.direkjames.lodestock.api.PlayerStats;
import io.github.direkjames.lodestock.api.PricePoint;
import io.github.direkjames.lodestock.api.QuoteStatus;
import io.github.direkjames.lodestock.api.TradeQuote;
import io.github.direkjames.lodestock.core.market.BulkQuote;
import io.github.direkjames.lodestock.core.market.ItemState;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.Quote;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.admin.MarketAdmin;
import io.github.direkjames.lodestock.paper.leaderboard.LeaderboardService;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The public API, registered with the server's ServicesManager. */
public final class LodestockApiImpl implements LodestockApi {
    private final LodestockPlugin plugin;

    public LodestockApiImpl(LodestockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int apiVersion() {
        return API_VERSION;
    }

    @Override
    public Collection<String> itemIds() {
        return plugin.market().items().stream().map(MarketItem::id).toList();
    }

    @Override
    public Optional<ItemInfo> item(String id) {
        Market market = plugin.market();
        String key = normalize(id);
        MarketItem item = market.item(key).orElse(null);
        ItemState state = market.state(key).orElse(null);
        if (item == null || state == null) return Optional.empty();
        return Optional.of(new ItemInfo(item.id(), item.basePrice(), state.price(), state.stock(),
                item.startStock(), item.maxStock(), price(market.quoteBuy(key)), price(market.quoteSell(key)),
                item.allowBuy(), item.allowSell(), state.priceHeld(), state.stockHeld()));
    }

    @Override
    public double taxPercent() {
        return plugin.market().settings().taxPercent();
    }

    @Override
    public OptionalDouble buyPrice(String id) {
        return price(plugin.market().quoteBuy(normalize(id)));
    }

    @Override
    public OptionalDouble sellPrice(String id) {
        return price(plugin.market().quoteSell(normalize(id)));
    }

    @Override
    public TradeQuote previewBuy(String id, int amount) {
        return quote(plugin.market().previewBuy(normalize(id), amount));
    }

    @Override
    public TradeQuote previewSell(String id, int amount) {
        return quote(plugin.market().previewSell(normalize(id), amount));
    }

    @Override
    public CompletableFuture<List<PricePoint>> priceHistory(String id, Duration span) {
        Objects.requireNonNull(span, "span");
        String key = normalize(id);
        if (!plugin.priceHistory().enabled() || plugin.market().item(key).isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        long since = System.currentTimeMillis() - span.toMillis();
        return plugin.priceHistory().read(key, since).thenApply(points -> points.stream()
                .map(p -> new PricePoint(p.time(), p.price(), p.stock())).toList());
    }

    @Override
    public PlayerStats playerStats(UUID player) {
        return ApiMapping.stats(plugin.lifetime().total(Objects.requireNonNull(player, "player")));
    }

    @Override
    public PlayerStats playerStats(UUID player, String item) {
        return ApiMapping.stats(plugin.lifetime().item(Objects.requireNonNull(player, "player"), normalize(item)));
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> leaderboard(LeaderboardType type, LeaderboardPeriod period,
                                                                 String item, int limit) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(period, "period");
        int size = Math.max(1, Math.min(LeaderboardService.MAX_RANK, limit));
        String key = item == null ? null : normalize(item);
        return plugin.leaderboards().fetch(ApiMapping.board(type), ApiMapping.period(period), key, size)
                .thenApply(ApiMapping::entries);
    }

    @Override
    public AdjustResult setPrice(String id, double price, String source) {
        return result(admin().setPrice(requireSource(source), normalize(id), price));
    }

    @Override
    public AdjustResult setStock(String id, int stock, String source) {
        return result(admin().setStock(requireSource(source), normalize(id), stock));
    }

    @Override
    public AdjustResult adjustPrices(String id, double percent, String source) {
        return result(admin().adjust(requireSource(source), id == null ? null : normalize(id), percent));
    }

    @Override
    public AdjustResult reset(String id, String source) {
        return result(admin().reset(requireSource(source), normalize(id)));
    }

    @Override
    public AdjustResult resetAll(String source) {
        return result(admin().resetAll(requireSource(source)));
    }

    private MarketAdmin admin() {
        return plugin.admin();
    }

    private static AdjustResult result(MarketAdmin.Outcome outcome) {
        return outcome.done() ? AdjustResult.DONE : AdjustResult.CANCELLED;
    }

    private static String requireSource(String source) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("source must say who is making the change");
        return source;
    }

    private static OptionalDouble price(Quote quote) {
        return quote.ok() ? OptionalDouble.of(quote.price()) : OptionalDouble.empty();
    }

    private static TradeQuote quote(BulkQuote q) {
        QuoteStatus status = ApiMapping.status(q.status());
        return new TradeQuote(status, q.count(), q.total());
    }

    /** "Minecraft:Diamond" and "diamond" both become "diamond". */
    private static String normalize(String id) {
        Objects.requireNonNull(id, "id");
        String clean = id.trim().toLowerCase(Locale.ROOT);
        return clean.startsWith("minecraft:") ? clean.substring("minecraft:".length()) : clean;
    }
}
