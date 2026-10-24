package com.openmc.judicator;

import com.openmc.judicator.commons.db.Dialect;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The files copied to a new server parse and hold what the code reads. The tests' {@code TestServer} writes its own
 * {@code discord.yml}, {@code immune.yml} and uses no {@code data.yml}, so these are checked here.
 */
class BundledConfigTest {

    private static ConfigurationNode load(String name) throws IOException {
        final URL resource = Judicator.class.getResource("/" + name);
        assertNotNull(resource, name);
        return YamlConfigurationLoader.builder().url(resource).build().load();
    }

    @Test
    void everyBundledFileParses() throws IOException {
        // immune.yml is left out: it only has comments and an empty 'players:' (see immune_exemptsNobodyByDefault).
        for (String name : List.of("config.yml", "data.yml", "discord.yml", "import.yml",
                "messages_en.yml", "messages_es.yml", "messages_pt.yml")) {
            assertFalse(load(name).empty(), name);
        }
    }

    @Test
    void discord_isOffUntilAUrlIsGiven_andEveryKindHasAnEvent() throws IOException {
        final ConfigurationNode discord = load("discord.yml");
        assertFalse(discord.node("enabled").getBoolean(true));
        assertEquals("", discord.node("webhook-url").getString());
        for (String event : List.of("ban", "tempban", "mute", "tempmute", "kick", "warn")) {
            assertTrue(discord.node("events", event, "enabled").getBoolean(false), event);
            assertTrue(discord.node("events", event, "webhook-url").virtual(), event + " uses the shared webhook-url");
        }
    }

    @Test
    void data_usesSqliteTheDefaultPortOfTheTypeAndASizedPool() throws IOException {
        final ConfigurationNode database = load("data.yml").node("database");
        assertEquals(Dialect.SQLITE, Dialect.fromConfig(database.node("type").getString()));
        assertEquals("judicator.db", database.node("file").getString());
        assertEquals("", database.node("port").getString());
        assertTrue(database.node("properties").childrenMap().isEmpty());
        assertEquals(10, database.node("pool", "maximum-size").getInt());
    }

    @Test
    void immune_exemptsNobodyByDefault() throws IOException {
        final ConfigurationNode immune = load("immune.yml");
        assertTrue(immune.node("users").virtual());
        assertTrue(immune.node("players").childrenMap().isEmpty());
    }
}
