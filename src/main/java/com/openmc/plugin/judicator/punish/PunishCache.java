package com.openmc.plugin.judicator.punish;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.commons.ChatContext;
import com.velocitypowered.api.scheduler.ScheduledTask;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PunishCache {

    private final Cache<String, ChatContext<PunishmentBuilder>> cache;
    private final HashMap<String, Punishment> mutePunishments = new HashMap<>();
    private final ScheduledTask cleanupTask;

    public PunishCache(Judicator judicator) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();
        this.cleanupTask = judicator.getServer().getScheduler().buildTask(judicator, cache::cleanUp)
                .repeat(6, TimeUnit.MINUTES)
                .schedule();
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
        mutePunishments.put(key.toLowerCase(), punishment);
    }

    public void removeMutePunishment(String key) {
        mutePunishments.remove(key.toLowerCase());
    }

    public Optional<Punishment> getPunishment(String key) {
        return Optional.ofNullable(mutePunishments.get(key.toLowerCase()));
    }

    public void shutdown() {
        this.cleanupTask.cancel();
    }

}
