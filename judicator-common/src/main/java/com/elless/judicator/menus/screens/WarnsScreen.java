package com.elless.judicator.menus.screens;

import com.elless.judicator.menus.ListOptions;
import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuContext;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.menus.MenuItem;
import com.elless.judicator.menus.Screen;
import com.elless.judicator.warns.Warn;
import com.elless.judicator.warns.types.WarnStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The warns of a player ({@code /whistory}); a click opens the warn.
 */
public record WarnsScreen(String player, int page, ListOptions options) implements Screen {

    static final ListOptions.Spec SPEC = new ListOptions.Spec(
            List.of("newest", "oldest"), List.of(), List.of("all", "active", "finished"));

    public WarnsScreen(String player, int page) {
        this(player, page, SPEC.defaults());
    }

    @Override
    public Menu build(MenuContext context) {
        if (!context.canUse("warnhistory")) return context.denied();
        final Comparator<Warn> newest = Comparator.comparing(Warn::getStartedAt).reversed();
        final List<Warn> warns = context.judicator().getWarnService().findAllOf(context.judicator().getPlayerDirectory().resolve(player))
                .stream()
                .filter(warn -> switch (options.status()) {
                    case "active" -> warn.getStatus() == WarnStatus.ACTIVE;
                    case "finished" -> warn.getStatus() != WarnStatus.ACTIVE;
                    default -> true;
                })
                .filter(warn -> options.matches(warn.getReason(), warn.getPunisher()))
                .sorted(options.order().equals("oldest") ? newest.reversed() : newest)
                .toList();
        final int shown = MenuContext.clamp(page, warns.size());
        final List<MenuItem> entries = MenuContext.slice(warns, shown, warn -> {
            final MenuItem item = context.item(MenuIcon.WARN, warn, "warns", "entry")
                    .headOf(warn.getPlayerUUID().orElse(null), warn.getNickname());
            return context.canUse("warnview") ? item.onClick(context.open(new WarnScreen(warn.getId()))) : item;
        });
        return context.page(context.text(Map.of("nickname", player, "count", String.valueOf(warns.size())), "warns", "title"),
                entries, shown, warns.size(), SPEC, options, (other, number) -> new WarnsScreen(player, number, other), Map.of());
    }
}
