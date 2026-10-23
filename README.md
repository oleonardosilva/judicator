# Judicator

**Judicator** is a moderation plugin for Minecraft networks and servers. It runs on the Velocity proxy and on
Spigot/Paper servers, and offers a wide variety of commands for punishments, mutes, and player management, making the
moderation team's work on the server much easier.

Download it!
- SpigotMC: https://www.spigotmc.org/resources/1-8-8-1-21-8-judicator-advanced-punishment-system-for-all-server-networks.127337/
- Mondrith: https://modrinth.com/plugin/judicator

---

## 🧩 Supported Platforms

| Platform | JAR | Requirements |
|---|---|---|
| Velocity (proxy) | `Judicator-Velocity-<version>.jar` | Velocity 3.4+, Java 17+ |
| Spigot / Paper (server) | `Judicator-Bukkit-<version>.jar` | Minecraft 1.18+, Java 17+ |

Use the Velocity build when you run a network behind a proxy: punishments then apply to every backend server. Use the
Spigot/Paper build on a standalone server. On Spigot/Paper the database drivers, HikariCP and Caffeine are downloaded
by the server itself (`libraries` in `plugin.yml`); on Velocity they are downloaded by the plugin on the first start.

### Database

Set `type` in `data.yml` to `postgres` (default), `mysql` or `sqlite`. SQLite needs no database server: the data is
kept in `plugins/<Judicator folder>/judicator.db` (`file` in `data.yml`). Spigot/Paper already include the SQLite
driver; on Velocity it is downloaded on the first start with `type: 'sqlite'`. Use SQLite for a single server or a
small network; a network with several Spigot/Paper servers sharing punishments needs PostgreSQL or MySQL. Changing
`type` requires a restart (it is not applied by `/judicator reload`).

### Building

```bash
./gradlew build
```

The JARs are written to `judicator-velocity/build/libs/` and `judicator-bukkit/build/libs/`.

Tests: `./gradlew :judicator-common:test`.

---

## 📋 Available Commands

The following commands are used to apply, remove, or check punishments for players and IPs.

* Arguments in `{}` are optional.
* Arguments in `()` are required.
* Each command requires a specific permission to be executed.

---

### 🔨 Permanent Punishments

* **`/ban (player) (reason)`**
  Permanently bans a player from the server.
  **Permission:** `judicator.ban`

* **`/banip (player) (reason)`**
  Permanently bans the IP associated with the player.
  **Permission:** `judicator.ban.ip`

* **`/mute (player) (reason)`**
  Permanently mutes the player in chat.
  **Permission:** `judicator.mute`

* **`/muteip (player) (reason)`**
  Permanently mutes all players with the same IP.
  **Permission:** `judicator.mute.ip`

---

### ⏳ Temporary Punishments

* **`/tempban (player) (duration) {reason}`**
  Temporarily bans the player.
  **Permission:** `judicator.tempban`

* **`/tempbanip (player) (duration) {reason}`**
  Temporarily bans the player's IP.
  **Permission:** `judicator.tempban.ip`

* **`/tempmute (player) (duration) {reason}`**
  Temporarily mutes the player.
  **Permission:** `judicator.tempmute`

* **`/tempmuteip (player) (duration) {reason}`**
  Temporarily mutes all players with the same IP.
  **Permission:** `judicator.tempmute.ip`

---

### 🚫 Warnings and Kicks

* **`/warn (player) {reason}`**
  Issues a warning to the player.
  **Permission:** `judicator.warn`

* **`/tempwarn (player) (duration) {reason}`**
  Issues a temporary warning.
  **Permission:** `judicator.tempwarn`

* **`/kick (player) {reason}`**
  Kicks the player from the server. Set `log-kicks: true` in `config.yml` to keep kicks in the punishment history
  (`/phistory`, `/pview`, `/blame`, `/activity`); a logged kick never blocks a login nor counts for `/punish` steps.
  **Permission:** `judicator.kick`

* **Reason shortcuts:** `#<key>` anywhere in the reason of `/ban`, `/mute`, `/kick`, `/warn` and their variants is
  replaced by the text of `reasons.<key>` in `config.yml` (`/ban Steve #reason2 x-ray` → "Cheating x-ray"). Only the
  text changes; with `require-reason-permission: true` the reason's permission is needed. Tab completion suggests
  the keys.

---

### 🔍 Lookup and History

* **`/phistory (player)`**
  Shows the player's punishment history.
  **Permission:** `judicator.history`

* **`/pview (id)`**
  Views details of a specific punishment.
  **Permission:** `judicator.view`

* **`/whistory (player)`**
  Displays all warnings for a player.
  **Permission:** `judicator.history`

