package io.github.direkjames.lodestock.paper;

import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.paper.command.LodestockCommand;
import io.github.direkjames.lodestock.paper.config.ConfigLoader;
import io.github.direkjames.lodestock.paper.config.Messages;
import io.github.direkjames.lodestock.paper.storage.YamlMarketStorage;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.Objects;

public final class LodestockPlugin extends JavaPlugin {
    private static final int BSTATS_ID = 34522;

    private YamlMarketStorage storage;
    private Messages messages;
    private Market market;
    private Map<String, ConfigLoader.Category> categories = Map.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveIfMissing("items.yml");
        saveIfMissing("lang/en.yml");

        messages = new Messages(this);
        storage = new YamlMarketStorage(new File(getDataFolder(), "data.yml"), getLogger());

        if (!loadMarket()) {
            getLogger().severe("Lodestock could not start because the config is invalid. Fix it and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PluginCommand command = Objects.requireNonNull(getCommand("lodestock"));
        LodestockCommand handler = new LodestockCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        // Temporary: write changes to disk every 30 seconds (replaced by the database in Phase 2).
        getServer().getScheduler().runTaskTimer(this, storage::writeIfDirty, 600L, 600L);

        new Metrics(this, BSTATS_ID);
        getLogger().info("Lodestock enabled.");
    }

    @Override
    public void onDisable() {
        if (market != null) market.flush();
        if (storage != null) storage.close();
        getLogger().info("Lodestock disabled.");
    }

    /** Loads (or reloads) config, language and market. Keeps the old market if the new config is invalid. */
    public boolean loadMarket() {
        try {
            ConfigLoader.Loaded loaded = ConfigLoader.load(this);
            messages.reload();
            if (market != null) market.flush();
            market = new Market(loaded.settings(), loaded.items(), storage);
            categories = loaded.categories();
            return true;
        } catch (IllegalArgumentException e) {
            getLogger().severe("Invalid config: " + e.getMessage());
            return false;
        }
    }

    private void saveIfMissing(String path) {
        if (!new File(getDataFolder(), path).exists()) saveResource(path, false);
    }

    public Market market() { return market; }
    public Messages messages() { return messages; }
    public Map<String, ConfigLoader.Category> categories() { return categories; }
}