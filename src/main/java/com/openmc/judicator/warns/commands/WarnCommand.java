package com.openmc.judicator.warns.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.warns.WarnBuilder;
import com.openmc.judicator.warns.WarnUtils;
import com.openmc.judicator.warns.handlers.WarnHandler;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

public class WarnCommand {

    private final Judicator judicator;
    private final ProxyServer server;

    public WarnCommand(Judicator judicator) {
        this.judicator = judicator;
        this.server = judicator.getServer();
    }

    public void register() {
        final CommandManager commandManager = server.getCommandManager();
        final CommandMeta commandMeta = commandManager.metaBuilder("warn")
                .plugin(judicator)
                .build();

        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("warn")
                .requires(source -> source.hasPermission(PunishPermissions.WARN.getPermission()) || source.hasPermission(PunishPermissions.ADMIN.getPermission()))
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            final String input = builder.getRemaining().toLowerCase();
                            server.matchPlayer(input).forEach(player -> builder.suggest(player.getUsername()));
                            return builder.buildFuture();
                        })
                        .executes(this::wrongUsage)
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                .executes(this::warn)
                        )
                )
                .executes(this::wrongUsage)
                .build();

        final BrigadierCommand command = new BrigadierCommand(node);
        commandManager.register(commandMeta, command);
    }

    private int wrongUsage(CommandContext<CommandSource> context) {
        final ConfigurationNode messages = judicator.getMessagesConfig();
        final CommandSource source = context.getSource();
        final TextComponent text = WarnUtils.getMessage(messages, "usages", "warn");
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameReturnValue")
    private int warn(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final String targetName = context.getArgument("player", String.class);
        if (!judicator.getImmuneCache().canPunish(source, targetName)) return Command.SINGLE_SUCCESS;
        final String reason = context.getArgument("reason", String.class);

        final WarnBuilder builder = new WarnBuilder()
                .reason(reason);

        server.getPlayer(targetName).ifPresentOrElse(
                builder::target, () -> builder.target(targetName)
        );

        if (source instanceof Player player) {
            builder.punisher(player);
        }

        new WarnHandler(judicator, builder).handle();

        return Command.SINGLE_SUCCESS;
    }
}
