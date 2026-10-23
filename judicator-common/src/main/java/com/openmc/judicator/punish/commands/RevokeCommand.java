package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishService;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.handlers.RevokeHandler;
import com.openmc.judicator.punish.types.PunishPermissions;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Optional;

public class RevokeCommand extends CoreCommand {

    public RevokeCommand(Judicator judicator) {
        super(judicator, "revoke", List.of("revogar", "revocar"), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<Long> optId = args.word().flatMap(PunishUtils::parseId);
        if (optId.isEmpty()) {
            sendUsage(source, "revoke");
            return;
        }
        final Long id = optId.get();
        final String reason = args.remaining();
        final ConfigurationNode messages = messages(source);
        final PunishService punishService = judicator.getPunishService();

        final Optional<Punishment> optPunishment = punishService.findById(id);
        if (optPunishment.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "punish-not-found"));
            return;
        }

        final Punishment punishment = optPunishment.get();
        if (punishment.isRevoked()) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "already-revoked"));
            return;
        }

        final String author = source.asPlayer().map(JudicatorPlayer::getName).orElse("Console");
        judicator.getPlatform().runAsync(() -> {
            final boolean revoked = !new RevokeHandler(judicator).revoke(List.of(punishment), reason, author, true).isEmpty();
            source.sendMessage(revoked
                    ? PunishUtils.getMessage(messages, "success", "pardon")
                    : PunishUtils.getMessage(messages, "error", "unknown"));
        });
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        return List.of();
    }

}
