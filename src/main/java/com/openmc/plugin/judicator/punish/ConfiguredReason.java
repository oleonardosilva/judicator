package com.openmc.plugin.judicator.punish;

import com.openmc.plugin.judicator.punish.types.PunishType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spongepowered.configurate.ConfigurationNode;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ConfiguredReason {

    private String reason;
    private String permission;
    private PunishType type;
    private boolean ip;
    private String duration;

    public static ConfiguredReason from(ConfigurationNode node) {
        final ConfiguredReason reason = new ConfiguredReason();
        reason.reason = node.node("reason").getString();
        reason.permission = node.node("permission").getString();
        reason.type = PunishType.valueOf(node.node("type").getString());
        reason.ip = node.node("ip").getBoolean();
        if (!node.node("duration").isNull())
            reason.duration = node.node("duration").getString();
        return reason;
    }

    public boolean isPermanent() {
        return duration == null;
    }

}
