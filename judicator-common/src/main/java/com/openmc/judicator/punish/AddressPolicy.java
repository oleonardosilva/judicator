package com.openmc.judicator.punish;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.DateTimeOffsetParser;
import com.openmc.judicator.platform.JudicatorPlayer;
import net.kyori.adventure.text.Component;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rules about IP addresses from {@code config.yml}:
 * <ul>
 *     <li>{@code exempt-addresses}: addresses/ranges shared by many people (a school network, the proxy's own
 *     address) that IP punishments, alt warnings and account limits leave alone;</li>
 *     <li>{@code ip-limits}: how many accounts may be online at once from one address, and how many different accounts
 *     may join from it within {@code recent-period}. Kept in memory, so a restart starts counting again.</li>
 * </ul>
 */
public class AddressPolicy {

    private static final int SWEEP_THRESHOLD = 10_000;

    private final Judicator judicator;
    private final Map<String, Map<String, Long>> recentAccounts = new ConcurrentHashMap<>();
    private volatile List<AddressRange> exempt = List.of();
    private Clock clock = Clock.systemUTC();

    public AddressPolicy(Judicator judicator) {
        this.judicator = judicator;
        reload();
    }

    /**
     * For tests: the clock the recent-accounts window is measured with.
     */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public void reload() {
        final List<AddressRange> ranges = new ArrayList<>();
        try {
            final List<String> entries = judicator.getConfig().node("exempt-addresses").getList(String.class, List.of());
            for (String entry : entries) {
                AddressRange.parse(entry).ifPresentOrElse(ranges::add,
                        () -> judicator.getLogger().warn("Ignoring exempt-addresses entry '{}': not an IP address or CIDR range", entry));
            }
        } catch (SerializationException e) {
            judicator.getLogger().warn("Ignoring exempt-addresses: it must be a list of IP addresses or CIDR ranges");
        }
        this.exempt = List.copyOf(ranges);
    }

    public boolean isExempt(String address) {
        if (address == null) return false;
        for (AddressRange range : exempt) {
            if (range.contains(address)) return true;
        }
        return false;
    }

    /**
     * Account limits for a player about to join. Nothing is recorded here: on Velocity this runs before the player is
     * authenticated, so a name that never logs in must not count (see {@link #recordLogin}).
     *
     * @return the disconnect message when the address already has too many accounts
     */
    public Optional<Component> checkLogin(String username, String address) {
        if (isExempt(address)) return Optional.empty();
        final ConfigurationNode limits = judicator.getConfig().node("ip-limits");

        final int maxOnline = limits.node("max-online").getInt(0);
        if (maxOnline > 0) {
            final long others = judicator.getPlatform().getOnlinePlayers().stream()
                    .filter(player -> address.equals(player.getHostAddress()))
                    .map(JudicatorPlayer::getName)
                    .filter(name -> !name.equalsIgnoreCase(username))
                    .count();
            if (others >= maxOnline) return Optional.of(denied());
        }

        final int maxRecent = limits.node("max-recent-accounts").getInt(0);
        if (maxRecent > 0) {
            final long period = DateTimeOffsetParser.parseMillis(limits.node("recent-period").getString("1:d")).orElse(86_400_000L);
            if (tooManyRecent(username.toLowerCase(Locale.ROOT), address, maxRecent, period)) return Optional.of(denied());
        }
        return Optional.empty();
    }

    /**
     * Counts an account that was let in (after authentication) for the recent-accounts limit of its address.
     */
    public void recordLogin(String username, String address) {
        if (isExempt(address)) return;
        final ConfigurationNode limits = judicator.getConfig().node("ip-limits");
        if (limits.node("max-recent-accounts").getInt(0) <= 0) return;
        final long period = DateTimeOffsetParser.parseMillis(limits.node("recent-period").getString("1:d")).orElse(86_400_000L);
        final long now = clock.millis();
        if (recentAccounts.size() > SWEEP_THRESHOLD) sweep(now, period);
        recentAccounts.compute(address, (key, seen) -> {
            final Map<String, Long> accounts = seen == null ? new HashMap<>() : seen;
            accounts.values().removeIf(last -> now - last >= period);
            accounts.put(username.toLowerCase(Locale.ROOT), now);
            return accounts;
        });
    }

    private boolean tooManyRecent(String account, String address, int max, long periodMillis) {
        final long now = clock.millis();
        final boolean[] tooMany = {false};
        recentAccounts.computeIfPresent(address, (key, accounts) -> {
            accounts.values().removeIf(last -> now - last >= periodMillis);
            tooMany[0] = !accounts.containsKey(account) && accounts.size() >= max;
            return accounts.isEmpty() ? null : accounts;
        });
        return tooMany[0];
    }

    private void sweep(long now, long periodMillis) {
        for (String address : List.copyOf(recentAccounts.keySet())) {
            recentAccounts.computeIfPresent(address, (key, accounts) -> {
                accounts.values().removeIf(last -> now - last >= periodMillis);
                return accounts.isEmpty() ? null : accounts;
            });
        }
    }

    private Component denied() {
        return PunishUtils.getMessageList(judicator.getMessagesConfig(), "runners", "too-many-accounts");
    }
}
