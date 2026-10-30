package com.elless.judicator.bukkit;

import com.elless.judicator.menus.DialogLayout;
import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuItem;
import com.elless.judicator.menus.MenuRenderer;
import com.elless.judicator.menus.MenuService;
import com.elless.judicator.platform.JudicatorPlayer;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The menus as dialogs, the screens of 1.21.6+ clients, laid out by {@link DialogLayout}: the body (texts, the items
 * without a click with their texts beside), a grid of buttons (the name as the label, the lore on hover) and the way
 * out below it, apart; a question gets a text field instead of the chat. Everything is as wide as the grid, so the
 * body, the fields and the buttons line up. Built with the Paper API and the server's Adventure; this class is only
 * loaded on a Paper server that has dialogs.
 */
final class PaperDialogs implements MenuRenderer {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final ClickCallback.Options CLICKS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofMinutes(30)).build();
    private static final int BUTTON_WIDTH = 150;
    /** the grid: its columns and the 2 pixels the client leaves between them */
    private static final int WIDTH = DialogLayout.COLUMNS * BUTTON_WIDTH + (DialogLayout.COLUMNS - 1) * 2;
    /** the texts beside an item: the grid less the item (16) and the gap before them */
    private static final int DESCRIPTION_WIDTH = WIDTH - 20;
    private static final String ANSWER = "answer";

    private final Scheduling scheduling;
    private final MenuItems items;
    private final Supplier<MenuService> service;

    PaperDialogs(Scheduling scheduling, MenuItems items, Supplier<MenuService> service) {
        this.scheduling = scheduling;
        this.items = items;
        this.service = service;
    }

    @Override
    public void open(JudicatorPlayer viewer, Menu menu) {
        final Player player = Bukkit.getPlayer(viewer.getUniqueId());
        if (player == null) return;
        final UUID uuid = player.getUniqueId();
        final DialogLayout layout = DialogLayout.of(menu);
        final List<DialogBody> body = new ArrayList<>();
        for (DialogLayout.Part part : layout.body()) {
            if (part instanceof DialogLayout.Line line) {
                body.add(DialogBody.plainMessage(text(line.text()), WIDTH));
            } else if (part instanceof DialogLayout.Content content) {
                body.add(DialogBody.item(items.create(content.item()))
                        .description(DialogBody.plainMessage(describe(content.item()), DESCRIPTION_WIDTH))
                        .showTooltip(false)
                        .build());
            }
        }
        final List<ActionButton> buttons = layout.buttons().stream().map(button -> button(uuid, menu, button)).toList();
        final ActionButton exit = layout.exit() == null ? null : button(uuid, menu, layout.exit());
        final DialogType type;
        if (layout.confirm() != null) {
            type = DialogType.confirmation(button(uuid, menu, layout.confirm()), exit);
        } else if (buttons.isEmpty()) {
            type = exit == null ? DialogType.notice() : DialogType.notice(exit);
        } else {
            type = DialogType.multiAction(buttons).columns(DialogLayout.COLUMNS).exitAction(exit).build();
        }
        player.showDialog(Dialog.create(factory -> factory.empty().base(base(menu.title()).body(body).build()).type(type)));
    }

    @Override
    public void close(JudicatorPlayer viewer) {
        final Player player = Bukkit.getPlayer(viewer.getUniqueId());
        if (player != null) player.closeDialog();
    }

    /**
     * A dialog with a text field; an empty answer counts as cancelling, unless the question is optional.
     */
    @Override
    public boolean ask(JudicatorPlayer viewer, Question question, Consumer<String> onAnswer, Runnable onCancel) {
        final Player player = Bukkit.getPlayer(viewer.getUniqueId());
        if (player == null) return false;
        final DialogBase base = base(question.title())
                .inputs(List.of(DialogInput.text(ANSWER, text(question.label())).width(WIDTH).maxLength(255).build()))
                .build();
        final ActionButton confirm = ActionButton.builder(text(question.confirm())).width(BUTTON_WIDTH)
                .action(DialogAction.customClick((response, audience) -> {
                    final String answer = response.getText(ANSWER);
                    final boolean empty = answer == null || answer.isBlank();
                    onGameThread(empty && !question.optional() ? onCancel : () -> onAnswer.accept(empty ? "" : answer)).accept(response, audience);
                }, CLICKS)).build();
        final ActionButton cancel = ActionButton.builder(text(question.cancel())).width(BUTTON_WIDTH)
                .action(DialogAction.customClick(onGameThread(onCancel), CLICKS)).build();
        player.showDialog(Dialog.create(factory -> factory.empty().base(base).type(DialogType.confirmation(confirm, cancel))));
        return true;
    }

    /**
     * A dialog with the fields of the form: a list of options per choice, a text field per text.
     */
    @Override
    public boolean form(JudicatorPlayer viewer, Form form, Consumer<Map<String, String>> onSubmit, Runnable onCancel) {
        final Player player = Bukkit.getPlayer(viewer.getUniqueId());
        if (player == null) return false;
        final List<DialogInput> inputs = new ArrayList<>();
        for (Field field : form.fields()) {
            if (field instanceof Choice choice) {
                inputs.add(DialogInput.singleOption(choice.key(), text(choice.label()), choice.options().stream()
                        .map(option -> SingleOptionDialogInput.OptionEntry.create(option.value(), text(option.label()),
                                option.value().equals(choice.selected())))
                        .toList()).width(WIDTH).build());
            } else if (field instanceof Text input) {
                inputs.add(DialogInput.text(input.key(), text(input.label())).initial(input.initial()).width(WIDTH).maxLength(64).build());
            }
        }
        final DialogBase base = base(form.title()).inputs(inputs).build();
        final ActionButton apply = ActionButton.builder(text(form.confirm())).width(BUTTON_WIDTH)
                .action(DialogAction.customClick((response, audience) -> {
                    final Map<String, String> answers = new HashMap<>();
                    for (Field field : form.fields()) {
                        final String value = response.getText(field.key());
                        if (value != null) answers.put(field.key(), value);
                    }
                    onGameThread(() -> onSubmit.accept(answers)).accept(response, audience);
                }, CLICKS)).build();
        final ActionButton cancel = ActionButton.builder(text(form.cancel())).width(BUTTON_WIDTH)
                .action(DialogAction.customClick(onGameThread(onCancel), CLICKS)).build();
        player.showDialog(Dialog.create(factory -> factory.empty().base(base).type(DialogType.confirmation(apply, cancel))));
        return true;
    }

    /**
     * A dialog that Escape closes (running its way out), the game going on behind, and that stays until the next one
     * replaces it.
     */
    private static DialogBase.Builder base(String title) {
        return DialogBase.builder(text(title(title)))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE);
    }

    private ActionButton button(UUID player, Menu menu, DialogLayout.Button button) {
        final ActionButton.Builder builder = ActionButton.builder(text(button.item().name()))
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(onGameThread(() -> click(player, menu, button.slot())), CLICKS));
        final List<String> lore = trimmed(button.item().lore());
        if (!lore.isEmpty()) builder.tooltip(text(String.join("\n", lore)));
        return builder.build();
    }

    private void click(UUID player, Menu menu, int slot) {
        final MenuService menus = service.get();
        if (menus != null) menus.click(player, menu, slot);
    }

    /**
     * On the thread of the player who answered (the game thread; Folia: their region).
     */
    private DialogActionCallback onGameThread(Runnable task) {
        return (response, audience) -> {
            if (audience instanceof Player player) scheduling.player(player.getUniqueId(), task);
            else scheduling.global(task);
        };
    }

    private static Component text(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    /**
     * The titles are written for an inventory, whose title sits on light grey (§8 by default): on the dark of a dialog
     * that colour goes, and the title is white.
     */
    private static String title(String legacy) {
        return legacy.replaceFirst("^(§[08])+", "");
    }

    private static Component describe(MenuItem item) {
        final List<String> lines = new ArrayList<>();
        lines.add(item.name());
        lines.addAll(trimmed(item.lore()));
        return text(String.join("\n", lines));
    }

    /**
     * The lines without the blank ones at both ends (no tooltip of empty space).
     */
    private static List<String> trimmed(List<String> lines) {
        int from = 0;
        int to = lines.size();
        while (from < to && blank(lines.get(from))) from++;
        while (to > from && blank(lines.get(to - 1))) to--;
        return lines.subList(from, to);
    }

    private static boolean blank(String legacy) {
        return legacy.replaceAll("§.", "").isBlank();
    }
}
