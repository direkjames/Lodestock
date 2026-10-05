package io.github.direkjames.lodestock.paper;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public final class LodestockPlugin extends JavaPlugin {
    private static final int BSTATS_ID = 34522;

    @Override
    public void onEnable() {
        new Metrics(this, BSTATS_ID);
        getLogger().info("Lodestock enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Lodestock disabled.");
    }
}