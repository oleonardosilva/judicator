package com.elless.judicator.menus.screens;

import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuContext;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.menus.Screen;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.reports.ReportState;

import java.util.List;
import java.util.Map;

/**
 * {@code /jmenu}: the lists in force, the reports, the players online and a search by name.
 */
public record MainScreen() implements Screen {

    @Override
    public Menu build(MenuContext context) {
        final Menu.Builder menu = Menu.builder(context.text(Map.of(), "main", "title"), 3);
        if (context.canUse("banlist")) {
            final int count = context.judicator().getPunishService().countActive(PunishType.BAN, PunishType.TEMPBAN);
            menu.set(10, context.item(MenuIcon.BAN, Map.of("count", String.valueOf(count)), "main", "bans")
                    .onClick(context.open(PunishmentsScreen.bans(1))));
        }
        if (context.canUse("mutelist")) {
            final int count = context.judicator().getPunishService().countActive(PunishType.MUTE, PunishType.TEMPMUTE);
            menu.set(12, context.item(MenuIcon.MUTE, Map.of("count", String.valueOf(count)), "main", "mutes")
                    .onClick(context.open(PunishmentsScreen.mutes(1))));
        }
        if (context.canUse("reports")) {
            final int count = context.judicator().getReportService().count(List.of(ReportState.OPEN, ReportState.ASSIGNED));
            menu.set(14, context.item(MenuIcon.REPORT, Map.of("count", String.valueOf(count)), "main", "reports")
                    .onClick(context.open(new ReportsScreen("open", 1))));
        }
        final int online = context.judicator().getPlatform().getOnlinePlayers().size();
        menu.set(16, context.item(MenuIcon.ONLINE, Map.of("count", String.valueOf(online)), "main", "online")
                .onClick(context.open(new OnlineScreen(1))));
        menu.set(22, context.item(MenuIcon.SEARCH, Map.of(), "main", "search")
                .onClick(() -> context.menus().askScreen(context.viewer(), context.text(Map.of(), "main", "search-what"),
                        answer -> new PlayerScreen(answer.split(" ")[0]))));
        menu.set(26, context.item(MenuIcon.CLOSE, Map.of(), "navigation", "close")
                .onClick(() -> context.menus().close(context.viewer())));
        return menu.build();
    }
}
