package com.openmc.judicator.testing;

import com.openmc.judicator.platform.JudicatorPlayer;
import net.kyori.adventure.text.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A player for tests: keeps every message received (as plain text) and the kick screen, if any.
 */
public final class FakePlayer implements JudicatorPlayer {

    private final String name;
    private final UUID uniqueId;
    private final String hostAddress;
    private final Set<String> permissions = new HashSet<>();
    private final List<Component> received = new ArrayList<>();
    private Component kickMessage;
    private int kicks;
    private Locale locale;

    public FakePlayer(String name, String hostAddress, String... permissions) {
        this(name, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), hostAddress, permissions);
    }

    /**
     * A player with a chosen UUID, e.g. the same account under a new name.
     */
    public FakePlayer(String name, UUID uniqueId, String hostAddress, String... permissions) {
        this.name = name;
        this.uniqueId = uniqueId;
        this.hostAddress = hostAddress;
        this.permissions.addAll(List.of(permissions));
    }

    /**
     * The language of the game client, e.g. {@code "pt_BR"} (none by default, like a client that has not sent it).
     */
    public FakePlayer locale(String tag) {
        this.locale = Locale.forLanguageTag(tag.replace('_', '-'));
        return this;
    }

    @Override
    public Optional<Locale> getLocale() {
        return Optional.ofNullable(locale);
    }

    public FakePlayer grant(String... permissions) {
        this.permissions.addAll(List.of(permissions));
        return this;
    }

    public FakePlayer deny(String... permissions) {
        List.of(permissions).forEach(this.permissions::remove);
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Override
    public String getHostAddress() {
        return hostAddress;
    }

    @Override
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    @Override
    public void sendMessage(Component message) {
        received.add(message);
    }

    @Override
    public void disconnect(Component reason) {
        this.kickMessage = reason;
        this.kicks++;
    }

    /**
     * @return every message received so far, as plain text without colour codes
     */
    public List<String> messages() {
        return received.stream().map(Texts::plain).toList();
    }

    /**
     * Commands behind the clickable parts of every message received.
     */
    public List<String> clicks() {
        return received.stream().flatMap(message -> Texts.clicks(message).stream()).toList();
    }

    public List<String> hovers() {
        return received.stream().flatMap(message -> Texts.hovers(message).stream()).toList();
    }

    /**
     * Commands a click puts in the chat box in every message received.
     */
    public List<String> suggestions() {
        return received.stream().flatMap(message -> Texts.suggestions(message).stream()).toList();
    }

    public boolean received(String fragment) {
        return messages().stream().anyMatch(message -> message.contains(fragment));
    }

    public void clearMessages() {
        received.clear();
    }

    /**
     * How many times the player was disconnected (a network must kick once, not once per server).
     */
    public int kicks() {
        return kicks;
    }

    public boolean wasKicked() {
        return kickMessage != null;
    }

    public String kickMessage() {
        return kickMessage == null ? null : Texts.plain(kickMessage);
    }

    @Override
    public String toString() {
        return "FakePlayer[" + name + "]";
    }
}
