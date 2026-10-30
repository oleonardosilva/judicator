package com.elless.judicator.punish.handlers;

import com.elless.judicator.Judicator;
import com.elless.judicator.api.event.PunishmentRevokedEvent;
import com.elless.judicator.integration.ApiViews;
import com.elless.judicator.punish.PunishService;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.rules.PunishCooldown;
import com.elless.judicator.punish.types.PunishType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Revokes bans and mutes ({@code /revoke}, {@code /unban}, {@code /unmute}, {@code /staffrollback} and the API): each
 * is stored as revoked (and synced), its mute leaves the cache, the players online here are told once, and an API
 * event is posted for each.
 */
public class RevokeHandler {

    private final Judicator judicator;

    public RevokeHandler(Judicator judicator) {
        this.judicator = judicator;
    }

    /**
     * @param releaseCooldown whether the target may get the same kind of punishment again right away (a mistaken
     *                        punishment undone)
     * @return those that were revoked; one revoked meanwhile, or that could not be stored, is left out
     */
    public List<Punishment> revoke(Collection<Punishment> punishments, String reason, String author, boolean releaseCooldown) {
        final PunishService punishService = judicator.getPunishService();
        final List<Punishment> revoked = new ArrayList<>();
        for (Punishment punishment : punishments) {
            if (!punishService.revokeNow(punishment.getId(), reason)) continue;
            revoked.add(punishment);
            judicator.getPunishCache().forgetMute(punishment.getId());
            if (releaseCooldown) {
                judicator.getPunishCooldown().release(PunishCooldown.categoryOf(punishment.getType()), punishment.getNickname());
            }
        }
        if (revoked.isEmpty()) return revoked;
        judicator.getPunishmentEffects().pardoned(revoked);
        revoked.forEach(punishment -> judicator.getEventBus().post(() ->
                new PunishmentRevokedEvent(ApiViews.revoked(punishment, reason), author)));
        return revoked;
    }

    /**
     * {@code /unban} or {@code /unmute}: revokes every ban (or mute) in force of a player and tells the staff.
     */
    public ActiveRevocation revokeActive(String target, boolean mute, String reason, String author, boolean silent) {
        final PunishType[] types = mute
                ? new PunishType[]{PunishType.MUTE, PunishType.TEMPMUTE}
                : new PunishType[]{PunishType.BAN, PunishType.TEMPBAN};
        final List<Punishment> active = judicator.getPunishService().findAllActiveOf(judicator.getPlayerDirectory().resolve(target), types);
        if (active.isEmpty()) return new ActiveRevocation(false, List.of());
        final List<Punishment> revoked = revoke(active, reason, author, true);
        if (!revoked.isEmpty()) {
            judicator.getStaffNotifier().notifyUnpunish(author, revoked.get(0).getNickname(), mute ? "MUTE" : "BAN", revoked.size(), silent);
        }
        return new ActiveRevocation(true, revoked);
    }

    /**
     * @param found   whether the player had any in force
     * @param revoked those that were revoked
     */
    public record ActiveRevocation(boolean found, List<Punishment> revoked) {
    }

}
