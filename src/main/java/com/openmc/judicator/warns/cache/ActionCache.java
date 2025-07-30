package com.openmc.judicator.warns.cache;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.warns.ConfiguredAction;
import lombok.Getter;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.HashMap;
import java.util.Optional;

@Getter
public class ActionCache {

    private final HashMap<Long, ConfiguredAction> actions;

    public ActionCache() {
        this.actions = new HashMap<>();
    }

    public void initialize(Judicator judicator) {
        actions.clear();
        try {
            judicator.getConfig().node("warns").childrenMap().forEach((o, configurationNode) -> {
                try {
                    final ConfiguredAction action = ConfiguredAction.from(configurationNode);
                    actions.put(action.getCount(), action);
                } catch (SerializationException e) {
                    judicator.getLogger().error(e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            judicator.getLogger().error(e.getMessage(), e);
        }
    }

    public Optional<ConfiguredAction> getAction(Long count) {
        return Optional.ofNullable(actions.get(count));
    }

}
