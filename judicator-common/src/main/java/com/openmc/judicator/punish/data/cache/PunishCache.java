package com.openmc.judicator.punish.data.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.ChatContext;
import com.openmc.judicator.platform.PlatformTask;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PunishCache {

    private final Cache<String, ChatContext<PunishmentBuilder>> cache;
    private final Map<String, Punishment> activeSilences = new ConcurrentHashMap<>();
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

    public void putMutePunishment(String key, Punishment punishment) {
        activeSilences.put(key.toLowerCase(), punishment);
    }

    public void removeMutePunishment(String key) {
        activeSilences.remove(key.toLowerCase());
    }

    /**
     * Drops a mute from every key it is cached under (the names of the accounts it was applied to, the IP address
     * restored at login), so all of them can talk right after it is revoked.
     */
    public void forgetMute(Long punishmentId) {
        activeSilences.values().removeIf(punishment -> Objects.equals(punishment.getId(), punishmentId));
    }

    /**
     * Puts the changed version of a cached mute (new end, reason, paused) wherever the old one is cached.
     */
    public void refreshMute(Punishment updated) {
        activeSilences.replaceAll((key, punishment) -> Objects.equals(punishment.getId(), updated.getId()) ? updated : punishment);
    }

    public Optional<Punishment> getPunishment(String key) {
        return Optional.ofNullable(activeSilences.get(key.toLowerCase()));
    }

    public void shutdown() {
        this.cleanupTask.cancel();
    }

}
