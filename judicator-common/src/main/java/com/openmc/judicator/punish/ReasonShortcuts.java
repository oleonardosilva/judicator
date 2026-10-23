package com.openmc.judicator.punish;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.data.cache.ReasonCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code #key} inside the reason of {@code /ban}, {@code /mute}, {@code /kick}, {@code /warn} and their variants is
 * replaced by the text of the {@code reasons.<key>} entry of {@code config.yml}, so staff write the same reason the
 * same way ({@code /ban Steve #reason2 x-ray} → "Cheating x-ray"). Only the text changes: type and duration are the
 * command's. A {@code #word} that is not a key stays as typed.
 */
public final class ReasonShortcuts {

    private static final String PREFIX = "#";
    private static final int REASON_MAX = 255;

    private ReasonShortcuts() {
    }

    /**
     * @return the reason with its shortcuts replaced, or empty when the sender may not use one of them (the sender
     * was told)
     */
    public static Optional<String> expand(Judicator judicator, JudicatorSender sender, String reason) {
        if (reason == null || !reason.contains(PREFIX)) return Optional.ofNullable(reason);
        final ReasonCache cache = judicator.getReasonCache();
        final String[] words = reason.split(" ", -1);
        boolean expanded = false;
        for (int i = 0; i < words.length; i++) {
            if (!words[i].startsWith(PREFIX) || words[i].length() == PREFIX.length()) continue;
            final Optional<ConfiguredReason> configured = cache.getReasonByKey(words[i].substring(PREFIX.length()));
            if (configured.isEmpty()) continue;
            if (!cache.canUse(sender, configured.get())) {
                sender.sendMessage(PunishUtils.getMessage(judicator.messagesFor(sender), "error", "reason-no-permission"));
                return Optional.empty();
            }
            words[i] = configured.get().getReason();
            expanded = true;
        }
        final String result = String.join(" ", words);
        // reason is VARCHAR(255): several shortcuts could make the database reject the punishment.
        return Optional.of(expanded && result.length() > REASON_MAX ? result.substring(0, REASON_MAX) : result);
    }

    /**
     * Tab-completion of {@code #key} for the word being typed in the reason.
     *
     * @param firstReasonArg index of the first argument that belongs to the reason
     */
    public static List<String> suggest(Judicator judicator, JudicatorSender sender, String[] args, int firstReasonArg) {
        if (args.length <= firstReasonArg) return List.of();
        final String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (!typed.startsWith(PREFIX)) return List.of();
        final ReasonCache cache = judicator.getReasonCache();
        final List<String> keys = new ArrayList<>();
        for (String key : cache.getKeys()) {
            final String shortcut = PREFIX + key;
            if (!shortcut.startsWith(typed)) continue;
            if (cache.getReasonByKey(key).map(reason -> cache.canUse(sender, reason)).orElse(false)) keys.add(shortcut);
        }
        keys.sort(null);
        return keys;
    }
}
