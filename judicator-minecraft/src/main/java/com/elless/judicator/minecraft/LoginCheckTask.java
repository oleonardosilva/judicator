package com.elless.judicator.minecraft;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The login checks of {@link JudicatorMinecraft#login} as a step of the configuration phase (NeoForge, Forge): they
 * run off the game thread, and the server asks {@link #tick} every tick until they are done. Vanilla API only: the
 * loader gives the address and how to disconnect.
 */
public final class LoginCheckTask implements ConfigurationTask {

    public static final Type TYPE = new Type("judicator:login");

    private final JudicatorMinecraft mod;
    private final GameProfile profile;
    private final String address;
    private final Consumer<Component> disconnect;
    private CompletableFuture<Optional<Component>> check;
    private boolean disconnected;

    public LoginCheckTask(JudicatorMinecraft mod, GameProfile profile, String address, Consumer<Component> disconnect) {
        this.mod = mod;
        this.profile = profile;
        this.address = address;
        this.disconnect = disconnect;
    }

    @Override
    public void start(Consumer<Packet<?>> send) {
        check = CompletableFuture.supplyAsync(() -> mod.login(profile.id(), profile.name(), address), mod.platform().asyncExecutor());
    }

    /**
     * @return true once the player may go on; never for a player who was disconnected
     */
    @Override
    public boolean tick() {
        if (check == null || !check.isDone() || disconnected) return false;
        final Optional<Component> kick = check.exceptionally(error -> {
            mod.platform().getLogger().error("Could not check the login of {}", profile.name(), error);
            return Optional.empty();
        }).join();
        if (kick.isEmpty()) return true;
        disconnected = true;
        disconnect.accept(kick.get());
        return false;
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
