package com.openmc.plugin.judicator.punish.types;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.Arrays;

public enum PunishType {

    @SerializedName("BAN")
    BAN("Banimento", false),
    @SerializedName("TEMPBAN")
    TEMPBAN("Banimento temporário", true),
    @SerializedName("MUTE")
    MUTE("Silenciamento", false),
    @SerializedName("TEMPMUTE")
    TEMPMUTE("Silenciamento temporário", true),
    @SerializedName("KICK")
    KICK("Suspensão", false);

    @Getter
    private final String identifier;
    @Getter
    private final boolean temp;

    PunishType(String identifier, boolean temp) {
        this.identifier = identifier;
        this.temp = temp;
    }

    public static PunishType getByIdentifier(String identifier) {
        return Arrays.stream(PunishType.values()).filter(punishType -> punishType.getIdentifier().equalsIgnoreCase(identifier)).findFirst().orElse(null);
    }
}
