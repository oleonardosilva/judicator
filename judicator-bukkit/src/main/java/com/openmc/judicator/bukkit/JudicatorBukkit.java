package com.openmc.judicator.bukkit;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.api.JudicatorApi;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.reports.TeleportRequest;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class JudicatorBukkit extends JavaPlugin {

    // https://bstats.org/what-is-my-plugin-id
    private static final int BSTATS_PLUGIN_ID = 34895;

    private BukkitAudiences audiences;
    private Judicator judicator;
    private Runnable unhookPlaceholders;

    @Override
    public void onEnable() {
        this.audiences = BukkitAudiences.create(this);
        final BukkitPlatform platform = new BukkitPlatform(this, audiences);
        platform.trackOnlinePlayers();

        final Judicator core = new Judicator(platform);
        try {
            core.enable();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Judicator could not be enabled; check the configuration files.", e);
            core.disable();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.judicator = core;

        for (CoreCommand command : core.getCommands()) {
            final PluginCommand pluginCommand = getCommand(command.getName());
            if (pluginCommand == null) {
                getLogger().warning("Command /" + command.getName() + " is missing from plugin.yml and was not registered.");
                continue;
            }
            final BukkitCommandAdapter adapter = new BukkitCommandAdapter(core, platform, command);
            pluginCommand.setExecutor(adapter);
            pluginCommand.setTabCompleter(adapter);
        }

        getServer().getPluginManager().registerEvents(new BukkitListener(core, platform), this);
        // /reports tp on the proxy: the backend teleports the staff member it was sent
        if (core.isProxyMode()) {
            getServer().getMessenger().registerIncomingPluginChannel(this, TeleportRequest.CHANNEL, new BukkitTeleportListener(this));
            getServer().getMessenger().registerOutgoingPluginChannel(this, TeleportRequest.CHANNEL);
        }
        // Also reachable through JudicatorProvider.get(), set by Judicator.enable().
        getServer().getServicesManager().register(JudicatorApi.class, core.getApi(), this, ServicePriority.Normal);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            unhookPlaceholders = JudicatorExpansion.register(this, core);
        }
        new Metrics(this, BSTATS_PLUGIN_ID);
        getLogger().info("Plugin has been initialized successfully!");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (unhookPlaceholders != null) {
            unhookPlaceholders.run();
            unhookPlaceholders = null;
        }
        if (judicator != null) {
            judicator.disable();
            judicator = null;
        }
        if (audiences != null) {
            audiences.close();
            audiences = null;
        }
    }

}
