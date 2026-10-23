package com.openmc.judicator.database;

import com.openmc.judicator.notes.Note;
import com.openmc.judicator.notes.repository.NoteRepository;
import com.openmc.judicator.notes.repository.dao.NoteRelationalDAO;
import com.openmc.judicator.punish.AccessAddress;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.repository.AccessAddressRepository;
import com.openmc.judicator.punish.data.repository.PunishmentRepository;
import com.openmc.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.openmc.judicator.punish.hierarchy.PlayerLevelRepository;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.sync.SyncMessage;
import com.openmc.judicator.sync.repository.SyncRepository;
import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.repository.WarnRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every repository implementation must do. Runs against the SQLite DAOs (the real SQL) and against the
 * in-memory repositories the feature tests use, so those fakes cannot drift from the database behaviour.
 */
abstract class RepositoryContract {

    /**
     * The four repositories of one fresh, empty store.
     */
    protected record Store(PunishmentRepository punishments, WarnRepository warns, AccessAddressRepository addresses,
                           PlayerLevelRepository levels, NoteRepository notes, SyncRepository sync, Runnable close) {
    }

    @TempDir
    Path folder;

    private Store store;

    protected abstract Store open(Path folder);

    @BeforeEach
    void openStore() {
        store = open(folder);
        // Creating the schema again must be harmless: every startup does it.
        store.punishments().initialize();
        store.warns().initialize();
        store.addresses().initialize();
        store.levels().initialize();
        store.notes().initialize();
        store.sync().initialize();
    }

    // --- sync notices ---

    @Test
    void syncNotices_comeBackInOrderAfterAnIdAndAreDeletedUpToAnId() {
        assertEquals(0, store.sync().maxId().orElseThrow());
        store.sync().insert("server-a", "PUNISHED", 7);
        store.sync().insert("server-b", "REVOKED", 7);
        store.sync().insert("server-a", "CHANGED", 9);

        final List<SyncMessage> all = store.sync().findAfter(0, 10);
        assertEquals(List.of("PUNISHED", "REVOKED", "CHANGED"), all.stream().map(SyncMessage::kind).toList());
        assertEquals("server-b", all.get(1).sender());
        assertEquals(9, all.get(2).punishmentId());
        assertTrue(all.get(0).id() < all.get(1).id() && all.get(1).id() < all.get(2).id());
        assertEquals(all.get(2).id(), store.sync().maxId().orElseThrow());

        assertEquals(all.subList(1, 3), store.sync().findAfter(all.get(0).id(), 10));
        assertEquals(all.subList(0, 2), store.sync().findAfter(0, 2));

        assertEquals(2, store.sync().deleteUpTo(all.get(1).id()));
        assertEquals(List.of(all.get(2)), store.sync().findAfter(0, 10));
        // ids keep growing after a delete: an instance never sees an old id again
        store.sync().insert("server-a", "DELETED", 9);
        assertTrue(store.sync().findAfter(all.get(2).id(), 10).get(0).id() > all.get(2).id());
    }

    @AfterEach
    void closeStore() {
        store.close().run();
    }

    private static Punishment punishment(String nickname, String address, PunishType type, LocalDateTime start,
                                         LocalDateTime end, String punisher) {
        return Punishment.create(uuidOf(nickname), "reason", punisher, nickname, address, start, end, List.of(), type);
    }

