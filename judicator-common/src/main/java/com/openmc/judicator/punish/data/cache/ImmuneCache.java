package com.openmc.judicator.punish.data.cache;

import java.util.ArrayList;
import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import org.spongepowered.configurate.ConfigurationNode;

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
 * Who cannot be punished: {@code players} in {@code immune.yml}, a UUID (or a name) and what it is exempt from —
 * {@code all}, {@code ban}, {@code mute}, {@code kick}, {@code warn} or {@code alts} (may join even when
 * {@code alts.deny-if-banned} would refuse it). A UUID keeps working after a name change, and in offline mode a name
 * proves nothing. The {@code users} list of 1.0.0 (names exempt from everything) is still honoured, with a warning, so
 * an upgrade does not leave anyone punishable by surprise.
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
            final List<String> users = judicator.getImmuneConfig().node("users").getList(String.class, List.of());
            if (!users.isEmpty()) {
                judicator.getLogger().warn("immune.yml: 'users' is deprecated; move each name to 'players' as \"<name or UUID>\": [all]");
                users.forEach(name -> names.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new HashSet<>()).add(ALL));
            }
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
     * @param type what the source wants to apply ({@link #BAN}, {@link #MUTE}, {@link #KICK}, {@link #WARN}); null
     *             before it is known (only a name exempt from everything refuses then; UUIDs are checked once the type
     *             is known, so no lookup is made twice)
     */
    public boolean canPunish(JudicatorSender source, String target, String type) {
        if (type != null) return canPunish(source, judicator.getPlayerDirectory().resolve(target), type);
        final ConfigurationNode messages = judicator.messagesFor(source);
        if (source.isPlayer() && source.getName().equalsIgnoreCase(target)) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "self-punish"));
            return false;
        }
        if (covers(byName.get(target.toLowerCase(Locale.ROOT)), null)) {
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
     * {@link #canPunish(JudicatorSender, String, String)} for a player already resolved (the command resolves the name
     * once and uses the same account for the checks and the punishment).
     *
     * @param type what the source wants to apply ({@link #BAN}, {@link #MUTE}, {@link #KICK}, {@link #WARN})
     */
    public boolean canPunish(JudicatorSender source, PlayerRef target, String type) {
        final ConfigurationNode messages = judicator.messagesFor(source);
        final boolean self = source.asPlayer()
                .map(player -> judicator.getPlayerDirectory().refOf(player))
                .map(own -> own.matches(target.uuid(), target.name()) || own.name().equalsIgnoreCase(target.name()))
                .orElse(false);
        if (self) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "self-punish"));
            return false;
        }
        if (isExempt(target.name(), target::uuid, type)) {
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
        final List<PlayerRef> accounts = new ArrayList<>(judicator.getAddressService().findAccountsByIp(address, null));
        judicator.getPlatform().getOnlinePlayers().stream()
                .filter(player -> address.equals(player.getHostAddress()))
                .map(player -> judicator.getPlayerDirectory().refOf(player))
                .forEach(accounts::add);
        for (PlayerRef account : accounts) {
            if (!canPunish(source, account, type)) return false;
        }
        return true;
    }
}
