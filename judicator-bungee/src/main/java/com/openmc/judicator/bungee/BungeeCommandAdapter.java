package com.openmc.judicator.bungee;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

public class BungeeCommandAdapter extends Command implements TabExecutor {

    private final Judicator judicator;
    private final BungeePlatform platform;
    private final CoreCommand command;

    BungeeCommandAdapter(Judicator judicator, BungeePlatform platform, CoreCommand command) {
        // no permission node here: hasPermission asks the command
        super(command.getName(), null, command.getAliases().toArray(String[]::new));
        this.judicator = judicator;
        this.platform = platform;
        this.command = command;
    }

    @Override
    public void execute(CommandSender bungeeSender, String[] args) {
        final JudicatorSender sender = BungeeSender.of(bungeeSender, platform);
        final CommandArgs commandArgs = CommandArgs.of(args);
        platform.runAsync(() -> {
            try {
                command.execute(sender, commandArgs);
            } catch (RuntimeException e) {
                judicator.getLogger().error("Error while executing /{}", command.getName(), e);
            }
        });
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender bungeeSender, String[] args) {
        return command.suggest(BungeeSender.of(bungeeSender, platform), args);
    }

    @Override
    public boolean hasPermission(CommandSender bungeeSender) {
        return command.canUse(BungeeSender.of(bungeeSender, platform));
    }

}
