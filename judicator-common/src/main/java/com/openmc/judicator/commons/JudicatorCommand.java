package com.openmc.judicator.commons;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.plugin.judicator.BuildConstants;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class JudicatorCommand extends CoreCommand {

    private static final List<HelpEntry> HELP = List.of(
            new HelpEntry("/judicator reload", "Reloads the Judicator plugin configurations.", "/judicator reload"),
            new HelpEntry("/judicator help", "Displays this help message.", "/judicator help"),
            new HelpEntry("/judicator version", "Displays the current version of the Judicator plugin.", "/judicator version"),
            new HelpEntry("/punish <player> [reason]", "Punishes a player with the specified reason.", "/punish "),
            new HelpEntry("/ban <player> [reason]", "Bans a player with the specified reason.", "/ban "),
            new HelpEntry("/banip <player> [reason]", "Bans a player by IP with the specified reason.", "/banip "),
            new HelpEntry("/tempban <player> <duration> [reason]", "Temporarily bans a player for the specified duration with an optional reason.", "/tempban "),
            new HelpEntry("/tempbanip <player> <duration> [reason]", "Temporarily bans a player by IP for the specified duration with an optional reason.", "/tempbanip "),
            new HelpEntry("/mute <player> [reason]", "Mutes a player with the specified reason.", "/mute "),
            new HelpEntry("/muteip <player> [reason]", "Mutes a player by IP with the specified reason.", "/muteip "),
            new HelpEntry("/tempmute <player> <duration> [reason]", "Temporarily mutes a player for the specified duration with an optional reason.", "/tempmute "),
            new HelpEntry("/tempmuteip <player> <duration> [reason]", "Temporarily mutes a player by IP for the specified duration with an optional reason.", "/tempmuteip "),
            new HelpEntry("/warn <player> <reason>", "Warns a player with the specified reason.", "/warn "),
            new HelpEntry("/tempwarn <player> <duration> <reason>", "Temporarily warns a player for the specified duration with an optional reason.", "/tempwarn "),
            new HelpEntry("/wview <id>", "Views the details of a warn by its ID.", "/wview "),
            new HelpEntry("/whistory <player>", "Views the warn history of a player.", "/whistory "),
            new HelpEntry("/pview <id>", "Views the details of a punishment by its ID.", "/pview "),
            new HelpEntry("/phistory <player>", "Views the punishment history of a player.", "/phistory "),
            new HelpEntry("/unwarn <id>", "Deletes a warn by its ID.", "/unwarn "),
            new HelpEntry("/revoke <id>", "Revokes a punishment by its ID.", "/revoke "),
            new HelpEntry("/unban <player> [-s] [reason]", "Revokes every active ban of a player.", "/unban "),
            new HelpEntry("/unmute <player> [-s] [reason]", "Revokes every active mute of a player.", "/unmute "),
            new HelpEntry("/alts <player>", "Lists accounts that shared an IP with a player.", "/alts "),
            new HelpEntry("/blame <staff> [period] [page]", "Lists the punishments applied by a staff member, optionally only in the last period (e.g. 7:d).", "/blame "),
            new HelpEntry("/activity <period> [page]", "Shows how many punishments each staff member applied in the last period (e.g. 24:h).", "/activity "),
            new HelpEntry("/banlist [page]", "Lists the bans in force.", "/banlist"),
            new HelpEntry("/mutelist [page]", "Lists the mutes in force.", "/mutelist"),
            new HelpEntry("/judicator import <source> [confirm]", "Imports punishments from another plugin (console only).", "/judicator import ")
    );

    public JudicatorCommand(Judicator judicator) {
        super(judicator, "judicator", List.of("jud"), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final String subcommand = args.word().orElse("").toLowerCase();
        switch (subcommand) {
            case "reload" -> reload(source);
            case "help" -> help(source);
            case "version" -> source.sendMessage(Component.text("§aJudicator Version: " + BuildConstants.VERSION));
            case "import" -> importPunishments(source, args);
            default -> source.sendMessage(Component.text("§cType /judicator help for a list of commands."));
        }
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        final String prefix = args.length == 0 ? "" : args[0].toLowerCase();
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            final String source = args[1].toLowerCase();
            return judicator.getImportService().sourceNames().stream().sorted().filter(s -> s.startsWith(source)).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("import")) {
            return Stream.of("confirm").filter(s -> s.startsWith(args[2].toLowerCase())).toList();
        }
        if (args.length > 1) return List.of();
        return Stream.of("reload", "help", "version", "import").filter(s -> s.startsWith(prefix)).toList();
    }

    private void reload(JudicatorSender source) {
        judicator.reloadConfigs();
        source.sendMessage(PunishUtils.getMessage(messages(), "success", "reloaded"));
    }

    private void importPunishments(JudicatorSender source, CommandArgs args) {
        // Bulk writes to the database: only from the console, never from an in-game account.
        if (source.isPlayer()) {
            source.sendMessage(Component.text("§cRun /judicator import from the server console."));
            return;
        }
        final Optional<String> name = args.word();
        if (name.isEmpty()) {
            source.sendMessage(Component.text("§cUse: /judicator import <" + String.join("|",
                    judicator.getImportService().sourceNames().stream().sorted().toList()) + "> [confirm]"));
            return;
        }
        final boolean confirm = args.word().map(word -> word.equalsIgnoreCase("confirm")).orElse(false);
        judicator.getImportService().run(source, name.get(), confirm);
    }

    private void help(JudicatorSender source) {
        final TextComponent.Builder text = Component.text().content("§aJudicator Commands\n");
        for (HelpEntry entry : HELP) {
            text.append(Component.text("\n§3" + entry.usage())
                    .hoverEvent(Component.text("§7" + entry.description()))
                    .clickEvent(ClickEvent.suggestCommand(entry.suggestion())));
        }
        source.sendMessage(text.build());
    }

    private record HelpEntry(String usage, String description, String suggestion) {
    }

}
