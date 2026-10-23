package com.openmc.judicator.api;

import com.openmc.judicator.api.event.EventBus;
import com.openmc.judicator.api.punishment.PunishmentRequest;
import com.openmc.judicator.api.punishment.PunishmentType;
import com.openmc.judicator.api.punishment.PunishmentView;
import com.openmc.judicator.api.warn.WarnRequest;
import com.openmc.judicator.api.warn.WarnView;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Judicator for other plugins: look up punishments and warns, apply and revoke them, and listen to what happens.
 * Get it with {@link JudicatorProvider#get()} (or, on Spigot/Paper, from the {@code ServicesManager}).
 *
 * <h2>Threads</h2>
 * Every method returns at once and does its work (database, Mojang lookups) on Judicator's asynchronous tasks; the
 * future completes on that thread. Do not {@code join()} it on the server thread. A future completes exceptionally
 * only on an unexpected error; refusals are an {@link ActionStatus}.
 *
 * <h2>Lookups</h2>
 * Players are matched by the account a punishment was recorded for: by name (any letter case) or by the UUID stored
 * with it. An IP punishment applied to another account on the same address is not found through this player; a
 * punishment stored without a UUID (a player who never joined and could not be looked up) is found only by name.
 *
 * <h2>Applying and revoking</h2>
 * Actions go through the same path as the commands: the punishment is stored, cached, synced to the other servers
 * ({@code sync}), its effects reach the players (kick, alert, announcement unless silent), staff are notified, the
 * Discord webhook is called, the warn tree runs, and the events are posted. The {@code punisher} is a name of your
 * choice (for instance your plugin's), recorded as the author.
 * <p>
 * Staff rules do not apply: an API call is treated like the console, so there is no staff hierarchy, no time limit
 * ({@code time-limits}), no evidence prompt and no permission check, and the cooldown on the target is not checked
 * (an API punishment still starts it for the staff). Exemptions do apply: a player exempt in {@code immune.yml}
 * (for that type, or for everything) is refused with {@link ActionStatus#TARGET_EXEMPT}, and an IP punishment of an
 * address in {@code exempt-addresses} with {@link ActionStatus#ADDRESS_EXEMPT}.
 */
public interface JudicatorApi {

    /**
     * @return where to subscribe to Judicator's events
     */
    EventBus getEventBus();

    // ---- punishments: lookups -----------------------------------------------------------------------------------

    /**
     * @param uniqueId the player's UUID
     * @return the ban in force for the account (the one that ends last when there are several)
     */
    CompletableFuture<Optional<PunishmentView>> getActiveBan(UUID uniqueId);

    /**
     * @param name the player's name, any letter case
     * @return the ban in force for the account (the one that ends last when there are several)
     */
    CompletableFuture<Optional<PunishmentView>> getActiveBan(String name);

    /**
     * @param uniqueId the player's UUID
     * @return the mute in force for the account (a normal mute before a soft one, then the one that ends last)
     */
    CompletableFuture<Optional<PunishmentView>> getActiveMute(UUID uniqueId);

    /**
     * @param name the player's name, any letter case
     * @return the mute in force for the account (a normal mute before a soft one, then the one that ends last)
     */
    CompletableFuture<Optional<PunishmentView>> getActiveMute(String name);

    /**
     * @param uniqueId the player's UUID
     * @return whether the account has a ban in force
     */
    default CompletableFuture<Boolean> isBanned(UUID uniqueId) {
        return getActiveBan(uniqueId).thenApply(Optional::isPresent);
    }

    /**
     * @param name the player's name, any letter case
     * @return whether the account has a ban in force
     */
    default CompletableFuture<Boolean> isBanned(String name) {
        return getActiveBan(name).thenApply(Optional::isPresent);
    }

    /**
     * @param uniqueId the player's UUID
     * @return whether the account has a mute in force (soft mutes included)
     */
    default CompletableFuture<Boolean> isMuted(UUID uniqueId) {
        return getActiveMute(uniqueId).thenApply(Optional::isPresent);
    }

    /**
     * @param name the player's name, any letter case
     * @return whether the account has a mute in force (soft mutes included)
     */
    default CompletableFuture<Boolean> isMuted(String name) {
        return getActiveMute(name).thenApply(Optional::isPresent);
    }

    /**
     * @param uniqueId the player's UUID
     * @return every punishment of the account (in force, finished, revoked, logged kicks), newest first
     */
    CompletableFuture<List<PunishmentView>> getPunishmentHistory(UUID uniqueId);

    /**
     * @param name the player's name, any letter case
     * @return every punishment of the account (in force, finished, revoked, logged kicks), newest first
     */
    CompletableFuture<List<PunishmentView>> getPunishmentHistory(String name);

    /**
     * @param id the punishment id
     * @return the punishment with that id
     */
    CompletableFuture<Optional<PunishmentView>> getPunishment(long id);

    // ---- warns: lookups -----------------------------------------------------------------------------------------

    /**
     * @param uniqueId the player's UUID
     * @return every warn of the account (active and finished), newest first
     */
    CompletableFuture<List<WarnView>> getWarns(UUID uniqueId);

    /**
     * @param name the player's name, any letter case
     * @return every warn of the account (active and finished), newest first
     */
    CompletableFuture<List<WarnView>> getWarns(String name);

    /**
     * @param id the warn id
     * @return the warn with that id
     */
    CompletableFuture<Optional<WarnView>> getWarn(long id);

    // ---- applying -----------------------------------------------------------------------------------------------

    /**
     * Applies a ban or mute as {@code /ban}, {@code /tempmute}, {@code /banip}, {@code /softmute}... from the console
     * would (see the class description for the rules that apply). A player who already has a punishment of that kind
     * gets another one, as with the commands.
     *
     * @param request what to apply
     * @return {@link ActionStatus#SUCCESS} with the stored punishment; {@link ActionStatus#TARGET_EXEMPT},
     * {@link ActionStatus#ADDRESS_EXEMPT}, {@link ActionStatus#ADDRESS_UNKNOWN} or {@link ActionStatus#FAILED}
     */
    CompletableFuture<ActionResult<PunishmentView>> punish(PunishmentRequest request);

    /**
     * Permanently bans an account.
     *
     * @param target   the player's name
     * @param reason   the reason; null for none
     * @param punisher the name recorded as the author
     * @return see {@link #punish(PunishmentRequest)}
     * @throws IllegalArgumentException when a value cannot be stored (see {@link PunishmentRequest})
     */
    default CompletableFuture<ActionResult<PunishmentView>> ban(String target, String reason, String punisher) {
        return punish(PunishmentRequest.builder(PunishmentType.BAN, target).reason(reason).punisher(punisher).build());
    }

    /**
     * Bans an account for a while.
     *
     * @param target   the player's name
     * @param duration how long, from now
     * @param reason   the reason; null for none
     * @param punisher the name recorded as the author
     * @return see {@link #punish(PunishmentRequest)}
     * @throws IllegalArgumentException when a value cannot be stored (see {@link PunishmentRequest})
     */
    default CompletableFuture<ActionResult<PunishmentView>> tempban(String target, Duration duration, String reason, String punisher) {
        return punish(PunishmentRequest.builder(PunishmentType.TEMPBAN, target).duration(duration).reason(reason).punisher(punisher).build());
    }

    /**
     * Permanently mutes an account.
     *
     * @param target   the player's name
     * @param reason   the reason; null for none
     * @param punisher the name recorded as the author
     * @return see {@link #punish(PunishmentRequest)}
     * @throws IllegalArgumentException when a value cannot be stored (see {@link PunishmentRequest})
     */
    default CompletableFuture<ActionResult<PunishmentView>> mute(String target, String reason, String punisher) {
        return punish(PunishmentRequest.builder(PunishmentType.MUTE, target).reason(reason).punisher(punisher).build());
    }

    /**
     * Mutes an account for a while.
     *
     * @param target   the player's name
     * @param duration how long, from now
     * @param reason   the reason; null for none
     * @param punisher the name recorded as the author
     * @return see {@link #punish(PunishmentRequest)}
     * @throws IllegalArgumentException when a value cannot be stored (see {@link PunishmentRequest})
     */
    default CompletableFuture<ActionResult<PunishmentView>> tempmute(String target, Duration duration, String reason, String punisher) {
        return punish(PunishmentRequest.builder(PunishmentType.TEMPMUTE, target).duration(duration).reason(reason).punisher(punisher).build());
    }

    /**
     * Kicks a player online on this server, as {@code /kick} from the console would.
     *
     * @param target   the player's name
     * @param reason   the reason; null or blank for none
     * @param punisher the name recorded as the author (at most {@value PunishmentRequest#NAME_MAX_LENGTH} characters)
     * @param silent   not announced to the players
     * @return {@link ActionStatus#SUCCESS}, with the kick as stored in the history only when {@code log-kicks} is on;
     * {@link ActionStatus#TARGET_OFFLINE} or {@link ActionStatus#TARGET_EXEMPT}
     */
    CompletableFuture<ActionResult<PunishmentView>> kick(String target, String reason, String punisher, boolean silent);

    /**
     * Applies a warn as {@code /warn} or {@code /tempwarn} from the console would; the warn tree may then run its
     * commands.
     *
     * @param request what to apply
     * @return {@link ActionStatus#SUCCESS} with the stored warn; {@link ActionStatus#TARGET_EXEMPT} or
     * {@link ActionStatus#FAILED}
     */
    CompletableFuture<ActionResult<WarnView>> warn(WarnRequest request);

    /**
     * Applies a permanent warn.
     *
     * @param target   the player's name
     * @param reason   the reason
     * @param punisher the name recorded as the author
     * @return see {@link #warn(WarnRequest)}
     * @throws IllegalArgumentException when a value cannot be stored (see {@link WarnRequest})
     */
    default CompletableFuture<ActionResult<WarnView>> warn(String target, String reason, String punisher) {
        return warn(WarnRequest.builder(target, reason).punisher(punisher).build());
    }

    // ---- revoking -----------------------------------------------------------------------------------------------

    /**
     * Revokes one ban or mute, as {@code /revoke}: it stays in the history as revoked.
     *
     * @param id        the punishment id
     * @param reason    why; null for none (stored up to 100 characters)
     * @param revokedBy the name of who revokes it
     * @return {@link ActionStatus#SUCCESS} with the revoked punishment; {@link ActionStatus#NOT_FOUND},
     * {@link ActionStatus#ALREADY_REVOKED} or {@link ActionStatus#FAILED}
     */
    CompletableFuture<ActionResult<PunishmentView>> revoke(long id, String reason, String revokedBy);

    /**
     * Revokes every ban in force of an account, as {@code /unban}.
     *
     * @param target    the player's name
     * @param reason    why; null for none
     * @param revokedBy the name of who revokes them
     * @return {@link ActionStatus#SUCCESS} with the revoked bans; {@link ActionStatus#NOT_FOUND} when there was none
     * in force, {@link ActionStatus#FAILED} when none could be revoked
     */
    CompletableFuture<ActionResult<List<PunishmentView>>> unban(String target, String reason, String revokedBy);

    /**
     * Revokes every mute in force of an account, as {@code /unmute}.
     *
     * @param target    the player's name
     * @param reason    why; null for none
     * @param revokedBy the name of who revokes them
     * @return {@link ActionStatus#SUCCESS} with the revoked mutes; {@link ActionStatus#NOT_FOUND} when there was none
     * in force, {@link ActionStatus#FAILED} when none could be revoked
     */
    CompletableFuture<ActionResult<List<PunishmentView>>> unmute(String target, String reason, String revokedBy);

    /**
     * Removes a warn, as {@code /unwarn}: Judicator deletes it, so it no longer counts nor shows in the history.
     *
     * @param id        the warn id
     * @param revokedBy the name of who removes it
     * @return {@link ActionStatus#SUCCESS} with the warn as it was; {@link ActionStatus#NOT_FOUND}
     */
    CompletableFuture<ActionResult<WarnView>> unwarn(long id, String revokedBy);

}
