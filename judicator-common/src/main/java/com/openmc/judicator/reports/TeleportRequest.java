package com.openmc.judicator.reports;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * What the proxy sends a backend running Judicator in {@code proxy-mode} on the {@link #CHANNEL} plugin channel, through
 * the staff member's own connection, after moving them to the reported player's server: teleport this staff member to
 * that player. Only ids travel, never text.
 *
 * @param id     the request, repeated in the {@link TeleportResult} so a late answer is not taken for a newer request
 * @param staff  who is teleported (the player the message comes through)
 * @param target who to go to
 */
public record TeleportRequest(long id, UUID staff, UUID target) {

    public static final String CHANNEL = "judicator:main";
    private static final int VERSION = 2;
    private static final String TELEPORT = "TP";

    public byte[] encode() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeUTF(TELEPORT);
            out.writeLong(id);
            out.writeLong(staff.getMostSignificantBits());
            out.writeLong(staff.getLeastSignificantBits());
            out.writeLong(target.getMostSignificantBits());
            out.writeLong(target.getLeastSignificantBits());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * @return empty for anything that is not a request of this version (an older or newer proxy, garbage)
     */
    public static Optional<TeleportRequest> decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readUnsignedByte() != VERSION || !TELEPORT.equals(in.readUTF())) return Optional.empty();
            final long id = in.readLong();
            final UUID staff = new UUID(in.readLong(), in.readLong());
            final UUID target = new UUID(in.readLong(), in.readLong());
            if (in.available() > 0) return Optional.empty();
            return Optional.of(new TeleportRequest(id, staff, target));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
