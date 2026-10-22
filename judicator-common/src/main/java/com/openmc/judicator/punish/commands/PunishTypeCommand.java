package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.punish.types.PunishType;

import java.util.List;
import java.util.Optional;

public class PunishTypeCommand extends CoreCommand {

    static final String SILENT_FLAG = "-s";

    private final PunishType type;
    private final boolean byAddress;

    private PunishTypeCommand(Judicator judicator, String name, PunishType type, boolean byAddress, PunishPermissions permission) {
        super(judicator, name, List.of(), permission.getPermission(), PunishPermissions.ADMIN.getPermission());
        this.type = type;
        this.byAddress = byAddress;
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
                new PunishTypeCommand(judicator, "tempmuteip", PunishType.TEMPMUTE, true, PunishPermissions.TEMPMUTEIP)
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
        boolean silent = args.consumeFlag(SILENT_FLAG);

        String duration = null;
        if (type.isTemp()) {
            final Optional<String> optDuration = args.quotableString();
            if (optDuration.isEmpty()) {
                sendUsage(source, getName());
                return;
            }
            duration = optDuration.get();
            silent |= args.consumeFlag(SILENT_FLAG);
        }
        final String reason = args.remaining();

        if (silent && !silentAllowed(source)) return;
        if (!judicator.getImmuneCache().canPunish(source, targetName)) return;

        final PunishmentBuilder builder = new PunishmentBuilder()
                .type(type)
                .reason(reason)
                .silent(silent);

        if (duration != null) {
            try {
                builder.duration(duration);
            } catch (IllegalArgumentException e) {
                sendUsage(source, getName());
                return;
            }
        }

        if (!PunishmentCommandSupport.resolveTarget(judicator, source, builder, targetName, byAddress)) return;
        PunishmentCommandSupport.submit(judicator, source, builder);
    }

}
