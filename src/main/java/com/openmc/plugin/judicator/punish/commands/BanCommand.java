package com.openmc.plugin.judicator.punish.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.commons.ChatContext;
import com.openmc.plugin.judicator.punish.PunishmentBuilder;
import com.openmc.plugin.judicator.punish.data.cache.PunishCache;
import com.openmc.plugin.judicator.punish.handlers.BanHandler;
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
                .aliases("banir")
                .plugin(this)
                .build();

        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("ban")
                .requires(source -> {
                    final boolean b = source.hasPermission(PunishPermissions.BAN.getPermission());
                    if (!b) {
                        final TextComponent text = Component.text(messages.node("permission-error").getString(""));
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
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                .executes(this::ban)
                        )
                        .executes(this::wrongUsage)
                )
                .executes(this::wrongUsage)
                .build();

        final BrigadierCommand command = new BrigadierCommand(node);
        commandManager.register(commandMeta, command);
    }

    private int wrongUsage(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final TextComponent text = Component.text(judicator.getMessagesConfig().node("usages.ban").getString(""));
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameReturnValue")
    private int ban(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final String targetName = context.getArgument("player", String.class);
        if (!judicator.getImmuneCache().canPunish(source, targetName)) return Command.SINGLE_SUCCESS;
        final String reason = context.getArgument("reason", String.class);

        final PunishmentBuilder builder = new PunishmentBuilder()
                .type(PunishType.BAN)
                .reason(reason);

        server.getPlayer(targetName).ifPresentOrElse(
                builder::target, () -> builder.target(targetName)
        );
        if (source instanceof Player player) {
            final String punisher = player.getUsername();
            builder.punisher(player);
            final TextComponent text = Component.text(judicator.getMessagesConfig().node("write-evidences").getString(""));
            player.sendMessage(text);

            final ChatContext<PunishmentBuilder> chatContext = new ChatContext<>();
            chatContext.setOperator(punisher);
            chatContext.setValue(builder);
            chatContext.setCallback((currentBuilder, event) -> {
                final String message = event.getMessage();
                final String readyPrompt = messages.node("ready-prompt").getString("");
                final String cancelPrompt = messages.node("cancel-prompt").getString("");
                if (message.equalsIgnoreCase(readyPrompt)) {
                    processor.removeContext(currentBuilder.getPunisher());
                    new BanHandler(judicator, builder).handle();
                    return;
                }
                if (message.equalsIgnoreCase(cancelPrompt)) {
                    final TextComponent cancelMessage = Component.text(messages.node("operation-cancel").getString(""));
                    event.getPlayer().sendMessage(cancelMessage);
                    processor.removeContext(currentBuilder.getPunisher());
                    return;
                }

                currentBuilder.appendEvidences(message);
                final TextComponent cancelMessage = Component.text(messages.node("next-link").getString(""));
                event.getPlayer().sendMessage(cancelMessage);

            });

            processor.putContext(punisher, chatContext);
        } else {
            new BanHandler(judicator, builder).handle();
        }

        return Command.SINGLE_SUCCESS;
    }

}
