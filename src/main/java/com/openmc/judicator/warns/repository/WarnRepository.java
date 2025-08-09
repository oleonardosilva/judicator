package com.openmc.judicator.warns.repository;

import com.openmc.judicator.commons.db.Repository;
import com.openmc.judicator.warns.Warn;

import java.util.List;
import java.util.UUID;

public interface WarnRepository extends Repository<Warn, Long> {

    List<Warn> findAllByUsername(String username);

    Long countActiveWarns(String username);

    List<Warn> findAllByUUID(UUID uuid);

}
