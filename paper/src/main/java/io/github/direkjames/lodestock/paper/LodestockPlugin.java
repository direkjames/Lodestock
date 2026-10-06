package io.github.direkjames.lodestock.paper;

import io.github.direkjames.lodestock.api.LodestockApi;
import io.github.direkjames.lodestock.core.market.Market;
import io.github.direkjames.lodestock.core.stats.LifetimeStats;
import io.github.direkjames.lodestock.paper.admin.MarketAdmin;
import io.github.direkjames.lodestock.paper.api.LodestockApiImpl;
import io.github.direkjames.lodestock.paper.command.LodestockCommand;
import io.github.direkjames.lodestock.paper.config.ConfigLoader;
import io.github.direkjames.lodestock.paper.config.ConfigWarnings;
import io.github.direkjames.lodestock.paper.config.Messages;
import io.github.direkjames.lodestock.paper.economy.EconomyHook;
import io.github.direkjames.lodestock.paper.gui.ChartService;
import io.github.direkjames.lodestock.paper.gui.GuiLayout;
import io.github.direkjames.lodestock.paper.gui.GuiLayoutLoader;
import io.github.direkjames.lodestock.paper.gui.MenuListener;
import io.github.direkjames.lodestock.paper.gui.MenuService;
import io.github.direkjames.lodestock.paper.history.PriceHistoryService;
import io.github.direkjames.lodestock.paper.leaderboard.LeaderboardService;
import io.github.direkjames.lodestock.paper.limits.DailyLimits;
import io.github.direkjames.lodestock.paper.log.TradeLog;
import io.github.direkjames.lodestock.paper.permission.OrePermissions;
import io.github.direkjames.lodestock.paper.placeholder.PlaceholderHook;
import io.github.direkjames.lodestock.paper.recovery.RecoveryTask;
import io.github.direkjames.lodestock.paper.stats.StatsStore;
import io.github.direkjames.lodestock.paper.storage.Database;
import io.github.direkjames.lodestock.paper.storage.SqlMarketStorage;
import io.github.direkjames.lodestock.paper.trade.TradeService;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LodestockPlugin extends JavaPlugin {
    private static final int BSTATS_ID = 34522;

    private Database database;
    private SqlMarketStorage storage;
    private TradeLog tradeLog;
    private LifetimeStats lifetime;
    private LeaderboardService leaderboards;
    private RecoveryTask recovery;
    private DailyLimits limits;
    private PriceHistoryService priceHistory;
    private final OrePermissions orePermissions = new OrePermissions();
    private Messages messages;
    private Market market;
    private EconomyHook economy;
    private TradeService trades;
    private MarketAdmin admin;
    private MenuService menus;
    private ChartService charts;
    private GuiLayout layout;
    private List<String> warnings = List.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveIfMissing("items.yml");
        saveIfMissing("gui.yml");
        saveIfMissing("lang/en.yml");

        try {
            database = new Database(new File(getDataFolder(), "lodestock.db"), getLogger());
            storage = new SqlMarketStorage(database);
        } catch (Exception e) {
            getLogger().severe("Could not open the database (lodestock.db): " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        lifetime = new LifetimeStats();
        try {
            database.<Boolean>query(c -> {
                StatsStore.loadInto(c, lifetime);
                return true;
            }).join();
        } catch (RuntimeException e) {
            getLogger().warning("Could not load the lifetime stats, all-time leaderboards will start empty: " + e.getMessage());
        }
        tradeLog = new TradeLog(database, lifetime);
        leaderboards = new LeaderboardService(this, lifetime);
        tradeLog.prune(getConfig().getInt("history.keep-days", 30));

        limits = new DailyLimits(this, database);
        priceHistory = new PriceHistoryService(this, database);
        messages = new Messages(this);
        economy = new EconomyHook(this);
        trades = new TradeService(this);
        admin = new MarketAdmin(this);
        menus = new MenuService(this);
        charts = new ChartService(this);

        if (!loadMarket()) {
            getLogger().severe("Lodestock could not start because the config is invalid. Fix it and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        limits.start();
        recovery = new RecoveryTask(this, database);
        recovery.start();
        menus.start();

        PluginCommand command = Objects.requireNonNull(getCommand("lodestock"));
        LodestockCommand handler = new LodestockCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(limits, this);

        // Economy plugins can register late, so check once the whole server has finished loading.
        getServer().getScheduler().runTask(this, () -> {
            if (!economy.available()) {
                getLogger().warning("No Vault economy found. Install an economy plugin (such as EssentialsX) or trading won't work.");
            }
        });

        PlaceholderHook.register(this);

        new Metrics(this, BSTATS_ID);
        getServer().getServicesManager().register(LodestockApi.class, new LodestockApiImpl(this), this, ServicePriority.Normal);
        getLogger().info("Lodestock enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        PlaceholderHook.unregister();
        if (priceHistory != null) priceHistory.shutdown();
        if (menus != null) menus.stop();
        if (recovery != null) recovery.stop();
        orePermissions.clear();
        if (market != null) market.flush();
        if (database != null) database.close(); // writes everything still queued, then closes the file
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
            market = new Market(loaded.settings(), loaded.recovery(), loaded.items(), storage);
            layout = newLayout;
            limits.configure(loaded.limits());
            priceHistory.configure(loaded.history());
            orePermissions.sync(loaded.items());
            warnings = List.copyOf(found);
            getLogger().info("Loaded " + loaded.items().size() + " market items.");

            if (recovery != null) recovery.reschedule();
            if (menus != null) menus.start();
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
    public MarketAdmin admin() { return admin; }
    public MenuService menus() { return menus; }
    public ChartService charts() { return charts; }
    public GuiLayout layout() { return layout; }
    public TradeLog tradeLog() { return tradeLog; }
    public LifetimeStats lifetime() { return lifetime; }
    public LeaderboardService leaderboards() { return leaderboards; }
    public DailyLimits limits() { return limits; }
    public PriceHistoryService priceHistory() { return priceHistory; }
    /** Problems found during the last (re)load. */
    public List<String> warnings() { return warnings; }
}