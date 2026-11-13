package com.maitreyoshi.mcavie.work.block;

import com.maitreyoshi.mcavie.compat.Nbt;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WorkstationBlockEntity extends RandomizableContainerBlockEntity {
    private static final int SIZE = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public WorkstationBlockEntity(BlockPos pos, BlockState state) {
        super(WorkBlocks.WORKSTATION_ENTITY, pos, state);
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> list) {
        items = list;
    }

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
        return ChestMenu.threeRows(syncId, playerInventory, this);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected void loadAdditional(ValueInput mcavieInput) {
        CompoundTag nbt = Nbt.in(mcavieInput);
        super.loadAdditional(mcavieInput);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(nbt)) {
            ContainerHelper.loadAllItems(nbt, items);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput mcavieOutput) {
        CompoundTag nbt = Nbt.out(mcavieOutput);
        super.saveAdditional(mcavieOutput);
        if (!trySaveLootTable(nbt)) {
            ContainerHelper.saveAllItems(nbt, items);
        }
    }
}
