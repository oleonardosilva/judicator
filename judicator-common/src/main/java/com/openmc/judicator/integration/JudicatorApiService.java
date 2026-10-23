package com.openmc.judicator.integration;

import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.api.ActionResult;
import com.openmc.judicator.api.ActionStatus;
import com.openmc.judicator.api.JudicatorApi;
import com.openmc.judicator.api.event.EventBus;
import com.openmc.judicator.api.punishment.PunishmentRequest;
import com.openmc.judicator.api.punishment.PunishmentView;
import com.openmc.judicator.api.warn.WarnRequest;
import com.openmc.judicator.api.warn.WarnView;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.punish.PlayerAddress;
import com.openmc.judicator.punish.PunishService;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.data.cache.ImmuneCache;
import com.openmc.judicator.punish.handlers.KickHandler;
import com.openmc.judicator.punish.handlers.PunishFactory;
import com.openmc.judicator.punish.handlers.RevokeHandler;
import com.openmc.judicator.punish.rules.PunishCooldown;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.WarnBuilder;
import com.openmc.judicator.warns.handlers.UnwarnHandler;
import com.openmc.judicator.warns.handlers.WarnHandler;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * {@link JudicatorApi} on top of the core: the same handlers and services the commands use, run on the platform's
 * asynchronous tasks. The caller is treated as the console by the staff rules ({@link ApiSender}).
 */
public class JudicatorApiService implements JudicatorApi {

    private static final PunishType[] BANS = {PunishType.BAN, PunishType.TEMPBAN};
    private static final PunishType[] MUTES = {PunishType.MUTE, PunishType.TEMPMUTE};
    private static final Comparator<Punishment> NEWEST_FIRST = Comparator.comparing(Punishment::getStartedAt)
            .thenComparing(Punishment::getId).reversed();
    private static final Comparator<Warn> NEWEST_WARN_FIRST = Comparator.comparing(Warn::getStartedAt)
            .thenComparing(Warn::getId).reversed();

    private final Judicator judicator;

    public JudicatorApiService(Judicator judicator) {
        this.judicator = judicator;
    }

    @Override
    public EventBus getEventBus() {
        return judicator.getEventBus();
    }

