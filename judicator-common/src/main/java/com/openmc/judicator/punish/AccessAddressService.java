package com.openmc.judicator.punish;

import com.openmc.judicator.punish.data.repository.AccessAddressRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

public class AccessAddressService {

    private final AccessAddressRepository repository;

    public AccessAddressService(AccessAddressRepository repository) {
        this.repository = repository;
        repository.initialize();
    }

    public void updateUserIP(String username, String addressIP) {
        final AccessAddress address = repository.findByIp(addressIP).orElseGet(() -> AccessAddress.create(addressIP, username));

        if (address.getId() == null) {
            repository.save(address);
            return;
        }

        if (!address.contains(username)) address.addAccount(username);
        // Always refresh: findByUsername picks the most recently used address for offline IP punishments.
        address.setLastUsage(LocalDateTime.now());
        repository.save(address);
    }

    /**
     * Takes the account out of every address it used; an address left without accounts is deleted.
     *
     * @return how many addresses had the account
     */
    public int removeAccount(String username) {
        final List<AccessAddress> addresses = repository.findAllByUsername(username);
        for (AccessAddress address : addresses) {
            address.removeAccount(username);
            if (address.getAccounts().isEmpty()) repository.deleteById(String.valueOf(address.getId()));
            else repository.save(address);
        }
        return addresses.size();
    }

    public Optional<AccessAddress> findByUsername(String username) {
        return repository.findByUsername(username);
    }

    public Set<String> findAlts(String username) {
        return findAlts(username, address -> false);
    }

    /**
     * @param ignoredAddress addresses that do not link accounts (e.g. exempt shared networks)
     */
    public Set<String> findAlts(String username, Predicate<String> ignoredAddress) {
        final String account = username.toLowerCase();
        final Set<String> alts = new TreeSet<>();
        for (AccessAddress address : repository.findAllByUsername(account)) {
            if (ignoredAddress.test(address.getHostAddress())) continue;
            address.getAccounts().stream().filter(other -> !other.equalsIgnoreCase(account)).forEach(alts::add);
        }
        return alts;
    }

    public Set<String> findAccountsByIp(String address, String excludeUsername) {
        final Set<String> accounts = new TreeSet<>();
        repository.findByIp(address).ifPresent(found -> found.getAccounts().stream()
                .filter(other -> !other.equalsIgnoreCase(excludeUsername))
                .forEach(accounts::add));
        return accounts;
    }
}
