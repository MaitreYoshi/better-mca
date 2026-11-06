package com.maitreyoshi.mcavie.work.block;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.compat.Registration;
import dev.architectury.registry.CreativeTabRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.RegistryKeys;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class WorkBlocks {
    private static final Map<Job, WorkstationBlock> BY_JOB = new EnumMap<>(Job.class);

    public static BlockEntityType<WorkstationBlockEntity> WORKSTATION_ENTITY;

    private WorkBlocks() {
    }

    private static void register(String name, Job job, Block base, int light, double[][] shape) {
        Registration.register(RegistryKeys.BLOCK, name, () -> {
            Block.Settings settings = Block.Settings.copy(base).strength(2.5F).nonOpaque().luminance(state -> light);
            return new WorkstationBlock(settings, job, shape);
        }, block -> BY_JOB.put(job, block));
        Registration.register(RegistryKeys.ITEM, name, () -> new BlockItem(BY_JOB.get(job), new Item.Settings()), item -> {
        });
    }

    @Nullable
    public static WorkstationBlock forJob(Job job) {
        return BY_JOB.get(job);
    }

    public static java.util.Collection<WorkstationBlock> all() {
        return BY_JOB.values();
    }

    public static void register() {
        register("seed_shed", Job.FARMER, Blocks.OAK_PLANKS, 0, Shapes.SEED_SHED);
        register("chopping_block", Job.LUMBERJACK, Blocks.OAK_PLANKS, 0, Shapes.CHOPPING_BLOCK);
        register("fish_stall", Job.FISHER, Blocks.SPRUCE_PLANKS, 0, Shapes.FISH_STALL);
        register("feeding_trough", Job.BREEDER, Blocks.OAK_PLANKS, 0, Shapes.FEEDING_TROUGH);
        register("apothecary_table", Job.DOCTOR, Blocks.BIRCH_PLANKS, 0, Shapes.APOTHECARY_TABLE);
        register("forge", Job.BLACKSMITH, Blocks.POLISHED_BLACKSTONE_BRICKS, 10, Shapes.FORGE);
        register("bread_oven", Job.BAKER, Blocks.BRICKS, 9, Shapes.BREAD_OVEN);
        register("miner_cart", Job.MINER, Blocks.OAK_PLANKS, 0, Shapes.MINER_CART);
        register("cooking_pot", Job.COOK, Blocks.BRICKS, 9, Shapes.COOKING_POT);
        register("school_desk", Job.TEACHER, Blocks.OAK_PLANKS, 0, Shapes.SCHOOL_DESK);
        Registration.register(RegistryKeys.BLOCK_ENTITY_TYPE, "workstation",
                () -> BlockEntityType.Builder.create(WorkstationBlockEntity::new, BY_JOB.values().toArray(new Block[0])).build(null),
                type -> WORKSTATION_ENTITY = type);
        CreativeTabRegistry.modify(CreativeTabRegistry.defer(ItemGroups.FUNCTIONAL), (flags, entries, operator) -> BY_JOB.values().forEach(entries::add));
    }

    private static final class Shapes {
        static final double[][] SEED_SHED = {{1, 0, 3.5, 15, 8.5, 14.5}, {1.75, 8.5, 5, 14, 13.3, 13.25}, {13.5, 0, 0.8, 14.2, 13.5, 3.5}};
        static final double[][] CHOPPING_BLOCK = {{2, 0, 2, 14, 10, 14}, {3, 9, 7.5, 13, 16, 8.5}, {12.5, 0, 0.5, 15.5, 2.5, 6.5}, {0.5, 0, 9.5, 6.5, 2.5, 12.5}};
        static final double[][] FISH_STALL = {{1, 0, 3, 15, 10.5, 14}, {0, 9, 3.5, 16, 15.5, 14.5}, {11.5, 0, 0.5, 15, 3.25, 3.5}};
        static final double[][] FEEDING_TROUGH = {{1, 3, 2.5, 15, 8.75, 13.25}, {1.5, 0, 3.5, 3, 3, 12.5}, {13, 0, 3.5, 14.5, 3, 12.5}};
        static final double[][] APOTHECARY_TABLE = {{1, 9, 1, 15, 10.5, 15}, {1.5, 0, 1.5, 14.5, 9, 14.5}, {1.5, 10.5, 3, 14.5, 15.5, 14.5}};
        static final double[][] FORGE = {{0, 0, 0, 16, 8.5, 16}, {6.25, 8.5, 4.75, 15.75, 13, 15.5}, {7, 13, 11, 15, 16, 15.5}, {0.5, 8.5, 1.5, 8.75, 13.75, 5.25}, {1, 8.5, 9, 5.5, 12.1, 16}, {11.6, 8.5, 0.85, 15.2, 15, 4.4}};
        static final double[][] BREAD_OVEN = {{0, 0, 0, 16, 3, 16}, {0, 3, 1, 16, 9.5, 15}, {2, 9.5, 3, 14, 14, 14}, {6.5, 14, 8, 9.5, 16, 11}};
        static final double[][] MINER_CART = {{0, 0, 0, 16, 1.75, 16}, {1.5, 1.75, 2.25, 14.5, 12.75, 13.75}, {12.25, 0, 13.75, 15.5, 14, 15.25}};
        static final double[][] COOKING_POT = {{1, 0, 1, 15, 4, 15}, {1.25, 4, 2, 14.75, 12, 14}, {9.5, 12, 9, 14, 16, 11}};
        static final double[][] SCHOOL_DESK = {{1, 0, 4, 12, 12.5, 13}, {0.5, 0, 13.25, 15.5, 16, 15.5}, {12.25, 0, 1, 15.5, 11.4, 9.1}};
    }
}
