package com.openmc.judicator.punish.data.cache;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.ConfiguredReason;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Optional;

public class ReasonCache {

    private final Map<String, ConfiguredReason> reasons;

    public ReasonCache() {
        this.reasons = new ConcurrentHashMap<>();
    }

    public void initialize(Judicator judicator) {
        reasons.clear();
        try {
            judicator.getConfig().node("reasons").childrenMap().forEach((o, configurationNode) -> {
                final ConfiguredReason reason = ConfiguredReason.from(configurationNode);
                reasons.put(reason.getReason(), reason);
            });
        } catch (Exception e) {
            judicator.getLogger().error(e.getMessage(), e);
        }
    }


    public Optional<ConfiguredReason> getReason(String reason) {
        return Optional.ofNullable(reasons.get(reason));
    }

    public List<ConfiguredReason> getReasons() {
        return new ArrayList<>(reasons.values());
    }


}
