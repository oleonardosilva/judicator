package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.ReasonShortcuts;
import com.openmc.judicator.punish.rules.PunishCooldown;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.punish.types.PunishType;

import java.util.List;
import java.util.Optional;

public class PunishTypeCommand extends CoreCommand {

    static final String SILENT_FLAG = "-s";
    /**
     * Temporary mutes: the time only runs while the player is online.
     */
    static final String ONLINE_ONLY_FLAG = "-o";

    private final PunishType type;
    private final boolean byAddress;
    private final boolean soft;

    private PunishTypeCommand(Judicator judicator, String name, PunishType type, boolean byAddress, PunishPermissions permission) {
        this(judicator, name, type, byAddress, false, permission);
    }

    private PunishTypeCommand(Judicator judicator, String name, PunishType type, boolean byAddress, boolean soft, PunishPermissions permission) {
        super(judicator, name, List.of(), permission.getPermission(), PunishPermissions.ADMIN.getPermission());
        this.type = type;
        this.byAddress = byAddress;
        this.soft = soft;
    }

    public static List<CoreCommand> all(Judicator judicator) {
        return List.of(
                new PunishTypeCommand(judicator, "ban", PunishType.BAN, false, PunishPermissions.BAN),
                new PunishTypeCommand(judicator, "banip", PunishType.BAN, true, PunishPermissions.BANIP),
                new PunishTypeCommand(judicator, "tempban", PunishType.TEMPBAN, false, PunishPermissions.TEMPBAN),
                new PunishTypeCommand(judicator, "tempbanip", PunishType.TEMPBAN, true, PunishPermissions.TEMPBANIP),
                new PunishTypeCommand(judicator, "mute", PunishType.MUTE, false, PunishPermissions.MUTE),
                new PunishTypeCommand(judicator, "muteip", PunishType.MUTE, true, PunishPermissions.MUTEIP),
                new PunishTypeCommand(judicator, "tempmute", PunishType.TEMPMUTE, false, PunishPermissions.TEMPMUTE),
                new PunishTypeCommand(judicator, "tempmuteip", PunishType.TEMPMUTE, true, PunishPermissions.TEMPMUTEIP),
                new PunishTypeCommand(judicator, "softmute", PunishType.MUTE, false, true, PunishPermissions.SOFTMUTE),
                new PunishTypeCommand(judicator, "tempsoftmute", PunishType.TEMPMUTE, false, true, PunishPermissions.TEMPSOFTMUTE)
        );
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> optTarget = args.word();
        if (optTarget.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        final String targetName = optTarget.get();
        final Flags flags = new Flags();
        flags.read(args);

        String duration = null;
        if (type.isTemp()) {
            final Optional<String> optDuration = args.quotableString();
            if (optDuration.isEmpty()) {
                sendUsage(source, getName());
                return;
            }
            duration = optDuration.get();
            flags.read(args);
        }
        final boolean silent = flags.silent;
        final boolean onlineOnly = flags.onlineOnly;
        if (onlineOnly && byAddress) {
            source.sendMessage(PunishUtils.getMessage(messages(source), "error", "online-only-ip"));
            return;
        }
        final Optional<String> reason = ReasonShortcuts.expand(judicator, source, args.remaining());
        if (reason.isEmpty()) return;

        if (silent && !silentAllowed(source)) return;
        if (!judicator.getImmuneCache().canPunish(source, targetName, PunishCooldown.categoryOf(type))) return;

        final PunishmentBuilder builder = new PunishmentBuilder()
                .type(type)
                .reason(reason.get())
                .silent(silent)
                .soft(soft)
                .onlineOnly(onlineOnly);

        if (duration != null) {
            try {
                builder.duration(duration);
            } catch (IllegalArgumentException e) {
                sendUsage(source, getName());
                return;
            }
        }

        if (!PunishmentCommandSupport.passesStaffRules(judicator, source, builder, targetName)) return;
        if (!PunishmentCommandSupport.resolveTarget(judicator, source, builder, targetName, byAddress)) return;
        PunishmentCommandSupport.submit(judicator, source, builder);
    }

    /**
     * {@code -s} and, on temporary mutes, {@code -o}, in any order, before or after the duration.
     */
    private final class Flags {
        private boolean silent;
        private boolean onlineOnly;

        private void read(CommandArgs args) {
            while (true) {
                if (args.consumeFlag(SILENT_FLAG)) silent = true;
                else if (type == PunishType.TEMPMUTE && args.consumeFlag(ONLINE_ONLY_FLAG)) onlineOnly = true;
                else return;
            }
        }
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        final List<String> shortcuts = ReasonShortcuts.suggest(judicator, sender, args, type.isTemp() ? 2 : 1);
        return shortcuts.isEmpty() ? super.suggest(sender, args) : shortcuts;
    }

}
