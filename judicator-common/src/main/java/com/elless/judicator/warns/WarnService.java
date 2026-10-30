package com.elless.judicator.warns;

import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.punish.data.repository.PunishmentRepository;
import com.elless.judicator.warns.repository.WarnRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class WarnService {

    private final WarnRepository repository;

    public WarnService(WarnRepository repository) {
        this.repository = repository;
        this.repository.initialize();
    }

    public Optional<Warn> findById(Long id) {
        return repository.findById(id);
    }

    public Warn save(Warn punishment) {
        return repository.save(punishment);
    }

    public List<Warn> findAllOf(PlayerRef player) {
        return repository.findAllOf(player);
    }

    public Long countActiveWarns(PlayerRef player) {
        return repository.countActiveWarns(player);
    }

    public int fillUuid(PlayerRef account) {
        return repository.fillUuid(account);
    }

    public List<PunishmentRepository.StaffCount> countByStaffSince(LocalDateTime since) {
        return repository.countByStaffSince(since);
    }

    public List<Warn> findByPunisherSince(PlayerRef staff, LocalDateTime since) {
        return repository.findByPunisherSince(staff, since);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

}
