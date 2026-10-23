# Judicator

**Judicator** is a moderation plugin for Minecraft networks and servers. It runs on the Velocity and BungeeCord proxies
and on Spigot/Paper servers, and offers a wide variety of commands for punishments, mutes, and player management, making the
moderation team's work on the server much easier.

Download it!
- SpigotMC: https://www.spigotmc.org/resources/1-8-8-1-21-8-judicator-advanced-punishment-system-for-all-server-networks.127337/
- Mondrith: https://modrinth.com/plugin/judicator

---

## 🧩 Supported Platforms

| Platform | JAR | Requirements |
|---|---|---|
| Velocity (proxy) | `Judicator-Velocity-<version>.jar` | Velocity 3.4+, Java 17+ |
| BungeeCord (proxy) | `Judicator-BungeeCord-<version>.jar` | BungeeCord 1.21+, Java 17+ |
| Spigot / Paper (server) | `Judicator-Bukkit-<version>.jar` | Minecraft 1.18+, Java 17+ |

Use the Velocity or BungeeCord build when you run a network behind a proxy: punishments then apply to every backend
server. Use the Spigot/Paper build on a standalone server. Several servers sharing one database: see
[Networks and Multiple Servers](#networks-and-multiple-servers). On Spigot/Paper and BungeeCord the database drivers,
HikariCP and Caffeine are downloaded by the server itself (`libraries` in `plugin.yml`/`bungee.yml`); on Velocity they
are downloaded by the plugin on the first start.

Running the Spigot/Paper build on backends behind Velocity or BungeeCord with UUID forwarding? Those servers report
`online-mode=false`, so set **`trust-uuids: true`** in `config.yml` to check bans by UUID too (a banned player who
changes name and IP is still refused). Never set it on a real offline-mode server, where UUIDs come from the name.

Players are identified by their **account (UUID)** whenever the server trusts UUIDs (online mode, or `trust-uuids`):
punishments, warns, notes, staff levels, alts and staff history follow the account through name changes, and the next
owner of a banned player's old name joins freely. A name typed in a command means the player online with it, else the
account that joined last with it (the `players` table), else the one the Mojang API gives. A punishment applied to a
name nobody had seen is stored without a UUID and holds that name until an account joins with it; from then on it
follows that account. On a real offline-mode server the name is the player, as before.

### Database

Set `type` in `data.yml` to `postgres` (default), `mysql` or `sqlite`. SQLite needs no database server: the data is
kept in `plugins/<Judicator folder>/judicator.db` (`file` in `data.yml`). Spigot/Paper already include the SQLite
driver; BungeeCord downloads it on the first start with the other libraries; on Velocity it is downloaded on the first
start with `type: 'sqlite'`. Use SQLite for a single server or a
small network; a network with several Spigot/Paper servers sharing punishments needs PostgreSQL or MySQL. Changing
`type` requires a restart (it is not applied by `/judicator reload`).

Every table and index starts with **`table-prefix`** (`judicator_` by default: `judicator_punishments`…), so they
never clash with other plugins in the same database. Use the same prefix on every server sharing the database; it is
read at startup only.

For PostgreSQL/MySQL, an empty `port` means the default of the type (5432/3306). `properties` passes options to the
JDBC driver (`useSSL: 'false'` for MySQL, `sslmode: 'require'` for PostgreSQL...) and `pool` sizes the connection
pool.

### Networks and Multiple Servers

Every server running Judicator on the same MySQL/PostgreSQL database reads the same punishments. To make a change made
on one server reach the players of the others **right away** (not only at their next login), turn on `sync` in the
`config.yml` of **every** server and restart them:

```yaml
sync:
  enabled: true
  poll-interval: 1 # seconds (1 to 30)
```

Each server writes a short notice (the punishment id, nothing else) to the `sync_messages` table and reads the others'
notices every `poll-interval` seconds; notices are deleted after about a minute. A ban applied on one server kicks the
player wherever they are, a mute blocks their chat, and `/unban`, `/unmute`, `/revoke`, `/staffrollback`, `/extend`,
`/changereason` and `/judicator clear` lift or change it everywhere. Staff of every server are notified and the
punishment cooldown counts on every server. Notices of revocations, edits and rollbacks to staff stay on the server
where the command was typed; the Discord webhook is sent once, by the server that applied the punishment.

| Network | Install | `config.yml` |
|---|---|---|
| A proxy only (Velocity or BungeeCord) | the proxy's build | nothing to change |
| Several Spigot/Paper servers, no proxy (or a proxy without Judicator) | Spigot/Paper build on each | `sync.enabled: true` on each |
| A proxy **and** the backends (signed chat of 1.19.1+ clients, menus on the backends) | the proxy's build and the Spigot/Paper build | proxy: `sync.enabled: true`, `chat-on-backends: true`; each backend: `sync.enabled: true`, `proxy-mode: true`, `trust-uuids: true` |

**`proxy-mode`** (Spigot/Paper behind a Velocity or BungeeCord running Judicator): the proxy does everything players see — refuses
banned players, kicks, mute alerts, announcements, staff notifications — for every punishment, wherever it was
applied, and keeps IP addresses, account limits, alt and note warnings, online-only mute time and data retention. The
backend keeps the mute of each player online and blocks muted chat and commands (and soft mutes keep the server's chat
format); it still refuses a banned player, in case someone reaches it without the proxy. Its console can use every
command (but `/kick` there only moves the player off that backend: kick from the proxy). With `chat-on-backends: true`,
the proxy stops blocking chat and backend commands itself, so 1.19.1+ players are not disconnected for a denied signed
message; commands of the proxy itself (a proxy-wide `/g`, for instance) are still blocked there.

Limits: SQLite can only be shared by servers on the same machine. A server that stops reading for more than a minute
(a long freeze) can miss notices; the player gets the right state at their next login.

#### Punishments for some servers (scopes)

Bans and mutes can apply to one server or a group of servers instead of the whole network: add
**`-scope=(server or group)`** to `/ban`, `/tempban`, `/mute`, `/tempmute`, `/softmute`, `/tempsoftmute`, their IP
variants or `/punish` (in any order with `-s`/`-o`; tab completion lists the groups and servers).

```yaml
scopes:
  server-name: '' # Spigot/Paper: this server's name in the proxy's config (empty = only punishments for every server here)
  groups:
    survival: [survival1, survival2]
```

* **Proxy** (Velocity, BungeeCord): a ban for some servers does not refuse the network. The player cannot connect to the servers it covers
  (also when the proxy picks one by itself, at login or after a server goes down) and, if already on one, is sent to
  the first server of the `try` list (velocity.toml) or `priorities` (BungeeCord's config.yml) it does not cover; with none left, they are disconnected. A mute for
  some servers only blocks their chat there. Announcements only show where the punishment applies.
* **Spigot/Paper** without a proxy: set `server-name`; a ban for this server (or a group with it) refuses the login, one
  for another server is ignored here. In `proxy-mode`, `server-name` must be the name the proxy gives the backend, so
  its mute check knows which mutes apply there.
* `/unban` and `/unmute` lift the ones for some servers too; `/revoke (id)` lifts one. `alts.deny-if-banned` only
  counts bans for every server. Kicks have no scope.
* A group removed from `scopes.groups` makes its punishments apply nowhere: the console says so at startup and on
  `/judicator reload`.
* A ban applied while the player is on their way to a server it covers moves them on as soon as they arrive.

### Building

```bash
./gradlew build
```

The JARs are written to `judicator-velocity/build/libs/`, `judicator-bungee/build/libs/` and
`judicator-bukkit/build/libs/`.

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

* **Online time only (`-o`)**: on `/tempmute` and `/tempsoftmute`, the time only runs while the player is online
  (`/tempmute Steve 1:h -o spam`). When they leave — or the server stops — the time left is kept and the mute is
  paused (still in force, shown as "… of online time left"); it runs again at their next login. Applied to an offline
  player, it starts paused. Not available for IP mutes.

### 🤐 Soft Mutes

* **`/softmute (player) {reason}`** and **`/tempsoftmute (player) (duration) {-o} {reason}`**
  The player still sees their own messages, nobody else does, and nothing tells them they are muted (no alert, no
  public announcement — staff are notified as `SOFTMUTE`/`TEMPSOFTMUTE`). Commands in `commands-locked-when-silenced`
  are blocked without a message. `/unmute` lifts it. On Spigot/Paper the message keeps the server's chat format; on
  a proxy it is replaced, for the sender only, by `soft-mute-format` in `config.yml`.
  **Permission:** `judicator.softmute`, `judicator.tempsoftmute`
* **Velocity and 1.19.1+ clients:** Velocity disconnects a 1.19.1+ player whose signed chat message is denied, which
  affects every chat block done on the proxy (normal and soft mutes). Install a plugin such as SignedVelocity on the
  proxy and the backends, or run Judicator on the backends in `proxy-mode` with `chat-on-backends: true` on the proxy
  (see [Networks and Multiple Servers](#networks-and-multiple-servers)), if your players use signed chat.
* **BungeeCord and 1.19.1+ clients:** BungeeCord does not disconnect the player itself, but a backend may when a
  signed message it expected never arrives. The same `proxy-mode` + `chat-on-backends: true` setup avoids it.

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

* **`/playerinfo (player)`** (alias `/pinfo`)
  One screen with the ban and mute in force, history counts, warns, staff notes, alt accounts and last login, each
  clickable to the full command. The last IP is only shown to who has `judicator.history`. (Not `/info`, which belongs
  to EssentialsX.)
  **Permission:** `judicator.info`

---

### 📝 Staff Notes

* **`/note (player) (text)`** — writes a note about a player (up to 255 characters) for the rest of the staff; the
  player never sees it. **Permission:** `judicator.note`
* **`/notes (player) {page}`** — lists the notes, newest first; hover for the whole text, click to prepare
  `/delnote`. **Permission:** `judicator.notes`
* **`/delnote (id)`** — deletes a note. **Permission:** `judicator.delnote`
* Staff with **`judicator.notes.notify`** are told when a player with notes joins (`notes.notify-on-join`).

---

### ✏️ Fixing a Punishment

* **`/extend (id) (duration)`**
  Adds time to a temporary ban or mute in force (to what is left, for a paused online-only mute). The result must fit
  your `time-limits`. **Permission:** `judicator.extend`
* **`/changereason (id) (reason)`**
  Replaces the reason of a punishment in force (`#key` shortcuts work). **Permission:** `judicator.changereason`
* Only the console, `judicator.admin`, the author of the punishment or a staff member above the author (hierarchy)
  may change it, and only if they could punish the player.

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

* **`/staffrollback (staff) (period) {confirm}`**
  Undoes what a staff member applied in the period (a stolen account, an abusive staff member): shows what would
  change and, with `confirm` (clickable), revokes the bans and mutes still in force — the records stay, revoked with
  "Rollback of …" — and deletes the warns still active (warns cannot be revoked; each one is written to the console log
  first). Punishments the console applied (e.g. the warn tree) are not included. You must be above the staff member in
  the hierarchy. **Permission:** `judicator.staffrollback`

---

### 👥 Alt Accounts

* **`/alts (player)`**
  Lists the accounts that have used the same IP as the player, with their status (banned, muted, clean).
  **Permission:** `judicator.alts`

* Staff with **`judicator.alts.notify`** are warned when a player joins sharing an IP with a banned or muted
  account (`alts.notify-on-join` in `config.yml`).
* **`alts.deny-if-banned: true`** (off by default) refuses an account while another account that used the same IP has
  a ban in force (ban evasion). The screen shows the ban's id, not the other account; staff with
  `judicator.alts.notify` are told. Addresses in `exempt-addresses` and players exempt from `alts` in `immune.yml`
  (siblings on the same home network, for instance) are let in.

### 🌐 Shared Networks and Accounts per IP

* **`exempt-addresses`** in `config.yml` (also at login: an older or imported IP ban on such an address only keeps out
  the account it was recorded on): IP addresses or CIDR ranges shared by many players (a school network,
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
* `announce` in `config.yml` turns the public announcement off for every kind (`false`) or only for some:

  ```yaml
  announce:
    ban: true
    mute: true
    kick: false
    warn: false
  ```

---

### 📢 Discord Webhook

Set `enabled: true` and `webhook-url` in `discord.yml` and every ban, mute, kick and warn is posted to that channel as
an embed (once, by the server that applied it). An event can post to another channel with its own `webhook-url`, or
be turned off with `enabled: false`. Title, description and colour are set per event; the author's name and picture,
the player picture, the footer and the field names (to translate them) are shared by all.

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
* Configure it under `hierarchy` in `config.yml`.

### 🚷 Exemptions (`immune.yml`)

* `players:` — a UUID (preferred: it survives a name change, and a name proves nothing in offline mode) or a name,
  and what it is exempt from: `all`, `ban`, `mute`, `kick`, `warn`, `alts` (may join despite `alts.deny-if-banned`),
  `report` (cannot be reported; `all` does not include it).

  ```yaml
  players:
    "069a79f4-44e9-4726-a5be-fca90e38aaf5": [all]
    "Builder": [mute, warn]
  ```
* Exemptions also apply to the console. An IP punishment is refused when any account of the address is exempt from it.

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
| `litebans` | bans, mutes and warnings from LiteBans' database | names come from LiteBans' history table; bans and mutes for one server keep it as their scope (`server_scope`; a name that is also a group of `scopes.groups` becomes that group); kicks, wildcard IP bans and warnings for one server are skipped |
| `advancedban` | `PunishmentHistory` and `Punishments` from AdvancedBan's database | punishments no longer in `Punishments` are imported as revoked; kicks and notes are skipped |
| `libertybans` | every punishment from LibertyBans' database (`simple_history` view) | names from its `names` table; bans and mutes for a server or a category keep their scope (a category becomes the group of the same name: add it to `scopes.groups`, the import tells which); kicks and warns for some servers are skipped |
| `banmanager` | player/IP bans and mutes (active and `*_records`) and warnings from BanManager's database | kicks, notes, name bans and IP range bans are not read; soft mutes become normal mutes; an online-only mute ends after the time it had left |
| `vanilla` | `banned-players.json` and `banned-ips.json` (also used by Essentials) | bans only |
| `judicator` | another Judicator database: punishments (with revocations, evidence, logged kicks, soft and online-only mutes), warns, staff notes and the IP history used by `/alts` | to move between databases, e.g. SQLite → MySQL: point `data.yml` to the new one, restart, import the old one; staff levels are recorded again on join |

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

### 🗑️ Data Retention and Deletion

Player names, IP addresses and histories are personal data. Judicator keeps them only as long as you tell it to:

* **`retention`** in `config.yml` — days to keep each kind of old record (`0` = forever, the default). They are
  deleted at startup and every 12 hours:

  ```yaml
  retention:
    kicks: 30                 # logged kicks
    finished-punishments: 365 # bans/mutes that ended (or were revoked) that long ago; nothing in force is deleted
    finished-warns: 180       # temporary warns that ended
    addresses: 90             # IPs no account used for that long (/alts and offline IP bans stop finding them)
    notes: 0                  # staff notes
    reports: 0                # player reports closed that long ago
  ```
* **`/judicator clear (player) {confirm}`** (console only) — for a deletion request: shows what is stored about the
  player and, with `confirm`, deletes their punishments (those in force are lifted), warns, staff notes, IP history,
  staff level, the reports they made or got and their entry in the `players` table. Records under an older name of the
  same account (same UUID) are included.

---

### 🌍 Messages per Player

Set **`per-player-language: true`** in `config.yml` and each player gets the messages in their game language when it
is English, Spanish or Portuguese (the three `messages_*.yml` files are created); other languages, the console,
Discord and the login screens (the game has not told its language yet) use `language`. Announcements and staff
notifications are built for each recipient. The evidence prompt accepts the word in the staff member's language and in
the server language.

---

### ⬆️ Upgrading to 2.0.0

2.0.0 changes the database: **stop every server sharing the database, update them all, then start them.** A 1.x server
does not find the tables once a 2.0 server has renamed them.

* **Table prefix**: on the first start the 1.x tables (`punishments`, `warns`, `notes`, `player_levels`,
  `sync_messages`, `access_addresses`) are renamed to `judicator_*` with their rows. A table of another plugin with one
  of those names (without Judicator's columns) is left alone. If the renaming cannot be done (no `ALTER` permission, for
  instance) the plugin does not start, rather than starting with empty tables. Tools that read Judicator's tables
  (web ban lists) must use the new names.
* **IP history** (`/alts`): copied from `access_addresses` (one row per address) into `judicator_player_addresses`
  (one row per account and address); the old table stays as `judicator_access_addresses_old` and can be dropped by
  hand.
* **Accounts**: new `judicator_players` table, filled on the first start with the accounts the stored records know.
  New columns `punisher_uuid` (`punishments`, `warns`), `author_uuid` (`notes`), `scope` (`punishments`) and the
  `judicator_reports` table.
* **Behaviour** with trusted UUIDs: the name check before login is gone (bans are checked on the account after
  login), so whoever takes a banned player's old name can join; the account limits of `ip-limits` are checked after
  login, so a banned player sees their ban screen. `/alts` shows names as the players typed them (not in lower case).
* **Commands and permissions**: `/report` (everyone, unless `reports.require-permission: true`; `judicator.report`,
  `judicator.report.offline`) and `/reports` (`judicator.reports`, `.manage`, `.tp`, `.notify`); `-scope=` on the ban
  and mute commands.
* New optional `config.yml` sections `scopes` and `reports`, `retention.reports`, `data.yml` → `table-prefix`,
  `import.yml` → `judicator.table-prefix`, `discord.yml` → `events.report` (off) and new labels.
* API: `PunishmentView` has a new `scope` component (its constructor changed), `PunishmentRequest.Builder.scope(...)`
  and `ActionStatus.SCOPE_UNKNOWN` are new, and name lookups now resolve the account first.

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
* Database: on the first start the plugin creates the `notes` table and adds the columns `soft`, `online_only` and
  `paused_remaining` to `punishments` (existing rows become normal mutes/bans). Going back to an older version keeps
  working, but a paused online-only mute would look permanent there.
* Also optional: `per-player-language`, `soft-mute-format`, `notes`, `alts.deny-if-banned`, `retention` and the
  `players:` section of `immune.yml`. Without them nothing changes (no retention, alts are only reported).
* `/tempmute` (and the other mute/ban commands) now read `-s` and `-o` in any order before or after the duration.
* The `warns:` tree no longer runs its commands for a name with characters other than letters, digits and `_`
  (possible in offline mode): removing them would name another account (`Adm.in` → `Admin`). The warns are stored;
  apply the punishment by hand.
* `/unban`, `/unmute` and `/revoke` free the cooldown of that player, so a mistaken punishment can be applied again
  right away.
* `/blame` no longer shows IP addresses in its hover (`blame.hover`).
* Optional and off by default: `sync`, `proxy-mode` and `chat-on-backends` (see
  [Networks and Multiple Servers](#networks-and-multiple-servers)). With `sync.enabled: true` the plugin creates the
  `sync_messages` table on the first start; they are read at startup only (`/judicator reload` warns that a restart is
  needed).
* The bundled configuration files were reorganised and commented; your files keep working as they are. To see the
  new layout, rename yours, restart and copy your values into the new ones.
* `immune.yml`: the `users:` list is deprecated. It is still honoured (with a warning in the console); move each name
  to `players:` as `"<name or UUID>": [all]`.
* `discord.yml`: a top-level `webhook-url` serves every event (an event's own `webhook-url` still wins), and
  `username`, `avatar-url`, `thumbnail-url`, `footer` and `labels` are new. Kicks are now posted too (`events.kick`).
* `data.yml`: new `properties` and `pool`; new files leave `port` empty (the default of the type).
* `announce` in `config.yml` also takes one value per kind (`ban`, `mute`, `kick`, `warn`).

---

### 📣 Reports

* **`/report (player) (reason)`**
  Tells the staff about a player who is online (`judicator.report.offline`: also about one who is not). Open to every
  player unless `reports.require-permission: true` (then `judicator.report`). A player waits `reports.cooldown`
  seconds between reports (on every server sharing the database, and across restarts) and has at most one open report about the same player; players exempt from `report` in
  `immune.yml` cannot be reported.

* **`/reports [list open|assigned|closed|all] [page]`** and **`/reports info (id)`**
  The reports (open ones by default), newest first, and the details of one, with buttons for what you may do.
  **Permission:** `judicator.reports`

* **`/reports assign (id) {staff}`**, **`/reports unassign (id)`**, **`/reports close (id) {comment}`**
  Takes a report (or gives it to another staff member who may read reports), gives it back, closes it. The reporter
  hears when theirs is taken and closed. When two staff members change the same report at once, the second is told
  it changed meanwhile instead of overwriting the first.
  **Permission:** `judicator.reports.manage`

* **`/reports tp (id)`**
  Goes to the reported player. On a proxy (Velocity, BungeeCord): to the player's server and, when that backend runs Judicator in
  `proxy-mode` (and you have `judicator.reports.tp` there too), to the player; the backend confirms it, and you are
  told when you only got to the server.
  **Permission:** `judicator.reports.tp`

* Staff with **`judicator.reports.notify`** hear of each new report and, when they join, how many are open
  (`reports.notify-on-join`). With `sync`, reports reach the staff of every server and the reporter hears back on the
  server they are on. `/playerinfo` shows how many times a player was reported; `events.report` in `discord.yml`
  (off by default) posts them.

---

### 🏷️ PlaceholderAPI

On Spigot/Paper with [PlaceholderAPI](https://www.spigotmc.org/resources/6245/) installed, Judicator registers
`%judicator_<placeholder>%` (nothing to download with `/papi ecloud`). They work for players who are offline too, so
scoreboards, menus and leaderboards can show them.

| Placeholder | Value |
|---|---|
| `banned`, `muted` | whether a ban / mute is in force (PlaceholderAPI's `boolean` texts) |
| `ban_reason`, `ban_punisher`, `ban_id`, `ban_scope` | the ban in force; empty without one |
| `ban_expires`, `ban_remaining` | its end (`date-format`) and the time left, or `permanent` from the messages |
| `mute_reason`, `mute_punisher`, `mute_id`, `mute_scope`, `mute_expires`, `mute_remaining` | the same for the mute in force (soft mutes too; a paused online-only mute shows the online time left) |
| `warns`, `warns_total` | warns in force / all warns |
| `bans_total`, `mutes_total`, `kicks_total`, `punishments_total` | the player's history (logged kicks only) |

A placeholder never waits for the database: each player is looked up in the background the first time one of their
placeholders is asked for (until then the value is empty) and again every 30 seconds while they are used. What this
server changes shows up at once; what another server of the network changes, within those 30 seconds. Texts come from
the player's messages file (their language with `per-player-language`).

---

### ⚙️ Other

* **`/punish (player) {reason}`**
  Quick punishment based on preset configuration.
  **Permission:** `judicator.punish`

---

### 🔐 Special Permission

* **`judicator.admin`**
  Full access to all administrative functions of the plugin.

---

## 🔌 API for Plugin Developers

Other plugins (an anticheat, a report system, a Discord bot) can look up, apply and revoke punishments and listen to
what happens, on Velocity, BungeeCord and Spigot/Paper alike, through the `judicator-api` module (package
`com.openmc.judicator.api`, plain Java 17, no dependencies). Both Judicator JARs ship it; **do not shade it** into
your plugin.

**Dependency.** It is not on a Maven repository yet: build it with `./gradlew :judicator-api:jar` (sources and javadoc
JARs: `:judicator-api:sourcesJar`, `:judicator-api:javadocJar`) and compile against it:

```groovy
dependencies {
    compileOnly files('libs/judicator-api-1.0.0.jar')
}
```

Declare Judicator as a dependency: `depend: [Judicator]` (or `softdepend`) in `plugin.yml`, `depends: [Judicator]`
(or `softDepends`) in `bungee.yml`, or `@Plugin(..., dependencies = @Dependency(id = "judicator"))` on Velocity.

**Usage.** Get the API in your enable step (on Spigot/Paper it is also in the `ServicesManager`):

```java
JudicatorApi judicator = JudicatorProvider.get(); // IllegalStateException if Judicator is not loaded

// Every method is asynchronous: never join() on the server thread.
judicator.getActiveBan(player.getUniqueId()).thenAccept(ban -> ban.ifPresent(b ->
        getLogger().info(b.targetName() + " is banned until " + b.endsAt().map(Object::toString).orElse("forever"))));

// Applied as from the console: stored, synced, announced, staff notified, Discord, events.
judicator.tempban("Steve", Duration.ofDays(3), "Fly hack", "AntiCheat").thenAccept(result -> {
    if (result.status() == ActionStatus.TARGET_EXEMPT) getLogger().info("Steve is exempt in immune.yml");
});

// Events (bans, mutes, kicks, warns, revocations, edits), from commands and the API alike.
Subscription subscription = judicator.getEventBus().subscribe(PunishmentAppliedEvent.class, event ->
        getLogger().info(event.punishment().punisher() + " applied " + event.punishment().type()
                + " to " + event.punishment().targetName()));
// in onDisable: subscription.unsubscribe();
```

Rules worth knowing (details in the Javadoc of `JudicatorApi` and `JudicatorEvent`):

* An API call is treated like the console: no staff hierarchy, time limits, cooldown wait or evidence prompt. The
  `punisher` is any name you choose (up to 20 characters, no spaces). Exemptions still apply: players in `immune.yml`
  and addresses in `exempt-addresses` are refused with a status, not an exception.
* `PunishmentRequest` covers IP, silent, soft, online-only and evidence options; `WarnRequest` covers temporary warns.
* Events are posted after the change is stored, on the thread that made it (usually not the server thread), and only
  on the server where it was made, not on the servers that learn about it through `sync`. A listener that throws is
  logged and never stops a punishment. There is no cancellable "before" event.
