# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Judicator** is a moderation plugin (bans, mutes, kicks, warns) for Minecraft. It runs on the
[Velocity](https://velocitypowered.com/) and BungeeCord proxies and on Spigot/Paper servers (1.18+), backed by PostgreSQL, MySQL or SQLite,
with Discord webhook notifications and multi-language support.

## Build Commands

```bash
# Build every module (each platform module produces a shadow JAR in <module>/build/libs/)
./gradlew build

# Run a local Velocity dev proxy with the plugin loaded
./gradlew :judicator-velocity:runVelocity

# Run a local Paper dev server with the plugin loaded
./gradlew :judicator-bukkit:runServer
```

```bash
# Unit and SQLite integration tests of the core (JUnit 5)
./gradlew :judicator-common:test
```

Tests live in `judicator-common/src/test/java`:

- `features/*Test` run the whole core through `testing/TestServer`: the real `*.yml` resources, commands, handlers
  and services, in-memory repositories and a `FakePlatform` (players added by the test, tasks run immediately,
  announcements and console commands recorded). Player actions go through the same `ConnectionHandler` /
  `ChatHandler` calls the Velocity/Bukkit listeners make: `server.online(player)`, `server.command(sender, "ban
  Steve x")`, `server.chat(player, "confirm")`, `server.quit(player)`, `server.restart()`. Assert on the text of
  `messages_en.yml` (`player.received("...")`). Mojang lookups are off in `TestServer`.
  Change settings with `TestServer.in(folder).set("ip-limits.max-online", 2)` / `.remove("cooldowns")` (fails
  if the setting is not in the bundled `config.yml`); exemptions with `.immunePlayers(...)`.
- `database/RepositoryContract` runs the same tests on the in-memory repositories and on the SQLite DAOs, so the
  test doubles cannot drift from the SQL. The in-memory repositories reject what the tables reject (NOT NULL,
  VARCHAR lengths) with an exception, where the DAOs would only log it and lose the row.
- Unit tests for parsers, `ConfiguredReason`, importer helpers, etc.

Add a test with every bug fix and every new command, parser, DAO query or importer; see it fail before the fix.
There is no lint task.

## Modules

| Module | Responsibility |
|---|---|
| `judicator-api` | Public API for other plugins, package `com.openmc.judicator.api`: `JudicatorApi` (async lookups, apply/revoke), `JudicatorProvider`, immutable views (`PunishmentView`, `WarnView`), requests, `ActionResult`, the `EventBus` and its events. Plain Java 17 with no dependencies (no Velocity, Bukkit, Adventure, Configurate or core types). Shipped unrelocated inside every platform JAR (never add it to a `relocate`). |
| `judicator-common` | Platform-independent core: `Judicator` (bootstrap of configs, caches, DB and services), commands, services, repositories/DAOs, caches, messages, resources (`*.yml`) and the `platform/` abstraction. Must not import Velocity, BungeeCord or Bukkit APIs. |
| `judicator-velocity` | Velocity entry point (`JudicatorVelocity`, `@Plugin`), `VelocityPlatform`, command adapter (`SimpleCommand`), event listener, runtime `DependencyManager` (Maven Central only; every jar has a SHA-256 fixed in `JudicatorVelocity` and is not loaded if it does not match — changing a version means changing its hash). |
| `judicator-bungee` | BungeeCord entry point (`JudicatorBungee`, `bungee.yml`), `BungeePlatform`, command adapter (`Command` + `TabExecutor`), event listener. Shades and relocates Adventure, Configurate and slf4j (BungeeCord keeps its own slf4j internal); JDBC drivers (SQLite included), HikariCP and Caffeine come from `libraries:` in `bungee.yml`. |
| `judicator-bukkit` | Spigot/Paper entry point (`JudicatorBukkit`, `plugin.yml`), `BukkitPlatform`, command adapter (`TabExecutor`), event listener. Shades and relocates Adventure and Configurate; JDBC drivers, HikariCP and Caffeine come from `libraries:` in `plugin.yml`. |

### Platform abstraction (`judicator-common/.../platform`)

`Platform` (players, scheduler, console commands, announcements, online mode), `JudicatorSender` and
`JudicatorPlayer`. Core code talks only to these; each platform module implements them.

- `runAsync` for I/O (database, HTTP). `runSync` for anything that touches the game: on Bukkit it is the main thread,
  on a proxy it is just another async task.
- Commands are `CoreCommand` subclasses (`execute(sender, CommandArgs)`, `suggest`, `canUse`). The adapters run
  `execute` asynchronously on both platforms. New commands must be added to `Judicator#createCommands` and, for
  Bukkit, to `judicator-bukkit/src/main/resources/plugin.yml`.
- Login/chat logic lives in `ConnectionHandler` and `ChatHandler`; each platform listener only translates its events.
  `ChatHandler.chatDecision` returns `ALLOW`/`BLOCK`/`SOFT` (soft mute: Bukkit keeps the message for the sender only,
  in a HIGHEST listener; the proxies deny it and echo `softMuteEcho` to the sender).
- Messages for someone: `Judicator.messagesFor(recipient)` (their client language with `per-player-language`, else
  the server's); messages to several players are built per recipient (`Platform.announceToServerOf(player,
  Function)`, `StaffNotifier.sendToPermission(permission, Function)`). Login screens, Discord and the console use
  `getMessagesConfig()`.

### Punishment Flow

1. **Pre-login** — `ConnectionHandler.checkPreLogin`: bans on the IP (and on the name in offline mode). After
   authentication `checkLogin`: `PlayerDirectory.joined`, the account's bans, `AddressPolicy.checkLogin` (`ip-limits`),
   then `alts.deny-if-banned`. A proxy also checks scoped bans on the first server and on each switch (Velocity
   `PlayerChooseInitialServerEvent`/`ServerPreConnectEvent`; BungeeCord `PostLoginEvent` target/`ServerConnectEvent`).
   `PlatformType.isProxy()` is how the core tells a proxy from Spigot/Paper; never compare with `VELOCITY`.
2. **Login** — active mutes go into `PunishCache`; `onLoginAllowed` resumes paused online-only mutes and stores the IP
   (`AccessAddressService`); `onJoin` records the staff level and tells `judicator.notes.notify` about staff notes.
   `onDisconnect` (and `Judicator.disable`) pauses online-only mutes.
3. **Chat/Command** — `ChatHandler` blocks muted players and handles the evidence prompt of staff members.
4. **Command** → staff rules (`ImmuneCache.canPunish(source, target, type)`: self, `immune.yml` `players` (the 1.0.0 `users` list is still honoured), hierarchy; `punish/rules/`:
   `TimeLimitService` and `PunishCooldown`; `AddressPolicy.isExempt` for IP punishments) → `PunishFactory` (checks the
   cooldown again at confirmation) → `BanHandler`/`MuteHandler` → `PunishService.save()` → DB → Discord webhook.
   `ReasonShortcuts` expands `#key` reasons; list commands page with `commons/Pagination` (clickable footer).
   Changes to a stored punishment go through `PunishService.changeEnd`/`changeReason` (an `UPDATE` that leaves revoked
   rows alone and refreshes the mute cache), never a full `save` that could undo a revocation.

### API (`judicator-api`)

The core side lives in `integration/`: `JudicatorApiService` (the `JudicatorApi`; an API caller is an `ApiSender`, which
the staff rules treat as the console, but `ImmuneCache`/`AddressPolicy` exemptions still refuse), `JudicatorEventBus`
and `ApiViews` (core model → API views, `LocalDateTime` → `Instant`). `Judicator.enable()` registers the API in
`JudicatorProvider`; `disable()` clears it. Events are posted, after the change is stored and only on the instance
that made it (never from `SyncService`), by `PunishFactory`, `KickHandler`, `WarnHandler`, `RevokeHandler`,
`UnwarnHandler` and `EditPunishmentCommand`: a new way of applying or revoking must go through those handlers.

`placeholders/PlaceholderService` builds `%judicator_...%` from the API alone: a Caffeine cache of each player's ban,
mute, history and warns, loaded on the first request and refreshed every 30 s and on the API events, so a placeholder
never blocks on the database. On Spigot/Paper `JudicatorExpansion` (the only class importing PlaceholderAPI, loaded
only when it is installed) hands it to PlaceholderAPI. A new placeholder goes in `PlaceholderService` and in
`features/PlaceholdersTest`.

### Identity

`players/`: a player is a `PlayerRef` — identified by UUID when `Judicator.trustsUuids()` (online mode or
`trust-uuids`), by name otherwise. `PlayerDirectory.resolve(name)` turns a typed name into one (online player, then the
`players` table, then Mojang in online mode); `refOf(player)` for someone online. Queries go through
`commons/db/IdentitySql` (`player_uuid = ? OR (player_uuid IS NULL AND lower_nickname = ?)`), the SQL twin of
`PlayerRef.matches`, which the in-memory repositories use. Records stored for a name nobody had seen have no UUID
(warns keep the offline-mode UUID of the name instead, `Warn#getAccountUuid`); `PlayerDirectory.joined` gives them the
account's UUID at login. Never look players up by name in new code: take a `PlayerRef`. Staff history uses
`punisher_uuid`/`author_uuid`. Alts are `player_addresses` rows (one per account and address).

### Scopes and reports

`punish/scopes/ScopeService`: a punishment's `scope` is null (everywhere), `server:<name>` or `group:<name>`
(`scopes.groups`). The proxy knows each player's backend (`Platform.serverOf`); Spigot/Paper uses `scopes.server-name`.
Logins only count bans that `refusesLogin`; the mute cache keeps every mute of a key and `ChatHandler` picks the one
that applies where the player is. `reports/`: `ReportService` (cooldown, states OPEN/ASSIGNED/CLOSED, notices, sync
`REPORTED`/`REPORT_CHANGED`), `/report`, `/reports`; `/reports tp` on a proxy sends a `TeleportRequest` on the
`judicator:main` plugin channel (the proxy drops client messages on it; the backend in `proxy-mode` also checks
`judicator.reports.tp`) and `PendingTeleports` waits for the backend's `TeleportResult`.

### Database

Configured in `data.yml`. Every table and index name carries `database.table-prefix` (`judicator_`): DAOs write
`{table}` in their SQL and `RelationalDBManager.sql()` puts the prefixed name in; `ensureColumn`/`ensureIndex` take the
name without it. `commons/db/LegacyTables` renames the 1.x tables at startup. PostgreSQL (default), MySQL and SQLite (`commons/db/Dialect`). HikariCP pool in
`RelationalDBManager`; DDL for the three dialects lives in each `*RelationalDAO.initialize()`. A column added after
1.0.0 goes in the `CREATE TABLE` **and** in `SchemaUtil.ensureColumnExists` (for tables created by older versions).
A paused online-only mute has `finish_at` NULL and `paused_remaining` set, so "in force" queries stay the same.
`commons/DataRetentionService` deletes old records (`retention.*`, at startup and every 12 hours) and runs
`/judicator clear`. Dates go through
`commons/db/SqlTime` (a `LocalDateTime` is stored as that time in UTC and read back the same way; "now" is a
parameter, never `CURRENT_TIMESTAMP`, which follows the database time zone and does not work in SQLite).

`/judicator import` (console only) lives in `importer/`: one `ImportSource` per plugin (LiteBans, AdvancedBan,
LibertyBans, BanManager, vanilla ban lists, another Judicator database — including its staff notes), `ImportContext` for the shared conversions and
`ImportService` for dry-run, de-duplication, batch inserts and merging of IP history. Sources are read-only; their connection settings are in `import.yml`.

### Localization

`messages_en.yml` / `messages_es.yml` / `messages_pt.yml` in `judicator-common/src/main/resources/`. Every new key
must exist in the three files.
