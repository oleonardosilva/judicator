package com.openmc.judicator.punish.types;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public enum PunishType {

    @SerializedName("BAN")
    BAN(false),
    @SerializedName("TEMPBAN")
    TEMPBAN(true),
    @SerializedName("MUTE")
    MUTE(false),
    @SerializedName("TEMPMUTE")
    TEMPMUTE(true),
    @SerializedName("KICK")
    KICK(false);

    private final boolean temp;

    PunishType(boolean temp) {
        this.temp = temp;
    }
}
