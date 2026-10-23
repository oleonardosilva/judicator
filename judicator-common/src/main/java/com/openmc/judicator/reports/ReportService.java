package com.openmc.judicator.reports;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.data.cache.ImmuneCache;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.reports.repository.ReportRepository;
import com.openmc.judicator.sync.SyncKind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Player reports: {@code /report} creates them, the staff handles them with {@code /reports} (assign, give back,
 * close). The staff with {@code judicator.reports.notify} hears of new ones, and the reporter hears when theirs is taken
 * or closed, on this server and, with {@code sync}, on the others.
 */
public class ReportService {

    public static final int REASON_MAX_LENGTH = 255;
    private static final int NAME_MAX_LENGTH = 20;

    /**
     * Why {@link #report} did or did not create a report.
     */
    public enum Outcome {
        CREATED, SELF, EXEMPT, UNKNOWN_PLAYER, OFFLINE, COOLDOWN, DUPLICATE, FAILED
    }

    public record Creation(Outcome outcome, Report report, long secondsLeft) {
    }

    /**
     * What happened to a staff action on a report.
     */
    public enum Change {
        DONE, NOT_FOUND, CLOSED, NOT_ASSIGNED, CHANGED_MEANWHILE
    }

    private final Judicator judicator;
    private final ReportRepository repository;
    private final Map<UUID, Long> lastReport = new ConcurrentHashMap<>();
    /** the state a reporter was last told about, so two notices read together do not tell them twice */
    private final Cache<Long, ReportState> told = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(10_000).build();

    public ReportService(Judicator judicator, ReportRepository repository) {
        this.judicator = judicator;
        this.repository = repository;
        repository.initialize();
    }

    /**
     * A player reports another. The reported player must be online unless the reporter has
     * {@code judicator.report.offline}.
     */
    public Creation report(JudicatorPlayer reporter, String targetName, String reason) {
        final Optional<JudicatorPlayer> online = judicator.getPlatform().getPlayer(targetName);
        if (online.isEmpty() && !reporter.hasPermission(PunishPermissions.REPORT_OFFLINE.getPermission())
                && !reporter.hasPermission(PunishPermissions.ADMIN.getPermission())) {
            return new Creation(Outcome.OFFLINE, null, 0);
        }
        if (targetName.length() > NAME_MAX_LENGTH) return new Creation(Outcome.UNKNOWN_PLAYER, null, 0);
        final PlayerRef target = judicator.getPlayerDirectory().resolve(targetName);
        if (judicator.getPlayerDirectory().refOf(reporter).matches(target.uuid(), target.name())) return new Creation(Outcome.SELF, null, 0);
        // with trusted UUIDs a report is about an account: a name with no account is a typo
        if (judicator.trustsUuids() && !target.identifiedByUuid()) return new Creation(Outcome.UNKNOWN_PLAYER, null, 0);

        // taken before anything else is checked: several reports sent at once wait like any other, and finding out who
        // is exempt costs a cooldown each time
        final long secondsLeft = claimCooldown(reporter.getUniqueId());
        if (secondsLeft > 0) return new Creation(Outcome.COOLDOWN, null, secondsLeft);
        if (judicator.getImmuneCache().isExemptFrom(target.name(), target.uuid(), ImmuneCache.REPORT)) return new Creation(Outcome.EXEMPT, null, 0);
        if (repository.hasOpen(reporter.getUniqueId(), target)) return new Creation(Outcome.DUPLICATE, null, 0);

        final String server = online.flatMap(player -> judicator.getScopeService().serverOf(player)).orElse(null);
        final Report report = repository.insert(Report.create(reporter.getUniqueId(), reporter.getName(), target.uuid(), target.name(),
                storedText(reason), server));
        if (report.getId() == null) {
            lastReport.remove(reporter.getUniqueId());
            return new Creation(Outcome.FAILED, null, 0);
        }
        notifyStaff(report);
        judicator.getSyncService().publish(SyncKind.REPORTED, report.getId());
        judicator.getDiscordWebhookService().sendReport(report);
        return new Creation(Outcome.CREATED, report, 0);
    }

    /**
     * @return 0 when the reporter may report now (the cooldown starts), else the seconds left
     */
    private long claimCooldown(UUID reporter) {
        final long now = System.currentTimeMillis();
        final long cooldown = Math.max(0, judicator.getConfig().node("reports", "cooldown").getLong(60)) * 1000;
        final long[] left = {0};
        lastReport.compute(reporter, (key, last) -> {
            if (last != null && now - last < cooldown) {
                left[0] = (cooldown - (now - last) + 999) / 1000;
                return last;
            }
            return now;
        });
        return left[0];
    }

