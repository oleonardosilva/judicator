package com.openmc.judicator.api.event;

import java.util.function.Consumer;

/**
 * Delivers Judicator's events to listeners of other plugins, on any platform.
 * <p>
 * A listener receives the events of the type it subscribed to and of its subtypes ({@link JudicatorEvent} for all of
 * them), in the order the subscriptions were made. A listener that throws is logged and skipped: it never stops the
 * punishment, nor the other listeners. Listeners run on the thread that posts the event (see
 * {@link JudicatorEvent}), so they should be quick and hand slow work to another thread.
 * <p>
 * Subscriptions do not survive a reload of Judicator; unsubscribe when your plugin is disabled.
 */
public interface EventBus {

    /**
     * @param eventType the events to receive, e.g. {@code PunishmentAppliedEvent.class}
     * @param listener  called for each of them
     * @param <T>       the event type
     * @return the subscription, to {@link Subscription#unsubscribe()} later
     */
    <T extends JudicatorEvent> Subscription subscribe(Class<T> eventType, Consumer<? super T> listener);

}
