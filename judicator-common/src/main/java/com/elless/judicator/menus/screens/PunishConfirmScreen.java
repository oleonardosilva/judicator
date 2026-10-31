package com.elless.judicator.menus.screens;

import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuContext;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.menus.Screen;
import com.elless.judicator.punish.types.PunishPermissions;

import java.util.Map;

/**
 * Confirms {@code /punish <player> <reason>}: apply now (no evidence prompt), or close the menu and go through the
 * evidence prompt of the chat as typing the command would; with {@code judicator.silent}, a toggle for {@code -s}.
 * Either way the command checks the staff rules again.
 */
public record PunishConfirmScreen(String player, String reason, Map<String, String> placeholders, boolean silent) implements Screen {

    public PunishConfirmScreen {
        placeholders = Map.copyOf(placeholders);
    }

    @Override
    public Menu build(MenuContext context) {
        if (!context.canUse("punish")) return context.denied();
        final String arguments = player + (silent ? " -s " : " ") + reason;
        final Menu.Builder menu = Menu.builder(context.text(placeholders, "confirm", "title"), 3);
        menu.set(4, context.item(MenuIcon.PUNISH, placeholders, "confirm", "punish"));
        menu.set(11, context.item(MenuIcon.CONFIRM, placeholders, "confirm", "punish-now")
                .onClick(() -> context.menus().run(context.viewer(), "punish", arguments, true, () -> context.menus().back(context.viewer(), 2))));
        menu.set(13, context.item(MenuIcon.EDIT, placeholders, "confirm", "punish-evidence")
                .onClick(() -> {
                    // a dialog asks and keeps the menu (cancel comes back here); without one, the chat prompt
                    if (context.menus().askEvidence(context.viewer(), "punish", arguments, 2)) return;
                    context.menus().close(context.viewer());
                    context.menus().run(context.viewer(), "punish", arguments, false, () -> { });
                }));
        menu.set(15, context.item(MenuIcon.CANCEL, placeholders, "confirm", "no").onClick(context.back()));
        if (context.has(PunishPermissions.SILENT)) {
            menu.set(22, context.item(silent ? MenuIcon.TOGGLE_ON : MenuIcon.TOGGLE_OFF, placeholders, "confirm",
                    silent ? "silent-on" : "silent-off")
                    .onClick(context.replace(new PunishConfirmScreen(player, reason, placeholders, !silent))));
        }
        return menu.build();
    }
}
