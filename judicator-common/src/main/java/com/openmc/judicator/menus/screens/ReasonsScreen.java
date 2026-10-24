package com.openmc.judicator.menus.screens;

import com.openmc.judicator.commons.DateTimeOffsetParser;
import com.openmc.judicator.menus.Menu;
import com.openmc.judicator.menus.MenuContext;
import com.openmc.judicator.menus.MenuItem;
import com.openmc.judicator.menus.Screen;
import com.openmc.judicator.punish.ConfiguredReason;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.commands.PunishCommand;

import java.util.List;
import java.util.Map;

/**
 * {@code /punish <player>}: the reasons of {@code config.yml} the viewer may use, each with the punishment its
 * progression gives this player now; a click asks for confirmation.
 */
public record ReasonsScreen(String player, int page) implements Screen {

    public ReasonsScreen(String player) {
        this(player, 1);
    }

    @Override
    public Menu build(MenuContext context) {
        if (!context.canUse("punish")) return context.denied();
        final List<Punishment> history = context.judicator().getPunishService()
                .findAllOf(context.judicator().getPlayerDirectory().resolve(player));
        final List<ConfiguredReason> reasons = context.judicator().getReasonCache().getReasons().stream()
                .filter(reason -> context.judicator().getReasonCache().canUse(context.viewer(), reason))
                .toList();
        final int shown = MenuContext.clamp(page, reasons.size());
        final List<MenuItem> entries = MenuContext.slice(reasons, shown, reason -> {
            final ConfiguredReason.Step step = reason.stepFor(PunishCommand.countPrevious(history, reason));
            final Map<String, String> placeholders = placeholders(context, player, reason, step);
            return context.item(Icons.of(step.type()), placeholders, "reasons", "entry")
                    .onClick(context.open(new PunishConfirmScreen(player, reason.getReason(), placeholders, false)));
        });
        return context.page(context.text(Map.of("nickname", player), "reasons", "title"),
                entries, shown, reasons.size(), next -> new ReasonsScreen(player, next), Map.of());
    }

    static Map<String, String> placeholders(MenuContext context, String player, ConfiguredReason reason, ConfiguredReason.Step step) {
        final String duration = step.isPermanent()
                ? context.message("Permanent", "permanent")
                : DateTimeOffsetParser.format(context.messages(), DateTimeOffsetParser.getMillisFromDuration(step.duration()));
        return Map.of(
                "nickname", player,
                "reason", reason.getReason(),
                "type", step.type().name(),
                "duration", duration,
                "ip", context.message(reason.isIp() ? "Yes" : "No", reason.isIp() ? "yes" : "no"));
    }
}
