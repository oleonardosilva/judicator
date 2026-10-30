package com.elless.judicator.punish.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.DateTimeOffsetParser;
import com.elless.judicator.commons.Pagination;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.data.repository.PunishmentRepository.StaffCount;
import com.elless.judicator.punish.types.PunishPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code /activity <period> [page]}: how many punishments and warns each staff member applied in the last period,
 * busiest first; clicking a staff member opens their {@code /blame} for the same period.
 */
public class ActivityCommand extends CoreCommand {

    private static final List<String> TYPE_ORDER = List.of("BAN", "TEMPBAN", "MUTE", "TEMPMUTE", "WARN", "KICK");

    public ActivityCommand(Judicator judicator) {
        super(judicator, "activity", List.of("staffactivity"), PunishPermissions.ACTIVITY.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> optPeriod = args.word();
        final OptionalLong millis = optPeriod.map(DateTimeOffsetParser::parseMillis).orElse(OptionalLong.empty());
        final OptionalInt requested = Pagination.parsePage(args.word());
        if (millis.isEmpty() || requested.isEmpty()) {
            sendUsage(source, "activity");
            return;
        }
        final String period = optPeriod.get();
        final ConfigurationNode messages = messages(source);
        final String periodText = DateTimeOffsetParser.format(messages, millis.getAsLong());
        final LocalDateTime since = DateTimeOffsetParser.since(millis.getAsLong());

        final List<Staff> staff = group(Stream.concat(
                judicator.getPunishService().countByStaffSince(since).stream(),
                judicator.getWarnService().countByStaffSince(since).stream()).toList());
        if (staff.isEmpty()) {
            source.sendMessage(PunishUtils.format(messages, Map.of("period", periodText), "activity", "none"));
            return;
        }

        final int pages = Pagination.pages(staff.size());
        final int page = Pagination.clamp(requested.getAsInt(), pages);
        final TextComponent.Builder text = Component.text().append(PunishUtils.format(messages, Map.of(
                "period", periodText,
                "count", String.valueOf(staff.size()),
                "page", String.valueOf(page),
                "pages", String.valueOf(pages)), "activity", "title"));
        final String separator = messages.node("activity", "separator").getString("&7, ");
        for (Staff member : staff.subList(Pagination.offset(page), Math.min(staff.size(), Pagination.offset(page) + Pagination.PAGE_SIZE))) {
            final String summary = member.counts().entrySet().stream()
                    .map(entry -> messages.node("activity", "entry").getString("&f{count} &c{type}")
                            .replace("{count}", String.valueOf(entry.getValue()))
                            .replace("{type}", entry.getKey()))
                    .collect(Collectors.joining(separator))
                    .replace("&", "§");
            final Map<String, String> placeholders = Map.of("staff", member.name(), "summary", summary,
                    "total", String.valueOf(member.total()), "period", periodText);
            text.append(PunishUtils.format(messages, placeholders, "activity", "line")
                    .hoverEvent(HoverEvent.showText(PunishUtils.format(messages, placeholders, "activity", "hover")))
                    .clickEvent(ClickEvent.runCommand("/blame " + member.name() + " " + period)));
        }
        text.append(Pagination.footer(messages, page, pages, target -> "/activity " + period + " " + target));
        source.sendMessage(text.build());
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        if (args.length > 1) return List.of();
        final String prefix = args.length == 0 ? "" : args[0];
        return Stream.of("1:h", "24:h", "7:d", "30:d").filter(period -> period.startsWith(prefix)).toList();
    }

    /**
     * Merges the counts of the same staff member (names compared case-insensitively), busiest first.
     */
    private static List<Staff> group(List<StaffCount> counts) {
        final Map<String, Staff> byName = new LinkedHashMap<>();
        for (StaffCount count : counts) {
            byName.computeIfAbsent(count.punisher().toLowerCase(Locale.ROOT), key -> new Staff(count.punisher(), new LinkedHashMap<>()))
                    .counts().merge(count.type(), count.count(), Integer::sum);
        }
        final List<Staff> staff = new ArrayList<>();
        for (Staff member : byName.values()) {
            final Map<String, Integer> ordered = new LinkedHashMap<>();
            member.counts().entrySet().stream()
                    .sorted(Comparator.comparingInt(entry -> order(entry.getKey())))
                    .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
            staff.add(new Staff(member.name(), ordered));
        }
        staff.sort(Comparator.comparingInt(Staff::total).reversed().thenComparing(Staff::name, String.CASE_INSENSITIVE_ORDER));
        return staff;
    }

    private static int order(String type) {
        final int index = TYPE_ORDER.indexOf(type);
        return index < 0 ? TYPE_ORDER.size() : index;
    }

    private record Staff(String name, Map<String, Integer> counts) {

        int total() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

}
