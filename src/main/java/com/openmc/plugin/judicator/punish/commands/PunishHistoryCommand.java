package com.openmc.plugin.judicator.punish.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.punish.PunishService;
import com.openmc.plugin.judicator.punish.PunishUtils;
import com.openmc.plugin.judicator.punish.Punishment;
import com.openmc.plugin.judicator.punish.types.PunishPermissions;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;

public class PunishHistoryCommand {

    private final Judicator judicator;
    private final ProxyServer server;
    private final ConfigurationNode messages;
    private final PunishService punishService;

    public PunishHistoryCommand(Judicator judicator) {
        this.judicator = judicator;
        this.server = judicator.getServer();
        this.messages = judicator.getMessagesConfig();
        this.punishService = judicator.getPunishService();
    }

    public void register() {
        final CommandManager commandManager = server.getCommandManager();
        final CommandMeta commandMeta = commandManager.metaBuilder("punishhistory")
                .aliases("phistory")
                .plugin(judicator)
                .build();

        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("punishhistory")
                .requires(source -> {
                    final boolean b = source.hasPermission(PunishPermissions.PUNISHHISTORY.getPermission()) || source.hasPermission(PunishPermissions.ADMIN.getPermission());
                    if (!b) {
                        final TextComponent text = PunishUtils.getMessage(messages, "error", "permission");
                        source.sendMessage(text);
                    }
                    return b;
                })
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            final String input = builder.getRemaining().toLowerCase();
                            server.matchPlayer(input).forEach(player -> builder.suggest(player.getUsername()));
                            return builder.buildFuture();
                        })
                        .executes(this::punishhistory)
                )
                .executes(this::wrongUsage)
                .build();

        final BrigadierCommand command = new BrigadierCommand(node);
        commandManager.register(commandMeta, command);
    }

    private int wrongUsage(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final TextComponent text = PunishUtils.getMessage(messages, "usages", "punishhistory");
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameReturnValue")
    private int punishhistory(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final String targetName = context.getArgument("player", String.class);


        final List<Punishment> punishments = punishService.findAllByUsername(targetName);
        if (punishments.isEmpty()) {
            final TextComponent text = PunishUtils.getMessage(messages, "error", "angel");
            source.sendMessage(text);
            return Command.SINGLE_SUCCESS;
        }

        final TextComponent textComponent = PunishUtils.getPunishmentHistoryMessage(messages, targetName, punishments);
        source.sendMessage(textComponent);
        return Command.SINGLE_SUCCESS;
    }

}
