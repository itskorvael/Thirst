# Thirst

A Paper plugin that adds a thirst mechanic alongside vanilla hunger: a HUD
indicator above the hotbar, exertion-based drain with starvation damage at zero,
bottle-based drinking, per-chunk water quality (FRESH / NEUTRAL / DIRTY), and a
stacking dirty-water illness (Salmonella).

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

## Features

- **Thirst meter** (0–20, mirrors hunger) that drains with exertion — sprinting,
  jumping, swimming, fighting, mining — and at zero deals starvation damage.
  At the critical threshold sprinting stops giving a speed bonus.
- **HUD**: an action-bar bar or percent (blue → red when critical, neon green
  while infected), with a boss-bar debug/fallback mode. Can share the action bar
  with other plugins through PlaceholderAPI.
- **Bottle-based drinking**: fill a glass bottle or bucket at any water block or
  cauldron to get a *Fresh*, *Neutral* or *Dirty* item, tagged with the quality of
  the chunk it came from. Rain makes water dirty.
- **Per-chunk water quality** stored in SQLite and painted with `/water`.
  Unpainted chunks are auto-detected (water → `natural-water-quality`).
- **Salmonella**: drinking dirty water can add a stack (up to 5). Each stack adds
  escalating potion effects, decays over time, and can be cured with milk (a
  chance, not a guarantee). Severe stacks deal periodic damage.
- **Everything is configurable.** Every gameplay number lives in `config.yml`;
  invalid values log a warning and fall back to the default.

## Commands and permissions

| Command | Permission | Default |
|---|---|---|
| `/thirst get` | `thirst.command.thirst` | everyone |
| `/thirst salmonella get` | `thirst.command.thirst` | everyone |
| `/thirst get <player>` | `thirst.admin.thirst` | op |
| `/thirst set <player> <level>` | `thirst.admin.thirst` | op |
| `/thirst salmonella set <player> <stacks>` | `thirst.admin.thirst` | op |
| `/thirst salmonella cure <player>` | `thirst.admin.thirst` | op |
| `/thirst reload` | `thirst.admin.thirst` | op |
| `/water get` | `thirst.admin.water` | op |
| `/water setchunk <fresh\|neutral\|dirty>` | `thirst.admin.water` | op |
| `/water setregion ...` | `thirst.admin.water` | op |

`thirst.admin` grants both admin permissions. `/thirst reload` prints any
config-validation warnings straight to whoever ran it.

## Roadmap

Not implemented: a water-quality wand with a chunk-outline preview, a rain
trickle-drink, and a resource-pack HUD with droplet glyphs.

## Configuration and design notes

All gameplay numbers live in `config.yml` (copied to the data folder on first
run). Nothing is hardcoded. Invalid values log a warning and fall back to the
built-in default instead of breaking gameplay.

### Design notes: thirst core

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
- **Sprinting made pointless at/below the critical threshold**
  (`disable-sprint-when-critical`, default on), since a true "can't sprint"
  isn't reachable from the server — the client owns that decision (it checks its
  own food level > 6; cancelling `PlayerToggleSprintEvent` does nothing). Two
  parts:
  - *Speed:* `CriticalSpeedModifier` adds a transient negative `MOVEMENT_SPEED`
    modifier while the player is both critical and sprinting, cancelling the
    ~30% boost (`critical-sprint-speed-multiplier`, default 0.75 ≈ walking).
    Removed the instant either condition stops — walking is never slowed.
  - *Jump:* the sprint-jump lunge is a client-side velocity kick the server
    can't dampen (`setVelocity` on a moving player doesn't stick — client
    authority), so `ThirstSprintListener` cancels the jump outright *while
    sprinting*. Release sprint and jumping is normal.
- **Folia is not supported** (uses the Bukkit scheduler).

### Design notes: HUD

- **One shared HUD task**, not one task per player — it iterates online players
  each interval. Joiners are picked up next tick; leavers cleaned in `onQuit`.
  Same net effect as per-player tasks, less overhead.
- **HUD hidden in Creative / Spectator**, matching vanilla hiding the hunger bar
  there.
- **`hud-mode` is a pluggable strategy** (`HudRenderer`): `ACTIONBAR_TEXT` and
  `BOSSBAR` today; `ACTIONBAR_RESOURCEPACK` currently logs a one-time warning and
  falls back to `ACTIONBAR_TEXT`. A resource-pack renderer can be added without
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

