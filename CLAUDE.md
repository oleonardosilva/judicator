# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Judicator** is a moderation plugin (bans, mutes, kicks, warns) for Minecraft. It runs on the
[Velocity](https://velocitypowered.com/) and BungeeCord proxies, on Spigot/Paper servers (1.8.8+, Java 17+) and Folia (1.20.1+), and as a
Fabric, NeoForge or Forge mod (Minecraft 26.1+, Java 25), and (experimental) on Hytale, backed by PostgreSQL, MySQL or SQLite,
with Discord webhook notifications and multi-language support.

## Build Commands

Gradle 9.5 through the wrapper; the JDKs of the toolchains (17, 21) are downloaded when missing (foojay).

```bash
# Build every module (each platform module produces a shadow JAR in <module>/build/libs/; the plain jar is *-plain)
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
| `judicator-api` | Public API for other plugins, package `com.elless.judicator.api`: `JudicatorApi` (async lookups, apply/revoke), `JudicatorProvider`, immutable views (`PunishmentView`, `WarnView`), requests, `ActionResult`, the `EventBus` and its events. Plain Java 17 with no dependencies (no Velocity, Bukkit, Adventure, Configurate or core types). Shipped unrelocated inside every platform JAR (never add it to a `relocate`). |
| `judicator-common` | Platform-independent core: `Judicator` (bootstrap of configs, caches, DB and services), commands, services, repositories/DAOs, caches, messages, resources (`*.yml`) and the `platform/` abstraction. Must not import Velocity, BungeeCord or Bukkit APIs. |
| `judicator-velocity` | Velocity entry point (`JudicatorVelocity`, `@Plugin`), `VelocityPlatform`, command adapter (`SimpleCommand`), event listener, downloads its libraries with the core's `commons/libs/DependencyManager` (Maven Central only; every jar has a SHA-256 fixed in `commons/libs/Libraries` and is not loaded if it does not match — changing a version means changing its hash). |
| `judicator-bungee` | BungeeCord entry point (`JudicatorBungee`, `bungee.yml`), `BungeePlatform`, command adapter (`Command` + `TabExecutor`), event listener. Shades and relocates Adventure, Configurate and slf4j (BungeeCord keeps its own slf4j internal); JDBC drivers (SQLite included), HikariCP and Caffeine come from `libraries:` in `bungee.yml`. |
| `judicator-bukkit` | Spigot/Paper entry point (`JudicatorBukkit`, `plugin.yml`), `BukkitPlatform`, command adapter (`TabExecutor`), event listener. Supports 1.8.8 to the latest version: it compiles against `spigot-api` 1.8.8, and API added later goes through reflection in `BukkitCompat` (never reference a `Material` constant or a method that 1.8 lacks). Shades and relocates Adventure, Configurate, HikariCP, Caffeine and slf4j (+ `slf4j-jdk14`); Gson stays the server's (1.8 has Gson 2.2.4: no `JsonParser.parseString`). The MySQL/PostgreSQL drivers come from `libraries:` in `plugin.yml` (1.16.5+); before 1.16.5 `LegacyDrivers` downloads the driver of `data.yml` into a class loader of its own, which `RelationalDBManager` uses through a `DriverDataSource` (`Platform.databaseDrivers()`). Every task goes through `Scheduling` (never `Bukkit.getScheduler()`, which throws on Folia): `BukkitScheduling` on Spigot/Paper, `FoliaScheduling` on Folia (`folia-supported: true` in `plugin.yml`), in the `folia` source set compiled against `paper-api` 1.20.1 and added to the jar by `pluginJar` like the dialogs: the console on the global region, a player's inventory, kick and teleport on their entity scheduler (`Scheduling.player`). |
| `judicator-minecraft` | What the mod loaders share (Minecraft 26.1+ is no longer obfuscated, so code compiled against the vanilla server with Loom runs on Fabric, NeoForge and Forge; Java 25, versions in `gradle.properties`): `MinecraftPlatform` (one game thread, players tracked on join/quit, async executor), `MinecraftCommands` (each core command as a Brigadier literal with one greedy argument, `requires` = `canUse`, replacing a vanilla node of the same name: `/ban`, `/kick`, `/banlist`, `/pardon`), `MinecraftText` (Adventure → vanilla components node by node, no JSON), `JudicatorMinecraft` (lifecycle and the login/join/quit/chat/command hooks every loader calls), `MinecraftPermissions` (per loader; `defaultLevel`/`byDefault` without a permissions mod), `LoginCheckTask` (the login checks as a vanilla `ConfigurationTask` polled by `tick()`, for the loaders that hold the login in the configuration phase), `LuckPermsCheck` (the LuckPerms API, the only class with LuckPerms types, loaded only when it is installed). The JDBC driver is downloaded into an isolated class loader (`commons/libs/DriverClassLoader`, shared with Spigot/Paper before 1.16.5). |
| `judicator-fabric` | Fabric mod (`JudicatorFabric`, `fabric.mod.json`): Fabric API events (login `QUERY_START` with a `LoginSynchronizer`, play join/disconnect, `ALLOW_CHAT_MESSAGE`, `CommandRegistrationCallback` for data pack reloads), `CommandsMixin` (every command before it runs: muted and prompt commands), `ServerLoginPacketListenerImplAccessor` (profile and connection of a login), permissions through fabric-permissions-api (jar-in-jar). Shades and relocates Adventure, Configurate, HikariCP and Caffeine; Minecraft brings slf4j, Gson and Guava. No menus yet (`Platform.menus()` empty). Checked with the bot on Fabric 26.1.2 (mineflayer does not speak 26.3), with and without LuckPerms. |
| `judicator-neoforge` | NeoForge mod (`JudicatorNeoForge`, `@Mod(dist = DEDICATED_SERVER)`, `META-INF/neoforge.mods.toml`; ModDevGradle, `neoForgeVersion` in `gradle.properties`): `RegisterConfigurationTasksEvent` (mod bus) adds a `LoginCheckTask`; `ServerChatEvent`, `CommandEvent` (cancelled for muted and prompt commands), `RegisterCommandsEvent`, logged in/out, server starting/stopping on the game bus. Permissions (`NeoForgePermissions`): the LuckPerms API when it is loaded (`LuckPermsCheck`; any string, so the dynamic levels, time limits and reason permissions work), otherwise the `judicator.*` nodes of `PunishPermissions` registered in `PermissionGatherEvent.Nodes` and the op level for the rest. Shaded like the Fabric jar. Checked with the bot on NeoForge 26.1.2, with and without LuckPerms. |
| `judicator-forge` | Forge mod (`JudicatorForge`, `@Mod`, `META-INF/mods.toml`; ForgeGradle 7, `forgeVersion` in `gradle.properties`, its first setup takes minutes): the same as NeoForge on Forge's EventBus 7 (`SomeEvent.BUS.addListener`; on a cancellable bus the listener is a predicate, true cancels), the login check through `GatherLoginConfigurationTasksEvent`, `ForgePermissions` (Forge's permission API, same rules as NeoForge). Forge sends no RCON reply to a command without immediate output (vanilla `/say` included), so Judicator's async replies never reach an RCON client there. Checked with the bot on Forge 26.1.2, with and without LuckPerms. |
| `judicator-hytale` | Hytale plugin, **experimental: compiled against the server API (`hytaleVersion` in `gradle.properties`) but never run on a server** (`JudicatorHytale extends JavaPlugin`, `manifest.json`; Java 25 toolchain, release 21). `HytalePlatform` (worlds tick on threads of their own, so `runSync` is just another async task, like the proxies; `/reports tp` runs Hytale's `/tp` as the console), `HytaleCommand` (each core command as an `AbstractCommand` taking the whole line in `acceptCall`, replacing Hytale's `/ban`, `/unban`, `/kick` in `CommandManager.getCommandRegistration()`), `HytaleText` (Adventure → `Message`; Hytale draws no § codes, so texts with them are parsed as legacy first: covered by `HytaleTextTest`), `HytaleLog` (java.util.logging → `HytaleLogger`, for `JulLoggerAdapter`), events `PlayerSetupConnectEvent` (login checks, cancel with a reason), `PlayerConnectEvent`/`PlayerDisconnectEvent`, `PlayerChatEvent` (async, `registerAsyncGlobal`). No pre-command event: muted players' commands are not blocked. `hasExplicitPermission` ignores grants through a wildcard (a probe node nobody grants). Shades slf4j (+ slf4j-jdk14) since the server has none; uses the server's Gson. |

### Platform abstraction (`judicator-common/.../platform`)

`Platform` (players, scheduler, console commands, announcements, online mode), `JudicatorSender` and
`JudicatorPlayer`. Core code talks only to these; each platform module implements them.

- `runAsync` for I/O (database, HTTP). `runSync` for anything that touches the game: on Bukkit it is the main thread,
  on a proxy it is just another async task. `runFor(player, task)` for what touches one player (their menu): the same
  as `runSync`, except on Folia, where it is the player's region (`MenuService` keeps each viewer's session there;
  `FakeMenus` fails a test that shows a menu outside of it).
- Commands are `CoreCommand` subclasses (`execute(sender, CommandArgs)`, `suggest`, `canUse`). The adapters run
  `execute` asynchronously on both platforms. New commands must be added to `Judicator#createCommands`, to a category of
  `commons/HelpMenu` with `help.commands.<name>` (usage and description) in every messages file (`MessagesTest` fails
  otherwise) and, for Bukkit, to `judicator-bukkit/src/main/resources/plugin.yml`.
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
3. **Chat/Command** — `ChatHandler` blocks muted players and handles the evidence prompt of staff members
   (`PunishmentCommandSupport.submit`: a `MenuRenderer.ask` dialog when the platform has one for the player, else a
   `ChatContext` in the chat). Its buttons run `/jprompt (word)` (`PromptCommand`): clients since 1.19.1 send clicks as
   commands, so a chat word as a click never arrives as chat; a prompt takes the commands typed meanwhile, and treats
   `/jprompt (word)` and the bare word as that word.
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

### Menus

`menus/`: the inventory menus of `/jmenu` (`MenuCommand`, only registered when `Platform.menus()` has a
`MenuRenderer`, i.e. on Spigot/Paper). Screens (`menus/screens/*Screen`) build a platform-neutral `Menu` (title, rows,
`MenuItem`s with a `MenuIcon`, § texts from the `menu` section of the messages and a click action) off the game thread;
`MenuService` keeps a stack of screens per viewer, shows the built menu on the game thread and receives the clicks and
closes from the renderer (a click on a menu that is no longer the current one is ignored). Actions never touch services
directly: they run the command a staff member would type (`MenuService.run`, which checks `canUse` again), a
confirmation screen first for what changes data, and `CommandArgs.confirmed` (only built there) applies a punishment
without the evidence prompt. Free text (a reason, a note) goes through `MenuService.askCommand`: the menu closes and the
next chat line is taken by a `ChatContext.textPrompt` in `PunishCache` (the cancel word brings the menu back).
On Spigot/Paper the renderer is `BukkitMenuChooser`: inventories (`BukkitMenus`, `MenuItems`), or dialogs
(`PaperDialogs`, in the `paper` source set of `judicator-bukkit`, compiled with Java 21 against `paper-api` and added to
the final jar by `pluginJar` *after* the shadow jar, so it uses the server's own Adventure — never reference relocated
libraries or Configurate there) when the server has the Paper Dialog API and the client is 1.21.6+ (ViaVersion's version
when present), as `menus.dialogs` says: `mixed` (default) keeps the menus built by `MenuContext.page` (`Menu.isList()`)
as inventories, `all`, `none`. `DialogLayout` (platform-neutral, `DialogLayoutTest`) lays a `Menu` out for a dialog,
where slots mean nothing: the body (header lines, a list's page and options as text, the items without a click), a grid
of `COLUMNS` buttons, and the way out apart at the bottom (Escape presses it): back, or close on the first screen of the
stack (`Menu.hasPrevious()`, set by `MenuService`); on a confirmation (no back nor close) its cancel, beside the yes
when that is the only other choice. A list's entries go in the seven middle columns of the four middle rows (`MenuContext.entrySlot`, `PAGE_SIZE` 28), its navigation row at the bottom (`MenuContext.NAVIGATION`); as a dialog it puts its page buttons above the entries in full rows (the page button fills
an odd row). `PaperDialogs` only draws that layout. A dialog asks questions and forms itself (`MenuRenderer.ask`, `MenuRenderer.form`);
inventories fall back to the chat (only the search; the order and filters have buttons). Switching a player from an
inventory to a dialog closes the inventory first, never inside its click event. Lists keep their `ListOptions` (order,
filter, status, search) in the screen record and declare what they offer in a `ListOptions.Spec`; long lists from the
database go through `commons/ListQuery` (`PunishmentRepository.findActive`, `ReportRepository.findByStates`: LIKE with
`ESCAPE '!'`, covered by `RepositoryContract`), the per-player ones are sorted in memory. Each `Menu` has a `key` (the screen's class name without `Screen`) and the optional
`menu.style.<key>.header` lines, the hook for a server resource pack.
`menus.replace-commands` makes the plain lookup commands open the menu (`MenuService.redirect`, called by the adapters
after `canUse`). Tests: `features/MenusTest` with `FakePlatform.menus` (only for `PlatformType.BUKKIT`): `menus.click(player,
"Bans in force")`, `menus.title(player)`, `menus.lore(player, name)`. A new screen needs its texts in the three files and
the permission of the command it shows (`MenuContext.canUse`).

### Database

Configured in `data.yml`. Every table and index name carries `database.table-prefix` (`judicator_`): DAOs write
`{table}` in their SQL and `RelationalDBManager.sql()` puts the prefixed name in; `ensureColumn`/`ensureIndex` take the
name without it. `commons/db/LegacyTables` renames the 1.x tables at startup. SQLite (default in the bundled `data.yml`), PostgreSQL and MySQL (`commons/db/Dialect`; a `data.yml` without `type` still means PostgreSQL, the 1.x default). HikariCP pool in
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
