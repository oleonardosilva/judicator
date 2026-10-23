package com.openmc.judicator.notes.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.notes.Note;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.types.PunishPermissions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
        final Optional<JudicatorPlayer> online = judicator.getPlatform().getPlayer(target.get());
        final String nickname = online.map(JudicatorPlayer::getName).orElse(target.get());
        final UUID uuid = online.map(JudicatorPlayer::getUniqueId).orElseGet(() -> judicator.getUuidManager().getUUID(nickname));
        final String author = source.asPlayer().map(JudicatorPlayer::getName).orElse("Console");

        final Note note = judicator.getNoteService().save(Note.create(uuid, nickname, author, content));
        source.sendMessage(PunishUtils.format(messages(source), Map.of("id", String.valueOf(note.getId()), "nickname", nickname),
                "notes", "added"));
    }

}
