package com.openmc.plugin.judicator.punish.types;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum PunishStatus {

    @SerializedName("FINISHED")
    FINISHED("Finalizada"),
    @SerializedName("ACTIVE")
    ACTIVE("Ativa"),
    @SerializedName("REVOKED")
    REVOKED("Revogada");

    private final String identifier;

    PunishStatus(String identifier) {
        this.identifier = identifier;
    }

    public static PunishStatus getByIdentifier(String identifier) {
        return Arrays.stream(PunishStatus.values()).filter(punishStatus -> punishStatus.getIdentifier().equals(identifier)).findFirst().orElse(null);
    }

}
