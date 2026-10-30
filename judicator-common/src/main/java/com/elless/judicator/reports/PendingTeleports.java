package com.elless.judicator.reports;

import com.elless.judicator.platform.TeleportOutcome;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The proxy's side of {@code /reports tp} once the staff member is on the player's server: a {@link TeleportRequest}
 * goes to that backend, and the {@link TeleportResult} it sends back says how it went. A backend without Judicator in
 * {@code proxy-mode} never answers.
 */
public final class PendingTeleports {

    static final long ANSWER_SECONDS = 5;

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();

    /**
     * @param send sends the encoded request through the staff member's connection to the backend
     * @return completes with the backend's answer, {@link TeleportOutcome#SERVER_ONLY} without one in
     * {@value #ANSWER_SECONDS} seconds, or {@link TeleportOutcome#SUPERSEDED} when the staff member asks again first
     */
    public CompletableFuture<TeleportOutcome> request(UUID staff, UUID target, Consumer<byte[]> send) {
        final Pending request = new Pending(nextId.incrementAndGet(), new CompletableFuture<>());
        final Pending previous = pending.put(staff, request);
        if (previous != null) previous.answer().complete(TeleportOutcome.SUPERSEDED);
        send.accept(new TeleportRequest(request.id(), staff, target).encode());
        return request.answer().completeOnTimeout(TeleportOutcome.SERVER_ONLY, ANSWER_SECONDS, TimeUnit.SECONDS)
                .whenComplete((outcome, error) -> pending.remove(staff, request));
    }

    /**
     * A backend answered through a player's connection; only that player's own, latest request takes it.
     *
     * @param from the player the backend connection belongs to
     */
    public void answer(UUID from, TeleportResult result) {
        if (!from.equals(result.staff())) return;
        final Pending request = pending.get(result.staff());
        // an answer to an older request is not this one's
        if (request != null && request.id() == result.request()) {
            request.answer().complete(result.teleported() ? TeleportOutcome.TELEPORTED : TeleportOutcome.FAILED);
        }
    }

    private record Pending(long id, CompletableFuture<TeleportOutcome> answer) {
    }

}
