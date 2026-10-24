package com.openmc.judicator.commons;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.plugin.judicator.BuildConstants;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@code /judicator help [category]}: the categories, then the commands of one. Texts come from the {@code help}
 * section of the messages ({@code help.commands.<command name>}, {@code judicator-<subcommand>} for /judicator's own).
 * Only the commands the sender can use are listed. A player hovers a command for its usage and clicks it to have it
 * written in the chat box; the console, which has neither, reads the usage on the line.
 */
public final class HelpMenu {

    /**
     * Category -> command names, in the order they are shown.
     */
    public static final Map<String, List<String>> CATEGORIES = categories();

    /**
     * The arguments of a usage: from the first space followed by ( or [.
     */
    private static final Pattern ARGUMENTS = Pattern.compile(" [(\\[].*");

    private final Judicator judicator;

    public HelpMenu(Judicator judicator) {
        this.judicator = judicator;
    }

    private static Map<String, List<String>> categories() {
        final Map<String, List<String>> categories = new LinkedHashMap<>();
        categories.put("punish", List.of("punish", "ban", "tempban", "banip", "tempbanip", "mute", "tempmute", "muteip",
                "tempmuteip", "softmute", "tempsoftmute", "kick", "warn", "tempwarn"));
        categories.put("revoke", List.of("unban", "unmute", "revoke", "unwarn", "extend", "changereason"));
        categories.put("lookup", List.of("jmenu", "playerinfo", "punishhistory", "punishview", "warnhistory", "warnview", "alts",
                "banlist", "mutelist"));
        categories.put("notes", List.of("note", "notes", "delnote"));
        categories.put("reports", List.of("report", "reports"));
        categories.put("staff", List.of("blame", "activity", "staffrollback"));
        categories.put("admin", List.of("judicator-reload", "judicator-version", "judicator-import", "judicator-clear"));
        return categories;
    }

    /**
     * @param category empty for the list of categories
     */
    public void show(JudicatorSender source, Optional<String> category, ConfigurationNode messages) {
        if (category.isEmpty()) {
            source.sendMessage(categoryList(source, messages));
            return;
        }
        final String key = category.get().toLowerCase(Locale.ROOT);
        final List<String> commands = CATEGORIES.containsKey(key) ? usable(source, key) : List.of();
        if (commands.isEmpty()) {
            source.sendMessage(PunishUtils.format(messages, Map.of("category", category.get()), "help", "unknown-category"));
            source.sendMessage(categoryList(source, messages));
            return;
        }
        final TextComponent.Builder text = Component.text()
                .append(PunishUtils.format(messages, Map.of("name", name(messages, key)), "help", "category-title"));
        for (String command : commands) {
            text.append(commandLine(source, messages, command));
        }
        text.append(PunishUtils.format(messages, Map.of(), "help", "back")
                .hoverEvent(HoverEvent.showText(PunishUtils.format(messages, Map.of(), "help", "back-hover")))
                .clickEvent(ClickEvent.runCommand("/judicator help")));
        source.sendMessage(text.build());
    }

    /**
     * @return the categories with at least one command the sender can use
     */
    public List<String> categoriesFor(JudicatorSender source) {
        return CATEGORIES.keySet().stream().filter(category -> !usable(source, category).isEmpty()).toList();
    }

    private Component categoryList(JudicatorSender source, ConfigurationNode messages) {
        final TextComponent.Builder text = Component.text()
                .append(PunishUtils.format(messages, Map.of("version", BuildConstants.VERSION), "help", "title"));
        for (String category : categoriesFor(source)) {
            final List<String> commands = usable(source, category);
            final Map<String, String> placeholders = Map.of(
                    "name", name(messages, category),
                    "description", messages.node("help", "categories", category, "description").getString("").replace("&", "§"),
                    "count", String.valueOf(commands.size()),
                    "commands", String.join("§7, §f", commands.stream().map(command -> bare(usage(messages, command))).toList()));
            text.append(PunishUtils.format(messages, placeholders, "help", "category")
                    .hoverEvent(HoverEvent.showText(PunishUtils.format(messages, placeholders, "help", "category-hover")))
                    .clickEvent(ClickEvent.runCommand("/judicator help " + category)));
        }
        return text.append(PunishUtils.format(messages, Map.of(), "help", "hint")).build();
    }

    private Component commandLine(JudicatorSender source, ConfigurationNode messages, String command) {
        final String usage = usage(messages, command);
        final Map<String, String> placeholders = Map.of(
                "command", source.isPlayer() ? bare(usage) : usage,
                "usage", usage,
                "description", messages.node("help", "commands", command, "description").getString("").replace("&", "§"));
        final boolean takesArguments = !bare(usage).equals(usage);
        return PunishUtils.format(messages, placeholders, "help", "command")
                .hoverEvent(HoverEvent.showText(PunishUtils.format(messages, placeholders, "help", "command-hover")))
                .clickEvent(ClickEvent.suggestCommand(bare(usage) + (takesArguments ? " " : "")));
    }

    private List<String> usable(JudicatorSender source, String category) {
        return CATEGORIES.get(category).stream().filter(command -> canUse(source, command)).toList();
    }

    private boolean canUse(JudicatorSender source, String command) {
        final String name = command.startsWith("judicator-") ? "judicator" : command;
        return judicator.getCommands().stream()
                .filter(registered -> registered.getName().equals(name))
                .anyMatch(registered -> registered.canUse(source));
    }

    private static String name(ConfigurationNode messages, String category) {
        return messages.node("help", "categories", category, "name").getString(category).replace("&", "§");
    }

    private static String usage(ConfigurationNode messages, String command) {
        return messages.node("help", "commands", command, "usage").getString("/" + command);
    }

    /**
     * @return the usage without its arguments: {@code /ban (player) [reason]} -> {@code /ban}
     */
    static String bare(String usage) {
        return ARGUMENTS.matcher(usage).replaceFirst("");
    }
}
