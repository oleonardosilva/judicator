package com.elless.judicator.menus.screens;

import com.elless.judicator.menus.ListOptions;
import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuContext;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.menus.MenuItem;
import com.elless.judicator.menus.Screen;
import com.elless.judicator.platform.JudicatorPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The players online here, by name, with a search; a click opens the player.
 */
public record OnlineScreen(int page, ListOptions options) implements Screen {

    static final ListOptions.Spec SPEC = new ListOptions.Spec(List.of("name"), List.of(), List.of());

    public OnlineScreen(int page) {
        this(page, SPEC.defaults());
    }

    @Override
    public Menu build(MenuContext context) {
        final List<JudicatorPlayer> online = context.judicator().getPlatform().getOnlinePlayers().stream()
                .<JudicatorPlayer>map(player -> player)
                .filter(player -> options.matches(player.getName()))
                .sorted(Comparator.comparing(player -> player.getName().toLowerCase(Locale.ROOT)))
                .toList();
        final int shown = MenuContext.clamp(page, online.size());
        final List<MenuItem> entries = MenuContext.slice(online, shown, player ->
                context.item(MenuIcon.PLAYER_HEAD, Map.of("nickname", player.getName()), "online", "entry")
                        .headOf(player.getUniqueId(), player.getName())
                        .onClick(context.open(new PlayerScreen(player.getName()))));
        return context.page(context.text(Map.of("count", String.valueOf(online.size())), "online", "title"),
                entries, shown, online.size(), SPEC, options, (other, number) -> new OnlineScreen(number, other), Map.of());
    }
}
