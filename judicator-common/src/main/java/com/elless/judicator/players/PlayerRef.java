package com.elless.judicator.players;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A player as the data finds them: by the account (UUID) when the server trusts the UUIDs players join with, by name
 * otherwise. Records stored without a UUID (a name nobody had seen) belong to whoever has that name, until that account
 * joins and the records get its UUID.
 *
 * @param uuid             the UUID to store on new records (also the offline-mode one); null when unknown
 * @param name             the name, as typed or as last seen
 * @param identifiedByUuid true when {@code uuid} identifies the account (online mode, or {@code trust-uuids}); false
 *                         when the name does (a real offline-mode server, where older records may hold another UUID)
 */
public record PlayerRef(UUID uuid, String name, boolean identifiedByUuid) {

    public PlayerRef {
        Objects.requireNonNull(name, "name");
        if (identifiedByUuid && uuid == null) throw new IllegalArgumentException("an account needs its UUID");
    }

    /**
     * The account with this UUID (online mode or {@code trust-uuids}).
     */
    public static PlayerRef account(UUID uuid, String name) {
        return new PlayerRef(uuid, name, true);
    }

    /**
     * Found by name: offline mode, or a name whose account is unknown.
     *
     * @param uuid what new records store; null when unknown
     */
    public static PlayerRef byName(String name, UUID uuid) {
        return new PlayerRef(uuid, name, false);
    }

    /**
     * The UUID an offline-mode server gives a name.
     */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public String lowerName() {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Whether a record (its UUID, null when it has none, and the name it was stored with) belongs to this player.
     * The SQL of the DAOs ({@link com.elless.judicator.commons.db.IdentitySql}) gives the same answer.
     */
    public boolean matches(UUID recordUuid, String recordName) {
        if (identifiedByUuid && recordUuid != null) return recordUuid.equals(uuid);
        return recordName != null && recordName.equalsIgnoreCase(name);
    }

    public PlayerRef withName(String newName) {
        return new PlayerRef(uuid, newName, identifiedByUuid);
    }

}
