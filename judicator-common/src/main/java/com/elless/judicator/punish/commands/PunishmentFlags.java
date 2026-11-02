package com.elless.judicator.punish.commands;

import com.elless.judicator.commons.command.CommandArgs;

import java.util.Optional;

/**
 * The flags of the punishment commands, in any order: {@code -s} (silent), {@code -scope=<server or group>}, where
 * allowed {@code -o} (online-only time of a temporary mute or jail) and, for jails, {@code -jail=<name>}.
 */
final class PunishmentFlags {

    static final String SILENT = "-s";
    static final String ONLINE_ONLY = "-o";
    static final String SCOPE = "-scope=";
    static final String JAIL = "-jail=";

    private final boolean onlineOnlyAllowed;
    boolean silent;
    boolean onlineOnly;
    /** what followed {@code -scope=}; null when not given */
    String scope;
    /** what followed {@code -jail=}; null when not given */
    String jail;

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
                if (value.isPresent()) {
                    scope = value.get();
                    continue;
                }
                final Optional<String> jailName = args.consumeFlagValue(JAIL);
                if (jailName.isEmpty()) return;
                jail = jailName.get();
            }
        }
    }

    static boolean isFlag(String word) {
        return word.equalsIgnoreCase(SILENT) || word.equalsIgnoreCase(ONLINE_ONLY) || word.startsWith(SCOPE) || word.startsWith(JAIL);
    }
}
