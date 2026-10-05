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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Reads config.yml and items.yml. Bad item entries are skipped with a warning. */
public final class ConfigLoader {
    private ConfigLoader() {}

    public record Category(String id, String icon, String name) {}

    public record Loaded(MarketSettings settings, List<MarketItem> items, Map<String, Category> categories) {}

    /** @throws IllegalArgumentException if the general settings are invalid */
    public static Loaded load(JavaPlugin plugin) {
        Logger log = plugin.getLogger();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        MarketSettings settings = new MarketSettings(
                cfg.getDouble("tax-percent", 10.0),
                cfg.getDouble("multiplier", 0.01),
                cfg.getDouble("price-floor", 0.01));
        if (settings.hasBuySellLoop()) {
            log.warning("Your tax-percent is too low for your multiplier: players can make free money by buying and selling in a loop. Raise tax-percent.");
        }

        YamlConfiguration itemsCfg = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "items.yml"));

        Map<String, Category> categories = new LinkedHashMap<>();
        ConfigurationSection catSec = itemsCfg.getConfigurationSection("categories");
        if (catSec != null) {
            for (String id : catSec.getKeys(false)) {
                ConfigurationSection c = catSec.getConfigurationSection(id);
                if (c == null) continue;
                categories.put(id, new Category(id, c.getString("icon", "minecraft:chest"), c.getString("name", id)));
            }
        }

        List<MarketItem> items = new ArrayList<>();
        ConfigurationSection itemSec = itemsCfg.getConfigurationSection("items");
        if (itemSec != null) {
            for (String id : itemSec.getKeys(false)) {
                ConfigurationSection s = itemSec.getConfigurationSection(id);
                if (s == null) {
                    log.warning("items.yml: '" + id + "' is not a valid entry, skipped.");
                    continue;
                }
                Material material = Material.matchMaterial(id);
                if (material == null || !material.isItem()) {
                    log.warning("items.yml: '" + id + "' is not a known item, skipped.");
                    continue;
                }
                String category = s.getString("category", "other");
                if (!categories.containsKey(category)) {
                    log.warning("items.yml: '" + id + "' uses unknown category '" + category + "'.");
                }
                try {
                    items.add(new MarketItem(
                            id, category,
                            s.getDouble("base-price"),
                            s.getInt("start-stock"),
                            s.getInt("max-stock"),
                            s.getBoolean("allow-buy", true),
                            s.getBoolean("allow-sell", true)));
                } catch (IllegalArgumentException e) {
                    log.warning("items.yml: " + e.getMessage() + " - skipped.");
                }
            }
        }
        log.info("Loaded " + items.size() + " market items.");
        return new Loaded(settings, items, categories);
    }
}