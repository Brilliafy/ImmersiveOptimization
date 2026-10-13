package net.conczin.immersive_optimization;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.conczin.immersive_optimization.config.ModConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entity tick throttling for Cleanroom 1.12.2.
 *
 * <p><b>Why this reworks the original port:</b> the original 1.12.2 port ran a background thread that
 * iterated {@code world.loadedEntityList} and read live entity/client state (positions, bounding boxes,
 * NBT, and the client camera frustum) from off the main thread. That is a data race, and worse, its
 * only output ({@code priorities}) was never consumed - no mixin called {@code shouldTick(entity)} - so
 * it was pure wasted CPU. Meanwhile the actual AI throttle recomputed player-distance and FOV for every
 * mob every tick, twice.</p>
 *
 * <p>This version computes the priority table <b>on the main thread</b>, amortised over a configurable
 * tick interval, stores it, and has the AI mixin consult the cached priority. Safety overrides that
 * must react instantly (airborne, hurt, targeting, swelling creeper, ridden) are still evaluated fresh
 * every tick, so combat/responsiveness is unaffected. There is no background thread any more, so the
 * cross-thread hazards are gone.</p>
 */
public class TickScheduler {
    public static final TickScheduler INSTANCE = new TickScheduler();

    public static final int MAX_STRESS_TICKS = 600;

    public volatile FrustumProxy frustum;

    public interface FrustumProxy {
        boolean isBoundingBoxInFrustum(AxisAlignedBB aabb);
    }

    public static class Stats {
        public double tickRate = 0;
        public int entities = 0;
        public int distanceCulledEntities = 0;
        public int trackingCulledEntities = 0;
        public int viewportCulledEntities = 0;
        public int combatProtectedEntities = 0;
        public int itemEntitiesCulled = 0;
        public final Map<String, Integer> topEntityCounts = new HashMap<>();

        public void reset() {
            tickRate = 0;
            entities = 0;
            distanceCulledEntities = 0;
            trackingCulledEntities = 0;
            viewportCulledEntities = 0;
            combatProtectedEntities = 0;
            itemEntitiesCulled = 0;
            topEntityCounts.clear();
        }
    }

    public static class LevelData {
        public final int dimensionId;
        public volatile boolean active = true;

        public long tick = 0;
        public int stressedTicks = 0;
        public int lifeTimeStressedTicks = 0;

        public Stats stats = new Stats();
        public Stats previousStats = new Stats();

        public volatile Int2IntOpenHashMap priorities = new Int2IntOpenHashMap();
        public volatile Set<ChunkPos> forcedChunks = Collections.emptySet();

        public LevelData(int dimensionId) {
            this.dimensionId = dimensionId;
            this.active = !ModConfig.isDimensionBlacklisted(dimensionId);
            this.priorities.defaultReturnValue(1);
        }

        public String toLog() {
            int total = previousStats.entities;
            if (total == 0) return "Dim " + dimensionId + ": 0 entities";
            double ratePct = (previousStats.tickRate / total) * 100.0;
            double savedPct = Math.max(0.0, 100.0 - ratePct);
            return String.format(Locale.ROOT,
                    "Dim %d: Rate %.1f%% (-%.1f%% CPU), %d mobs, %d stress | culled %.1f%% dist, %.1f%% track, %.1f%% view | combat safe: %d",
                    dimensionId,
                    ratePct,
                    savedPct,
                    total,
                    stressedTicks,
                    ((float) previousStats.distanceCulledEntities / total) * 100.0f,
                    ((float) previousStats.trackingCulledEntities / total) * 100.0f,
                    ((float) previousStats.viewportCulledEntities / total) * 100.0f,
                    previousStats.combatProtectedEntities
            );
        }
    }

    public final Map<Integer, LevelData> levelData = new ConcurrentHashMap<>();

    public static void setServer(MinecraftServer server) {
        // No background thread any more; just start clean.
        INSTANCE.reset();
    }

