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
                    // The name entry goes in every case (an IP mute is restored by address at the next login).
                    cache.removeMutePunishment(username);
                    if (punishment.getIpAddress().isEmpty()) return;

                    final boolean noneMatch = judicator.getPlatform().getOnlinePlayers().stream()
                            .noneMatch(p -> {
                                boolean isSameUser = p.getName().equals(username);
                                boolean hasSameAddress = p.getHostAddress().equals(address);
                                return !isSameUser && hasSameAddress;
                            });

                    if (noneMatch) cache.removeMutePunishment(punishment.getIpAddress().get());
                });
    }

    /**
     * revoked_reason is VARCHAR(100): a longer reason would make PostgreSQL/MySQL reject the update.
     */
    private static String revokeReason(String reason) {
        if (reason == null) return null;
        return reason.length() <= 100 ? reason : reason.substring(0, 100);
    }

    public void revoke(Long id, String reason, Runnable onSuccess, Runnable onFailure) {
        judicator.getPlatform().runAsync(() -> {
            if (repository.revoke(id, revokeReason(reason))) onSuccess.run();
            else onFailure.run();
        });
    }

    public String statusOf(String username) {
        final List<Punishment> active = repository.findAllActiveByUsernameAndTypes(username,
                PunishType.BAN, PunishType.TEMPBAN, PunishType.MUTE, PunishType.TEMPMUTE);
        if (active.stream().anyMatch(Punishment::isBanType)) return "banned";
        if (active.stream().anyMatch(Punishment::isMuteType)) return "muted";
        return "clean";
    }

    public boolean revokeNow(Long id, String reason) {
        return repository.revoke(id, revokeReason(reason));
    }

    public Optional<Punishment> findById(Long id) {
        return repository.findById(id);
    }

    /**
     * @param since null for every punishment of the staff member
     */
    public int countByPunisher(String punisher, LocalDateTime since) {
        return repository.countByPunisher(punisher, since);
    }

    public List<Punishment> findByPunisher(String punisher, LocalDateTime since, int limit, int offset) {
        return repository.findByPunisher(punisher, since, limit, offset);
    }

    public int countActive(PunishType... types) {
        return repository.countActiveByTypes(types);
    }

    public List<Punishment> findActive(int limit, int offset, PunishType... types) {
        return repository.findActiveByTypes(limit, offset, types);
    }

    public List<PunishmentRepository.StaffCount> countByStaffSince(LocalDateTime since) {
        return repository.countByStaffSince(since);
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
