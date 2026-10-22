package com.openmc.judicator.importer;

import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.warns.Warn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a source produced: the records Judicator can store and, per reason, how many it had to leave out.
 */
public final class ImportResult {

    private final List<Punishment> punishments = new ArrayList<>();
    private final List<Warn> warns = new ArrayList<>();
    private final Map<String, Integer> skipped = new TreeMap<>();

    public void add(Punishment punishment) {
        punishments.add(punishment);
    }

    public void add(Warn warn) {
        warns.add(warn);
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

    public Map<String, Integer> getSkipped() {
        return skipped;
    }
}
