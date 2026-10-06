package io.github.direkjames.lodestock.paper.config;

import io.github.direkjames.lodestock.core.audit.EconomyAudit;
import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.MarketSettings;
import io.github.direkjames.lodestock.core.market.RecoverySettings;
import io.github.direkjames.lodestock.paper.history.HistorySettings;
import io.github.direkjames.lodestock.paper.limits.LimitSettings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reads config.yml and items.yml. Bad item entries are skipped with a warning. */
public final class ConfigLoader {
    private ConfigLoader() {}

    /** An item pinned to an exact GUI slot. {@code page} starts at 1. */
    public record Pin(int slot, int page) {}

    public record Loaded(MarketSettings settings, RecoverySettings recovery, LimitSettings limits,
                         HistorySettings history, List<MarketItem> items, Map<String, Pin> pins) {}

    /** @throws IllegalArgumentException if the general settings are invalid */
    public static Loaded load(JavaPlugin plugin, ConfigWarnings warn) {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        MarketSettings settings = new MarketSettings(
                cfg.getDouble("tax-percent", 10.0),
                cfg.getDouble("multiplier", 0.01),
                cfg.getDouble("price-floor", 0.01));
        RecoverySettings recovery = loadRecovery(cfg, warn);
        LimitSettings limits = loadLimits(cfg, warn);
        HistorySettings history = loadHistory(cfg, warn);

        YamlConfiguration itemsCfg = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "items.yml"));
        List<MarketItem> items = new ArrayList<>();
        Map<String, Pin> pins = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();

        for (String key : itemsCfg.getKeys(false)) {
            ConfigurationSection section = itemsCfg.getConfigurationSection(key);
            if (section == null) {
                warn.add("items.yml: '" + key + "' needs settings under it (base-price, start-stock, max-stock), skipped.");
                continue;
            }

            String cleaned = key.trim().toLowerCase(Locale.ROOT);
            if (cleaned.startsWith("minecraft:")) cleaned = cleaned.substring("minecraft:".length());
            Material material = cleaned.contains(":") ? null : Material.matchMaterial(cleaned);
            if (material == null || material.isLegacy() || !material.isItem() || material.isAir()) {
                warn.add("items.yml: '" + key + "' is not a Minecraft item ID, skipped. Use IDs such as diamond or iron_ingot.");
                continue;
            }
            String id = material.getKey().getKey(); // the official ID, so variants of a name count as duplicates
            if (!seen.add(id)) {
                warn.add("items.yml: '" + key + "' is listed twice, the second entry was skipped.");
                continue;
            }

            try {
                items.add(new MarketItem(id,
                        section.getDouble("base-price"),
                        section.getInt("start-stock"),
                        section.getInt("max-stock"),
                        section.getBoolean("allow-buy", true),
                        section.getBoolean("allow-sell", true),
                        section.getBoolean("drift", true),
                        section.getBoolean("regen", true),
                        section.getInt("daily-buy", -1),
                        section.getInt("daily-sell", -1)));
            } catch (IllegalArgumentException e) {
                warn.add("items.yml: " + e.getMessage() + " - skipped.");
                continue;
            }

            if (section.contains("slot")) {
                int slot = section.getInt("slot");
                int page = section.getInt("page", 1);
                if (slot < 0 || page < 1) {
                    warn.add("items.yml: " + id + " has an invalid slot or page, it will be placed automatically.");
                } else {
                    pins.put(id, new Pin(slot, page));
                }
            }
        }

        EconomyAudit.Report audit = EconomyAudit.run(settings, recovery, limits.dailyBuy(), limits.dailySell(), items, null);
        long problems = audit.count(EconomyAudit.Level.WARN);
        if (problems > 0) {
            warn.add("Economy audit: " + problems + " problem(s) that could let players make free money "
                    + "(for example tax-percent too low for the multiplier). Run /lodestock audit for details.");
        }
        return new Loaded(settings, recovery, limits, history, items, pins);
    }

    /** Bad price history values are replaced with the defaults and reported. */
    private static HistorySettings loadHistory(FileConfiguration cfg, ConfigWarnings warn) {
        HistorySettings d = HistorySettings.DEFAULT;
        int interval = cfg.getInt("price-history.interval-minutes", d.intervalMinutes());
        if (interval < 1) {
            warn.add("config.yml: price-history.interval-minutes must be at least 1, using " + d.intervalMinutes() + ".");
            interval = d.intervalMinutes();
        }
        int keep = cfg.getInt("price-history.keep-days", d.keepDays());
        if (keep < 0) {
            warn.add("config.yml: price-history.keep-days can't be negative, using " + d.keepDays() + ".");
            keep = d.keepDays();
        }
        return new HistorySettings(cfg.getBoolean("price-history.enabled", d.enabled()), interval, keep);
    }

    /** Bad limit values are replaced with safe ones and reported, they never stop the plugin. */
    private static LimitSettings loadLimits(FileConfiguration cfg, ConfigWarnings warn) {
        int buy = cfg.getInt("limits.daily-buy", 0);
        if (buy < 0) {
            warn.add("config.yml: limits.daily-buy can't be negative, using 0 (no limit).");
            buy = 0;
        }
        int sell = cfg.getInt("limits.daily-sell", 0);
        if (sell < 0) {
            warn.add("config.yml: limits.daily-sell can't be negative, using 0 (no limit).");
            sell = 0;
        }
        LocalTime reset = LocalTime.MIDNIGHT;
        String rawTime = cfg.getString("limits.reset-time", "00:00");
        try {
            reset = LocalTime.parse(rawTime.trim());
        } catch (DateTimeParseException | NullPointerException e) {
            warn.add("config.yml: limits.reset-time '" + rawTime + "' is not a time like 00:00 or 04:30, using 00:00.");
        }
        ZoneId zone = ZoneId.systemDefault();
        String rawZone = cfg.getString("limits.timezone", "server");
        if (rawZone != null && !rawZone.isBlank() && !rawZone.trim().equalsIgnoreCase("server")) {
            try {
                zone = ZoneId.of(rawZone.trim());
            } catch (java.time.DateTimeException e) {
                warn.add("config.yml: limits.timezone '" + rawZone + "' is not a valid time zone (try Asia/Manila or UTC), using the server's time zone.");
            }
        }
        return new LimitSettings(buy, sell, reset, zone);
    }

    /** Bad recovery values are replaced with the defaults and reported, they never stop the plugin. */
    private static RecoverySettings loadRecovery(FileConfiguration cfg, ConfigWarnings warn) {
        RecoverySettings d = RecoverySettings.DEFAULT;
        int interval = cfg.getInt("recovery.interval-minutes", d.intervalMinutes());
        if (interval < 1) {
            warn.add("config.yml: recovery.interval-minutes must be at least 1, using " + d.intervalMinutes() + ".");
            interval = d.intervalMinutes();
        }
        int catchUp = cfg.getInt("recovery.catch-up-hours", d.catchUpHours());
        if (catchUp < 0) {
            warn.add("config.yml: recovery.catch-up-hours can't be negative, using 0 (no catch-up).");
            catchUp = 0;
        }
        catchUp = Math.min(catchUp, 24 * 30);
        double driftPercent = cfg.getDouble("drift.percent", d.driftPercent());
        if (driftPercent < 0 || driftPercent > 100) {
            warn.add("config.yml: drift.percent must be from 0 to 100, using " + d.driftPercent() + ".");
            driftPercent = d.driftPercent();
        }
        double regenPercent = cfg.getDouble("regen.percent", d.regenPercent());
        if (regenPercent < 0 || regenPercent > 100) {
            warn.add("config.yml: regen.percent must be from 0 to 100, using " + d.regenPercent() + ".");
            regenPercent = d.regenPercent();
        }
        return new RecoverySettings(interval, catchUp,
                cfg.getBoolean("drift.enabled", d.driftEnabled()), driftPercent,
                cfg.getBoolean("regen.enabled", d.regenEnabled()), regenPercent);
    }
}