package com.openmc.judicator.punish.commands;

import com.openmc.judicator.commons.command.CommandArgs;

import java.util.Optional;

/**
 * The flags of the punishment commands, in any order: {@code -s} (silent), {@code -scope=<server or group>} and, where
 * allowed, {@code -o} (online-only time of a temporary mute).
 */
final class PunishmentFlags {

    static final String SILENT = "-s";
    static final String ONLINE_ONLY = "-o";
    static final String SCOPE = "-scope=";

    private final boolean onlineOnlyAllowed;
    boolean silent;
    boolean onlineOnly;
    /** what followed {@code -scope=}; null when not given */
    String scope;

    PunishmentFlags(boolean onlineOnlyAllowed) {
        this.onlineOnlyAllowed = onlineOnlyAllowed;
    }

    /**
     * Reads the flags at the cursor, stopping at the first word that is not one.
     */
    void read(CommandArgs args) {
        while (true) {
            if (args.consumeFlag(SILENT)) {
                silent = true;
            } else if (onlineOnlyAllowed && args.consumeFlag(ONLINE_ONLY)) {
                onlineOnly = true;
            } else {
                final Optional<String> value = args.consumeFlagValue(SCOPE);
                if (value.isEmpty()) return;
                scope = value.get();
            }
        }
    }

    static boolean isFlag(String word) {
        return word.equalsIgnoreCase(SILENT) || word.equalsIgnoreCase(ONLINE_ONLY) || word.startsWith(SCOPE);
    }
}
