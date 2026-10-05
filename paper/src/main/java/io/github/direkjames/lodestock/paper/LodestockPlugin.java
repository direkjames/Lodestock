package io.github.direkjames.lodestock.paper;

import org.bukkit.plugin.java.JavaPlugin;

public final class LodestockPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Lodestock enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Lodestock disabled.");
    }
}