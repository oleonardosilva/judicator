package com.elless.judicator.api.punishment;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A ban or mute to apply through {@link com.elless.judicator.api.JudicatorApi#punish(PunishmentRequest)}, built with
 * {@link #builder(PunishmentType, String)}. The builder checks what the database would refuse, so a request that
 * builds can be stored.
 */
public final class PunishmentRequest {

    /** Longest name a target or punisher can have. */
    public static final int NAME_MAX_LENGTH = 20;
    /** Longest reason. */
    public static final int REASON_MAX_LENGTH = 255;
    /** Longest the evidence links can be, joined by commas. */
    public static final int EVIDENCE_MAX_LENGTH = 255;
    /** The punisher recorded when none is given, as for punishments applied from the console. */
    public static final String DEFAULT_PUNISHER = "Console";

    private final PunishmentType type;
    private final String target;
    private final String reason;
    private final Duration duration;
    private final String punisher;
    private final boolean silent;
    private final boolean byAddress;
    private final boolean soft;
    private final boolean onlineOnly;
    private final List<String> evidences;
    private final String scope;

    private PunishmentRequest(Builder builder) {
        this.type = builder.type;
        this.target = builder.target;
        this.reason = builder.reason;
        this.duration = builder.duration;
        this.punisher = builder.punisher;
        this.silent = builder.silent;
        this.byAddress = builder.byAddress;
        this.soft = builder.soft;
        this.onlineOnly = builder.onlineOnly;
        this.evidences = List.copyOf(builder.evidences);
        this.scope = builder.scope;
    }

    /**
     * @param type   {@link PunishmentType#BAN}, {@link PunishmentType#TEMPBAN}, {@link PunishmentType#MUTE} or
     *               {@link PunishmentType#TEMPMUTE} (kicks go through
     *               {@link com.elless.judicator.api.JudicatorApi#kick(String, String, String, boolean)})
     * @param target the name of the player to punish (online or not)
     * @return a builder for that punishment
     */
    public static Builder builder(PunishmentType type, String target) {
        return new Builder(type, target);
    }

    /** @return what to apply */
    public PunishmentType type() {
        return type;
    }

    /** @return the name of the player to punish */
    public String target() {
        return target;
    }

    /** @return the reason; empty when none was given */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    /** @return how long a temporary punishment lasts; empty for a permanent one */
    public Optional<Duration> duration() {
        return Optional.ofNullable(duration);
    }

    /** @return the name recorded as the author */
    public String punisher() {
        return punisher;
    }

    /** @return whether it is not announced to the players (staff with {@code judicator.notify.silent} still see it) */
    public boolean silent() {
        return silent;
    }

    /** @return whether it punishes the target's address (the address they are on, or the last one they joined from) */
    public boolean byAddress() {
        return byAddress;
    }

    /** @return whether it is a soft mute */
    public boolean soft() {
        return soft;
    }

    /** @return whether the time of this temporary mute only runs while the player is online */
    public boolean onlineOnly() {
        return onlineOnly;
    }

    /**
     * @return the server or group it applies to, as given to {@link Builder#scope}; empty for every server
     */
    public Optional<String> scope() {
        return Optional.ofNullable(scope);
    }

    /** @return the evidence links to attach */
    public List<String> evidences() {
        return evidences;
    }

    /**
     * Builds a {@link PunishmentRequest}. Not thread-safe.
     */
    public static final class Builder {

        private final PunishmentType type;
        private final String target;
        private String reason;
        private Duration duration;
        private String punisher = DEFAULT_PUNISHER;
        private boolean silent;
        private boolean byAddress;
        private boolean soft;
        private boolean onlineOnly;
        private final List<String> evidences = new ArrayList<>();
        private String scope;

        private Builder(PunishmentType type, String target) {
            this.type = Objects.requireNonNull(type, "type");
            this.target = Objects.requireNonNull(target, "target");
        }

        /**
         * @param reason the reason shown to the player and the staff; null or blank for none
         * @return this builder
         */
        public Builder reason(String reason) {
            this.reason = reason == null || reason.isBlank() ? null : reason;
            return this;
        }

        /**
         * @param duration how long it lasts from now; required for {@link PunishmentType#TEMPBAN} and
         *                 {@link PunishmentType#TEMPMUTE}, refused for the others
         * @return this builder
         */
        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        /**
         * @param punisher the name recorded as the author, e.g. your plugin's name (default {@code Console})
         * @return this builder
         */
        public Builder punisher(String punisher) {
            this.punisher = Objects.requireNonNull(punisher, "punisher");
            return this;
        }

        /**
         * @param silent not announced to the players; staff with {@code judicator.notify.silent} still see it
         * @return this builder
         */
        public Builder silent(boolean silent) {
            this.silent = silent;
            return this;
        }

        /**
         * @param byAddress punish the target's address: the one they are on, or the last one they joined from
         * @return this builder
         */
        public Builder byAddress(boolean byAddress) {
            this.byAddress = byAddress;
            return this;
        }

        /**
         * @param soft a soft mute (mutes only): the player still sees their own messages and is not told
         * @return this builder
         */
        public Builder soft(boolean soft) {
            this.soft = soft;
            return this;
        }

        /**
         * @param onlineOnly the time only runs while the player is online ({@link PunishmentType#TEMPMUTE} of an
         *                   account only)
         * @return this builder
         */
        public Builder onlineOnly(boolean onlineOnly) {
            this.onlineOnly = onlineOnly;
            return this;
        }

        /**
         * @param scope a server of the network or a group of {@code scopes.groups}, as staff type after
         *              {@code -scope=}; null for every server (the default)
         * @return this builder
         */
        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        /**
         * @param link an evidence link (no spaces or commas)
         * @return this builder
         */
        public Builder evidence(String link) {
            this.evidences.add(Objects.requireNonNull(link, "link"));
            return this;
        }

        /**
         * @return the request
         * @throws IllegalArgumentException when the combination is not possible or a value is too long to be stored
         */
        public PunishmentRequest build() {
            if (type == PunishmentType.KICK) {
                throw new IllegalArgumentException("Kicks are applied with JudicatorApi#kick");
            }
            checkName("target", target);
            checkName("punisher", punisher);
            if (reason != null && reason.length() > REASON_MAX_LENGTH) {
                throw new IllegalArgumentException("reason is longer than " + REASON_MAX_LENGTH + " characters");
            }
            if (type.isTemporary()) {
                if (duration == null || duration.isNegative() || duration.isZero()) {
                    throw new IllegalArgumentException(type + " needs a positive duration");
                }
            } else if (duration != null) {
                throw new IllegalArgumentException(type + " is permanent: use the temporary type for a duration");
            }
            if (soft && !type.isMute()) throw new IllegalArgumentException("Only mutes can be soft");
            if (onlineOnly && (type != PunishmentType.TEMPMUTE || byAddress)) {
                throw new IllegalArgumentException("Only a TEMPMUTE of an account (not by address) can be online-only");
            }
            for (String link : evidences) {
                if (link.isBlank() || link.chars().anyMatch(c -> c == ',' || Character.isWhitespace(c))) {
                    throw new IllegalArgumentException("Evidence links cannot be blank or have spaces or commas: " + link);
                }
            }
            if (String.join(",", evidences).length() > EVIDENCE_MAX_LENGTH) {
                throw new IllegalArgumentException("The evidence links are longer than " + EVIDENCE_MAX_LENGTH + " characters");
            }
            return new PunishmentRequest(this);
        }

        static void checkName(String what, String name) {
            if (name.isBlank() || name.length() > NAME_MAX_LENGTH || name.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException(what + " must be a name of 1 to " + NAME_MAX_LENGTH + " characters without spaces: " + name);
            }
        }
    }

}
