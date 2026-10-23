package com.openmc.judicator.reports.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.Pagination;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.reports.Report;
import com.openmc.judicator.reports.ReportService;
import com.openmc.judicator.reports.ReportState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * {@code /reports [list [open|assigned|closed|all]] [page]}, {@code /reports info <id>}, {@code assign <id> [staff]},
 * {@code unassign <id>}, {@code close <id> [comment]} and {@code tp <id>}.
 */
public class ReportsCommand extends CoreCommand {

    /**
     * What {@code /reports list} can show; "open" is every report not closed yet.
     */
    private static final Map<String, Set<ReportState>> FILTERS = new LinkedHashMap<>();

    static {
        FILTERS.put("open", Set.of(ReportState.OPEN, ReportState.ASSIGNED));
        FILTERS.put("assigned", Set.of(ReportState.ASSIGNED));
        FILTERS.put("closed", Set.of(ReportState.CLOSED));
        FILTERS.put("all", Set.of(ReportState.values()));
    }

    private static final List<String> SUBCOMMANDS = List.of("list", "info", "assign", "unassign", "close", "tp");

    public ReportsCommand(Judicator judicator) {
        super(judicator, "reports", List.of(), PunishPermissions.REPORTS.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> sub = args.word();
        if (sub.isEmpty() || sub.get().chars().allMatch(Character::isDigit)) {
            list(source, "open", Pagination.parsePage(sub));
            return;
        }
        switch (sub.get().toLowerCase(Locale.ROOT)) {
            case "list" -> {
                final Optional<String> filter = args.word();
                if (filter.isPresent() && filter.get().chars().allMatch(Character::isDigit)) {
                    list(source, "open", Pagination.parsePage(filter));
                } else {
                    list(source, filter.orElse("open").toLowerCase(Locale.ROOT), Pagination.parsePage(args.word()));
                }
            }
            case "info" -> withReport(source, args, report -> info(source, report));
            case "assign" -> {
                if (!may(source, PunishPermissions.REPORTS_MANAGE)) return;
                withId(source, args, id -> assign(source, id, args.word()));
            }
            case "unassign" -> {
                if (!may(source, PunishPermissions.REPORTS_MANAGE)) return;
                withId(source, args, id -> done(source, judicator.getReportService().unassign(source, id), id, "unassigned", Map.of()));
            }
            case "close" -> {
                if (!may(source, PunishPermissions.REPORTS_MANAGE)) return;
                withId(source, args, id -> done(source, judicator.getReportService().close(source, id, args.remaining()), id, "closed", Map.of()));
            }
            case "tp" -> {
                if (!may(source, PunishPermissions.REPORTS_TP)) return;
                withReport(source, args, report -> teleport(source, report));
            }
            default -> sendUsage(source, getName());
        }
    }

    private boolean may(JudicatorSender source, PunishPermissions permission) {
        if (source.hasPermission(permission.getPermission()) || source.hasPermission(PunishPermissions.ADMIN.getPermission())) return true;
        source.sendMessage(PunishUtils.getMessage(messages(source), "reports", "no-permission"));
        return false;
    }

    private interface IdAction {
        void run(long id);
    }

    private interface ReportAction {
        void run(Report report);
    }

    private void withId(JudicatorSender source, CommandArgs args, IdAction action) {
        final Optional<String> raw = args.word();
        if (raw.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        final Optional<Long> id = PunishUtils.parseId(raw.get());
        if (id.isEmpty()) {
            source.sendMessage(PunishUtils.format(messages(source), Map.of("id", raw.get()), "reports", "invalid-id"));
            return;
        }
        action.run(id.get());
    }

    private void withReport(JudicatorSender source, CommandArgs args, ReportAction action) {
        withId(source, args, id -> judicator.getReportService().findById(id).ifPresentOrElse(action::run,
                () -> source.sendMessage(PunishUtils.format(messages(source), Map.of("id", String.valueOf(id)), "reports", "not-found"))));
    }

    private void list(JudicatorSender source, String filter, OptionalInt requested) {
        final ConfigurationNode messages = messages(source);
        final Set<ReportState> states = FILTERS.get(filter);
        if (states == null) {
            source.sendMessage(PunishUtils.format(messages, Map.of("state", filter), "reports", "invalid-state"));
            return;
        }
        if (requested.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        final ReportService service = judicator.getReportService();
        final int total = service.count(states);
        if (total == 0) {
            source.sendMessage(PunishUtils.getMessage(messages, "reports", "none"));
            return;
        }
        final int pages = Pagination.pages(total);
        final int page = Pagination.clamp(requested.getAsInt(), pages);
        final String filterName = messages.node("reports", "filters", filter).getString(filter).replace("&", "§");
        final TextComponent.Builder text = Component.text().append(PunishUtils.format(messages, Map.of(
                "filter", filterName,
                "count", String.valueOf(total),
                "page", String.valueOf(page),
                "pages", String.valueOf(pages)), "reports", "title"));
        for (Report report : service.find(states, Pagination.PAGE_SIZE, Pagination.offset(page))) {
            final Map<String, String> placeholders = ReportService.placeholders(messages, report);
            text.append(PunishUtils.format(messages, placeholders, "reports", "line")
                    .hoverEvent(HoverEvent.showText(PunishUtils.format(messages, placeholders, "reports", "hover")))
                    .clickEvent(ClickEvent.runCommand("/reports info " + report.getId())));
        }
        text.append(Pagination.footer(messages, page, pages, target -> "/reports list " + filter + " " + target));
        source.sendMessage(text.build());
    }

    private void info(JudicatorSender source, Report report) {
        final ConfigurationNode messages = messages(source);
        final Map<String, String> placeholders = ReportService.placeholders(messages, report);
        final TextComponent.Builder text = Component.text().append(PunishUtils.format(messages, placeholders, "reports", "info"));
        final boolean manages = source.hasPermission(PunishPermissions.REPORTS_MANAGE.getPermission())
                || source.hasPermission(PunishPermissions.ADMIN.getPermission());
        if (report.getState().isOpen() && manages) {
            text.append(button(messages, "assign", "/reports assign " + report.getId()));
            text.append(button(messages, "close", "/reports close " + report.getId()));
        }
        if (source.isPlayer() && (source.hasPermission(PunishPermissions.REPORTS_TP.getPermission())
                || source.hasPermission(PunishPermissions.ADMIN.getPermission()))) {
            text.append(button(messages, "tp", "/reports tp " + report.getId()));
        }
        source.sendMessage(text.build());
    }

    private static Component button(ConfigurationNode messages, String key, String command) {
        return PunishUtils.format(messages, Map.of(), "reports", "buttons", key)
                .hoverEvent(HoverEvent.showText(Component.text(command)))
                .clickEvent(key.equals("close") ? ClickEvent.suggestCommand(command + " ") : ClickEvent.runCommand(command));
    }

    private void assign(JudicatorSender source, long id, Optional<String> name) {
        final Optional<JudicatorPlayer> assignee = name.isPresent()
                ? judicator.getPlatform().getPlayer(name.get())
                : source.asPlayer();
        if (assignee.isEmpty()) {
            source.sendMessage(PunishUtils.format(messages(source), Map.of("staff", name.orElse("")), "reports",
                    name.isPresent() ? "assignee-offline" : "assign-whom"));
            return;
        }
        // the report names who made it: it only goes to someone who may read reports
        if (!assignee.get().hasPermission(PunishPermissions.REPORTS.getPermission())
                && !assignee.get().hasPermission(PunishPermissions.ADMIN.getPermission())) {
            source.sendMessage(PunishUtils.format(messages(source), Map.of("staff", assignee.get().getName()), "reports", "assignee-not-staff"));
            return;
        }
        done(source, judicator.getReportService().assign(source, id, assignee.get()), id, "assigned",
                Map.of("assignee", assignee.get().getName()));
    }

    private void done(JudicatorSender source, ReportService.Change change, long id, String key, Map<String, String> extra) {
        final ConfigurationNode messages = messages(source);
        if (change != ReportService.Change.DONE) {
            source.sendMessage(ReportService.describe(messages, change, id));
            return;
        }
        final Map<String, String> placeholders = new LinkedHashMap<>(extra);
        placeholders.put("id", String.valueOf(id));
        source.sendMessage(PunishUtils.format(messages, placeholders, "reports", key));
    }

    private void teleport(JudicatorSender source, Report report) {
        final ConfigurationNode messages = messages(source);
        final Optional<JudicatorPlayer> staff = source.asPlayer();
        if (staff.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages, "reports", "only-players"));
            return;
        }
        final Optional<JudicatorPlayer> target = report.getTargetUuid().flatMap(judicator.getPlatform()::getPlayer)
                .or(() -> judicator.getPlatform().getPlayer(report.getTargetName()));
        final Map<String, String> placeholders = Map.of("target", report.getTargetName());
        if (target.isEmpty()) {
            source.sendMessage(PunishUtils.format(messages, placeholders, "reports", "tp-offline"));
            return;
        }
        source.sendMessage(PunishUtils.format(messages, placeholders, "reports", "tp"));
        judicator.getPlatform().teleport(staff.get(), target.get()).thenAccept(outcome -> {
            switch (outcome) {
                case SERVER_ONLY -> staff.get().sendMessage(PunishUtils.format(messages, placeholders, "reports", "tp-server-only"));
                case FAILED -> staff.get().sendMessage(PunishUtils.format(messages, placeholders, "reports", "tp-failed"));
                default -> {
                    // TELEPORTED: "Going to" was already said; SUPERSEDED: a newer request answers
                }
            }
        });
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return FILTERS.keySet().stream().filter(filter -> filter.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("assign")) {
            return judicator.getPlatform().matchPlayerNames(args[2]);
        }
        return List.of();
    }

}
