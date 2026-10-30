package com.elless.judicator.neoforge;

import com.elless.judicator.minecraft.LuckPermsCheck;
import com.elless.judicator.minecraft.MinecraftPermissions;
import com.elless.judicator.punish.types.PunishPermissions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * With LuckPerms, every permission from it; otherwise the {@code judicator.*} nodes of {@link PunishPermissions} through
 * the permission API of NeoForge (which another permissions mod may answer), and the op level for the rest.
 */
final class NeoForgePermissions implements MinecraftPermissions {

    private static final String PREFIX = "judicator.";

    private final Map<String, PermissionNode<Boolean>> nodes = Arrays.stream(PunishPermissions.values())
            .map(PunishPermissions::getPermission)
            .filter(permission -> permission.startsWith(PREFIX))
            .collect(Collectors.toMap(Function.identity(), NeoForgePermissions::node));
    private final LuckPermsCheck luckPerms;

    NeoForgePermissions(boolean luckPerms) {
        this.luckPerms = luckPerms ? new LuckPermsCheck() : null;
    }

    private static PermissionNode<Boolean> node(String permission) {
        return new PermissionNode<>("judicator", permission.substring(PREFIX.length()), PermissionTypes.BOOLEAN,
                (player, uuid, context) -> player != null && MinecraftPermissions.byDefault(player, permission));
    }

    /**
     * For {@code PermissionGatherEvent.Nodes}: the API only answers nodes registered there.
     */
    List<PermissionNode<?>> nodes() {
        return List.copyOf(nodes.values());
    }

    @Override
    public boolean has(ServerPlayer player, String permission) {
        if (luckPerms != null) {
            final Boolean value = luckPerms.value(player, permission);
            return value != null ? value : MinecraftPermissions.byDefault(player, permission);
        }
        final PermissionNode<Boolean> node = nodes.get(permission);
        if (node == null) return MinecraftPermissions.byDefault(player, permission);
        try {
            return PermissionAPI.getPermission(player, node);
        } catch (RuntimeException e) {
            // before the API is ready, or a handler that does not know the node
            return MinecraftPermissions.byDefault(player, permission);
        }
    }

    @Override
    public boolean explicit(ServerPlayer player, String permission) {
        return luckPerms != null && Boolean.TRUE.equals(luckPerms.value(player, permission));
    }
}
