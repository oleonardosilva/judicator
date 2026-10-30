package com.elless.judicator.menus.screens;

import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuContext;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.menus.Screen;

import java.util.Map;

/**
 * Asks before a command that changes something (revoke, unban, kick, delete a note...): confirm runs it as the viewer
 * and returns to the screen before; when the command takes an optional text (a reason, a comment), a second button
 * asks for it in the chat first. Texts at {@code menu.confirm.<key>}.
 */
public record ConfirmScreen(MenuIcon icon, String key, Map<String, String> placeholders, String command, String arguments,
                            boolean optionalText) implements Screen {

    public static ConfirmScreen simple(MenuIcon icon, String key, Map<String, String> placeholders, String command, String arguments) {
        return new ConfirmScreen(icon, key, Map.copyOf(placeholders), command, arguments, false);
    }

    public static ConfirmScreen withReason(MenuIcon icon, String key, Map<String, String> placeholders, String command, String arguments) {
        return new ConfirmScreen(icon, key, Map.copyOf(placeholders), command, arguments, true);
    }

    @Override
    public Menu build(MenuContext context) {
        final Menu.Builder menu = Menu.builder(context.text(placeholders, "confirm", "title"), 3);
        menu.set(4, context.item(icon, placeholders, "confirm", key));
        menu.set(11, context.item(MenuIcon.CONFIRM, placeholders, "confirm", "yes")
                .onClick(() -> context.menus().run(context.viewer(), command, arguments, false, context.back())));
        if (optionalText) {
            menu.set(13, context.item(MenuIcon.EDIT, placeholders, "confirm", "yes-with-text")
                    .onClick(() -> context.menus().askCommand(context.viewer(), context.text(Map.of(), "confirm", "text-what"),
                            command, text -> arguments + " " + text, 1)));
        }
        menu.set(15, context.item(MenuIcon.CANCEL, placeholders, "confirm", "no").onClick(context.back()));
        return menu.build();
    }
}
