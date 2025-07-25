package com.openmc.plugin.judicator.punish.data.repository;

import com.openmc.plugin.judicator.punish.Punishment;
import com.openmc.plugin.judicator.punish.types.PunishType;

import java.util.List;
import java.util.UUID;

public interface PunishmentRepository extends Repository<Punishment, Long> {

    List<Punishment> findAllByUsername(String username);

    List<Punishment> findAllActiveByUsernameAndTypes(String username, PunishType... types);

    List<Punishment> findAllActiveByUsernameOrIpAndTypes(String username, String ipAddress, PunishType... types);

    List<Punishment> findAllByUUID(UUID uuid);

    List<Punishment> findAllActiveByUUIDAndTypes(UUID uuid, PunishType... types);

    boolean revoke(Long id, String reason);

}
