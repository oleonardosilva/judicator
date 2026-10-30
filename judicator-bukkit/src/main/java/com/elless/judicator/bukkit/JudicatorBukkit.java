package com.elless.judicator.bukkit;

import com.elless.judicator.Judicator;
import com.elless.judicator.api.JudicatorApi;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.reports.TeleportRequest;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import com.elless.judicator.menus.MenuRenderer;
import com.elless.judicator.menus.MenuService;

import java.io.IOException;
import java.util.function.Supplier;
import java.net.URLClassLoader;
import java.util.logging.Level;

public class JudicatorBukkit extends JavaPlugin {

    // https://bstats.org/what-is-my-plugin-id
    private static final int BSTATS_PLUGIN_ID = 34895;

    private BukkitAudiences audiences;
    /** the JDBC driver on servers without libraries: (before 1.16.5) */
    private URLClassLoader drivers;
    private Judicator judicator;
    private Runnable unhookPlaceholders;

    @Override
    public void onEnable() {
        final Scheduling scheduling;
        try {
            scheduling = Scheduling.of(this);
        } catch (IllegalStateException e) {
            getLogger().severe("Judicator was not enabled: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.audiences = BukkitAudiences.create(this);
        final BukkitPlatform platform = new BukkitPlatform(this, scheduling, audiences);
        try {
            this.drivers = LegacyDrivers.load(getDataFolder().toPath(), platform.getLogger()).orElse(null);
        } catch (IllegalStateException e) {
            getLogger().severe("Judicator was not enabled: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        platform.databaseDrivers(drivers);
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
        platform.menuRenderer().bind(core::getMenuService);
        platform.dialogsSetting(() -> BukkitMenuChooser.Mode.of(core.getConfig().node("menus", "dialogs").getString("mixed")));
        getServer().getPluginManager().registerEvents(platform.menuRenderer(), this);
        getServer().getPluginManager().registerEvents(platform.menuChooser(), this);
        loadDialogs(platform, core);
        // /reports tp on the proxy: the backend teleports the staff member it was sent
        if (core.isProxyMode()) {
            getServer().getMessenger().registerIncomingPluginChannel(this, TeleportRequest.CHANNEL, new BukkitTeleportListener(this, platform.scheduling()));
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

    /**
     * Dialogs for 1.21.6+ clients when the server is a Paper with their API. The class is compiled against that API
     * with Java 21, so it is only loaded then.
     */
    private void loadDialogs(BukkitPlatform platform, Judicator core) {
        if (!BukkitCompat.classExists("io.papermc.paper.dialog.Dialog")) return;
        try {
            final MenuRenderer dialogs = (MenuRenderer) Class.forName("com.elless.judicator.bukkit.PaperDialogs")
                    .getDeclaredConstructor(Scheduling.class, MenuItems.class, Supplier.class)
                    .newInstance(platform.scheduling(), platform.menuRenderer().items(), (Supplier<MenuService>) core::getMenuService);
            platform.menuChooser().dialogs(dialogs);
            getLogger().info("Menus use dialogs for 1.21.6+ clients (menus.dialogs in config.yml).");
        } catch (ReflectiveOperationException | LinkageError e) {
            getLogger().log(Level.WARNING, "Dialogs are not available on this server; menus open as inventories.", e);
        }
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
        if (drivers != null) {
            try {
                drivers.close();
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Could not close the database driver", e);
            }
            drivers = null;
        }
        if (audiences != null) {
            audiences.close();
            audiences = null;
        }
    }

}
