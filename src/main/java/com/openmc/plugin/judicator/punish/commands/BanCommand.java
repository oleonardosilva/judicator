package com.openmc.plugin.judicator.punish.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.commons.ChatContext;
import com.openmc.plugin.judicator.punish.PunishUtils;
import com.openmc.plugin.judicator.punish.PunishmentBuilder;
import com.openmc.plugin.judicator.punish.data.cache.PunishCache;
import com.openmc.plugin.judicator.punish.handlers.PunishFactory;
import com.openmc.plugin.judicator.punish.types.PunishPermissions;
import com.openmc.plugin.judicator.punish.types.PunishType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

public class BanCommand {

    private final Judicator judicator;
    private final ProxyServer server;
    private final PunishCache processor;
    private final ConfigurationNode messages;

    public BanCommand(Judicator judicator) {
        this.judicator = judicator;
        this.server = judicator.getServer();
        this.processor = judicator.getPunishCache();
        this.messages = judicator.getMessagesConfig();
    }

    public void register() {
        final CommandManager commandManager = server.getCommandManager();
        final CommandMeta commandMeta = commandManager.metaBuilder("ban")
                .plugin(judicator)
                .build();

        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("ban")
                .requires(source -> {
                    final boolean b = source.hasPermission(PunishPermissions.BAN.getPermission()) || source.hasPermission(PunishPermissions.ADMIN.getPermission());
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
                        .executes(this::ban)
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                .executes(this::ban)
                        )
                )
                .executes(this::wrongUsage)
                .build();

        final BrigadierCommand command = new BrigadierCommand(node);
        commandManager.register(commandMeta, command);
    }

    private int wrongUsage(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final TextComponent text = PunishUtils.getMessage(messages, "usages", "ban");
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameReturnValue")
    private int ban(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final String targetName = context.getArgument("player", String.class);
        if (!judicator.getImmuneCache().canPunish(source, targetName)) return Command.SINGLE_SUCCESS;
        final String reason = context.getArguments().containsKey("reason") ? context.getArgument("reason", String.class) : "";

        final PunishmentBuilder builder = new PunishmentBuilder()
                .type(PunishType.BAN)
                .reason(reason);

        server.getPlayer(targetName).ifPresentOrElse(
                builder::target, () -> builder.target(targetName)
        );

        if (source instanceof Player player) {
            final String punisher = player.getUsername();
            builder.punisher(player);
            final Component text = PunishUtils.getConfirmationMessage(messages);
            player.sendMessage(text);

            final ChatContext<PunishmentBuilder> chatContext = ChatContext.buildPunishmentContext(punisher, builder, judicator);
            processor.putContext(punisher, chatContext);
        } else {
            new PunishFactory(judicator, builder).factory();
        }

        return Command.SINGLE_SUCCESS;
    }
}
