package io.github.direkjames.lodestock.paper.placeholder;

import io.github.direkjames.lodestock.paper.LodestockPlugin;

/**
 * Registers the placeholders if PlaceholderAPI is installed. The plugin only ever mentions this class, never
 * the PlaceholderAPI classes themselves, so a server without PlaceholderAPI loads Lodestock without trouble.
 */
public final class PlaceholderHook {
    private static LodestockExpansion expansion;

    private PlaceholderHook() {}

    public static void register(LodestockPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            expansion = new LodestockExpansion(plugin);
            if (expansion.register()) {
                plugin.getLogger().info("PlaceholderAPI found: the %lodestock_...% placeholders are ready.");
            }
        } catch (Throwable e) {
            plugin.getLogger().warning("Could not register the PlaceholderAPI placeholders: " + e.getMessage());
        }
    }

    public static void unregister() {
        if (expansion == null) return;
        try {
            expansion.unregister();
        } catch (Throwable ignored) {
            // the server is shutting down anyway
        }
        expansion = null;
    }
}