### Design notes: water quality

- **Storage:** SQLite (`water_quality.db` in the plugin folder), table
  `chunk_water_quality (world_name, chunk_x, chunk_z, quality)` with a
  `CHECK (quality IN ('FRESH','NEUTRAL','DIRTY'))`. An older DB whose stored
  `CREATE` statement predates `NEUTRAL` (or used `CLEAN`) is rebuilt on startup
  (`WaterQualityStore#migrateSchema`, remapping `CLEAN -> FRESH`).
- **Threading:** one dedicated daemon thread owns a single JDBC connection
  (`SQLiteDataSource`, WAL, `synchronous=NORMAL`). Callers get
  `CompletableFuture`s; the main thread never blocks on the DB *except* the
  one-time `initialize()` + `loadAll()` at enable.
- **Cache:** the whole table is read into a `ConcurrentHashMap<ChunkKey,
  WaterQuality>` on enable (not lazy per-chunk, not per-`WorldLoadEvent` — a
  single server's DB is small and this is simpler; a world added later still has
  its rows because nothing filters by loaded worlds). Reads fall back to
  `default-water-quality` (now **`NEUTRAL`**). Writes hit the cache immediately
  and the DB async, so a drink right after `/water setchunk` sees the new value.
- **`NEUTRAL`** is "no established quality" — the default for land with no
  natural water. Pouring a tagged *Bucket of Fresh/Neutral/Dirty Water* into a chunk
  that is still `NEUTRAL` (`BucketListener#onBucketEmpty`) sets that chunk to the
  bucket's quality (chunk-level, not per-block). Drinking neutral water restores
  `neutral-water-restore-amount` (default 3) and never causes Salmonella.
- **Natural-water detection** (`water.detect-water`, default on): an unpainted
  chunk is scanned once — 256 `HeightMap.MOTION_BLOCKING` column checks — the
  first time its quality is asked for. Has surface water → `natural-water-quality`
  (default DIRTY); dry → `default-water-quality` (NEUTRAL). Result cached for the
  session; a painted chunk is never scanned or overwritten (detection is
  read-only). Limits: won't notice water *added* to a dry chunk until restart,
  and misses water fully under solid overhangs / leaves. Admins can still paint
  those.
- **`/water setregion`** takes two **block**-coordinate corners (what admins
  read off F3), converts to a chunk rectangle, and writes it in one transaction.
  Guarded by `water.max-region-chunks` (default 50 000).
- **`/water set <fresh|neutral|dirty> [radius]` from the original spec is
  dropped** in favour of `setchunk` (per an earlier maintainer decision).
- `ChunkKey` / `ChunkArea` conversions are pure and unit-tested; the store is
  tested against an in-memory SQLite DB.
- On Java 25, sqlite-jdbc loading its native library prints a one-line
  `WARNING: ... native access ...` to the console on first DB use. Cosmetic —
  it's the JDK's new native-access notice, not an error.

### Design notes: drinking

- **Bottle-only for *drinking*, no drink-from-block** — per the earlier
  maintainer decision. Right-clicking **any water block** — natural or placed,
  source or flowing — or a cauldron with an empty `GLASS_BOTTLE` cancels the
  vanilla fill (which is source-only) and gives a tagged bottle.
  `resolveFillTarget` tries `getTargetBlockExact(..., FluidCollisionMode.ALWAYS)`,
  then a `getLineOfSight` walk (catches thin flowing-water shapes the block ray
  passes through), then the eye block. Quality is the chunk's (or DIRTY while
  raining, or the cauldron's tracked quality) — loose water blocks are not
  individually tracked. A **bucket** (`PlayerBucketFillEvent`) is tagged the
  same way — *Bucket of Freshwater / Water / Dirty Water* — but stays vanilla
  source-only.
- **The bottle is a real water potion** (`PotionType.WATER`) with a PDC quality
  tag, a tint and a config name; drinking it uses the vanilla animation and
  returns a glass bottle for free. `drinking.cooldown-ticks` is enforced
  server-side (the interact that starts a drink is cancelled if you drank too
  recently). *A client-side `USE_COOLDOWN` data component was tried and removed —
  mixing the experimental `ItemStack#setData` API with `setItemMeta` was wiping
  the item's custom data (the quality tag).*
