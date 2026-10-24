package com.openmc.judicator.menus.screens;

import com.openmc.judicator.menus.Menu;
import com.openmc.judicator.menus.MenuContext;
import com.openmc.judicator.menus.MenuIcon;
import com.openmc.judicator.menus.MenuItem;
import com.openmc.judicator.menus.Screen;
import com.openmc.judicator.warns.Warn;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The warns of a player ({@code /whistory}), newest first; a click opens the warn.
 */
public record WarnsScreen(String player, int page) implements Screen {

    @Override
    public Menu build(MenuContext context) {
        if (!context.canUse("warnhistory")) return context.denied();
        final List<Warn> warns = context.judicator().getWarnService().findAllOf(context.judicator().getPlayerDirectory().resolve(player))
                .stream().sorted(Comparator.comparing(Warn::getStartedAt).reversed()).toList();
        final int shown = MenuContext.clamp(page, warns.size());
        final List<MenuItem> entries = MenuContext.slice(warns, shown, warn -> {
            final MenuItem item = context.item(MenuIcon.WARN, warn, "warns", "entry");
            return context.canUse("warnview") ? item.onClick(context.open(new WarnScreen(warn.getId()))) : item;
        });
        return context.page(context.text(Map.of("nickname", player, "count", String.valueOf(warns.size())), "warns", "title"),
                entries, shown, warns.size(), next -> new WarnsScreen(player, next), Map.of());
    }
}
