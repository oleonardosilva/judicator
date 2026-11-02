package com.elless.judicator.api.jail;

import java.util.Objects;
import java.util.Optional;

/**
 * A jail made with {@code /jails}, as it was when it was read. Where its spawn, area and escape routes are is kept to
 * the server (escape routes are secret).
 *
 * @param name         the jail's name, as {@code -jail=} takes it
 * @param server       the server of the network it is on (as the proxy calls it); empty for a jail of a server
 *                     without {@code scopes.server-name}
 * @param shared       false: one prisoner at a time
 * @param capacity     the most prisoners of a shared jail; 0 for no limit
 * @param prisoners    how many prisoners it holds now (fugitives not counted)
 * @param escapeRoutes how many secret ways out it has
 */
public record JailView(String name, Optional<String> server, boolean shared, int capacity, int prisoners, int escapeRoutes) {

    public JailView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(server, "server");
    }

    /**
     * @return how many prisoners it holds at most ({@link Integer#MAX_VALUE} without a limit)
     */
    public int room() {
        if (!shared) return 1;
        return capacity == 0 ? Integer.MAX_VALUE : capacity;
    }

    /**
     * @return whether it can take another prisoner now
     */
    public boolean hasRoom() {
        return prisoners < room();
    }
}
