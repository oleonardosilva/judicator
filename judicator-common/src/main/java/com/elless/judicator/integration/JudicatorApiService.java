package com.elless.judicator.integration;

import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.Judicator;
import com.elless.judicator.api.ActionResult;
import com.elless.judicator.api.ActionStatus;
import com.elless.judicator.api.JudicatorApi;
import com.elless.judicator.api.event.EventBus;
import com.elless.judicator.api.punishment.PunishmentRequest;
import com.elless.judicator.api.punishment.PunishmentView;
import com.elless.judicator.api.warn.WarnRequest;
import com.elless.judicator.api.warn.WarnView;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.punish.PlayerAddress;
import com.elless.judicator.punish.PunishService;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.PunishmentBuilder;
import com.elless.judicator.punish.Revocation;
import com.elless.judicator.punish.data.cache.ImmuneCache;
import com.elless.judicator.punish.handlers.KickHandler;
import com.elless.judicator.punish.handlers.PunishFactory;
import com.elless.judicator.punish.handlers.RevokeHandler;
import com.elless.judicator.punish.rules.PunishCooldown;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.api.jail.JailView;
import com.elless.judicator.api.jail.JailDefinition;
import com.elless.judicator.api.jail.JailEscape;
import com.elless.judicator.api.jail.JailPoint;
import com.elless.judicator.api.note.NoteView;
import com.elless.judicator.api.player.AccountStatus;
import com.elless.judicator.api.player.AccountView;
import com.elless.judicator.api.player.PlayerView;
import com.elless.judicator.api.report.ReportStatus;
import com.elless.judicator.api.report.ReportView;
import com.elless.judicator.api.staff.StaffActivity;
import com.elless.judicator.jails.EscapeRoute;
import com.elless.judicator.jails.Jail;
import com.elless.judicator.jails.JailArea;
import com.elless.judicator.notes.Note;
import com.elless.judicator.notes.NoteHandler;
import com.elless.judicator.platform.Location;
import com.elless.judicator.reports.ReportService;
import com.elless.judicator.reports.ReportState;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import com.elless.judicator.jails.JailService;
import com.elless.judicator.warns.Warn;
import com.elless.judicator.warns.WarnBuilder;
import com.elless.judicator.warns.handlers.UnwarnHandler;
import com.elless.judicator.warns.handlers.WarnHandler;

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
    private static final PunishType[] JAILS = {PunishType.JAIL, PunishType.TEMPJAIL};
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

    @Override
    public CompletableFuture<Optional<PunishmentView>> getActiveJail(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return async(() -> jailOf(judicator.getPunishService().findAllActiveOf(judicator.getPlayerDirectory().refOf(uniqueId), JAILS)));
    }

    @Override
    public CompletableFuture<Optional<PunishmentView>> getActiveJail(String name) {
        Objects.requireNonNull(name, "name");
        return async(() -> jailOf(judicator.getPunishService().findAllActiveOf(judicator.getPlayerDirectory().resolve(name), JAILS)));
    }

    /**
     * The jail that holds the player, before one they escaped from.
     */
    private static Optional<PunishmentView> jailOf(List<Punishment> active) {
        return active.stream().min(Comparator.comparing(Punishment::isEscaped)).map(ApiViews::punishment);
    }

    @Override
    public CompletableFuture<List<JailView>> getJails() {
        return async(() -> judicator.getJailService().all().stream()
                .map(jail -> new JailView(jail.name(), Optional.of(jail.server()).filter(server -> !server.isEmpty()), jail.shared(),
                        jail.capacity(), judicator.getJailService().occupants(jail), jail.escapes().size()))
                .toList());
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
            if (type == PunishType.TEMPJAIL && judicator.getJailService().onlineOnlyByDefault()) builder.onlineOnly(true);
            if (request.type().isJail()) {
                final boolean jailed = judicator.getPunishService().findAllActiveOf(player, PunishType.JAIL, PunishType.TEMPJAIL).stream()
                        .anyMatch(punishment -> !punishment.isEscaped());
                if (jailed) return ActionResult.failure(ActionStatus.ALREADY_JAILED);
                final JailService.Choice choice = judicator.getJailService().choose(request.jail().orElse(null));
                if (choice.jail() == null) return ActionResult.failure(ActionStatus.NO_JAIL);
                builder.jail(choice.jail().name());
            }
            if (request.scope().isPresent()) {
                final Optional<String> scope = judicator.getScopeService().canScope()
                        ? judicator.getScopeService().parse(request.scope().get()) : Optional.empty();
                if (scope.isEmpty()) return ActionResult.failure(ActionStatus.SCOPE_UNKNOWN);
                builder.scope(scope.get());
            }

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
            if (new RevokeHandler(judicator).revoke(List.of(punishment), Revocation.now(stored, revokedBy, null)).isEmpty()) {
                return ActionResult.failure(ActionStatus.FAILED);
            }
            return ActionResult.success(ApiViews.revoked(punishment, stored));
        });
    }

    @Override
    public CompletableFuture<ActionResult<List<PunishmentView>>> unban(String target, String reason, String revokedBy) {
        return revokeActive(target, RevokeHandler.Kind.BAN, reason, revokedBy);
    }

    @Override
    public CompletableFuture<ActionResult<List<PunishmentView>>> unmute(String target, String reason, String revokedBy) {
        return revokeActive(target, RevokeHandler.Kind.MUTE, reason, revokedBy);
    }

    @Override
    public CompletableFuture<ActionResult<List<PunishmentView>>> unjail(String target, String reason, String revokedBy) {
        return revokeActive(target, RevokeHandler.Kind.JAIL, reason, revokedBy);
    }

    private CompletableFuture<ActionResult<List<PunishmentView>>> revokeActive(String target, RevokeHandler.Kind kind, String reason, String revokedBy) {
        Objects.requireNonNull(target, "target");
        checkPunisher(revokedBy);
        return async(() -> {
            final String stored = reason == null ? "" : reason;
            final RevokeHandler.ActiveRevocation revocation = new RevokeHandler(judicator)
                    .revokeActive(target, kind, Revocation.now(stored, revokedBy, null), false);
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
            new UnwarnHandler(judicator).remove(warn.get(), revokedBy);
            return ActionResult.success(ApiViews.warn(warn.get()));
        });
    }


    // ---- jails --------------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<Optional<JailDefinition>> getJail(String name) {
        Objects.requireNonNull(name, "name");
        return async(() -> judicator.getJailService().find(name).map(JudicatorApiService::definition));
    }

    @Override
    public CompletableFuture<ActionResult<JailView>> saveJail(JailDefinition definition, String author) {
        Objects.requireNonNull(definition, "definition");
        checkPunisher(author);
        return async(() -> {
            final Jail jail = jail(definition);
            if (!judicator.getJailService().save(jail, author)) return ActionResult.failure(ActionStatus.FAILED);
            return ActionResult.success(jailView(jail));
        });
    }

    @Override
    public CompletableFuture<ActionResult<JailView>> deleteJail(String name) {
        Objects.requireNonNull(name, "name");
        return async(() -> {
            final Optional<Jail> jail = judicator.getJailService().find(name);
            if (jail.isEmpty()) return ActionResult.failure(ActionStatus.NOT_FOUND);
            if (judicator.getJailService().isInUse(jail.get())) return ActionResult.failure(ActionStatus.IN_USE);
            final JailView view = jailView(jail.get());
            if (!judicator.getJailService().delete(jail.get().name())) return ActionResult.failure(ActionStatus.FAILED);
            return ActionResult.success(view);
        });
    }

    private JailView jailView(Jail jail) {
        return new JailView(jail.name(), Optional.of(jail.server()).filter(server -> !server.isEmpty()), jail.shared(),
                jail.capacity(), judicator.getJailService().occupants(jail), jail.escapes().size());
    }

    private static JailDefinition definition(Jail jail) {
        final JailArea area = jail.area();
        return new JailDefinition(jail.name(), Optional.of(jail.server()).filter(server -> !server.isEmpty()), point(jail.spawn()),
                JailPoint.of(area.world(), area.minX(), area.minY(), area.minZ()),
                JailPoint.of(area.world(), area.maxX(), area.maxY(), area.maxZ()),
                jail.release().map(JudicatorApiService::point), jail.shared(), jail.capacity(),
                jail.escapes().stream().map(route -> new JailEscape(point(route.at()), route.radius(), route.chance())).toList());
    }

    private static Jail jail(JailDefinition definition) {
        final Location spawn = location(definition.spawn());
        return new Jail(definition.name(), definition.server().orElse(""), spawn,
                JailArea.between(location(definition.corner1()), location(definition.corner2())),
                definition.release().map(JudicatorApiService::location), definition.shared(), definition.capacity(),
                definition.escapes().stream().map(escape -> new EscapeRoute(location(escape.at()), escape.radius(), escape.chance())).toList());
    }

    private static JailPoint point(Location location) {
        return new JailPoint(location.world(), location.x(), location.y(), location.z(), location.yaw(), location.pitch());
    }

    private static Location location(JailPoint point) {
        return new Location(point.world(), point.x(), point.y(), point.z(), point.yaw(), point.pitch());
    }

    // ---- accounts -----------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<Optional<PlayerView>> getPlayer(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return async(() -> judicator.getPlayerDirectory().findByUuid(uniqueId)
                .map(known -> new PlayerView(known.uuid(), known.name(), ApiViews.instant(known.lastSeen()))));
    }

    @Override
    public CompletableFuture<AccountStatus> getAccountStatus(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return async(() -> status(judicator.getPlayerDirectory().refOf(uniqueId)));
    }

    @Override
    public CompletableFuture<AccountStatus> getAccountStatus(String name) {
        Objects.requireNonNull(name, "name");
        return async(() -> status(judicator.getPlayerDirectory().resolve(name)));
    }

    @Override
    public CompletableFuture<List<AccountView>> getAlts(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return async(() -> alts(judicator.getPlayerDirectory().refOf(uniqueId)));
    }

    @Override
    public CompletableFuture<List<AccountView>> getAlts(String name) {
        Objects.requireNonNull(name, "name");
        return async(() -> alts(judicator.getPlayerDirectory().resolve(name)));
    }

    private AccountStatus status(PlayerRef player) {
        return AccountStatus.valueOf(judicator.getPunishService().statusOf(player).toUpperCase(java.util.Locale.ROOT));
    }

    private List<AccountView> alts(PlayerRef player) {
        return judicator.getAddressService().findAlts(player, judicator.getAddressPolicy()::isExempt).stream()
                .map(account -> new AccountView(account.name(), Optional.ofNullable(account.uuid()), status(account)))
                .toList();
    }

    // ---- staff --------------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<List<PunishmentView>> getPunishmentsBy(String staff, Instant since, int limit, int offset) {
        Objects.requireNonNull(staff, "staff");
        if (limit < 0 || offset < 0) throw new IllegalArgumentException("limit and offset cannot be negative");
        return async(() -> judicator.getPunishService().findByPunisher(judicator.getPlayerDirectory().resolveKnown(staff),
                since == null ? null : local(since), limit, offset).stream().map(ApiViews::punishment).toList());
    }

    @Override
    public CompletableFuture<List<StaffActivity>> getStaffActivity(Instant since) {
        Objects.requireNonNull(since, "since");
        return async(() -> {
            final LocalDateTime from = local(since);
            final Map<String, String> names = new LinkedHashMap<>();
            final Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
            Stream.concat(judicator.getPunishService().countByStaffSince(from).stream(),
                    judicator.getWarnService().countByStaffSince(from).stream()).forEach(count -> {
                final String key = count.punisher().toLowerCase(java.util.Locale.ROOT);
                names.putIfAbsent(key, count.punisher());
                counts.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).merge(count.type(), count.count(), Integer::sum);
            });
            return counts.entrySet().stream()
                    .map(entry -> new StaffActivity(names.get(entry.getKey()), entry.getValue()))
                    .sorted(Comparator.comparingInt(StaffActivity::total).reversed()
                            .thenComparing(StaffActivity::staff, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        });
    }

    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    // ---- reports ------------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<Optional<ReportView>> getReport(long id) {
        return async(() -> judicator.getReportService().findById(id).map(ApiViews::report));
    }

    @Override
    public CompletableFuture<List<ReportView>> getReports(Set<ReportStatus> statuses, int limit, int offset) {
        Objects.requireNonNull(statuses, "statuses");
        if (limit < 0 || offset < 0) throw new IllegalArgumentException("limit and offset cannot be negative");
        return async(() -> statuses.isEmpty() ? List.<ReportView>of()
                : judicator.getReportService().find(states(statuses), limit, offset).stream().map(ApiViews::report).toList());
    }

    @Override
    public CompletableFuture<Integer> countReports(Set<ReportStatus> statuses) {
        Objects.requireNonNull(statuses, "statuses");
        return async(() -> statuses.isEmpty() ? 0 : judicator.getReportService().count(states(statuses)));
    }

    @Override
    public CompletableFuture<Integer> countReportsAgainst(String name) {
        Objects.requireNonNull(name, "name");
        return async(() -> judicator.getReportService().countAgainst(judicator.getPlayerDirectory().resolve(name)));
    }

    private static List<ReportState> states(Set<ReportStatus> statuses) {
        return statuses.stream().map(status -> ReportState.valueOf(status.name())).toList();
    }

    @Override
    public CompletableFuture<ActionResult<ReportView>> report(String reporter, String target, String reason) {
        checkPunisher(reporter);
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) throw new IllegalArgumentException("A report needs a reason");
        return async(() -> {
            final ReportService.Creation creation = judicator.getReportService().reportAs(reporter, target, reason);
            return switch (creation.outcome()) {
                case CREATED -> ActionResult.success(ApiViews.report(creation.report()));
                case EXEMPT -> ActionResult.failure(ActionStatus.TARGET_EXEMPT);
                case DUPLICATE -> ActionResult.failure(ActionStatus.ALREADY_REPORTED);
                case UNKNOWN_PLAYER -> ActionResult.failure(ActionStatus.NOT_FOUND);
                default -> ActionResult.failure(ActionStatus.FAILED);
            };
        });
    }

    @Override
    public CompletableFuture<ActionResult<ReportView>> closeReport(long id, String comment, String closedBy) {
        checkPunisher(closedBy);
        return async(() -> {
            final ReportService.Change change = judicator.getReportService().close(new ApiSender(closedBy), id, comment);
            return switch (change) {
                case DONE -> judicator.getReportService().findById(id)
                        .<ActionResult<ReportView>>map(report -> ActionResult.success(ApiViews.report(report)))
                        .orElseGet(() -> ActionResult.failure(ActionStatus.NOT_FOUND));
                case NOT_FOUND -> ActionResult.failure(ActionStatus.NOT_FOUND);
                case CLOSED -> ActionResult.failure(ActionStatus.ALREADY_CLOSED);
                default -> ActionResult.failure(ActionStatus.FAILED);
            };
        });
    }

    // ---- staff notes --------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<List<NoteView>> getNotes(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return async(() -> notes(judicator.getPlayerDirectory().refOf(uniqueId)));
    }

    @Override
    public CompletableFuture<List<NoteView>> getNotes(String name) {
        Objects.requireNonNull(name, "name");
        return async(() -> notes(judicator.getPlayerDirectory().resolve(name)));
    }

    private List<NoteView> notes(PlayerRef player) {
        final int count = judicator.getNoteService().countOf(player);
        return count == 0 ? List.of() : judicator.getNoteService().findOf(player, count, 0).stream().map(ApiViews::note).toList();
    }

    @Override
    public CompletableFuture<ActionResult<NoteView>> addNote(String target, String content, String author) {
        checkPunisher(author);
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");
        if (target.isBlank() || target.length() > PunishmentRequest.NAME_MAX_LENGTH) throw new IllegalArgumentException("Not a player name: " + target);
        if (content.isBlank() || content.trim().length() > NoteHandler.CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("A note is 1 to " + NoteHandler.CONTENT_MAX_LENGTH + " characters");
        }
        return async(() -> ActionResult.success(ApiViews.note(new NoteHandler(judicator)
                .add(judicator.getPlayerDirectory().resolve(target), content.trim(), author, null))));
    }

    @Override
    public CompletableFuture<ActionResult<NoteView>> deleteNote(long id, String deletedBy) {
        checkPunisher(deletedBy);
        return async(() -> {
            final Optional<Note> note = judicator.getNoteService().findById(id);
            if (note.isEmpty()) return ActionResult.failure(ActionStatus.NOT_FOUND);
            new NoteHandler(judicator).delete(note.get(), deletedBy);
            return ActionResult.success(ApiViews.note(note.get()));
        });
    }

}
