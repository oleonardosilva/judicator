package com.elless.judicator.reports;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * The backend's answer to a {@link TeleportRequest}, on the same channel through the staff member's connection: whether
 * it teleported them. Only the proxy reads it (it never comes from a client: the proxy only takes it from a backend).
 */
public record TeleportResult(long request, UUID staff, boolean teleported) {

    private static final int VERSION = 2;
    private static final String RESULT = "TP_RESULT";

    public byte[] encode() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeUTF(RESULT);
            out.writeLong(request);
            out.writeLong(staff.getMostSignificantBits());
            out.writeLong(staff.getLeastSignificantBits());
            out.writeBoolean(teleported);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * @return empty for anything that is not a result of this version
     */
    public static Optional<TeleportResult> decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readUnsignedByte() != VERSION || !RESULT.equals(in.readUTF())) return Optional.empty();
            final long request = in.readLong();
            final UUID staff = new UUID(in.readLong(), in.readLong());
            final boolean teleported = in.readBoolean();
            if (in.available() > 0) return Optional.empty();
            return Optional.of(new TeleportResult(request, staff, teleported));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
