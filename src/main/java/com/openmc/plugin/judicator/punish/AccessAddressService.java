package com.openmc.plugin.judicator.punish;

import com.openmc.plugin.judicator.punish.data.repository.AccessAddressRepository;

import java.util.Optional;

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

        if (!address.contains(username)) {
            address.addAccount(username);
            repository.save(address);
        }
    }

    public Optional<AccessAddress> findByUsername(String username) {
        return repository.findByUsername(username);
    }
}
