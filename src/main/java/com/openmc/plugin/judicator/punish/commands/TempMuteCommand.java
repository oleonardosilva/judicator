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

public class TempMuteCommand {

    private final Judicator judicator;
    private final ProxyServer server;
    private final PunishCache cache;
    private final ConfigurationNode messages;

    public TempMuteCommand(Judicator judicator) {
        this.judicator = judicator;
        this.server = judicator.getServer();
        this.cache = judicator.getPunishCache();
        this.messages = judicator.getMessagesConfig();
    }

    public void register() {
        final CommandManager commandManager = server.getCommandManager();
        final CommandMeta commandMeta = commandManager.metaBuilder("tempmute")
                .plugin(judicator)
                .build();

        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("tempmute")
                .requires(source -> {
                    final boolean b = source.hasPermission(PunishPermissions.TEMPMUTE.getPermission()) || source.hasPermission(PunishPermissions.ADMIN.getPermission());
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
                        .then(BrigadierCommand.requiredArgumentBuilder("duration", StringArgumentType.string())
                                .then(BrigadierCommand
                                        .requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                        .executes(this::tempmute)
                                )
                                .executes(this::tempmute)
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
        final TextComponent text = PunishUtils.getMessage(messages, "usages", "tempmute");
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameReturnValue")
    private int tempmute(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final String durationStr = context.getArgument("duration", String.class);
        final String targetName = context.getArgument("player", String.class);
        if (!judicator.getImmuneCache().canPunish(source, targetName)) return Command.SINGLE_SUCCESS;
        final String reason = context.getArguments().containsKey("reason") ? context.getArgument("reason", String.class) : "";

        final PunishmentBuilder builder = new PunishmentBuilder()
                .type(PunishType.TEMPMUTE)
                .reason(reason);

        builder.duration(durationStr);

        server.getPlayer(targetName).ifPresentOrElse(
                builder::target, () -> builder.target(targetName)
        );

        if (source instanceof Player player) {
            final String punisher = player.getUsername();
            builder.punisher(player);
            final Component text = PunishUtils.getConfirmationMessage(messages);
            player.sendMessage(text);

            final ChatContext<PunishmentBuilder> chatContext = ChatContext.buildPunishmentContext(punisher, builder, judicator);
            cache.putContext(punisher, chatContext);
        } else {
            new PunishFactory(judicator, builder).factory();
        }

        return Command.SINGLE_SUCCESS;
    }

}
