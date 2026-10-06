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
    public record Line(MarketEvent event, String next) {}

    private final LodestockPlugin plugin;
    private volatile boolean enabled;
    private volatile boolean broadcast = true;
    private volatile ZoneId zone = ZoneId.systemDefault();
    private volatile Map<String, MarketEvent> events = Map.of();
    private BukkitTask task;
    /** key = event id + the moment it is due. Value: true = it will happen. Kept across reloads. */
    private final Map<String, Boolean> rolled = new HashMap<>();
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
                    if (enabled && event.scheduled() && event.percent() > tax) {
                        found.add("events." + id + ": a " + PERCENT.format(event.percent()) + "% " + event.type().name().toLowerCase(Locale.ROOT)
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
            lines.add(new Line(event, next));
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
        return fire(event) ? RunResult.DONE : RunResult.FAILED;
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
                    boolean go = roll(event);
                    rolled.put(key, go);
                    if (go && event.warnMinutes() > 0) {
                        long minutes = Math.max(1, (long) Math.ceil((due.toEpochMilli() - now.toEpochMilli()) / 60_000.0));
                        announce("event-warning", event, Placeholder.unparsed("minutes", String.valueOf(minutes)));
                    }
                } else {
                    Boolean go = rolled.remove(key);
                    if (go == null) go = roll(event); // no warning phase (warn-minutes 0, or the server just started)
                    handled.put(key, now.toEpochMilli());
                    if (go) fire(event);
                }
            }
        }
        long cutoff = now.toEpochMilli() - 86_400_000L;
        handled.values().removeIf(time -> time < cutoff);
        rolled.keySet().removeIf(key -> Long.parseLong(key.substring(key.lastIndexOf('@') + 1)) < cutoff);
    }

    private static boolean roll(MarketEvent event) {
        return ThreadLocalRandom.current().nextDouble() * 100 < event.chance();
    }

    private boolean fire(MarketEvent event) {
        String source = "Event: " + event.name();
        MarketAdmin.Outcome outcome;
        try {
            outcome = event.allItems()
                    ? plugin.admin().adjust(source, null, event.signedPercent())
                    : plugin.admin().adjustItems(source, event.items(), event.signedPercent());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Event \"" + event.id() + "\" could not run: " + e.getMessage());
            return false;
        }
        if (!outcome.done()) {
            plugin.getLogger().info("Event \"" + event.id() + "\" was cancelled by another plugin.");
            return false;
        }
        announce("event-start", event);
        return true;
    }

    private void announce(String key, MarketEvent event, TagResolver... extra) {
        if (!broadcast) return;
        var messages = plugin.messages();
        String effectKey = "event-effect-" + event.type().name().toLowerCase(Locale.ROOT) + (event.allItems() ? "-all" : "-items");
        String items = event.items().stream().map(ItemNames::pretty).collect(Collectors.joining(", "));
        Component effect = messages.get(effectKey,
                Placeholder.unparsed("percent", PERCENT.format(event.percent())),
                Placeholder.unparsed("items", items));
        List<TagResolver> tags = new ArrayList<>(List.of(extra));
        tags.add(Placeholder.unparsed("name", event.name()));
        tags.add(Placeholder.component("effect", effect));
        plugin.getServer().broadcast(messages.get(key, tags.toArray(new TagResolver[0])));
    }
}
