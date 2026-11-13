package com.maitreyoshi.mcavie.path;

import com.maitreyoshi.mcavie.compat.ServerTickEvents;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
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
    private static final Map<ServerLevel, Map<UUID, Long>> LAST = new WeakHashMap<>();

    private HomeEntrance() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(HomeEntrance::tick);
    }

    private static void tick(ServerLevel world) {
        long time = world.getGameTime();
        if (time % INTERVAL != 0) {
            return;
        }
        Map<UUID, Long> last = LAST.computeIfAbsent(world, w -> new java.util.HashMap<>());
        last.values().removeIf(t -> time - t > COOLDOWN);
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof VillagerEntityMCA villager && villager.isAlive() && !villager.isSleeping()
                    && !last.containsKey(villager.getUUID()) && detour(world, villager, time)) {
                last.put(villager.getUUID(), time);
            }
        }
    }

    private static boolean detour(ServerLevel world, VillagerEntityMCA villager, long time) {
        Brain<Villager> brain = villager.getBrain();
        if (!brain.isActive(Activity.REST)) {
            return false;
        }
        Optional<Long> stuck = brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        GlobalPos home = brain.getMemoryInternal(MemoryModuleType.HOME).orElse(null);
        if (stuck == null || stuck.isEmpty() || time - stuck.get() < STUCK_TICKS
                || home == null || home.dimension() != world.dimension()) {
            return false;
        }
        BlockPos bed = home.pos();
        BlockPos pos = villager.blockPosition();
        if (!pos.closerThan(bed, RANGE) || pos.closerThan(bed, 2)) {
            return false;
        }
        Building building = villager.getResidency().getHomeVillage().flatMap(v -> v.getBuildingAt(bed)).orElse(null);
        if (building == null || building.containsPos(pos)) {
            return false;
        }
        List<BlockPos> doors = doors(world, building);
        doors.sort(Comparator.comparingDouble(d -> d.distSqr(pos)));
        for (BlockPos door : doors.subList(0, Math.min(MAX_DOORS, doors.size()))) {
            Path path = villager.getNavigation().createPath(door, 1);
            if (path != null && path.canReach()) {
                brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(door, 0.5f, 1));
                return true;
            }
        }
        return false;
    }

    /** Portes (moitié basse) et portillons sur le pourtour du bâtiment. */
    private static List<BlockPos> doors(ServerLevel world, Building building) {
        List<BlockPos> doors = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(building.getPos0().offset(-1, -1, -1), building.getPos1().offset(1, 1, 1))) {
            if (!world.hasChunkAt(p)) {
                continue;
            }
            BlockState state = world.getBlockState(p);
            if ((state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                    || state.getBlock() instanceof FenceGateBlock) {
                doors.add(p.immutable());
            }
        }
        return doors;
    }
}
