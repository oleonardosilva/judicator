package com.openmc.judicator.commons;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.plugin.judicator.BuildConstants;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class JudicatorCommand extends CoreCommand {

    private final HelpMenu helpMenu;

    public JudicatorCommand(Judicator judicator) {
        super(judicator, "judicator", List.of("jud"), PunishPermissions.ADMIN.getPermission());
        this.helpMenu = new HelpMenu(judicator);
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final String subcommand = args.word().orElse("").toLowerCase();
        switch (subcommand) {
            case "reload" -> reload(source);
            case "", "help" -> helpMenu.show(source, args.word(), messages(source));
            case "version" -> source.sendMessage(Component.text("§aJudicator Version: " + BuildConstants.VERSION));
            case "import" -> importPunishments(source, args);
            case "clear" -> clear(source, args);
            default -> source.sendMessage(Component.text("§cType /judicator help for a list of commands."));
        }
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        final String prefix = args.length == 0 ? "" : args[0].toLowerCase();
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            return helpMenu.categoriesFor(sender).stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            final String source = args[1].toLowerCase();
            return judicator.getImportService().sourceNames().stream().sorted().filter(s -> s.startsWith(source)).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("import")) {
            return Stream.of("confirm").filter(s -> s.startsWith(args[2].toLowerCase())).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("clear")) {
            return Stream.of("confirm").filter(s -> s.startsWith(args[2].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clear")) return super.suggest(sender, new String[]{args[1]});
        if (args.length > 1) return List.of();
        return Stream.of("reload", "help", "version", "import", "clear").filter(s -> s.startsWith(prefix)).toList();
    }

    private void reload(JudicatorSender source) {
        judicator.reloadConfigs();
        source.sendMessage(PunishUtils.getMessage(messages(source), "success", "reloaded"));
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

    /**
     * Deletes everything stored about a player (a data deletion request). Console only, like the import.
     */
    private void clear(JudicatorSender source, CommandArgs args) {
        if (source.isPlayer()) {
            source.sendMessage(Component.text("§cRun /judicator clear from the server console."));
            return;
        }
        final Optional<String> name = args.word();
        if (name.isEmpty()) {
            source.sendMessage(Component.text("§cUse: /judicator clear <player> [confirm]"));
            return;
        }
        final boolean confirm = args.word().map(word -> word.equalsIgnoreCase("confirm")).orElse(false);
        judicator.getDataRetentionService().clear(source, name.get(), confirm);
    }

}
