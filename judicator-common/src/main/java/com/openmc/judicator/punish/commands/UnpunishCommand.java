package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.handlers.RevokeHandler;
import com.openmc.judicator.punish.types.PunishPermissions;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UnpunishCommand extends CoreCommand {

    private final boolean mute;

    private UnpunishCommand(Judicator judicator, String name, PunishPermissions permission, boolean mute) {
        super(judicator, name, List.of(), permission.getPermission(), PunishPermissions.ADMIN.getPermission());
        this.mute = mute;
    }

    public static List<CoreCommand> all(Judicator judicator) {
        return List.of(
                new UnpunishCommand(judicator, "unban", PunishPermissions.UNBAN, false),
                new UnpunishCommand(judicator, "unmute", PunishPermissions.UNMUTE, true)
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
        final boolean silent = args.consumeFlag(PunishTypeCommand.SILENT_FLAG);
        final String reason = args.remaining();
        if (silent && !silentAllowed(source)) return;

        final ConfigurationNode messages = messages(source);
        final String author = source.asPlayer().map(JudicatorPlayer::getName).orElse("Console");
        final RevokeHandler.ActiveRevocation revocation = new RevokeHandler(judicator).revokeActive(targetName, mute, reason, author, silent);
        if (!revocation.found()) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", mute ? "not-muted" : "not-banned"));
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
