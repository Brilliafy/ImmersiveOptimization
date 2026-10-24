# Entity Cadence (1.12.2 Cleanroom & Forge)

<p align="center">
  <img src="logo.png" alt="Entity Cadence" width="180">
</p>

<p align="center">
  <b>A crash-safe, high-performance entity and AI tick scheduler for Minecraft 1.12.2.</b><br>
  <i>Throttles distant AI pathfinding and dropped items without breaking mob physics, combat, or modded mechanics.</i>
</p>

<p align="center">
  <a href="https://github.com/Brilliafy/ImmersiveOptimization"><img src="https://img.shields.io/badge/Minecraft-1.12.2-informational.svg" alt="Minecraft 1.12.2"></a>
  <a href="https://github.com/CleanroomMC"><img src="https://img.shields.io/badge/Modloader-Cleanroom%20%7C%20Forge-orange.svg" alt="Cleanroom / Forge"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPLv3"></a>
</p>

---

## Overview

In modded Minecraft 1.12.2, servers and singleplayer worlds frequently suffer from severe **entity tick lag**. Having hundreds or thousands of active entities loaded across dimensions—in dark caves, unlit wilderness, or distant automated mob farms—forces the game to spend 70–85% of each server tick running AI pathfinding algorithms and goal selectors for mobs that no player can even see.

**Entity Cadence** solves this by dynamically scheduling entity tick rates based on proximity to players, line of sight, and real-time server stress. Distant entities run their expensive AI calculations less frequently, while nearby entities, active combatants, airborne mobs, and bosses continue ticking at 100% full speed.

---

## Origins & Credits

