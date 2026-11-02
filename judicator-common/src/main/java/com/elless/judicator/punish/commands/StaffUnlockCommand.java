package com.elless.judicator.punish.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.rules.StaffRateLimit;
import com.elless.judicator.punish.types.PunishPermissions;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /staffunlock [staff]}: lets a staff member locked by {@code staff-rate-limit} punish again; without a name it
 * lists who is locked. Nobody unlocks themselves, and the hierarchy applies as in {@code /staffrollback}.
 */
public class StaffUnlockCommand extends CoreCommand {

    public StaffUnlockCommand(Judicator judicator) {
        super(judicator, "staffunlock", List.of(), PunishPermissions.STAFFUNLOCK.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final ConfigurationNode messages = messages(source);
        final StaffRateLimit rateLimit = judicator.getStaffRateLimit();
        final Optional<String> staff = args.word();
        if (staff.isEmpty()) {
            final List<String> locked = rateLimit.locked();
            source.sendMessage(locked.isEmpty() ? PunishUtils.getMessage(messages, "rate-limit", "none")
                    : PunishUtils.format(messages, Map.of("staff", String.join(", ", locked)), "rate-limit", "list"));
            return;
        }
        if (source.getName().equalsIgnoreCase(staff.get())
                || source.asPlayer().map(JudicatorPlayer::getUniqueId).map(rateLimit::isLocked).orElse(false)) {
            source.sendMessage(PunishUtils.getMessage(messages, "rate-limit", "self"));
            return;
        }
        if (!judicator.getHierarchyService().canActOnStaff(source, staff.get())) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "hierarchy"));
            return;
        }
        final Optional<String> unlocked = rateLimit.unlock(staff.get());
        if (unlocked.isEmpty()) {
            source.sendMessage(PunishUtils.format(messages, Map.of("staff", staff.get()), "rate-limit", "not-locked"));
            return;
        }
        final Map<String, String> values = Map.of("staff", unlocked.get(), "author", source.getName());
        judicator.getLogger().info("{} unlocked {}, locked by the staff rate limit", source.getName(), unlocked.get());
        source.sendMessage(PunishUtils.format(messages, values, "rate-limit", "unlocked"));
        judicator.getStaffNotifier().sendToPermission(StaffRateLimit.NOTIFY,
                recipient -> PunishUtils.format(recipient, values, "rate-limit", "unlocked-notice"));
        judicator.getPlatform().getPlayer(unlocked.get()).ifPresent(player ->
                player.sendMessage(PunishUtils.getMessage(judicator.messagesFor(player), "rate-limit", "you-unlocked")));
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        if (args.length > 1) return List.of();
        final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return judicator.getStaffRateLimit().locked().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }

}
