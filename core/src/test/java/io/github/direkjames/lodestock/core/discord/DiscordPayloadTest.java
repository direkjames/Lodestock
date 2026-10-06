package io.github.direkjames.lodestock.core.discord;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordPayloadTest {

    @Test
    void onlyRealDiscordWebhooksAreAccepted() {
        assertTrue(DiscordPayload.validWebhookUrl("https://discord.com/api/webhooks/123456789/abc_DEF-123"));
        assertTrue(DiscordPayload.validWebhookUrl("https://discord.com/api/v10/webhooks/123456789/abc_DEF-123"));
        assertTrue(DiscordPayload.validWebhookUrl("https://ptb.discord.com/api/webhooks/1/x"));
        assertTrue(DiscordPayload.validWebhookUrl("https://discordapp.com/api/webhooks/1/x?wait=true"));
        assertFalse(DiscordPayload.validWebhookUrl(""));
        assertFalse(DiscordPayload.validWebhookUrl(null));
        assertFalse(DiscordPayload.validWebhookUrl("http://discord.com/api/webhooks/1/x"));
        assertFalse(DiscordPayload.validWebhookUrl("https://example.com/api/webhooks/1/x"));
        assertFalse(DiscordPayload.validWebhookUrl("https://discord.com.evil.com/api/webhooks/1/x"));
        assertFalse(DiscordPayload.validWebhookUrl("https://evil.com/?https://discord.com/api/webhooks/1/x"));
        assertFalse(DiscordPayload.validWebhookUrl("https://discord.com/api/webhooks/abc/x"));
    }

    @Test
    void quotingEscapesWhatJsonNeeds() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", DiscordPayload.quote("a\"b\\c\nd"));
        assertEquals("\"\\u0001\"", DiscordPayload.quote("\u0001"));
    }

    @Test
    void longTextIsCutWithoutSplittingAnEmoji() {
        assertEquals("abc", DiscordPayload.clip("abc", 5));
        assertEquals("abc\u2026", DiscordPayload.clip("abcdefgh", 4));
        String text = "ab" + "\uD83D\uDE00" + "cd"; // ab, one emoji (2 chars), cd
        assertEquals("ab\u2026", DiscordPayload.clip(text, 4));
    }

    @Test
    void markdownInNamesIsEscaped() {
        assertEquals("Mr\\_Miner\\_99", DiscordPayload.escapeMarkdown("Mr_Miner_99"));
        assertEquals("\\*\\*hi\\*\\*", DiscordPayload.escapeMarkdown("**hi**"));
    }

    @Test
    void theMessageHasNoMentionsAndTheEmbedFields() {
        String json = DiscordPayload.json("Lodestock", "https://example.com/a.png", List.of(
                new DiscordPayload.Embed("Big sale", "desc", 0x2ECC71,
                        List.of(new DiscordPayload.Field("Item", "Diamond", true), new DiscordPayload.Field("Empty", " ", false)),
                        "Lodestock", Instant.parse("2026-10-06T12:00:00Z"))));
        assertTrue(json.startsWith("{\"username\":\"Lodestock\",\"avatar_url\":\"https://example.com/a.png\","));
        assertTrue(json.contains("\"allowed_mentions\":{\"parse\":[]}"));
        assertTrue(json.contains("\"color\":3066993"));
        assertTrue(json.contains("\"timestamp\":\"2026-10-06T12:00:00Z\""));
        assertTrue(json.contains("{\"name\":\"Item\",\"value\":\"Diamond\",\"inline\":true}"));
        assertTrue(json.contains("{\"name\":\"Empty\",\"value\":\"\u200B\",\"inline\":false}"));
        assertTrue(json.endsWith("}]}"));
    }

    @Test
    void anAvatarThatIsNotHttpsIsLeftOut() {
        String json = DiscordPayload.json("L", "http://example.com/a.png", List.of(
                new DiscordPayload.Embed("t", null, 0, null, null, null)));
        assertFalse(json.contains("avatar_url"));
    }

    @Test
    void retryAfterIsReadFromARateLimitAnswer() {
        assertEquals(1.5, DiscordPayload.retryAfterSeconds("{\"message\":\"x\",\"retry_after\": 1.5,\"global\":false}", 5), 0.0001);
        assertEquals(5, DiscordPayload.retryAfterSeconds("nope", 5), 0.0001);
        assertEquals(5, DiscordPayload.retryAfterSeconds(null, 5), 0.0001);
    }
}
