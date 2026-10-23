package com.openmc.judicator.punish;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.punish.data.repository.PlayerAddressRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The IP addresses each account joined from: {@code /alts}, the alt notices and refusals at login, and the address of
 * an offline player for IP punishments.
 */
public class AccessAddressService {

    private final Judicator judicator;
    private final PlayerAddressRepository repository;

    public AccessAddressService(Judicator judicator, PlayerAddressRepository repository) {
        this.judicator = judicator;
        this.repository = repository;
        repository.initialize();
    }

    public void updateUserIP(PlayerRef player, String address) {
        repository.record(player, address, LocalDateTime.now());
    }

    /**
     * Forgets every address the account used.
     *
     * @return how many account-address records it had
     */
    public int removeAccount(PlayerRef player) {
        return repository.deleteAllOf(player);
    }

    public int fillUuid(PlayerRef account) {
        return repository.fillUuid(account);
    }

    /**
     * The address the player joined from most recently.
     */
    public Optional<PlayerAddress> findLast(PlayerRef player) {
        return repository.findAllOf(player).stream().findFirst();
    }

    /**
     * How many account-address records the player has.
     */
    public int countAddresses(PlayerRef player) {
        return repository.countOf(player);
    }

    public List<PlayerRef> findAlts(PlayerRef player) {
        return findAlts(player, address -> false);
    }

    /**
     * The other accounts that joined from an address the player used, by name.
     *
     * @param ignoredAddress addresses that do not link accounts (e.g. exempt shared networks)
     */
    public List<PlayerRef> findAlts(PlayerRef player, Predicate<String> ignoredAddress) {
        final Map<String, PlayerRef> alts = new LinkedHashMap<>();
        for (PlayerAddress own : repository.findAllOf(player)) {
            if (ignoredAddress.test(own.hostAddress())) continue;
            accountsOn(own.hostAddress(), player).forEach(other -> alts.putIfAbsent(key(other), other));
        }
        return alts.values().stream().sorted(Comparator.comparing(PlayerRef::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static String key(PlayerRef account) {
        return account.identifiedByUuid() ? account.uuid().toString() : account.lowerName();
    }

    /**
     * The accounts that joined from an address, one entry each.
     *
     * @param exclude a player left out of the result; null for none
     */
    public List<PlayerRef> findAccountsByIp(String address, PlayerRef exclude) {
        return accountsOn(address, exclude);
    }

    private List<PlayerRef> accountsOn(String address, PlayerRef exclude) {
        final Map<String, PlayerRef> accounts = new LinkedHashMap<>();
        for (PlayerAddress row : repository.findByAddress(address)) {
            if (exclude != null && exclude.matches(row.uuid(), row.nickname())) continue;
            final PlayerRef account = judicator.getPlayerDirectory().refOf(row.uuid(), row.nickname());
            accounts.putIfAbsent(key(account), account);
        }
        return new ArrayList<>(accounts.values());
    }

}