    private static UUID uuidOf(String nickname) {
        return UUID.nameUUIDFromBytes(nickname.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void savedPunishmentReadsBackUnchanged() {
        final LocalDateTime start = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        final Punishment saved = store.punishments().save(punishment("Steve", null, PunishType.TEMPBAN, start, start.plusDays(7), "Admin"));
        assertTrue(saved.getId() != null && saved.getId() > 0);

        final Punishment loaded = store.punishments().findById(saved.getId()).orElseThrow();
        assertEquals("Steve", loaded.getNickname());
        assertEquals(uuidOf("Steve"), loaded.getPlayerUUID().orElseThrow());
        assertEquals(start, loaded.getStartedAt());
        assertEquals(start.plusDays(7), loaded.getFinishAt().orElseThrow());
        assertEquals(PunishType.TEMPBAN, loaded.getType());
        assertFalse(loaded.isRevoked());
        assertTrue(loaded.getEvidences().isEmpty());
    }

    @Test
    void savingAnExistingPunishmentUpdatesIt() {
        final LocalDateTime start = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        final Punishment saved = store.punishments().save(punishment("Steve", null, PunishType.BAN, start, null, "Admin"));
        saved.setReason("updated reason");
        saved.setEvidences(new java.util.ArrayList<>(List.of("https://a.example/1", "https://b.example/2")));
        store.punishments().save(saved);

        final Punishment loaded = store.punishments().findById(saved.getId()).orElseThrow();
        assertEquals("updated reason", loaded.getReason().orElseThrow());
        assertEquals(List.of("https://a.example/1", "https://b.example/2"), loaded.getEvidences());
        assertEquals(1, store.punishments().findAllByUsername("steve").size(), "updated, not inserted again");

        store.punishments().deleteById(saved.getId());
        assertTrue(store.punishments().findById(saved.getId()).isEmpty());
    }

    @Test
    void softAndOnlineOnlyMutesReadBackWithWhatIsLeftWhilePaused() {
        final LocalDateTime start = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        final Punishment mute = punishment("Steve", null, PunishType.TEMPMUTE, start, null, "Mod");
        mute.setSoft(true);
        mute.setOnlineOnly(true);
        mute.setPausedRemaining(3_600_000L);
        store.punishments().save(mute);

        final Punishment read = store.punishments().findById(mute.getId()).orElseThrow();
        assertTrue(read.isSoft());
        assertTrue(read.isOnlineOnly());
        assertEquals(3_600_000L, read.getPausedRemaining());
        assertEquals(1, store.punishments().findAllActiveByUsernameAndTypes("steve", PunishType.TEMPMUTE).size(),
                "paused: no end, still in force");

        read.setPausedRemaining(null);
        read.setFinishAt(start.plusHours(1));
        store.punishments().save(read);
        final Punishment resumed = store.punishments().findById(mute.getId()).orElseThrow();
        assertEquals(null, resumed.getPausedRemaining());
        assertEquals(start.plusHours(1), resumed.getFinishAt().orElseThrow());

        final Punishment plain = store.punishments().save(punishment("Alex", null, PunishType.MUTE, start, null, "Mod"));
        final Punishment plainRead = store.punishments().findById(plain.getId()).orElseThrow();
        assertFalse(plainRead.isSoft());
        assertFalse(plainRead.isOnlineOnly());
        assertEquals(null, plainRead.getPausedRemaining());
    }

    @Test
    void endAndReasonChangesLeaveRevokedPunishmentsAlone() {
        final LocalDateTime start = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        final Punishment mute = store.punishments().save(punishment("Steve", null, PunishType.TEMPMUTE, start, start.plusHours(1), "Mod"));

        assertTrue(store.punishments().changeEnd(mute.getId(), start.plusHours(3), null));
        assertTrue(store.punishments().changeReason(mute.getId(), "flood"));
        final Punishment changed = store.punishments().findById(mute.getId()).orElseThrow();
        assertEquals(start.plusHours(3), changed.getFinishAt().orElseThrow());
        assertEquals("flood", changed.getReason().orElseThrow());
        assertTrue(store.punishments().changeEnd(mute.getId(), null, 60_000L));
        assertEquals(60_000L, store.punishments().findById(mute.getId()).orElseThrow().getPausedRemaining());

        store.punishments().revoke(mute.getId(), "appeal");
        assertFalse(store.punishments().changeEnd(mute.getId(), start.plusDays(9), null));
        assertFalse(store.punishments().changeReason(mute.getId(), "other"));
        assertFalse(store.punishments().changeEnd(999_999L, start, null));
        final Punishment revoked = store.punishments().findById(mute.getId()).orElseThrow();
        assertTrue(revoked.isRevoked());
        assertEquals("flood", revoked.getReason().orElseThrow());
    }

    @Test
    void retentionDeletesOnlyWhatIsOverAndOld() {
        final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        final LocalDateTime cutoff = now.minusDays(30);
        final Punishment oldKick = punishment("A", null, PunishType.KICK, now.minusDays(40), now.minusDays(40), "Mod");
        oldKick.setPermanent(false);
        store.punishments().save(oldKick);
        final Punishment newKick = punishment("A", null, PunishType.KICK, now.minusDays(1), now.minusDays(1), "Mod");
        newKick.setPermanent(false);
        store.punishments().save(newKick);
        final Punishment oldEnded = store.punishments().save(punishment("B", null, PunishType.TEMPBAN, now.minusDays(50), now.minusDays(40), "Mod"));
        final Punishment recentEnded = store.punishments().save(punishment("B", null, PunishType.TEMPBAN, now.minusDays(50), now.minusDays(10), "Mod"));
        final Punishment oldPermanent = store.punishments().save(punishment("C", null, PunishType.BAN, now.minusDays(400), null, "Mod"));
        final Punishment oldRevoked = store.punishments().save(punishment("C", null, PunishType.BAN, now.minusDays(400), null, "Mod"));
        store.punishments().revoke(oldRevoked.getId(), "x");
        final Punishment paused = punishment("D", null, PunishType.TEMPMUTE, now.minusDays(400), null, "Mod");
        paused.setOnlineOnly(true);
        paused.setPausedRemaining(1000L);
        store.punishments().save(paused);

        assertEquals(2, store.punishments().deleteFinishedBefore(cutoff), "the ended tempban and the revoked ban, not the kick");
        assertTrue(store.punishments().findById(oldKick.getId()).isPresent(), "kicks have their own retention");
        assertEquals(1, store.punishments().deleteKicksBefore(cutoff));
        assertTrue(store.punishments().findById(newKick.getId()).isPresent());
        assertTrue(store.punishments().findById(oldEnded.getId()).isEmpty());
        assertTrue(store.punishments().findById(oldRevoked.getId()).isEmpty());
        assertTrue(store.punishments().findById(recentEnded.getId()).isPresent());
        assertTrue(store.punishments().findById(oldPermanent.getId()).isPresent(), "in force");
        assertTrue(store.punishments().findById(paused.getId()).isPresent(), "paused: in force");

        final UUID uuid = UUID.randomUUID();
        store.warns().save(Warn.create(uuid, "old", "Mod", "Steve", now.minusDays(50), now.minusDays(40)));
        final Warn permanent = store.warns().save(Warn.create(uuid, "permanent", "Mod", "Steve", now.minusDays(400), null));
        final Warn recent = store.warns().save(Warn.create(uuid, "recent", "Mod", "Steve", now.minusDays(50), now.minusDays(1)));
        assertEquals(1, store.warns().deleteFinishedBefore(cutoff));
        assertEquals(2, store.warns().findAllByUsername("steve").size());
        assertTrue(store.warns().findById(permanent.getId()).isPresent() && store.warns().findById(recent.getId()).isPresent());

        final AccessAddress stale = AccessAddress.create("10.0.0.1", "Steve");
        stale.setLastUsage(now.minusDays(40));
        store.addresses().save(stale);
        store.addresses().save(AccessAddress.create("10.0.0.2", "Steve"));
        assertEquals(1, store.addresses().deleteUnusedBefore(cutoff));
        assertTrue(store.addresses().findByIp("10.0.0.1").isEmpty());
        assertTrue(store.addresses().findByIp("10.0.0.2").isPresent());

        store.notes().save(new Note(null, null, "Steve", "Mod", "old", now.minusDays(40)));
        store.notes().save(new Note(null, null, "Steve", "Mod", "new", now.minusDays(1)));
        assertEquals(1, store.notes().deleteBefore(cutoff));
        assertEquals(1, store.notes().countByUsername("Steve"));
    }

    @Test
    void everythingOfOnePlayerIsDeletedByNameOrUuid() {
        final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        final UUID steve = UUID.randomUUID();
        final Punishment current = punishment("Steve", null, PunishType.BAN, now.minusDays(1), null, "Mod");
        current.setPlayerUUID(steve);
        store.punishments().save(current);
        final Punishment oldName = punishment("Steve_Old", null, PunishType.MUTE, now.minusDays(9), null, "Mod");
        oldName.setPlayerUUID(steve);
        store.punishments().save(oldName);
        final Punishment other = store.punishments().save(punishment("Alex", null, PunishType.BAN, now.minusDays(1), null, "Mod"));
        store.warns().save(Warn.create(steve, "a", "Mod", "Steve_Old", now.minusDays(2), null));
        store.warns().save(Warn.create(UUID.randomUUID(), "b", "Mod", "Alex", now.minusDays(2), null));
        store.notes().save(new Note(null, steve, "Steve_Old", "Mod", "x", now));
        store.notes().save(new Note(null, null, "steve", "Mod", "y", now));
        store.notes().save(new Note(null, null, "Alex", "Mod", "z", now));
        store.levels().saveLevel("Steve", steve, 10);

        assertEquals(2, store.notes().countAllOf("Steve", steve));
        assertEquals(1, store.notes().countAllOf("Steve", null));
        assertEquals(2, store.punishments().deleteAllOf("STEVE", steve));
        assertEquals(1, store.warns().deleteAllOf("steve", steve));
        assertEquals(2, store.notes().deleteAllOf("Steve", steve));
        assertTrue(store.levels().delete("steve"));
        assertFalse(store.levels().delete("steve"));

        assertTrue(store.punishments().findById(other.getId()).isPresent());
        assertEquals(1, store.warns().findAllByUsername("Alex").size());
        assertEquals(1, store.notes().countByUsername("Alex"));
        assertTrue(store.levels().findLevel("Steve").isEmpty());
        assertEquals(0, store.punishments().deleteAllOf("Alex_Typo", null));
    }

    @Test
    void evidenceIsStoredAsCommaSeparatedText() {
        final Punishment saved = store.punishments().save(Punishment.create(null, "r", "Admin", "Steve", null,
                LocalDateTime.now(), null, List.of("https://x.example/a,b", " "), PunishType.BAN));
        // A comma inside a link splits it and blank parts disappear: callers must not rely on commas surviving.
        assertEquals(List.of("https://x.example/a", "b"), store.punishments().findById(saved.getId()).orElseThrow().getEvidences());
    }

    @Test
    void savingAnExistingWarnUpdatesIt() {
        final Warn warn = store.warns().save(Warn.create(UUID.randomUUID(), "first", "Mod", "Steve", LocalDateTime.now().withNano(0), null));
        warn.setReason("second");
        store.warns().save(warn);
        assertEquals("second", store.warns().findById(warn.getId()).orElseThrow().getReason());
        assertEquals(1, store.warns().findAllByUsername("steve").size());
    }

    @Test
    void onlyUnexpiredAndUnrevokedPunishmentsAreActive() {
        final LocalDateTime now = LocalDateTime.now();
        store.punishments().save(punishment("Steve", null, PunishType.TEMPMUTE, now.minusDays(2), now.minusDays(1), "Admin"));
        final Punishment ban = store.punishments().save(punishment("Steve", null, PunishType.TEMPBAN, now.minusHours(1), now.plusHours(1), "Admin"));
        store.punishments().save(punishment("Other", "10.0.0.1", PunishType.BAN, now.minusHours(1), null, "Admin"));

        assertEquals(1, store.punishments().findAllActiveByUsernameAndTypes("STEVE", PunishType.values()).size());
        assertEquals(0, store.punishments().findAllActiveByUsernameAndTypes("steve", PunishType.MUTE, PunishType.TEMPMUTE).size());
        assertEquals(2, store.punishments().findAllActiveByUsernameOrIpAndTypes("steve", "10.0.0.1", PunishType.values()).size());
        assertEquals(1, store.punishments().findAllActiveByUUIDAndTypes(uuidOf("Steve"), PunishType.TEMPBAN).size());
        assertEquals(2, store.punishments().findAllByUUID(uuidOf("Steve")).size());

        assertTrue(store.punishments().revoke(ban.getId(), "appeal"));
        assertFalse(store.punishments().revoke(ban.getId(), "again"));
        assertTrue(store.punishments().findAllActiveByUsernameAndTypes("steve", PunishType.values()).isEmpty());
        assertEquals("appeal", store.punishments().findById(ban.getId()).orElseThrow().getRevokedReason().orElseThrow());
        assertEquals(2, store.punishments().findAllByUsername("steve").size());
    }

    @Test
    void activePunishmentsOfSeveralAccountsInOneQuery() {
        final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        final Punishment steve = store.punishments().save(punishment("Steve", null, PunishType.BAN, now.minusDays(1), null, "Mod"));
        final Punishment alex = store.punishments().save(punishment("Alex", null, PunishType.TEMPBAN, now.minusDays(1), now.plusDays(1), "Mod"));
        store.punishments().save(punishment("Alex", null, PunishType.TEMPBAN, now.minusDays(3), now.minusDays(2), "Mod"));
        store.punishments().save(punishment("Alex", null, PunishType.MUTE, now.minusDays(1), null, "Mod"));
        store.punishments().save(punishment("Other", null, PunishType.BAN, now.minusDays(1), null, "Mod"));
        final Punishment revoked = store.punishments().save(punishment("Bob", null, PunishType.BAN, now.minusDays(1), null, "Mod"));
        store.punishments().revoke(revoked.getId(), "x");

        final List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < 450; i++) names.add("account" + i); // more names than one statement takes
        names.addAll(List.of("STEVE", "alex", "bob"));

        final Set<Long> found = new java.util.HashSet<>();
        store.punishments().findAllActiveByUsernamesAndTypes(names, PunishType.BAN, PunishType.TEMPBAN).forEach(p -> found.add(p.getId()));

        assertEquals(Set.of(steve.getId(), alex.getId()), found);
        assertTrue(store.punishments().findAllActiveByUsernamesAndTypes(List.of(), PunishType.BAN).isEmpty());
    }

    @Test
    void aLoggedKickIsNeverActive() {
        final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        final Punishment kick = Punishment.create(uuidOf("Steve"), "calm down", "Mod", "Steve", null, now, now, List.of(), PunishType.KICK);
        kick.setPermanent(false);
        store.punishments().save(kick);

        assertTrue(store.punishments().findAllActiveByUsernameAndTypes("steve", PunishType.values()).isEmpty());
        assertTrue(store.punishments().findAllActiveByUUIDAndTypes(uuidOf("Steve"), PunishType.values()).isEmpty());
        assertEquals(PunishType.KICK, store.punishments().findAllByUsername("steve").get(0).getType());
    }

    @Test
    void blamePagesNewestFirst() {
        final LocalDateTime start = LocalDateTime.now().minusDays(30);
        for (int i = 0; i < 12; i++) {
            store.punishments().save(punishment("Player" + i, null, PunishType.BAN, start.plusDays(i), null, i % 2 == 0 ? "Mod" : "Admin"));
        }
        assertEquals(6, store.punishments().countByPunisher("MOD"));
        assertEquals(List.of("Player10", "Player8", "Player6", "Player4"),
                store.punishments().findByPunisher("mod", 4, 0).stream().map(Punishment::getNickname).toList());
        assertEquals(List.of("Player2", "Player0"),
                store.punishments().findByPunisher("mod", 4, 4).stream().map(Punishment::getNickname).toList());
        assertEquals(0, store.punishments().countByPunisher("nobody"));
    }

    @Test
    void blameCanBeLimitedToAPeriod() {
        final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        store.punishments().save(punishment("Old", null, PunishType.BAN, now.minusDays(10), null, "Mod"));
        store.punishments().save(punishment("Recent", null, PunishType.BAN, now.minusHours(1), null, "Mod"));
        store.punishments().save(punishment("Newest", null, PunishType.BAN, now.minusMinutes(1), null, "mod"));

        assertEquals(3, store.punishments().countByPunisher("Mod", null));
        assertEquals(2, store.punishments().countByPunisher("Mod", now.minusDays(1)));
        assertEquals(List.of("Newest", "Recent"),
                store.punishments().findByPunisher("MOD", now.minusDays(1), 10, 0).stream().map(Punishment::getNickname).toList());
    }

    @Test
    void activeByTypesPagesWhatIsInForceNewestFirst() {
        final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        store.punishments().save(punishment("A", null, PunishType.BAN, now.minusHours(3), null, "Mod"));
        store.punishments().save(punishment("B", null, PunishType.TEMPBAN, now.minusHours(2), now.plusHours(1), "Mod"));
        store.punishments().save(punishment("C", null, PunishType.TEMPBAN, now.minusDays(2), now.minusDays(1), "Mod"));
        final Punishment revoked = store.punishments().save(punishment("D", null, PunishType.BAN, now.minusHours(1), null, "Mod"));
        store.punishments().revoke(revoked.getId(), "appeal");
        store.punishments().save(punishment("E", "10.0.0.1", PunishType.MUTE, now.minusMinutes(1), null, "Mod"));

        assertEquals(2, store.punishments().countActiveByTypes(PunishType.BAN, PunishType.TEMPBAN));
        assertEquals(List.of("B", "A"), store.punishments().findActiveByTypes(10, 0, PunishType.BAN, PunishType.TEMPBAN)
                .stream().map(Punishment::getNickname).toList());
        assertEquals(List.of("A"), store.punishments().findActiveByTypes(1, 1, PunishType.BAN, PunishType.TEMPBAN)
                .stream().map(Punishment::getNickname).toList());
        assertEquals(1, store.punishments().countActiveByTypes(PunishType.MUTE, PunishType.TEMPMUTE));
        assertEquals(0, store.punishments().countActiveByTypes());
    }

    @Test
    void staffActivityIsCountedPerStaffMemberAndType() {
        final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        store.punishments().save(punishment("A", null, PunishType.BAN, now.minusHours(1), null, "Mod"));
        store.punishments().save(punishment("B", null, PunishType.BAN, now.minusHours(1), null, "mod"));
        store.punishments().save(punishment("C", null, PunishType.TEMPMUTE, now.minusHours(1), now.plusHours(1), "Mod"));
        store.punishments().save(punishment("D", null, PunishType.BAN, now.minusDays(5), null, "Mod"));
        store.warns().save(Warn.create(UUID.randomUUID(), "r", "Helper", "A", now.minusHours(1), null));
        store.warns().save(Warn.create(UUID.randomUUID(), "r", "helper", "B", now.minusHours(1), null));
        store.warns().save(Warn.create(UUID.randomUUID(), "r", "Helper", "C", now.minusDays(5), null));

        final Map<String, Integer> punishments = new HashMap<>();
        store.punishments().countByStaffSince(now.minusDays(1))
                .forEach(c -> punishments.put(c.punisher().toLowerCase() + "|" + c.type(), c.count()));
        assertEquals(Map.of("mod|BAN", 2, "mod|TEMPMUTE", 1), punishments);

        final List<PunishmentRepository.StaffCount> warns = store.warns().countByStaffSince(now.minusDays(1));
        assertEquals(1, warns.size());
        assertEquals("helper", warns.get(0).punisher().toLowerCase());
        assertEquals("WARN", warns.get(0).type());
        assertEquals(2, warns.get(0).count());
    }

    @Test
    void importHelpersInsertAndRecognizeDuplicates() throws Exception {
        final LocalDateTime start = LocalDateTime.now().minusDays(3).truncatedTo(ChronoUnit.SECONDS);
        final Punishment imported = punishment("Alex", null, PunishType.MUTE, start, null, "Console");
        imported.setPlayerUUID(null); // name-only records are allowed

        assertEquals(1, store.punishments().insertAll(List.of(imported)));
        final Set<String> keys = store.punishments().findAllImportKeys();
        assertEquals(Set.of(PunishmentRelationalDAO.importKey(imported)), keys);
        assertTrue(store.punishments().findAllByUsername("alex").get(0).getPlayerUUID().isEmpty());
    }

    @Test
    void warnsCountOnlyActiveOnes() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        store.warns().save(Warn.create(uuid, "a", "Mod", "Steve", now.minusDays(2), null));
        store.warns().save(Warn.create(uuid, "b", "Mod", "Steve", now.minusDays(2), now.plusDays(1)));
        final Warn expired = store.warns().save(Warn.create(uuid, "c", "Mod", "Steve", now.minusDays(2), now.minusDays(1)));

        assertEquals(2L, store.warns().countActiveWarns("steve").longValue());
        assertEquals(3, store.warns().findAllByUsername("Steve").size());
        assertEquals(3, store.warns().findAllByUUID(uuid).size());
        assertEquals(1, store.warns().findAllImportKeys().size(), "same player and start: one key");

        store.warns().deleteById(expired.getId());
        assertTrue(store.warns().findById(expired.getId()).isEmpty());
    }

