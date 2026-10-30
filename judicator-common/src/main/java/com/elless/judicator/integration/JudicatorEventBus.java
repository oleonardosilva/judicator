package com.elless.judicator.integration;

import com.elless.judicator.api.event.EventBus;
import com.elless.judicator.api.event.JudicatorEvent;
import com.elless.judicator.api.event.Subscription;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The {@link EventBus} of the API. {@link #post} runs the listeners on the calling thread, in subscription order; a
 * listener that throws is logged and the others still run, so a broken plugin never undoes or stops a punishment.
 */
public class JudicatorEventBus implements EventBus {

    private final Logger logger;
    private final List<Listener<?>> listeners = new CopyOnWriteArrayList<>();

    public JudicatorEventBus(Logger logger) {
        this.logger = logger;
    }

    @Override
    public <T extends JudicatorEvent> Subscription subscribe(Class<T> eventType, Consumer<? super T> listener) {
        final Listener<T> registered = new Listener<>(Objects.requireNonNull(eventType, "eventType"),
                Objects.requireNonNull(listener, "listener"));
        listeners.add(registered);
        return registered;
    }

    /**
     * Builds the event only when someone listens, and never throws: an event that cannot be built is logged, as a
     * listener that fails is, because the change it reports is already stored. A null event is not posted.
     */
    public void post(Supplier<? extends JudicatorEvent> event) {
        if (listeners.isEmpty()) return;
        final JudicatorEvent built;
        try {
            built = event.get();
        } catch (RuntimeException e) {
            logger.error("Could not build an API event; the punishment went on.", e);
            return;
        }
        if (built != null) post(built);
    }

    /**
     * Takes now what an event posted later needs (the state before a change), only when someone listens; empty when
     * nobody does or it could not be taken (logged).
     */
    public <T> Optional<T> capture(Supplier<T> state) {
        if (listeners.isEmpty()) return Optional.empty();
        try {
            return Optional.of(state.get());
        } catch (RuntimeException e) {
            logger.error("Could not prepare an API event; the punishment went on.", e);
            return Optional.empty();
        }
    }

    public void post(JudicatorEvent event) {
        for (Listener<?> listener : listeners) {
            try {
                listener.offer(event);
            } catch (VirtualMachineError e) {
                throw e;
            } catch (Throwable e) {
                logger.error("A listener of {} threw an exception; the punishment went on.", event.getClass().getSimpleName(), e);
            }
        }
    }

    private final class Listener<T extends JudicatorEvent> implements Subscription {

        private final Class<T> type;
        private final Consumer<? super T> consumer;

        private Listener(Class<T> type, Consumer<? super T> consumer) {
            this.type = type;
            this.consumer = consumer;
        }

        private void offer(JudicatorEvent event) {
            if (type.isInstance(event)) consumer.accept(type.cast(event));
        }

        @Override
        public void unsubscribe() {
            listeners.remove(this);
        }

        @Override
        public boolean isActive() {
            return listeners.contains(this);
        }
    }

}
