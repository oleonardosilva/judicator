package com.elless.judicator.fabric;

import com.elless.judicator.minecraft.MinecraftPermissions;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.server.level.ServerPlayer;

/**
 * fabric-permissions-api: LuckPerms or another permissions mod answers; without one, the op level.
 */
final class FabricPermissions implements MinecraftPermissions {

    @Override
    public boolean has(ServerPlayer player, String permission) {
        return Permissions.check(player, permission, MinecraftPermissions.defaultLevel(permission));
    }

    @Override
    public boolean explicit(ServerPlayer player, String permission) {
        return Permissions.getPermissionValue(player, permission) == TriState.TRUE;
    }
}
