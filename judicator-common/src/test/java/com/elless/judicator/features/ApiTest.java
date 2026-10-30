package com.elless.judicator.features;

import com.elless.judicator.api.ActionResult;
import com.elless.judicator.api.ActionStatus;
import com.elless.judicator.api.JudicatorApi;
import com.elless.judicator.api.JudicatorProvider;
import com.elless.judicator.api.punishment.PunishmentRequest;
import com.elless.judicator.api.punishment.PunishmentStatus;
import com.elless.judicator.api.punishment.PunishmentType;
import com.elless.judicator.api.punishment.PunishmentView;
import com.elless.judicator.api.warn.WarnRequest;
import com.elless.judicator.api.warn.WarnView;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import com.elless.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * judicator-api: other plugins apply and look up punishments through the same path as the commands.
 */
class ApiTest {

    private static final String PLUGIN = "AntiCheat";

    @TempDir
    Path folder;

    private TestServer server;

    private TestServer start(TestServer.Builder builder) {
        server = builder.start();
        return server;
    }

    private JudicatorApi api() {
        if (server == null) start(TestServer.in(folder));
        return server.judicator.getApi();
    }

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private List<Punishment> active(String name, PunishType... types) {
        return server.punishments.findAllActiveByUsernameAndTypes(name, types);
    }

    @Test
    void provider_givesTheRunningApiAndFailsClearlyOnceDisabled() {
        final JudicatorApi api = api();
        assertSame(api, JudicatorProvider.get());
        assertSame(api, JudicatorProvider.find().orElseThrow());

        server.close();
        server = null;

        final IllegalStateException error = assertThrows(IllegalStateException.class, JudicatorProvider::get);
        assertTrue(error.getMessage().contains("Judicator"), error.getMessage());
        assertTrue(JudicatorProvider.find().isEmpty());
    }

    @Test
    void apiBan_doesWhatTheConsoleCommandDoes() {
        final JudicatorApi api = api();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer bystander = server.online(new FakePlayer("Bystander", "10.0.0.2"));
        final FakePlayer staff = server.online(new FakePlayer("Helper", "10.0.0.3", "judicator.notify"));

        final ActionResult<PunishmentView> result = api.ban("Steve", "fly hack", PLUGIN).join();

        assertEquals(ActionStatus.SUCCESS, result.status());
        final PunishmentView ban = result.value().orElseThrow();
        assertEquals(PunishmentType.BAN, ban.type());
        assertEquals(PLUGIN, ban.punisher());
        assertEquals("fly hack", ban.reason());
        assertEquals(steve.getUniqueId(), ban.targetUniqueId().orElseThrow());
        assertTrue(ban.endsAt().isEmpty());
        assertTrue(ban.permanent());
        assertEquals(PunishmentStatus.ACTIVE, ban.status());

        final Punishment stored = active("Steve", PunishType.BAN).get(0);
        assertEquals(ban.id(), stored.getId());
        assertEquals(PLUGIN, stored.getPunisher());
        assertTrue(steve.wasKicked());
        assertTrue(steve.kickMessage().contains("You have been banned from the server."), steve.kickMessage());
        assertTrue(steve.kickMessage().contains("Reason: fly hack"), steve.kickMessage());
        assertTrue(bystander.received("User Steve was banned by AntiCheat."), bystander.messages().toString());
        assertTrue(staff.received("[Staff] AntiCheat applied BAN to Steve: fly hack"), staff.messages().toString());

        server.quit(steve);
        assertTrue(server.join(new FakePlayer("Steve", "10.0.0.1")).isPresent());
    }

    @Test
    void apiTempmute_mutesForTheDurationAndAlertsThePlayer() {
        final JudicatorApi api = api();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final Instant before = Instant.now();

        final PunishmentView mute = api.tempmute("Steve", Duration.ofHours(2), "spam", PLUGIN).join().value().orElseThrow();

        assertEquals(PunishmentType.TEMPMUTE, mute.type());
        final Instant end = mute.endsAt().orElseThrow();
        assertFalse(end.isBefore(before.plus(Duration.ofHours(2)).minusSeconds(1)), end.toString());
        assertFalse(end.isAfter(Instant.now().plus(Duration.ofHours(2))), end.toString());
        assertTrue(steve.received("You have been muted on the server."), steve.messages().toString());
        assertEquals(Outcome.BLOCKED_MUTED, server.chat(steve, "hello"));
    }

