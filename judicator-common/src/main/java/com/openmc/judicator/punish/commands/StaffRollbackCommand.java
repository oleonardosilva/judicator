package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.DateTimeOffsetParser;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.punish.PunishService;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.handlers.RevokeHandler;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.punish.types.PunishStatus;
import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.handlers.UnwarnHandler;
import com.openmc.judicator.warns.types.WarnStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * {@code /staffrollback <staff> <period> [confirm]}: undoes what a staff member applied in the period (a stolen
 * account, an abusive staff member). Bans and mutes still in force are revoked, so the record of what was done stays;
 * warns still active are deleted, as warns cannot be revoked (each one is logged to the console first). Without
 * {@code confirm} it only shows what would be undone.
 */
public class StaffRollbackCommand extends CoreCommand {

    private static final String CONFIRM = "confirm";

    public StaffRollbackCommand(Judicator judicator) {
        super(judicator, "staffrollback", List.of(), PunishPermissions.STAFFROLLBACK.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> staff = args.word();
        final Optional<String> period = args.word();
        final OptionalLong millis = period.map(DateTimeOffsetParser::parseMillis).orElse(OptionalLong.empty());
        if (staff.isEmpty() || millis.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        final boolean confirm = args.word().map(CONFIRM::equalsIgnoreCase).orElse(false);
        final ConfigurationNode messages = messages(source);
        if (!judicator.getHierarchyService().canActOnStaff(source, staff.get())) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "hierarchy"));
            return;
        }

        final LocalDateTime since = DateTimeOffsetParser.since(millis.getAsLong());
        final PunishService punishService = judicator.getPunishService();
        final PlayerRef staffMember = judicator.getPlayerDirectory().resolveKnown(staff.get());
        final int applied = punishService.countByPunisher(staffMember, since);
        final List<Punishment> punishments = applied == 0 ? List.of()
                : punishService.findByPunisher(staffMember, since, applied, 0).stream()
                .filter(punishment -> punishment.getStatus() == PunishStatus.ACTIVE)
                .toList();
        final List<Warn> warns = judicator.getWarnService().findByPunisherSince(staffMember, since).stream()
                .filter(warn -> warn.getStatus() == WarnStatus.ACTIVE)
                .toList();

        final long bans = punishments.stream().filter(Punishment::isBanType).count();
        final long mutes = punishments.stream().filter(Punishment::isMuteType).count();
        final Map<String, String> placeholders = Map.of(
                "staff", staff.get(),
                "period", DateTimeOffsetParser.format(messages, millis.getAsLong()),
                "bans", String.valueOf(bans),
                "mutes", String.valueOf(mutes),
                "warns", String.valueOf(warns.size()));
        if (punishments.isEmpty() && warns.isEmpty()) {
            source.sendMessage(PunishUtils.format(messages, placeholders, "rollback", "none"));
            return;
        }
        if (!confirm) {
            final String command = "/" + getName() + " " + staff.get() + " " + period.get() + " " + CONFIRM;
            source.sendMessage(Component.text()
                    .append(PunishUtils.format(messages, placeholders, "rollback", "preview"))
                    .append(PunishUtils.format(messages, placeholders, "rollback", "confirm")
                            .hoverEvent(HoverEvent.showText(Component.text(command)))
                            .clickEvent(ClickEvent.runCommand(command)))
                    .build());
            return;
        }

        final String author = source.asPlayer().map(JudicatorPlayer::getName).orElse("Console");
        final String reason = PunishUtils.format(judicator.getMessagesConfig(), Map.of("staff", staff.get(), "author", author),
                "rollback", "revoke-reason").content();
        final List<Punishment> revoked = new RevokeHandler(judicator).revoke(punishments, reason, author, false);
        final UnwarnHandler unwarnHandler = new UnwarnHandler(judicator);
        for (Warn warn : warns) {
            judicator.getLogger().info("Rollback by {}: deleting warn #{} of {} applied by {} at {}: {}",
                    author, warn.getId(), warn.getNickname(), warn.getPunisher(), warn.getStartedAt(), warn.getReason());
            unwarnHandler.remove(warn, author, false);
        }

        source.sendMessage(PunishUtils.format(messages, Map.of(
                "staff", staff.get(),
                "punishments", String.valueOf(revoked.size()),
                "warns", String.valueOf(warns.size())), "rollback", "done"));
        judicator.getStaffNotifier().notifyRollback(author, staff.get(), millis.getAsLong(), revoked.size(), warns.size());
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        if (args.length == 3) return CONFIRM.startsWith(args[2].toLowerCase()) ? List.of(CONFIRM) : List.of();
        return super.suggest(sender, args);
    }

}
