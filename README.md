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

### Building

```bash
./gradlew build
```

The JARs are written to `judicator-velocity/build/libs/` and `judicator-bukkit/build/libs/`.

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
  Kicks the player from the server.
  **Permission:** `judicator.kick`

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

---

### 👥 Alt Accounts

* **`/alts (player)`**
  Lists the accounts that have used the same IP as the player, with their status (banned, muted, clean).
  **Permission:** `judicator.alts`

* Staff with **`judicator.alts.notify`** are warned when a player joins sharing an IP with a banned or muted
  account (`alts.notify-on-join` in `config.yml`).

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

### ⬆️ Upgrading from 1.0.0

* New message keys are read from the plugin's bundled files when your `messages_*.yml` doesn't have them yet, so you
  don't need to delete your translations. Copy the new keys into your file if you want to change their text.
* New `config.yml` sections (`hierarchy`, `staff-notifications`, `alts`, `require-reason-permission`) are optional;
  their defaults are shown above.

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
