package com.openmc.judicator.importer;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.notes.Note;
import com.openmc.judicator.notes.repository.NoteRepository;
import com.openmc.judicator.notes.repository.dao.NoteRelationalDAO;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PlayerAddress;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.repository.PlayerAddressRepository;
import com.openmc.judicator.punish.data.repository.PunishmentRepository;
import com.openmc.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.repository.WarnRepository;
import com.openmc.judicator.warns.repository.dao.WarnRelationalDAO;
import net.kyori.adventure.text.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code /judicator import <source> [confirm]}: reads another plugin's punishments and, with {@code confirm},
 * stores the ones Judicator does not have yet. Without {@code confirm} it only reports what would happen.
 * Running it twice is safe: a punishment with the same player, type and start is skipped.
 */
public class ImportService {

    private static final int BATCH = 500;

    private final Judicator judicator;
    private final PunishmentRepository punishments;
    private final WarnRepository warns;
    private final PlayerAddressRepository addresses;
    private final NoteRepository notes;
    private final Map<String, ImportSource> sources;
    private final AtomicBoolean running = new AtomicBoolean();

    public ImportService(Judicator judicator, PunishmentRepository punishments, WarnRepository warns,
                         PlayerAddressRepository addresses, NoteRepository notes) {
        this.judicator = judicator;
        this.punishments = punishments;
        this.warns = warns;
        this.addresses = addresses;
        this.notes = notes;
        this.sources = List.of(new LiteBansImporter(), new AdvancedBanImporter(), new LibertyBansImporter(),
                        new BanManagerImporter(), new VanillaImporter(), new JudicatorImporter()).stream()
                .collect(Collectors.toUnmodifiableMap(ImportSource::name, Function.identity()));
    }

    public Set<String> sourceNames() {
        return sources.keySet();
    }

    public void run(JudicatorSender sender, String sourceName, boolean confirm) {
        final ImportSource source = sources.get(sourceName.toLowerCase(Locale.ROOT));
        if (source == null) {
            send(sender, "§cUnknown source. Use one of: " + String.join(", ", sources.keySet().stream().sorted().toList()));
            return;
        }
        if (!running.compareAndSet(false, true)) {
            send(sender, "§cAn import is already running.");
            return;
        }
        send(sender, "§eReading " + source.name() + "...");
        try {
            judicator.getPlatform().runAsync(() -> {
                try {
                    execute(sender, source, confirm);
                } catch (ImportException e) {
                    send(sender, "§cImport failed: " + e.getMessage());
                } catch (Exception e) {
                    judicator.getLogger().error("Import from {} failed", source.name(), e);
                    send(sender, "§cImport failed: " + e + " (see the console for details).");
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException e) {
            // The scheduler refused the task (server shutting down): release the lock.
            running.set(false);
            send(sender, "§cCould not start the import: " + e.getMessage());
        }
    }

    private void execute(JudicatorSender sender, ImportSource source, boolean confirm) throws Exception {
        final ImportResult result = source.read(new ImportContext(judicator, judicator.loadImportConfig()));

        final Set<String> punishmentKeys = punishments.findAllImportKeys();
        final List<Punishment> newPunishments = new ArrayList<>();
        int duplicates = 0;
        for (Punishment punishment : result.getPunishments()) {
            final String key = PunishmentRelationalDAO.importKey(punishment);
            if (punishmentKeys.add(key)) newPunishments.add(punishment);
            else duplicates++;
        }
        final Set<String> warnKeys = warns.findAllImportKeys();
        final List<Warn> newWarns = new ArrayList<>();
        for (Warn warn : result.getWarns()) {
            if (warnKeys.add(WarnRelationalDAO.importKey(warn.getPlayerUUID().map(UUID::toString).orElse(null), warn.getNickname(), warn.getStartedAt()))) newWarns.add(warn);
            else duplicates++;
        }

        final Set<String> noteKeys = result.getNotes().isEmpty() ? new HashSet<>() : notes.findAllImportKeys();
        final List<Note> newNotes = new ArrayList<>();
        for (Note note : result.getNotes()) {
            if (noteKeys.add(NoteRelationalDAO.importKey(note))) newNotes.add(note);
            else duplicates++;
        }

        send(sender, "§e" + source.name() + ": §f" + newPunishments.size() + "§e punishments and §f" + newWarns.size()
                + "§e warns to import" + (result.getNotes().isEmpty() ? "" : ", §f" + newNotes.size() + "§e staff notes")
                + ", §f" + duplicates + "§e already in Judicator.");
        final List<PlayerAddress> addressChanges = new ArrayList<>();
        int newAddresses = 0;
        for (PlayerAddress address : result.getAddresses()) {
            final Optional<PlayerAddress> stored = addresses.findSame(address);
            if (stored.isPresent() && !address.lastUsage().isAfter(stored.get().lastUsage())) continue;
            if (stored.isEmpty()) newAddresses++;
            addressChanges.add(address);
        }
        if (!result.getAddresses().isEmpty()) {
            send(sender, "§e  IP addresses (for /alts, per account): §f" + newAddresses + "§e new, §f" + (addressChanges.size() - newAddresses)
                    + "§e used more recently in the source, §f" + (result.getAddresses().size() - addressChanges.size())
                    + "§e already up to date.");
        }
        result.getSkipped().forEach((reason, count) -> send(sender, "§7  skipped " + count + ": " + reason));

        if (!confirm) {
            send(sender, "§eNothing was written. Back up your database, then run §f/judicator import " + source.name() + " confirm§e.");
            return;
        }

        int written = 0;
        int writtenWarns = 0;
        int writtenNotes = 0;
        try {
            for (int from = 0; from < newPunishments.size(); from += BATCH) {
                written += punishments.insertAll(newPunishments.subList(from, Math.min(from + BATCH, newPunishments.size())));
            }
            for (int from = 0; from < newWarns.size(); from += BATCH) {
                writtenWarns += warns.insertAll(newWarns.subList(from, Math.min(from + BATCH, newWarns.size())));
            }
            for (int from = 0; from < newNotes.size(); from += BATCH) {
                writtenNotes += notes.insertAll(newNotes.subList(from, Math.min(from + BATCH, newNotes.size())));
            }
        } catch (SQLException e) {
            judicator.getLogger().error("Import from {} stopped while writing", source.name(), e);
            send(sender, "§cImport stopped after " + written + " punishments, " + writtenWarns + " warns and " + writtenNotes + " notes: "
                    + e.getMessage() + ". Running the same command again continues where it stopped.");
            return;
        }
        addressChanges.forEach(addresses::save);
        send(sender, "§aImported " + written + " punishments"
                + (writtenNotes == 0 ? " and " + writtenWarns + " warns" : ", " + writtenWarns + " warns and " + writtenNotes + " staff notes")
                + " from " + source.name()
                + (addressChanges.isEmpty() ? "" : " and updated " + addressChanges.size() + " IP address records")
                + ". Online players pick up imported mutes when they rejoin.");
    }

    private static void send(JudicatorSender sender, String message) {
        sender.sendMessage(Component.text(message));
    }
}
