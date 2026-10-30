package com.elless.judicator.api;

import java.util.Optional;

/**
 * Where other plugins get the {@link JudicatorApi}. Judicator registers it when it is enabled and removes it when it
 * is disabled, so declare Judicator as a dependency (Spigot/Paper: {@code depend} or {@code softdepend} in
 * {@code plugin.yml}; Velocity: {@code @Dependency(id = "judicator")}) and get it in your own enable step, not in a
 * static initializer.
 */
public final class JudicatorProvider {

    private static volatile JudicatorApi instance;

    private JudicatorProvider() {
    }

    /**
     * @return the API of the Judicator running on this server
     * @throws IllegalStateException when Judicator is not installed, failed to start, or is disabled
     */
    public static JudicatorApi get() {
        final JudicatorApi api = instance;
        if (api == null) {
            throw new IllegalStateException("The Judicator API is not loaded: Judicator is not installed, failed to start, "
                    + "or is not enabled yet (declare it as a dependency of your plugin).");
        }
        return api;
    }

    /**
     * @return the API, or empty when Judicator is not loaded (for an optional integration)
     */
    public static Optional<JudicatorApi> find() {
        return Optional.ofNullable(instance);
    }

    /**
     * Called by Judicator when it is enabled; not for other plugins.
     *
     * @param api the API to hand out
     */
    public static synchronized void register(JudicatorApi api) {
        if (api == null) throw new IllegalArgumentException("api");
        instance = api;
    }

    /**
     * Called by Judicator when it is disabled; not for other plugins. Does nothing if another API was registered since.
     *
     * @param api the API that was registered
     */
    public static synchronized void unregister(JudicatorApi api) {
        if (instance == api) instance = null;
    }

}
