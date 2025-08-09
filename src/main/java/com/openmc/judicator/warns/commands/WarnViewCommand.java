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

public class WarnViewCommand {

    private final Judicator judicator;
    private final ProxyServer server;
    private final WarnService warnService;

    public WarnViewCommand(Judicator judicator) {
        this.judicator = judicator;
        this.server = judicator.getServer();
        this.warnService = judicator.getWarnService();
    }

    public void register() {
        final CommandManager commandManager = server.getCommandManager();
        final CommandMeta commandMeta = commandManager.metaBuilder("warnview")
                .aliases("wview")
                .plugin(judicator)
                .build();

        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("warnview")
                .requires(source -> source.hasPermission(PunishPermissions.VIEW.getPermission()) || source.hasPermission(PunishPermissions.ADMIN.getPermission()))
                .then(BrigadierCommand.requiredArgumentBuilder("id", StringArgumentType.word()).executes(this::warnview))
                .executes(this::wrongUsage)
                .build();

        final BrigadierCommand command = new BrigadierCommand(node);
        commandManager.register(commandMeta, command);
    }

    private int wrongUsage(CommandContext<CommandSource> context) {
        final ConfigurationNode messages = judicator.getMessagesConfig();
        final CommandSource source = context.getSource();
        final TextComponent text = WarnUtils.getMessage(messages, "usages", "warnview");
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameReturnValue")
    private int warnview(CommandContext<CommandSource> context) {
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
        final TextComponent textComponent = WarnUtils.getMessageList(messages, warn, "runners", "warn-view");
        source.sendMessage(textComponent);
        return Command.SINGLE_SUCCESS;
    }

}
