package com.elless.judicator.menus.screens;

import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuContext;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.menus.Screen;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.punish.types.PunishPermissions;
import com.elless.judicator.reports.Report;
import com.elless.judicator.reports.ReportService;
import com.elless.judicator.reports.ReportState;

import java.util.Map;
import java.util.Optional;

/**
 * One report ({@code /reports info}): its data, the reported player and who reported, and — with
 * {@code judicator.reports.manage} — assign it to oneself, open it again or close it; with {@code judicator.reports.tp},
 * go to the reported player.
 */
public record ReportScreen(long id) implements Screen {

    @Override
    public Menu build(MenuContext context) {
        if (!context.canUse("reports")) return context.denied();
        final Optional<Report> found = context.judicator().getReportService().findById(id);
        if (found.isEmpty()) return context.gone();
        final Report report = found.get();
        final Map<String, String> placeholders = ReportService.placeholders(context.messages(), report);
        final String argument = String.valueOf(id);

        final Menu.Builder menu = Menu.builder(context.text(placeholders, "report", "title"), 3);
        menu.set(4, context.item(MenuIcon.REPORT, placeholders, "report", "info"));
        final Optional<JudicatorPlayer> target = report.getTargetUuid().flatMap(context.judicator().getPlatform()::getPlayer)
                .or(() -> context.judicator().getPlatform().getPlayer(report.getTargetName()));
        menu.set(9, context.item(MenuIcon.PLAYER_HEAD, placeholders, "report", "target")
                .headOf(target.map(JudicatorPlayer::getUniqueId).or(report::getTargetUuid).orElse(null), report.getTargetName())
                .onClick(context.open(new PlayerScreen(report.getTargetName()))));
        menu.set(10, context.item(MenuIcon.PLAYER_HEAD, placeholders, "report", "reporter")
                .headOf(report.getReporterUuid(), report.getReporterName())
                .onClick(context.open(new PlayerScreen(report.getReporterName()))));
        if (context.has(PunishPermissions.REPORTS_MANAGE)) {
            if (report.getState().isOpen()) {
                menu.set(12, context.item(MenuIcon.ASSIGN, placeholders, "report", "assign")
                        .onClick(() -> context.menus().run(context.viewer(), "reports", "assign " + argument, false,
                                () -> context.menus().refresh(context.viewer()))));
                menu.set(14, context.item(MenuIcon.CONFIRM, placeholders, "report", "close")
                        .onClick(context.open(ConfirmScreen.withReason(MenuIcon.CONFIRM, "close-report", placeholders, "reports", "close " + argument))));
            }
            if (report.getState() == ReportState.ASSIGNED) {
                menu.set(13, context.item(MenuIcon.CANCEL, placeholders, "report", "unassign")
                        .onClick(() -> context.menus().run(context.viewer(), "reports", "unassign " + argument, false,
                                () -> context.menus().refresh(context.viewer()))));
            }
        }
        if (target.isPresent() && context.has(PunishPermissions.REPORTS_TP)) {
            menu.set(16, context.item(MenuIcon.TELEPORT, placeholders, "report", "tp")
                    .onClick(() -> {
                        context.menus().close(context.viewer());
                        context.menus().run(context.viewer(), "reports", "tp " + argument, false, () -> { });
                    }));
        }
        menu.set(18, context.item(MenuIcon.BACK, Map.of(), "navigation", "back").onClick(context.back()));
        menu.set(26, context.item(MenuIcon.CLOSE, Map.of(), "navigation", "close")
                .onClick(() -> context.menus().close(context.viewer())));
        return menu.build();
    }
}
