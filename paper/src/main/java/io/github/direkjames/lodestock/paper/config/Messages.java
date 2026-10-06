package io.github.direkjames.lodestock.paper.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Language file loader. Missing keys fall back to the bundled English file. */
public final class Messages {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private YamlConfiguration lang = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        String code = plugin.getConfig().getString("language", "en").replaceAll("[^a-zA-Z0-9_-]", "");
        File file = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        lang = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        if (!file.exists()) {
            plugin.getLogger().warning("Language file lang/" + code + ".yml not found, using English.");
        }
        try (InputStream in = plugin.getResource("lang/en.yml")) {
            fallback = in == null ? new YamlConfiguration()
                    : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read the bundled language file: " + e.getMessage());
        }
    }

    /** Parses MiniMessage text such as a title or button name from gui.yml. */
    public static Component mini(String raw, TagResolver... resolvers) {
        return MM.deserialize(raw, resolvers);
    }

    private String raw(String key) {
        return lang.getString(key, fallback.getString(key, "<red>Missing message: " + key));
    }

    /** Your own placeholders plus &lt;prefix&gt;, which comes from "prefix:" in the language file. */
    private TagResolver resolvers(TagResolver... extra) {
        String prefix = lang.getString("prefix", fallback.getString("prefix", ""));
        return TagResolver.builder()
                .resolvers(extra)
                .resolver(Placeholder.parsed("prefix", prefix))
                .build();
    }

    /** A message as plain text, with no formatting. For words dropped into other messages. */
    public String plain(String key) {
        return raw(key);
    }

    public Component get(String key, TagResolver... extra) {
        return MM.deserialize(raw(key), resolvers(extra));
    }

    /** For keys that hold a list of lines, such as item lore. */
    public List<Component> list(String key, TagResolver... extra) {
        List<String> lines = lang.contains(key) ? lang.getStringList(key) : fallback.getStringList(key);
        TagResolver all = resolvers(extra);
        return lines.stream().map(line -> MM.deserialize(line, all)).toList();
    }

    public void send(CommandSender to, String key, TagResolver... extra) {
        to.sendMessage(get(key, extra));
    }
}