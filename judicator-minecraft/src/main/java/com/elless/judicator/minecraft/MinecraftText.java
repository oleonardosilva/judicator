package com.elless.judicator.minecraft;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;

/**
 * The core's Adventure text as the game's own components, built node by node (no JSON in between, whose format
 * changes between versions): text, colors, decorations, insertion, and the click and hover events the core uses.
 */
public final class MinecraftText {

    private MinecraftText() {
    }

    /**
     * § codes inside a text stay as they are: the game draws them.
     */
    public static net.minecraft.network.chat.Component toNative(Component component) {
        return convert(component);
    }

    private static MutableComponent convert(Component component) {
        final MutableComponent converted = component instanceof TextComponent text
                ? net.minecraft.network.chat.Component.literal(text.content())
                : net.minecraft.network.chat.Component.empty();
        converted.setStyle(style(component.style()));
        for (Component child : component.children()) {
            converted.append(convert(child));
        }
        return converted;
    }

    private static net.minecraft.network.chat.Style style(Style style) {
        net.minecraft.network.chat.Style converted = net.minecraft.network.chat.Style.EMPTY;
        if (style.color() != null) converted = converted.withColor(style.color().value());
        converted = converted
                .withBold(state(style.decoration(TextDecoration.BOLD)))
                .withItalic(state(style.decoration(TextDecoration.ITALIC)))
                .withUnderlined(state(style.decoration(TextDecoration.UNDERLINED)))
                .withStrikethrough(state(style.decoration(TextDecoration.STRIKETHROUGH)))
                .withObfuscated(state(style.decoration(TextDecoration.OBFUSCATED)));
        if (style.insertion() != null) converted = converted.withInsertion(style.insertion());
        final ClickEvent click = style.clickEvent();
        if (click != null) {
            final net.minecraft.network.chat.ClickEvent event = click(click);
            if (event != null) converted = converted.withClickEvent(event);
        }
        final HoverEvent<?> hover = style.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT) {
            converted = converted.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(toNative((Component) hover.value())));
        }
        return converted;
    }

    private static net.minecraft.network.chat.ClickEvent click(ClickEvent click) {
        final String value = click.value();
        return switch (click.action()) {
            case RUN_COMMAND -> new net.minecraft.network.chat.ClickEvent.RunCommand(value);
            case SUGGEST_COMMAND -> new net.minecraft.network.chat.ClickEvent.SuggestCommand(value);
            case COPY_TO_CLIPBOARD -> new net.minecraft.network.chat.ClickEvent.CopyToClipboard(value);
            case OPEN_URL -> {
                try {
                    yield new net.minecraft.network.chat.ClickEvent.OpenUrl(URI.create(value));
                } catch (IllegalArgumentException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private static Boolean state(TextDecoration.State state) {
        return switch (state) {
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            case NOT_SET -> null;
        };
    }
}
