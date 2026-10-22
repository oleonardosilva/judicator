# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Judicator** is a moderation plugin (bans, mutes, kicks, warns) for Minecraft. It runs on the
[Velocity](https://velocitypowered.com/) proxy and on Spigot/Paper servers (1.18+), backed by PostgreSQL, MySQL or SQLite,
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

There are no test or lint tasks configured.

## Modules

| Module | Responsibility |
|---|---|
| `judicator-common` | Platform-independent core: `Judicator` (bootstrap of configs, caches, DB and services), commands, services, repositories/DAOs, caches, messages, resources (`*.yml`) and the `platform/` abstraction. Must not import Velocity or Bukkit APIs. |
| `judicator-velocity` | Velocity entry point (`JudicatorVelocity`, `@Plugin`), `VelocityPlatform`, command adapter (`SimpleCommand`), event listener, runtime `DependencyManager`. |
| `judicator-bukkit` | Spigot/Paper entry point (`JudicatorBukkit`, `plugin.yml`), `BukkitPlatform`, command adapter (`TabExecutor`), event listener. Shades and relocates Adventure and Configurate; JDBC drivers, HikariCP and Caffeine come from `libraries:` in `plugin.yml`. |

### Platform abstraction (`judicator-common/.../platform`)

`Platform` (players, scheduler, console commands, announcements, online mode), `JudicatorSender` and
`JudicatorPlayer`. Core code talks only to these; each platform module implements them.

- `runAsync` for I/O (database, HTTP). `runSync` for anything that touches the game: on Bukkit it is the main thread,
  on Velocity it is just another async task.
- Commands are `CoreCommand` subclasses (`execute(sender, CommandArgs)`, `suggest`, `canUse`). The adapters run
  `execute` asynchronously on both platforms. New commands must be added to `Judicator#createCommands` and, for
  Bukkit, to `judicator-bukkit/src/main/resources/plugin.yml`.
- Login/chat logic lives in `ConnectionHandler` and `ChatHandler`; each platform listener only translates its events.

### Punishment Flow

1. **Pre-login** — `ConnectionHandler.checkPreLogin` (name/IP) and, in online mode, `checkLogin` (UUID); a ban
   denies the connection.
2. **Login** — active mutes go into `PunishCache`; the last IP is stored by `AccessAddressService`.
3. **Chat/Command** — `ChatHandler` blocks muted players and handles the evidence prompt of staff members.
4. **Command** → `PunishFactory` → `BanHandler`/`MuteHandler` → `PunishService.save()` → DB → Discord webhook.

### Database

Configured in `data.yml`. PostgreSQL (default), MySQL and SQLite (`commons/db/Dialect`). HikariCP pool in
`RelationalDBManager`; DDL for the three dialects lives in each `*RelationalDAO.initialize()`. Dates go through
`commons/db/SqlTime` (a `LocalDateTime` is stored as that time in UTC and read back the same way; "now" is a
parameter, never `CURRENT_TIMESTAMP`, which follows the database time zone and does not work in SQLite).

`/judicator import` (console only) lives in `importer/`: one `ImportSource` per plugin (LiteBans, AdvancedBan,
LibertyBans, BanManager, vanilla ban lists), `ImportContext` for the shared conversions and `ImportService` for dry-run, de-duplication
and batch inserts. Sources are read-only; their connection settings are in `import.yml`.

### Localization

`messages_en.yml` / `messages_es.yml` / `messages_pt.yml` in `judicator-common/src/main/resources/`. Every new key
must exist in the three files.
