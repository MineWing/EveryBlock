<h1 align="center">EveryBlock</h1>

<p align="center">
  <strong>One server. Every obtainable block. A collection everyone builds together.</strong>
</p>

<p align="center">
  <img alt="Paper 1.21.11" src="https://img.shields.io/badge/Paper-1.21.11-2E8B57?style=for-the-badge">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-E76F00?style=for-the-badge">
  <img alt="SQLite" src="https://img.shields.io/badge/Storage-SQLite-2563EB?style=for-the-badge">
  <a href="https://github.com/MineWing"><img alt="MineWing" src="https://img.shields.io/badge/MineWing-Plugin_Suite-2563EB?style=for-the-badge&logo=github"></a>
</p>

EveryBlock turns normal survival play into a shared collection challenge. The plugin automatically builds a catalogue of obtainable block items, watches participant inventories, remembers the first player to discover each block, and presents the entire journey through polished in-game menus.

## The challenge at a glance

| | |
|---|---|
| **Automatic catalogue** | Builds the challenge from placeable block items and excludes technical, administrative, and unobtainable materials |
| **Source-independent discoveries** | Counts legitimate blocks whether they were mined, crafted, traded, smelted, farmed, or looted |
| **Collection GUI** | Category hub, paginated collections, global search, and All/Missing/Found filters |
| **Shared history** | Stores the first finder, discovery time, recent discoveries, and contribution rankings in SQLite |
| **Satisfying progression** | Rarity-based effects, configurable milestones, and a staged server-wide completion finale |
| **Every Item mode** | Optional companion collection for every obtainable inventory item, disabled by default |
| **PlaceholderAPI** | Built-in expansion for totals, percentage, contributions, and latest discoveries |

## How discoveries work

1. EveryBlock scans participating survival inventories at a configurable interval.
2. New catalogue items are recorded once for the whole server.
3. The first finder and timestamp are written to `progress.db`.
4. Configurable messages, sounds, particles, titles, and celebrations announce the discovery.
5. `/blocks` immediately reflects the new team progress.

The default participant mode is `EVERYONE`. Servers that want a closed team can switch to `ALLOWLIST` and add player UUIDs without changing the collection itself.

## Commands

| Command | Purpose | Permission |
|---|---|---|
| `/blocks` | Open the block collection | `everyblock.use` |
| `/blocks status` | Show overall team progress | `everyblock.use` |
| `/blocks search <query>` | Search the collection | `everyblock.use` |
| `/blocks top` | Show leading contributors | `everyblock.use` |
| `/blocks add <block>` | Mark a block as collected | `everyblock.admin` |
| `/blocks remove <block>` | Remove a collected block | `everyblock.admin` |
| `/blocks reset confirm` | Reset the block challenge | `everyblock.admin` |
| `/blocks reload` | Reload configuration and messages | `everyblock.admin` |
| `/items` | Open the optional item collection | `everyblock.items` |
| `/items status` | Show item challenge progress | `everyblock.items` |
| `/items reset confirm` | Reset item progress | `everyblock.admin` |

Aliases include `/everyblock`, `/blockchallenge`, `/blockdex`, `/everyitem`, `/itemchallenge`, and `/itemdex`.

## PlaceholderAPI

PlaceholderAPI is optional. When installed, EveryBlock registers its expansion automatically:

```text
%everyblock_collected%
%everyblock_total%
%everyblock_remaining%
%everyblock_percent%
%everyblock_contribution%
%everyblock_latest%
%everyblock_latest_player%
```

No separate eCloud download is required.

## Install

### Requirements

- Paper 1.21.11
- Java 21
- PlaceholderAPI 2.12.3 or newer (optional)

1. Build the project with `mvn package`.
2. Copy the shaded `EveryBlock-*.jar` into the server's `plugins/` directory.
3. Restart Paper.
4. Open the collection with `/blocks`.

EveryBlock creates `config.yml`, `messages.yml`, and `progress.db` inside `plugins/EveryBlock/`.

## Configuration

The defaults work without setup. The most common customizations are:

```yaml
participants:
  mode: EVERYONE       # Or ALLOWLIST
  allowlist: []

counting:
  scan-interval-ticks: 10
  count-creative-mode: false

items:
  enabled: false       # Enable the companion Every Item challenge
```

`config.yml` controls participants, catalogue exclusions, counting, GUI presentation, storage, and milestones. `messages.yml` contains ordered action lists for chat, broadcasts, sounds, particles, titles, boss bars, and fireworks, plus GUI lore as YAML string lists.

Changing the database filename requires a full restart. Other supported settings can be refreshed with `/blocks reload`.

## Build from source

```bash
git clone https://github.com/MineWing/EveryBlock.git
cd EveryBlock
mvn package
```

The Maven package runs the test suite and produces the shaded plugin JAR with its SQLite driver included.

---

<p align="center">
  Built by <a href="https://github.com/MineWing">MineWing</a> · See also <a href="https://github.com/MineWing/Rivet">Rivet</a> and <a href="https://github.com/MineWing/one-million-crops">OneMillionCrops</a>
</p>
