# Entity Cadence (1.12.2 Cleanroom & Forge)

**Entity Cadence** is an intelligent, crash-safe entity and AI tick scheduler for Minecraft 1.12.2 on **Cleanroom** and **Forge**.

By dynamically scaling the tick frequency of distant entity AI pathfinding and ground items without pausing physics or breaking combat, Entity Cadence significantly improves server MSPT and client tick rates—all while keeping gameplay completely seamless.

---

### 💡 Why Do You Need This?

In modded 1.12.2, servers and singleplayer worlds easily accumulate hundreds or thousands of loaded entities. Deep in caves, wandering dark forests, or resting in distant pens, these entities force Minecraft to waste up to **80% of its tick budget** running pathfinding searches, line-of-sight checks, and goal evaluations that no player can even observe.

Unlike blunt optimizers that freeze entities in place or break modded AI, **Entity Cadence maintains natural rhythm and pacing**:
* Distant mobs gradually think less often.
* Approaching mobs smoothly transition back to full 20 TPS.
* Mobs in combat, falling through the air, taking damage, or preparing to explode react **instantly** at 100% speed.
* Zero floating mobs, zero delayed fall damage, and zero rubberbanding.

---

### ✨ Features

* 🚀 **Massive Tick Performance Gains**: Lowers server MSPT by 30% to 70% in entity-heavy areas, keeping servers at a steady 20 TPS.
* 🧠 **Smart AI Throttling**: Rather than halting the whole entity, it intercepts only expensive `EntityAITasks` pathfinding, allowing gravity, motion, collisions, and animations to run naturally.
* 📦 **Dropped Item & XP Orb Throttling**: Tames lag from automated mob farms, quarries, or dropped item overflows by slowing uncollected items far from players. Items within 5 blocks of a player always tick at full speed for instant pickup.
* ⚔️ **Instant Combat & Motion Protections**: Evaluated fresh every tick! If an entity is airborne, falling off a cliff, hurt, has an attack/revenge target, or is a swelling Creeper, it immediately bypasses throttling.
* 🛡️ **Dynamic Anti-Lag Stress Scaling**: Constantly monitors server tick health. If tick times cross 45 ms, throttling gently intensifies to recover TPS, and eases back down once the lag spike subsides.
* 👾 **Built-in 1.12.2 Modpack Compat**: Pre-configured exclusions for bosses and dangerous mobs from **Ice and Fire**, **Lycanites Mobs**, **Scape and Run: Parasites**, **Aether Legacy**, **Twilight Forest**, **Mowzie's Mobs**, **Champions**, **Infernal Mobs**, and **Scaling Health**.
* 🚫 **Thread-Safe Architecture**: Completely avoids the dangerous off-thread data races present in other optimizers. All scheduling is amortized safely on the main thread.

---

### ⚙️ Requirements & Installation

* **Minecraft**: `1.12.2`
* **Modloader**: [CleanroomMC](https://github.com/CleanroomMC) (v0.6.x+) or **Minecraft Forge** (`14.23.5.2847`+)
* **Dependencies**: [MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixinbooter) (v8.0+ or v10.x)
* **Java**: Supports standard Java 8 as well as modern JVMs (Java 17–25) on Cleanroom.

#### Where to Install?
* **Server-Side**: Install on your dedicated server. Clients do **NOT** need the mod installed to join.
* **Singleplayer**: Install in your client's `mods` folder to enjoy improved internal server TPS and viewport frustum culling.

---

### 🕹️ In-Game Commands

* `/ec status` - Shows current optimization status, server MSPT, TPS, and dimension CPU savings.
* `/ec top [dim]` - Displays the top 10 most populated entity types in the dimension.
* `/ec stress` - Displays real-time anti-lag stress metrics and scaling factors.
* `/ec toggle` - (OP Level 2) Dynamically enable or disable entity throttling on the fly.
* `/ec reload` - (OP Level 2) Hot-reloads the configuration file.

*(Legacy command aliases `/io` and `/immersiveoptimization` are also supported).*

---

### 🤝 Mod Compatibility

Entity Cadence is designed to be fully compatible with existing 1.12.2 performance suites:
* **VintageFix** / **FoamFix** / **Universal Tweaks** - Fully compatible.
* **Clumps** - Fully compatible (both work together to minimize XP orb lag).
* **Entity Culling** - Fully compatible (Entity Culling handles client render occlusion; Entity Cadence handles server tick logic).
* **Tech Mods (AE2, Ender IO, GregTech)** - Fully compatible. (Block entity/TileEntity throttling is intentionally disabled to ensure machines and piping never desync).

---

### 📜 Credits & License

* **Original Concept & Inspiration**: Inspired by [ImmersiveOptimization](https://github.com/Luke100000/ImmersiveOptimization) by [Luke100000](https://github.com/Luke100000).
* **Cleanroom 1.12.2 Architecture & Rewrite**: Developed by Brilliafy.
* **License**: Licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html).
