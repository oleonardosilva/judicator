package com.openmc.judicator.warns.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.warns.WarnBuilder;
import com.openmc.judicator.warns.handlers.WarnHandler;

import java.util.List;
import java.util.Optional;

public class WarnCommand extends CoreCommand {

    private final boolean temporary;

    public WarnCommand(Judicator judicator) {
        this(judicator, "warn", false, PunishPermissions.WARN);
    }

    protected WarnCommand(Judicator judicator, String name, boolean temporary, PunishPermissions permission) {
        super(judicator, name, List.of(), permission.getPermission(), PunishPermissions.ADMIN.getPermission());
        this.temporary = temporary;
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> optTarget = args.word();
        if (optTarget.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        final String targetName = optTarget.get();

        String duration = null;
        if (temporary) {
            final Optional<String> optDuration = args.quotableString();
            if (optDuration.isEmpty()) {
                sendUsage(source, getName());
                return;
            }
            duration = optDuration.get();
        }

        final String reason = args.remaining();
        if (reason.isEmpty()) {
            sendUsage(source, getName());
            return;
        }

        if (!judicator.getImmuneCache().canPunish(source, targetName)) return;

        final WarnBuilder builder = new WarnBuilder()
                .reason(reason);

        if (duration != null) {
            try {
                builder.duration(duration);
            } catch (IllegalArgumentException e) {
                sendUsage(source, getName());
                return;
            }
        }

        judicator.getPlatform().getPlayer(targetName).ifPresentOrElse(
                builder::target, () -> builder.target(targetName, judicator.getUuidManager().getUUID(targetName))
        );

        source.asPlayer().ifPresent(builder::punisher);

        new WarnHandler(judicator, builder).handle();
    }

}
