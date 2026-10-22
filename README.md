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
