package com.openmc.judicator.importer;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.scopes.ScopeService;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.warns.Warn;
import org.spongepowered.configurate.ConfigurationNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared conversions from foreign records to Judicator's model, so every source applies the same limits (column
 * sizes, console name, revocation of removed punishments, UUID of names without one).
 */
public final class ImportContext {

    static final String CONSOLE = "Console";
    private static final int NAME_MAX = 20;
    private static final int ADDRESS_MAX = 45;
    private static final int REASON_MAX = 255;
    private static final int REVOKED_REASON_MAX = 100;

    private final Judicator judicator;
    private final ConfigurationNode config;

    ImportContext(Judicator judicator, ConfigurationNode config) {
        this.judicator = judicator;
        this.config = config;
    }

    public ConfigurationNode config(String source) {
        return config.node(source);
    }

    public Judicator judicator() {
        return judicator;
    }

    public static LocalDateTime fromEpochMillis(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
    }

    public static LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * UUID for a record that only has a name: on an offline-mode server it is the offline UUID the server itself
     * gives that name; on an online-mode server it stays unknown (no Mojang lookups during an import) and the
     * punishment is enforced by name.
     */
    public Optional<UUID> uuidForName(String name) {
        if (judicator.trustsUuids()) return Optional.empty();
        return Optional.of(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Name stored for an IP punishment that has no player. Judicator also matches punishments by name at login, so
     * this must never be a real account (an IP-only ban on a shared address would otherwise ban that account
     * everywhere): the address itself, which no player name can be, or a placeholder when it does not fit.
     */
    public static String nameForAddress(String address) {
        return address.length() <= NAME_MAX ? address : "#ip-ban";
    }

    /**
     * @return whether the address fits the {@code ip_address} column (an oversized value would roll back a batch)
     */
    public static boolean validAddress(String address) {
        return address != null && !address.isBlank() && address.length() <= ADDRESS_MAX;
    }

    /**
     * @param permanent true for a permanent ban/mute ({@code finishAt} is then ignored)
     * @param active    false when the source says the punishment was removed; it is stored revoked unless it had
     *                  already ended on its own
     */
    public Punishment punishment(UUID uuid, String nickname, String address, String reason, String operator,
                                 boolean ban, LocalDateTime startedAt, LocalDateTime finishAt, boolean permanent,
                                 boolean active, String sourceName) {
        final boolean endedOnItsOwn = !permanent && finishAt.isBefore(LocalDateTime.now());
        return punishment(uuid, nickname, address, reason, operator, ban, startedAt, finishAt, permanent,
                !active && !endedOnItsOwn, null, sourceName);
    }

    /**
     * Variant for sources that record when and why a punishment was removed.
     *
     * @param revoked       whether it was removed before it ended
     * @param revokedReason reason given when it was removed, if any
     */
    public Punishment punishment(UUID uuid, String nickname, String address, String reason, String operator,
                                 boolean ban, LocalDateTime startedAt, LocalDateTime finishAt, boolean permanent,
                                 boolean revoked, String revokedReason, String sourceName) {
        final Punishment punishment = new Punishment();
        punishment.setPlayerUUID(uuid);
        punishment.setNickname(nickname);
        punishment.setIpAddress(address);
        punishment.setReason(truncate(reason == null ? "" : reason, REASON_MAX));
        punishment.setPunisher(operatorName(operator));
        punishment.setStartedAt(startedAt);
        punishment.setPermanent(permanent);
        punishment.setFinishAt(permanent ? null : finishAt);
        punishment.setType(ban ? (permanent ? PunishType.BAN : PunishType.TEMPBAN) : (permanent ? PunishType.MUTE : PunishType.TEMPMUTE));
        punishment.setEvidences(new ArrayList<>());
        if (revoked) {
            punishment.setRevoked(true);
            final String why = revokedReason == null || revokedReason.isBlank()
                    ? "Removed in " + sourceName + " (imported)" : revokedReason + " (" + sourceName + ")";
            punishment.setRevokedReason(truncate(why, REVOKED_REASON_MAX));
        }
        return punishment;
    }

    /**
     * Gives an imported punishment the scope it had in the source plugin.
     *
     * @param name  the server or group of the source; null or empty for every server
     * @param group true for a group of servers (a LibertyBans category)
     * @return false when the scope cannot be a Judicator one (the punishment must be skipped; the reason is recorded)
     */
    public boolean scope(Punishment punishment, String name, boolean group, ImportResult result) {
        if (name == null || name.isBlank()) return true;
        final Optional<String> scope = ScopeService.imported(name, group);
        if (scope.isEmpty()) {
            result.skip("scope that is not a server or group name of up to 32 letters, digits, _, - and . (" + name + ")");
            return false;
        }
        punishment.setScope(scope.get());
        if (group && !judicator.getScopeService().hasGroup(name)) {
            result.advise("add the group '" + ScopeService.displayName(scope.get()) + "' to scopes.groups in config.yml with its servers: "
                    + "until then its punishments apply on no server");
        }
        return true;
    }

    /**
     * A warn the source marks as removed is stored as already finished, so it stays in the history without counting
     * towards the warn limits.
     */
    public Warn warn(UUID uuid, String nickname, String reason, String operator, LocalDateTime startedAt,
                     LocalDateTime finishAt, boolean permanent, boolean active) {
        final boolean finished = !active;
        return new Warn(null, uuid, truncate(reason == null ? "" : reason, REASON_MAX), operatorName(operator), nickname,
                startedAt, finished ? startedAt : (permanent ? null : finishAt), !finished && permanent);
    }

    public static boolean validName(String name) {
        return name != null && !name.isBlank() && name.length() <= NAME_MAX;
    }

    private static String operatorName(String operator) {
        if (operator == null || operator.isBlank()) return "Unknown";
        if (operator.equalsIgnoreCase("CONSOLE")) return CONSOLE;
        return truncate(operator, NAME_MAX);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
