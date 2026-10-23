package com.openmc.judicator.reports;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin message of {@code /reports tp} (proxy → backend in proxy-mode).
 */
class TeleportRequestTest {

    @Test
    void aRequestReadsBackAsItWasWritten() {
        final TeleportRequest request = new TeleportRequest(7L, UUID.randomUUID(), UUID.randomUUID());

        assertEquals(Optional.of(request), TeleportRequest.decode(request.encode()));
    }

    @Test
    void theBackendsAnswerReadsBack_andIsNeverTakenForARequest() {
        final TeleportResult result = new TeleportResult(9L, UUID.randomUUID(), true);

        assertEquals(Optional.of(result), TeleportResult.decode(result.encode()));
        assertTrue(TeleportRequest.decode(result.encode()).isEmpty());
        assertTrue(TeleportResult.decode(new TeleportRequest(7L, UUID.randomUUID(), UUID.randomUUID()).encode()).isEmpty());
    }

    @Test
    void anythingElseIsIgnored() {
        final byte[] valid = new TeleportRequest(7L, UUID.randomUUID(), UUID.randomUUID()).encode();
        final byte[] otherVersion = valid.clone();
        otherVersion[0] = 1;

        assertTrue(TeleportRequest.decode(new byte[0]).isEmpty());
        assertTrue(TeleportRequest.decode(otherVersion).isEmpty());
        assertTrue(TeleportRequest.decode(Arrays.copyOf(valid, valid.length - 1)).isEmpty(), "cut short");
        assertTrue(TeleportRequest.decode(Arrays.copyOf(valid, valid.length + 1)).isEmpty(), "trailing bytes");
        assertTrue(TeleportRequest.decode("hello".getBytes()).isEmpty());
    }
}
