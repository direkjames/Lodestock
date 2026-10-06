package io.github.direkjames.lodestock.paper.history;

import io.github.direkjames.lodestock.core.history.ChartMath;
import io.github.direkjames.lodestock.core.history.PricePoint;
import io.github.direkjames.lodestock.core.market.ItemState;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.storage.Database;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Takes a price sample of every changed item on a timer, keeps the last 24 hours in memory (for the
 * trend line and the 24h chart) and everything else in the database. Main thread only.
 */
public final class PriceHistoryService {
    private static final long DAY_MS = 86_400_000L;
    private static final int PRUNE_EVERY_SAMPLES = 144; // about once a day at the default interval

    private final LodestockPlugin plugin;
    private final PriceHistoryStore store;
    private final Map<String, Deque<PricePoint>> recent = new HashMap<>();
    private final Map<String, ItemState> lastSampled = new HashMap<>();
    private HistorySettings settings = HistorySettings.DEFAULT;
    private BukkitTask task;
    private long lastMarketVersion = -1;
    private long version; // goes up when samples are added, so windows redraw the trend line
    private int ticks;

    public PriceHistoryService(LodestockPlugin plugin, Database db) {
        this.plugin = plugin;
        this.store = new PriceHistoryStore(db);
    }

    public PriceHistoryStore store() {
        return store;
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public long version() {
        return version;
    }

    /** Applies new settings (also after a reload) and restarts the timer. The first call loads the last 24 hours. */
    public void configure(HistorySettings next) {
        boolean first = this.task == null && recent.isEmpty() && lastSampled.isEmpty();
        this.settings = next;
        stop();
        lastMarketVersion = -1;
        if (!next.enabled()) return;
        if (first) {
            long since = System.currentTimeMillis() - DAY_MS;
            store.readAll(since).join().forEach((item, points) -> {
                Deque<PricePoint> deque = new ArrayDeque<>(points);
                recent.put(item, deque);
                PricePoint last = points.get(points.size() - 1);
                lastSampled.put(item, new ItemState(last.price(), last.stock()));
            });
            store.prune(next.keepDays());
        }
        sample(); // an anchor point right now, so a chart has something to start from
        long period = Math.max(1, next.intervalMinutes()) * 60L * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sample, period, period);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    /** Saves a final sample, then stops. Called when the plugin shuts down. */
    public void shutdown() {
        if (settings.enabled()) sample();
        stop();
    }

    /** Saves a sample of every item whose price or stock changed since its last sample. */
    public void sample() {
        if (!settings.enabled()) return;
        Market market = plugin.market();
        if (market == null) return;
        long marketVersion = market.version();
        if (marketVersion == lastMarketVersion) return;
        lastMarketVersion = marketVersion;

        long now = System.currentTimeMillis();
        List<PriceHistoryStore.Sample> samples = new ArrayList<>();
        for (MarketItem item : market.items()) {
            ItemState state = market.state(item.id()).orElse(null);
            if (state == null) continue;
            ItemState before = lastSampled.get(item.id());
            if (before != null && before.price() == state.price() && before.stock() == state.stock()) continue;
            PricePoint point = new PricePoint(now, state.price(), state.stock());
            samples.add(new PriceHistoryStore.Sample(item.id(), point));
            lastSampled.put(item.id(), new ItemState(state.price(), state.stock()));
            Deque<PricePoint> deque = recent.computeIfAbsent(item.id(), k -> new ArrayDeque<>());
            deque.addLast(point);
            while (!deque.isEmpty() && deque.peekFirst().time() < now - DAY_MS) deque.removeFirst();
        }
        if (samples.isEmpty()) return;
        store.add(samples);
        version++;
        if (++ticks % PRUNE_EVERY_SAMPLES == 0) store.prune(settings.keepDays());
    }

    /** The last 24 hours of one item, from memory. Oldest first. */
    public List<PricePoint> recent(String itemId) {
        Deque<PricePoint> deque = recent.get(itemId);
        long since = System.currentTimeMillis() - DAY_MS;
        List<PricePoint> out = new ArrayList<>();
        if (deque != null) for (PricePoint p : deque) if (p.time() >= since) out.add(p);
        return out;
    }

    /** Samples from {@code since} on. Uses memory for the last 24 hours, the database for anything older. */
    public CompletableFuture<List<PricePoint>> read(String itemId, long since) {
        if (since >= System.currentTimeMillis() - DAY_MS) {
            List<PricePoint> out = new ArrayList<>();
            for (PricePoint p : recent(itemId)) if (p.time() >= since) out.add(p);
            return CompletableFuture.completedFuture(out);
        }
        return store.read(itemId, since);
    }

    /** Percent change of the price over the last 24 hours, or NaN when there is not enough data yet. */
    public double trend24h(String itemId) {
        List<PricePoint> points = recent(itemId);
        ItemState now = plugin.market().state(itemId).orElse(null);
        if (points.isEmpty() || now == null) return Double.NaN;
        return ChartMath.changePercent(points.get(0).price(), now.price());
    }
}
