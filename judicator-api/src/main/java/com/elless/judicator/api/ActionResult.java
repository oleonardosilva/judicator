package com.elless.judicator.api;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of an action requested through {@link JudicatorApi}: a refusal is a status, not an exception.
 *
 * @param status how it ended
 * @param value  what was stored, when {@link #isSuccess()} (see each method for when it may still be empty)
 * @param <T>    what the action produces
 */
public record ActionResult<T>(ActionStatus status, Optional<T> value) {

    public ActionResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(value, "value");
    }

    /**
     * @param value what was stored; null when nothing was
     * @param <T>   what the action produces
     * @return a {@link ActionStatus#SUCCESS} result
     */
    public static <T> ActionResult<T> success(T value) {
        return new ActionResult<>(ActionStatus.SUCCESS, Optional.ofNullable(value));
    }

    /**
     * @param status why it was not done
     * @param <T>    what the action would have produced
     * @return a result without value
     */
    public static <T> ActionResult<T> failure(ActionStatus status) {
        if (status == ActionStatus.SUCCESS) throw new IllegalArgumentException("SUCCESS is not a failure");
        return new ActionResult<>(status, Optional.empty());
    }

    /**
     * @return whether the action was done
     */
    public boolean isSuccess() {
        return status == ActionStatus.SUCCESS;
    }

}
