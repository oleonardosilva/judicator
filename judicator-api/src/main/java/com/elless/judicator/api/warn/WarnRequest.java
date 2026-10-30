package com.elless.judicator.api.warn;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * A warn to apply through {@link com.elless.judicator.api.JudicatorApi#warn(WarnRequest)}, built with
 * {@link #builder(String, String)}. A warn counts towards the warn tree ({@code warns} in {@code config.yml}), which may
 * run commands (for instance a tempban) when the player reaches a number of active warns.
 */
public final class WarnRequest {

    /** Longest name a target or punisher can have. */
    public static final int NAME_MAX_LENGTH = 20;
    /** Longest reason. */
    public static final int REASON_MAX_LENGTH = 255;

    private final String target;
    private final String reason;
    private final Duration duration;
    private final String punisher;
    private final boolean silent;

    private WarnRequest(Builder builder) {
        this.target = builder.target;
        this.reason = builder.reason;
        this.duration = builder.duration;
        this.punisher = builder.punisher;
        this.silent = builder.silent;
    }

    /**
     * @param target the name of the player to warn (online or not)
     * @param reason the reason (required, as for {@code /warn})
     * @return a builder for that warn
     */
    public static Builder builder(String target, String reason) {
        return new Builder(target, reason);
    }

    /** @return the name of the player to warn */
    public String target() {
        return target;
    }

    /** @return the reason */
    public String reason() {
        return reason;
    }

    /** @return how long a temporary warn lasts; empty for a permanent one */
    public Optional<Duration> duration() {
        return Optional.ofNullable(duration);
    }

    /** @return the name recorded as the author */
    public String punisher() {
        return punisher;
    }

    /** @return whether it is not announced to the players */
    public boolean silent() {
        return silent;
    }

    /**
     * Builds a {@link WarnRequest}. Not thread-safe.
     */
    public static final class Builder {

        private final String target;
        private final String reason;
        private Duration duration;
        private String punisher = "Console";
        private boolean silent;

        private Builder(String target, String reason) {
            this.target = Objects.requireNonNull(target, "target");
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        /**
         * @param duration how long the warn lasts from now; null for a permanent warn
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
         * @return the request
         * @throws IllegalArgumentException when a value is missing, invalid or too long to be stored
         */
        public WarnRequest build() {
            checkName("target", target);
            checkName("punisher", punisher);
            if (reason.isBlank() || reason.length() > REASON_MAX_LENGTH) {
                throw new IllegalArgumentException("reason must have 1 to " + REASON_MAX_LENGTH + " characters");
            }
            if (duration != null && (duration.isNegative() || duration.isZero())) {
                throw new IllegalArgumentException("duration must be positive");
            }
            return new WarnRequest(this);
        }

        private static void checkName(String what, String name) {
            if (name.isBlank() || name.length() > NAME_MAX_LENGTH || name.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException(what + " must be a name of 1 to " + NAME_MAX_LENGTH + " characters without spaces: " + name);
            }
        }
    }

}
