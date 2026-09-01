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
- [ ] Phase 2 — thirst core (`ThirstManager`, drain + exhaustion task, starvation damage)
- [ ] Phase 3 — action-bar HUD
- [ ] Phase 4 — per-chunk water quality (SQLite schema, async load/save, cache, `/water`)
- [ ] Phase 5 — bottle-based drinking (fill at a source → Clean/Dirty Water bottle → drink)
- [ ] Phase 6 — Salmonella stacks, effects, decay, cure
- [ ] Phase 7 — `/thirst` commands, permissions, config validation, `/thirst reload`
- [ ] Phase 8 — HUD v2 (resource-pack droplet glyphs), optional

## Configuration

All gameplay numbers live in `config.yml` (copied to the data folder on first
run). Nothing is hardcoded. Invalid values log a warning and fall back to the
built-in default instead of breaking gameplay.

### Open questions (defaults chosen, pending review)

1. **Sprint gating** — thirst is currently *pressure only*; it does not disable
   sprint at 0 (unlike vanilla hunger). Flip to hard gating later if desired.
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
