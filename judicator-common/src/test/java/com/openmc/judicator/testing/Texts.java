package com.openmc.judicator.testing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

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

    private static void append(Component component, StringBuilder text) {
        if (component == null) return;
        if (component instanceof TextComponent textComponent) text.append(textComponent.content());
        for (Component child : component.children()) append(child, text);
    }
}