* **`/wview (id)`**
  Views details of a specific warn.
  **Permission:** `judicator.view`

* **`/banlist {page}`** and **`/mutelist {page}`**
  List the bans/mutes in force, newest first. Hover an entry for details, click it to open `/pview`, click
  « Previous / Next » to change page.
  **Permission:** `judicator.banlist`, `judicator.mutelist`

---

### ✅ Punishment Removal

* **`/revoke (id)`**
  Removes any punishment by ID.
  **Permission:** `judicator.admin`

* **`/unwarn (id)`**
  Removes warn.
  **Permission:** `judicator.unwarn`

* **`/unban (player) {-s} {reason}`**
  Revokes every active ban of the player.
  **Permission:** `judicator.unban`

* **`/unmute (player) {-s} {reason}`**
  Revokes every active mute of the player.
  **Permission:** `judicator.unmute`

* **`/blame (staff) {period} {page}`**
  Lists the punishments applied by a staff member, newest first (10 per page). With a period (`7:d`, `24:h`), only
  the ones applied within it. Alias: `/staffhistory`.
  **Permission:** `judicator.blame`

* **`/activity (period) {page}`**
  How many punishments and warns each staff member applied in the period (`/activity 24:h`), busiest first. Click a
  staff member to open their `/blame` for the same period. Alias: `/staffactivity`.
  **Permission:** `judicator.activity`

---

### 👥 Alt Accounts

* **`/alts (player)`**
  Lists the accounts that have used the same IP as the player, with their status (banned, muted, clean).
  **Permission:** `judicator.alts`

* Staff with **`judicator.alts.notify`** are warned when a player joins sharing an IP with a banned or muted
  account (`alts.notify-on-join` in `config.yml`).

### 🌐 Shared Networks and Accounts per IP

* **`exempt-addresses`** in `config.yml`: IP addresses or CIDR ranges shared by many players (a school network,
  `127.0.0.1` behind a proxy without IP forwarding). `/banip`, `/muteip` and IP reasons of `/punish` are refused on
  them — punish the account instead; they do not link accounts in `/alts` nor trigger the alt warning, and they are
  never limited by `ip-limits`. New installs list `127.0.0.1` and `::1`.
* **`ip-limits`** (both `0` = off):
  * `max-online`: most accounts online at the same time from one IP;
  * `max-recent-accounts` + `recent-period`: most different accounts that may join from one IP within the period
    (an account already seen in the period can always come back). Counted in memory: a restart starts over.

---

### 🤫 Silent Punishments and Staff Notifications

* Add **`-s`** right after the player (or after the duration) to punish without the public announcement:
  `/tempban Steve -s 7:d cheating`. **Permission:** `judicator.silent`
* Staff with **`judicator.notify`** receive a notification for every punishment; silent ones are only shown to
  **`judicator.notify.silent`** (`staff-notifications` in `config.yml`).

---

### 🛡️ Staff Hierarchy

* Give each rank a level with **`judicator.level.<N>`** (for example `judicator.level.10` for helpers and
  `judicator.level.50` for admins). Staff can only punish players with a **lower** level; players without a level
  can always be punished. Levels are stored on join, so they also apply to offline targets.
* Levels must be given on purpose: on Spigot/Paper being op does **not** grant any `judicator.level.<N>`.
* IP punishments (`/banip`, `/muteip`, IP reasons of `/punish`...) are refused when any account that used that IP is
  above the staff member, is in `immune.yml`, or is the staff member themselves.
* A staff member's level is only known once they have joined after the update; until then they can be punished
  while offline like any player without a level.
* Configure it under `hierarchy` in `config.yml`. `immune.yml` still works as before.

### ⏱️ Time Limits and Cooldowns

* **`time-limits`**: the longest ban/mute each staff group may apply. Give a group's staff the permission
  **`judicator.timelimit.<group>`** (explicit, like the levels):

  ```yaml
  time-limits:
    helper:          # judicator.timelimit.helper
      ban: "1:d"     # tempbans up to 1 day, no permanent ban
      mute: "7:d"
  ```

  A limited staff member cannot apply a permanent ban/mute, nor a `/punish` reason whose step goes beyond the limit.
  With several groups the longest limit wins; a group without `ban` (or `mute`) does not limit it. Console and
  `judicator.admin` have no limit. If your permission plugin makes higher ranks inherit the helper group, they inherit
  `judicator.timelimit.helper` too: set it to `false` on those ranks (or give them their own, longer group).
* **`cooldowns`**: seconds before the same player can get another punishment of the same kind (`ban`, `mute`,
  `warn`, `kick`; `0` = off), so two staff members do not punish the same player twice for the same thing. Checked
  again when a staff member confirms in chat. Console and `judicator.admin` are not blocked, but what they apply
  starts the cooldown.

