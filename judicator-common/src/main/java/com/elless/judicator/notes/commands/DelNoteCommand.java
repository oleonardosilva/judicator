package com.elless.judicator.notes.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.notes.Note;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.types.PunishPermissions;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /delnote <id>}: deletes a staff note.
 */
public class DelNoteCommand extends CoreCommand {

    public DelNoteCommand(Judicator judicator) {
        super(judicator, "delnote", List.of(), PunishPermissions.DELNOTE.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<Long> id = args.word().flatMap(PunishUtils::parseId);
        if (id.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        final Optional<Note> note = judicator.getNoteService().findById(id.get());
        if (note.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages(source), "notes", "not-found"));
            return;
        }
        judicator.getNoteService().deleteById(id.get());
        judicator.getLogger().info("{} deleted note #{} about {} by {}: {}", source.getName(), id.get(),
                note.get().getNickname(), note.get().getAuthor(), note.get().getContent());
        source.sendMessage(PunishUtils.format(messages(source), Map.of("id", String.valueOf(id.get()),
                "nickname", note.get().getNickname()), "notes", "deleted"));
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        return List.of();
    }

}
