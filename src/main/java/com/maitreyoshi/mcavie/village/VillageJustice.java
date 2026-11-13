package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.life.Wellbeing;
import com.maitreyoshi.mcavie.life.WellbeingRegistry;
import com.maitreyoshi.mcavie.social.Social;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.MoveState;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class VillageJustice {
    private static final java.util.Map<Long, BlockPos> CELLS = new java.util.HashMap<>();
    public static final int PRISON = 0;
    public static final int PARDON = 1;

    private VillageJustice() {
    }

    public static boolean isImprisoned(VillagerEntityMCA villager) {
        return VillageDecrees.residentVillage(villager).map(v -> VillageState.of(v).prisoners.containsKey(villager.getUUID())).orElse(false);
    }

    public static boolean isImprisoned(Village village, UUID uuid) {
        return VillageState.of(village).prisoners.containsKey(uuid);
    }

    public static Optional<Building> prison(Village village, Prestige.Result result) {
        return village.getBuildingsOfType("prison").filter(b -> {
            Prestige.Score score = result.score(b);
            return score != null && score.isActive();
        }).findFirst();
    }

    public static void daily(Village village, ServerLevel world, VillageState state, Prestige.Result result, long today) {
        VillageConfig config = VillageConfig.get();
        boolean hasPrison = prison(village, result).isPresent();
        state.cases.removeIf(c -> !village.hasResident(c.suspect));

        for (Iterator<VillageState.Case> it = state.cases.iterator(); it.hasNext(); ) {
            VillageState.Case c = it.next();
            if (today - c.day >= config.judgmentDays) {
                it.remove();
                sentence(village, world, state, c, hasPrison ? PRISON : PARDON, today);
            }
        }
        for (Iterator<Map.Entry<UUID, VillageState.Prisoner>> it = state.prisoners.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, VillageState.Prisoner> entry = it.next();
            if (today >= entry.getValue().releaseDay || !hasPrison || !village.hasResident(entry.getKey())) {
                it.remove();
                state.pendingRelease.add(entry.getKey());
                VillageServer.notify(world, village, Component.translatable("msg.mca_vie.justice.released", entry.getValue().name).withStyle(ChatFormatting.GRAY));
            }
        }

        if (!state.economyActive || state.treasury <= 0) {
            return;
        }
        WellbeingRegistry wellbeing = WellbeingRegistry.get(world.getServer());
        int guards = 0;
        List<UUID> residents = village.getResidentsUUIDs().toList();
        for (UUID uuid : residents) {
            if (world.getEntity(uuid) instanceof VillagerEntityMCA v && v.isGuard()) {
                guards++;
            }
        }
        double chance = config.crimeChance * (hasPrison ? config.prisonCrimeMultiplier : 1);
        for (UUID uuid : residents) {
            if (state.treasury <= 0) {
                break;
            }
            WellbeingRegistry.Record record = wellbeing.get(uuid);
            if (record == null || record.happiness >= config.crimeHappiness || state.prisoners.containsKey(uuid)
                    || state.cases.stream().anyMatch(c -> c.suspect.equals(uuid)) || world.random.nextDouble() >= chance) {
                continue;
            }
            if (!(world.getEntity(uuid) instanceof VillagerEntityMCA thief) || thief.isGuard() || thief.isBaby()) {
                continue;
            }
            long amount = Math.min(state.treasury, 1 + world.random.nextInt(Math.max(1, config.theftMax)));
            state.treasury -= amount;
            if (world.random.nextDouble() < Math.min(config.arrestMaxChance, guards * config.arrestChancePerGuard)) {
                state.treasury += amount;
                VillageState.Case c = new VillageState.Case();
                c.suspect = uuid;
                c.name = name(thief);
                c.day = today;
                c.amount = amount;
                state.cases.add(c);
                Social.theft(village, world, uuid);
                VillageServer.notify(world, village, Component.translatable("msg.mca_vie.justice.arrested", village.getName(), c.name, amount).withStyle(ChatFormatting.YELLOW));
            } else {
                VillageServer.notify(world, village, Component.translatable("msg.mca_vie.justice.stolen", village.getName(), amount).withStyle(ChatFormatting.RED));
            }
        }
    }

    public static void devCase(Village village, ServerLevel world) {
        VillageState state = VillageState.of(village);
        for (UUID uuid : village.getResidentsUUIDs().toList()) {
            if (world.getEntity(uuid) instanceof VillagerEntityMCA villager && !villager.isGuard() && !villager.isBaby()
                    && !state.prisoners.containsKey(uuid) && state.cases.stream().noneMatch(c -> c.suspect.equals(uuid))) {
                VillageState.Case c = new VillageState.Case();
                c.suspect = uuid;
                c.name = name(villager);
                c.day = com.maitreyoshi.mcavie.calendar.WorldCalendar.now(world).dayIndex();
                c.amount = 3;
                state.cases.add(c);
                Social.theft(village, world, uuid);
                village.markDirty();
                VillageServer.notify(world, village, Component.translatable("msg.mca_vie.justice.arrested", village.getName(), c.name, c.amount).withStyle(ChatFormatting.YELLOW));
                return;
            }
        }
    }

    public static void tick(Village village, ServerLevel world, VillageState state, Prestige.Result result) {
        Optional<Building> prison = prison(village, result);
        if (prison.isPresent() != state.prisonActive) {
            state.prisonActive = prison.isPresent();
            village.markDirty();
        }
        if (!state.pendingRelease.isEmpty() && state.pendingRelease.removeIf(uuid -> release(world, uuid))) {
            village.markDirty();
        }
        if (state.prisoners.isEmpty()) {
            return;
        }
        if (prison.isEmpty()) {
            state.pendingRelease.addAll(state.prisoners.keySet());
            state.prisoners.clear();
            village.markDirty();
            return;
        }
        for (UUID uuid : state.prisoners.keySet()) {
            if (world.getEntity(uuid) instanceof VillagerEntityMCA villager) {
                confine(world, villager, prison.get());
            }
        }
    }

    private static void confine(ServerLevel world, VillagerEntityMCA villager, Building prison) {
        if (villager.isPassenger()) {
            villager.stopRiding();
        }
        if (!prison.containsPos(villager.blockPosition())) {
            BlockPos cell = cell(world, prison);
            if (cell == null) {
                return;
            }
            if (villager.isSleeping()) {
                villager.stopSleeping();
            }
            villager.teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
        }
        if (villager.getVillagerBrain().getMoveState() != MoveState.STAY) {
            villager.getVillagerBrain().setMoveState(MoveState.STAY, null);
        }
    }

    private static BlockPos cell(ServerLevel world, Building prison) {
        long key = prison.getCenter().asLong();
        BlockPos cached = CELLS.get(key);
        if (cached != null && prison.containsPos(cached) && isStandable(world, cached) && nextToBed(world, prison, cached)) {
            return cached;
        }
        CELLS.remove(key);
        for (List<BlockPos> positions : prison.getBlocks().values()) {
            for (BlockPos pos : positions) {
                if (isLoaded(world, pos) && world.getBlockState(pos).getBlock() instanceof BedBlock) {
                    for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        BlockPos floor = pos.relative(direction);
                        if (prison.containsPos(floor) && isStandable(world, floor)) {
                            CELLS.put(key, floor);
                            return floor;
                        }
                    }
                }
            }
        }
        BlockPos p0 = prison.getPos0();
        BlockPos p1 = prison.getPos1();
        for (BlockPos pos : BlockPos.betweenClosed(Math.min(p0.getX(), p1.getX()), Math.min(p0.getY(), p1.getY()), Math.min(p0.getZ(), p1.getZ()),
                Math.max(p0.getX(), p1.getX()), Math.max(p0.getY(), p1.getY()), Math.max(p0.getZ(), p1.getZ()))) {
            if (isStandable(world, pos)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static boolean nextToBed(ServerLevel world, Building prison, BlockPos floor) {
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos bed = floor.relative(direction);
            if (prison.containsPos(bed) && isLoaded(world, bed) && world.getBlockState(bed).getBlock() instanceof BedBlock) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLoaded(ServerLevel world, BlockPos pos) {
        return world.hasChunk(net.minecraft.core.SectionPos.blockToSectionCoord(pos.getX()), net.minecraft.core.SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private static boolean isStandable(ServerLevel world, BlockPos pos) {
        return isLoaded(world, pos)
                && world.getBlockState(pos.below()).isRedstoneConductor(world, pos.below())
                && world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
                && world.getBlockState(pos.above()).getCollisionShape(world, pos.above()).isEmpty();
    }

    private static boolean release(ServerLevel world, UUID uuid) {
        for (ServerLevel level : world.getServer().getAllLevels()) {
            if (level.getEntity(uuid) instanceof VillagerEntityMCA villager) {
                if (villager.getVillagerBrain().getMoveState() == MoveState.STAY) {
                    villager.getVillagerBrain().setMoveState(MoveState.MOVE, null);
                }
                return true;
            }
        }
        return false;
    }

    public static void judge(ServerPlayer player, int villageId, UUID suspect, int verdict) {
        VillageManager.get(player.level()).getOrEmpty(villageId).ifPresent(village -> {
            if (!VillageRights.personalRank(village, player).isAtLeast(VillageRights.JUSTICE)) {
                player.sendOverlayMessage(Component.translatable("msg.mca_vie.rights.denied", Component.translatable(VillageRights.JUSTICE.getTranslationKey())));
                return;
            }
            VillageState state = VillageState.of(village);
            state.cases.removeIf(c -> !village.hasResident(c.suspect));
            Optional<VillageState.Case> found = state.cases.stream().filter(c -> c.suspect.equals(suspect)).findFirst();
            if (found.isEmpty() || (verdict != PRISON && verdict != PARDON)) {
                return;
            }
            ServerLevel world = player.level();
            if (verdict == PRISON && !state.prisonActive) {
                player.sendOverlayMessage(Component.translatable("msg.mca_vie.justice.no_prison"));
                return;
            }
            state.cases.remove(found.get());
            sentence(village, world, state, found.get(), verdict, com.maitreyoshi.mcavie.calendar.WorldCalendar.now(world).dayIndex());
            village.markDirty();
        });
    }

    private static void sentence(Village village, ServerLevel world, VillageState state, VillageState.Case c, int verdict, long today) {
        VillageConfig config = VillageConfig.get();
        if (verdict == PRISON) {
            VillageState.Prisoner prisoner = new VillageState.Prisoner();
            prisoner.name = c.name;
            prisoner.releaseDay = today + config.prisonDays;
            state.prisoners.put(c.suspect, prisoner);
            Wellbeing.addHappiness(world, c.suspect, -config.prisonHappiness);
            state.addRegistry(new CivilRegistry.Entry(CivilRegistry.JUSTICE, today, c.name, "", config.prisonDays));
            VillageServer.notify(world, village, Component.translatable("msg.mca_vie.justice.prison", c.name, config.prisonDays).withStyle(ChatFormatting.GOLD));
        } else {
            Wellbeing.addHappiness(world, c.suspect, config.pardonHappiness);
            state.addRegistry(new CivilRegistry.Entry(CivilRegistry.JUSTICE, today, c.name, "", 0));
            VillageServer.notify(world, village, Component.translatable("msg.mca_vie.justice.pardon", c.name).withStyle(ChatFormatting.GREEN));
        }
    }

    private static String name(VillagerEntityMCA villager) {
        String name = villager.getTrackedValue(VillagerLike.VILLAGER_NAME);
        return name == null || name.isBlank() ? villager.getName().getString() : name;
    }
}
