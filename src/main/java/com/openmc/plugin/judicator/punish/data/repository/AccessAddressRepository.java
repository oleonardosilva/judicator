package com.openmc.plugin.judicator.punish.data.repository;

import com.openmc.plugin.judicator.punish.AccessAddress;

import java.util.Optional;

public interface AccessAddressRepository extends Repository<AccessAddress, String> {

    Optional<AccessAddress> findByIp(String ip);

    Optional<AccessAddress> findByUsername(String username);

}
