package com.openmc.judicator.punish;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.punish.data.cache.PunishCache;
import com.openmc.judicator.punish.data.repository.PunishmentRepository;
import com.openmc.judicator.punish.types.PunishType;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PunishService {

    private final Judicator judicator;
    private final PunishmentRepository repository;
    private final PunishCache cache;
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
        this.repository.initialize();
    }

    public Optional<Punishment> searchOnPreLogin(String username, String ipAddress) {
        final List<Punishment> punishments = repository.findAllActiveByUsernameOrIpAndTypes(username.toLowerCase(), ipAddress, PunishType.values());
        if (punishments.isEmpty()) return Optional.empty();

        final Optional<Punishment> ban = punishments.stream().filter(Punishment::isBanType).findFirst();
        if (ban.isPresent()) return ban;

        punishments.stream().filter(Punishment::isMuteType).min(heavyFirst).ifPresent(punishment -> {
            final String key = punishment.getIpAddress().orElseGet(punishment::getNickname);
            cache.putMutePunishment(key, punishment);
        });
        return Optional.empty();
    }

    public Optional<Punishment> searchOnLogin(UUID uuid, String username, String address) {
        final List<Punishment> punishments = repository.findAllActiveByUUIDAndTypes(uuid, PunishType.values());
        if (punishments.isEmpty()) return Optional.empty();

        final Optional<Punishment> ban = punishments.stream().filter(Punishment::isBanType).findFirst();
        if (ban.isPresent()) return ban;

        if (cache.getPunishment(username).or(() -> cache.getPunishment(address)).isPresent()) return Optional.empty();

        punishments.stream().filter(Punishment::isMuteType).min(heavyFirst)
                .ifPresent(punishment -> cache.putMutePunishment(username, punishment));
        return Optional.empty();
    }

    public void cleanupMutePunishmentCache(String username, String address) {
        cache.getPunishment(username)
                .or(() -> cache.getPunishment(address))
                .ifPresent(punishment -> {
                    if (punishment.getIpAddress().isEmpty()) {
                        cache.removeMutePunishment(username);
                        return;
                    }

                    final boolean noneMatch = judicator.getPlatform().getOnlinePlayers().stream()
                            .noneMatch(p -> {
                                boolean isSameUser = p.getName().equals(username);
                                boolean hasSameAddress = p.getHostAddress().equals(address);
                                return !isSameUser && hasSameAddress;
                            });

                    if (noneMatch) cache.removeMutePunishment(punishment.getIpAddress().get());
                });
    }

    public void revoke(Long id, String reason, Runnable onSuccess, Runnable onFailure) {
        judicator.getPlatform().runAsync(() -> {
            if (repository.revoke(id, reason)) onSuccess.run();
            else onFailure.run();
        });
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

    public boolean isOnlinePlayerAffected(JudicatorPlayer player, Punishment punishment) {
        return punishment.getIpAddress()
                .map(ip -> player.getHostAddress().equals(ip))
                .orElseGet(() -> player.getName().equalsIgnoreCase(punishment.getNickname()));
    }

}
