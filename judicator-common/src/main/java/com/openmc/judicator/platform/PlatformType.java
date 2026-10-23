package com.openmc.judicator.platform;

public enum PlatformType {

    VELOCITY(true),
    BUNGEECORD(true),
    BUKKIT(false);

    private final boolean proxy;

    PlatformType(boolean proxy) {
        this.proxy = proxy;
    }

    /**
     * @return whether Judicator runs on a proxy in front of the servers (Velocity, BungeeCord): it sees which server
     * each player is on and moves them between servers, but not the game
     */
    public boolean isProxy() {
        return proxy;
    }

}
