package com.elless.judicator.menus.screens;

import com.elless.judicator.menus.ListOptions;
import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuContext;
import com.elless.judicator.menus.MenuItem;
import com.elless.judicator.menus.MenuService;
import com.elless.judicator.menus.Screen;
import com.elless.judicator.punish.PunishService;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishStatus;
import com.elless.judicator.punish.types.PunishType;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A list of punishments: the history of a player ({@code /phistory}), or the bans or mutes in force ({@code /banlist},
 * {@code /mutelist}). A click opens the punishment.
 *
 * <p>The history is one player's, sorted and filtered here; the lists in force can be long and are sorted and
 * searched by the database.
 *
 * @param player the player of a history; null for the lists in force
 */
public record PunishmentsScreen(String kind, String player, int page, ListOptions options) implements Screen {

    static final ListOptions.Spec HISTORY = new ListOptions.Spec(
            List.of("newest", "oldest", "ending", "type"),
            List.of("all", "bans", "mutes", "kicks"),
            List.of("all", "active", "revoked", "finished"));
    static final ListOptions.Spec IN_FORCE = new ListOptions.Spec(
            List.of("newest", "oldest", "ending", "name"),
            List.of("all", "permanent", "temporary"),
            List.of());

    public static PunishmentsScreen history(String player, int page) {
        return new PunishmentsScreen("history", player, page, HISTORY.defaults());
    }

    /**
     * The punishments a staff member applied ({@code /blame}).
     */
    public static PunishmentsScreen blame(String staff, int page) {
        return new PunishmentsScreen("blame", staff, page, HISTORY.defaults());
    }

    public static PunishmentsScreen bans(int page) {
        return new PunishmentsScreen("bans", null, page, IN_FORCE.defaults());
    }

    public static PunishmentsScreen mutes(int page) {
        return new PunishmentsScreen("mutes", null, page, IN_FORCE.defaults());
    }

    @Override
    public Menu build(MenuContext context) {
        final PunishService punishments = context.judicator().getPunishService();
        final List<MenuItem> entries;
        final int total;
        final int shown;
        final ListOptions.Spec spec;
        if (kind.equals("history") || kind.equals("blame")) {
            final boolean blame = kind.equals("blame");
            if (!context.canUse(blame ? "blame" : "punishhistory")) return context.denied();
            spec = HISTORY;
            final List<Punishment> rows;
            if (blame) {
                final com.elless.judicator.players.PlayerRef staff = context.judicator().getPlayerDirectory().resolveKnown(player);
                rows = punishments.findByPunisher(staff, null, Math.max(1, punishments.countByPunisher(staff, null)), 0);
            } else {
                rows = punishments.findAllOf(context.judicator().getPlayerDirectory().resolve(player));
            }
            final List<Punishment> all = rows.stream()
                    .filter(kindFilter(options.filter()))
                    .filter(statusFilter(options.status()))
                    .filter(p -> options.matches(p.getReason().orElse(null), p.getPunisher(), p.getNickname()))
                    .sorted(order(options.order()))
                    .toList();
            total = all.size();
            shown = MenuContext.clamp(page, total);
            entries = MenuContext.slice(all, shown, punishment -> entry(context, punishment));
        } else {
            final boolean bans = kind.equals("bans");
            if (!context.canUse(bans ? "banlist" : "mutelist")) return context.denied();
            spec = IN_FORCE;
            final PunishType[] types = typesInForce(bans, options.filter());
            total = punishments.countActive(options.query(), types);
            shown = MenuContext.clamp(page, total);
            entries = punishments.findActive(options.query(), MenuService.PAGE_SIZE, MenuContext.offset(shown), types).stream()
                    .map(punishment -> entry(context, punishment)).toList();
        }
        final String title = context.text(Map.of("nickname", player == null ? "" : player, "count", String.valueOf(total)),
                "punishments", "title-" + kind);
        return context.page(title, entries, shown, total, spec, options,
                (other, number) -> new PunishmentsScreen(kind, player, number, other), Map.of());
    }

    private static PunishType[] typesInForce(boolean bans, String filter) {
        final PunishType permanent = bans ? PunishType.BAN : PunishType.MUTE;
        final PunishType temporary = bans ? PunishType.TEMPBAN : PunishType.TEMPMUTE;
        return switch (filter) {
            case "permanent" -> new PunishType[]{permanent};
            case "temporary" -> new PunishType[]{temporary};
            default -> new PunishType[]{permanent, temporary};
        };
    }

    private static Predicate<Punishment> kindFilter(String filter) {
        return switch (filter) {
            case "bans" -> Punishment::isBanType;
            case "mutes" -> Punishment::isMuteType;
            case "kicks" -> p -> p.getType() == PunishType.KICK;
            default -> p -> true;
        };
    }

    private static Predicate<Punishment> statusFilter(String status) {
        return switch (status) {
            case "active" -> p -> p.getStatus() == PunishStatus.ACTIVE;
            case "revoked" -> Punishment::isRevoked;
            case "finished" -> p -> p.getStatus() == PunishStatus.FINISHED;
            default -> p -> true;
        };
    }

    private static Comparator<Punishment> order(String order) {
        final Comparator<Punishment> newest = Comparator.comparing(Punishment::getStartedAt).reversed();
        return switch (order) {
            case "oldest" -> Comparator.comparing(Punishment::getStartedAt);
            // the ones ending soonest first, permanent ones (and kicks) last
            case "ending" -> Comparator.comparing((Punishment p) -> p.getFinishAt().orElse(LocalDateTime.MAX)).thenComparing(newest);
            case "type" -> Comparator.comparing((Punishment p) -> p.getType().name()).thenComparing(newest);
            default -> newest;
        };
    }

    private static MenuItem entry(MenuContext context, Punishment punishment) {
        final MenuItem item = context.item(Icons.of(punishment.getType()), punishment, "punishments", "entry")
                .headOf(punishment.getPlayerUUID().orElse(null), punishment.getNickname());
        return context.canUse("punishview") ? item.onClick(context.open(new PunishmentScreen(punishment.getId()))) : item;
    }
}
