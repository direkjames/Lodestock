package io.github.direkjames.lodestock.paper.config;

import io.github.direkjames.lodestock.core.market.MarketItem;
import io.github.direkjames.lodestock.core.market.MarketSettings;
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

    public record Loaded(MarketSettings settings, List<MarketItem> items, Map<String, Pin> pins) {}

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
                        section.getBoolean("allow-sell", true)));
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
        return new Loaded(settings, items, pins);
    }
}