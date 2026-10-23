package com.openmc.judicator.punish.scopes;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.punish.Punishment;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Where a punishment applies ({@code scopes} in {@code config.yml}): everywhere (no scope), on one server of the
 * network ({@code server:<name>}) or on a group of servers ({@code group:<name>}). The proxy knows the server each
 * player is on; a Spigot/Paper server knows its own name from {@code scopes.server-name}.
 */
public class ScopeService {

    public static final String SERVER = "server:";
    public static final String GROUP = "group:";
    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]{1,32}");

    private final Judicator judicator;
    private volatile String serverName = "";
    private volatile Map<String, Set<String>> groups = Map.of();

    public ScopeService(Judicator judicator) {
        this.judicator = judicator;
        reload();
    }

    public void reload() {
        final ConfigurationNode scopes = judicator.getConfig().node("scopes");
        final String name = scopes.node("server-name").getString("").trim().toLowerCase(Locale.ROOT);
        if (!name.isEmpty() && !VALID.matcher(name).matches()) {
            judicator.getLogger().warn("scopes.server-name '{}' is not a server name (up to 32 of a-z, 0-9, _, - and .): ignored.", name);
            this.serverName = "";
        } else {
            this.serverName = name;
        }
        final Map<String, Set<String>> read = new HashMap<>();
        scopes.node("groups").childrenMap().forEach((key, node) -> {
            final String group = String.valueOf(key).trim().toLowerCase(Locale.ROOT);
            if (!VALID.matcher(group).matches()) {
                judicator.getLogger().warn("scopes.groups: ignoring '{}': not a group name (up to 32 of a-z, 0-9, _, - and .)", key);
                return;
            }
            try {
                final Set<String> servers = new HashSet<>();
                for (String server : node.getList(String.class, List.of())) servers.add(server.trim().toLowerCase(Locale.ROOT));
                read.put(group, Set.copyOf(servers));
            } catch (SerializationException e) {
                judicator.getLogger().warn("scopes.groups: ignoring '{}': give a list of servers such as [survival1, survival2]", key);
            }
        });
        this.groups = Map.copyOf(read);
        warnAboutMissingGroups();
        if (judicator.isProxyMode() && serverName.isEmpty()) {
            judicator.getLogger().warn("proxy-mode without scopes.server-name: mutes for one server or group never apply on this server.");
        }
    }

    /**
     * A punishment for a group that {@code scopes.groups} no longer has applies on no server: say so, rather than let
     * bans and mutes stop holding unnoticed.
     */
    private void warnAboutMissingGroups() {
        if (judicator.getPunishService() == null) return;
        judicator.getPlatform().runAsync(() -> {
            for (String scope : judicator.getPunishService().findActiveScopes()) {
                if (scope.startsWith(GROUP) && !groups.containsKey(scope.substring(GROUP.length()))) {
                    judicator.getLogger().warn("Punishments in force are for the group '{}', which scopes.groups no longer has: they apply on no server.",
                            scope.substring(GROUP.length()));
                }
            }
        });
    }

    private boolean onProxy() {
        return judicator.getPlatform().getType().isProxy();
    }

    /**
     * Whether {@code -scope=} can be used here: always on the proxy; on Spigot/Paper only with
     * {@code scopes.server-name} (without it the server cannot tell where it is).
     */
    public boolean canScope() {
        return onProxy() || !serverName.isEmpty();
    }

    /**
     * The scope as it is stored for a name typed after {@code -scope=}: a group of {@code scopes.groups}, else a server
     * of the network (on the proxy, one it knows; on Spigot/Paper, this server or one in a group).
     *
     * @return empty when it is neither
     */
    public Optional<String> parse(String input) {
        final String name = input.trim().toLowerCase(Locale.ROOT);
        if (!VALID.matcher(name).matches()) return Optional.empty();
        if (groups.containsKey(name)) return Optional.of(GROUP + name);
        if (knownServers().contains(name)) return Optional.of(SERVER + name);
        return Optional.empty();
    }

    private Set<String> knownServers() {
        final Set<String> servers = new HashSet<>();
        if (onProxy()) {
            judicator.getPlatform().serverNames().forEach(server -> servers.add(server.toLowerCase(Locale.ROOT)));
        } else {
            if (!serverName.isEmpty()) servers.add(serverName);
            groups.values().forEach(servers::addAll);
        }
        return servers;
    }

    /**
     * What {@code -scope=} offers: the groups, then the servers.
     */
    public List<String> suggestions() {
        final List<String> names = new ArrayList<>(groups.keySet().stream().sorted().toList());
        knownServers().stream().sorted().filter(server -> !names.contains(server)).forEach(names::add);
        return names;
    }

    /**
     * @param server the server's name; null when not known (a player not on a server yet, a Spigot/Paper server
     *               without {@code scopes.server-name}): only a punishment without scope applies then
     */
    public boolean appliesTo(Punishment punishment, String server) {
        return appliesTo(punishment.getScope().orElse(null), server);
    }

    public boolean appliesTo(String scope, String server) {
        if (scope == null) return true;
        if (server == null) return false;
        final String name = server.toLowerCase(Locale.ROOT);
        if (scope.startsWith(SERVER)) return scope.substring(SERVER.length()).equals(name);
        if (scope.startsWith(GROUP)) return groups.getOrDefault(scope.substring(GROUP.length()), Set.of()).contains(name);
        return false;
    }

    /**
     * The server a player is on: the proxy's backend; on Spigot/Paper, {@code scopes.server-name}.
     */
    public Optional<String> serverOf(JudicatorPlayer player) {
        if (onProxy()) return judicator.getPlatform().serverOf(player).map(name -> name.toLowerCase(Locale.ROOT));
        return serverName.isEmpty() ? Optional.empty() : Optional.of(serverName);
    }

    public boolean appliesToPlayer(Punishment punishment, JudicatorPlayer player) {
        return punishment.getScope().isEmpty() || serverOf(player).map(server -> appliesTo(punishment, server)).orElse(false);
    }

    /**
     * Whether a ban refuses the login: one without scope; on Spigot/Paper also one for this server. On the proxy a ban
     * for some servers only keeps the player out of those ({@code checkServer}).
     */
    public boolean refusesLogin(Punishment punishment) {
        if (punishment.getScope().isEmpty()) return true;
        return !onProxy() && !serverName.isEmpty() && appliesTo(punishment, serverName);
    }

    /**
     * A scope of another plugin as Judicator stores it, for imports.
     *
     * @param group true for a group of servers (a LibertyBans category), false for one server
     * @return empty when the name cannot be a Judicator scope (too long, other characters)
     */
    public static Optional<String> imported(String name, boolean group) {
        if (name == null) return Optional.empty();
        final String lower = name.trim().toLowerCase(Locale.ROOT);
        if (!VALID.matcher(lower).matches()) return Optional.empty();
        return Optional.of((group ? GROUP : SERVER) + lower);
    }

    /**
     * Whether {@code scopes.groups} has this group.
     */
    public boolean hasGroup(String name) {
        return name != null && groups.containsKey(name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * The name shown for a stored scope ({@code survival} for {@code group:survival}).
     */
    public static String displayName(String scope) {
        final int colon = scope.indexOf(':');
        return colon < 0 ? scope : scope.substring(colon + 1);
    }

}
