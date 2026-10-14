# Immersive Optimization

![ImmersiveOptimization](logo.png)

Server-side entity tick scheduler for Minecraft 1.12.2 on Cleanroom or Minecraft Forge.

This is a Cleanroom 1.12.2 port of [ImmersiveOptimization](https://github.com/Luke100000/ImmersiveOptimization)
by Luke100000. The original targets newer Minecraft versions; this port brings the same idea to 1.12.2
and fixes several issues that were present in the first pass of the port.

## What it does

Large packs end up with hundreds or thousands of loaded entities. Many of them are far from any
player, out of sight, or idle, yet they still tick at full speed every tick. Immersive Optimization
gives each loaded entity a throttle level based on how much it matters, and runs the cheap far away
ones less often. Entities near players, in view, in combat, or in the air are never slowed down.

## How it works

A priority table maps each entity id to a tick interval. It is rebuilt on the main thread at a
configurable interval, since the entity list and the camera frustum can only be read safely there.
The AI mixin and the item and XP orb mixins then consult that table:

- An entity with interval 1 runs every tick.
- An entity with interval N runs once every N ticks, staggered by its entity id.

Overrides that have to react immediately are evaluated every tick and are never cached. That covers
entities that are airborne or moving fast, are hurt or hurt-resistant, have a revenge or attack
target, are a swelling creeper, or are riding or being ridden. Bosses, named mobs, projectiles,
falling blocks, primed TNT, and configured entities and namespaces are exempt entirely.

A dynamic stress factor watches the server tick time and lowers priorities further when the server is
struggling.

## Fixes in this port

The first version of the port had a few real problems, which are fixed here:

- It ran a background thread that read live main-thread and client state, including the entity list,
  bounding boxes, NBT data and the client camera frustum. That is a data race. Priority computation
  now runs on the main thread.
- The background thread's only output, the priority table, was never read, so it consumed CPU for
  nothing. The table is now actually used, and item and XP orb ticking is throttled as well.
- The per-tick AI check recomputed player distance and view angle for every mob every tick, and it ran
  twice per mob because both AI task list calls are wrapped. It now uses the cached table.

## Requirements

- Minecraft 1.12.2
- Minecraft Forge 14.23.5.x or Cleanroom 0.6.x
- Java 8

## Installation

Put `immersive_optimization-<version>.jar` into your `mods` folder. Install it on the server. It is a
server-side optimization and does nothing useful on a client only.

## Configuration

File: `config/immersive_optimization.cfg`

### General

| Option | Default | Description |
|---|---|---|
| enableEntities | true | Master toggle for entity throttling. |
| enableBlockEntities | false | Reserved. Block entity throttling is not implemented in this port. |
| optimizeForceLoadedChunks | false | Also throttle entities in force loaded chunks. |
| enableDistanceCulling | true | Throttle harder past render distance in singleplayer. |
| enableTrackingCulling | true | Throttle harder past network tracking distance. |
| enableViewportCulling | true | Throttle entities outside the camera frustum in singleplayer. |
| enableItemThrottling | true | Throttle distant dropped items and XP orbs. |

### Tuning

| Option | Default | Description |
|---|---|---|
| minDistance | 8 | Distance from the nearest player before throttling starts. |
| blocksPerLevel | 48 | Blocks per throttle tier. |
| maxLevel | 20 | Highest throttle interval. |
| itemDropPriority | 8 | Throttle interval for distant dropped items. |
| stressedThreshold | 45 | Tick time in ms that activates the stress scaling. |
| minDecreaseFactor | 0.25 | How far the stress scaling can lower priorities. |
| workerIntervalMs | 500 | Interval between priority table rebuilds. |

### Combat and physics

| Option | Default | Description |
|---|---|---|
| enableCombatProtection | true | Keep mobs that target a player fast. |
| combatTargetDistance | 24 | Distance within which a targeting mob runs at full speed. |
| combatPriorityCap | 2 | Highest interval for a targeting mob. |
| enableMotionProtection | true | Keep fast moving entities smooth. |
| enableFireProtection | true | Keep burning entities responsive. |

### Filtering

| Option | Default | Description |
|---|---|---|
| entityBlacklist | list | Entity registry names that are never throttled. Prefilled with bosses and dangerous entities from several mods. |
| namespaceBlacklist | list | Mod namespaces that are never throttled. |
| dimensionBlacklist | list | Dimensions where throttling is off. |

## Commands

- `/io status` shows the current state and per dimension stats.
- `/io top [dim]` lists the most common loaded entities in a dimension.
- `/io stress` shows the anti lag stress values.
- `/io toggle` enables or disables entity throttling.
- `/io reload` reloads the config.

Aliases: `/immersiveoptimization`, `/immersive_optimization`.

## Notes and limitations

- Block entity throttling is not implemented in this port. The config option is present but does
  nothing, and it is off by default.
- The throttling is intentionally lenient near players. It saves the most in mob dense or idle areas,
  not in active combat.

## Credits

Original mod by Luke100000: https://github.com/Luke100000/ImmersiveOptimization

Cleanroom 1.12.2 port and fixes by Brilliafy.

## License

GPL-3.0, the same license as the original. Copyright (C) 2026 Brilliafy and contributors. See the
LICENSE file.
