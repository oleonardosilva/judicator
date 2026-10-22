package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.ChatContext;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.handlers.PunishFactory;

import java.util.Optional;

final class PunishmentCommandSupport {

    private PunishmentCommandSupport() {
    }

    static void resolveTarget(Judicator judicator, JudicatorSender source, PunishmentBuilder builder, String targetName, boolean byAddress) {
        final Optional<JudicatorPlayer> online = judicator.getPlatform().getPlayer(targetName);
        if (online.isPresent()) {
            builder.target(online.get());
            if (byAddress) builder.ipAddress(online.get().getHostAddress());
            return;
        }

        builder.target(targetName, judicator.getUuidManager().getUUID(targetName));
        if (!byAddress) return;

        judicator.getAddressService().findByUsername(targetName).ifPresentOrElse(
                accessAddress -> builder.ipAddress(accessAddress.getHostAddress()),
                () -> source.sendMessage(PunishUtils.getMessage(judicator.getMessagesConfig(), "error", "player-ip-not-found"))
        );
    }

    static void submit(Judicator judicator, JudicatorSender source, PunishmentBuilder builder) {
        final Optional<JudicatorPlayer> optPlayer = source.asPlayer();
        if (optPlayer.isEmpty()) {
            new PunishFactory(judicator, builder).factory();
            return;
        }

        final JudicatorPlayer player = optPlayer.get();
        final String punisher = player.getName();
        builder.punisher(player);
        player.sendMessage(PunishUtils.getConfirmationMessage(judicator.getMessagesConfig()));

        final ChatContext<PunishmentBuilder> chatContext = ChatContext.buildPunishmentContext(punisher, builder, judicator);
        judicator.getPunishCache().putContext(punisher, chatContext);
    }

}
