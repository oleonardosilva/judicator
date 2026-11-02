package com.elless.judicator.punish.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.Revocation;
import com.elless.judicator.punish.handlers.RevokeHandler;
import com.elless.judicator.punish.rules.StaffRateLimit;
import com.elless.judicator.punish.types.PunishPermissions;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UnpunishCommand extends CoreCommand {

    private final RevokeHandler.Kind kind;

    private UnpunishCommand(Judicator judicator, String name, PunishPermissions permission, RevokeHandler.Kind kind) {
        super(judicator, name, List.of(), permission.getPermission(), PunishPermissions.ADMIN.getPermission());
        this.kind = kind;
    }

    public static List<CoreCommand> all(Judicator judicator) {
        return List.of(
                new UnpunishCommand(judicator, "unban", PunishPermissions.UNBAN, RevokeHandler.Kind.BAN),
                new UnpunishCommand(judicator, "unmute", PunishPermissions.UNMUTE, RevokeHandler.Kind.MUTE),
                new UnpunishCommand(judicator, "unjail", PunishPermissions.UNJAIL, RevokeHandler.Kind.JAIL)
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
        final boolean silent = args.consumeFlag(PunishmentFlags.SILENT);
        final String reason = args.remaining();
        if (silent && !silentAllowed(source)) return;

        final ConfigurationNode messages = messages(source);
        if (!judicator.getStaffRateLimit().acquire(source, StaffRateLimit.REVOKE)) return;
        final RevokeHandler.ActiveRevocation revocation = new RevokeHandler(judicator)
                .revokeActive(targetName, kind, Revocation.now(reason, source), silent);
        if (revocation.revoked().isEmpty()) judicator.getStaffRateLimit().release(source, StaffRateLimit.REVOKE);
        if (!revocation.found()) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", switch (kind) {
                case BAN -> "not-banned";
                case MUTE -> "not-muted";
                case JAIL -> "not-jailed";
            }));
            return;
        }
        final List<Punishment> revoked = revocation.revoked();
        if (revoked.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "unknown"));
            return;
        }

        source.sendMessage(PunishUtils.format(messages, Map.of("nickname", revoked.get(0).getNickname(), "count", String.valueOf(revoked.size())),
                "success", getName()));
    }

}
