package com.openmc.plugin.judicator.punish;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.commons.ChatContext;
import com.velocitypowered.api.scheduler.ScheduledTask;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PunishProcessor {

    private final Cache<String, ChatContext<PunishmentBuilder>> cache;
    private final ScheduledTask scheduledTask;

    public PunishProcessor(Judicator judicator) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();
        this.scheduledTask = judicator.getServer().getScheduler().buildTask(judicator, cache::cleanUp)
                .repeat(10, TimeUnit.MINUTES)
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

    public void shutdown() {
        this.scheduledTask.cancel();
    }

}
