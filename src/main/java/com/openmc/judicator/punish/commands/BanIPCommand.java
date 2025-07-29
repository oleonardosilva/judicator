package com.openmc.judicator.punish.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.ChatContext;
import com.openmc.judicator.punish.AccessAddressService;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.data.cache.PunishCache;
import com.openmc.judicator.punish.handlers.PunishFactory;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.punish.types.PunishType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

public class BanIPCommand {

    private final Judicator judicator;
    private final ProxyServer server;
    private final PunishCache processor;
    private final ConfigurationNode messages;
    private final AccessAddressService addressService;

    public BanIPCommand(Judicator judicator) {
        this.judicator = judicator;
        this.server = judicator.getServer();
        this.processor = judicator.getPunishCache();
        this.messages = judicator.getMessagesConfig();
        this.addressService = judicator.getAddressService();
    }

    public void register() {
        final CommandManager commandManager = server.getCommandManager();
        final CommandMeta commandMeta = commandManager.metaBuilder("banip")
                .plugin(judicator)
                .build();

        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("banip")
                .requires(source -> {
                    final boolean b = source.hasPermission(PunishPermissions.BANIP.getPermission()) || source.hasPermission(PunishPermissions.ADMIN.getPermission());
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
                        .executes(this::banip)
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                .executes(this::banip)
                        )
                )
                .executes(this::wrongUsage)
                .build();

        final BrigadierCommand command = new BrigadierCommand(node);
        commandManager.register(commandMeta, command);
    }

    private int wrongUsage(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final TextComponent text = PunishUtils.getMessage(messages, "usages", "banip");
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameReturnValue")
    private int banip(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final String targetName = context.getArgument("player", String.class);
        if (!judicator.getImmuneCache().canPunish(source, targetName)) return Command.SINGLE_SUCCESS;
        final String reason = context.getArguments().containsKey("reason") ? context.getArgument("reason", String.class) : "";

        final PunishmentBuilder builder = new PunishmentBuilder()
                .type(PunishType.BAN)
                .reason(reason);

        server.getPlayer(targetName).ifPresentOrElse(
                player -> builder.target(player).ipAddress(player.getRemoteAddress().getAddress().getHostAddress())
                , () -> {
                    builder.target(targetName);
                    addressService.findByUsername(targetName)
                            .ifPresentOrElse(accessAddress -> builder.ipAddress(accessAddress.getHostAddress()), () -> {
                                final TextComponent text = PunishUtils.getMessage(messages, "error", "player-ip-not-found");
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
            processor.putContext(punisher, chatContext);
        } else {
            new PunishFactory(judicator, builder).factory();
        }

        return Command.SINGLE_SUCCESS;
    }
}
