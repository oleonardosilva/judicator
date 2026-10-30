package com.elless.judicator.neoforge;

import com.mojang.brigadier.ParseResults;
import com.elless.judicator.minecraft.JudicatorMinecraft;
import com.elless.judicator.minecraft.LoginCheckTask;
import com.elless.judicator.minecraft.MinecraftPlatform;
import com.elless.judicator.platform.PlatformType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * The NeoForge mod: NeoForge events into {@link JudicatorMinecraft}, files in {@code config/judicator}. Only on a
 * dedicated server.
 */
@Mod(value = "judicator", dist = Dist.DEDICATED_SERVER)
public final class JudicatorNeoForge {

    private final JudicatorMinecraft mod;

    public JudicatorNeoForge(IEventBus modBus) {
        final NeoForgePermissions permissions = new NeoForgePermissions(ModList.get().isLoaded("luckperms"));
        this.mod = new JudicatorMinecraft(new MinecraftPlatform(PlatformType.NEOFORGE,
                FMLPaths.CONFIGDIR.get().resolve("judicator"), permissions));

        modBus.addListener(this::onConfigurationTasks);
        final IEventBus game = NeoForge.EVENT_BUS;
        game.addListener((PermissionGatherEvent.Nodes event) -> event.addNodes(permissions.nodes()));
        game.addListener((ServerStartingEvent event) -> mod.start(event.getServer()));
        game.addListener((ServerStoppingEvent event) -> mod.stop());
        game.addListener((RegisterCommandsEvent event) -> mod.registerCommands(event.getDispatcher()));
        game.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) mod.join(player);
        });
        game.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) mod.quit(player);
        });
        game.addListener((ServerChatEvent event) -> {
            if (!mod.allowChat(event.getPlayer(), event.getRawText())) event.setCanceled(true);
        });
        game.addListener(this::onCommand);
    }

    /**
     * The login waits for the checks in the configuration phase.
     */
    private void onConfigurationTasks(RegisterConfigurationTasksEvent event) {
        final ServerConfigurationPacketListener listener = event.getListener();
        if (!(listener instanceof ServerConfigurationPacketListenerImpl configuration) || configuration.getOwner() == null) return;
        event.register(new LoginCheckTask(mod, configuration.getOwner(), hostOf(listener.getConnection().getRemoteAddress()),
                listener::disconnect));
    }

    private void onCommand(CommandEvent event) {
        final ParseResults<CommandSourceStack> parse = event.getParseResults();
        final ServerPlayer player = parse.getContext().getSource().getPlayer();
        if (player != null && !mod.allowCommand(player, parse.getReader().getString())) event.setCanceled(true);
    }

    private static String hostOf(SocketAddress address) {
        return address instanceof InetSocketAddress inet && inet.getAddress() != null
                ? inet.getAddress().getHostAddress()
                : String.valueOf(address);
    }
}
