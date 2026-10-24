package net.conczin.entitycadence.config;

import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityMultiPart;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.effect.EntityWeatherEffect;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Config(modid = "entitycadence", name = "entitycadence")
public class ModConfig {

    @Config.Name("General")
    @Config.Comment("General settings and toggles")
    public static GeneralCategory GENERAL = new GeneralCategory();

    @Config.Name("Tuning")
    @Config.Comment("Tick throttling distance, intervals, and stress tuning")
    public static TuningCategory TUNING = new TuningCategory();

    @Config.Name("CombatAndPhysics")
    @Config.Comment("Dynamic combat fluidity and physics protections")
    public static CombatCategory COMBAT = new CombatCategory();

    @Config.Name("Filtering")
    @Config.Comment("Entity, namespace, and dimension blacklist configuration")
    public static FilteringCategory FILTERING = new FilteringCategory();

    public static class GeneralCategory {
        @Config.Comment("Enable the entity tick scheduler. Setting to false turns off all throttling.")
        public boolean enableEntities = true;

        @Config.Comment("Reserved. Block entity (TileEntity) throttling is not implemented in this 1.12.2 port; this option currently does nothing. Keep FALSE for tech/magic packs.")
        public boolean enableBlockEntities = false;

        @Config.Comment("Throttle entities in force-loaded (chunkloaded) chunks.")
        public boolean optimizeForceLoadedChunks = false;

        @Config.Comment("Reduce tick rate more aggressively when entities are beyond view render distance (singleplayer only).")
        public boolean enableDistanceCulling = true;

        @Config.Comment("Reduce tick rate more aggressively when entities are beyond network tracking distance.")
        public boolean enableTrackingCulling = true;

        @Config.Comment("Reduce tick rate of entities outside player camera view frustum (singleplayer only).")
        public boolean enableViewportCulling = true;

        @Config.Comment("Enable throttling of distant item drops and XP orbs on the ground.")
        public boolean enableItemThrottling = true;
    }

    public static class TuningCategory {
        @Config.Comment("Distance (in blocks) from the nearest player before any throttling begins.")
        @Config.RangeInt(min = 0, max = 256)
        public int minDistance = 8;

        @Config.Comment("Blocks per throttling tier. For every N blocks beyond minDistance, tick interval increases by 1.")
        @Config.RangeInt(min = 2, max = 512)
        public int blocksPerLevel = 48;

        @Config.Comment("Blocks per throttling tier for entities culled by render distance.")
        @Config.RangeInt(min = 2, max = 256)
        public int blocksPerLevelDistanceCulled = 12;

        @Config.Comment("Blocks per throttling tier for entities culled by tracking distance.")
        @Config.RangeInt(min = 2, max = 256)
        public int blocksPerLevelTrackingCulled = 10;

        @Config.Comment("Blocks per throttling tier for entities outside camera view frustum.")
        @Config.RangeInt(min = 2, max = 256)
        public int blocksPerLevelViewportCulled = 16;

        @Config.Comment("Maximum throttling tier (1 = tick every tick, 20 = tick once per second).")
        @Config.RangeInt(min = 1, max = 100)
        public int maxLevel = 20;

        @Config.Comment("Throttling tier for distant dropped items outside player pickup range.")
        @Config.RangeInt(min = 1, max = 20)
        public int itemDropPriority = 8;

        @Config.Comment("Server tick time threshold in ms before dynamic anti-lag stress scaling activates (50ms = 20 TPS, 0 to disable).")
        @Config.RangeInt(min = 0, max = 200)
        public int stressedThreshold = 45;

        @Config.Comment("Factor by which throttling ramps up during heavy server stress.")
        @Config.RangeDouble(min = 0.0, max = 0.9)
        public double minDecreaseFactor = 0.25;

        @Config.Comment("Interval in milliseconds between main-thread priority/statistics recalculations (converted to whole ticks; 50ms = every tick).")
        @Config.RangeInt(min = 50, max = 5000)
        public int workerIntervalMs = 500;
    }

