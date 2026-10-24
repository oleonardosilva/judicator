package com.openmc.judicator.bukkit;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;

public class BukkitCommandAdapter implements TabExecutor {

    private static final String DEFAULT_NO_PERMISSION = "§cYou do not have permission to use this command.";

    private final Judicator judicator;
    private final BukkitPlatform platform;
    private final CoreCommand command;

    BukkitCommandAdapter(Judicator judicator, BukkitPlatform platform, CoreCommand command) {
        this.judicator = judicator;
        this.platform = platform;
        this.command = command;
    }

    @Override
    public boolean onCommand(CommandSender bukkitSender, Command bukkitCommand, String label, String[] args) {
        final JudicatorSender sender = BukkitSender.of(bukkitSender, platform);
        if (!command.canUse(sender)) {
            sender.sendMessage(noPermissionMessage());
            return true;
        }
        if (judicator.getMenuService().redirect(sender, command.getName(), args)) return true;
        final CommandArgs commandArgs = CommandArgs.of(args);
        platform.runAsync(() -> {
            try {
                command.execute(sender, commandArgs);
            } catch (RuntimeException e) {
                judicator.getLogger().error("Error while executing /{}", command.getName(), e);
            }
        });
        return true;
    }

    private Component noPermissionMessage() {
        final ConfigurationNode node = judicator.getMessagesConfig().node("error", "no-permission");
        if (node.virtual()) return Component.text(DEFAULT_NO_PERMISSION);
        return PunishUtils.getMessage(judicator.getMessagesConfig(), "error", "no-permission");
    }

    @Override
    public List<String> onTabComplete(CommandSender bukkitSender, Command bukkitCommand, String label, String[] args) {
        final JudicatorSender sender = BukkitSender.of(bukkitSender, platform);
        if (!command.canUse(sender)) return List.of();
        return command.suggest(sender, args);
    }

}