- **Cauldrons hold the quality of what's poured in**, not the chunk
  (`CauldronWaterTracker`, in-memory `Map<BlockPos, WaterQuality>`): a tagged
  bucket / bottle carries its quality across; rain / dripstone (`NATURAL_FILL`,
  gated by `rain-cauldron-always-dirty`) is dirty runoff. Mixing rule: any dirty
  part → whole pot dirty. Untracked cauldrons (pre-restart) fall back to the
  source-quality of their own location.
- **Salmonella:** dirty water rolls `salmonella-chance` and, on a hit, bumps the
  stack count + `salmonella_ticks_remaining` in the PDC (capped at
  `stack-durations-seconds.size()`). Stacks then drive the effects, decay and cure.
- **`dirty-while-raining`** (default on) — while a world has active rain/storm,
  every open source there resolves DIRTY regardless of chunk. Checked at fill
  time, so it reverts the moment the weather clears. Containers keep their
  fill-time tag.
- **Loose water blocks are not tracked** — emptying a *Bucket of Fresh Water*
  into the world just makes ordinary chunk-quality water. Per-block quality
  propagation through flowing water is out of scope.
- **Creative** players fill without consuming the container or emptying the cauldron.
- New config: `drinking.{fresh,dirty}-{bottle,bucket}-name` (MiniMessage).
- Mostly not unit-testable (ItemStack needs a running server). The pure bits —
  the Salmonella stack maths and the cauldron `mix()` rule — are tested; the
  rest needs an in-game check.

### Design notes: Salmonella

- **Stack state lives in the PDC** (`salmonella_stacks`, `salmonella_ticks_remaining`),
  written by `ThirstManager` and owned by `SalmonellaService`. No task of its
  own — `ThirstTickTask` drives `SalmonellaService.tick(player)` once per second
  before the thirst-drain logic, so illness ticks even while the player is
  otherwise thirst-exempt (Creative, etc.).
- **Effect structure is code-defined, tuning is config.** `SalmonellaEffects`
  maps a fixed 1→5 severity progression — Slowness I; +Weakness I; +Nausea I
  (from `nausea-from-stack`); held; only the worst stack (5) amplifies Weakness
  and Nausea to level II — that's the illness's shape, not a number to tweak.
  Durations (`stack-durations-seconds`), decay interval, damage and cure are
  all in `config.yml`. `max-stacks` = number of duration entries (capped at 5).
  Nausea is the icon shown, matching the earlier decision to reuse the vanilla
  Nausea indicator.
- **`salmonella-chance` defaults to 0.65** (up from an initial 0.35) so the
  illness actually shows up in normal play instead of needing many dirty
  drinks before it lands.
- **Nausea's amplifier doesn't do what you'd expect.** Vanilla's Nausea/Confusion
  screen-warp intensity is identical at every level — the amplifier only
  affects duration bookkeeping, not how much it shakes. So "tone down the
  shake" can't be done by lowering the level; instead `nausea-from-stack`
  (default 3) delays Nausea's *onset* — a single dirty-water infection (stack 1)
  now only gives Slowness, no wobble at all.
- **Decay is pure arithmetic** (`SalmonellaService.decay(...)` → `DecayStep`,
  unit-tested): the timer counts down each tick; on expiry it drops one stack
  and resets, or reports a cure at zero.
- **Milk is a gamble, not a guarantee** (`PlayerItemConsumeEvent`, `MILK_BUCKET`)
  — `milk-cure-chance` (default 0.3) is rolled, only while actually infected
  (`SalmonellaService#isInfected`, so a healthy player's glass of milk is a
  silent no-op, not a wasted roll); on a hit it removes `milk-cure-stacks`
  (default 1). A miss plays a quiet "no effect" cue (`ENTITY_VILLAGER_NO`) so
  it doesn't look like nothing happened. `cure-with-milk: false` disables
  curing entirely.
- **Severe-stack damage:** from `damage.from-stack` (default 4) the player takes
  `damage.amount` every `damage.interval-seconds` as `DamageType.MAGIC`, with a
  1-health floor **until** `damage.lethal-from-stack` (default 5), where the
  floor drops to 0 and Salmonella can kill. Timer is in-memory (`forget` on quit).
