package com.openmc.plugin.judicator.punish.db;

import com.openmc.plugin.judicator.punish.Punishment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PunishmentRepository {

    void initialize();
    Optional<Punishment> findPunishmentById(Long id);
    List<Punishment> findAllPunishmentsByUsername(String username);
    List<Punishment> findAllPunishmentsByIP(String ip);
    List<Punishment> findAllPunishmentsByUUID(UUID uuid);
    Punishment save(Punishment punishment);
    void deleteById(Long id);

}
