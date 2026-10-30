package com.elless.judicator.commons;

import com.elless.judicator.punish.PunishUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntFunction;

/**
 * Pages of 10 entries for the list commands, with a clickable "« Previous | Page x/y | Next »" footer.
 */
public final class Pagination {

    public static final int PAGE_SIZE = 10;

    private Pagination() {
    }

    public static int pages(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /**
     * @return the page, moved into 1..pages
     */
    public static int clamp(int page, int pages) {
        return Math.max(1, Math.min(page, pages));
    }

    public static int offset(int page) {
        return (page - 1) * PAGE_SIZE;
    }

    /**
     * @return 1 when no page was given, empty when the argument is not a number
     */
    public static OptionalInt parsePage(Optional<String> argument) {
        if (argument.isEmpty()) return OptionalInt.of(1);
        try {
            return OptionalInt.of(Integer.parseInt(argument.get()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /**
     * @param command the command that opens a given page, e.g. {@code p -> "/banlist " + p}
     * @return the footer, or an empty component when everything fits in one page
     */
    public static Component footer(ConfigurationNode messages, int page, int pages, IntFunction<String> command) {
        if (pages <= 1) return Component.empty();
        final TextComponent.Builder footer = Component.text().content("\n");
        if (page > 1) {
            footer.append(link(messages, "previous", page - 1, command));
        }
        footer.append(PunishUtils.format(messages, Map.of("page", String.valueOf(page), "pages", String.valueOf(pages)),
                "pagination", "page"));
        if (page < pages) {
            footer.append(link(messages, "next", page + 1, command));
        }
        return footer.build();
    }

    private static Component link(ConfigurationNode messages, String key, int target, IntFunction<String> command) {
        final Map<String, String> placeholders = Map.of("page", String.valueOf(target));
        return PunishUtils.format(messages, placeholders, "pagination", key)
                .hoverEvent(HoverEvent.showText(PunishUtils.format(messages, placeholders, "pagination", "hover")))
                .clickEvent(ClickEvent.runCommand(command.apply(target)));
    }
}
