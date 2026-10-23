package com.openmc.judicator.commons;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.platform.PlatformTask;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishStatus;
import com.openmc.judicator.sync.SyncKind;
import com.openmc.judicator.warns.Warn;
import net.kyori.adventure.text.Component;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Personal data kept no longer than needed (IP addresses and histories are personal data):
 * <ul>
 *     <li>{@code retention.<kind>} in {@code config.yml} (days, 0 = forever): old kicks, punishments and warns that are
 *     over, IP addresses no longer used and old staff notes are deleted at startup and every 12 hours;</li>
 *     <li>{@code /judicator clear <player> [confirm]} (console only): everything stored about one player.</li>
 * </ul>
 */
public class DataRetentionService {

    private static final long PERIOD_HOURS = 12;

    private final Judicator judicator;
    private final Judicator.Repositories repositories;
    private PlatformTask task;

    public DataRetentionService(Judicator judicator, Judicator.Repositories repositories) {
        this.judicator = judicator;
        this.repositories = repositories;
    }

    public void start() {
        judicator.getPlatform().runAsync(this::runSafely);
        this.task = judicator.getPlatform().runAsyncRepeating(this::runSafely, PERIOD_HOURS, TimeUnit.HOURS);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void runSafely() {
        try {
            run(LocalDateTime.now());
        } catch (RuntimeException e) {
            judicator.getLogger().error("Data retention failed", e);
        }
    }

    /**
     * Deletes what is older than each configured retention.
     *
     * @return how many records of each kind were deleted (only the kinds that are configured)
     */
    public Map<String, Integer> run(LocalDateTime now) {
        final ConfigurationNode retention = judicator.getConfig().node("retention");
        final Map<String, Integer> deleted = new HashMap<>();
        days(retention, "kicks").ifPresent(days -> deleted.put("kicks", repositories.punishments().deleteKicksBefore(now.minusDays(days))));
        days(retention, "finished-punishments").ifPresent(days ->
                deleted.put("finished-punishments", repositories.punishments().deleteFinishedBefore(now.minusDays(days))));
        days(retention, "finished-warns").ifPresent(days -> deleted.put("finished-warns", repositories.warns().deleteFinishedBefore(now.minusDays(days))));
        days(retention, "addresses").ifPresent(days -> deleted.put("addresses", repositories.addresses().deleteUnusedBefore(now.minusDays(days))));
        days(retention, "notes").ifPresent(days -> deleted.put("notes", repositories.notes().deleteBefore(now.minusDays(days))));
        deleted.forEach((kind, count) -> {
            if (count > 0) judicator.getLogger().info("Retention: deleted {} {} older than retention.{}", count, kind, kind);
        });
        return deleted;
    }

    private static OptionalInt days(ConfigurationNode retention, String kind) {
        final int days = retention.node(kind).getInt(0);
        return days > 0 ? OptionalInt.of(days) : OptionalInt.empty();
    }

    /**
     * Without {@code confirm}, tells what is stored about the player; with it, deletes all of it: punishments
     * (those in force are lifted), warns, staff notes, the name in the IP addresses it used and the staff level.
     */
    public void clear(JudicatorSender sender, String username, boolean confirm) {
        final UUID uuid = judicator.getUuidManager().getUUID(username);
        final List<Punishment> punishments = ofPlayer(repositories.punishments().findAllByUsername(username),
                uuid == null ? List.of() : repositories.punishments().findAllByUUID(uuid), Punishment::getId);
        final List<Warn> warns = ofPlayer(repositories.warns().findAllByUsername(username),
                uuid == null ? List.of() : repositories.warns().findAllByUUID(uuid), Warn::getId);
        final long inForce = punishments.stream().filter(p -> p.getStatus() == PunishStatus.ACTIVE).count();
        final int notes = repositories.notes().countAllOf(username, uuid);
        final int addresses = repositories.addresses().findAllByUsername(username).size();
        final boolean level = repositories.levels().findLevel(username).isPresent();
        final String summary = punishments.size() + " punishment(s) (" + inForce + " in force), " + warns.size() + " warn(s), "
                + notes + " staff note(s), " + addresses + " IP address record(s)" + (level ? ", a staff level" : "");

        if (!confirm) {
            send(sender, "§eStored about " + username + (uuid == null ? "" : " (" + uuid + ")") + ": §f" + summary + "§e.");
            if (inForce > 0) send(sender, "§cThe " + inForce + " punishment(s) in force will be lifted.");
            send(sender, "§eNothing was deleted. Run §f/judicator clear " + username + " confirm§e to delete it all.");
            return;
        }

        repositories.punishments().deleteAllOf(username, uuid);
        repositories.warns().deleteAllOf(username, uuid);
        repositories.notes().deleteAllOf(username, uuid);
        judicator.getAddressService().removeAccount(username);
        repositories.levels().delete(username);
        punishments.forEach(punishment -> judicator.getPunishCache().forgetMute(punishment.getId()));
        punishments.stream().filter(punishment -> punishment.getStatus() == PunishStatus.ACTIVE)
                .forEach(punishment -> judicator.getSyncService().publish(SyncKind.DELETED, punishment.getId()));
        judicator.getPunishCache().removeMutePunishment(username);
        judicator.getLogger().info("{} deleted the data of {}: {}", sender.getName(), username, summary);
        send(sender, "§aDeleted the data of " + username + ": " + summary + ".");
    }

    /**
     * The records found by name and by UUID, each once.
     */
    private static <T> List<T> ofPlayer(List<T> byName, List<T> byUuid, Function<T, Long> id) {
        final Map<Long, T> all = new LinkedHashMap<>();
        byName.forEach(record -> all.put(id.apply(record), record));
        byUuid.forEach(record -> all.putIfAbsent(id.apply(record), record));
        return List.copyOf(all.values());
    }

    private static void send(JudicatorSender sender, String message) {
        sender.sendMessage(Component.text(message));
    }
}
