package com.openmc.judicator.players;

import java.util.function.Function;
import java.util.Map;
import java.util.Locale;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.players.repository.PlayerRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Who a name or an online player is, for the data: the account (UUID) when the server trusts the UUIDs players join
 * with ({@link Judicator#trustsUuids()}), the name otherwise. Keeps the {@code players} table (the name each account last
 * joined with), which is what finds the account of a name typed by the staff.
 */
public class PlayerDirectory {

    private final Judicator judicator;
    private final PlayerRepository repository;

    public PlayerDirectory(Judicator judicator, PlayerRepository repository) {
        this.judicator = judicator;
        this.repository = repository;
        repository.initialize();
    }

    /**
     * First start of 2.0: the accounts the stored records already know go into {@code players}.
     */
    public void seed() {
        if (!judicator.trustsUuids()) return;
        final int added = repository.seedIfEmpty();
        if (added > 0) judicator.getLogger().info("Players table filled with {} accounts found in the punishments, warns, notes and staff levels.", added);
    }

    public PlayerRef refOf(JudicatorPlayer player) {
        return refOf(player.getUniqueId(), player.getName());
    }

    public PlayerRef refOf(UUID uuid, String name) {
        if (judicator.trustsUuids() && uuid != null) return PlayerRef.account(uuid, name);
        return PlayerRef.byName(name, uuid != null || judicator.trustsUuids() ? uuid : PlayerRef.offlineUuid(name));
    }

    /**
     * The player with this UUID (a plugin asking through the API): with the name it is online with or last joined with,
     * so records stored under that name without a UUID are found too. An account never seen matches only the records
     * stored with its UUID.
     */
    public PlayerRef refOf(UUID uuid) {
        final Optional<String> name = judicator.getPlatform().getPlayer(uuid).map(JudicatorPlayer::getName)
                .or(() -> repository.findByUuid(uuid).map(KnownPlayer::name));
        return name.map(known -> refOf(uuid, known)).orElseGet(() -> PlayerRef.account(uuid, ""));
    }

    /**
     * The player a name typed by the staff (or a plugin) means: the one online with it, else the account that joined
     * last with that name (with the name as it joined, whatever letter case was typed); else, in offline mode, the
     * name itself and in online mode the account the Mojang API gives it.
     */
    public PlayerRef resolve(String name) {
        return resolve(name, true);
    }

    /**
     * {@link #resolve} without asking the Mojang API: for checks that must answer at once (levels, notices).
     */
    public PlayerRef resolveKnown(String name) {
        return resolve(name, false);
    }

    private PlayerRef resolve(String name, boolean askMojang) {
        final Optional<JudicatorPlayer> online = judicator.getPlatform().getPlayer(name);
        if (online.isPresent()) return refOf(online.get());
        final Optional<KnownPlayer> known = repository.findLatestByName(name);
        if (known.isPresent()) return refOf(known.get().uuid(), known.get().name());
        if (!judicator.trustsUuids()) return PlayerRef.byName(name, PlayerRef.offlineUuid(name));
        final UUID uuid = askMojang ? judicator.getUuidManager().getUUID(name) : judicator.getUuidManager().getKnownUUID(name);
        return uuid == null ? PlayerRef.byName(name, null) : PlayerRef.account(uuid, name);
    }

    /**
     * The UUID of each name of 1.x, for records copied from it: the account that used it last, the offline-mode one in
     * offline mode; null when unknown. The known names are read once, so a large copy makes no query per name.
     */
    public Function<String, UUID> legacyNames() {
        if (!judicator.trustsUuids()) return PlayerRef::offlineUuid;
        final Map<String, UUID> known = repository.latestUuidsByName();
        return name -> known.get(name.toLowerCase(Locale.ROOT));
    }

    public Optional<KnownPlayer> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    /**
     * An authenticated player is joining (before the ban checks): the account is stored with the name it joins with,
     * and when that name is new for it, the records stored under the name without a UUID become the account's.
     * In {@code proxy-mode} the proxy does it for the network.
     */
    public void joined(UUID uuid, String name) {
        judicator.getUuidManager().remember(name, uuid);
        if (judicator.isProxyMode()) return;
        final boolean newName = repository.record(uuid, name, LocalDateTime.now());
        if (!newName || !judicator.trustsUuids()) return;
        final PlayerRef account = PlayerRef.account(uuid, name);
        final int filled = judicator.getPunishService().fillUuid(account) + judicator.getWarnService().fillUuid(account)
                + judicator.getNoteService().fillUuid(account) + judicator.getAddressService().fillUuid(account);
        if (filled > 0) judicator.getLogger().info("{} record(s) stored for the name {} now follow its account {}.", filled, name, uuid);
    }

    /**
     * {@code /judicator clear}: the account is no longer known.
     */
    public int forget(PlayerRef player) {
        return player.uuid() == null ? 0 : repository.deleteByUuid(player.uuid());
    }

}
