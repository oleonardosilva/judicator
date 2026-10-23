package com.openmc.judicator.punish.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.Platform;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * What a punishment does to the players online on this instance: for those applied here (after the handlers store
 * them) and for those another instance sharing the database applied ({@code sync}). The Discord webhook is not here:
 * only the instance that applied the punishment sends it.
 * <p>
 * A Spigot/Paper backend in {@code proxy-mode} leaves everything players see to the proxy, which does it for every
 * punishment wherever it was applied; the backend only keeps the mute cache its chat check reads.
 */
public class PunishmentEffects {

    private final Judicator judicator;

    public PunishmentEffects(Judicator judicator) {
        this.judicator = judicator;
    }

    public void punished(Punishment punishment) {
        if (punishment.isBanType()) banned(punishment);
        else if (punishment.isMuteType()) muted(punishment);
    }

    private void banned(Punishment punishment) {
        if (judicator.isProxyMode()) return;
        judicator.getStaffNotifier().notifyPunishment(punishment);
        if (!punishment.isSilent()) announce(punishment, "ban");
        final Platform platform = judicator.getPlatform();
        platform.runSync(() -> affectedPlayers(platform, punishment).forEach(player -> player.disconnect(
                PunishUtils.getMessageList(judicator.messagesFor(player), punishment, "runners", "ban-kick"))));
    }

    private void muted(Punishment punishment) {
        final boolean proxyMode = judicator.isProxyMode();
        final Platform platform = judicator.getPlatform();
        platform.runSync(() -> affectedPlayers(platform, punishment).forEach(player -> {
            // a soft-muted player must not notice it
            if (!proxyMode && !punishment.isSoft()) {
                player.sendMessage(PunishUtils.getMessageList(judicator.messagesFor(player), punishment, "runners", "mute-alert"));
            }
            judicator.getPunishCache().putMutePunishment(player.getName().toLowerCase(), punishment);
        }));
        if (proxyMode) return;
        judicator.getStaffNotifier().notifyPunishment(punishment);
        if (!punishment.isSilent() && !punishment.isSoft()) announce(punishment, "mute");
    }

    private void announce(Punishment punishment, String key) {
        if (!judicator.getConfig().node("announce").getBoolean(true)) return;
        final Platform platform = judicator.getPlatform();
        platform.getPlayer(punishment.getNickname()).ifPresent(player -> platform.announceToServerOf(player,
                recipient -> PunishUtils.getMessageList(judicator.messagesFor(recipient), punishment, "announcements", key)));
    }

    /**
     * Tells each player online here that their punishment was lifted, once per player, unless all of theirs were soft
     * mutes (they never knew about them).
     */
    public void pardoned(Collection<Punishment> revoked) {
        if (judicator.isProxyMode()) return;
        final Map<String, Boolean> noticed = new LinkedHashMap<>();
        revoked.forEach(punishment -> noticed.merge(punishment.getNickname().toLowerCase(Locale.ROOT), !punishment.isSoft(), Boolean::logicalOr));
        noticed.forEach((nickname, tell) -> {
            if (!tell) return;
            judicator.getPlatform().getPlayer(nickname).ifPresent(player ->
                    player.sendMessage(PunishUtils.getMessage(judicator.messagesFor(player), "success", "got-pardon")));
        });
    }

    static Stream<? extends JudicatorPlayer> affectedPlayers(Platform platform, Punishment punishment) {
        return punishment.getIpAddress()
                .<Stream<? extends JudicatorPlayer>>map(ip -> platform.getOnlinePlayers().stream()
                        .filter(player -> player.getHostAddress().equals(ip)))
                .orElseGet(() -> platform.getPlayer(punishment.getNickname()).stream());
    }

}
