package com.openmc.judicator.testing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain text of a component tree (content of every text component, in order), without § colour codes.
 */
public final class Texts {

    private Texts() {
    }

    public static String plain(Component component) {
        final StringBuilder text = new StringBuilder();
        append(component, text);
        return text.toString().replaceAll("§.", "");
    }

    /**
     * Commands run by clicking parts of the component tree, in order.
     */
    public static List<String> clicks(Component component) {
        final List<String> commands = new ArrayList<>();
        collectClicks(component, commands);
        return commands;
    }

    /**
     * Plain hover texts of the component tree, in order.
     */
    public static List<String> hovers(Component component) {
        final List<String> texts = new ArrayList<>();
        collectHovers(component, texts);
        return texts;
    }

    private static void collectClicks(Component component, List<String> commands) {
        if (component == null) return;
        final ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.RUN_COMMAND) commands.add(click.value());
        for (Component child : component.children()) collectClicks(child, commands);
    }

    private static void collectHovers(Component component, List<String> texts) {
        if (component == null) return;
        final HoverEvent<?> hover = component.hoverEvent();
        if (hover != null && hover.value() instanceof Component shown) texts.add(plain(shown));
        for (Component child : component.children()) collectHovers(child, texts);
    }

    private static void append(Component component, StringBuilder text) {
        if (component == null) return;
        if (component instanceof TextComponent textComponent) text.append(textComponent.content());
        for (Component child : component.children()) append(child, text);
    }
}