    public static class CombatCategory {
        @Config.Comment("Ensure mobs actively in combat (targeting a player or currently taking damage) maintain high tick rates.")
        public boolean enableCombatProtection = true;

        @Config.Comment("Distance in blocks within which a mob actively targeting a player runs at 100% full speed.")
        @Config.RangeInt(min = 4, max = 128)
        public int combatTargetDistance = 24;

        @Config.Comment("Maximum throttling tier for mobs actively targeting a player beyond combatTargetDistance (2 = every other tick).")
        @Config.RangeInt(min = 1, max = 10)
        public int combatPriorityCap = 2;

        @Config.Comment("Limit throttling for entities with high vertical velocity (falling off cliffs, launched into air) so motion stays smooth.")
        public boolean enableMotionProtection = true;

        @Config.Comment("Limit throttling for burning entities so fire/lava damage ticks remain responsive.")
        public boolean enableFireProtection = true;
    }

    public static class FilteringCategory {
        @Config.Comment("List of entity registry names that should NEVER be throttled (bosses, dangerous projectiles, special mobs).")
        public String[] entityBlacklist = new String[]{
                // Vanilla
                "minecraft:player",
                "minecraft:ender_dragon",
                "minecraft:wither",
                "minecraft:ender_pearl",
                "minecraft:shulker_bullet",

                // Ice and Fire
                "iceandfire:firedragon",
                "iceandfire:icedragon",
                "iceandfire:lightningdragon",
                "iceandfire:seaserpent",
                "iceandfire:gorgon",
                "iceandfire:cyclops",
                "iceandfire:siren",
                "iceandfire:dread_lich",
                "iceandfire:dread_beast",
                "iceandfire:dread_scuttler",
                "iceandfire:dread_thrall",
                "iceandfire:dread_ghoul",

                // Lycanites Mobs
                "lycanitesmobs:rahovart",
                "lycanitesmobs:asmodeus",
                "lycanitesmobs:amalgalich",

                // Scape and Run: Parasites
                "srparasites:overseer",
                "srparasites:beckon_si",
                "srparasites:beckon_sii",
                "srparasites:beckon_siii",
                "srparasites:beckon_siv",
                "srparasites:dispatcher_si",
                "srparasites:dispatcher_sii",
                "srparasites:dispatcher_siii",
                "srparasites:dispatcher_siv",
                "srparasites:parasitestain",
                "srparasites:scent",
                "srparasites:source",
                "srparasites:carrier_flying",
                "srparasites:carrier_heavy",
                "srparasites:wraith",
                "srparasites:bogle",
                "srparasites:haunter",

                // BattleTowers & Defiled Lands
                "battletowers:golem",
                "defiledlands:the_destroyer",
                "defiledlands:the_mourner",

                // The Aether
                "aether_legacy:slider",
                "aether_legacy:valkyrie_queen",
                "aether_legacy:sun_spirit",

                // Fish's Undead Rising
                "mod_lavacow:skeletonking",
                "mod_lavacow:sludgelord",
                "mod_lavacow:avaton",
                "mod_lavacow:forsaken",

                // Scaling Health & Player Bosses
                "scalinghealth:blight_fire",
                "playerbosses:player_boss",

                // MosonTweaker Bosses & Special Entities
                "mosontweaker:paladin",
                "mosontweaker:zeus",
                "mosontweaker:dr_stalker",
                "mosontweaker:undead_master",
                "mosontweaker:illager_king",
                "mosontweaker:mad_scientist",
                "mosontweaker:paladin_axe_attack",
                "mosontweaker:zeus_boulder",
                "mosontweaker:zeus_lightning_disc",
                "mosontweaker:zeus_lightning_arrow",
                "mosontweaker:zeus_cyclone_bolt",
                "mosontweaker:zeus_vortex",
                "mosontweaker:zeus_thundering_chain",
                "mosontweaker:zeus_stormcloud",
                "mosontweaker:zeus_sigil",
                "mosontweaker:zeus_fire_ring",
                "mosontweaker:zeus_quake_wave",
                "mosontweaker:zeus_sonic_wave",
                "mosontweaker:zeus_tesla_beam",
                "mosontweaker:zeus_lightning_spawner",
                "mosontweaker:zeus_magic_lightning",
                "mosontweaker:zeus_falling_block_spawner",
                "mosontweaker:zeus_ring",

                // Mowzie's Mobs & Twilight Forest (future-proof)
                "mowziesmobs:ferrous_wroughtnaut",
                "mowziesmobs:frostmaw",
                "mowziesmobs:barako",
                "twilightforest:naga",
                "twilightforest:lich",
                "twilightforest:hydra",
                "twilightforest:ur_ghast",
                "twilightforest:minoshroom",
                "twilightforest:snow_queen"
        };

