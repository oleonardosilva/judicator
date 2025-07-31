package com.openmc.judicator.punish.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.warns.WarnUtils;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Optional;

public class KickCommand {

    private final Judicator judicator;
    private final ProxyServer server;

    public KickCommand(Judicator judicator) {
        this.judicator = judicator;
        this.server = judicator.getServer();
    }

    public void register() {
        final CommandManager commandManager = server.getCommandManager();
        final CommandMeta commandMeta = commandManager.metaBuilder("kick")
                .plugin(judicator)
                .build();

        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("kick")
                .requires(source -> source.hasPermission(PunishPermissions.KICK.getPermission()) || source.hasPermission(PunishPermissions.ADMIN.getPermission()))
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            final String input = builder.getRemaining().toLowerCase();
                            server.matchPlayer(input).forEach(player -> builder.suggest(player.getUsername()));
                            return builder.buildFuture();
                        })
                        .executes(this::kick)
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                .executes(this::kick)
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
        final TextComponent text = PunishUtils.getMessage(messages, "usages", "kick");
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameReturnValue")
    private int kick(CommandContext<CommandSource> context) {
        final ConfigurationNode messages = judicator.getMessagesConfig();
        final CommandSource source = context.getSource();
        final String targetName = context.getArgument("player", String.class);
        if (!judicator.getImmuneCache().canPunish(source, targetName)) return Command.SINGLE_SUCCESS;
        final String reason = context.getArguments().containsKey("reason") ? context.getArgument("reason", String.class) : "None";

        final Optional<Player> optPlayer = server.getPlayer(targetName);
        if (optPlayer.isEmpty()) {
            final TextComponent text = WarnUtils.getMessage(messages, "error", "player-offline");
            source.sendMessage(text);
            return Command.SINGLE_SUCCESS;
        }

        final String punisher = source instanceof Player ? ((Player) source).getUsername() : "Console";

        final Player player = optPlayer.get();
        server.getScheduler().buildTask(judicator,
                () -> {
                    final TextComponent kickMessage = Component.text(PunishUtils.getMessageList(messages, "runners", "kick").content()
                            .replace("{nickname}", targetName)
                            .replace("{author}", punisher)
                            .replace("{reason}", reason));

                    if (judicator.getConfig().node("announce").getBoolean(true)) {
                        player.getCurrentServer().ifPresent(serverConnection -> {
                            final TextComponent announcement = Component.text(PunishUtils.getMessageList(messages, "announcements", "kick").content()
                                    .replace("{nickname}", targetName)
                                    .replace("{author}", punisher)
                                    .replace("{reason}", reason));

                            serverConnection.getServer().sendMessage(announcement);
                        });
                    }
                    player.disconnect(kickMessage);
                }
        ).schedule();

        return Command.SINGLE_SUCCESS;
    }
}
