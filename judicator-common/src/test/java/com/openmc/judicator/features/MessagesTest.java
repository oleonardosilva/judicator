package com.openmc.judicator.features;

import com.openmc.judicator.commons.HelpMenu;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.PlatformType;
import com.openmc.judicator.testing.TestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three language files must offer the same messages, and every command must have its usage text.
 */
class MessagesTest {

    private static final List<String> LANGUAGES = List.of("en", "es", "pt");

    @TempDir
    Path folder;

    private static ConfigurationNode load(String language) throws Exception {
        return YamlConfigurationLoader.builder().url(MessagesTest.class.getResource("/messages_" + language + ".yml")).build().load();
    }

    private static void collect(ConfigurationNode node, String path, Set<String> keys) {
        if (node.childrenMap().isEmpty()) {
            keys.add(path);
            return;
        }
        node.childrenMap().forEach((key, child) -> collect(child, path.isEmpty() ? key.toString() : path + "." + key, keys));
    }

    @Test
    void everyLanguageHasTheSameKeys() throws Exception {
        final Set<String> english = new TreeSet<>();
        collect(load("en"), "", english);
        for (String language : LANGUAGES) {
            final Set<String> keys = new TreeSet<>();
            collect(load(language), "", keys);
            assertEquals(english, keys, "messages_" + language + ".yml");
        }
        assertTrue(english.size() > 100, "loaded " + english.size());
    }

    @Test
    void yesAndNoAreTextKeys() throws Exception {
        // Unquoted yes/no keys are booleans in YAML 1.1, so node("yes") would never find them.
        for (String language : LANGUAGES) {
            final ConfigurationNode messages = load(language);
            assertTrue(messages.node("yes").getString() != null, "\"yes\" in messages_" + language + ".yml");
            assertTrue(messages.node("no").getString() != null, "\"no\" in messages_" + language + ".yml");
        }
    }

    @Test
    void everyCommandHasAUsageMessageInEveryLanguage() throws Exception {
        final Set<String> names = new TreeSet<>();
        try (TestServer server = TestServer.in(folder).platform(PlatformType.BUKKIT).start()) {
            server.judicator.getCommands().stream().map(CoreCommand::getName)
                    .filter(name -> !name.equals("judicator"))
                    .forEach(names::add);
        }
        for (String language : LANGUAGES) {
            final ConfigurationNode usages = load(language).node("usages");
            for (String name : names) {
                assertTrue(usages.node(name).getString("").contains("/"), "usages." + name + " in messages_" + language + ".yml");
            }
        }
    }

    @Test
    void everyCommandIsInTheHelpWithItsTextsInEveryLanguage() throws Exception {
        final Set<String> listed = new TreeSet<>();
        HelpMenu.CATEGORIES.values().forEach(listed::addAll);
        try (TestServer server = TestServer.in(folder).platform(PlatformType.BUKKIT).start()) {
            server.judicator.getCommands().stream().map(CoreCommand::getName)
                    .filter(name -> !name.equals("judicator"))
                    .forEach(name -> assertTrue(listed.contains(name), name + " is in no category of HelpMenu"));
        }
        for (String language : LANGUAGES) {
            final ConfigurationNode help = load(language).node("help");
            for (String category : HelpMenu.CATEGORIES.keySet()) {
                assertFalse(help.node("categories", category, "name").virtual(), "help.categories." + category + " in messages_" + language + ".yml");
            }
            for (String command : listed) {
                assertTrue(help.node("commands", command, "usage").getString("").startsWith("/"), "help.commands." + command + ".usage in messages_" + language + ".yml");
                assertFalse(help.node("commands", command, "description").getString("").isBlank(), "help.commands." + command + ".description in messages_" + language + ".yml");
            }
        }
    }
}
