package com.maitreyoshi.mcavie.work.block;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.work.Job;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class WorkBlocks {
    private static final Map<Job, WorkstationBlock> BY_JOB = new EnumMap<>(Job.class);

    public static final WorkstationBlock SEED_SHED = register("seed_shed", Job.FARMER, Blocks.OAK_PLANKS, 0, Shapes.SEED_SHED);
    public static final WorkstationBlock CHOPPING_BLOCK = register("chopping_block", Job.LUMBERJACK, Blocks.OAK_PLANKS, 0, Shapes.CHOPPING_BLOCK);
    public static final WorkstationBlock FISH_STALL = register("fish_stall", Job.FISHER, Blocks.SPRUCE_PLANKS, 0, Shapes.FISH_STALL);
    public static final WorkstationBlock FEEDING_TROUGH = register("feeding_trough", Job.BREEDER, Blocks.OAK_PLANKS, 0, Shapes.FEEDING_TROUGH);
    public static final WorkstationBlock APOTHECARY_TABLE = register("apothecary_table", Job.DOCTOR, Blocks.BIRCH_PLANKS, 0, Shapes.APOTHECARY_TABLE);
    public static final WorkstationBlock FORGE = register("forge", Job.BLACKSMITH, Blocks.POLISHED_BLACKSTONE_BRICKS, 10, Shapes.FORGE);
    public static final WorkstationBlock BREAD_OVEN = register("bread_oven", Job.BAKER, Blocks.BRICKS, 9, Shapes.BREAD_OVEN);
    public static final WorkstationBlock MINER_CART = register("miner_cart", Job.MINER, Blocks.OAK_PLANKS, 0, Shapes.MINER_CART);
    public static final WorkstationBlock COOKING_POT = register("cooking_pot", Job.COOK, Blocks.BRICKS, 9, Shapes.COOKING_POT);
    public static final WorkstationBlock SCHOOL_DESK = register("school_desk", Job.TEACHER, Blocks.OAK_PLANKS, 0, Shapes.SCHOOL_DESK);

    public static final BlockEntityType<WorkstationBlockEntity> WORKSTATION_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, MCAVie.id("workstation"),
            BlockEntityType.Builder.create(WorkstationBlockEntity::new, BY_JOB.values().toArray(new Block[0])).build(null));

    private WorkBlocks() {
    }

    private static WorkstationBlock register(String name, Job job, Block base, int light, double[][] shape) {
        Block.Settings settings = Block.Settings.copy(base).strength(2.5F).nonOpaque().luminance(state -> light);
        WorkstationBlock block = Registry.register(Registries.BLOCK, MCAVie.id(name), new WorkstationBlock(settings, job, shape));
        Registry.register(Registries.ITEM, MCAVie.id(name), new BlockItem(block, new Item.Settings()));
        BY_JOB.put(job, block);
        return block;
    }

    @Nullable
    public static WorkstationBlock forJob(Job job) {
        return BY_JOB.get(job);
    }

    public static java.util.Collection<WorkstationBlock> all() {
        return BY_JOB.values();
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> BY_JOB.values().forEach(entries::add));
    }

    private static final class Shapes {
        static final double[][] SEED_SHED = {{1, 0, 5, 10, 9, 14}, {10.5, 0, 6, 15.5, 7.2, 11}, {2.75, 0, 0, 5.25, 11, 6}};
        static final double[][] CHOPPING_BLOCK = {{2, 0, 3, 14, 9, 13}, {3, 0, 2, 13, 9, 14}, {5, 9, 7, 12, 14.5, 9}};
        static final double[][] FISH_STALL = {{1, 0, 3, 15, 6, 13}, {2, 5, 4, 14, 7.5, 12}, {15, 1, 4, 16, 6.5, 11}};
        static final double[][] FEEDING_TROUGH = {{1, 3, 3, 15, 9, 13}, {1, 0, 3, 2.5, 3, 13}, {13.5, 0, 3, 15, 3, 13}};
        static final double[][] APOTHECARY_TABLE = {{1, 9, 1, 15, 11, 15}, {2, 0, 2, 14, 9, 14}, {2, 11, 2, 14, 15, 14}};
        static final double[][] FORGE = {{0, 0, 0, 16, 8, 16}, {6, 8, 3.5, 16, 11, 16}, {6, 11, 13.5, 16, 16, 16}, {0.5, 8, 1.5, 7.5, 13.5, 5.5}};
        static final double[][] BREAD_OVEN = {{0, 0, 0, 16, 4, 16}, {1, 4, 2, 15, 10.5, 15}, {2, 10.5, 4, 14, 13.5, 14}, {5.5, 13.5, 7, 12.5, 16, 13}};
        static final double[][] MINER_CART = {{0, 0, 0, 16, 2, 16}, {1.25, 2, 3, 14.75, 11.5, 13}, {3, 11.5, 5.25, 13, 13, 10.75}, {0.5, 0, 0, 3.5, 5.5, 3.25}};
        static final double[][] COOKING_POT = {{1, 0, 1, 15, 5, 15}, {1, 5, 2, 15, 13.25, 14}, {9, 13.25, 9, 13, 16, 11}};
        static final double[][] SCHOOL_DESK = {{1, 0, 3.5, 15, 10.5, 12}, {1, 0, 12, 15, 10.5, 14}, {0.5, 9.5, 14, 15.5, 16, 15.5}};
    }
}
