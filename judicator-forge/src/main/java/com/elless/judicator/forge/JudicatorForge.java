package com.elless.judicator.forge;

import com.mojang.brigadier.ParseResults;
import com.elless.judicator.minecraft.JudicatorMinecraft;
import com.elless.judicator.minecraft.LoginCheckTask;
import com.elless.judicator.minecraft.MinecraftPlatform;
import com.elless.judicator.platform.PlatformType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.network.GatherLoginConfigurationTasksEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * The Forge mod: Forge events into {@link JudicatorMinecraft}, files in {@code config/judicator}. Only on a dedicated
 * server; clients without it can join.
 */
@Mod("judicator")
public final class JudicatorForge {

    public JudicatorForge(FMLJavaModLoadingContext context) {
        context.registerDisplayTest(IExtensionPoint.DisplayTest.IGNORE_SERVER_VERSION);
        if (FMLEnvironment.dist.isClient()) return;

        final ForgePermissions permissions = new ForgePermissions(ModList.getModContainerById("luckperms").isPresent());
        final JudicatorMinecraft mod = new JudicatorMinecraft(new MinecraftPlatform(PlatformType.FORGE,
                FMLPaths.CONFIGDIR.get().resolve("judicator"), permissions));

        PermissionGatherEvent.Nodes.BUS.addListener(event -> event.addNodes(permissions.nodes()));
        ServerStartingEvent.BUS.addListener(event -> mod.start(event.getServer()));
        ServerStoppingEvent.BUS.addListener(event -> mod.stop());
        RegisterCommandsEvent.BUS.addListener(event -> mod.registerCommands(event.getDispatcher()));
        GatherLoginConfigurationTasksEvent.BUS.addListener(event -> addLoginCheck(mod, event));
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) mod.join(player);
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) mod.quit(player);
        });
        // a cancellable bus: true cancels
        ServerChatEvent.BUS.addListener(event -> !mod.allowChat(event.getPlayer(), event.getRawText()));
        CommandEvent.BUS.addListener(event -> {
            final ParseResults<CommandSourceStack> parse = event.getParseResults();
            final ServerPlayer player = parse.getContext().getSource().getPlayer();
            return player != null && !mod.allowCommand(player, parse.getReader().getString());
        });
    }

    /**
     * The login waits for the checks in the configuration phase.
     */
    private static void addLoginCheck(JudicatorMinecraft mod, GatherLoginConfigurationTasksEvent event) {
        final Connection connection = event.getConnection();
        if (!(connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl configuration)
                || configuration.getOwner() == null) return;
        event.addTask(new LoginCheckTask(mod, configuration.getOwner(), hostOf(connection.getRemoteAddress()),
                configuration::disconnect));
    }

    private static String hostOf(SocketAddress address) {
        return address instanceof InetSocketAddress inet && inet.getAddress() != null
                ? inet.getAddress().getHostAddress()
                : String.valueOf(address);
    }
}
