package com.elless.judicator.punish;

import com.elless.judicator.commons.ChatContext;
import com.elless.judicator.menus.MenuRenderer;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The evidence of a punishment asked on a dialog: one optional field of links, the same whether the punishment came
 * from a typed command or from a menu.
 */
public final class Evidence {

    private Evidence() {
    }

    /**
     * {@code prompt.dialog} of the messages; an empty answer applies without evidence.
     */
    public static MenuRenderer.Question question(ConfigurationNode messages) {
        return new MenuRenderer.Question(
                PunishUtils.fill(messages.node("prompt", "dialog", "title").getString("&8Evidence"), Map.of()),
                PunishUtils.fill(messages.node("prompt", "dialog", "label").getString("Links of the evidence:"), Map.of()),
                PunishUtils.fill(messages.node("prompt", "dialog", "confirm").getString("&aApply"), Map.of()),
                PunishUtils.fill(messages.node("prompt", "dialog", "cancel").getString("&cCancel"), Map.of()),
                true);
    }

    /**
     * The links of the answer that fit in the stored evidence, in order (the field takes 255 characters; a client could
     * send more).
     */
    public static List<String> links(String answer) {
        final List<String> links = new ArrayList<>();
        for (String link : answer.trim().split("\\s+")) {
            if (link.isEmpty()) continue;
            links.add(link);
            if (String.join(",", links).length() > ChatContext.EVIDENCE_MAX_LENGTH) {
                links.remove(links.size() - 1);
                break;
            }
        }
        return links;
    }
}
