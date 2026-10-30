package com.elless.judicator.importer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Player names as a source plugin last saw them. Judicator also enforces punishments by name, so a punished player
 * is stored under a name only when that player is still the latest holder of it: a banned player who changed name
 * cannot join, so the source keeps the old name, and another player may have taken it since.
 */
final class NameHistory {

    private record Seen(String name, UUID uuid, long when) {
    }

    private final Map<UUID, Seen> byUuid = new HashMap<>();
    private final Map<String, Seen> byName = new HashMap<>();

    void add(UUID uuid, String name, long when) {
        if (uuid == null || name == null || name.isBlank()) return;
        final Seen seen = new Seen(name, uuid, when);
        byUuid.merge(uuid, seen, (old, now) -> now.when() >= old.when() ? now : old);
        byName.merge(name.toLowerCase(Locale.ROOT), seen, (old, now) -> now.when() >= old.when() ? now : old);
    }

    /**
     * @return the latest name the source knows for this player, for display (staff names)
     */
    String latestName(UUID uuid) {
        final Seen seen = uuid == null ? null : byUuid.get(uuid);
        return seen == null ? null : seen.name();
    }

    /**
     * @return the name to store for a punished player: their latest name if nobody took it after them, else a
     * placeholder no player can have (the punishment is then enforced by UUID); null if the player is unknown
     */
    String victimName(UUID uuid) {
        final Seen seen = uuid == null ? null : byUuid.get(uuid);
        if (seen == null) return null;
        final Seen holder = byName.get(seen.name().toLowerCase(Locale.ROOT));
        if (holder != null && holder.uuid().equals(uuid)) return seen.name();
        return "#" + uuid.toString().replace("-", "").substring(0, 19);
    }
}
