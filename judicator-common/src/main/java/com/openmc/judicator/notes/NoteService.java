package com.openmc.judicator.notes;

import com.openmc.judicator.notes.repository.NoteRepository;

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

    public int countByUsername(String username) {
        return repository.countByUsername(username);
    }

    public List<Note> findByUsername(String username, int limit, int offset) {
        return repository.findByUsername(username, limit, offset);
    }

}