    public static void stop() {
        INSTANCE.reset();
    }

    /** Kept for command/back-compat: true while throttling is enabled. */
    public static boolean isWorkerRunning() {
        return ModConfig.GENERAL.enableEntities;
    }

    public void reset() {
        levelData.clear();
        frustum = null;
    }

    public static int refreshIntervalTicks() {
        return Math.max(1, Math.max(50, ModConfig.TUNING.workerIntervalMs) / 50);
    }

    public void startLevelTick(World world) {
        if (world.isRemote) return;

        int dimId = world.provider.getDimension();
        LevelData data = levelData.computeIfAbsent(dimId, LevelData::new);

        // Re-evaluate on config reload instead of freezing the decision at construction.
        data.active = !ModConfig.isDimensionBlacklisted(dimId);
        if (!data.active) return;

        MinecraftServer server = world.getMinecraftServer();
        if (server != null) {
            double avgTickMs = getAverageTickMs(server);
            int threshold = ModConfig.TUNING.stressedThreshold;
            if (threshold > 0 && avgTickMs > threshold) {
                data.stressedTicks = Math.min(MAX_STRESS_TICKS, data.stressedTicks + 2);
                data.lifeTimeStressedTicks++;
            } else {
                data.stressedTicks = Math.max(0, data.stressedTicks - 1);
            }
        }

        data.tick++;

        // Amortised, main-thread priority + statistics refresh.
        if (data.tick % refreshIntervalTicks() == 0) {
            refreshPriorities(world, data);
        }
    }

    /**
     * Recomputes the priority table and statistics. Runs on the main thread, at most once every
     * {@link #refreshIntervalTicks()} ticks, so its cost is amortised and it may safely touch entity
     * state and the client frustum (integrated server shares the client thread in 1.12.2).
     */
    private void refreshPriorities(World world, LevelData data) {
        Stats prev = data.previousStats;
        data.previousStats = data.stats;
        data.stats = prev;
        data.stats.reset();

        Set<ChunkPos> forced = ModConfig.GENERAL.optimizeForceLoadedChunks
                ? Collections.emptySet()
                : ForgeChunkManager.getPersistentChunksFor(world).keySet();
        data.forcedChunks = forced;

        if (!ModConfig.GENERAL.enableEntities) {
            Int2IntOpenHashMap empty = new Int2IntOpenHashMap();
            empty.defaultReturnValue(1);
            data.priorities = empty;
            return;
        }

        List<Entity> loaded = world.loadedEntityList;
        int size = loaded.size();
        Int2IntOpenHashMap newPriorities = new Int2IntOpenHashMap(Math.max(16, size));
        newPriorities.defaultReturnValue(1);

        boolean collectTypes = size > 0;
        for (int i = 0; i < size; i++) {
            Entity entity = loaded.get(i);
            if (entity == null || entity.isDead) continue;

            int priority = getPriority(data, world, entity, forced);
            if (priority > 1) {
                newPriorities.put(entity.getEntityId(), priority);
            }

            data.stats.tickRate += 1.0 / Math.max(1, priority);
            data.stats.entities += 1;

            if (collectTypes) {
                ResourceLocation key = EntityList.getKey(entity);
                String name = key != null ? key.toString() : entity.getClass().getSimpleName();
                data.stats.topEntityCounts.merge(name, 1, Integer::sum);
            }
        }

        data.priorities = newPriorities;
    }

