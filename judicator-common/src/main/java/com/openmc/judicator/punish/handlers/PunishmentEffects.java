package com.openmc.judicator.punish.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.Platform;
import com.openmc.judicator.punish.PunishService;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
            judicator.getPunishCache().putMutePunishment(PunishService.cacheKey(player), punishment);
        }));
        if (proxyMode) return;
        judicator.getStaffNotifier().notifyPunishment(punishment);
        if (!punishment.isSilent() && !punishment.isSoft()) announce(punishment, "mute");
    }

    private void announce(Punishment punishment, String key) {
        if (!PunishUtils.announces(judicator.getConfig(), key)) return;
        final Platform platform = judicator.getPlatform();
        target(platform, punishment).ifPresent(player -> platform.announceToServerOf(player,
                recipient -> PunishUtils.getMessageList(judicator.messagesFor(recipient), punishment, "announcements", key)));
    }

    /**
     * Tells each player online here that their punishment was lifted, once per player, unless all of theirs were soft
     * mutes (they never knew about them).
     */
    public void pardoned(Collection<Punishment> revoked) {
        if (judicator.isProxyMode()) return;
        final Map<UUID, Boolean> noticed = new LinkedHashMap<>();
        final Map<UUID, JudicatorPlayer> players = new LinkedHashMap<>();
        revoked.forEach(punishment -> target(judicator.getPlatform(), punishment).ifPresent(player -> {
            players.putIfAbsent(player.getUniqueId(), player);
            noticed.merge(player.getUniqueId(), !punishment.isSoft(), Boolean::logicalOr);
        }));
        noticed.forEach((uuid, tell) -> {
            if (!tell) return;
            final JudicatorPlayer player = players.get(uuid);
            player.sendMessage(PunishUtils.getMessage(judicator.messagesFor(player), "success", "got-pardon"));
        });
    }

    Stream<? extends JudicatorPlayer> affectedPlayers(Platform platform, Punishment punishment) {
        return punishment.getIpAddress()
                .<Stream<? extends JudicatorPlayer>>map(ip -> platform.getOnlinePlayers().stream()
                        .filter(player -> player.getHostAddress().equals(ip)))
                .orElseGet(() -> target(platform, punishment).stream());
    }

    /**
     * The player online here the punishment was applied to: by UUID, or by name for one stored without a UUID (and in
     * offline mode, where the name is the identity).
     */
    private Optional<JudicatorPlayer> target(Platform platform, Punishment punishment) {
        final Optional<UUID> uuid = punishment.getPlayerUUID().filter(ignored -> judicator.trustsUuids());
        if (uuid.isPresent()) return platform.getPlayer(uuid.get());
        return platform.getPlayer(punishment.getNickname());
    }

}
