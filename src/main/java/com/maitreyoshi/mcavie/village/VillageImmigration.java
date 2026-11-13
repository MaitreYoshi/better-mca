package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.SurnameRegistry;
import com.maitreyoshi.mcavie.Surnames;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.Orientation;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class VillageImmigration {
    public static final int MAX_PENDING = 3;

    private VillageImmigration() {
    }

    public static void weekly(Village village, VillageState state, Prestige.Result result) {
        if (!Prestige.hasActive(result, village, "inn")) {
            return;
        }
        state.immigrationProgress += VillageConfig.get().familiesPerWeek.get(state.rank.ordinal());
        while (state.immigrationProgress >= 1) {
            state.immigrationProgress -= 1;
            addFamily(state);
        }
    }

    public static void addFamily(VillageState state) {
        state.pendingFamilies = Math.min(MAX_PENDING, state.pendingFamilies + 1);
    }

    public static boolean hasRoom(Village village, VillageState state) {
        int free = village.getMaxPopulation() - village.getPopulation() - state.pendingFamilies * VillageConfig.get().familyMinSize;
        return state.pendingFamilies < MAX_PENDING && free >= VillageConfig.get().familyMinSize;
    }

    public static boolean trySpawn(Village village, ServerLevel world, VillageState state, Prestige.Result result) {
        long today = WorldCalendar.now(world).dayIndex();
        if (state.pendingFamilies <= 0 || state.lastArrivalDay == today
                || world.players().stream().noneMatch(village::isWithinBorder)) {
            return false;
        }
        Optional<Building> inn = village.getBuildingsOfType("inn").filter(b -> {
            Prestige.Score score = result.score(b);
            return score != null && score.isActive();
        }).findAny();
        if (inn.isEmpty()) {
            return false;
        }
        int freeBeds = village.getMaxPopulation() - village.getPopulation();
        VillageConfig config = VillageConfig.get();
        if (freeBeds < config.familyMinSize) {
            return false;
        }
        BlockPos spot = findSpot(world, inn.get());
        if (spot == null) {
            return false;
        }

        RandomSource random = world.getRandom();
        int size = config.familyMinSize + random.nextInt(Math.max(1, config.familyMaxSize - config.familyMinSize + 1));
        size = Math.min(size, freeBeds);

        VillagerEntityMCA father = VillagerFactory.newVillager(world).withGender(Gender.MALE).withAge(0).withPosition(spot).spawn(EntitySpawnReason.EVENT);
        VillagerEntityMCA mother = VillagerFactory.newVillager(world).withGender(Gender.FEMALE).withAge(0).withPosition(spot).spawn(EntitySpawnReason.EVENT);
        List<VillagerEntityMCA> children = new ArrayList<>();
        for (int i = 2; i < size; i++) {
            int age = -(int) (AgeState.getMaxAge() * (0.1 + 0.6 * random.nextFloat()));
            children.add(VillagerFactory.newVillager(world).withAge(age).withPosition(spot).spawn(EntitySpawnReason.EVENT));
        }

        if (!Orientation.compatible(father, mother)) {
            Orientation.apply(father.getTraits(), Orientation.STRAIGHT);
            Orientation.apply(mother.getTraits(), Orientation.STRAIGHT);
        }
        world.getServer().execute(() -> {
            father.getRelationships().marry(mother);
            mother.getRelationships().marry(father);
            SurnameRegistry registry = SurnameRegistry.get(world.getServer());
            String surname = registry.get(father.getUUID());
            if (surname == null) {
                surname = Surnames.randomSurnameFor(father);
                registry.set(father.getUUID(), surname);
            }
            for (VillagerEntityMCA child : children) {
                child.getRelationships().getFamilyEntry().replaceParents(father.getRelationships(), mother.getRelationships());
                registry.set(child.getUUID(), surname);
            }
        });

        state.pendingFamilies--;
        state.lastArrivalDay = today;
        VillageServer.notify(world, village, Component.translatable("msg.mca_vie.immigration.arrived", size, village.getName()).withStyle(ChatFormatting.GREEN));
        return true;
    }

    private static BlockPos findSpot(ServerLevel world, Building inn) {
        List<BlockPos> candidates = new ArrayList<>(inn.getBlocks().values().stream().flatMap(Collection::stream).toList());
        candidates.add(inn.getCenter());
        Collections.shuffle(candidates);
        for (BlockPos base : candidates) {
            if (!world.isPositionEntityTicking(base)) {
                continue;
            }
            for (BlockPos p : new BlockPos[]{base.above(), base.north(), base.south(), base.east(), base.west()}) {
                if (world.getBlockState(p).getCollisionShape(world, p).isEmpty()
                        && world.getBlockState(p.above()).getCollisionShape(world, p.above()).isEmpty()
                        && !world.getBlockState(p.below()).getCollisionShape(world, p.below()).isEmpty()) {
                    return p;
                }
            }
        }
        return null;
    }
}
