package com.openmc.judicator.menus.screens;

import com.openmc.judicator.menus.Menu;
import com.openmc.judicator.menus.MenuContext;
import com.openmc.judicator.menus.MenuIcon;
import com.openmc.judicator.menus.MenuItem;
import com.openmc.judicator.menus.MenuService;
import com.openmc.judicator.menus.Screen;
import com.openmc.judicator.reports.ReportService;
import com.openmc.judicator.reports.ReportState;
import com.openmc.judicator.reports.commands.ReportsCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The reports ({@code /reports list}) of one filter — open, assigned, closed or all —, newest first; a button switches
 * the filter and a click opens the report.
 */
public record ReportsScreen(String filter, int page) implements Screen {

    @Override
    public Menu build(MenuContext context) {
        if (!context.canUse("reports")) return context.denied();
        final Set<ReportState> states = ReportsCommand.FILTERS.getOrDefault(filter, ReportsCommand.FILTERS.get("open"));
        final ReportService reports = context.judicator().getReportService();
        final int total = reports.count(states);
        final int shown = MenuContext.clamp(page, total);
        final List<MenuItem> entries = reports.find(states, MenuService.PAGE_SIZE, MenuContext.offset(shown)).stream()
                .map(report -> context.item(MenuIcon.REPORT, ReportService.placeholders(context.messages(), report), "reports", "entry")
                        .onClick(context.open(new ReportScreen(report.getId()))))
                .toList();

        final List<String> filters = new ArrayList<>(ReportsCommand.FILTERS.keySet());
        final String next = filters.get((filters.indexOf(filter) + 1) % filters.size());
        final Map<String, String> filterNames = Map.of("filter", name(context, filter), "next", name(context, next));
        final MenuItem switcher = context.item(MenuIcon.FILTER, filterNames, "reports", "filter")
                .onClick(context.replace(new ReportsScreen(next, 1)));
        return context.page(context.text(Map.of("filter", name(context, filter), "count", String.valueOf(total)), "reports", "title"),
                entries, shown, total, other -> new ReportsScreen(filter, other), Map.of(47, switcher));
    }

    private static String name(MenuContext context, String filter) {
        return context.message(filter, "reports", "filters", filter);
    }
}
