package com.elless.judicator.commons;

import com.elless.judicator.Judicator;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.platform.PlatformTask;
import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.punish.PunishService;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishStatus;
import com.elless.judicator.sync.SyncKind;
import com.elless.judicator.warns.Warn;
import net.kyori.adventure.text.Component;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
        days(retention, "reports").ifPresent(days -> deleted.put("reports", repositories.reports().deleteClosedBefore(now.minusDays(days))));
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
     * (those in force are lifted), warns, staff notes, the IP addresses it used, the staff level and the account's name
     * in the {@code players} table.
     */
    public void clear(JudicatorSender sender, String username, boolean confirm) {
        final PlayerRef player = judicator.getPlayerDirectory().resolve(username);
        final UUID uuid = player.uuid();
        final List<Punishment> punishments = repositories.punishments().findAllOf(player);
        final List<Warn> warns = repositories.warns().findAllOf(player);
        final long inForce = punishments.stream().filter(p -> p.getStatus() == PunishStatus.ACTIVE).count();
        final int notes = repositories.notes().countOf(player);
        final int addresses = judicator.getAddressService().countAddresses(player);
        final boolean level = repositories.levels().findLevel(player).isPresent();
        final int reports = judicator.getReportService().countAllOf(player);
        final String summary = punishments.size() + " punishment(s) (" + inForce + " in force), " + warns.size() + " warn(s), "
                + notes + " staff note(s), " + addresses + " IP address record(s), " + reports + " report(s) made or received"
                + (level ? ", a staff level" : "");

        if (!confirm) {
            send(sender, "§eStored about " + username + (uuid == null ? "" : " (" + uuid + ")") + ": §f" + summary + "§e.");
            if (inForce > 0) send(sender, "§cThe " + inForce + " punishment(s) in force will be lifted.");
            send(sender, "§eNothing was deleted. Run §f/judicator clear " + username + " confirm§e to delete it all.");
            return;
        }

        repositories.punishments().deleteAllOf(player);
        repositories.warns().deleteAllOf(player);
        repositories.notes().deleteAllOf(player);
        repositories.reports().deleteAllOf(player);
        judicator.getAddressService().removeAccount(player);
        repositories.levels().delete(player);
        judicator.getPlayerDirectory().forget(player);
        punishments.forEach(punishment -> judicator.getPunishCache().forgetMute(punishment.getId()));
        punishments.stream().filter(punishment -> punishment.getStatus() == PunishStatus.ACTIVE)
                .forEach(punishment -> judicator.getSyncService().publish(SyncKind.DELETED, punishment.getId()));
        judicator.getPunishCache().removeMutePunishment(PunishService.cacheKey(player));
        judicator.getLogger().info("{} deleted the data of {}: {}", sender.getName(), username, summary);
        send(sender, "§aDeleted the data of " + username + ": " + summary + ".");
    }

    private static void send(JudicatorSender sender, String message) {
        sender.sendMessage(Component.text(message));
    }
}