    /**
     * Decides whether an entity's AI should run this tick. Cheap, main-thread, called from the
     * {@code EntityLiving#updateEntityActionState} hook.
     */
    public boolean shouldRunAi(EntityLiving entity) {
        if (entity == null || entity.isDead || !entity.isEntityAlive()) return true;
        if (!ModConfig.GENERAL.enableEntities) return true;

        World world = entity.world;
        if (world == null || world.isRemote) return true;

        int dimId = world.provider.getDimension();
        if (ModConfig.isDimensionBlacklisted(dimId)) return true;

        // Fresh, cheap safety overrides that must react instantly and are never cached.
        if (entity.isRiding() || entity.isBeingRidden()) return true;
        if (!entity.onGround
                || entity.isElytraFlying()
                || Math.abs(entity.motionY) > 0.08
                || (entity.motionX * entity.motionX + entity.motionZ * entity.motionZ) > 0.02) {
            return true;
        }
        if (entity.hurtTime > 0 || entity.hurtResistantTime > 0) return true;
        if (entity.getRevengeTarget() != null) return true;
        if (entity.getAttackTarget() != null) return true;
        if (entity instanceof EntityCreeper && ((EntityCreeper) entity).getCreeperState() > 0) return true;

        LevelData data = levelData.get(dimId);
        if (data == null || !data.active) return true;

        List<EntityPlayer> players = world.playerEntities;
        if (players.isEmpty()) {
            // No players in this dimension: gentle idle scan instead of a hard throttle.
            return (world.getTotalWorldTime() + entity.getEntityId()) % 4 == 0;
        }

        int priority = data.priorities.get(entity.getEntityId());
        if (priority <= 1) return true;
        return (data.tick + entity.getEntityId()) % priority == 0;
    }

    /**
     * Whether a non-AI entity (dropped item, XP orb) should run its full {@code onUpdate} this tick.
     * Uses the same cached priority table; near-player entities have priority 1 and are never skipped.
     */
    public boolean shouldTick(Entity entity) {
        if (entity == null || entity.isDead) return true;
        if (!ModConfig.GENERAL.enableEntities) return true;

        World world = entity.world;
        if (world == null || world.isRemote) return true;

        int dimId = world.provider.getDimension();
        if (ModConfig.isDimensionBlacklisted(dimId)) return true;

        LevelData data = levelData.get(dimId);
        if (data == null || !data.active) return true;

        int priority = data.priorities.get(entity.getEntityId());
        if (priority <= 1) return true;
        return (data.tick + entity.getEntityId()) % priority == 0;
    }

    public static double getAverageTickMs(MinecraftServer server) {
        if (server == null || server.tickTimeArray == null || server.tickTimeArray.length == 0) return 0.0;
        long sum = 0;
        for (long t : server.tickTimeArray) {
            sum += t;
        }
        return ((double) sum / server.tickTimeArray.length) * 1.0E-6D;
    }

    /**
     * Computes the maximum tick interval for an entity (1 = every tick). Called only from
     * {@link #refreshPriorities} on the main thread.
     */
    public int getPriority(LevelData data, World world, Entity entity, Set<ChunkPos> forced) {
        if (ModConfig.isEntityBlacklisted(entity)) return 0;

        ChunkPos chunkPos = new ChunkPos(entity.chunkCoordX, entity.chunkCoordZ);
        if (forced.contains(chunkPos)) return 0;

        double minDistanceSq = Double.MAX_VALUE;
        List<EntityPlayer> players = world.playerEntities;
        int pCount = players.size();
        for (int i = 0; i < pCount; i++) {
            EntityPlayer player = players.get(i);
            if (player != null && !player.isDead) {
                double d = player.getDistanceSq(entity);
                if (d < minDistanceSq) {
                    minDistanceSq = d;
                }
            }
        }

        if (minDistanceSq == Double.MAX_VALUE) {
            // No players in this dimension.
            return ModConfig.TUNING.maxLevel;
        }

        int minDistance = ModConfig.TUNING.minDistance;
        if (minDistanceSq <= (double) (minDistance * minDistance)) {
            return 1;
        }

        if (ModConfig.GENERAL.enableItemThrottling) {
            if (entity instanceof EntityItem) {
                if (minDistanceSq <= 5.0 * 5.0) return 1;
                data.stats.itemEntitiesCulled++;
                return Math.min(ModConfig.TUNING.maxLevel, ModConfig.TUNING.itemDropPriority);
            }
            if (entity instanceof EntityXPOrb) {
                if (minDistanceSq <= 5.0 * 5.0) return 1;
                data.stats.itemEntitiesCulled++;
                return Math.min(4, ModConfig.TUNING.itemDropPriority);
            }
        }

        if (ModConfig.COMBAT.enableCombatProtection && entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.hurtTime > 0) {
                data.stats.combatProtectedEntities++;
                return 1;
            }
            if (living instanceof EntityLiving) {
                EntityLiving el = (EntityLiving) living;
                EntityLivingBase target = el.getAttackTarget();
                if (target instanceof EntityPlayer) {
                    data.stats.combatProtectedEntities++;
                    double combatDist = ModConfig.COMBAT.combatTargetDistance;
                    if (minDistanceSq <= combatDist * combatDist) {
                        return 1;
                    }
                    return Math.min(ModConfig.COMBAT.combatPriorityCap, ModConfig.TUNING.maxLevel);
                }
            }
        }

