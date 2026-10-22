package com.openmc.judicator.punish.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.Platform;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.data.cache.PunishCache;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

public class MuteHandler implements PunishHandler {

    private final Judicator judicator;
    private final PunishmentBuilder punishmentBuilder;
    private final ConfigurationNode messagesNode;
    private final PunishCache cache;

    public MuteHandler(Judicator judicator, PunishmentBuilder punishmentBuilder) {
        this.judicator = judicator;
        this.punishmentBuilder = punishmentBuilder;
        this.messagesNode = judicator.getMessagesConfig();
        this.cache = judicator.getPunishCache();
    }

    public Punishment handle() {
        final Punishment punishment = judicator.getPunishService().save(punishmentBuilder.build());
        this.mute(punishment);
        this.announce(punishment);
        return punishment;
    }

    private void mute(Punishment punishment) {
        final Platform platform = judicator.getPlatform();
        platform.runSync(() -> {
            final TextComponent muteAlert = PunishUtils.getMessageList(messagesNode, punishment, "runners", "mute-alert");
            BanHandler.affectedPlayers(platform, punishment).forEach(player -> {
                player.sendMessage(muteAlert);
                cache.putMutePunishment(player.getName().toLowerCase(), punishment);
            });
        });
    }

    private void announce(Punishment punishment) {
        judicator.getDiscordWebhookService().sendPunishment(punishment);

        if (judicator.getConfig().node("announce").getBoolean(true)) {
            final Platform platform = judicator.getPlatform();
            platform.getPlayer(punishment.getNickname()).ifPresent(player -> {
                final TextComponent announcement = PunishUtils.getMessageList(messagesNode, punishment, "announcements", "mute");
                platform.announceToServerOf(player, announcement);
            });
        }
    }
}
