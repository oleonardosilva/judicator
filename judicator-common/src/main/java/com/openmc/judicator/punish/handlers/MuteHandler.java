package com.openmc.judicator.punish.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.Platform;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.data.cache.PunishCache;

import java.time.Duration;

public class MuteHandler implements PunishHandler {

    private final Judicator judicator;
    private final PunishmentBuilder punishmentBuilder;
    private final PunishCache cache;

    public MuteHandler(Judicator judicator, PunishmentBuilder punishmentBuilder) {
        this.judicator = judicator;
        this.punishmentBuilder = punishmentBuilder;
        this.cache = judicator.getPunishCache();
    }

    public Punishment handle() {
        final Punishment built = punishmentBuilder.build();
        // An online-only mute of a player who is not online starts paused: its time runs from their next login.
        if (built.isOnlineOnly() && built.getFinishAt().isPresent()
                && judicator.getPlatform().getPlayer(built.getNickname()).isEmpty()) {
            built.setPausedRemaining(Duration.between(built.getStartedAt(), built.getFinishAt().get()).toMillis());
            built.setFinishAt(null);
        }
        final Punishment punishment = judicator.getPunishService().save(built);
        this.mute(punishment);
        this.announce(punishment);
        return punishment;
    }

    private void mute(Punishment punishment) {
        final Platform platform = judicator.getPlatform();
        platform.runSync(() -> {
            BanHandler.affectedPlayers(platform, punishment).forEach(player -> {
                // a soft-muted player must not notice it
                if (!punishment.isSoft()) {
                    player.sendMessage(PunishUtils.getMessageList(judicator.messagesFor(player), punishment, "runners", "mute-alert"));
                }
                cache.putMutePunishment(player.getName().toLowerCase(), punishment);
            });
        });
    }

    private void announce(Punishment punishment) {
        judicator.getDiscordWebhookService().sendPunishment(punishment);

        judicator.getStaffNotifier().notifyPunishment(punishment);
        if (!punishment.isSilent() && !punishment.isSoft() && judicator.getConfig().node("announce").getBoolean(true)) {
            final Platform platform = judicator.getPlatform();
            platform.getPlayer(punishment.getNickname()).ifPresent(player -> platform.announceToServerOf(player,
                    recipient -> PunishUtils.getMessageList(judicator.messagesFor(recipient), punishment, "announcements", "mute")));
        }
    }
}
