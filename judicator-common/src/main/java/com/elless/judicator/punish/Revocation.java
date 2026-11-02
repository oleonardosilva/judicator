package com.elless.judicator.punish;

import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What is stored when a punishment is revoked: why, who and when ({@code revoked_reason}, {@code revoked_by},
 * {@code revoked_by_uuid}, {@code revoked_at}).
 *
 * @param reason the reason as it is stored ({@link PunishService#storedRevokeReason})
 * @param by     the staff member's name, {@code Console} or the name of the plugin that called the API
 * @param byUuid the staff member's UUID; null for the console and a plugin
 */
public record Revocation(String reason, String by, UUID byUuid, LocalDateTime at) {

    /**
     * A revocation made now.
     */
    public static Revocation now(String reason, String by, UUID byUuid) {
        return new Revocation(PunishService.storedRevokeReason(reason), by, byUuid, LocalDateTime.now());
    }

    /**
     * A revocation made now by whoever ran the command.
     */
    public static Revocation now(String reason, JudicatorSender source) {
        return now(reason, source.asPlayer().map(JudicatorPlayer::getName).orElse("Console"),
                source.asPlayer().map(JudicatorPlayer::getUniqueId).orElse(null));
    }
}