        @Config.Comment("List of mod namespaces whose entities should NEVER be throttled.")
        public String[] namespaceBlacklist = new String[]{
                "valkyrienskies",
                "astikorcarts",
                "cfm",
                "sit",
                "comforts"
        };

        @Config.Comment("Dimension IDs where entity throttling is completely disabled.")
        public int[] dimensionBlacklist = new int[0];
    }

    private static final Set<String> cachedBlacklistedEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> cachedBlacklistedNamespaces = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<Integer> cachedBlacklistedDimensions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final java.util.Map<Class<?>, Boolean> HAS_BOSS_BAR_CACHE = new ConcurrentHashMap<>();
    private static final java.util.Map<Class<?>, Boolean> CLASS_BLACKLIST_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean cacheInitialized = false;

    public static void initCache() {
        cachedBlacklistedEntities.clear();
        for (String s : FILTERING.entityBlacklist) {
            if (s != null && !s.trim().isEmpty()) {
                cachedBlacklistedEntities.add(s.trim().toLowerCase());
            }
        }

        cachedBlacklistedNamespaces.clear();
        for (String s : FILTERING.namespaceBlacklist) {
            if (s != null && !s.trim().isEmpty()) {
                cachedBlacklistedNamespaces.add(s.trim().toLowerCase());
            }
        }

        cachedBlacklistedDimensions.clear();
        for (int dim : FILTERING.dimensionBlacklist) {
            cachedBlacklistedDimensions.add(dim);
        }

        HAS_BOSS_BAR_CACHE.clear();
        CLASS_BLACKLIST_CACHE.clear();
        cacheInitialized = true;
    }

    public static boolean isDimensionBlacklisted(int dimId) {
        if (!cacheInitialized) initCache();
        return cachedBlacklistedDimensions.contains(dimId);
    }

