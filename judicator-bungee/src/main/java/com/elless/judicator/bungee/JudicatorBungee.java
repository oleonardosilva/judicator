package com.elless.judicator.bungee;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.reports.TeleportRequest;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.plugin.Plugin;
import org.bstats.bungeecord.Metrics;

import java.util.logging.Level;

public class JudicatorBungee extends Plugin {

    // https://bstats.org/what-is-my-plugin-id
    private static final int BSTATS_PLUGIN_ID = 34896;

    private BungeeAudiences audiences;
    private Judicator judicator;

    @Override
    public void onEnable() {
        this.audiences = BungeeAudiences.create(this);
        final BungeePlatform platform = new BungeePlatform(this, audiences);

        final Judicator core = new Judicator(platform);
        try {
            core.enable();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Judicator could not be enabled; check the configuration files.", e);
            core.disable();
            return;
        }
        this.judicator = core;

        for (CoreCommand command : core.getCommands()) {
            getProxy().getPluginManager().registerCommand(this, new BungeeCommandAdapter(core, platform, command));
        }
        getProxy().getPluginManager().registerListener(this, new BungeeListener(this, core, platform));
        getProxy().registerChannel(TeleportRequest.CHANNEL);
        new Metrics(this, BSTATS_PLUGIN_ID);
        getLogger().info("Plugin has been initialized successfully!");
    }

    @Override
    public void onDisable() {
        getProxy().unregisterChannel(TeleportRequest.CHANNEL);
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
