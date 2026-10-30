package com.elless.judicator.api.event;

/**
 * Something that happened in Judicator, delivered by the {@link EventBus}. Subscribe to this type to receive every
 * event.
 * <p>
 * Every event is posted <b>after</b> the change was stored, from commands, the API, the warn tree and the evidence
 * prompt alike; none of them can be cancelled. The effects on players (kick, alert, announcement) may still be
 * running when it arrives.
 * <p>
 * <b>Thread:</b> events are posted on the thread that made the change, which is normally not the server thread
 * (commands and API calls run asynchronously). On Spigot/Paper, schedule a task before touching the world or the
 * Bukkit API that requires the main thread.
 * <p>
 * <b>Several servers:</b> an event is posted only on the server where the change was made. Servers that learn about
 * it through cross-server sync ({@code sync.enabled}) apply it to their players but do not post it again, so a
 * listener on every server of a network sees each change once.
 */
public interface JudicatorEvent {
}
