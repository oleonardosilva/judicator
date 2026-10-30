package com.elless.judicator.testing;

import com.elless.judicator.notes.Note;
import com.elless.judicator.notes.repository.NoteRepository;
import com.elless.judicator.notes.repository.dao.NoteRelationalDAO;
import com.elless.judicator.players.PlayerRef;

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
    public int countOf(PlayerRef player) {
        return find(of(player)).size();
    }

    @Override
    public List<Note> findOf(PlayerRef player, int limit, int offset) {
        return find(of(player)).stream()
                .sorted(Comparator.comparing(Note::getCreatedAt).thenComparing(Note::getId).reversed())
                .skip(offset).limit(limit).toList();
    }

    @Override
    public synchronized int deleteAllOf(PlayerRef player) {
        final int before = rows.size();
        rows.values().removeIf(of(player));
        return before - rows.size();
    }

    @Override
    public synchronized int fillUuid(PlayerRef account) {
        int changed = 0;
        for (Note row : rows.values()) {
            if (row.getPlayerUUID().isEmpty() && row.getNickname().equalsIgnoreCase(account.name())) {
                row.setPlayerUUID(account.uuid());
                changed++;
            }
        }
        return changed;
    }

    private static Predicate<Note> of(PlayerRef player) {
        return n -> player.matches(n.getPlayerUUID().orElse(null), n.getNickname());
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
        final Note row = new Note(n.getId(), n.getPlayerUUID().orElse(null), n.getNickname(), n.getAuthor(), n.getContent(), trim(n.getCreatedAt()));
        row.setAuthorUuid(n.getAuthorUuid().orElse(null));
        return row;
    }

    private static LocalDateTime trim(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.MILLIS);
    }
}
