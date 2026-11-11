package com.maitreyoshi.mcavie.path;

import com.maitreyoshi.mcavie.compat.ServerTickEvents;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.Building;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.entity.ai.brain.Activity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * PNJ qui ne trouve pas l'entrée de sa maison : quand le trajet vers son lit n'aboutit pas, il s'arrête au point
 * le plus proche du lit, souvent derrière le mur. Dans ce cas, on l'envoie d'abord à une porte de la maison
 * joignable depuis sa position ; une fois devant, MCA reprend le trajet vers le lit.
 */
public final class HomeEntrance {
    private static final int INTERVAL = 20;
    private static final int STUCK_TICKS = 60;
    private static final int COOLDOWN = 400;
    private static final int RANGE = 32;
    private static final int MAX_DOORS = 4;
    private static final Map<ServerWorld, Map<UUID, Long>> LAST = new WeakHashMap<>();

    private HomeEntrance() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(HomeEntrance::tick);
    }

    private static void tick(ServerWorld world) {
        long time = world.getTime();
        if (time % INTERVAL != 0) {
            return;
        }
        Map<UUID, Long> last = LAST.computeIfAbsent(world, w -> new java.util.HashMap<>());
        last.values().removeIf(t -> time - t > COOLDOWN);
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof VillagerEntityMCA villager && villager.isAlive() && !villager.isSleeping()
                    && !last.containsKey(villager.getUuid()) && detour(world, villager, time)) {
                last.put(villager.getUuid(), time);
            }
        }
    }

    private static boolean detour(ServerWorld world, VillagerEntityMCA villager, long time) {
        Brain<VillagerEntity> brain = villager.getBrain();
        if (!brain.hasActivity(Activity.REST)) {
            return false;
        }
        Optional<Long> stuck = brain.getOptionalMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        GlobalPos home = brain.getOptionalMemory(MemoryModuleType.HOME).orElse(null);
        if (stuck == null || stuck.isEmpty() || time - stuck.get() < STUCK_TICKS
                || home == null || home.getDimension() != world.getRegistryKey()) {
            return false;
        }
        BlockPos bed = home.getPos();
        BlockPos pos = villager.getBlockPos();
        if (!pos.isWithinDistance(bed, RANGE) || pos.isWithinDistance(bed, 2)) {
            return false;
        }
        Building building = villager.getResidency().getHomeVillage().flatMap(v -> v.getBuildingAt(bed)).orElse(null);
        if (building == null || building.containsPos(pos)) {
            return false;
        }
        List<BlockPos> doors = doors(world, building);
        doors.sort(Comparator.comparingDouble(d -> d.getSquaredDistance(pos)));
        for (BlockPos door : doors.subList(0, Math.min(MAX_DOORS, doors.size()))) {
            Path path = villager.getNavigation().findPathTo(door, 1);
            if (path != null && path.reachesTarget()) {
                brain.forget(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                brain.remember(MemoryModuleType.WALK_TARGET, new WalkTarget(door, 0.5f, 1));
                return true;
            }
        }
        return false;
    }

    /** Portes (moitié basse) et portillons sur le pourtour du bâtiment. */
    private static List<BlockPos> doors(ServerWorld world, Building building) {
        List<BlockPos> doors = new ArrayList<>();
        for (BlockPos p : BlockPos.iterate(building.getPos0().add(-1, -1, -1), building.getPos1().add(1, 1, 1))) {
            if (!world.isChunkLoaded(p)) {
                continue;
            }
            BlockState state = world.getBlockState(p);
            if ((state.getBlock() instanceof DoorBlock && state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                    || state.getBlock() instanceof FenceGateBlock) {
                doors.add(p.toImmutable());
            }
        }
        return doors;
    }
}
