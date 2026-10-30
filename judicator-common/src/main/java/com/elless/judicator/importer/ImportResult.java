package com.elless.judicator.importer;

import com.elless.judicator.notes.Note;
import com.elless.judicator.punish.PlayerAddress;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.warns.Warn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What a source produced: the records Judicator can store and, per reason, how many it had to leave out.
 */
public final class ImportResult {

    private final List<Punishment> punishments = new ArrayList<>();
    private final List<Warn> warns = new ArrayList<>();
    private final List<PlayerAddress> addresses = new ArrayList<>();
    private final List<Note> notes = new ArrayList<>();
    private final Map<String, Integer> skipped = new TreeMap<>();
    private final Set<String> advice = new TreeSet<>();

    public void add(Punishment punishment) {
        punishments.add(punishment);
    }

    public void add(Warn warn) {
        warns.add(warn);
    }

    public void add(PlayerAddress address) {
        addresses.add(address);
    }

    public void add(Note note) {
        notes.add(note);
    }

    public void skip(String reason) {
        skipped.merge(reason, 1, Integer::sum);
    }

    public List<Punishment> getPunishments() {
        return punishments;
    }

    public List<Warn> getWarns() {
        return warns;
    }

    public List<PlayerAddress> getAddresses() {
        return addresses;
    }

    public List<Note> getNotes() {
        return notes;
    }

    /**
     * Something the server owner must do or know after the import (shown once, however many rows caused it).
     */
    public void advise(String text) {
        advice.add(text);
    }

    public Set<String> getAdvice() {
        return advice;
    }

    public Map<String, Integer> getSkipped() {
        return skipped;
    }
}