    @Test
    void apiSilentSoftMuteByAddress_isStoredAsRequested() {
        final JudicatorApi api = api();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer bystander = server.online(new FakePlayer("Bystander", "10.0.0.2"));

        final PunishmentView mute = api.punish(PunishmentRequest.builder(PunishmentType.MUTE, "Steve")
                .reason("toxic").punisher(PLUGIN).silent(true).soft(true).evidence("https://imgur.com/a").build()).join().value().orElseThrow();

        assertTrue(mute.soft());
        assertEquals(List.of("https://imgur.com/a"), mute.evidences());
        assertEquals(Outcome.SOFT_MUTED, server.chat(steve, "hello"));
        assertFalse(steve.received("You have been muted"), "a soft mute is not told");
        assertFalse(bystander.received("muted"), bystander.messages().toString());

        final PunishmentView ipBan = api.punish(PunishmentRequest.builder(PunishmentType.BAN, "Steve")
                .byAddress(true).punisher(PLUGIN).build()).join().value().orElseThrow();
        assertEquals("10.0.0.1", ipBan.ipAddress().orElseThrow());
        assertTrue(server.join(new FakePlayer("Other", "10.0.0.1")).isPresent(), "same address");
    }

    @Test
    void apiIpBanOfAnUnknownAddress_isRefused() {
        final ActionResult<PunishmentView> result = api().punish(PunishmentRequest.builder(PunishmentType.BAN, "Ghost")
                .byAddress(true).build()).join();

        assertEquals(ActionStatus.ADDRESS_UNKNOWN, result.status());
        assertTrue(server.punishments.all().isEmpty());
    }

    @Test
    void requestsThatCannotBeStored_failWhenBuilt() {
        assertThrows(IllegalArgumentException.class, () -> PunishmentRequest.builder(PunishmentType.TEMPBAN, "Steve").build());
        assertThrows(IllegalArgumentException.class, () -> PunishmentRequest.builder(PunishmentType.BAN, "Steve").duration(Duration.ofDays(1)).build());
        assertThrows(IllegalArgumentException.class, () -> PunishmentRequest.builder(PunishmentType.BAN, "Steve").soft(true).build());
        assertThrows(IllegalArgumentException.class, () -> PunishmentRequest.builder(PunishmentType.KICK, "Steve").build());
        assertThrows(IllegalArgumentException.class, () -> PunishmentRequest.builder(PunishmentType.MUTE, "Steve").punisher("A plugin name that is far too long").build());
        assertThrows(IllegalArgumentException.class, () -> PunishmentRequest.builder(PunishmentType.MUTE, "Steve").reason("x".repeat(256)).build());
        assertThrows(IllegalArgumentException.class, () -> PunishmentRequest.builder(PunishmentType.TEMPMUTE, "Steve")
                .duration(Duration.ofHours(1)).onlineOnly(true).byAddress(true).build());
        assertThrows(IllegalArgumentException.class, () -> WarnRequest.builder("Steve", " ").build());
    }

    @Test
    void lookups_returnWhatTheCommandsStored() {
        final JudicatorApi api = api();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "mute Steve flood");
        server.command(server.console, "warn Steve toxic language");
        server.command(server.console, "tempban Steve 2:h spam");

        final PunishmentView ban = api.getActiveBan("steve").join().orElseThrow();
        assertEquals(PunishmentType.TEMPBAN, ban.type());
        assertEquals("spam", ban.reason());
        assertEquals("Console", ban.punisher());
        assertEquals(ban, api.getActiveBan(steve.getUniqueId()).join().orElseThrow());
        assertEquals(ban, api.getPunishment(ban.id()).join().orElseThrow());
        assertTrue(api.isBanned("Steve").join());
        assertTrue(api.isBanned(steve.getUniqueId()).join());

