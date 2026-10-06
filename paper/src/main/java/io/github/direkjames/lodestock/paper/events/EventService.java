package io.github.direkjames.lodestock.paper.events;

import io.github.direkjames.lodestock.core.events.EventTimes;
import io.github.direkjames.lodestock.core.events.MarketEvent;
import io.github.direkjames.lodestock.core.events.MarketEventParser;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.paper.LodestockPlugin;
import io.github.direkjames.lodestock.paper.admin.MarketAdmin;
import io.github.direkjames.lodestock.paper.util.ItemNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Runs the scheduled crashes and surges from events.yml. Everything happens on the main thread. */
public final class EventService {
    /** An event that was due longer ago than this (the server was off or lagging) is skipped. */
    private static final long GRACE_MILLIS = 120_000L;
    private static final long TICK = 200L; // 10 seconds
    private static final DecimalFormat PERCENT = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));

    public enum RunResult { UNKNOWN, DONE, FAILED }

    /** One line of /lodestock events. */
    public record Line(MarketEvent event, String percent, String items, String next) {}

    /** The decision made at warning time: whether it happens, and what it will do. */
    private record Pending(boolean go, MarketEvent.Roll roll) {}

    private final LodestockPlugin plugin;
    private volatile boolean enabled;
    private volatile boolean broadcast = true;
    private volatile boolean revealDetails;
    private volatile ZoneId zone = ZoneId.systemDefault();
    private volatile Map<String, MarketEvent> events = Map.of();
    private BukkitTask task;
    /** key = event id + the moment it is due. Kept across reloads. */
    private final Map<String, Pending> rolled = new HashMap<>();
    /** The same keys, once they have been handled, with the time they were handled. */
    private final Map<String, Long> handled = new HashMap<>();

    public EventService(LodestockPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Reads events.yml again. Problems are added to {@code problems} (they are also shown by /lodestock reload).
     * Needs the market to be loaded, to know which items exist.
     */
    public void reload(List<String> problems) {
        if (task != null) task.cancel();
        task = null;

        YamlConfiguration yaml;
        try {
            yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "events.yml"));
        } catch (RuntimeException e) {
            problems.add("Could not read events.yml, scheduled events are off: " + e.getMessage());
            enabled = false;
            events = Map.of();
            return;
        }
        enabled = yaml.getBoolean("enabled", false);
        broadcast = yaml.getBoolean("broadcast", true);
        revealDetails = yaml.getBoolean("reveal-in-warning", false);

        ZoneId newZone = ZoneId.systemDefault();
        String zoneName = yaml.getString("timezone", "").trim();
        if (!zoneName.isEmpty()) {
            try {
                newZone = ZoneId.of(zoneName);
            } catch (DateTimeException e) {
                problems.add("events.yml: timezone \"" + zoneName + "\" is not a time zone, using the server's");
            }
        }
        zone = newZone;

        List<String> known = plugin.market().items().stream().map(MarketItem::id).toList();
        double tax = plugin.market().settings().taxPercent();
        List<String> found = new ArrayList<>();
        Map<String, MarketEvent> loaded = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("events");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection one = section.getConfigurationSection(id);
                if (one == null) {
                    found.add("events." + id + ": is not a section, so it was skipped");
                    continue;
                }
                MarketEventParser.parse(id, one.getValues(false), known, found).ifPresent(event -> {
                    loaded.put(id, event);
                    if (enabled && event.scheduled() && event.percentMax() > tax) {
                        found.add("events." + id + ": a " + PERCENT.format(event.percentMax()) + "% " + event.type().name().toLowerCase(Locale.ROOT)
                                + " is bigger than your " + PERCENT.format(tax) + "% tax, so players who act on the warning can profit. "
                                + "Keep daily limits on, or lower the percent (see docs/ECONOMY.md)");
                    }
                });
            }
        }
        events = Map.copyOf(loaded);
        for (String problem : found) {
            problems.add("events.yml: " + problem);
            plugin.getLogger().warning("events.yml: " + problem);
        }

        if (enabled) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK, TICK);
            plugin.getLogger().info("Scheduled events are on: " + loaded.size() + " event(s).");
        }
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }

    public boolean enabled() {
        return enabled;
    }

    public ZoneId zone() {
        return zone;
    }

    /** Every event with the next time it is due. */
    public List<Line> list() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH).withZone(zone);
        List<Line> lines = new ArrayList<>();
        for (MarketEvent event : events.values()) {
            String next;
            if (!event.scheduled()) next = "by hand only";
            else next = EventTimes.next(event, zone, Instant.now()).map(format::format).orElse("not in the next 8 days");
            String percent = event.percentMax() > event.percent()
                    ? PERCENT.format(event.percent()) + "-" + PERCENT.format(event.percentMax()) : PERCENT.format(event.percent());
            String pool = event.allItems() ? "all items" : String.join(", ", event.items());
            String items = event.pick() > 0 ? event.pick() + " random of " + pool : pool;
            lines.add(new Line(event, percent, items, next));
        }
        return lines;
    }

    public List<String> ids() {
        return List.copyOf(events.keySet());
    }

    /** Runs an event right now, without a warning or a chance roll. */
    public RunResult run(String id) {
        MarketEvent event = events.get(id);
        if (event == null) return RunResult.UNKNOWN;
        return fire(event, event.roll(ThreadLocalRandom.current(), marketIds())) ? RunResult.DONE : RunResult.FAILED;
    }

    public Optional<MarketEvent> find(String id) {
        return Optional.ofNullable(events.get(id));
    }

    // ---------- the timer ----------

    private void tick() {
        Instant now = Instant.now();
        long warnExtra;
        for (MarketEvent event : events.values()) {
            if (!event.scheduled()) continue;
            warnExtra = event.warnMinutes() * 60_000L + 1000L;
            for (Instant due : EventTimes.between(event, zone, now.minusMillis(GRACE_MILLIS), now.plusMillis(warnExtra))) {
                String key = event.id() + "@" + due.toEpochMilli();
                if (handled.containsKey(key)) continue;
                if (now.isBefore(due)) {
                    // Warning time: decide now whether it happens, so players are only warned about real events.
                    if (rolled.containsKey(key)) continue;
                    Pending pending = decide(event);
                    rolled.put(key, pending);
                    if (pending.go() && event.warnMinutes() > 0) {
                        long minutes = Math.max(1, (long) Math.ceil((due.toEpochMilli() - now.toEpochMilli()) / 60_000.0));
                        announce("event-warning", event, pending.roll(), event.random() && !revealDetails,
                                Placeholder.unparsed("minutes", String.valueOf(minutes)));
                    }
                } else {
                    Pending pending = rolled.remove(key);
                    if (pending == null) pending = decide(event); // no warning phase (warn-minutes 0, or the server just started)
                    handled.put(key, now.toEpochMilli());
                    if (pending.go()) fire(event, pending.roll());
                }
            }
        }
        long cutoff = now.toEpochMilli() - 86_400_000L;
        handled.values().removeIf(time -> time < cutoff);
        rolled.keySet().removeIf(key -> Long.parseLong(key.substring(key.lastIndexOf('@') + 1)) < cutoff);
    }

    /** Rolls the chance, and if it happens, the size and the items. Both are fixed from here on. */
    private Pending decide(MarketEvent event) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean go = random.nextDouble() * 100 < event.chance();
        return new Pending(go, go ? event.roll(random, marketIds()) : null);
    }

    private List<String> marketIds() {
        return plugin.market().items().stream().map(MarketItem::id).toList();
    }

    private boolean fire(MarketEvent event, MarketEvent.Roll roll) {
        String source = "Event: " + event.name();
        MarketAdmin.Outcome outcome;
        try {
            outcome = roll.allItems()
                    ? plugin.admin().adjust(source, null, event.signed(roll.percent()))
                    : plugin.admin().adjustItems(source, roll.items(), event.signed(roll.percent()));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Event \"" + event.id() + "\" could not run: " + e.getMessage());
            return false;
        }
        if (!outcome.done()) {
            plugin.getLogger().info("Event \"" + event.id() + "\" was cancelled by another plugin.");
            return false;
        }
        announce("event-start", event, roll, false);
        return true;
    }

    /** {@code hide}: say only that some prices are about to change, not which ones or by how much. */
    private void announce(String key, MarketEvent event, MarketEvent.Roll roll, boolean hide, TagResolver... extra) {
        if (!broadcast) return;
        var messages = plugin.messages();
        String type = event.type().name().toLowerCase(Locale.ROOT);
        String effectKey = hide ? "event-effect-hidden-" + type : "event-effect-" + type + (roll.allItems() ? "-all" : "-items");
        String items = roll.items().stream().map(ItemNames::pretty).collect(Collectors.joining(", "));
        Component effect = messages.get(effectKey,
                Placeholder.unparsed("percent", PERCENT.format(roll.percent())),
                Placeholder.unparsed("items", items));
        List<TagResolver> tags = new ArrayList<>(List.of(extra));
        tags.add(Placeholder.unparsed("name", event.name()));
        tags.add(Placeholder.component("effect", effect));
        plugin.getServer().broadcast(messages.get(key, tags.toArray(new TagResolver[0])));
    }
}
