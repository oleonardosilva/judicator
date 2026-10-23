package com.openmc.judicator.punish.data.cache;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import org.spongepowered.configurate.ConfigurationNode;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Who cannot be punished, from {@code immune.yml}:
 * <ul>
 *     <li>{@code users}: names exempt from everything (the format of older versions);</li>
 *     <li>{@code players}: a UUID (or a name) and what it is exempt from — {@code all}, {@code ban}, {@code mute},
 *     {@code kick}, {@code warn} or {@code alts} (may join even when {@code alts.deny-if-banned} would refuse it).
 *     A UUID keeps working after a name change, and in offline mode a name proves nothing.</li>
 * </ul>
 */
public class ImmuneCache {

    public static final String ALL = "all";
    public static final String BAN = "ban";
    public static final String MUTE = "mute";
    public static final String KICK = "kick";
    public static final String WARN = "warn";
    public static final String ALTS = "alts";
    private static final Set<String> TYPES = Set.of(ALL, BAN, MUTE, KICK, WARN, ALTS);

    private volatile Map<String, Set<String>> byName = Map.of();
    private volatile Map<UUID, Set<String>> byUuid = Map.of();
    private Judicator judicator;

    public ImmuneCache() {
    }

    public void initialize(Judicator judicator) {
        this.judicator = judicator;
        final Map<String, Set<String>> names = new HashMap<>();
        final Map<UUID, Set<String>> uuids = new HashMap<>();
        try {
            final List<String> users = judicator.getImmuneConfig().node("users").getList(String.class);
            if (users != null) users.forEach(name -> names.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new HashSet<>()).add(ALL));
        } catch (Exception e) {
            judicator.getLogger().error(e.getMessage(), e);
        }
        judicator.getImmuneConfig().node("players").childrenMap().forEach((key, node) -> {
            final String id = String.valueOf(key).trim();
            final Set<String> types = types(id, node);
            if (types.isEmpty()) return;
            final UUID uuid = judicator.getUuidManager().fromString(id);
            if (uuid != null) uuids.computeIfAbsent(uuid, k -> new HashSet<>()).addAll(types);
            else names.computeIfAbsent(id.toLowerCase(Locale.ROOT), k -> new HashSet<>()).addAll(types);
        });
        this.byName = Map.copyOf(names);
        this.byUuid = Map.copyOf(uuids);
    }

    private Set<String> types(String id, ConfigurationNode node) {
        final Set<String> types = new HashSet<>();
        try {
            final List<String> listed = node.isList() ? node.getList(String.class, List.of()) : List.of(node.getString(""));
            for (String type : listed) {
                final String normalized = type.trim().toLowerCase(Locale.ROOT);
                if (TYPES.contains(normalized)) types.add(normalized);
                else judicator.getLogger().warn("immune.yml: ignoring '{}' for {} (use all, ban, mute, kick, warn or alts)", type, id);
            }
        } catch (Exception e) {
            judicator.getLogger().warn("immune.yml: ignoring {}: give a list such as [ban, mute]", id);
        }
        return types;
    }

    /**
     * @param uuid the player's UUID when known
     * @param type {@link #BAN}, {@link #MUTE}, {@link #KICK}, {@link #WARN}, {@link #ALTS}; null when only an exemption
     *             from everything counts
     */
    public boolean isExempt(String name, UUID uuid, String type) {
        return isExempt(name, () -> uuid, type);
    }

    /**
     * @param uuid asked only when no name entry decides and some exemption is by UUID (the lookup may be slow)
     */
    private boolean isExempt(String name, Supplier<UUID> uuid, String type) {
        if (covers(byName.get(name.toLowerCase(Locale.ROOT)), type)) return true;
        if (byUuid.isEmpty()) return false;
        final UUID id = uuid.get();
        return id != null && covers(byUuid.get(id), type);
    }

    private static boolean covers(Set<String> types, String type) {
        return types != null && (types.contains(ALL) || (type != null && types.contains(type)));
    }

    /**
     * The target's UUID: the online player's or the one remembered at join (whatever case was typed); otherwise, in
     * offline mode, the one the server gives that name (the Mojang API would answer with a premium account's), and in
     * online mode the Mojang API's.
     */
    private UUID uuidOf(String target) {
        final UUID known = judicator.getUuidManager().getKnownUUID(target);
        if (known != null) return known;
        if (!judicator.trustsUuids()) {
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + target).getBytes(StandardCharsets.UTF_8));
        }
        return judicator.getUuidManager().getUUID(target);
    }

    /**
     * @param type what the source wants to apply ({@link #BAN}, {@link #MUTE}, {@link #KICK}, {@link #WARN}); null
     *             before it is known (only a name exempt from everything refuses then; UUIDs are checked once the type
     *             is known, so no lookup is made twice)
     */
    public boolean canPunish(JudicatorSender source, String target, String type) {
        final ConfigurationNode messages = judicator.messagesFor(source);
        if (source.isPlayer() && source.getName().equalsIgnoreCase(target)) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "self-punish"));
            return false;
        }

        final boolean exempt = type == null
                ? covers(byName.get(target.toLowerCase(Locale.ROOT)), null)
                : isExempt(target, () -> uuidOf(target), type);
        if (exempt) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "immune"));
            return false;
        }

        if (!judicator.getHierarchyService().canPunish(source, target)) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "hierarchy"));
            return false;
        }
        return true;
    }

    /**
     * An IP punishment reaches every account that used the address, so each of them must be punishable by the
     * source (self, exemptions and hierarchy), not only the account that was named in the command.
     */
    public boolean canPunishAddress(JudicatorSender source, String address, String type) {
        final Set<String> accounts = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        accounts.addAll(judicator.getAddressService().findAccountsByIp(address, ""));
        judicator.getPlatform().getOnlinePlayers().stream()
                .filter(player -> address.equals(player.getHostAddress()))
                .map(JudicatorPlayer::getName)
                .forEach(accounts::add);
        for (String account : accounts) {
            if (!canPunish(source, account, type)) return false;
        }
        return true;
    }
}
