package com.openmc.judicator.punish.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.api.event.PlayerKickedEvent;
import com.openmc.judicator.integration.ApiViews;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Kicks a player online here, once the rules allowed it ({@code /kick} or the API): the kick screen, the announcement,
 * the staff notification, the history with {@code log-kicks} and the API event.
 */
public class KickHandler {

    private static final int REASON_MAX_LENGTH = 255;

    private final Judicator judicator;

    public KickHandler(Judicator judicator) {
        this.judicator = judicator;
    }

    /**
     * @param reason the reason, {@code None} when none was given
     * @return the kick as stored in the history; empty when {@code log-kicks} is off
     */
    public Optional<Punishment> handle(JudicatorPlayer player, String punisher, String reason, boolean silent) {
        final String targetName = player.getName();
        judicator.getPlatform().runSync(() -> {
            final TextComponent kickMessage = kickText(judicator.messagesFor(player), "runners", targetName, punisher, reason);
            if (!silent && PunishUtils.announces(judicator.getConfig(), "kick")) {
                judicator.getPlatform().announceToServerOf(player,
                        recipient -> kickText(judicator.messagesFor(recipient), "announcements", targetName, punisher, reason));
            }
            player.disconnect(kickMessage);
        });
        judicator.getStaffNotifier().notifyKick(punisher, targetName, reason, silent);
        final Punishment kick = kickRecord(player, punisher, reason);
        final Optional<Punishment> logged = judicator.getConfig().node("log-kicks").getBoolean(false)
                ? Optional.of(judicator.getPunishService().save(kick))
                : Optional.empty();
        judicator.getDiscordWebhookService().sendPunishment(logged.orElse(kick));
        judicator.getEventBus().post(() -> new PlayerKickedEvent(targetName, player.getUniqueId(), punisher, reason, silent,
                logged.map(ApiViews::punishment)));
        return logged;
    }

    private static TextComponent kickText(ConfigurationNode messages, String section, String nickname, String author, String reason) {
        return Component.text(PunishUtils.getMessageList(messages, section, "kick").content()
                .replace("{nickname}", nickname)
                .replace("{author}", author)
                .replace("{reason}", reason));
    }

    /**
     * The kick as a punishment, stored in the history only with {@code log-kicks: true} but always sent to Discord. It
     * is already over (end = start), so it never counts as an active punishment.
     */
    private static Punishment kickRecord(JudicatorPlayer player, String punisher, String reason) {
        final LocalDateTime now = LocalDateTime.now();
        // reason is VARCHAR(255): a longer one would make the database reject the row.
        final Punishment kick = Punishment.create(player.getUniqueId(),
                reason.length() <= REASON_MAX_LENGTH ? reason : reason.substring(0, REASON_MAX_LENGTH),
                punisher, player.getName(), null,
                now, now, new ArrayList<>(), PunishType.KICK);
        kick.setPermanent(false);
        return kick;
    }

}
