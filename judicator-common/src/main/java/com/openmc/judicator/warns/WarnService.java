package com.openmc.judicator.warns;

import com.openmc.judicator.punish.data.repository.PunishmentRepository;
import com.openmc.judicator.warns.repository.WarnRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class WarnService {

    private final WarnRepository repository;

    public WarnService(WarnRepository repository) {
        this.repository = repository;
        this.repository.initialize();
    }

    public Optional<Warn> findById(Long id) {
        return repository.findById(id);
    }

    public List<Warn> findAllByUsername(String username) {
        return repository.findAllByUsername(username);
    }

    public Warn save(Warn punishment) {
        return repository.save(punishment);
    }

    public Long countActiveWarns(String username) {
        return repository.countActiveWarns(username);
    }

    public List<PunishmentRepository.StaffCount> countByStaffSince(LocalDateTime since) {
        return repository.countByStaffSince(since);
    }

    public List<Warn> findByPunisherSince(String punisher, LocalDateTime since) {
        return repository.findByPunisherSince(punisher, since);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

}
