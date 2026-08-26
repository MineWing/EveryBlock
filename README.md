# EveryBlock

A shared Paper 1.21.11 challenge plugin for collecting every obtainable block.

## What it does

- Builds the challenge list automatically from placeable block items.
- Excludes admin, technical, and unobtainable blocks by default.
- Detects blocks in participant inventories, regardless of whether they were mined,
  crafted, traded, smelted, or looted.
- Tracks the first finder and discovery time in SQLite.
- Provides a category hub and paginated category collections with All, Missing,
  and Found filters plus global search.
- Includes rarity-based effects, recent discovery history, contribution rankings,
  PlaceholderAPI values, milestones, and a staged completion finale.
- Includes a separate Every Item collection and `/items` GUI. It is disabled by
  default and can be enabled with `items.enabled: true`.
- Uses configurable Rivet/OneMillionCrops-style tagged message actions.

## Commands

`/blocks`, `/blocks status`, `/blocks search <query>`, `/blocks top`, and `/blocks help`

Admins can use `/blocks add <block>`, `/blocks remove <block>`,
`/blocks reset confirm`, and `/blocks reload`.

When the optional item challenge is enabled, use `/items`, `/items status`, and
`/items reset confirm`.

## PlaceholderAPI

`%everyblock_collected%`, `%everyblock_total%`, `%everyblock_remaining%`,
`%everyblock_percent%`, `%everyblock_contribution%`, `%everyblock_latest%`, and
`%everyblock_latest_player%`.

## Build

```sh
mvn clean package
```

The configured Maven build also copies the shaded jar into the local Testserver
plugins directory.
