package com.openmc.judicator.testing;

import com.openmc.judicator.notes.Note;
import com.openmc.judicator.notes.repository.NoteRepository;
import com.openmc.judicator.notes.repository.dao.NoteRelationalDAO;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Same rules as {@link NoteRelationalDAO} (checked by the repository contract tests), kept in memory.
 */
public final class InMemoryNoteRepository implements NoteRepository {

    private final Map<Long, Note> rows = new TreeMap<>();
    private long nextId = 1;

    @Override
    public void initialize() {
    }

    @Override
    public synchronized Note save(Note note) {
        check(note);
        if (note.getId() == null) note.setId(nextId++);
        rows.put(note.getId(), copy(note));
        return note;
    }

    @Override
    public synchronized Optional<Note> findById(Long id) {
        return Optional.ofNullable(rows.get(id)).map(InMemoryNoteRepository::copy);
    }

    @Override
    public synchronized void deleteById(Long id) {
        rows.remove(id);
    }

    @Override
    public int countByUsername(String username) {
        return find(n -> n.getNickname().equalsIgnoreCase(username)).size();
    }

    @Override
    public List<Note> findByUsername(String username, int limit, int offset) {
        return find(n -> n.getNickname().equalsIgnoreCase(username)).stream()
                .sorted(Comparator.comparing(Note::getCreatedAt).thenComparing(Note::getId).reversed())
                .skip(offset).limit(limit).toList();
    }

    @Override
    public int countAllOf(String username, UUID uuid) {
        return find(of(username, uuid)).size();
    }

    @Override
    public synchronized int deleteAllOf(String username, UUID uuid) {
        final int before = rows.size();
        rows.values().removeIf(of(username, uuid));
        return before - rows.size();
    }

    private static Predicate<Note> of(String username, UUID uuid) {
        return n -> n.getNickname().equalsIgnoreCase(username) || (uuid != null && n.getPlayerUUID().map(uuid::equals).orElse(false));
    }

    @Override
    public synchronized int deleteBefore(LocalDateTime cutoff) {
        final int before = rows.size();
        rows.values().removeIf(n -> n.getCreatedAt().isBefore(cutoff));
        return before - rows.size();
    }

    @Override
    public Set<String> findAllImportKeys() {
        final Set<String> keys = new HashSet<>();
        find(n -> true).forEach(n -> keys.add(NoteRelationalDAO.importKey(n)));
        return keys;
    }

    @Override
    public synchronized int insertAll(List<Note> notes) {
        notes.forEach(InMemoryNoteRepository::check);
        notes.forEach(n -> {
            final Note row = copy(n);
            row.setId(nextId++);
            rows.put(row.getId(), row);
        });
        return notes.size();
    }

    public synchronized List<Note> all() {
        return find(n -> true);
    }

    private synchronized List<Note> find(Predicate<Note> filter) {
        return new ArrayList<>(rows.values().stream().filter(filter).map(InMemoryNoteRepository::copy).toList());
    }

    /**
     * The constraints of the notes table (see NoteRelationalDAO#initialize).
     */
    private static void check(Note n) {
        Columns.notNull(n.getNickname(), "nickname");
        Columns.maxLength(n.getNickname(), 20, "nickname");
        Columns.notNull(n.getAuthor(), "author");
        Columns.maxLength(n.getAuthor(), 20, "author");
        Columns.notNull(n.getContent(), "content");
        Columns.maxLength(n.getContent(), 255, "content");
        Columns.notNull(n.getCreatedAt(), "created_at");
    }

    private static Note copy(Note n) {
        return new Note(n.getId(), n.getPlayerUUID().orElse(null), n.getNickname(), n.getAuthor(), n.getContent(), trim(n.getCreatedAt()));
    }

    private static LocalDateTime trim(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.MILLIS);
    }
}
