package io.github.direkjames.lodestock.core.discord;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the JSON for a Discord webhook message. No library is used: the format is small, and keeping it
 * here lets it be tested without a server. Text is cut to Discord's limits so a long name never makes
 * Discord reject the whole message.
 */
public final class DiscordPayload {
    public static final int TITLE_MAX = 256;
    public static final int DESCRIPTION_MAX = 4096;
    public static final int FIELD_NAME_MAX = 256;
    public static final int FIELD_VALUE_MAX = 1024;
    public static final int FIELDS_MAX = 25;
    public static final int USERNAME_MAX = 80;
    public static final int FOOTER_MAX = 2048;

    private static final Pattern WEBHOOK_URL = Pattern.compile(
            "^https://(?:(?:canary|ptb)\\.)?discord(?:app)?\\.com/api/(?:v\\d+/)?webhooks/\\d+/[A-Za-z0-9_\\-]+/?(?:\\?[A-Za-z0-9_=&\\-]*)?$");
    private static final Pattern RETRY_AFTER = Pattern.compile("\"retry_after\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    public record Field(String name, String value, boolean inline) {}

    /** {@code color} is 0xRRGGBB; {@code timestamp} and {@code footer} may be null. */
    public record Embed(String title, String description, int color, List<Field> fields, String footer, Instant timestamp) {
        public Embed {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    private DiscordPayload() {}

    /** True for a real Discord webhook address. Anything else is refused, so the plugin only ever talks to Discord. */
    public static boolean validWebhookUrl(String url) {
        return url != null && WEBHOOK_URL.matcher(url.trim()).matches();
    }

    /** Seconds Discord asks us to wait, from a rate-limit (429) answer, or {@code fallback} if it is not there. */
    public static double retryAfterSeconds(String body, double fallback) {
        if (body == null) return fallback;
        Matcher m = RETRY_AFTER.matcher(body);
        if (!m.find()) return fallback;
        try {
            return Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * The message body. Mentions are switched off ({@code allowed_mentions}), so a player name or item text
     * can never ping @everyone.
     */
    public static String json(String username, String avatarUrl, List<Embed> embeds) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        if (username != null && !username.isBlank()) {
            sb.append("\"username\":").append(quote(clip(username.trim(), USERNAME_MAX))).append(',');
        }
        if (avatarUrl != null && avatarUrl.trim().startsWith("https://")) {
            sb.append("\"avatar_url\":").append(quote(avatarUrl.trim())).append(',');
        }
        sb.append("\"allowed_mentions\":{\"parse\":[]},\"embeds\":[");
        for (int i = 0; i < Math.min(10, embeds.size()); i++) {
            if (i > 0) sb.append(',');
            appendEmbed(sb, embeds.get(i));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void appendEmbed(StringBuilder sb, Embed e) {
        sb.append('{');
        sb.append("\"title\":").append(quote(clip(e.title(), TITLE_MAX)));
        if (e.description() != null && !e.description().isEmpty()) {
            sb.append(",\"description\":").append(quote(clip(e.description(), DESCRIPTION_MAX)));
        }
        sb.append(",\"color\":").append(e.color() & 0xFFFFFF);
        if (e.footer() != null && !e.footer().isEmpty()) {
            sb.append(",\"footer\":{\"text\":").append(quote(clip(e.footer(), FOOTER_MAX))).append('}');
        }
        if (e.timestamp() != null) {
            sb.append(",\"timestamp\":").append(quote(e.timestamp().toString()));
        }
        if (!e.fields().isEmpty()) {
            sb.append(",\"fields\":[");
            for (int i = 0; i < Math.min(FIELDS_MAX, e.fields().size()); i++) {
                Field f = e.fields().get(i);
                if (i > 0) sb.append(',');
                // Discord rejects empty names and values, so a blank one becomes a zero-width space.
                sb.append("{\"name\":").append(quote(nonEmpty(clip(f.name(), FIELD_NAME_MAX))))
                        .append(",\"value\":").append(quote(nonEmpty(clip(f.value(), FIELD_VALUE_MAX))))
                        .append(",\"inline\":").append(f.inline()).append('}');
            }
            sb.append(']');
        }
        sb.append('}');
    }

    private static String nonEmpty(String text) {
        return text == null || text.isBlank() ? "\u200B" : text;
    }

    /** Cuts text to {@code max} characters, ending with an ellipsis when something was removed. */
    public static String clip(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        int end = max - 1;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--; // never split an emoji
        return text.substring(0, Math.max(0, end)) + "\u2026";
    }

    /** Stops Discord turning a name like {@code Mr_Miner_99} into italics. */
    public static String escapeMarkdown(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (char c : text.toCharArray()) {
            if ("\\*_~`|>#-[]()".indexOf(c) >= 0) sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

    /** A JSON string, with quotes. */
    public static String quote(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 2);
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
