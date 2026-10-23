package com.openmc.judicator.punish.data.repository;

import com.openmc.judicator.commons.db.Repository;
import com.openmc.judicator.punish.AccessAddress;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccessAddressRepository extends Repository<AccessAddress, String> {

    Optional<AccessAddress> findByIp(String ip);

    Optional<AccessAddress> findByUsername(String username);

    List<AccessAddress> findAllByUsername(String username);

    /**
     * Deletes the addresses no account used since a moment.
     *
     * @return how many were deleted
     */
    int deleteUnusedBefore(LocalDateTime cutoff);

}
