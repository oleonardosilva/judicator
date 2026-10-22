package com.openmc.judicator.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordWebhookServiceTest {

    @Test
    void placeholderOrBlankUrlIsNotConfigured() {
        // The default discord.yml ships enabled with this placeholder: every punishment used to post to it.
        assertFalse(DiscordWebhookService.isConfigured("https://discord.com/api/webhooks/YOUR_WEBHOOK_HERE"));
        assertFalse(DiscordWebhookService.isConfigured(""));
        assertFalse(DiscordWebhookService.isConfigured(null));
        assertTrue(DiscordWebhookService.isConfigured("https://discord.com/api/webhooks/123/abc"));
    }
}
