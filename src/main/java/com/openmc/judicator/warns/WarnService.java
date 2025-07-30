package com.openmc.judicator.warns;

import com.openmc.judicator.warns.repository.WarnRepository;

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

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

}
