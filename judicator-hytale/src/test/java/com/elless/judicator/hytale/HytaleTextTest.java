package com.elless.judicator.hytale;

import com.hypixel.hytale.server.core.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Hytale draws no § codes: the core's texts must arrive as styled parts.
 */
class HytaleTextTest {

    /**
     * Each part with text, in order, as "text|color".
     */
    private static List<String> parts(Message message) {
        final List<String> parts = new ArrayList<>();
        collect(message, null, parts);
        return parts;
    }

    private static void collect(Message message, String inherited, List<String> parts) {
        final String color = message.getColor() != null ? message.getColor().toLowerCase() : inherited;
        final String text = message.getRawText();
        if (text != null && !text.isEmpty()) parts.add(text + "|" + color);
        for (Message child : message.getChildren()) collect(child, color, parts);
    }

    private static String plain(Message message) {
        final StringBuilder text = new StringBuilder();
        parts(message).forEach(part -> text.append(part, 0, part.lastIndexOf('|')));
        return text.toString();
    }

    @Test
    void sectionCodesBecomeColors() {
        final Message message = HytaleText.toMessage(Component.text("§cBanned: §ehacking"));

        assertEquals("Banned: hacking", plain(message));
        assertEquals(List.of("Banned: |#ff5555", "hacking|#ffff55"), parts(message));
    }

    @Test
    void ampersandMessagesOfTheCoreKeepTheirColors() {
        // PunishUtils.getMessage: legacyAmpersand().deserialize
        final Message message = HytaleText.toMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&aDone &7(#3)"));

        assertEquals(List.of("Done |#55ff55", "(#3)|#aaaaaa"), parts(message));
    }

    @Test
    void childrenWithCodesAfterStyledText() {
        final Message message = HytaleText.toMessage(Component.text("Ban ", NamedTextColor.RED)
                .append(Component.text("§fSteve")));

        assertEquals(List.of("Ban |#ff5555", "Steve|#ffffff"), parts(message));
    }

    @Test
    void textWithoutCodesStaysAsItIs() {
        final Message message = HytaleText.toMessage(Component.text("plain").decorate(TextDecoration.BOLD));

        assertEquals("plain", plain(message));
        assertFalse(plain(message).contains("§"));
    }

    @Test
    void linksAreKeptOtherClicksDropped() {
        final Message link = HytaleText.toMessage(Component.text("proof").clickEvent(ClickEvent.openUrl("https://imgur.com/a")));
        final Message command = HytaleText.toMessage(Component.text("confirm").clickEvent(ClickEvent.runCommand("/jprompt confirm")));

        assertEquals("https://imgur.com/a", link.getFormattedMessage().link);
        assertEquals(null, command.getFormattedMessage().link);
        assertEquals("confirm", plain(command));
    }
}
