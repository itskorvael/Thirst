# Thirst

A Paper plugin that adds thirst next to vanilla hunger. There's a meter that drains as you move around, a HUD above the hotbar, bottles of water that are fresh, neutral or dirty depending on the chunk they came from, and a stacking illness called Salmonella that you get from dirty water.

## Download

Get the jar from the [Releases page](https://github.com/itskorvael/Thirst/releases/latest) and pick the one that matches your server:

| Your server | Jar | Java |
|---|---|---|
| Paper 26.1.2 | `Thirst-<version>.jar` | 25 |
| Paper 26.2 | `Thirst-<version>-paper-26.2.jar` | 25 |
| Paper 26.3 | `Thirst-<version>-paper-26.3.jar` | 25 |
| Paper 1.21.4 to 1.21.11 | `Thirst-<version>-paper-1.21.4-1.21.11.jar` | 21 |

Put it in your server's `plugins` folder and restart. Thirst is Paper only (not Spigot or Bukkit) and Folia isn't supported. It's distributed only from this repository. For help, use [Discord](https://discord.gg/FJGjZC3JeV) or the [issue tracker](https://github.com/itskorvael/Thirst/issues).

Mojang switched to year-based versions in 2026. The 26.x releases follow the 1.21 line and need Java 25, and the 1.21.x build runs on Java 21. PlaceholderAPI is optional and only used for `hud.external-segment`.

## How it plays

**Thirst.** The meter runs from 0 to 20, like hunger. It drains with exertion (sprinting, jumping, swimming, fighting, mining) and a little while you stand still. At zero you take starvation damage. Below a critical level sprinting gives no speed bonus. Creative and Spectator players are exempt, and dying refills thirst and clears any illness.

**The HUD.** An action-bar bar or percentage, blue and turning red when critical, neon green while you're sick. There's also a boss-bar mode. It can share the action bar with another plugin through PlaceholderAPI (see below).

**Drinking.** Right-click any water block or a cauldron with an empty glass bottle to get a bottle of fresh, neutral or dirty water, tagged with the quality of the chunk it came from. A bucket is tagged the same way. Rain makes all open water dirty. Drinking uses the normal animation and gives the glass bottle back.

**Water quality.** Every chunk is fresh, neutral or dirty. Admins paint chunks with `/water`. A chunk nobody painted is checked once for surface water: if it has some, it's dirty by default (`natural-water-quality`), and if it's dry it's neutral. Neutral water restores a little thirst (`neutral-water-restore-amount`, 3) and never makes you sick. Poured water remembers what it was, and so do cauldrons, so you can't turn dirty water fresh by pouring it into a fresh chunk and scooping it up again. Rain and dripstone fill cauldrons with dirty water.

**The Pitcher Filter.** A crafted item with a limited number of uses (pitcher pod, gravel, sand and charcoal by default). Hold it in your main hand and a Thirst bottle or bucket in the other, then right-click, and the water goes up one quality step. It never goes above `filter.max-quality` (neutral by default, so fresh water keeps its value). A bottle costs one use and a bucket costs `filter.bucket-cost` (3). A filter can be recharged with Eggshell Fragments from the Archaeology plugin (`filter.recharge`), which is what makes clean water need trips to nests. Without Archaeology nobody can get fragments, so filters just wear out. Cauldrons can't be filtered yet, so fill a bottle first. It doesn't cure Salmonella.

**Salmonella.** Dirty water has a chance (`salmonella-chance`, 0.65) to add a stack, up to 5. Stacks add effects: Slowness first, then Weakness, then Nausea (from stack 3 by default), and only stack 5 makes Weakness and Nausea stronger. Stacks wear off over time. Milk has a chance to remove one (`milk-cure-chance`, 0.3) and does nothing if you're healthy. From stack 4 you take periodic magic damage, which can only kill you at stack 5. Vomiting is cosmetic.

Every number is in `config.yml`. A bad value logs a warning and falls back to the default, and `/thirst reload` prints those warnings to whoever ran it.

## Commands and permissions

| Command | Permission | Default |
|---|---|---|
| `/thirst get`, `/thirst salmonella get` | `thirst.command.thirst` | everyone |
| `/thirst get <player>` | `thirst.admin.thirst` | op |
| `/thirst set <player> <level>` | `thirst.admin.thirst` | op |
| `/thirst salmonella set <player> <stacks>` | `thirst.admin.thirst` | op |
| `/thirst salmonella cure <player>` | `thirst.admin.thirst` | op |
| `/thirst filter give <player> [uses]` | `thirst.admin.thirst` | op |
| `/thirst reload` | `thirst.admin.thirst` | op |
| `/water get` | `thirst.admin.water` | op |
| `/water setchunk <fresh\|neutral\|dirty>` | `thirst.admin.water` | op |
| `/water setregion ...` | `thirst.admin.water` | op |

`thirst.admin` grants both admin permissions. `/water setchunk` paints the chunk you're standing in. `/water setregion` takes two block corners (what you read off F3) and paints that area in one go, up to `water.max-region-chunks` (50,000).

## Sharing the action bar

`hud.external-segment` puts text from a PlaceholderAPI placeholder on the same action-bar line as the thirst bar, so two plugins don't fight over the slot. It needs PlaceholderAPI and is ignored without it. For example, with RealisticSeasons:

1. In RealisticSeasons' config, turn its own action bar off: `display → temperature → actionbar: false`.
2. In Thirst's `config.yml`:
   ```yaml
   hud:
     external-segment:
       enabled: true
       placeholder: "%rs_temperaturecolor%%rs_temperature%"
       place-before: true
       separator: " <white>|</white> "
   ```
3. Run `/thirst reload`.

You get something like `22°C | [||||||----]`. Any PlaceholderAPI string works, not only RealisticSeasons'. Legacy colour codes in the placeholder are honoured, and the separator is MiniMessage. HUD colours and bar characters also use MiniMessage, so hex colours and gradients work.

Paper can't see another plugin's action-bar packets, so there's no automatic yielding. If Thirst is the only plugin using the action bar, set `actionbar-overwrite-others: true`. A plugin that depends on Thirst can also call `((ThirstPlugin) Bukkit.getPluginManager().getPlugin("Thirst")).hud().requestActionBarYield(player[, millis])` before sending its own.

## Notes

- Water quality is stored in SQLite (`water_quality.db` in the plugin folder), read into memory at startup and written in the background. Older databases are upgraded on load, and the old `clean` name still works as `fresh`.
- The SQLite driver isn't bundled. `ThirstPluginLoader` fetches it into Paper's shared library cache. On Java 25 you may see a one-line native-access warning in the console the first time the database is used. It's harmless.
- Thirst doesn't interact with vanilla hunger, so there's no extra drain from salty food.
- Salmonella is cured with milk only. There's no recovery over time from drinking fresh water.
- The vanilla Nausea effect shakes the screen the same at every level, so lowering the level wouldn't tone it down. `nausea-from-stack` delays when it starts instead.
- Not built: a water-quality wand with a chunk outline, a rain trickle-drink, and a resource-pack HUD with droplet icons (`ACTIONBAR_RESOURCEPACK` falls back to text).
- Cooldowns and speed changes are enforced on the server because the client decides whether you can sprint. At critical thirst a sprinting player gets a slowdown (`critical-sprint-speed-multiplier`, 0.75, about walking pace) and sprint-jumps are cancelled.

## Building

```bash
./gradlew build
```

The jar is written to `build/libs/Thirst-<version>.jar` (Paper 26.1.2, Java 25). For another Paper line, pass the API and Java version:

```bash
./gradlew build -PpaperApi=26.3.build.236-stable -PapiVersion=26.3
./gradlew build -PpaperApi=1.21.4-R0.1-SNAPSHOT -PapiVersion=1.21 -PjavaVersion=21
```

You need a JDK for Gradle's toolchain. Gradle will download one if it can, or you can point it at a local install with `org.gradle.java.installations.paths`. Building is for your own use only (see the licence).

## Licence

All rights reserved. Thirst is free to use: you can download it and run it on your own server, including one that takes donations. You can't redistribute it, publish modified versions or sell it. See [LICENSE](LICENSE) for the full terms. Copies received under an earlier licence, including the MIT License used before 2026-11-25, keep that licence.
