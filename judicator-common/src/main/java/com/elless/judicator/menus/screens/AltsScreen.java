package com.elless.judicator.menus.screens;

import com.elless.judicator.Judicator;
import com.elless.judicator.menus.ListOptions;
import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuContext;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.menus.MenuItem;
import com.elless.judicator.menus.Screen;
import com.elless.judicator.players.PlayerRef;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The accounts that shared an IP with a player ({@code /alts}) and whether each is banned or muted; a click opens the
 * account.
 */
public record AltsScreen(String player, int page, ListOptions options) implements Screen {

    static final ListOptions.Spec SPEC = new ListOptions.Spec(
            List.of("name", "status"), List.of("all", "banned", "muted", "clean"), List.of());
    /** banned accounts first when sorted by status */
    private static final List<String> STATUS_ORDER = List.of("banned", "muted", "clean");

    public AltsScreen(String player, int page) {
        this(player, page, SPEC.defaults());
    }

    private record Alt(PlayerRef account, String status) {
    }

    @Override
    public Menu build(MenuContext context) {
        if (!context.canUse("alts")) return context.denied();
        final Judicator judicator = context.judicator();
        final Comparator<Alt> byName = Comparator.comparing(alt -> alt.account().name().toLowerCase(Locale.ROOT));
        final List<Alt> alts = judicator.getAddressService().findAlts(judicator.getPlayerDirectory().resolve(player),
                        judicator.getAddressPolicy()::isExempt).stream()
                .map(account -> new Alt(account, judicator.getPunishService().statusOf(account)))
                .filter(alt -> options.filter().equals("all") || alt.status().equals(options.filter()))
                .filter(alt -> options.matches(alt.account().name()))
                .sorted(options.order().equals("status")
                        ? Comparator.comparing((Alt alt) -> STATUS_ORDER.indexOf(alt.status())).thenComparing(byName)
                        : byName)
                .toList();
        final int shown = MenuContext.clamp(page, alts.size());
        final List<MenuItem> entries = MenuContext.slice(alts, shown, alt -> {
            final String status = context.message(alt.status(), "alts", "status", alt.status());
            return context.item(MenuIcon.PLAYER_HEAD, Map.of("account", alt.account().name(), "status", status), "alts", "entry")
                    .headOf(alt.account().uuid(), alt.account().name())
                    .onClick(context.open(new PlayerScreen(alt.account().name())));
        });
        return context.page(context.text(Map.of("nickname", player, "count", String.valueOf(alts.size())), "alts", "title"),
                entries, shown, alts.size(), SPEC, options, (other, number) -> new AltsScreen(player, number, other), Map.of());
    }
}
