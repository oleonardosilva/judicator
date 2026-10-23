package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.ChatContext;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.handlers.PunishFactory;
import com.openmc.judicator.punish.rules.PunishCooldown;

import java.util.Optional;

final class PunishmentCommandSupport {

    private PunishmentCommandSupport() {
    }

    /**
     * Rules that depend on who punishes: the sender's time limit and the cooldown on the target. Checked before the
     * target is resolved; the cooldown is checked again when the punishment is confirmed.
     *
     * @return false when the punishment must not go on (the reason was already sent to the source)
     */
    static boolean passesStaffRules(Judicator judicator, JudicatorSender source, PunishmentBuilder builder, String targetName) {
        if (!judicator.getTimeLimitService().allows(source, builder.getType(), builder.getStartedAt(), builder.getFinishAt())) {
            return false;
        }
        return judicator.getPunishCooldown().allows(source, PunishCooldown.categoryOf(builder.getType()), targetName);
    }

    /**
     * Fills the target (and, for IP punishments, the address) of the builder.
     *
     * @return false when the punishment must not go on: the address is in {@code exempt-addresses}, or an IP
     * punishment would reach an account the source cannot punish (the reason was already sent to the source).
     */
    static boolean resolveTarget(Judicator judicator, JudicatorSender source, PunishmentBuilder builder, String targetName, boolean byAddress) {
        final Optional<JudicatorPlayer> online = judicator.getPlatform().getPlayer(targetName);
        String address = null;
        if (online.isPresent()) {
            builder.target(online.get());
            if (byAddress) address = online.get().getHostAddress();
        } else {
            builder.target(targetName, judicator.getUuidManager().getUUID(targetName));
            if (byAddress) {
                address = judicator.getAddressService().findByUsername(targetName)
                        .map(accessAddress -> accessAddress.getHostAddress())
                        .orElse(null);
                if (address == null) {
                    source.sendMessage(PunishUtils.getMessage(judicator.messagesFor(source), "error", "player-ip-not-found"));
                }
            }
        }

        if (address == null) return true;
        if (judicator.getAddressPolicy().isExempt(address)) {
            source.sendMessage(PunishUtils.getMessage(judicator.messagesFor(source), "error", "address-exempt"));
            return false;
        }
        if (!judicator.getImmuneCache().canPunishAddress(source, address, PunishCooldown.categoryOf(builder.getType()))) return false;
        builder.ipAddress(address);
        return true;
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
        player.sendMessage(PunishUtils.getConfirmationMessage(judicator.messagesFor(player)));

        final ChatContext<PunishmentBuilder> chatContext = ChatContext.buildPunishmentContext(punisher, builder, judicator);
        judicator.getPunishCache().putContext(punisher, chatContext);
    }

}
