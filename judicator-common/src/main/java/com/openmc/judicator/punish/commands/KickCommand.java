package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.types.PunishPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Optional;

public class KickCommand extends CoreCommand {

    public KickCommand(Judicator judicator) {
        super(judicator, "kick", List.of(), PunishPermissions.KICK.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> optTarget = args.word();
        if (optTarget.isEmpty()) {
            sendUsage(source, "kick");
            return;
        }
        final String targetName = optTarget.get();
        final ConfigurationNode messages = messages();
        if (!judicator.getImmuneCache().canPunish(source, targetName)) return;
        final String remaining = args.remaining();
        final String reason = remaining.isEmpty() ? "None" : remaining;

        final Optional<JudicatorPlayer> optPlayer = judicator.getPlatform().getPlayer(targetName);
        if (optPlayer.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "player-offline"));
            return;
        }

        final String punisher = source.asPlayer().map(JudicatorPlayer::getName).orElse("Console");
        final JudicatorPlayer player = optPlayer.get();

        judicator.getPlatform().runSync(() -> {
            final TextComponent kickMessage = Component.text(PunishUtils.getMessageList(messages, "runners", "kick").content()
                    .replace("{nickname}", targetName)
                    .replace("{author}", punisher)
                    .replace("{reason}", reason));

            if (judicator.getConfig().node("announce").getBoolean(true)) {
                final TextComponent announcement = Component.text(PunishUtils.getMessageList(messages, "announcements", "kick").content()
                        .replace("{nickname}", targetName)
                        .replace("{author}", punisher)
                        .replace("{reason}", reason));
                judicator.getPlatform().announceToServerOf(player, announcement);
            }
            player.disconnect(kickMessage);
        });
    }

}