    @Test
    void warnsOfAStaffMemberSinceAMomentNewestFirst() {
        final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        final Warn older = store.warns().save(Warn.create(UUID.randomUUID(), "a", "Mod", "A", now.minusHours(3), null));
        final Warn newer = store.warns().save(Warn.create(UUID.randomUUID(), "b", "mod", "B", now.minusHours(1), null));
        store.warns().save(Warn.create(UUID.randomUUID(), "c", "Mod", "C", now.minusDays(3), null));
        store.warns().save(Warn.create(UUID.randomUUID(), "d", "Other", "D", now.minusHours(1), null));

        final List<Warn> found = store.warns().findByPunisherSince("MOD", now.minusDays(1));

        assertEquals(List.of(newer.getId(), older.getId()), found.stream().map(Warn::getId).toList());
    }

    @Test
    void notesArePagedNewestFirstPerPlayer() throws Exception {
        final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        final Note older = store.notes().save(new Note(null, UUID.randomUUID(), "Steve", "Mod", "first", now.minusHours(2)));
        final Note newer = store.notes().save(new Note(null, null, "steve", "Helper", "second", now.minusHours(1)));
        store.notes().save(new Note(null, null, "Alex", "Mod", "other", now));

        assertEquals(2, store.notes().countByUsername("STEVE"));
        assertEquals(List.of(newer.getId(), older.getId()),
                store.notes().findByUsername("Steve", 10, 0).stream().map(Note::getId).toList());
        assertEquals(List.of(older.getId()), store.notes().findByUsername("Steve", 1, 1).stream().map(Note::getId).toList());
        final Note read = store.notes().findById(older.getId()).orElseThrow();
        assertEquals("first", read.getContent());
        assertEquals("Mod", read.getAuthor());
        assertEquals(older.getCreatedAt(), read.getCreatedAt());
        assertTrue(read.getPlayerUUID().isPresent());

        assertEquals(Set.of(NoteRelationalDAO.importKey(older), NoteRelationalDAO.importKey(newer),
                NoteRelationalDAO.importKey("alex", "mod", now)), store.notes().findAllImportKeys());
        assertEquals(1, store.notes().insertAll(List.of(new Note(null, null, "Steve", "Mod", "imported", now.minusDays(1)))));
        assertEquals(3, store.notes().countByUsername("steve"));

        store.notes().deleteById(older.getId());
        assertTrue(store.notes().findById(older.getId()).isEmpty());
    }

