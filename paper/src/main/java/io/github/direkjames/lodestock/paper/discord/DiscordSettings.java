package io.github.direkjames.lodestock.paper.discord;

import io.github.direkjames.lodestock.core.discord.DiscordPayload;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** What discord.yml says. {@code problems} lists settings that were wrong and were replaced by a default. */
record DiscordSettings(boolean enabled, String url, String username, String avatarUrl,
                       boolean bigTrades, double minTotal, boolean adminActions,
                       boolean summary, LocalTime summaryTime, ZoneId zone, List<String> problems) {

    static DiscordSettings load(FileConfiguration c) {
        List<String> problems = new ArrayList<>();
        boolean enabled = c.getBoolean("enabled", false);
        String url = c.getString("webhook-url", "").trim();
        if (enabled && url.isEmpty()) problems.add("webhook-url is empty");
        else if (enabled && !DiscordPayload.validWebhookUrl(url)) {
            problems.add("webhook-url is not a Discord webhook address (it should start with https://discord.com/api/webhooks/)");
        }

        String avatar = c.getString("avatar-url", "").trim();
        if (!avatar.isEmpty() && !avatar.startsWith("https://")) {
            problems.add("avatar-url must start with https:// so it is ignored");
            avatar = "";
        }

        double min = c.getDouble("big-trades.min-total", 10000);
        if (min < 0) min = 0;

        LocalTime time = LocalTime.of(20, 0);
        try {
            time = LocalTime.parse(c.getString("daily-summary.time", "20:00").trim());
        } catch (DateTimeParseException e) {
            problems.add("daily-summary.time must look like 20:00, using 20:00");
        }
        ZoneId zone = ZoneId.systemDefault();
        String zoneName = c.getString("daily-summary.timezone", "").trim();
        if (!zoneName.isEmpty()) {
            try {
                zone = ZoneId.of(zoneName);
            } catch (DateTimeException e) {
                problems.add("daily-summary.timezone \"" + zoneName + "\" is not a time zone, using the server's");
            }
        }

        return new DiscordSettings(enabled, url, c.getString("username", "Lodestock"), avatar,
                c.getBoolean("big-trades.enabled", true), min, c.getBoolean("admin-actions", true),
                c.getBoolean("daily-summary.enabled", false), time, zone, List.copyOf(problems));
    }

    /** On and with a usable address. */
    boolean ready() {
        return enabled && DiscordPayload.validWebhookUrl(url);
    }
}
