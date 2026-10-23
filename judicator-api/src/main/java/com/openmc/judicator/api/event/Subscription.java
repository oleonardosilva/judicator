package com.openmc.judicator.api.event;

/**
 * A listener registered with {@link EventBus#subscribe}.
 */
public interface Subscription {

    /**
     * Stops delivering events to the listener. Calling it again does nothing.
     */
    void unsubscribe();

    /**
     * @return whether the listener still receives events
     */
    boolean isActive();

}