        final PunishmentView mute = api.getActiveMute(steve.getUniqueId()).join().orElseThrow();
        assertEquals("flood", mute.reason());
        assertTrue(api.isMuted("STEVE").join());

        final List<PunishmentView> history = api.getPunishmentHistory("Steve").join();
        assertEquals(2, history.size());
        assertEquals(ban.id(), history.get(0).id(), "newest first");
        assertEquals(history, api.getPunishmentHistory(steve.getUniqueId()).join());

        final List<WarnView> warns = api.getWarns("steve").join();
        assertEquals(1, warns.size());
        assertEquals("toxic language", warns.get(0).reason());
        assertTrue(warns.get(0).isActive());
        assertEquals(warns, api.getWarns(steve.getUniqueId()).join());
        assertEquals(warns.get(0), api.getWarn(warns.get(0).id()).join().orElseThrow());

        final UUID stranger = UUID.randomUUID();
        assertFalse(api.isBanned("Nobody").join());
        assertFalse(api.isMuted(stranger).join());
        assertTrue(api.getPunishmentHistory(stranger).join().isEmpty());
        assertTrue(api.getPunishment(999_999).join().isEmpty());
        assertTrue(api.getWarn(999_999).join().isEmpty());
    }

    @Test
    void lookups_seeRevokedAndFinishedPunishmentsAsSuch() {
        final JudicatorApi api = api();
        server.command(server.console, "ban Steve x");
        server.command(server.console, "unban Steve sorry");

        assertTrue(api.getActiveBan("Steve").join().isEmpty());
        final PunishmentView revoked = api.getPunishmentHistory("Steve").join().get(0);
        assertEquals(PunishmentStatus.REVOKED, revoked.status());
        assertEquals("sorry", revoked.revokedReason().orElseThrow());
    }

    @Test
    void apiWarn_runsTheWarnTreeLikeTheCommand() {
        final JudicatorApi api = api();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        final WarnView warn = api.warn("Steve", "one", PLUGIN).join().value().orElseThrow();
        assertEquals(PLUGIN, warn.punisher());
        assertTrue(warn.isPermanent());
        assertTrue(steve.received("You received a warning from AntiCheat."), steve.messages().toString());

        api.warn("Steve", "two", PLUGIN).join();
        final WarnView temporary = api.warn(WarnRequest.builder("Steve", "three").duration(Duration.ofDays(1)).punisher(PLUGIN).build())
                .join().value().orElseThrow();
        assertFalse(temporary.isPermanent());
        assertEquals(List.of("tempban Steve '3:d' got three warns"), server.platform.consoleCommands());
    }

    @Test
    void apiKick_disconnectsAnOnlinePlayerAndRefusesAnOfflineOne() {
        final JudicatorApi api = api();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        final ActionResult<PunishmentView> kicked = api.kick("Steve", null, PLUGIN, false).join();

        assertEquals(ActionStatus.SUCCESS, kicked.status());
        assertTrue(kicked.value().isEmpty(), "log-kicks is off");
        assertTrue(steve.kickMessage().contains("Reason: None"), steve.kickMessage());
        assertEquals(ActionStatus.TARGET_OFFLINE, api.kick("Ghost", "x", PLUGIN, false).join().status());
    }

    @Test
    void apiRevocations_liftThePunishmentsLikeTheCommands() {
        final JudicatorApi api = api();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final long muteId = api.mute("Steve", "flood", PLUGIN).join().value().orElseThrow().id();
        api.tempmute("Steve", Duration.ofHours(1), "spam", PLUGIN).join();

        final ActionResult<List<PunishmentView>> unmuted = api.unmute("Steve", "appeal accepted", PLUGIN).join();

        assertEquals(ActionStatus.SUCCESS, unmuted.status());
        assertEquals(2, unmuted.value().orElseThrow().size());
        assertTrue(unmuted.value().orElseThrow().stream().allMatch(mute -> mute.status() == PunishmentStatus.REVOKED));
        assertEquals(Outcome.ALLOWED, server.chat(steve, "hello"));
        assertEquals(1, steve.messages().stream().filter(message -> message.contains("Your punishment has been revoked!")).count(),
                "told once for both mutes");
        assertEquals(ActionStatus.NOT_FOUND, api.unmute("Steve", null, PLUGIN).join().status());
        assertEquals(ActionStatus.ALREADY_REVOKED, api.revoke(muteId, null, PLUGIN).join().status());
        assertEquals(ActionStatus.NOT_FOUND, api.revoke(999_999, null, PLUGIN).join().status());

        server.quit(steve);
        final long banId = api.ban("Steve", "x", PLUGIN).join().value().orElseThrow().id();
        final PunishmentView revoked = api.revoke(banId, "mistake", PLUGIN).join().value().orElseThrow();
        assertEquals("mistake", revoked.revokedReason().orElseThrow());
        assertTrue(server.join(new FakePlayer("Steve", "10.0.0.1")).isEmpty());

        final long warnId = api.warn("Steve", "toxic", PLUGIN).join().value().orElseThrow().id();
        assertEquals(ActionStatus.SUCCESS, api.unwarn(warnId, PLUGIN).join().status());
        assertTrue(server.warns.all().isEmpty());
        assertEquals(ActionStatus.NOT_FOUND, api.unwarn(warnId, PLUGIN).join().status());
    }

    @Test
    void apiSkipsTheStaffCooldown_butStartsItForTheStaff() {
        // default config: 30 s between two mutes of the same player
        start(TestServer.in(folder));
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.mute"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        assertEquals(ActionStatus.SUCCESS, api().mute("Steve", "flood", PLUGIN).join().status());
        assertEquals(ActionStatus.SUCCESS, api().tempmute("Steve", Duration.ofMinutes(5), "again", PLUGIN).join().status(),
                "the API is not held by the cooldown");

        server.command(mod, "mute Steve more");
        assertTrue(mod.received("Wait"), mod.messages().toString());
    }

    @Test
    void immunePlayers_areRefusedThroughTheApi() {
        start(TestServer.in(folder).immunePlayers("  Notch: [all]\n  Steve: [mute, warn]\n"));
        final JudicatorApi api = api();
        final FakePlayer notch = server.online(new FakePlayer("Notch", "10.0.0.5"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        assertEquals(ActionStatus.TARGET_EXEMPT, api.ban("Notch", "x", PLUGIN).join().status());
        assertEquals(ActionStatus.TARGET_EXEMPT, api.kick("Notch", "x", PLUGIN, false).join().status());
        assertEquals(ActionStatus.TARGET_EXEMPT, api.warn("Notch", "x", PLUGIN).join().status());
        assertFalse(notch.wasKicked());

        assertEquals(ActionStatus.TARGET_EXEMPT, api.mute("Steve", "x", PLUGIN).join().status());
        assertEquals(ActionStatus.TARGET_EXEMPT, api.warn("Steve", "x", PLUGIN).join().status());
        assertTrue(server.warns.all().isEmpty());
        assertTrue(active("Steve", PunishType.MUTE).isEmpty());
        assertEquals(ActionStatus.SUCCESS, api.ban("Steve", "x", PLUGIN).join().status(), "exempt from mutes and warns only");

        // an IP punishment reaches every account on the address: one exempt account protects it
        server.online(new FakePlayer("Alt", "10.0.0.5"));
        assertEquals(ActionStatus.TARGET_EXEMPT, api.punish(PunishmentRequest.builder(PunishmentType.BAN, "Alt")
                .byAddress(true).build()).join().status());
        assertTrue(active("Alt", PunishType.BAN).isEmpty());
    }

    @Test
    void exemptAddresses_areRefusedThroughTheApi() {
        // default config: 127.0.0.1 is in exempt-addresses
        start(TestServer.in(folder));
        server.online(new FakePlayer("Steve", "127.0.0.1"));

        final ActionResult<PunishmentView> result = api().punish(PunishmentRequest.builder(PunishmentType.BAN, "Steve")
                .byAddress(true).build()).join();

        assertEquals(ActionStatus.ADDRESS_EXEMPT, result.status());
        assertEquals(Optional.empty(), result.value());
    }

}
