package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.api.event.PunishmentEditedEvent;
import com.openmc.judicator.api.punishment.PunishmentView;
import com.openmc.judicator.commons.DateTimeOffsetParser;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.integration.ApiViews;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.ReasonShortcuts;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.punish.types.PunishStatus;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Fixes a punishment in force without revoking it and punishing again: {@code /extend <id> <duration>} adds time to
 * a temporary ban or mute, {@code /changereason <id> <reason>} replaces its reason. Only the console, the author, or
 * a staff member above the author (and allowed to punish the player) may change it.
 */
public class EditPunishmentCommand extends CoreCommand {

    private static final int REASON_MAX_LENGTH = 255;

    private final boolean extend;

    private EditPunishmentCommand(Judicator judicator, String name, boolean extend, PunishPermissions permission) {
        super(judicator, name, List.of(), permission.getPermission(), PunishPermissions.ADMIN.getPermission());
        this.extend = extend;
    }

    public static List<CoreCommand> all(Judicator judicator) {
        return List.of(
                new EditPunishmentCommand(judicator, "extend", true, PunishPermissions.EXTEND),
                new EditPunishmentCommand(judicator, "changereason", false, PunishPermissions.CHANGEREASON)
        );
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<Long> id = args.word().flatMap(PunishUtils::parseId);
        final Optional<String> value = extend ? args.quotableString() : Optional.of(args.remaining()).filter(s -> !s.isBlank());
        if (id.isEmpty() || value.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        final ConfigurationNode messages = messages(source);
        final Optional<Punishment> found = judicator.getPunishService().findById(id.get());
        if (found.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "punish-not-found"));
            return;
        }
        final Punishment punishment = found.get();
        if (punishment.getStatus() != PunishStatus.ACTIVE) {
            source.sendMessage(PunishUtils.getMessage(messages, "edit", "not-active"));
            return;
        }
        if (!judicator.getHierarchyService().canActOnStaff(source, punishment.getPunisher())) {
            source.sendMessage(PunishUtils.getMessage(messages, "edit", "not-allowed"));
            return;
        }
        if (!judicator.getHierarchyService().canPunish(source, punishment.getNickname())) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "hierarchy"));
            return;
        }

        // a snapshot: storing the change updates the punishment object itself
        final Optional<PunishmentView> previous = judicator.getEventBus().capture(() -> ApiViews.punishment(punishment));
        final Optional<Boolean> changed = extend ? extend(source, messages, punishment, value.get()) : changeReason(source, punishment, value.get());
        if (changed.isEmpty()) return;
        if (!changed.get()) {
            source.sendMessage(PunishUtils.getMessage(messages, "edit", "not-active"));
            return;
        }
        source.sendMessage(PunishUtils.getMessageList(messages, punishment, "edit", extend ? "extended" : "reason-changed"));
        final String author = source.asPlayer().map(JudicatorPlayer::getName).orElse("Console");
        judicator.getStaffNotifier().notifyEdit(author, punishment, extend ? "change-duration" : "change-reason");
        judicator.getEventBus().post(() -> previous
                .map(before -> new PunishmentEditedEvent(before, ApiViews.punishment(punishment), author))
                .orElse(null));
    }

    /**
     * Adds the duration to the end, or to what is left of a paused online-only mute.
     *
     * @return empty when it must not go on (invalid duration, permanent, beyond the source's time limit: the reason was
     * sent); otherwise whether it was stored (false: revoked meanwhile)
     */
    private Optional<Boolean> extend(JudicatorSender source, ConfigurationNode messages, Punishment punishment, String duration) {
        if (!punishment.getType().isTemp() || (punishment.getFinishAt().isEmpty() && !punishment.isPaused())) {
            source.sendMessage(PunishUtils.getMessage(messages, "edit", "permanent"));
            return Optional.empty();
        }
        final OptionalLong millis = DateTimeOffsetParser.parseMillis(duration);
        if (millis.isEmpty()) {
            sendUsage(source, getName());
            return Optional.empty();
        }
        if (punishment.isPaused()) {
            final long left = punishment.getPausedRemaining() + millis.getAsLong();
            final LocalDateTime now = LocalDateTime.now();
            if (!judicator.getTimeLimitService().allows(source, punishment.getType(), now, now.plus(Duration.ofMillis(left)))) {
                return Optional.empty();
            }
            return Optional.of(judicator.getPunishService().changeEnd(punishment, null, left));
        }
        final LocalDateTime newEnd = DateTimeOffsetParser.getFinishedAtFromDuration(duration, punishment.getFinishAt().get());
        if (!judicator.getTimeLimitService().allows(source, punishment.getType(), punishment.getStartedAt(), newEnd)) {
            return Optional.empty();
        }
        return Optional.of(judicator.getPunishService().changeEnd(punishment, newEnd, null));
    }

    private Optional<Boolean> changeReason(JudicatorSender source, Punishment punishment, String typed) {
        final Optional<String> reason = ReasonShortcuts.expand(judicator, source, typed);
        if (reason.isEmpty()) return Optional.empty();
        final String stored = reason.get().length() <= REASON_MAX_LENGTH ? reason.get() : reason.get().substring(0, REASON_MAX_LENGTH);
        return Optional.of(judicator.getPunishService().changeReason(punishment, stored));
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        return extend ? List.of() : ReasonShortcuts.suggest(judicator, sender, args, 1);
    }

}
