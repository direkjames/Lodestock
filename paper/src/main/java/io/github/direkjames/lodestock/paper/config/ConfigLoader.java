package io.github.direkjames.lodestock.paper.config;

import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.MarketSettings;
import io.github.direkjames.lodestock.core.market.RecoverySettings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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

    public record Loaded(MarketSettings settings, RecoverySettings recovery, List<MarketItem> items,
                         Map<String, Pin> pins) {}

    /** @throws IllegalArgumentException if the general settings are invalid */
    public static Loaded load(JavaPlugin plugin, ConfigWarnings warn) {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        MarketSettings settings = new MarketSettings(
                cfg.getDouble("tax-percent", 10.0),
                cfg.getDouble("multiplier", 0.01),
                cfg.getDouble("price-floor", 0.01));
        if (settings.hasBuySellLoop()) {
            warn.add("tax-percent is too low for your multiplier: players can make free money by buying and selling in a loop. Raise tax-percent.");
        }

        RecoverySettings recovery = loadRecovery(cfg, warn);

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
                        section.getBoolean("regen", true)));
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
        return new Loaded(settings, recovery, items, pins);
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