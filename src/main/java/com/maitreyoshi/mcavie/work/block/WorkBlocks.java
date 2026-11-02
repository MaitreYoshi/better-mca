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

    public static final WorkstationBlock SEED_SHED = register("seed_shed", Job.FARMER, Blocks.OAK_PLANKS);
    public static final WorkstationBlock CHOPPING_BLOCK = register("chopping_block", Job.LUMBERJACK, Blocks.OAK_PLANKS);
    public static final WorkstationBlock FISH_STALL = register("fish_stall", Job.FISHER, Blocks.SPRUCE_PLANKS);
    public static final WorkstationBlock FEEDING_TROUGH = register("feeding_trough", Job.BREEDER, Blocks.OAK_PLANKS);
    public static final WorkstationBlock APOTHECARY_TABLE = register("apothecary_table", Job.DOCTOR, Blocks.BIRCH_PLANKS);
    public static final WorkstationBlock FORGE = register("forge", Job.BLACKSMITH, Blocks.POLISHED_BLACKSTONE_BRICKS);
    public static final WorkstationBlock BREAD_OVEN = register("bread_oven", Job.BAKER, Blocks.BRICKS);
    public static final WorkstationBlock SCHOOL_DESK = register("school_desk", Job.TEACHER, Blocks.OAK_PLANKS);

    public static final BlockEntityType<WorkstationBlockEntity> WORKSTATION_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, MCAVie.id("workstation"),
            BlockEntityType.Builder.create(WorkstationBlockEntity::new, BY_JOB.values().toArray(new Block[0])).build(null));

    private WorkBlocks() {
    }

    private static WorkstationBlock register(String name, Job job, Block base) {
        WorkstationBlock block = Registry.register(Registries.BLOCK, MCAVie.id(name), new WorkstationBlock(Block.Settings.copy(base).strength(2.5F), job));
        Registry.register(Registries.ITEM, MCAVie.id(name), new BlockItem(block, new Item.Settings()));
        BY_JOB.put(job, block);
        return block;
    }

    @Nullable
    public static WorkstationBlock forJob(Job job) {
        return BY_JOB.get(job);
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> BY_JOB.values().forEach(entries::add));
    }
}