This mod is an extensive Cleanroom / Forge 1.12.2 rewrite and architectural evolution inspired by [**ImmersiveOptimization**](https://github.com/Luke100000/ImmersiveOptimization) originally created by [**Luke100000**](https://github.com/Luke100000) for modern Minecraft (1.20.1+).

While inspired by Luke100000's core concept, **Entity Cadence** is a **ground-up rewrite of the scheduling mechanics**, specifically re-engineered to prevent race conditions, avoid breaking modded entity physics, and seamlessly support the complex 1.12.2 modding ecosystem.

---

## Key Benefits

* ⚡ **Dramatic MSPT / Tick Time Reduction**: Saves 30% to 70% of CPU time spent on entity ticking in mob-dense areas.
* 🛡️ **Zero Physics Glitches**: Mobs never float, stutter, or freeze in mid-air. Gravity, motion, burn damage, and animations always tick smoothly.
* ⚔️ **Flawless Combat Responsiveness**: Mobs targeting a player, taking damage, or preparing to explode (Creepers) automatically bypass throttling.
* 📦 **Dropped Item & XP Orb Optimization**: Restrains runaway item drop lag from automated mob grinders and quarries.
* 📈 **Dynamic Anti-Lag Stress Scaling**: Automatically throttles distant mobs more aggressively when the server experiences tick time spikes (>45 ms).
* 🧩 **Deep 1.12.2 Ecosystem Compatibility**: Native out-of-the-box protections for bosses and elite mobs from mods like *Ice & Fire*, *Lycanites Mobs*, *Scape and Run: Parasites*, *Champions*, and *Twilight Forest*.

---

## The 1.12.2 Rewrite: How It Differs from Original 1.20.1

The modern version (1.20.1) and initial port concepts had architectural assumptions that do not hold in heavy 1.12.2 modpacks. Here is a thorough breakdown of how this mod differs:

### 1. Main-Thread Amortized Scheduler (Eliminating Async Data Races)
* **Original (1.20.1)**: Spawns an asynchronous background worker thread (`Thread.sleep(500)`) that iterates `world.loadedEntityList`, reading live entity positions, bounding boxes, NBT tags, and even client camera frustums off-thread. In the original code, `ConcurrentModificationException` and `ArrayIndexOutOfBoundsException` were caught and ignored. In heavily modded 1.12.2 environments, reading mutable entity collections off-thread causes severe data races and JVM memory corruption.
* **Entity Cadence**: **The background worker thread has been completely removed.** Priority calculation runs synchronously on the server main thread during `World.updateEntities()`, amortized over a configurable interval (`workerIntervalMs`, default 500ms / 10 ticks). Because it executes on the main thread, all data races and concurrent modification hazards are eliminated with negligible overhead.

### 2. AI Goal Throttling vs. Full Entity Tick Halting
* **Original (1.20.1)**: Cancels the entire entity tick via `ServerLevel.tickNonPassenger(Entity)` (`ci.cancel()`). When an entity is throttled, its whole update halts.
* **Entity Cadence**: Intercepts **only AI execution** via `@WrapOperation` on `EntityAITasks.onUpdateTasks()` in `EntityLiving.updateEntityActionState()`.
  * *Why this is critical*: Skipping full entity ticks breaks physics—mobs float in mid-air, take delayed fall damage, stutter during knockback, and rubberband. By throttling *only* AI task execution, entity physics, gravity, collision, burning, and animations continue running at a full 20 TPS, while the expensive pathfinding calculations are throttled.

### 3. Functional Dropped Item & XP Orb Throttling
* **Original (1.20.1)**: Offered configuration options for item throttling, but lacked mixins for non-living entities, leaving item drops unthrottled.
* **Entity Cadence**: Implements dedicated `EntityItemMixin` and `EntityXPOrbMixin` hooks on `onUpdate()`. Distant dropped items tick at a reduced rate (`itemDropPriority = 8`), while items within pickup range of a player (<5 blocks) are guaranteed priority 1 (tick every tick) for instant pickup.

### 4. Instant Combat & Motion Safety Overrides (Evaluated Fresh Every Tick)
To prevent delayed reactions during gameplay, Entity Cadence evaluates safety-critical conditions **every tick** without waiting for the priority refresh:
* **Airborne / High Velocity**: Mobs launched into the air, falling off cliffs, flying with Elytra, or moving rapidly (`motionY > 0.08`, horizontal `motion > 0.02`) run at full speed.
* **Damage & Invulnerability**: Mobs currently in `hurtTime` or `hurtResistantTime` are never throttled.
* **Combat Targeting**: Mobs that have an active attack target or revenge target maintain high tick rates.
* **Exploding Creepers**: Creepers that have started swelling (`getCreeperState() > 0`) bypass throttling instantly.
* **Mounts & Vehicles**: Ridden entities or entities with passengers (boats, carts, tamed mounts) are always exempt.
* **Sitting Pets**: Tamed sitting pets are throttled to `maxLevel`, saving idle CPU cycles.

### 5. Hot-Path Optimizations & Memoization
In vanilla 1.12.2, `EntityLiving.updateEntityActionState()` calls `tasks.onUpdateTasks()` and `targetTasks.onUpdateTasks()` sequentially. To keep scheduler overhead near zero:
* **Dimension Caching**: The scheduler caches `currentDim` and `currentLevel` per tick, avoiding `ConcurrentHashMap` lookups on every mob.
* **Single-Entry Memoization**: The second AI task list call on the same mob in the same tick reuses the cached boolean in a handful of CPU cycles.
* **Class Hierarchy Reflection Caching**: Mod and registry checks are cached once per entity class (`CLASS_BLACKLIST_CACHE`), avoiding repetitive reflection or registry lookups for groups of similar mobs.

### 6. Built-in 1.12.2 Modpack Ecosystem Protections
Out-of-the-box blacklist and boss bar detectors protect high-stakes modded encounters:
* **Ice and Fire**: Dragons, Sea Serpents, Cyclopes, Gorgons, Dread Liches, and beasts.
* **Lycanites Mobs**: Rahovart, Asmodeus, Amalgalich, and all mobs implementing `com.lycanitesmobs.api.IGroupBoss`.
* **Scape and Run: Parasites**: Overseer, Beckons (I–IV), Dispatchers (I–IV), Carriers, Wraiths, and Parasite nodes.
* **Elite Systems**: Automatic detection of *Scaling Health* Blights, *Infernal Mobs*, and *Champions*.
* **Vehicle / Utility Mods**: Automatic namespace protection for *Valkyrien Skies*, *AstikorCarts*, *MrCrayfish's Furniture Mod*, *Sit*, and *Comforts*.
* **Universal Boss Detection**: Universal inspection for `BossInfo` health bars.

---

## Requirements

* **Minecraft**: `1.12.2`
* **Modloader**: [CleanroomMC](https://github.com/CleanroomMC) (v0.6.x+) or **Minecraft Forge** (`14.23.5.2847`+)
* **Dependencies**: [MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixinbooter) (v8.0+ or v10.x)
* **Java**: Compatible with Java 8 through modern JDKs (Java 17–25) when running on Cleanroom.

---

## Installation

1. Download the latest `entitycadence-<version>.jar`.
2. Ensure [MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixinbooter) is installed.
3. Place the `.jar` into your `mods` folder.
4. **Server-Side Only**: Entity Cadence is primarily a server-side mod. It can be installed on dedicated servers without requiring clients to install it. It also works in singleplayer worlds.

---

## In-Game Commands

| Command | Permission | Description |
|---|---|---|
| `/ec status` | All | Displays overall throttling state, server MSPT/TPS, and per-dimension CPU savings. |
| `/ec top [dim]` | All | Lists the top 10 most populated entity types in the specified dimension. |
| `/ec stress` | All | Displays anti-lag stress metrics, dynamic scaling factors, and lifetime stress ticks. |
| `/ec toggle` | OP (Level 2) | Toggles entity throttling on or off at runtime without restarting. |
| `/ec reload` | OP (Level 2) | Reloads `config/entitycadence.cfg` and flushes class caches. |

*Aliases: `/entitycadence`, `/entity_cadence`, `/io`, `/immersiveoptimization`.*

---

## Configuration

Configuration file location: `config/entitycadence.cfg`

### `[General]`
* `enableEntities` *(default: true)*: Master switch for entity throttling.
* `enableBlockEntities` *(default: false)*: Reserved. TileEntity throttling is intentionally disabled in this port to prevent machine/pipe desyncs in tech packs.
* `optimizeForceLoadedChunks` *(default: false)*: When true, entities in chunkloaded areas will also be throttled.
* `enableDistanceCulling` *(default: true)*: Throttles harder past render distance (singleplayer).
* `enableTrackingCulling` *(default: true)*: Throttles harder past network tracking distance.
* `enableViewportCulling` *(default: true)*: Throttles entities outside player camera view frustum (singleplayer).
* `enableItemThrottling` *(default: true)*: Throttles distant dropped items and XP orbs on the ground.

### `[Tuning]`
* `minDistance` *(default: 8)*: Block distance from nearest player before throttling starts. Entities within this radius always run at full speed.
* `blocksPerLevel` *(default: 48)*: Distance step for each throttle tier.
* `maxLevel` *(default: 20)*: Maximum throttling interval (e.g., 20 = tick once per second).
* `itemDropPriority` *(default: 8)*: Throttle tier for distant dropped items.
* `stressedThreshold` *(default: 45)*: Server MSPT threshold where anti-lag scaling activates (45ms = ~20 TPS).
* `minDecreaseFactor` *(default: 0.25)*: Factor by which throttling tiers intensify during heavy server lag.
* `workerIntervalMs` *(default: 500)*: Milliseconds between priority table recalculations on the main thread.

### `[CombatAndPhysics]`
* `enableCombatProtection` *(default: true)*: Keeps mobs in active combat at high tick rates.
* `combatTargetDistance` *(default: 24)*: Range within which an attacking mob runs at 100% full speed.
* `combatPriorityCap` *(default: 2)*: Maximum throttle interval for aggressive mobs beyond combat range.
* `enableMotionProtection` *(default: true)*: Guarantees smooth physics for falling or airborne entities.
* `enableFireProtection` *(default: true)*: Keeps burning mobs responsive to fire/lava damage.

### `[Filtering]`
* `entityBlacklist`: List of entity registry names (`modid:entity`) that are completely exempt from throttling.
* `namespaceBlacklist`: Entire mod namespaces that should never be throttled.
* `dimensionBlacklist`: Dimension IDs where entity throttling is completely disabled.

---

## Frequently Asked Questions (FAQ)

#### Does this break mob farms?
**No.** Far-away passive and hostile mobs continue ticking, just at a staggered rate. Spawners and mob farms will continue producing drops without freezing.

#### Do dropped items take longer to despawn?
Because `EntityItem.onUpdate()` is throttled when farther than 5 blocks from a player, the vanilla 5-minute despawn timer advances only on ticks when the item runs. This means distant uncollected items stay in the world longer in real-world time, while using virtually no CPU. Items near players despawn and can be collected normally.

#### Can I use this alongside other optimization mods?
**Yes.** Entity Cadence is fully compatible with:
* [VintageFix](https://www.curseforge.com/minecraft/mc-mods/vintagefix)
* [Entity Culling](https://www.curseforge.com/minecraft/mc-mods/entity-culling-fabric)
* [Clumps](https://www.curseforge.com/minecraft/mc-mods/clumps)
* [FoamFix](https://www.curseforge.com/minecraft/mc-mods/foamfix-optimization-mod)
* [Universal Tweaks](https://www.curseforge.com/minecraft/mc-mods/universal-tweaks)

---

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).  
Copyright (C) 2026 Brilliafy and contributors.  
Original concept and inspiration by [Luke100000](https://github.com/Luke100000/ImmersiveOptimization).
