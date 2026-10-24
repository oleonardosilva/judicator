package com.openmc.judicator.menus.screens;

import com.openmc.judicator.menus.Menu;
import com.openmc.judicator.menus.MenuContext;
import com.openmc.judicator.menus.MenuItem;
import com.openmc.judicator.menus.MenuService;
import com.openmc.judicator.menus.Screen;
import com.openmc.judicator.punish.PunishService;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A list of punishments: the history of a player ({@code /phistory}), or the bans or mutes in force ({@code /banlist},
 * {@code /mutelist}), newest first. A click opens the punishment.
 *
 * @param player the player of a history; null for the lists in force
 */
public record PunishmentsScreen(String kind, String player, int page) implements Screen {

    public static PunishmentsScreen history(String player, int page) {
        return new PunishmentsScreen("history", player, page);
    }

    public static PunishmentsScreen bans(int page) {
        return new PunishmentsScreen("bans", null, page);
    }

    public static PunishmentsScreen mutes(int page) {
        return new PunishmentsScreen("mutes", null, page);
    }

    @Override
    public Menu build(MenuContext context) {
        final PunishService punishments = context.judicator().getPunishService();
        final List<MenuItem> entries;
        final int total;
        final int shown;
        switch (kind) {
            case "history" -> {
                if (!context.canUse("punishhistory")) return context.denied();
                final List<Punishment> all = punishments.findAllOf(context.judicator().getPlayerDirectory().resolve(player)).stream()
                        .sorted(Comparator.comparing(Punishment::getStartedAt).reversed()).toList();
                total = all.size();
                shown = MenuContext.clamp(page, total);
                entries = MenuContext.slice(all, shown, punishment -> entry(context, punishment));
            }
            case "bans", "mutes" -> {
                final boolean bans = kind.equals("bans");
                if (!context.canUse(bans ? "banlist" : "mutelist")) return context.denied();
                final PunishType[] types = bans ? new PunishType[]{PunishType.BAN, PunishType.TEMPBAN}
                        : new PunishType[]{PunishType.MUTE, PunishType.TEMPMUTE};
                total = punishments.countActive(types);
                shown = MenuContext.clamp(page, total);
                entries = punishments.findActive(MenuService.PAGE_SIZE, MenuContext.offset(shown), types).stream()
                        .map(punishment -> entry(context, punishment)).toList();
            }
            default -> throw new IllegalArgumentException(kind);
        }
        final String title = context.text(Map.of("nickname", player == null ? "" : player, "count", String.valueOf(total)),
                "punishments", "title-" + kind);
        return context.page(title, entries, shown, total, next -> new PunishmentsScreen(kind, player, next), Map.of());
    }

    private static MenuItem entry(MenuContext context, Punishment punishment) {
        final MenuItem item = context.item(Icons.of(punishment.getType()), punishment, "punishments", "entry");
        return context.canUse("punishview") ? item.onClick(context.open(new PunishmentScreen(punishment.getId()))) : item;
    }
}
