package com.elless.judicator.menus.screens;

import com.elless.judicator.menus.ListOptions;
import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuContext;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.menus.MenuItem;
import com.elless.judicator.menus.MenuService;
import com.elless.judicator.menus.Screen;
import com.elless.judicator.reports.ReportService;
import com.elless.judicator.reports.ReportState;
import com.elless.judicator.reports.commands.ReportsCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The reports ({@code /reports list}) of one filter — open, assigned, closed or all —, sorted and searched by the
 * database; a click opens the report.
 */
public record ReportsScreen(int page, ListOptions options) implements Screen {

    static final ListOptions.Spec SPEC = new ListOptions.Spec(
            List.of("newest", "oldest", "name"), new ArrayList<>(ReportsCommand.FILTERS.keySet()), List.of());

    /**
     * @param filter open, assigned, closed or all
     */
    public ReportsScreen(String filter, int page) {
        this(page, SPEC.defaults().withFilter(ReportsCommand.FILTERS.containsKey(filter) ? filter : "open"));
    }

    @Override
    public Menu build(MenuContext context) {
        if (!context.canUse("reports")) return context.denied();
        final Set<ReportState> states = ReportsCommand.FILTERS.getOrDefault(options.filter(), ReportsCommand.FILTERS.get("open"));
        final ReportService reports = context.judicator().getReportService();
        final int total = reports.count(states, options.query());
        final int shown = MenuContext.clamp(page, total);
        final List<MenuItem> entries = reports.find(states, options.query(), MenuService.PAGE_SIZE, MenuContext.offset(shown)).stream()
                .map(report -> context.item(MenuIcon.REPORT, ReportService.placeholders(context.messages(), report), "reports", "entry")
                        .headOf(report.getTargetUuid().orElse(null), report.getTargetName())
                        .onClick(context.open(new ReportScreen(report.getId()))))
                .toList();
        return context.page(context.text(Map.of("filter", context.value(options.filter()), "count", String.valueOf(total)), "reports", "title"),
                entries, shown, total, SPEC, options, (other, number) -> new ReportsScreen(number, other), Map.of());
    }
}
