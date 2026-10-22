package com.openmc.judicator.importer;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakeConfig;
import com.openmc.judicator.testing.FakePlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static com.openmc.judicator.testing.FakeConfig.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaImporterTest {

    @TempDir
    Path folder;

    private ImportResult read() throws Exception {
        final ImportContext context = new ImportContext(new Judicator(FakePlatform.create(folder, true)),
                FakeConfig.of(map("vanilla", map("directory", folder.toString()))));
        return new VanillaImporter().read(context);
    }

    @Test
    void importsPlayerAndAddressBans() throws Exception {
        Files.writeString(folder.resolve("banned-players.json"), """
                [
                  {"uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "name": "Notch", "created": "2024-01-15 10:30:00 +0000",
                   "source": "Server", "expires": "forever", "reason": "Banned by an operator."},
                  {"uuid": "not-a-uuid", "name": "Temp", "created": "2024-01-15 10:30:00 -0300",
                   "source": "Admin", "expires": "2030-01-01 00:00:00 +0000", "reason": "spam"},
                  {"uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf6", "name": "BadDate", "created": "yesterday",
                   "source": "Admin", "expires": "forever", "reason": "x"}
                ]""", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("banned-ips.json"), """
                [
                  {"ip": "10.0.0.5", "created": "2024-02-01 00:00:00 +0000", "source": "(Unknown)", "expires": "forever", "reason": "bots"},
                  {"ip": "<unknown>", "created": "2024-02-01 00:00:00 +0000", "source": "Server", "expires": "forever", "reason": "?"}
                ]""", StandardCharsets.UTF_8);

        final ImportResult result = read();
        final List<Punishment> punishments = result.getPunishments();
        assertEquals(3, punishments.size());

        final Punishment notch = punishments.get(0);
        assertEquals("Notch", notch.getNickname());
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), notch.getPlayerUUID().orElseThrow());
        assertEquals(PunishType.BAN, notch.getType());
        assertEquals("Console", notch.getPunisher());
        assertEquals(LocalDateTime.ofInstant(ZonedDateTime.parse("2024-01-15T10:30:00Z").toInstant(), ZoneId.systemDefault()),
                notch.getStartedAt());

        final Punishment temp = punishments.get(1);
        assertEquals(PunishType.TEMPBAN, temp.getType());
        // Invalid UUID on an online-mode server: enforced by name only.
        assertTrue(temp.getPlayerUUID().isEmpty());
        assertEquals("Admin", temp.getPunisher());

        final Punishment address = punishments.get(2);
        assertEquals("10.0.0.5", address.getIpAddress().orElseThrow());
        // Never a real account: an address-only ban must not ban a player by name.
        assertEquals("10.0.0.5", address.getNickname());
        assertEquals("Unknown", address.getPunisher());

        assertEquals(2, result.getSkipped().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void missingFilesAreReported() {
        assertThrows(ImportException.class, this::read);
    }
}
