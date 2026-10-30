package com.elless.judicator.menus.screens;

import com.elless.judicator.Judicator;
import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuContext;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.menus.MenuItem;
import com.elless.judicator.menus.Screen;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.punish.PlayerAddress;
import com.elless.judicator.punish.PunishService;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishPermissions;
import com.elless.judicator.punish.types.PunishStatus;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.warns.Warn;
import com.elless.judicator.warns.types.WarnStatus;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * {@code /jmenu <player>}: what {@code /playerinfo} shows, the way into the player's lists and the actions on them.
 * Each part needs the permission of the command that shows or does it.
 */
public record PlayerScreen(String name) implements Screen {

    @Override
    public Menu build(MenuContext context) {
        final Judicator judicator = context.judicator();
        final Optional<JudicatorPlayer> online = judicator.getPlatform().getPlayer(name);
        final PlayerRef player = judicator.getPlayerDirectory().resolve(name);
        final String nickname = online.map(JudicatorPlayer::getName).orElse(player.name());
        final List<Punishment> history = judicator.getPunishService().findAllOf(player);
        final List<Warn> warns = judicator.getWarnService().findAllOf(player);
        final int notes = judicator.getNoteService().countOf(player);
        final Optional<PlayerAddress> lastAddress = judicator.getAddressService().findLast(player);
        final int alts = judicator.getAddressService().findAlts(player, judicator.getAddressPolicy()::isExempt).size();
        final UUID uuid = online.map(JudicatorPlayer::getUniqueId).orElse(player.uuid());

        final DateTimeFormatter dates = DateTimeFormatter.ofPattern(context.messages().node("date-format").getString("dd/MM/yyyy HH:mm"));
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("nickname", nickname);
        placeholders.put("uuid", uuid == null ? "?" : uuid.toString());
        placeholders.put("state", context.message(online.isPresent() ? "online" : "offline", "info", online.isPresent() ? "online" : "offline"));
        placeholders.put("bans", String.valueOf(count(history, PunishType.BAN, PunishType.TEMPBAN)));
        placeholders.put("mutes", String.valueOf(count(history, PunishType.MUTE, PunishType.TEMPMUTE)));
        placeholders.put("kicks", String.valueOf(count(history, PunishType.KICK)));
        placeholders.put("punishments", String.valueOf(history.size()));
        placeholders.put("warns", String.valueOf(warns.size()));
        placeholders.put("activeWarns", String.valueOf(warns.stream().filter(w -> w.getStatus() == WarnStatus.ACTIVE).count()));
        placeholders.put("notes", String.valueOf(notes));
        placeholders.put("reports", String.valueOf(judicator.getReportService().countAgainst(player)));
        placeholders.put("alts", String.valueOf(alts));
        placeholders.put("lastSeen", online.isPresent()
                ? context.message("now", "info", "now")
                : lastAddress.map(address -> dates.format(address.lastUsage())).orElse("?"));

        final Menu.Builder menu = Menu.builder(context.text(placeholders, "player", "title"), 6);
        final boolean seesInfo = context.canUse("playerinfo");
        final List<String> headLore = new ArrayList<>(seesInfo ? context.lines(placeholders, "player", "head", "lore") : List.of());
        // the IP, as in /playerinfo, only to who may see histories
        if (seesInfo && context.has(PunishPermissions.HISTORY)) {
            final String ip = online.map(JudicatorPlayer::getHostAddress).or(() -> lastAddress.map(PlayerAddress::hostAddress)).orElse("?");
            headLore.add(context.text(Map.of("ip", ip), "player", "head", "ip"));
        }
        menu.set(4, new MenuItem(MenuIcon.PLAYER_HEAD, context.text(placeholders, "player", "head", "name"), headLore, 1,
                uuid, nickname, null, null));

        final Optional<Punishment> ban = inForce(history, Punishment::isBanType);
        final Optional<Punishment> mute = inForce(history, Punishment::isMuteType);
        if (seesInfo) {
            menu.set(19, inForceItem(context, ban, "ban", "no-ban"));
            menu.set(21, inForceItem(context, mute, "mute", "no-mute"));
        }
        if (context.canUse("punishhistory")) {
            menu.set(23, context.item(MenuIcon.HISTORY, placeholders, "player", "history")
                    .amount(history.size()).onClick(context.open(PunishmentsScreen.history(nickname, 1))));
        }
        if (context.canUse("warnhistory")) {
            menu.set(25, context.item(MenuIcon.WARN, placeholders, "player", "warns")
                    .amount(warns.size()).onClick(context.open(new WarnsScreen(nickname, 1))));
        }
        if (context.canUse("notes")) {
            menu.set(29, context.item(MenuIcon.NOTE, placeholders, "player", "notes")
                    .amount(notes).onClick(context.open(new NotesScreen(nickname, 1))));
        }
        if (context.canUse("alts")) {
            menu.set(31, context.item(MenuIcon.ALTS, placeholders, "player", "alts")
                    .amount(alts).onClick(context.open(new AltsScreen(nickname, 1))));
        }

        if (context.canUse("blame")) {
            final int applied = judicator.getPunishService().countByPunisher(judicator.getPlayerDirectory().resolveKnown(nickname), null);
            if (applied > 0) {
                placeholders.put("applied", String.valueOf(applied));
                menu.set(33, context.item(MenuIcon.PUNISH, placeholders, "player", "blame")
                        .amount(applied).onClick(context.open(PunishmentsScreen.blame(nickname, 1))));
            }
        }
        if (context.canUse("punish")) {
            menu.set(38, context.item(MenuIcon.PUNISH, placeholders, "player", "punish")
                    .onClick(context.open(new ReasonsScreen(nickname))));
        }
        if (online.isPresent() && context.canUse("kick")) {
            menu.set(39, context.item(MenuIcon.KICK, placeholders, "player", "kick")
                    .onClick(context.open(ConfirmScreen.withReason(MenuIcon.KICK, "kick", placeholders, "kick", nickname))));
        }
        if (context.canUse("warn")) {
            menu.set(40, context.item(MenuIcon.WARN, placeholders, "player", "warn")
                    .onClick(() -> context.menus().askCommand(context.viewer(), context.text(Map.of(), "player", "warn-what"),
                            "warn", reason -> nickname + " " + reason)));
        }
        if (context.canUse("note")) {
            menu.set(41, context.item(MenuIcon.NOTE, placeholders, "player", "note")
                    .onClick(() -> context.menus().askCommand(context.viewer(), context.text(Map.of(), "player", "note-what"),
                            "note", text -> nickname + " " + text)));
        }
        if (ban.isPresent() && context.canUse("unban")) {
            menu.set(42, context.item(MenuIcon.PARDON, placeholders, "player", "unban")
                    .onClick(context.open(ConfirmScreen.withReason(MenuIcon.PARDON, "unban", placeholders, "unban", nickname))));
        }
        if (mute.isPresent() && context.canUse("unmute")) {
            menu.set(43, context.item(MenuIcon.PARDON, placeholders, "player", "unmute")
                    .onClick(context.open(ConfirmScreen.withReason(MenuIcon.PARDON, "unmute", placeholders, "unmute", nickname))));
        }
        // the navigation on a row of its own, as on the lists
        menu.set(45, context.item(MenuIcon.BACK, Map.of(), "navigation", "back").onClick(context.back()));
        menu.set(53, context.item(MenuIcon.CLOSE, Map.of(), "navigation", "close")
                .onClick(() -> context.menus().close(context.viewer())));
        return menu.build();
    }

    private static MenuItem inForceItem(MenuContext context, Optional<Punishment> punishment, String key, String noneKey) {
        if (punishment.isEmpty()) return context.item(MenuIcon.CONFIRM, Map.of(), "player", noneKey);
        final MenuItem item = context.item(Icons.of(punishment.get().getType()), punishment.get(), "player", key);
        return context.canUse("punishview")
                ? item.onClick(context.open(new PunishmentScreen(punishment.get().getId())))
                : item;
    }

    private static Optional<Punishment> inForce(List<Punishment> history, Predicate<Punishment> ofKind) {
        return history.stream()
                .filter(ofKind)
                .filter(p -> p.getStatus() == PunishStatus.ACTIVE)
                .min(PunishService.heavyFirst());
    }

    private static long count(List<Punishment> history, PunishType... types) {
        final List<PunishType> wanted = List.of(types);
        return history.stream().filter(p -> wanted.contains(p.getType())).count();
    }
}
