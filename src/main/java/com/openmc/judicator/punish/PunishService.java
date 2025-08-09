package com.openmc.judicator.punish;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.data.cache.PunishCache;
import com.openmc.judicator.punish.data.repository.PunishmentRepository;
import com.openmc.judicator.punish.types.PunishType;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class PunishService {

    private final Judicator judicator;
    private final PunishmentRepository repository;
    private final PunishCache cache;
    private final ProxyServer server;
    private final Comparator<Punishment> heavyFirst = (o1, o2) -> {
        final Optional<LocalDateTime> f1 = o1.getFinishAt();
        final Optional<LocalDateTime> f2 = o2.getFinishAt();
        if (f1.isEmpty() && f2.isPresent()) return -1;
        if (f1.isPresent() && f2.isEmpty()) return 1;
        return f1.map(localDateTime -> f2.get().compareTo(localDateTime)).orElse(0);
    };

    public PunishService(Judicator judicator, PunishmentRepository repository) {
        this.judicator = judicator;
        this.repository = repository;
        this.cache = judicator.getPunishCache();
        this.server = judicator.getServer();
        this.repository.initialize();
    }

    public void searchOnPreLogin(PreLoginEvent event, Consumer<Punishment> banCallback, Consumer<Punishment> muteCallback) {
        final String username = event.getUsername().toLowerCase();
        final String ipAddress = event.getConnection().getRemoteAddress().getAddress().getHostAddress();

        final List<Punishment> punishments = repository.findAllActiveByUsernameOrIpAndTypes(username, ipAddress, PunishType.values());
        if (punishments.isEmpty()) return;

        final Optional<Punishment> banPunishmentQuery = punishments.stream().filter(Punishment::isBanType).findFirst();
        if (banCallback != null) banPunishmentQuery.ifPresent(banCallback);

        if (!event.getResult().isAllowed()) return;

        final Optional<Punishment> mutePunishmentQuery = punishments.stream().filter(Punishment::isMuteType).min(heavyFirst);
        mutePunishmentQuery.ifPresent(punishment -> {
            final String key = punishment.getIpAddress().orElseGet(punishment::getNickname);
            cache.putMutePunishment(key, punishment);
            if (muteCallback != null) mutePunishmentQuery.ifPresent(muteCallback);
        });
    }

    public void searchOnLogin(LoginEvent event, Consumer<Punishment> banCallback, Consumer<Punishment> muteCallback) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        final List<Punishment> punishments = repository.findAllActiveByUUIDAndTypes(uuid, PunishType.values());
        if (punishments.isEmpty()) return;

        final Optional<Punishment> banPunishmentQuery = punishments.stream().filter(Punishment::isBanType).findFirst();
        if (banCallback != null) banPunishmentQuery.ifPresent(banCallback);

        if (!event.getResult().isAllowed()) return;

        final String address = player.getRemoteAddress().getAddress().getHostAddress();
        final String username = player.getUsername();

        if (cache.getPunishment(username).or(() -> cache.getPunishment(address)).isPresent()) return;

        final Optional<Punishment> mutePunishmentQuery = punishments.stream().filter(Punishment::isMuteType).min(heavyFirst);
        mutePunishmentQuery.ifPresent(punishment -> {
            cache.putMutePunishment(username, punishment);
            if (muteCallback != null) mutePunishmentQuery.ifPresent(muteCallback);
        });
    }

    public void cleanupMutePunishmentCache(DisconnectEvent event) {
        final String username = event.getPlayer().getUsername();
        final String address = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();

        cache.getPunishment(username)
                .or(() -> cache.getPunishment(address))
                .ifPresent(punishment -> {
                    if (punishment.getIpAddress().isEmpty()) {
                        cache.removeMutePunishment(username);
                        return;
                    }

                    final boolean noneMatch = server.getAllPlayers().stream()
                            .noneMatch(p -> {
                                boolean isSameUser = p.getUsername().equals(username);
                                boolean hasSameAddress = p.getRemoteAddress().getAddress().getHostAddress().equals(address);
                                return !isSameUser && hasSameAddress;
                            });

                    if (noneMatch) cache.removeMutePunishment(punishment.getIpAddress().get());
                });
    }

    public void revoke(Long id, String reason, Runnable onSuccess, Runnable onFailure) {
        server.getScheduler().buildTask(judicator, () -> {
            if (repository.revoke(id, reason)) onSuccess.run();
            else onFailure.run();
        }).schedule();
    }

    public Optional<Punishment> findById(Long id) {
        return repository.findById(id);
    }

    public List<Punishment> findAllByUsername(String username) {
        return repository.findAllByUsername(username);
    }

    public List<Punishment> findAllActiveByUsernameAndTypes(String username, PunishType... types) {
        return repository.findAllActiveByUsernameAndTypes(username, types);
    }

    public Punishment save(Punishment punishment) {
        return repository.save(punishment);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

}
