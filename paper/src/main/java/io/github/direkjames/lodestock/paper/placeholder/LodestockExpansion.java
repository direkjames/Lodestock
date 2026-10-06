package io.github.direkjames.lodestock.paper.placeholder;

import io.github.direkjames.lodestock.paper.LodestockPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The %lodestock_...% placeholders. Only loaded when PlaceholderAPI is installed (see PlaceholderHook). */
final class LodestockExpansion extends PlaceholderExpansion {
    private final LodestockPlugin plugin;
    private final PlaceholderResolver resolver;

    LodestockExpansion(LodestockPlugin plugin) {
        this.plugin = plugin;
        this.resolver = new PlaceholderResolver(plugin);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lodestock";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // stays registered across /papi reload
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        return resolver.resolve(player == null ? null : player.getUniqueId(), params);
    }
}
