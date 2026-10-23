package com.openmc.judicator.notes;

import com.openmc.judicator.notes.repository.NoteRepository;
import com.openmc.judicator.players.PlayerRef;

import java.util.List;
import java.util.Optional;

public class NoteService {

    private final NoteRepository repository;

    public NoteService(NoteRepository repository) {
        this.repository = repository;
        this.repository.initialize();
    }

    public Note save(Note note) {
        return repository.save(note);
    }

    public Optional<Note> findById(Long id) {
        return repository.findById(id);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    public int countOf(PlayerRef player) {
        return repository.countOf(player);
    }

    public List<Note> findOf(PlayerRef player, int limit, int offset) {
        return repository.findOf(player, limit, offset);
    }

    public int fillUuid(PlayerRef account) {
        return repository.fillUuid(account);
    }

}