        if (entity instanceof EntityTameable) {
            EntityTameable tame = (EntityTameable) entity;
            if (tame.isTamed() && tame.isSitting()) {
                return ModConfig.TUNING.maxLevel;
            }
        }

        if (entity instanceof EntityLiving && ((EntityLiving) entity).isAIDisabled()) {
            return ModConfig.TUNING.maxLevel;
        }

        AxisAlignedBB box = entity.getEntityBoundingBox();
        int blocksPerLevel = ModConfig.TUNING.blocksPerLevel;
        MinecraftServer server = world.getMinecraftServer();
        boolean singlePlayer = server != null && !server.isDedicatedServer() && server.getCurrentPlayerCount() == 1;

        if (ModConfig.GENERAL.enableDistanceCulling && singlePlayer && !entity.isInRangeToRenderDist(minDistanceSq)) {
            blocksPerLevel = ModConfig.TUNING.blocksPerLevelDistanceCulled;
            data.stats.distanceCulledEntities++;
        }

        if (ModConfig.GENERAL.enableTrackingCulling && blocksPerLevel > ModConfig.TUNING.blocksPerLevelTrackingCulled) {
            if (minDistanceSq > 64.0 * 64.0) {
                blocksPerLevel = ModConfig.TUNING.blocksPerLevelTrackingCulled;
                data.stats.trackingCulledEntities++;
            }
        }

        FrustumProxy activeFrustum = frustum;
        if (ModConfig.GENERAL.enableViewportCulling
                && blocksPerLevel > ModConfig.TUNING.blocksPerLevelViewportCulled
                && singlePlayer
                && activeFrustum != null
                && box != null
                && !activeFrustum.isBoundingBoxInFrustum(box.grow(1.0))) {
            blocksPerLevel = ModConfig.TUNING.blocksPerLevelViewportCulled;
            data.stats.viewportCulledEntities++;
        }

        double antiStress = 1.0 - ((double) data.stressedTicks / MAX_STRESS_TICKS) * ModConfig.TUNING.minDecreaseFactor;
        int finalBlocksPerLevel = (int) (blocksPerLevel * Math.max(0.1, antiStress));
        double dist = Math.sqrt(minDistanceSq);
        int distanceLevel = (int) ((dist - minDistance) / Math.max(2, finalBlocksPerLevel));
        int priority = Math.min(ModConfig.TUNING.maxLevel, Math.max(1, distanceLevel + 1));

        if (ModConfig.COMBAT.enableMotionProtection && !entity.onGround && Math.abs(entity.motionY) > 0.25) {
            priority = Math.min(2, priority);
        }
        if (ModConfig.COMBAT.enableFireProtection && entity.isBurning()) {
            priority = Math.min(2, priority);
        }

        return priority;
    }
}
