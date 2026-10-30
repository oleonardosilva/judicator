package com.elless.judicator.discord;

import com.elless.judicator.testing.FakeConfig;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;

import static com.elless.judicator.testing.FakeConfig.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordWebhookServiceTest {

    @Test
    void placeholderOrBlankUrlIsNotConfigured() {
        // The default discord.yml of older versions shipped enabled with this placeholder: every punishment posted to it.
        assertFalse(DiscordWebhookService.isConfigured("https://discord.com/api/webhooks/YOUR_WEBHOOK_HERE"));
        assertFalse(DiscordWebhookService.isConfigured(""));
        assertFalse(DiscordWebhookService.isConfigured(null));
        assertTrue(DiscordWebhookService.isConfigured("https://discord.com/api/webhooks/123/abc"));
    }

    @Test
    void anEventWithoutItsOwnUrl_usesTheSharedOne() {
        final ConfigurationNode config = FakeConfig.of(map(
                "webhook-url", "https://discord.com/api/webhooks/1/shared",
                "events", map("ban", map("enabled", true), "warn", map("webhook-url", "https://discord.com/api/webhooks/2/warns"))));

        assertEquals("https://discord.com/api/webhooks/1/shared",
                DiscordWebhookService.webhookUrl(config, config.node("events", "ban")));
        assertEquals("https://discord.com/api/webhooks/2/warns",
                DiscordWebhookService.webhookUrl(config, config.node("events", "warn")));
    }

    @Test
    void anOlderFileWithAUrlPerEventOnly_keepsWorking() {
        final ConfigurationNode config = FakeConfig.of(map(
                "events", map("ban", map("webhook-url", "https://discord.com/api/webhooks/1/bans"))));

        assertEquals("https://discord.com/api/webhooks/1/bans", DiscordWebhookService.webhookUrl(config, config.node("events", "ban")));
    }

    @Test
    void textIsEscapedForJson() {
        assertEquals("\"say \\\"hi\\\"\\nC:\\\\x\\t\\u0001\"", DiscordWebhookService.jsonString("say \"hi\"\nC:\\x\t\u0001"));
        assertEquals("\"N/A\"", DiscordWebhookService.jsonString(null));
    }
}