    private <T> CompletableFuture<T> async(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, judicator.getPlatform()::runAsync);
    }

    // ---- lookups ------------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<Optional<PunishmentView>> getActiveBan(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return async(() -> heaviest(judicator.getPunishService().findAllActiveOf(judicator.getPlayerDirectory().refOf(uniqueId), BANS)));
    }

    @Override
    public CompletableFuture<Optional<PunishmentView>> getActiveBan(String name) {
        Objects.requireNonNull(name, "name");
        return async(() -> heaviest(judicator.getPunishService().findAllActiveOf(judicator.getPlayerDirectory().resolve(name), BANS)));
    }

    @Override
    public CompletableFuture<Optional<PunishmentView>> getActiveMute(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return async(() -> heaviest(judicator.getPunishService().findAllActiveOf(judicator.getPlayerDirectory().refOf(uniqueId), MUTES)));
    }

    @Override
    public CompletableFuture<Optional<PunishmentView>> getActiveMute(String name) {
        Objects.requireNonNull(name, "name");
        return async(() -> heaviest(judicator.getPunishService().findAllActiveOf(judicator.getPlayerDirectory().resolve(name), MUTES)));
    }

    private static Optional<PunishmentView> heaviest(List<Punishment> active) {
        return active.stream().min(PunishService.heavyFirst()).map(ApiViews::punishment);
    }

    @Override
    public CompletableFuture<List<PunishmentView>> getPunishmentHistory(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return async(() -> punishments(judicator.getPunishService().findAllOf(judicator.getPlayerDirectory().refOf(uniqueId))));
    }

    @Override
    public CompletableFuture<List<PunishmentView>> getPunishmentHistory(String name) {
        Objects.requireNonNull(name, "name");
        return async(() -> punishments(judicator.getPunishService().findAllOf(judicator.getPlayerDirectory().resolve(name))));
    }

    private static List<PunishmentView> punishments(List<Punishment> punishments) {
        return punishments.stream().sorted(NEWEST_FIRST).map(ApiViews::punishment).toList();
    }

    @Override
    public CompletableFuture<Optional<PunishmentView>> getPunishment(long id) {
        return async(() -> judicator.getPunishService().findById(id).map(ApiViews::punishment));
    }

    @Override
    public CompletableFuture<List<WarnView>> getWarns(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return async(() -> warns(judicator.getWarnService().findAllOf(judicator.getPlayerDirectory().refOf(uniqueId))));
    }

    @Override
    public CompletableFuture<List<WarnView>> getWarns(String name) {
        Objects.requireNonNull(name, "name");
        return async(() -> warns(judicator.getWarnService().findAllOf(judicator.getPlayerDirectory().resolve(name))));
    }

    private static List<WarnView> warns(List<Warn> warns) {
        return warns.stream().sorted(NEWEST_WARN_FIRST).map(ApiViews::warn).toList();
    }

    @Override
    public CompletableFuture<Optional<WarnView>> getWarn(long id) {
        return async(() -> judicator.getWarnService().findById(id).map(ApiViews::warn));
    }

    // ---- applying -----------------------------------------------------------------------------------------------

    /**
     * {@code PunishTypeCommand} without the staff rules: exemptions, then the target (and address), then
     * {@link PunishFactory}, which stores it and does everything a confirmed command does.
     */
    @Override
    public CompletableFuture<ActionResult<PunishmentView>> punish(PunishmentRequest request) {
        Objects.requireNonNull(request, "request");
        return async(() -> {
            final PunishType type = PunishType.valueOf(request.type().name());
            final String category = PunishCooldown.categoryOf(type);
            final String target = request.target();
            final ApiSender sender = new ApiSender(request.punisher());
            final PlayerRef player = judicator.getPlayerDirectory().resolve(target);
            if (!judicator.getImmuneCache().canPunish(sender, player, category)) return ActionResult.failure(ActionStatus.TARGET_EXEMPT);

            final PunishmentBuilder builder = new PunishmentBuilder()
                    .type(type)
                    .reason(request.reason().orElse(""))
                    .punisher(request.punisher())
                    .silent(request.silent())
                    .soft(request.soft())
                    .onlineOnly(request.onlineOnly())
                    .evidences(request.evidences());
            request.duration().ifPresent(builder::duration);

            final Optional<JudicatorPlayer> online = judicator.getPlatform().getPlayer(target);
            builder.target(player);
            String address = null;
            if (online.isPresent()) {
                if (request.byAddress()) address = online.get().getHostAddress();
            } else if (request.byAddress()) {
                address = judicator.getAddressService().findLast(player).map(PlayerAddress::hostAddress).orElse(null);
                if (address == null) return ActionResult.failure(ActionStatus.ADDRESS_UNKNOWN);
            }
            if (address != null) {
                if (judicator.getAddressPolicy().isExempt(address)) return ActionResult.failure(ActionStatus.ADDRESS_EXEMPT);
                if (!judicator.getImmuneCache().canPunishAddress(sender, address, category)) {
                    return ActionResult.failure(ActionStatus.TARGET_EXEMPT);
                }
                builder.ipAddress(address);
            }

            return new PunishFactory(judicator, builder).factory()
                    .map(punishment -> ActionResult.success(ApiViews.punishment(punishment)))
                    .orElseGet(() -> ActionResult.failure(ActionStatus.FAILED));
        });
    }

    @Override
    public CompletableFuture<ActionResult<PunishmentView>> kick(String target, String reason, String punisher, boolean silent) {
        Objects.requireNonNull(target, "target");
        checkPunisher(punisher);
        return async(() -> {
            if (!judicator.getImmuneCache().canPunish(new ApiSender(punisher), target, ImmuneCache.KICK)) {
                return ActionResult.failure(ActionStatus.TARGET_EXEMPT);
            }
            final Optional<JudicatorPlayer> player = judicator.getPlatform().getPlayer(target);
            if (player.isEmpty()) return ActionResult.failure(ActionStatus.TARGET_OFFLINE);
            judicator.getPunishCooldown().record(PunishCooldown.KICK, target);
            final String text = reason == null || reason.isBlank() ? "None" : reason;
            return ActionResult.success(new KickHandler(judicator).handle(player.get(), punisher, text, silent)
                    .map(ApiViews::punishment).orElse(null));
        });
    }

    private static void checkPunisher(String punisher) {
        Objects.requireNonNull(punisher, "punisher");
        if (punisher.isBlank() || punisher.length() > PunishmentRequest.NAME_MAX_LENGTH || punisher.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("punisher must be a name of 1 to " + PunishmentRequest.NAME_MAX_LENGTH
                    + " characters without spaces: " + punisher);
        }
    }

    /**
     * {@code WarnCommand} without the staff rules.
     */
    @Override
    public CompletableFuture<ActionResult<WarnView>> warn(WarnRequest request) {
        Objects.requireNonNull(request, "request");
        return async(() -> {
            final String target = request.target();
            if (!judicator.getImmuneCache().canPunish(new ApiSender(request.punisher()), target, ImmuneCache.WARN)) {
                return ActionResult.failure(ActionStatus.TARGET_EXEMPT);
            }
            final WarnBuilder builder = new WarnBuilder()
                    .reason(request.reason())
                    .punisher(request.punisher())
                    .silent(request.silent());
            request.duration().ifPresent(builder::duration);
            builder.target(judicator.getPlayerDirectory().resolve(target));

            judicator.getPunishCooldown().record(PunishCooldown.WARN, target);
            final Optional<Warn> warn = new WarnHandler(judicator, builder).handle();
            if (warn.isEmpty()) {
                judicator.getPunishCooldown().release(PunishCooldown.WARN, target);
                return ActionResult.failure(ActionStatus.FAILED);
            }
            return ActionResult.success(ApiViews.warn(warn.get()));
        });
    }

    // ---- revoking -----------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<ActionResult<PunishmentView>> revoke(long id, String reason, String revokedBy) {
        checkPunisher(revokedBy);
        return async(() -> {
            final Optional<Punishment> found = judicator.getPunishService().findById(id);
            if (found.isEmpty()) return ActionResult.failure(ActionStatus.NOT_FOUND);
            final Punishment punishment = found.get();
            if (punishment.isRevoked()) return ActionResult.failure(ActionStatus.ALREADY_REVOKED);
            final String stored = reason == null ? "" : reason;
            if (new RevokeHandler(judicator).revoke(List.of(punishment), stored, revokedBy, true).isEmpty()) {
                return ActionResult.failure(ActionStatus.FAILED);
            }
            return ActionResult.success(ApiViews.revoked(punishment, stored));
        });
    }

    @Override
    public CompletableFuture<ActionResult<List<PunishmentView>>> unban(String target, String reason, String revokedBy) {
        return revokeActive(target, false, reason, revokedBy);
    }

    @Override
    public CompletableFuture<ActionResult<List<PunishmentView>>> unmute(String target, String reason, String revokedBy) {
        return revokeActive(target, true, reason, revokedBy);
    }

    private CompletableFuture<ActionResult<List<PunishmentView>>> revokeActive(String target, boolean mute, String reason, String revokedBy) {
        Objects.requireNonNull(target, "target");
        checkPunisher(revokedBy);
        return async(() -> {
            final String stored = reason == null ? "" : reason;
            final RevokeHandler.ActiveRevocation revocation = new RevokeHandler(judicator)
                    .revokeActive(target, mute, stored, revokedBy, false);
            if (!revocation.found()) return ActionResult.failure(ActionStatus.NOT_FOUND);
            if (revocation.revoked().isEmpty()) return ActionResult.failure(ActionStatus.FAILED);
            return ActionResult.success(revocation.revoked().stream()
                    .map(punishment -> ApiViews.revoked(punishment, stored))
                    .toList());
        });
    }

    @Override
    public CompletableFuture<ActionResult<WarnView>> unwarn(long id, String revokedBy) {
        checkPunisher(revokedBy);
        return async(() -> {
            final Optional<Warn> warn = judicator.getWarnService().findById(id);
            if (warn.isEmpty()) return ActionResult.failure(ActionStatus.NOT_FOUND);
            new UnwarnHandler(judicator).remove(warn.get(), revokedBy, true);
            return ActionResult.success(ApiViews.warn(warn.get()));
        });
    }

}
