package com.openmc.judicator;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.spongepowered.configurate.ConfigurationNode;

public class JudicatorCommand {

    private final Judicator judicator;
    private final ProxyServer server;
    private final ConfigurationNode messages;

    public JudicatorCommand(Judicator judicator) {
        this.judicator = judicator;
        this.server = judicator.getServer();
        this.messages = judicator.getMessagesConfig();
    }

    public void register() {
        final CommandManager commandManager = server.getCommandManager();
        final CommandMeta commandMeta = commandManager.metaBuilder("judicator")
                .aliases("jud")
                .plugin(judicator)
                .build();

        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("judicator")
                .requires(source -> source.hasPermission(PunishPermissions.ADMIN.getPermission()))
                .then(BrigadierCommand.literalArgumentBuilder("reload")
                        .executes(this::reload)
                )
                .then(BrigadierCommand.literalArgumentBuilder("help")
                        .executes(this::help)
                )
                .then(BrigadierCommand.literalArgumentBuilder("version")
                        .executes(this::version)
                )
                .executes(this::wrongUsage)
                .build();

        final BrigadierCommand command = new BrigadierCommand(node);
        commandManager.register(commandMeta, command);
    }

    private int wrongUsage(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final TextComponent text = Component.text("§cType /judicator help for a list of commands.");
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameReturnValue")
    private int reload(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        judicator.reloadConfigs();
        final TextComponent text = PunishUtils.getMessage(messages, "success", "reloaded");
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    private int version(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final String version = judicator.getContainer().getDescription().getVersion().orElse("unknown");
        final TextComponent text = Component.text("§aJudicator Version: " + version);
        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    private int help(CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final TextComponent text = Component
                .text("§aJudicator Commands\n")
                .append(
                        Component.text("\n§3/judicator reload")
                                .hoverEvent(Component.text("§7Reloads the Judicator plugin configurations."))
                                .clickEvent(ClickEvent.suggestCommand("/judicator reload"))
                )
                .append(
                        Component.text("\n§3/judicator help")
                                .hoverEvent(Component.text("§7Displays this help message."))
                                .clickEvent(ClickEvent.suggestCommand("/judicator help"))
                )
                .append(
                        Component.text("\n§3/judicator version")
                                .hoverEvent(Component.text("§7Displays the current version of the Judicator plugin."))
                                .clickEvent(ClickEvent.suggestCommand("/judicator version"))
                )
                .append(
                        Component.text("\n§3/punish <player> [reason]")
                                .hoverEvent(Component.text("§7Punishes a player with the specified reason."))
                                .clickEvent(ClickEvent.suggestCommand("/punish "))
                )
                .append(
                        Component.text("\n§3/ban <player> [reason]")
                                .hoverEvent(Component.text("§7Bans a player with the specified reason."))
                                .clickEvent(ClickEvent.suggestCommand("/ban "))
                )
                .append(
                        Component.text("\n§3/banip <player> [reason]")
                                .hoverEvent(Component.text("§7Bans a player by IP with the specified reason."))
                                .clickEvent(ClickEvent.suggestCommand("/banip "))
                )
                .append(
                        Component.text("\n§3/tempban <player> <duration> [reason]")
                                .hoverEvent(Component.text("§7Temporarily bans a player for the specified duration with an optional reason."))
                                .clickEvent(ClickEvent.suggestCommand("/tempban "))
                )
                .append(
                        Component.text("\n§3/tempbanip <player> <duration> [reason]")
                                .hoverEvent(Component.text("§7Temporarily bans a player by IP for the specified duration with an optional reason."))
                                .clickEvent(ClickEvent.suggestCommand("/tempbanip "))
                )
                .append(
                        Component.text("\n§3/mute <player> [reason]")
                                .hoverEvent(Component.text("§7Mutes a player with the specified reason."))
                                .clickEvent(ClickEvent.suggestCommand("/mute "))
                )
                .append(
                        Component.text("\n§3/muteip <player> [reason]")
                                .hoverEvent(Component.text("§7Mutes a player by IP with the specified reason."))
                                .clickEvent(ClickEvent.suggestCommand("/muteip "))
                )
                .append(
                        Component.text("\n§3/tempmute <player> <duration> [reason]")
                                .hoverEvent(Component.text("§7Temporarily mutes a player for the specified duration with an optional reason."))
                                .clickEvent(ClickEvent.suggestCommand("/tempmute "))
                )
                .append(
                        Component.text("\n§3/tempmuteip <player> <duration> [reason]")
                                .hoverEvent(Component.text("§7Temporarily mutes a player by IP for the specified duration with an optional reason."))
                                .clickEvent(ClickEvent.suggestCommand("/tempmuteip "))
                )
                .append(
                        Component.text("\n§3/warn <player> <reason>")
                                .hoverEvent(Component.text("§7Warns a player with the specified reason."))
                                .clickEvent(ClickEvent.suggestCommand("/warn "))
                )
                .append(
                        Component.text("\n§3/tempwarn <player> <duration> <reason>")
                                .hoverEvent(Component.text("§7Temporarily warns a player for the specified duration with an optional reason."))
                                .clickEvent(ClickEvent.suggestCommand("/tempwarn "))
                )
                .append(
                        Component.text("\n§3/wview <id>")
                                .hoverEvent(Component.text("§7Views the details of a warn by its ID."))
                                .clickEvent(ClickEvent.suggestCommand("/wview "))
                )
                .append(
                        Component.text("\n§3/whistory <player>")
                                .hoverEvent(Component.text("§7Views the warn history of a player."))
                                .clickEvent(ClickEvent.suggestCommand("/whistory "))
                )
                .append(
                        Component.text("\n§3/pview <id>")
                                .hoverEvent(Component.text("§7Views the details of a punishment by its ID."))
                                .clickEvent(ClickEvent.suggestCommand("/pview "))
                )
                .append(
                        Component.text("\n§3/phistory <player>")
                                .hoverEvent(Component.text("§7Views the punishment history of a player."))
                                .clickEvent(ClickEvent.suggestCommand("/phistory "))
                )
                .append(
                        Component.text("\n§3/unwarn <id>")
                                .hoverEvent(Component.text("§7Deletes a warn by its ID."))
                                .clickEvent(ClickEvent.suggestCommand("/unwarn "))

                )
                .append(
                        Component.text("\n§3/revoke <id>")
                                .hoverEvent(Component.text("§7Revokes a punishment by its ID."))
                                .clickEvent(ClickEvent.suggestCommand("/revoke "))

                );

        source.sendMessage(text);
        return Command.SINGLE_SUCCESS;
    }
}