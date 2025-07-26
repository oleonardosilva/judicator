package com.openmc.plugin.judicator.punish.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.commons.ChatContext;
import com.openmc.plugin.judicator.punish.AccessAddressService;
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

public class TempBanIPCommand {

    private final Judicator judicator;
    private final ProxyServer server;
    private final PunishCache cache;
    private final ConfigurationNode messages;
    private final AccessAddressService addressService;

    public TempBanIPCommand(Judicator judicator) {
        this.judicator = judicator;
        this.server = judicator.getServer();
        this.cache = judicator.getPunishCache();
        this.messages = judicator.getMessagesConfig();
        this.addressService = judicator.getAddressService();
    }

    public void register() {
        final CommandManager commandManager = server.getCommandManager();
        final CommandMeta commandMeta = commandManager.metaBuilder("tempbanip")
                .plugin(judicator)
                .build();

        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("tempbanip")
                .requires(source -> {
                    final boolean b = source.hasPermission(PunishPermissions.TEMPBANIP.getPermission()) || source.hasPermission(PunishPermissions.ADMIN.getPermission());
                    if (!b) {
                        final TextComponent text = PunishUtils.getMessage(messages, "permission-error");
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
                                        .executes(this::tempbanip)
                                )
                                .executes(this::tempbanip)
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
        final TextComponent text = PunishUtils.getMessage(messages, "usages", "tempbanip");
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameReturnValue")
    private int tempbanip(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final String durationStr = context.getArgument("duration", String.class);
        final String targetName = context.getArgument("player", String.class);
        if (!judicator.getImmuneCache().canPunish(source, targetName)) return Command.SINGLE_SUCCESS;
        final String reason = context.getArguments().containsKey("reason") ? context.getArgument("reason", String.class) : "";

        final PunishmentBuilder builder = new PunishmentBuilder()
                .type(PunishType.TEMPBAN)
                .reason(reason);

        builder.duration(durationStr);

        server.getPlayer(targetName).ifPresentOrElse(
                player -> builder.target(player).ipAddress(player.getRemoteAddress().getAddress().getHostAddress())
                , () -> {
                    builder.target(targetName);
                    addressService.findByUsername(targetName)
                            .ifPresentOrElse(accessAddress -> builder.ipAddress(accessAddress.getHostAddress()), () -> {
                                final TextComponent text = PunishUtils.getMessage(messages, "player-ip-not-found");
                                source.sendMessage(text);
                            });
                }
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
