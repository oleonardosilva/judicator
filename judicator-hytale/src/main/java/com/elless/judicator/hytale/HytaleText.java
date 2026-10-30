package com.elless.judicator.hytale;

import com.hypixel.hytale.server.core.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * The core's Adventure text as Hytale messages: text, color, bold, italic and links. Hytale draws no § codes, so the
 * texts that carry them (most messages of the core) are read as legacy text first. Click commands and hovers have no
 * Hytale counterpart and are left out: the evidence prompt is answered by typing its word.
 */
final class HytaleText {

    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private HytaleText() {
    }

    static Message toMessage(Component component) {
        if (component instanceof TextComponent text && text.content().indexOf(LegacyComponentSerializer.SECTION_CHAR) >= 0) {
            // the § codes become styles; the parsed part keeps the component's own style underneath and its children after
            final Component parsed = SECTION.deserialize(text.content()).applyFallbackStyle(text.style());
            final List<Component> children = new ArrayList<>(parsed.children());
            children.addAll(text.children());
            return toMessage(parsed.children(children));
        }
        final Message message = Message.raw(component instanceof TextComponent text ? text.content() : "");
        final TextColor color = component.color();
        if (color != null) message.color(color.asHexString());
        final TextDecoration.State bold = component.decoration(TextDecoration.BOLD);
        if (bold != TextDecoration.State.NOT_SET) message.bold(bold == TextDecoration.State.TRUE);
        final TextDecoration.State italic = component.decoration(TextDecoration.ITALIC);
        if (italic != TextDecoration.State.NOT_SET) message.italic(italic == TextDecoration.State.TRUE);
        final ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.OPEN_URL) message.link(click.value());
        final List<Message> children = component.children().stream().map(HytaleText::toMessage).toList();
        if (!children.isEmpty()) message.insertAll(children);
        return message;
    }
}
