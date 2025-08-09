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

import java.util.List;

public class WarnHistoryCommand {

    private final Judicator judicator;
    private final ProxyServer server;
    private final WarnService warnService;

    public WarnHistoryCommand(Judicator judicator) {
        this.judicator = judicator;
        this.server = judicator.getServer();
        this.warnService = judicator.getWarnService();
    }

    public void register() {
        final CommandManager commandManager = server.getCommandManager();
        final CommandMeta commandMeta = commandManager.metaBuilder("warnhistory")
                .aliases("whistory")
                .plugin(judicator)
                .build();

        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("warnhistory")
                .requires(source -> source.hasPermission(PunishPermissions.HISTORY.getPermission()) || source.hasPermission(PunishPermissions.ADMIN.getPermission()))
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            final String input = builder.getRemaining().toLowerCase();
                            server.matchPlayer(input).forEach(player -> builder.suggest(player.getUsername()));
                            return builder.buildFuture();
                        })
                        .executes(this::warnhistory)
                )
                .executes(this::wrongUsage)
                .build();

        final BrigadierCommand command = new BrigadierCommand(node);
        commandManager.register(commandMeta, command);
    }

    private int wrongUsage(CommandContext<CommandSource> context) {
        final ConfigurationNode messages = judicator.getMessagesConfig();
        final CommandSource source = context.getSource();
        final TextComponent text = WarnUtils.getMessage(messages, "usages", "warnhistory");
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameReturnValue")
    private int warnhistory(CommandContext<CommandSource> context) {
        final ConfigurationNode messages = judicator.getMessagesConfig();
        final CommandSource source = context.getSource();
        final String targetName = context.getArgument("player", String.class);

        final List<Warn> warns = warnService.findAllByUsername(targetName);
        if (warns.isEmpty()) {
            final TextComponent text = WarnUtils.getMessage(messages, "error", "angel");
            source.sendMessage(text);
            return Command.SINGLE_SUCCESS;
        }

        final TextComponent textComponent = WarnUtils.getWarnHistoryMessage(messages, targetName, warns);
        source.sendMessage(textComponent);
        return Command.SINGLE_SUCCESS;
    }

}
