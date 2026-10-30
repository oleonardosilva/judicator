package com.elless.judicator.fabric.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The account and the connection of a login, which the login events of Fabric API do not give.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public interface ServerLoginPacketListenerImplAccessor {

    @Accessor("authenticatedProfile")
    GameProfile judicator$profile();

    @Accessor("connection")
    Connection judicator$connection();
}
