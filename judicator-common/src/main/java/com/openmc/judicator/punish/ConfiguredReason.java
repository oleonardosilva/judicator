package com.openmc.judicator.punish;

import com.openmc.judicator.punish.types.PunishType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.ArrayList;
import java.util.List;

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
    private List<Step> steps = new ArrayList<>();

    public static ConfiguredReason from(ConfigurationNode node) {
        final ConfiguredReason reason = new ConfiguredReason();
        reason.reason = node.node("reason").getString();
        reason.permission = node.node("permission").getString();
        reason.ip = node.node("ip").getBoolean();

        final List<Step> steps = new ArrayList<>();
        for (ConfigurationNode stepNode : node.node("steps").childrenList()) {
            steps.add(Step.from(stepNode));
        }

        if (steps.isEmpty()) {
            final String duration = node.node("duration").isNull() ? null : node.node("duration").getString();
            steps.add(new Step(PunishType.valueOf(node.node("type").getString()), duration));
        }

        reason.steps = List.copyOf(steps);
        reason.type = steps.get(0).type();
        reason.duration = steps.get(0).duration();
        return reason;
    }

    public boolean isPermanent() {
        return duration == null;
    }

    public boolean isProgressive() {
        return steps.size() > 1;
    }

    public Step stepFor(int previousOccurrences) {
        final int index = Math.max(0, Math.min(previousOccurrences, steps.size() - 1));
        return steps.get(index);
    }

    public record Step(PunishType type, String duration) {

        static Step from(ConfigurationNode node) {
            final PunishType type = PunishType.valueOf(node.node("type").getString());
            final String duration = node.node("duration").isNull() ? null : node.node("duration").getString();
            return new Step(type, duration);
        }

        public boolean isPermanent() {
            return duration == null;
        }

    }

}
