package com.openmc.judicator.menus.screens;

import com.openmc.judicator.menus.Menu;
import com.openmc.judicator.menus.MenuContext;
import com.openmc.judicator.menus.MenuIcon;
import com.openmc.judicator.menus.MenuItem;
import com.openmc.judicator.menus.Screen;
import com.openmc.judicator.platform.JudicatorPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The players online here, by name; a click opens the player.
 */
public record OnlineScreen(int page) implements Screen {

    @Override
    public Menu build(MenuContext context) {
        final List<JudicatorPlayer> online = context.judicator().getPlatform().getOnlinePlayers().stream()
                .<JudicatorPlayer>map(player -> player)
                .sorted(Comparator.comparing(player -> player.getName().toLowerCase()))
                .toList();
        final int shown = MenuContext.clamp(page, online.size());
        final List<MenuItem> entries = MenuContext.slice(online, shown, player ->
                context.item(MenuIcon.PLAYER_HEAD, Map.of("nickname", player.getName()), "online", "entry")
                        .headOf(player.getUniqueId())
                        .onClick(context.open(new PlayerScreen(player.getName()))));
        return context.page(context.text(Map.of("count", String.valueOf(online.size())), "online", "title"),
                entries, shown, online.size(), OnlineScreen::new, Map.of());
    }
}
