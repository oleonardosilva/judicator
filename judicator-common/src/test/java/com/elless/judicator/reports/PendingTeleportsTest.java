package com.elless.judicator.reports;

import com.elless.judicator.platform.TeleportOutcome;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The proxy waits for the backend's answer to {@code /reports tp} (Velocity and BungeeCord share it).
 */
class PendingTeleportsTest {

    private static final UUID STAFF = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    private final PendingTeleports teleports = new PendingTeleports();
    private final List<TeleportRequest> sent = new ArrayList<>();

    private CompletableFuture<TeleportOutcome> request() {
        return teleports.request(STAFF, TARGET, bytes -> sent.add(TeleportRequest.decode(bytes).orElseThrow()));
    }

    @Test
    void theBackendsAnswer_completesTheRequest() {
        final CompletableFuture<TeleportOutcome> outcome = request();

        assertEquals(TARGET, sent.get(0).target());
        teleports.answer(STAFF, new TeleportResult(sent.get(0).id(), STAFF, true));

        assertEquals(TeleportOutcome.TELEPORTED, outcome.join());
    }

    @Test
    void aBackendThatCouldNotTeleport_failsIt() {
        final CompletableFuture<TeleportOutcome> outcome = request();

        teleports.answer(STAFF, new TeleportResult(sent.get(0).id(), STAFF, false));

        assertEquals(TeleportOutcome.FAILED, outcome.join());
    }

    @Test
    void anAnswerThroughAnotherPlayersConnection_isIgnored() {
        final CompletableFuture<TeleportOutcome> outcome = request();

        teleports.answer(UUID.randomUUID(), new TeleportResult(sent.get(0).id(), STAFF, true));

        assertFalse(outcome.isDone());
    }

    @Test
    void askingAgain_supersedesTheFirstRequest_andAnOldAnswerIsIgnored() {
        final CompletableFuture<TeleportOutcome> first = request();
        final CompletableFuture<TeleportOutcome> second = request();

        assertEquals(TeleportOutcome.SUPERSEDED, first.join());
        teleports.answer(STAFF, new TeleportResult(sent.get(0).id(), STAFF, true));
        assertFalse(second.isDone());

        teleports.answer(STAFF, new TeleportResult(sent.get(1).id(), STAFF, true));
        assertEquals(TeleportOutcome.TELEPORTED, second.join());
    }

}
