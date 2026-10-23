package com.openmc.judicator.bukkit;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.placeholders.PlaceholderService;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code %judicator_<placeholder>%} for PlaceholderAPI (see {@link PlaceholderService} for the list). The only class
 * that touches PlaceholderAPI: it is loaded only when that plugin is installed.
 */
final class JudicatorExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final PlaceholderService placeholders;

    private JudicatorExpansion(JavaPlugin plugin, PlaceholderService placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
    }

    /**
     * @return what to run when the plugin is disabled
     */
    static Runnable register(JavaPlugin plugin, Judicator judicator) {
        final PlaceholderService placeholders = new PlaceholderService(judicator,
                value -> value ? PlaceholderAPIPlugin.booleanTrue() : PlaceholderAPIPlugin.booleanFalse());
        final JudicatorExpansion expansion = new JudicatorExpansion(plugin, placeholders);
        expansion.register();
        return () -> {
            expansion.unregister();
            placeholders.close();
        };
    }

    @Override
    public String getIdentifier() {
        return "judicator";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * Kept through {@code /papi reload}: Judicator registers it, not a downloaded jar.
     */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return null;
        return placeholders.resolve(player.getUniqueId(), params);
    }

}
