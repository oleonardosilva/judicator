package com.elless.judicator.menus.screens;

import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuContext;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.menus.Screen;
import com.elless.judicator.warns.Warn;
import com.elless.judicator.warns.types.WarnStatus;

import java.util.Map;
import java.util.Optional;

/**
 * One warn ({@code /wview}): its data, the player and removing it while active.
 */
public record WarnScreen(long id) implements Screen {

    @Override
    public Menu build(MenuContext context) {
        if (!context.canUse("warnview")) return context.denied();
        final Optional<Warn> found = context.judicator().getWarnService().findById(id);
        if (found.isEmpty()) return context.gone();
        final Warn warn = found.get();
        final Map<String, String> placeholders = Map.of("id", String.valueOf(id), "nickname", warn.getNickname());

        final Menu.Builder menu = Menu.builder(context.text(placeholders, "warn", "title"), 3);
        menu.set(4, context.item(MenuIcon.WARN, warn, "warn", "info"));
        menu.set(11, context.item(MenuIcon.PLAYER_HEAD, placeholders, "warn", "player")
                .headOf(warn.getPlayerUUID().orElse(null), warn.getNickname())
                .onClick(context.open(new PlayerScreen(warn.getNickname()))));
        if (warn.getStatus() == WarnStatus.ACTIVE && context.canUse("unwarn")) {
            menu.set(15, context.item(MenuIcon.PARDON, placeholders, "warn", "remove")
                    .onClick(context.open(ConfirmScreen.simple(MenuIcon.PARDON, "unwarn", placeholders, "unwarn", String.valueOf(id)))));
        }
        menu.set(18, context.item(MenuIcon.BACK, Map.of(), "navigation", "back").onClick(context.back()));
        menu.set(26, context.item(MenuIcon.CLOSE, Map.of(), "navigation", "close")
                .onClick(() -> context.menus().close(context.viewer())));
        return menu.build();
    }
}
