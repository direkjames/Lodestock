package io.github.direkjames.lodestock.paper;

import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.paper.command.LodestockCommand;
import io.github.direkjames.lodestock.paper.config.ConfigLoader;
import io.github.direkjames.lodestock.paper.config.ConfigWarnings;
import io.github.direkjames.lodestock.paper.config.Messages;
import io.github.direkjames.lodestock.paper.economy.EconomyHook;
import io.github.direkjames.lodestock.paper.gui.GuiLayout;
import io.github.direkjames.lodestock.paper.gui.GuiLayoutLoader;
import io.github.direkjames.lodestock.paper.gui.MenuListener;
import io.github.direkjames.lodestock.paper.gui.MenuService;
import io.github.direkjames.lodestock.paper.storage.YamlMarketStorage;
import io.github.direkjames.lodestock.paper.trade.TradeService;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LodestockPlugin extends JavaPlugin {
    private static final int BSTATS_ID = 34522;

    private YamlMarketStorage storage;
    private Messages messages;
    private Market market;
    private EconomyHook economy;
    private TradeService trades;
    private MenuService menus;
    private GuiLayout layout;
    private List<String> warnings = List.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveIfMissing("items.yml");
        saveIfMissing("gui.yml");
        saveIfMissing("lang/en.yml");

        messages = new Messages(this);
        economy = new EconomyHook(this);
        trades = new TradeService(this);
        menus = new MenuService(this);
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
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        // Temporary: write changes to disk every 30 seconds (replaced by the database in Phase 2).
        getServer().getScheduler().runTaskTimer(this, storage::writeIfDirty, 600L, 600L);

        // Economy plugins can register late, so check once the whole server has finished loading.
        getServer().getScheduler().runTask(this, () -> {
            if (!economy.available()) {
                getLogger().warning("No Vault economy found. Install an economy plugin (such as EssentialsX) or trading won't work.");
            }
        });

        new Metrics(this, BSTATS_ID);
        getLogger().info("Lodestock enabled.");
    }

    @Override
    public void onDisable() {
        if (market != null) market.flush();
        if (storage != null) storage.close();
        getLogger().info("Lodestock disabled.");
    }

    /** Loads (or reloads) config, language, items and the GUI. Keeps the old setup if the new config is invalid. */
    public boolean loadMarket() {
        try {
            List<String> found = new ArrayList<>();
            ConfigWarnings warn = new ConfigWarnings(getLogger(), found);
            ConfigLoader.Loaded loaded = ConfigLoader.load(this, warn);
            GuiLayout newLayout = GuiLayoutLoader.load(this, loaded.items(), loaded.pins(), warn);
            messages.reload();

            if (market != null) market.flush();
            market = new Market(loaded.settings(), loaded.items(), storage);
            layout = newLayout;
            warnings = List.copyOf(found);
            getLogger().info("Loaded " + loaded.items().size() + " market items.");

            if (menus != null) menus.closeAll();
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
    public EconomyHook economy() { return economy; }
    public TradeService trades() { return trades; }
    public MenuService menus() { return menus; }
    public GuiLayout layout() { return layout; }
    /** Problems found during the last (re)load. */
    public List<String> warnings() { return warnings; }
}