# Thirst

A Paper plugin that adds a thirst mechanic alongside vanilla hunger: a HUD
indicator above the hotbar, exertion-based drain with starvation damage at zero,
bottle-based drinking, per-chunk water quality (CLEAN / DIRTY), and a stacking
dirty-water illness (Salmonella).

## Requirements

| | |
|---|---|
| Server | Paper **26.1.2** (`api-version: '26.1'`) |
| Java | **25** (Minecraft 26.1+ requires Java 25 — Java 21 will not run it) |
| Build | Gradle wrapper (9.7.1), bundled |
| Optional | PlaceholderAPI — only for `hud.external-segment` |

> The original spec targeted "Paper 1.21.x / Java 21". Mojang switched to
> year-based versioning in 2026; the `26.1` drop the build pins is the successor
> to the 1.21 line and mandates Java 25. See `build.gradle.kts`.

## Building

```bash
./gradlew build
```

The plugin jar is written to `build/libs/Thirst-<version>.jar`. You need a JDK 25
available for the Gradle toolchain (Gradle will download one if it can, or point
it at a local install via `org.gradle.java.installations.paths`).

The SQLite JDBC driver is **not** shaded — `ThirstPluginLoader` fetches it into
Paper's shared library cache at load time.

## Development status

Built in phases; see the checklist below.

- [x] **Phase 1** — project skeleton, `paper-plugin.yml`, config load + validation
- [x] **Phase 2** — thirst core: `ThirstManager` (PDC-backed), exhaustion listeners,
  per-second drain + starvation task, `ThirstMath` pure functions + unit tests,
  minimal `/thirst get|set|reload`
- [x] **Phase 3** — HUD: action-bar text (bar or percent, blue → red at critical),
  `BOSSBAR` debug/fallback mode, pluggable `HudRenderer` strategy for the future
  resource-pack mode, `ThirstHudFormatter` pure functions + unit tests, optional
  PlaceholderAPI `hud.external-segment` (share the action bar with RealisticSeasons)
- [ ] Phase 4 — per-chunk water quality (SQLite schema, async load/save, cache, `/water`)
- [ ] Phase 5 — bottle-based drinking (fill at a source → Clean/Dirty Water bottle → drink)
- [ ] Phase 6 — Salmonella stacks, effects, decay, cure
- [ ] Phase 7 — `/thirst` commands, permissions, config validation, `/thirst reload`
- [ ] Phase 8 — HUD v2 (resource-pack droplet glyphs), optional

## Configuration

All gameplay numbers live in `config.yml` (copied to the data folder on first
run). Nothing is hardcoded. Invalid values log a warning and fall back to the
built-in default instead of breaking gameplay.

### Phase 2 design notes (for review)

- **Passive drain is folded into the exhaustion bucket.** Idle time adds
  `exhaustion-threshold / base-drain-interval-seconds` per second, so "1 point
  per N minutes idle" and the exertion multipliers share one code path.
- **Tick cadence: 1 second** (20 ticks). Starvation timing is counted in whole
  seconds from this loop.
- **Exhaustion is flushed to the PDC once per second**, not on every move event,
  to keep `PlayerMoveEvent` cheap. Level changes write through immediately.
- **`thirst_exhaustion` is stored as a PDC `double`** (the spec said "float" —
  double chosen for headroom; the size difference is 4 bytes/player).
- **Exempt players:** Creative + Spectator (like vanilla hunger). Starvation
  damage is additionally skipped on Peaceful difficulty. Difficulty-scaled
  health floors are *not* mirrored — `starvation-minimum-health` is the single
  knob.
- **Respawn refills thirst to full** and clears exhaustion + illness, matching
  vanilla hunger resetting on death.
- **Sprint speed bonus neutralised at/below the critical threshold**
  (`disable-sprint-when-critical`, default on). `CriticalSpeedModifier` adds a
  transient negative `MOVEMENT_SPEED` modifier while the player is *both*
  critical and sprinting, cancelling the ~30% boost
  (`critical-sprint-speed-multiplier`, default 0.75 ≈ walking pace). Removed the
  instant either condition stops — walking is never slowed. Cancelling
  `PlayerToggleSprintEvent` was tried first and does nothing: the client, not
  the server, decides whether to sprint (it checks its own food level > 6).