- **Neon-green HUD while infected** (the extra ask): `ThirstHudFormatter` colour
  priority is *infected > critical thirst > normal*; `hud.color-infected`
  (default `<color:#39FF14>`) plus a `☣<stacks>` suffix when
  `hud.salmonella-indicator` is on. The boss-bar fallback goes
  `BossBar.Color.GREEN`.
- **Vomit** is cosmetic only (slime particles + burp) from `vomit-from-stack`,
  ~1/4 seconds.
- New config: `hud.color-infected`, `hud.salmonella-indicator`, and the
  `salmonella.{milk-cure-stacks,vomit-from-stack}` + `salmonella.damage.*`
  subsection.

### Design notes: commands and permissions

- **Two permissions instead of one.** Previously the entire `/thirst` tree sat
  behind `thirst.admin.thirst`. Now `thirst.command.thirst` (default **true**)
  covers only the no-target `get` forms — a player checking their own thirst
  or Salmonella status — while `thirst.admin.thirst` (default op) is required
  for `/thirst get <target>`, `set`, `reload`, and every `salmonella`
  subcommand. Each Brigadier node carries its own `.requires(...)`, so the
  permission split is enforced per-branch, not just at the top of the tree.
  `/water` stays fully behind `thirst.admin.water` — it edits world state, not
  personal status, so there's no self-service case for it.
- **`/thirst salmonella get|set|cure`** exist mainly because testing Salmonella
  without them meant chugging real dirty-water bottles to reach a given stack
  count. `set` clamps into `[0, max-stacks]` and applies/clears effects
  immediately (`SalmonellaService#setStacks`); `cure` (`#cureAll`) clears
  unconditionally, bypassing `milk-cure-chance` and `milk-cure-stacks` — it's
  an admin override, not a simulated milk bucket.
- **`/thirst reload` now surfaces config-validation results.** `ThirstConfig`
  has clamped bad values and logged warnings from the start, but only to the
  console. `ThirstPlugin#loadPluginConfig` now also collects every warning
  from that pass (`lastConfigWarnings()`), and the reload command prints the
  count and each message straight to whoever ran it — an admin editing
  `config.yml` sees typos immediately instead of needing console access.
- **Left out on purpose:** rain trickle-drink and a water-quality wand with a
  chunk-outline visualizer. Both are new features rather than polish, so they
  were not built, and their config keys were removed rather than left unused.

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

### Open questions (defaults chosen)

1. **Sprint gating** — *resolved:* at/below the critical threshold the sprint
   speed bonus is neutralised (`disable-sprint-when-critical`,
   `critical-sprint-speed-multiplier`). A true "can't sprint" isn't reachable
   from the server — the client owns the sprint decision — so sprinting is made
   pointless instead: no faster than walking. Normal walking is unaffected.
2. **Hunger interaction** — thirst is fully independent of vanilla hunger for
   now (no salty-food drain multiplier).
3. **Salmonella cure** — currently item-only (milk bucket). Time-based recovery
   while hydrated on fresh water is not implemented.
4. **Scale** — SQLite is assumed sufficient. Revisit if chunk counts reach the
   millions or multiple servers share the store.

### Deviations from the original spec

1. **Drinking is bottle-based only.** Fill an empty glass bottle at a water
   source block (or water cauldron) to receive a *Bottle of Fresh Water* or
   *Bottle of Dirty Water*, tagged with the quality of the chunk it was filled
   in. There is no "drink straight from the block" interaction.

2. **Water-quality command is `/water setchunk <fresh|neutral|dirty>`** — it
   targets the single chunk the player is currently standing in (no coordinates,
   no radius). `/water get` still reports the current chunk. `/water setregion
   ...` is kept for bulk area painting. The spec's `/water set ... [radius]` form
   is dropped in favour of `setchunk`.

3. **Water quality has three states: `FRESH` / `NEUTRAL` / `DIRTY`** (spec had
   two). `CLEAN` was renamed `FRESH` (`"clean"` still accepted). Unpainted
   chunks: `NEUTRAL` if dry, `natural-water-quality` (DIRTY) if they contain
   surface water (`water.detect-water`). Databases migrate on load.

4. **Buckets are tagged too** (*Bucket of Fresh/Neutral/Dirty Water*), and rain
   makes water dirty (`dirty-while-raining`, `rain-cauldron-always-dirty`).