    /**
     * Player text as it is stored: without the section sign (no colour or formatting codes) and cut to what the column
     * holds, never in the middle of a character outside the basic plane.
     */
    static String storedText(String text) {
        final String clean = text.replace("§", "").trim();
        if (clean.length() <= REASON_MAX_LENGTH) return clean;
        final int end = Character.isHighSurrogate(clean.charAt(REASON_MAX_LENGTH - 1)) ? REASON_MAX_LENGTH - 1 : REASON_MAX_LENGTH;
        return clean.substring(0, end);
    }

    public Optional<Report> findById(long id) {
        return repository.findById(id);
    }

    public int count(Collection<ReportState> states) {
        return repository.countByStates(states);
    }

    public List<Report> find(Collection<ReportState> states, int limit, int offset) {
        return repository.findByStates(states, limit, offset);
    }

    public int countAgainst(PlayerRef target) {
        return repository.countAgainst(target);
    }

    /**
     * Gives a report to a staff member.
     *
     * @param assignee who handles it; the one assigning when it is themselves
     */
    public Change assign(JudicatorSender actor, long id, JudicatorPlayer assignee) {
        return change(id, actor, report -> {
            if (!report.getState().isOpen()) return Change.CLOSED;
            report.setState(ReportState.ASSIGNED);
            report.setAssigneeUuid(assignee.getUniqueId());
            report.setAssigneeName(assignee.getName());
            return Change.DONE;
        });
    }

    /**
     * Gives an assigned report back to the staff (open again).
     */
    public Change unassign(JudicatorSender actor, long id) {
        return change(id, actor, report -> {
            if (!report.getState().isOpen()) return Change.CLOSED;
            if (report.getState() != ReportState.ASSIGNED) return Change.NOT_ASSIGNED;
            report.setState(ReportState.OPEN);
            report.setAssigneeUuid(null);
            report.setAssigneeName(null);
            return Change.DONE;
        });
    }

    /**
     * @param comment what was done; may be empty
     */
    public Change close(JudicatorSender actor, long id, String comment) {
        return change(id, actor, report -> {
            if (!report.getState().isOpen()) return Change.CLOSED;
            report.setState(ReportState.CLOSED);
            report.setClosedAt(LocalDateTime.now());
            report.setCloseComment(comment == null || comment.isBlank() ? null : storedText(comment));
            if (report.getAssigneeName().isEmpty()) report.setAssigneeName(actor.getName());
            return Change.DONE;
        });
    }

    private interface Edit {
        Change apply(Report report);
    }

    private Change change(long id, JudicatorSender actor, Edit edit) {
        final Optional<Report> found = repository.findById(id);
        if (found.isEmpty()) return Change.NOT_FOUND;
        final Report report = found.get();
        final ReportState before = report.getState();
        final Change change = edit.apply(report);
        if (change != Change.DONE) return change;
        report.setUpdatedAt(LocalDateTime.now());
        if (!repository.update(report, before)) return Change.CHANGED_MEANWHILE;
        if (report.getState() == ReportState.ASSIGNED) tellAssignee(report, actor);
        tellReporter(report);
        judicator.getSyncService().publish(SyncKind.REPORT_CHANGED, report.getId());
        return Change.DONE;
    }

    /**
     * A notice of another server sharing the database ({@code sync}).
     */
    public void received(SyncKind kind, long id) {
        final Optional<Report> report = repository.findById(id);
        if (report.isEmpty()) return;
        if (kind == SyncKind.REPORTED) notifyStaff(report.get());
        else if (kind == SyncKind.REPORT_CHANGED) tellReporter(report.get());
    }

    /**
     * {@code reports.notify-on-join}: a staff member who joins hears how many reports wait.
     */
    public void notifyOnJoin(JudicatorPlayer staff) {
        if (!judicator.getConfig().node("reports", "notify-on-join").getBoolean(true)) return;
        if (!staff.hasPermission(PunishPermissions.REPORTS_NOTIFY.getPermission())) return;
        final int open = repository.countByStates(List.of(ReportState.OPEN));
        if (open == 0) return;
        final ConfigurationNode messages = judicator.messagesFor(staff);
        staff.sendMessage(PunishUtils.format(messages, Map.of("count", String.valueOf(open)), "reports", "open-on-join")
                .hoverEvent(HoverEvent.showText(PunishUtils.format(messages, Map.of(), "reports", "open-on-join-hover")))
                .clickEvent(ClickEvent.runCommand("/reports")));
    }

