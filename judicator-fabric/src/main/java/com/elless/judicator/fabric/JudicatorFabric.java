package com.elless.judicator.fabric;

import com.mojang.authlib.GameProfile;
import com.elless.judicator.fabric.mixin.ServerLoginPacketListenerImplAccessor;
import com.elless.judicator.minecraft.JudicatorMinecraft;
import com.elless.judicator.minecraft.MinecraftPlatform;
import com.elless.judicator.platform.PlatformType;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * The Fabric mod: Fabric API events into {@link JudicatorMinecraft}, files in {@code config/judicator}.
 */
public final class JudicatorFabric implements DedicatedServerModInitializer {

    private static volatile JudicatorMinecraft judicator;

    @Override
    public void onInitializeServer() {
        final JudicatorMinecraft mod = new JudicatorMinecraft(new MinecraftPlatform(PlatformType.FABRIC,
                FabricLoader.getInstance().getConfigDir().resolve("judicator"), new FabricPermissions()));
        judicator = mod;

        ServerLifecycleEvents.SERVER_STARTING.register(mod::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> mod.stop());
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> mod.registerCommands(dispatcher));

        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> login(mod, handler, synchronizer));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> mod.join(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> mod.quit(handler.player));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> mod.allowChat(sender, message.signedContent()));
    }

    /**
     * The login waits for the checks, done off the game thread.
     */
    private static void login(JudicatorMinecraft mod, ServerLoginPacketListenerImpl handler,
                              ServerLoginNetworking.LoginSynchronizer synchronizer) {
        final ServerLoginPacketListenerImplAccessor login = (ServerLoginPacketListenerImplAccessor) handler;
        final GameProfile profile = login.judicator$profile();
        if (profile == null) return;
        final String address = hostOf(login.judicator$connection().getRemoteAddress());
        synchronizer.waitFor(CompletableFuture.runAsync(() -> {
            try {
                mod.login(profile.id(), profile.name(), address).ifPresent(handler::disconnect);
            } catch (RuntimeException e) {
                mod.platform().getLogger().error("Could not check the login of {}", profile.name(), e);
            }
        }, mod.platform().asyncExecutor()));
    }

    private static String hostOf(SocketAddress address) {
        return address instanceof InetSocketAddress inet && inet.getAddress() != null
                ? inet.getAddress().getHostAddress()
                : String.valueOf(address);
    }

    /**
     * {@code CommandsMixin}: whether a command may run.
     *
     * @param command without the slash
     */
    public static boolean allowCommand(CommandSourceStack source, String command) {
        final JudicatorMinecraft mod = judicator;
        final ServerPlayer player = source.getPlayer();
        return mod == null || player == null || mod.allowCommand(player, command);
    }
}
