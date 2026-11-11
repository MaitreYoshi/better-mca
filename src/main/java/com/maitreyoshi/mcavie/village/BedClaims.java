package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.compat.ServerTickEvents;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.server.world.data.VillageManager;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lits attribués aux PNJ. Le « Définir le lit » de MCA prend n'importe quel lit libre à 8 blocs du joueur
 * (pas forcément celui à côté duquel il se tient), puis réserve le premier lit libre à 1 bloc de celui-ci :
 * avec deux lits côte à côte, la réservation tombe parfois sur le lit voisin. Le lit retenu reste alors libre
 * et un autre PNJ le prend, pendant que le lit voisin est bloqué. Ici : le lit choisi est le plus proche du
 * joueur, sa réservation est exacte, et un passage régulier rend chaque lit à un seul PNJ (priorité au lit
 * attribué par le joueur) et rétablit la réservation d'un lit mémorisé mais resté libre.
 */
public final class BedClaims {
    private static final int RADIUS = 8;
    private static final int CHECK_INTERVAL = 100;

    private BedClaims() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(BedClaims::tick);
    }

    /** Remplace Residency.setHome de MCA (mêmes messages). */
    public static void setHome(VillagerEntityMCA villager, ServerPlayerEntity player) {
        if (!villager.requiresHome()) {
            villager.sendChatMessage(player, "interaction.sethome.temporary");
            return;
        }
        ServerWorld world = (ServerWorld) player.getWorld();
        VillageManager.get(world).processBuilding(player.getBlockPos(), true, false);
        villager.getResidency().seekHome();

        PointOfInterestStorage poi = world.getPointOfInterestStorage();
        BlockPos origin = player.getBlockPos();
        List<BlockPos> beds = new ArrayList<>(poi.getPositions(e -> e.matchesKey(PointOfInterestTypes.HOME), p -> true,
                origin, RADIUS, PointOfInterestStorage.OccupationStatus.ANY).toList());
        beds.sort(Comparator.comparingDouble(p -> distance(world, p, origin)));

        GlobalPos current = villager.getBrain().getOptionalMemory(MemoryModuleType.HOME).orElse(null);
        for (BlockPos bed : beds) {
            GlobalPos target = GlobalPos.create(world.getRegistryKey(), bed);
            if (target.equals(current)) {
                claim(villager, player, target, false);
                return;
            }
            if (hasSpace(poi, bed)) {
                claim(villager, player, target, true);
                return;
            }
            // Lit déjà réservé : repris seulement à un PNJ qui ne l'a pas reçu du joueur.
            List<VillagerEntityMCA> owners = owners(world, target, villager);
            if (owners.stream().noneMatch(BedClaims::forced)) {
                owners.forEach(BedClaims::evict);
                claim(villager, player, target, false);
                return;
            }
        }

        villager.getBrain().forget(MemoryModuleTypeMCA.FORCED_HOME.get());
        villager.getResidency().getHomeVillage().flatMap(v -> v.getBuildingAt(villager.getBlockPos()))
                .filter(b -> b.getBuildingType().noBeds())
                .ifPresentOrElse(b -> villager.sendChatMessage(player, "interaction.sethome.bedfail." + b.getBuildingType().name()),
                        () -> villager.sendChatMessage(player, "interaction.sethome.bedfail"));
    }

    private static void claim(VillagerEntityMCA villager, ServerPlayerEntity player, GlobalPos target, boolean reserve) {
        villager.sendChatMessage(player, "interaction.sethome.success");
        GlobalPos current = villager.getBrain().getOptionalMemory(MemoryModuleType.HOME).orElse(null);
        if (current != null && !current.equals(target)) {
            villager.releaseTicketFor(MemoryModuleType.HOME);
            villager.getBrain().forget(MemoryModuleType.HOME);
        }
        if (reserve) {
            reserve((ServerWorld) player.getWorld(), target.getPos());
        }
        villager.getBrain().remember(MemoryModuleType.HOME, target);
        villager.getBrain().remember(MemoryModuleTypeMCA.FORCED_HOME.get(), true);
        villager.getResidency().seekHome();
    }

    /** Un lit = un PNJ ; un lit mémorisé reste réservé. */
    private static void tick(ServerWorld world) {
        if (world.getTime() % CHECK_INTERVAL != 0) {
            return;
        }
        Map<GlobalPos, List<VillagerEntityMCA>> byBed = new HashMap<>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof VillagerEntityMCA villager && villager.isAlive()) {
                villager.getBrain().getOptionalMemory(MemoryModuleType.HOME)
                        .filter(h -> h.getDimension() == world.getRegistryKey())
                        .ifPresent(h -> byBed.computeIfAbsent(h, k -> new ArrayList<>()).add(villager));
            }
        }
        PointOfInterestStorage poi = world.getPointOfInterestStorage();
        byBed.forEach((bed, villagers) -> {
            if (villagers.size() > 1) {
                VillagerEntityMCA keeper = villagers.stream().filter(BedClaims::forced).findFirst().orElse(villagers.get(0));
                villagers.stream().filter(v -> v != keeper).forEach(BedClaims::evict);
            }
            BlockPos pos = bed.getPos();
            if (world.isChunkLoaded(pos) && hasSpace(poi, pos)) {
                reserve(world, pos);
            }
        });
    }

    private static boolean forced(VillagerEntityMCA villager) {
        Optional<Boolean> forced = villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.FORCED_HOME.get());
        //noinspection OptionalAssignedToNull
        return forced != null && forced.isPresent();
    }

    /** Oublie le lit sans rendre sa réservation (elle appartient au PNJ qui le garde). */
    private static void evict(VillagerEntityMCA villager) {
        villager.getBrain().forget(MemoryModuleType.HOME);
        villager.getBrain().forget(MemoryModuleTypeMCA.FORCED_HOME.get());
    }

    private static List<VillagerEntityMCA> owners(ServerWorld world, GlobalPos bed, VillagerEntityMCA except) {
        List<VillagerEntityMCA> owners = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof VillagerEntityMCA other && other != except
                    && other.getBrain().getOptionalMemory(MemoryModuleType.HOME).filter(bed::equals).isPresent()) {
                owners.add(other);
            }
        }
        return owners;
    }

    private static boolean hasSpace(PointOfInterestStorage poi, BlockPos pos) {
        return poi.getPositions(e -> e.matchesKey(PointOfInterestTypes.HOME), pos::equals, pos, 1,
                PointOfInterestStorage.OccupationStatus.HAS_SPACE).findAny().isPresent();
    }

    private static void reserve(ServerWorld world, BlockPos pos) {
        world.getPointOfInterestStorage().getPosition(e -> e.matchesKey(PointOfInterestTypes.HOME), (e, p) -> p.equals(pos), pos, 1);
    }

    /** Distance du joueur au lit, tête ou pied. */
    private static double distance(ServerWorld world, BlockPos head, BlockPos origin) {
        double d = head.getSquaredDistance(origin);
        BlockState state = world.getBlockState(head);
        if (state.getBlock() instanceof BedBlock) {
            BlockPos foot = head.offset(state.get(BedBlock.FACING).getOpposite());
            d = Math.min(d, foot.getSquaredDistance(origin));
        }
        return d;
    }
}
