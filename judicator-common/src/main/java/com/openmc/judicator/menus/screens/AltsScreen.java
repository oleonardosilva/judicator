package com.openmc.judicator.menus.screens;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.menus.Menu;
import com.openmc.judicator.menus.MenuContext;
import com.openmc.judicator.menus.MenuIcon;
import com.openmc.judicator.menus.MenuItem;
import com.openmc.judicator.menus.Screen;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.players.PlayerRef;

import java.util.List;
import java.util.Map;

/**
 * The accounts that shared an IP with a player ({@code /alts}) and whether each is banned or muted; a click opens the
 * account.
 */
public record AltsScreen(String player, int page) implements Screen {

    @Override
    public Menu build(MenuContext context) {
        if (!context.canUse("alts")) return context.denied();
        final Judicator judicator = context.judicator();
        final List<PlayerRef> alts = judicator.getAddressService().findAlts(judicator.getPlayerDirectory().resolve(player),
                judicator.getAddressPolicy()::isExempt);
        final int shown = MenuContext.clamp(page, alts.size());
        final List<MenuItem> entries = MenuContext.slice(alts, shown, account -> {
            final String status = context.message(judicator.getPunishService().statusOf(account), "alts", "status",
                    judicator.getPunishService().statusOf(account));
            return context.item(MenuIcon.PLAYER_HEAD, Map.of("account", account.name(), "status", status), "alts", "entry")
                    .headOf(judicator.getPlatform().getPlayer(account.name()).map(JudicatorPlayer::getUniqueId).orElse(null))
                    .onClick(context.open(new PlayerScreen(account.name())));
        });
        return context.page(context.text(Map.of("nickname", player, "count", String.valueOf(alts.size())), "alts", "title"),
                entries, shown, alts.size(), next -> new AltsScreen(player, next), Map.of());
    }
}
