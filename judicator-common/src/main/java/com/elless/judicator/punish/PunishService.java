package com.elless.judicator.punish;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.ListQuery;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.punish.data.cache.PunishCache;
import com.elless.judicator.punish.data.repository.PunishmentRepository;
import com.elless.judicator.punish.scopes.ScopeService;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.sync.SyncKind;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PunishService {

    private final Judicator judicator;
    private final PunishmentRepository repository;
    private final PunishCache cache;

    /**
     * The punishment that weighs most first: a normal mute before a soft one (it is the one the player must notice),
     * then the one that ends last — permanent first, a paused online-only mute by the time it has left (it is not
     * permanent even though it has no end).
     */
    public static Comparator<Punishment> heavyFirst() {
        final LocalDateTime now = LocalDateTime.now();
        return Comparator.comparing(Punishment::isSoft)
                .thenComparing((Punishment punishment) -> effectiveEnd(punishment, now), Comparator.reverseOrder());
    }

    private static LocalDateTime effectiveEnd(Punishment punishment, LocalDateTime now) {
        if (punishment.isPaused()) return now.plus(Duration.ofMillis(punishment.getPausedRemaining()));
        return punishment.getFinishAt().orElse(LocalDateTime.MAX);
    }

    public PunishService(Judicator judicator, PunishmentRepository repository) {
        this.judicator = judicator;
        this.repository = repository;
        this.cache = judicator.getPunishCache();
        this.repository.initialize();
    }

    /**
     * Before authentication (Velocity {@code PreLoginEvent}): a ban on the address and, when the name is the identity
     * (offline mode), a ban on the name. With trusted UUIDs the name proves nothing yet — it may be the old name of a
     * banned account now used by someone else — so the account is checked by {@link #searchOnLogin}.
     */
    public Optional<Punishment> searchOnPreLogin(String username, String ipAddress) {
        final List<Punishment> bans = new ArrayList<>();
        // An IP punishment stored on an exempt address (older, or imported) must not reach everyone behind it.
        if (!judicator.getAddressPolicy().isExempt(ipAddress)) {
            bans.addAll(repository.findAllActiveOfAddress(ipAddress, PunishType.BAN, PunishType.TEMPBAN));
        }
        if (!judicator.trustsUuids()) {
            bans.addAll(repository.findAllActiveOf(PlayerRef.byName(username, null), PunishType.BAN, PunishType.TEMPBAN));
        }
        return bans.stream().filter(judicator.getScopeService()::refusesLogin).findFirst();
    }

    /**
     * After authentication: a ban of the account that refuses the login ({@link ScopeService#refusesLogin}) refuses it;
     * otherwise its mutes in force (its own and the IP mutes of the address, for any scope) are cached for the chat
     * checks, which pick the one that applies where the player talks.
     */
    public Optional<Punishment> searchOnLogin(PlayerRef player, String address) {
        final List<Punishment> punishments = repository.findAllActiveOf(player, PunishType.values());
        final Optional<Punishment> ban = punishments.stream().filter(Punishment::isBanType)
                .filter(judicator.getScopeService()::refusesLogin).findFirst();
        if (ban.isPresent()) return ban;

        punishments.stream().filter(Punishment::isMuteType).forEach(punishment -> cache.putMutePunishment(cacheKey(player), punishment));
        if (!judicator.getAddressPolicy().isExempt(address)) {
            repository.findAllActiveOfAddress(address, PunishType.MUTE, PunishType.TEMPMUTE)
                    .forEach(punishment -> cache.putMutePunishment(address, punishment));
        }
        return Optional.empty();
    }

    /**
     * The key of a player's own mute in the {@link PunishCache} (IP mutes are cached by address).
     */
    public static String cacheKey(PlayerRef player) {
        return player.uuid() != null ? player.uuid().toString() : player.lowerName();
    }

    public static String cacheKey(JudicatorPlayer player) {
        return player.getUniqueId().toString();
    }

    /**
     * The player left: their own cached mute goes, and the IP mute of their address when nobody else online uses it
     * (it is restored by address at the next login).
     */
    public void cleanupMutePunishmentCache(UUID uuid, String address) {
        cache.removeMutePunishment(uuid.toString());
        final boolean addressStillUsed = judicator.getPlatform().getOnlinePlayers().stream()
                .anyMatch(other -> !other.getUniqueId().equals(uuid) && other.getHostAddress().equals(address));
        if (!addressStillUsed) cache.removeMutePunishment(address);
    }

    /**
     * The revocation reason as it is stored: revoked_reason is VARCHAR(100), and a longer reason would make
     * PostgreSQL/MySQL reject the update.
     */
    public static String storedRevokeReason(String reason) {
        if (reason == null) return null;
        return reason.length() <= 100 ? reason : reason.substring(0, 100);
    }

    public String statusOf(PlayerRef player) {
        final List<Punishment> active = repository.findAllActiveOf(player,
                PunishType.BAN, PunishType.TEMPBAN, PunishType.MUTE, PunishType.TEMPMUTE);
        if (active.stream().anyMatch(Punishment::isBanType)) return "banned";
        if (active.stream().anyMatch(Punishment::isMuteType)) return "muted";
        return "clean";
    }

    public boolean revokeNow(Long id, String reason) {
        if (!repository.revoke(id, storedRevokeReason(reason))) return false;
        judicator.getSyncService().publish(SyncKind.REVOKED, id);
        return true;
    }

    public Optional<Punishment> findById(Long id) {
        return repository.findById(id);
    }

    /**
     * @param since null for every punishment of the staff member
     */
    public int countByPunisher(PlayerRef staff, LocalDateTime since) {
        return repository.countByPunisher(staff, since);
    }

    public List<Punishment> findByPunisher(PlayerRef staff, LocalDateTime since, int limit, int offset) {
        return repository.findByPunisher(staff, since, limit, offset);
    }

    public int countActive(PunishType... types) {
        return repository.countActiveByTypes(types);
    }

    public List<Punishment> findActive(int limit, int offset, PunishType... types) {
        return repository.findActiveByTypes(limit, offset, types);
    }

    public int countActive(ListQuery query, PunishType... types) {
        return repository.countActive(query, types);
    }

    public List<Punishment> findActive(ListQuery query, int limit, int offset, PunishType... types) {
        return repository.findActive(query, limit, offset, types);
    }

    public List<PunishmentRepository.StaffCount> countByStaffSince(LocalDateTime since) {
        return repository.countByStaffSince(since);
    }

    /**
     * Every punishment of a player, oldest first.
     */
    public List<Punishment> findAllOf(PlayerRef player) {
        return repository.findAllOf(player);
    }

    public List<Punishment> findAllActiveOf(PlayerRef player, PunishType... types) {
        return repository.findAllActiveOf(player, types);
    }

    public int fillUuid(PlayerRef account) {
        return repository.fillUuid(account);
    }

    /**
     * Bans in force on any of these accounts that refuse a login here ({@link ScopeService#refusesLogin}): a ban for
     * some servers of the network only keeps the player out of those.
     */
    public List<Punishment> findLoginBans(Collection<PlayerRef> players) {
        return repository.findAllActiveOfAny(players, PunishType.BAN, PunishType.TEMPBAN).stream()
                .filter(judicator.getScopeService()::refusesLogin).toList();
    }

    /**
     * The scopes of the punishments in force, each once.
     */
    public Set<String> findActiveScopes() {
        return repository.findActiveScopes();
    }

    /**
     * The bans in force that keep a player out of some servers only: their own and those on their address.
     */
    public List<Punishment> findScopedBans(PlayerRef player, String address) {
        final List<Punishment> bans = judicator.getAddressPolicy().isExempt(address)
                ? repository.findAllActiveOf(player, PunishType.BAN, PunishType.TEMPBAN)
                : repository.findAllActiveOfPlayerOrAddress(player, address, PunishType.BAN, PunishType.TEMPBAN);
        return bans.stream().filter(ban -> ban.getScope().isPresent()).toList();
    }

    /**
     * Stores a punishment; a new one is announced to the other servers ({@code sync}), which apply its effects to
     * their players.
     */
    public Punishment save(Punishment punishment) {
        final boolean created = punishment.getId() == null;
        final Punishment saved = repository.save(punishment);
        if (created) judicator.getSyncService().publish(SyncKind.PUNISHED, saved.getId());
        return saved;
    }

    /**
     * Stores a new end (or what is left of a paused online-only mute) and makes a cached mute follow it right away.
     *
     * @return false when the punishment was revoked meanwhile
     */
    public boolean changeEnd(Punishment punishment, LocalDateTime finishAt, Long pausedRemaining) {
        if (!repository.changeEnd(punishment.getId(), finishAt, pausedRemaining)) return false;
        punishment.setFinishAt(finishAt);
        punishment.setPausedRemaining(pausedRemaining);
        cache.refreshMute(punishment);
        judicator.getSyncService().publish(SyncKind.CHANGED, punishment.getId());
        return true;
    }

    /**
     * @return false when the punishment was revoked meanwhile
     */
    public boolean changeReason(Punishment punishment, String reason) {
        if (!repository.changeReason(punishment.getId(), reason)) return false;
        punishment.setReason(reason);
        cache.refreshMute(punishment);
        judicator.getSyncService().publish(SyncKind.CHANGED, punishment.getId());
        return true;
    }

    /**
     * The player left (or the server is stopping with them online): their online-only mutes stop running and keep
     * what is left until they join again.
     */
    public void pauseOnlineOnlyMutes(PlayerRef player) {
        final LocalDateTime now = LocalDateTime.now();
        for (Punishment mute : repository.findAllActiveOf(player, PunishType.TEMPMUTE)) {
            if (!mute.isOnlineOnly() || mute.isPaused() || mute.getFinishAt().isEmpty()) continue;
            final long left = Duration.between(now, mute.getFinishAt().get()).toMillis();
            if (left > 0) changeEnd(mute, null, left);
        }
    }

    /**
     * The player joined (in the game, after authentication: a name typed at the login screen, or a login refused by a
     * whitelist or a full server, must not make the mute run): their paused online-only mutes run again from now.
     */
    public void resumeOnlineOnlyMutes(PlayerRef player) {
        final LocalDateTime now = LocalDateTime.now();
        for (Punishment mute : repository.findAllActiveOf(player, PunishType.TEMPMUTE)) {
            if (mute.isPaused()) changeEnd(mute, now.plus(Duration.ofMillis(mute.getPausedRemaining())), null);
        }
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
        judicator.getSyncService().publish(SyncKind.DELETED, id);
    }

    public boolean isOnlinePlayerAffected(JudicatorPlayer player, Punishment punishment) {
        return punishment.getIpAddress()
                .map(ip -> player.getHostAddress().equals(ip))
                .orElseGet(() -> judicator.getPlayerDirectory().refOf(player)
                        .matches(punishment.getPlayerUUID().orElse(null), punishment.getNickname()));
    }

}
