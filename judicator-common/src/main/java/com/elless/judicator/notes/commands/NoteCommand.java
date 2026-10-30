package com.elless.judicator.notes.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.notes.Note;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.types.PunishPermissions;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /note <player> <text>}: writes down context about a player for the rest of the staff.
 */
public class NoteCommand extends CoreCommand {

    static final int CONTENT_MAX_LENGTH = 255;
    static final int NAME_MAX_LENGTH = 20;

    public NoteCommand(Judicator judicator) {
        super(judicator, "note", List.of(), PunishPermissions.NOTE.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> target = args.word();
        final String content = args.remaining().trim();
        if (target.isEmpty() || target.get().length() > NAME_MAX_LENGTH || content.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            source.sendMessage(PunishUtils.getMessage(messages(source), "notes", "too-long"));
            return;
        }
        final PlayerRef player = judicator.getPlayerDirectory().resolve(target.get());
        final String nickname = player.name();
        final String author = source.asPlayer().map(JudicatorPlayer::getName).orElse("Console");

        final Note created = Note.create(player.uuid(), nickname, author, content);
        source.asPlayer().ifPresent(staff -> created.setAuthorUuid(staff.getUniqueId()));
        final Note note = judicator.getNoteService().save(created);
        source.sendMessage(PunishUtils.format(messages(source), Map.of("id", String.valueOf(note.getId()), "nickname", nickname),
                "notes", "added"));
    }

}
