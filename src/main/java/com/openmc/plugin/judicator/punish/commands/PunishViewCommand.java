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

import java.util.Optional;

public class PunishViewCommand {

    private final Judicator judicator;
    private final ProxyServer server;
    private final ConfigurationNode messages;
    private final PunishService punishService;

    public PunishViewCommand(Judicator judicator) {
        this.judicator = judicator;
        this.server = judicator.getServer();
        this.messages = judicator.getMessagesConfig();
        this.punishService = judicator.getPunishService();
    }

    public void register() {
        final CommandManager commandManager = server.getCommandManager();
        final CommandMeta commandMeta = commandManager.metaBuilder("punishview")
                .aliases("pview")
                .plugin(judicator)
                .build();

        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("punishview")
                .requires(source -> {
                    final boolean b = source.hasPermission(PunishPermissions.PUNISHVIEW.getPermission()) || source.hasPermission(PunishPermissions.ADMIN.getPermission());
                    if (!b) {
                        final TextComponent text = PunishUtils.getMessage(messages, "error", "permission");
                        source.sendMessage(text);
                    }
                    return b;
                })
                .then(BrigadierCommand.requiredArgumentBuilder("id", StringArgumentType.word()).executes(this::punishview))
                .executes(this::wrongUsage)
                .build();

        final BrigadierCommand command = new BrigadierCommand(node);
        commandManager.register(commandMeta, command);
    }

    private int wrongUsage(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final TextComponent text = PunishUtils.getMessage(messages, "usages", "punishview");
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameReturnValue")
    private int punishview(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final Long id = Long.parseLong(context.getArgument("id", String.class).replace("#", ""));

        final Optional<Punishment> optPunishment = punishService.findById(id);
        if (optPunishment.isEmpty()) {
            final TextComponent text = PunishUtils.getMessage(messages, "error", "punish-not-found");
            source.sendMessage(text);
            return Command.SINGLE_SUCCESS;
        }

        final Punishment punishment = optPunishment.get();
        final TextComponent textComponent = PunishUtils.getMessageList(messages, punishment, "runners", "punishment-view");
        source.sendMessage(textComponent);
        return Command.SINGLE_SUCCESS;
    }

}