---

### 📈 Progressive Punishments

Reasons used by `/punish` can escalate with `steps`: the first time uses the first step, the second time the
second step, and so on (the last step repeats). Reasons without `steps` keep working as before.

```yaml
reasons:
  reason1:
    reason: "Player Offense"
    permission: 'punish.helper'
    ip: false
    steps:
      - type: TEMPMUTE
        duration: "7:d"
      - type: TEMPMUTE
        duration: "30:d"
      - type: MUTE
```

Set `require-reason-permission: true` in `config.yml` to require the `permission` of each reason to use it (staff
with `judicator.admin` can always use every reason). It is `false` by default, so existing setups keep working.

---

### 📥 Importing from Other Plugins

Run from the **server console**: `/judicator import <litebans|advancedban|libertybans|banmanager|vanilla|judicator>` shows what would be imported
without writing anything; add `confirm` to write it. Back up your database first. Running it again is safe:
punishments already in Judicator (same target — IP, UUID or name — type and start time) are skipped.

| Source | What is read | Notes |
|---|---|---|
| `litebans` | bans, mutes and warnings from LiteBans' database | names come from LiteBans' history table; kicks, wildcard IP bans and punishments limited to one server (`server_scope`) are skipped |
| `advancedban` | `PunishmentHistory` and `Punishments` from AdvancedBan's database | punishments no longer in `Punishments` are imported as revoked; kicks and notes are skipped |
| `libertybans` | every punishment from LibertyBans' database (`simple_history` view) | names from its `names` table; kicks and punishments limited to some servers (scopes) are skipped |
| `banmanager` | player/IP bans and mutes (active and `*_records`) and warnings from BanManager's database | kicks, notes, name bans and IP range bans are not read; soft mutes become normal mutes; an online-only mute ends after the time it had left |
| `vanilla` | `banned-players.json` and `banned-ips.json` (also used by Essentials) | bans only |
| `judicator` | another Judicator database: punishments (with revocations, evidence and logged kicks), warns and the IP history used by `/alts` | to move between databases, e.g. SQLite → MySQL: point `data.yml` to the new one, restart, import the old one; staff levels are recorded again on join |

Configure the source database in `import.yml` (`jdbc-url`, `user`, `password`). MySQL/MariaDB (`jdbc:mysql://`),
PostgreSQL and SQLite work directly. For H2 (LiteBans' and BanManager's default) or HSQLDB (AdvancedBan's and
LibertyBans' default), put the driver
jar in `plugins/<Judicator folder>/drivers` and set `driver-jar` and `driver-class` (see the comments in
`import.yml`); the source plugin must not be running while its embedded database is read.

* Punishments removed in the source plugin are imported as revoked; removed warns as finished.
* IP punishments with no player are stored by address only, never under a player's name.
* LiteBans, LibertyBans and BanManager keep a name history: a punishment whose player's last known name now belongs
  to someone else is stored under `#<uuid>` (enforced by UUID) so the new owner of the name is not punished.
  AdvancedBan has no name history, so its punishments keep the name they were applied to.
* Player punishments whose player is missing from the source's name table are skipped (shown in the report).
* No Mojang lookups are made: on an online-mode server, AdvancedBan records without a UUID are enforced by name.
* Online players pick up imported mutes when they rejoin.

---

### ⬆️ Upgrading from 1.0.0

* New message keys are read from the plugin's bundled files when your `messages_*.yml` doesn't have them yet, so you
  don't need to delete your translations. Copy the new keys into your file if you want to change their text.
* New `config.yml` sections (`hierarchy`, `staff-notifications`, `alts`, `require-reason-permission`, `time-limits`,
  `cooldowns`, `exempt-addresses`, `ip-limits`, `log-kicks`) are optional: without them the plugin behaves as before
  (no limits, no cooldowns, no exempt addresses, kicks not stored). New installs get cooldowns of 30 seconds for
  bans, mutes and warns and `127.0.0.1`/`::1` as exempt addresses.
* Velocity: every library downloaded into `plugins/judicator/libs` is checked against a SHA-256 fixed in the plugin
  and only Maven Central is used. A jar that does not match is downloaded again; if it still does not match the
  plugin does not start. Other jars in that folder are no longer loaded.

---

### 📝 Reports - Coming Soon

* **`/report (player) {reason}`**
  Sends a report to the moderation team.

* **`/reports`**
  Shows pending reports.
  **Permission:** `judicator.reports`

---

### ⚙️ Other

* **`/punish (player) {reason}`**
  Quick punishment based on preset configuration.
  **Permission:** `judicator.punish`

---

### 🔐 Special Permission

* **`judicator.admin`**
  Full access to all administrative functions of the plugin.
