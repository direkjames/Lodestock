package io.github.direkjames.lodestock.paper.storage;

import io.github.direkjames.lodestock.core.market.ItemState;
import io.github.direkjames.lodestock.core.market.MarketStorage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Temporary storage for Phase 1: live prices and stock in data.yml.
 * Writes go to a temp file first, then replace the real file, so a crash
 * mid-write can't leave a half-written data.yml. Replaced by SQL in Phase 2.
 */
public final class YamlMarketStorage implements MarketStorage {
    private final File file;
    private final Logger log;
    private final Map<String, ItemState> data = new HashMap<>();
    private boolean dirty = false;

    public YamlMarketStorage(File file, Logger log) {
        this.file = file;
        this.log = log;
        load();
    }

    // '.' is a path separator in YAML configs, and '~' can't appear in item IDs.
    private static String encode(String id) { return id.replace('.', '~'); }
    private static String decode(String key) { return key.replace('~', '.'); }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("items");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) continue;
            data.put(decode(key), new ItemState(s.getDouble("price"), s.getInt("stock")));
        }
    }

    @Override
    public synchronized Optional<ItemState> load(String itemId) {
        return Optional.ofNullable(data.get(itemId));
    }

    @Override
    public synchronized void save(String itemId, ItemState state) {
        data.put(itemId, state);
        dirty = true;
    }

    @Override
    public synchronized void saveAll(Map<String, ItemState> states) {
        data.putAll(states);
        dirty = true;
        writeIfDirty();
    }

    /** Called by a timer, and when the server stops. */
    public synchronized void writeIfDirty() {
        if (!dirty) return;
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, ItemState> e : data.entrySet()) {
            String base = "items." + encode(e.getKey());
            yml.set(base + ".price", e.getValue().price());
            yml.set(base + ".stock", e.getValue().stock());
        }
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            yml.save(tmp);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (IOException e) {
            log.severe("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }

    @Override
    public void close() {
        writeIfDirty();
    }
}