- **Folia is not supported** (uses the Bukkit scheduler).
- `/thirst` is a minimal debug command this phase; Phase 7 owns the real one.

### Phase 3 design notes (for review)

- **One shared HUD task**, not one task per player — it iterates online players
  each interval. Joiners are picked up next tick; leavers cleaned in `onQuit`.
  Same net effect as per-player tasks, less overhead.
- **HUD hidden in Creative / Spectator**, matching vanilla hiding the hunger bar
  there.
- **`hud-mode` is a pluggable strategy** (`HudRenderer`): `ACTIONBAR_TEXT` and
  `BOSSBAR` today; `ACTIONBAR_RESOURCEPACK` currently logs a one-time warning and
  falls back to `ACTIONBAR_TEXT`. Phase 8 adds the third implementation without
  touching the task.
- **Action-bar "yield" is cooperative, not automatic.** Paper exposes no way to
  detect another plugin's outgoing action-bar packets, so
  `actionbar-overwrite-others: false` cannot magically notice a third-party HUD.
  Three ways to coexist, best first:
  1. `hud.external-segment` — pull the other plugin's text in via PlaceholderAPI
     and render both on one line (see below). One plugin owns the slot.
  2. A plugin that depends on Thirst can call
     `((ThirstPlugin) Bukkit.getPluginManager().getPlugin("Thirst")).hud()
     .requestActionBarYield(player[, millis])` before its own send.
  3. If Thirst is the only action-bar user (common), set
     `actionbar-overwrite-others: true`.

### Sharing the action bar with RealisticSeasons (or any plugin)

`hud.external-segment` composes an extra bit of text — resolved from a
PlaceholderAPI placeholder — onto the same action-bar line as the thirst bar, so
the two plugins don't fight over the slot. Needs **PlaceholderAPI**; Thirst runs
fine without it (the section is just ignored). Action-bar HUD modes only.

For RealisticSeasons:

1. In **RealisticSeasons'** config, turn its own action bar off:
   `display → temperature → actionbar: false`.
2. In **Thirst's** `config.yml`:
   ```yaml
   hud:
     external-segment:
       enabled: true
       placeholder: "%rs_temperaturecolor%%rs_temperature%"
       place-before: true
       separator: " <white>|</white> "
   ```
3. `/thirst reload`.

Result: `22°C | [||||||----]`. Legacy colour codes (`&` or `§`) in the resolved
placeholder are honoured; the separator is MiniMessage. Any PlaceholderAPI
string works here, not just RealisticSeasons'.
- **HUD colours / bar characters** go through MiniMessage, so `color-normal` /
  `color-critical` accept hex and gradients, not just named colours.
- Boss-bar mode uses the **Adventure `BossBar`** API (per-player, shown/hidden
  via the player `Audience`), not the legacy `org.bukkit.boss` one.

### Open questions (defaults chosen, pending review)

1. **Sprint gating** — *resolved:* at/below the critical threshold the sprint
   speed bonus is neutralised (`disable-sprint-when-critical`,
   `critical-sprint-speed-multiplier`). A true "can't sprint" isn't reachable
   from the server — the client owns the sprint decision — so sprinting is made
   pointless instead: no faster than walking. Normal walking is unaffected.
2. **Hunger interaction** — thirst is fully independent of vanilla hunger for
   now (no salty-food drain multiplier).
3. **Salmonella cure** — currently item-only (milk bucket). Time-based recovery
   while hydrated on clean water is not implemented.
4. **Scale** — SQLite is assumed sufficient. Revisit if chunk counts reach the
   millions or multiple servers share the store.

### Spec changes (confirmed with maintainer)

1. **Drinking is bottle-based only.** Fill an empty glass bottle at a water
   source block (or water cauldron) to receive a *Bottle of Clean Water* or
   *Bottle of Dirty Water*, tagged with the quality of the chunk it was filled
   in. There is no "drink straight from the block" interaction.

2. **Water-quality command is `/water setchunk <clean|dirty>`** — it targets the
   single chunk the player is currently standing in (no coordinates, no radius).
   `/water get` still reports the current chunk. `/water setregion ...` is kept
   for bulk area painting. The spec's `/water set <clean|dirty> [radius]` form is
   dropped in favour of `setchunk`.
