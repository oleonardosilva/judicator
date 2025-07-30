package com.openmc.judicator.warns.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.WarnService;
import com.openmc.judicator.warns.WarnUtils;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Optional;

public class UnwarnCommand {

    private final Judicator judicator;
    private final ProxyServer server;
    private final WarnService warnService;

    public UnwarnCommand(Judicator judicator) {
        this.judicator = judicator;
        this.server = judicator.getServer();
        this.warnService = judicator.getWarnService();
    }

    public void register() {
        final ConfigurationNode messages = judicator.getMessagesConfig();
        final CommandManager commandManager = server.getCommandManager();
        final CommandMeta commandMeta = commandManager.metaBuilder("unwarn")
                .plugin(judicator)
                .build();

        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("unwarn")
                .requires(source -> {
                    final boolean b = source.hasPermission(PunishPermissions.ADMIN.getPermission());
                    if (!b) {
                        final TextComponent text = WarnUtils.getMessage(messages, "error", "permission");
                        source.sendMessage(text);
                    }
                    return b;
                })
                .then(BrigadierCommand.requiredArgumentBuilder("id", StringArgumentType.word())
                        .executes(this::unwarn)
                )
                .executes(this::wrongUsage)
                .build();

        final BrigadierCommand command = new BrigadierCommand(node);
        commandManager.register(commandMeta, command);
    }

    private int wrongUsage(CommandContext<CommandSource> context) {
        final ConfigurationNode messages = judicator.getMessagesConfig();
        final CommandSource source = context.getSource();
        final TextComponent text = WarnUtils.getMessage(messages, "usages", "unwarn");
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameReturnValue")
    private int unwarn(CommandContext<CommandSource> context) {
        final ConfigurationNode messages = judicator.getMessagesConfig();
        final CommandSource source = context.getSource();
        final Long id = Long.parseLong(context.getArgument("id", String.class).replace("#", ""));

        final Optional<Warn> optWarn = warnService.findById(id);
        if (optWarn.isEmpty()) {
            final TextComponent text = WarnUtils.getMessage(messages, "error", "warn-not-found");
            source.sendMessage(text);
            return Command.SINGLE_SUCCESS;
        }

        final Warn warn = optWarn.get();
        warnService.deleteById(id);

        final TextComponent text = WarnUtils.getMessage(messages, "success", "unwarn");
        source.sendMessage(text);

        server.getPlayer(warn.getNickname()).ifPresent(player -> {
            final TextComponent gotPardon = WarnUtils.getMessage(messages, "success", "got-unwarn");
            player.sendMessage(gotPardon);
        });

        return Command.SINGLE_SUCCESS;
    }

}