    @Test
    void addressHistoryMatchesWholeNamesNewestFirst() {
        final AccessAddress first = AccessAddress.create("10.0.0.1", "Steve");
        first.setLastUsage(LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS));
        store.addresses().save(first);
        final AccessAddress second = AccessAddress.create("10.0.0.2", "Steve");
        second.addAccount("Steve2");
        store.addresses().save(second);

        assertEquals("10.0.0.2", store.addresses().findByUsername("steve").orElseThrow().getHostAddress());
        assertEquals(List.of("10.0.0.2", "10.0.0.1"),
                store.addresses().findAllByUsername("steve").stream().map(AccessAddress::getHostAddress).toList());
        assertEquals(1, store.addresses().findAllByUsername("steve2").size());
        assertTrue(store.addresses().findAllByUsername("stev").isEmpty(), "a part of a name is not a match");

        final AccessAddress reloaded = store.addresses().findByIp("10.0.0.1").orElseThrow();
        reloaded.addAccount("Alex");
        store.addresses().save(reloaded);
        assertTrue(store.addresses().findByIp("10.0.0.1").orElseThrow().contains("alex"));
        assertTrue(store.addresses().findByIp("10.9.9.9").isEmpty());
    }

    @Test
    void staffLevelsAreInsertedThenUpdated() {
        assertEquals(OptionalInt.empty(), store.levels().findLevel("Mod"));
        store.levels().saveLevel("Mod", UUID.randomUUID(), 10);
        store.levels().saveLevel("MOD", UUID.randomUUID(), 50);
        assertEquals(OptionalInt.of(50), store.levels().findLevel("mod"));
    }
}