    public static boolean hasBossHealthBar(Entity entity) {
        if (!(entity instanceof net.minecraft.entity.EntityLivingBase)) return false;
        net.minecraft.entity.EntityLivingBase living = (net.minecraft.entity.EntityLivingBase) entity;

        // Standard Minecraft/Forge boss check (bosses override isNonBoss() to false)
        if (!living.isNonBoss()) {
            return true;
        }

        // Fast cached inspection for any BossInfo/BossInfoServer field in the class hierarchy
        return HAS_BOSS_BAR_CACHE.computeIfAbsent(living.getClass(), clazz -> {
            Class<?> current = clazz;
            while (current != null && current != Entity.class && current != Object.class) {
                for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                    if (net.minecraft.world.BossInfo.class.isAssignableFrom(field.getType())) {
                        return true;
                    }
                }
                current = current.getSuperclass();
            }
            return false;
        });
    }

    public static boolean isSpecialOrElite(Entity entity) {
        if (!(entity instanceof net.minecraft.entity.EntityLivingBase)) {
            return false;
        }
        net.minecraft.entity.EntityLivingBase living = (net.minecraft.entity.EntityLivingBase) entity;

        // Universal exemption for any mob with a boss health bar
        if (hasBossHealthBar(living)) {
            return true;
        }

        net.minecraft.nbt.NBTTagCompound data = living.getEntityData();
        if (data != null) {
            // ScalingHealth Blight / Infernal Mobs / Champions
            if (data.getBoolean("ScalingHealth.IsBlight")) return true;
            if (data.hasKey("InfernalMobsMod") || data.hasKey("infernalmobs")) return true;
            if (data.hasKey("champions")) return true;
        }

        return false;
    }

    /**
     * Instance-independent blacklist checks (type hierarchy, registry name/namespace, Lycanites boss
     * interface), cached per entity class. An entity's class never changes, so this avoids repeating
     * registry lookups and interface walks for every entity on every priority refresh.
     */
    public static boolean isClassBlacklisted(Class<?> clazz) {
        if (!cacheInitialized) initCache();
        Boolean cached = CLASS_BLACKLIST_CACHE.get(clazz);
        if (cached != null) return cached;
        boolean result = computeClassBlacklisted(clazz);
        CLASS_BLACKLIST_CACHE.put(clazz, result);
        return result;
    }

    private static boolean computeClassBlacklisted(Class<?> clazz) {
        // Hardcoded safety rules for critical game mechanics
        if (EntityPlayer.class.isAssignableFrom(clazz)) return true;
        if (IProjectile.class.isAssignableFrom(clazz)
                || EntityArrow.class.isAssignableFrom(clazz)
                || EntityThrowable.class.isAssignableFrom(clazz)
                || EntityFireball.class.isAssignableFrom(clazz)
                || EntityFishHook.class.isAssignableFrom(clazz)) {
            return true;
        }
        if (IEntityMultiPart.class.isAssignableFrom(clazz)
                || EntityDragon.class.isAssignableFrom(clazz)
                || EntityWither.class.isAssignableFrom(clazz)) {
            return true;
        }
        if (EntityWeatherEffect.class.isAssignableFrom(clazz)
                || EntityFallingBlock.class.isAssignableFrom(clazz)
                || EntityTNTPrimed.class.isAssignableFrom(clazz)) {
            return true;
        }

        // Lycanites Mobs boss interface (class hierarchy traversal)
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Class<?> iface : c.getInterfaces()) {
                if ("com.lycanitesmobs.api.IGroupBoss".equals(iface.getName())) {
                    return true;
                }
            }
        }

        // Registry name / namespace blacklists
        EntityEntry entry = EntityRegistry.getEntry((Class<? extends Entity>) clazz);
        if (entry != null && entry.getRegistryName() != null) {
            ResourceLocation key = entry.getRegistryName();
            if (cachedBlacklistedEntities.contains(key.toString().toLowerCase())) return true;
            if (cachedBlacklistedNamespaces.contains(key.getNamespace().toLowerCase())) return true;
        }

        return false;
    }

    public static boolean isEntityBlacklisted(Entity entity) {
        if (entity == null) return true;

        // Never throttle entities ridden by or riding players (changes every tick, so not cached).
        if (entity.isRiding()) return true;
        if (entity.isBeingRidden()) {
            for (Entity passenger : entity.getRecursivePassengers()) {
                if (passenger instanceof EntityPlayer) {
                    return true;
                }
            }
        }

        // Class-deterministic checks, cached per class.
        if (isClassBlacklisted(entity.getClass())) return true;

        // Per-instance elite / boss checks (Blights, Infernal Mobs, Champions, boss health bar).
        return isSpecialOrElite(entity);
    }

    @Mod.EventBusSubscriber(modid = "entitycadence")
    public static class ConfigSyncHandler {
        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals("entitycadence")) {
                ConfigManager.sync("entitycadence", Config.Type.INSTANCE);
                initCache();
            }
        }
    }
}
