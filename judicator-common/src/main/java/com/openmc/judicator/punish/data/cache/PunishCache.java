package com.openmc.judicator.punish.data.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.ChatContext;
import com.openmc.judicator.platform.PlatformTask;
import com.openmc.judicator.punish.PunishService;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PunishCache {

    private final Cache<String, ChatContext<PunishmentBuilder>> cache;
    private final Map<String, List<Punishment>> activeSilences = new ConcurrentHashMap<>();
    private final PlatformTask cleanupTask;

    public PunishCache(Judicator judicator) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();
        this.cleanupTask = judicator.getPlatform().runAsyncRepeating(cache::cleanUp, 6, TimeUnit.MINUTES);
    }

    public Optional<ChatContext<PunishmentBuilder>> getContext(String key) {
        return Optional.ofNullable(cache.getIfPresent(key.toLowerCase()));
    }

    public void putContext(String key, ChatContext<PunishmentBuilder> context) {
        cache.put(key.toLowerCase(), context);
    }

    public void removeContext(String key) {
        cache.invalidate(key.toLowerCase());
    }

    /**
     * Caches a mute in force under a key (a player's UUID or an IP address); a key keeps every mute it has, since a
     * mute for one server does not hide a mute that applies elsewhere.
     */
    public void putMutePunishment(String key, Punishment punishment) {
        activeSilences.compute(key.toLowerCase(), (k, mutes) -> {
            final List<Punishment> list = mutes == null ? new ArrayList<>() : new ArrayList<>(mutes);
            list.removeIf(cached -> Objects.equals(cached.getId(), punishment.getId()));
            list.add(punishment);
            return List.copyOf(list);
        });
    }

    public void removeMutePunishment(String key) {
        activeSilences.remove(key.toLowerCase());
    }

    /**
     * Drops one cached mute of a key (it ended or was revoked).
     */
    public void removeMutePunishment(String key, Long punishmentId) {
        activeSilences.computeIfPresent(key.toLowerCase(), (k, mutes) -> {
            final List<Punishment> left = mutes.stream().filter(cached -> !Objects.equals(cached.getId(), punishmentId)).toList();
            return left.isEmpty() ? null : left;
        });
    }

    /**
     * Drops a mute from every key it is cached under (the accounts it was applied to, the IP address restored at
     * login), so all of them can talk right after it is revoked.
     */
    public void forgetMute(Long punishmentId) {
        for (String key : List.copyOf(activeSilences.keySet())) removeMutePunishment(key, punishmentId);
    }

    /**
     * Puts the changed version of a cached mute (new end, reason, paused) wherever the old one is cached.
     */
    public void refreshMute(Punishment updated) {
        activeSilences.replaceAll((key, mutes) -> mutes.stream()
                .map(cached -> Objects.equals(cached.getId(), updated.getId()) ? updated : cached)
                .toList());
    }

    /**
     * Every mute cached under a key.
     */
    public List<Punishment> getMutes(String key) {
        return activeSilences.getOrDefault(key.toLowerCase(), List.of());
    }

    /**
     * The mute of a key that weighs most ({@link PunishService#heavyFirst}), wherever it applies.
     */
    public Optional<Punishment> getPunishment(String key) {
        return getMutes(key).stream().min(PunishService.heavyFirst());
    }

    public void shutdown() {
        this.cleanupTask.cancel();
    }

}