    private void notifyStaff(Report report) {
        // behind a proxy running Judicator, the proxy tells the network
        if (judicator.isProxyMode()) return;
        judicator.getStaffNotifier().sendToPermission(PunishPermissions.REPORTS_NOTIFY.getPermission(), messages ->
                PunishUtils.format(messages, placeholders(messages, report), "reports", "notify")
                        .hoverEvent(HoverEvent.showText(PunishUtils.format(messages, Map.of("id", String.valueOf(report.getId())), "reports", "notify-hover")))
                        .clickEvent(ClickEvent.runCommand("/reports info " + report.getId())));
    }

    private void tellAssignee(Report report, JudicatorSender actor) {
        if (judicator.isProxyMode() || report.getAssigneeUuid().isEmpty()) return;
        if (actor.asPlayer().map(player -> player.getUniqueId().equals(report.getAssigneeUuid().get())).orElse(false)) return;
        judicator.getPlatform().getPlayer(report.getAssigneeUuid().get()).ifPresent(assignee -> {
            final ConfigurationNode messages = judicator.messagesFor(assignee);
            final Map<String, String> placeholders = placeholders(messages, report);
            placeholders.put("actor", actor.getName());
            assignee.sendMessage(PunishUtils.format(messages, placeholders, "reports", "assigned-to-you")
                    .clickEvent(ClickEvent.runCommand("/reports info " + report.getId())));
        });
    }

    /**
     * Tells the reporter, if online here, that their report was taken or closed (once per state).
     */
    private void tellReporter(Report report) {
        if (judicator.isProxyMode() || report.getState() == ReportState.OPEN) return;
        judicator.getPlatform().getPlayer(report.getReporterUuid()).ifPresent(reporter -> {
            if (told.getIfPresent(report.getId()) == report.getState()) return;
            told.put(report.getId(), report.getState());
            final ConfigurationNode messages = judicator.messagesFor(reporter);
            final String key = report.getState() == ReportState.CLOSED ? "reporter-closed" : "reporter-assigned";
            reporter.sendMessage(PunishUtils.format(messages, placeholders(messages, report), "reports", key));
        });
    }

    /**
     * The placeholders of a report's messages: {@code {id} {reporter} {target} {reason} {server} {state} {assignee}
     * {createdAt} {closedAt} {comment}}.
     */
    public static Map<String, String> placeholders(ConfigurationNode messages, Report report) {
        final String none = messages.node("reports", "none-value").getString("-").replace("&", "§");
        final DateTimeFormatter dates = DateTimeFormatter.ofPattern(messages.node("date-format").getString("dd/MM/yyyy HH:mm"));
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", String.valueOf(report.getId()));
        placeholders.put("reporter", report.getReporterName());
        placeholders.put("target", report.getTargetName());
        placeholders.put("reason", report.getReason());
        placeholders.put("server", report.getServer().orElse(none));
        placeholders.put("state", messages.node("reports", "states", report.getState().key()).getString(report.getState().key()).replace("&", "§"));
        placeholders.put("assignee", report.getAssigneeName().orElse(none));
        placeholders.put("createdAt", dates.format(report.getCreatedAt()));
        placeholders.put("closedAt", report.getClosedAt().map(dates::format).orElse(none));
        placeholders.put("comment", report.getCloseComment().orElse(none));
        return placeholders;
    }

    /**
     * How many reports a player made or got.
     */
    public int countAllOf(PlayerRef player) {
        return repository.countAllOf(player);
    }

    public int deleteAllOf(PlayerRef player) {
        return repository.deleteAllOf(player);
    }

    public int deleteClosedBefore(LocalDateTime cutoff) {
        return repository.deleteClosedBefore(cutoff);
    }

    /**
     * The message shown for a {@link Change} other than {@link Change#DONE}.
     */
    public static Component describe(ConfigurationNode messages, Change change, long id) {
        final String key = switch (change) {
            case NOT_FOUND -> "not-found";
            case CLOSED -> "already-closed";
            case NOT_ASSIGNED -> "not-assigned";
            case CHANGED_MEANWHILE -> "changed-meanwhile";
            case DONE -> "done";
        };
        return PunishUtils.format(messages, Map.of("id", String.valueOf(id)), "reports", key);
    }
}
