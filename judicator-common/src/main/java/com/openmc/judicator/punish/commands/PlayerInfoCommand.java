package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.AccessAddress;
import com.openmc.judicator.punish.PunishService;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.punish.types.PunishStatus;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.types.WarnStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * {@code /playerinfo <player>}: one screen with what the other commands show separately — punishments in force,
 * history counts, warns, notes, alts and the last login. The last IP only appears to who may see histories.
 */
public class PlayerInfoCommand extends CoreCommand {

    public PlayerInfoCommand(Judicator judicator) {
        super(judicator, "playerinfo", List.of("pinfo"), PunishPermissions.INFO.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> target = args.word();
        if (target.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        final ConfigurationNode messages = messages(source);
        final Optional<JudicatorPlayer> online = judicator.getPlatform().getPlayer(target.get());
        final String nickname = online.map(JudicatorPlayer::getName).orElse(target.get());

        final List<Punishment> history = judicator.getPunishService().findAllByUsername(nickname);
        final List<Warn> warns = judicator.getWarnService().findAllByUsername(nickname);
        final int notes = judicator.getNoteService().countByUsername(nickname);
        final Optional<AccessAddress> lastAddress = judicator.getAddressService().findByUsername(nickname);
        if (online.isEmpty() && history.isEmpty() && warns.isEmpty() && notes == 0 && lastAddress.isEmpty()) {
            source.sendMessage(PunishUtils.format(messages, Map.of("nickname", nickname), "info", "unknown"));
            return;
        }

        final UUID uuid = online.map(JudicatorPlayer::getUniqueId).orElseGet(() -> judicator.getUuidManager().getUUID(nickname));
        final DateTimeFormatter dates = DateTimeFormatter.ofPattern(messages.node("date-format").getString("dd/MM/yyyy HH:mm"));
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("nickname", nickname);
        placeholders.put("uuid", uuid == null ? "?" : uuid.toString());
        placeholders.put("state", messages.node("info", online.isPresent() ? "online" : "offline").getString("").replace("&", "§"));
        placeholders.put("bans", String.valueOf(count(history, PunishType.BAN, PunishType.TEMPBAN)));
        placeholders.put("mutes", String.valueOf(count(history, PunishType.MUTE, PunishType.TEMPMUTE)));
        placeholders.put("kicks", String.valueOf(count(history, PunishType.KICK)));
        placeholders.put("warns", String.valueOf(warns.size()));
        placeholders.put("activeWarns", String.valueOf(warns.stream().filter(w -> w.getStatus() == WarnStatus.ACTIVE).count()));
        placeholders.put("notes", String.valueOf(notes));
        placeholders.put("alts", String.valueOf(judicator.getAddressService().findAlts(nickname, judicator.getAddressPolicy()::isExempt).size()));
        placeholders.put("lastSeen", online.isPresent()
                ? messages.node("info", "now").getString("now").replace("&", "§")
                : lastAddress.map(address -> dates.format(address.getLastUsage())).orElse("?"));

        final TextComponent.Builder text = Component.text()
                .append(PunishUtils.format(messages, placeholders, "info", "title"))
                .append(PunishUtils.format(messages, placeholders, "info", "uuid"));
        text.append(inForce(messages, history, "ban", Punishment::isBanType));
        text.append(inForce(messages, history, "mute", Punishment::isMuteType));
        text.append(line(messages, placeholders, "punishments", "/phistory " + nickname));
        text.append(line(messages, placeholders, "warns", "/whistory " + nickname));
        text.append(line(messages, placeholders, "notes", "/notes " + nickname));
        text.append(line(messages, placeholders, "alts", "/alts " + nickname));
        text.append(PunishUtils.format(messages, placeholders, "info", "last-seen"));
        final boolean seesAddresses = source.hasPermission(PunishPermissions.HISTORY.getPermission())
                || source.hasPermission(PunishPermissions.ADMIN.getPermission());
        if (seesAddresses) {
            final String ip = online.map(JudicatorPlayer::getHostAddress)
                    .or(() -> lastAddress.map(AccessAddress::getHostAddress)).orElse("?");
            text.append(PunishUtils.format(messages, Map.of("ip", ip), "info", "ip"));
        }
        source.sendMessage(text.build());
    }

    private static long count(List<Punishment> history, PunishType... types) {
        final List<PunishType> wanted = List.of(types);
        return history.stream().filter(p -> wanted.contains(p.getType())).count();
    }

    private static Component inForce(ConfigurationNode messages, List<Punishment> history, String kind,
                                     Predicate<Punishment> ofKind) {
        final Optional<Punishment> active = history.stream()
                .filter(ofKind)
                .filter(p -> p.getStatus() == PunishStatus.ACTIVE)
                .min(PunishService.heavyFirst());
        if (active.isEmpty()) return PunishUtils.format(messages, Map.of(), "info", "no-" + kind);
        return PunishUtils.getMessageList(messages, active.get(), "info", kind)
                // the hover of /banlist: /phistory's one shows the IP, which /playerinfo only shows to who may see histories
                .hoverEvent(HoverEvent.showText(PunishUtils.getMessageList(messages, active.get(), "banlist", "hover")))
                .clickEvent(ClickEvent.runCommand("/pview " + active.get().getId()));
    }

    private static Component line(ConfigurationNode messages, Map<String, String> placeholders, String key, String command) {
        return PunishUtils.format(messages, placeholders, "info", key)
                .hoverEvent(HoverEvent.showText(Component.text(command)))
                .clickEvent(ClickEvent.runCommand(command));
    }

}
