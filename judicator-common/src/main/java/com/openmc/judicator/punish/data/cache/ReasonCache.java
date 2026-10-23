package com.openmc.judicator.punish.data.cache;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.ConfiguredReason;
import com.openmc.judicator.punish.types.PunishPermissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ReasonCache {

    private final Map<String, ConfiguredReason> reasons;
    private final Map<String, ConfiguredReason> reasonsByKey;
    private Judicator judicator;

    public ReasonCache() {
        this.reasons = new ConcurrentHashMap<>();
        this.reasonsByKey = new ConcurrentHashMap<>();
    }

    public void initialize(Judicator judicator) {
        this.judicator = judicator;
        reasons.clear();
        reasonsByKey.clear();
        try {
            judicator.getConfig().node("reasons").childrenMap().forEach((key, configurationNode) -> {
                final ConfiguredReason reason = ConfiguredReason.from(configurationNode);
                reasons.put(reason.getReason(), reason);
                reasonsByKey.put(String.valueOf(key).toLowerCase(Locale.ROOT), reason);
            });
        } catch (Exception e) {
            judicator.getLogger().error(e.getMessage(), e);
        }
    }


    public Optional<ConfiguredReason> getReason(String reason) {
        return Optional.ofNullable(reasons.get(reason));
    }

    /**
     * @param key the reason's key under {@code reasons:} in {@code config.yml} (case-insensitive)
     */
    public Optional<ConfiguredReason> getReasonByKey(String key) {
        return Optional.ofNullable(reasonsByKey.get(key.toLowerCase(Locale.ROOT)));
    }

    public List<String> getKeys() {
        return new ArrayList<>(reasonsByKey.keySet());
    }

    public List<ConfiguredReason> getReasons() {
        return new ArrayList<>(reasons.values());
    }

    /**
     * With {@code require-reason-permission: true}, a reason with a {@code permission} can only be used by who has
     * it (or {@code judicator.admin}).
     */
    public boolean canUse(JudicatorSender sender, ConfiguredReason reason) {
        if (!judicator.getConfig().node("require-reason-permission").getBoolean(false)) return true;
        final String permission = reason.getPermission();
        return permission == null || permission.isBlank()
                || sender.hasPermission(permission)
                || sender.hasPermission(PunishPermissions.ADMIN.getPermission());
    }

}
