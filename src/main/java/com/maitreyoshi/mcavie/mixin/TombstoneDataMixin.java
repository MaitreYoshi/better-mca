package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.compat.Nbt;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import com.maitreyoshi.mcavie.life.TombstoneDetails;
import net.conczin.mca.block.TombstoneBlock;
import org.jetbrains.annotations.Nullable;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Function;

@Mixin(TombstoneBlock.Data.class)
public abstract class TombstoneDataMixin implements TombstoneDetails.Holder {
    @Unique
    private static final String KEY = "mca_surnames:life_years";

    @Unique
    @Nullable
    private String mcavie$years;

    @Unique
    @Nullable
    private TombstoneDetails mcavie$details;

    @Unique
    private static final String TITLE_KEY = "mca_vie:vault_title";

    @Unique
    private static final String OWNER_KEY = "mca_vie:vault_owner";

    @Unique
    @Nullable
    private String mcavie$vaultTitle;

    @Unique
    @Nullable
    private java.util.UUID mcavie$vaultOwner;

    @Shadow(remap = false)
    private FlowingText computedName;

    @Override
    @Nullable
    public String mcavie$vaultTitle() {
        return mcavie$vaultTitle;
    }

    @Override
    public void mcavie$setVaultTitle(@Nullable String title) {
        mcavie$vaultTitle = title;
    }

    @Override
    @Nullable
    public java.util.UUID mcavie$vaultOwner() {
        return mcavie$vaultOwner;
    }

    @Override
    public void mcavie$setVaultOwner(@Nullable java.util.UUID owner) {
        mcavie$vaultOwner = owner;
    }

    @Shadow(remap = false)
    public abstract Optional<Component> getEntityName();

    @Inject(method = "setEntity", at = @At("HEAD"), remap = false)
    private void mcavie$recordDates(@Nullable Entity entity, CallbackInfo ci) {
        mcavie$years = null;
        mcavie$details = null;
        if (entity == null || !(entity.level() instanceof ServerLevel world)) {
            return;
        }
        LifeRegistry registry = LifeRegistry.get(world.getServer());
        Long birth = registry.getBirthDay(entity.getUUID());
        Long death = registry.getDeathDay(entity.getUUID());
        if (death == null) {
            death = WorldCalendar.now(world).dayIndex();
            if (birth != null) {
                registry.recordDeath(entity.getUUID(), death, WorldCalendar.exactCalendarDay(world));
            }
        }
        mcavie$details = TombstoneDetails.of(entity, world, birth, death);
        if (birth == null) {
            return;
        }
        mcavie$years = mcavie$format(WorldCalendar.displayYearOfDay(birth), WorldCalendar.displayYearOfDay(death));
    }

    @Override
    @Nullable
    public TombstoneDetails mcavie$details() {
        return mcavie$details;
    }

    @Unique
    private static String mcavie$format(int birthYear, int deathYear) {
        String separator = birthYear < 0 || deathYear < 0 ? " - " : "-";
        return "(" + birthYear + separator + deathYear + ")";
    }

    @Inject(method = "getEntityName", at = @At("RETURN"), cancellable = true, remap = false)
    private void mcavie$appendDates(CallbackInfoReturnable<Optional<Component>> cir) {
        if (mcavie$years != null) {
            String years = mcavie$years;
            cir.setReturnValue(cir.getReturnValue().map(name -> Component.literal(name.getString() + " " + years)));
        }
    }

    @Inject(method = "getOrCreateEntityName", at = @At("HEAD"), cancellable = true, remap = false)
    private void mcavie$datesOnOwnLine(Function<Component, FlowingText> factory, CallbackInfoReturnable<FlowingText> cir) {
        if (computedName == null && mcavie$years != null) {
            String years = mcavie$years;
            String name = getEntityName().map(Component::getString).orElse("");
            if (name.endsWith(" " + years)) {
                name = name.substring(0, name.length() - years.length() - 1);
            }
            computedName = factory.apply(Component.literal(name + "\n" + years));
        }
        if (computedName != null) {
            cir.setReturnValue(computedName);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void mcavie$writeDates(CompoundTag nbt, CallbackInfo ci) {
        if (mcavie$years != null) {
            nbt.putString(KEY, mcavie$years);
        }
        if (mcavie$details != null) {
            mcavie$details.write(nbt);
        }
        if (mcavie$vaultTitle != null) {
            nbt.putString(TITLE_KEY, mcavie$vaultTitle);
        }
        if (mcavie$vaultOwner != null) {
            Nbt.putUUID(nbt, OWNER_KEY, mcavie$vaultOwner);
        }
    }

    @Inject(method = "writeToStack", at = @At("TAIL"), remap = false)
    private void mcavie$writeDatesToStack(ItemStack stack, CallbackInfo ci) {
        CompoundTag data = stack.getTagElement("entityData");
        if (mcavie$years != null && data != null) {
            data.putString(KEY, mcavie$years);
        }
        if (mcavie$details != null && data != null) {
            mcavie$details.write(data);
        }
    }

    @Inject(method = "readFromStack", at = @At("TAIL"), remap = false)
    private void mcavie$readDatesFromStack(ItemStack stack, CallbackInfo ci) {
        CompoundTag data = stack == null ? null : stack.getTagElement("entityData");
        mcavie$years = data != null && data.contains(KEY) ? data.getStringOr(KEY, "") : null;
        mcavie$details = TombstoneDetails.read(data);
        computedName = null;
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void mcavie$readDates(CompoundTag nbt, CallbackInfo ci) {
        mcavie$years = nbt.contains(KEY) ? nbt.getStringOr(KEY, "") : null;
        TombstoneDetails previous = mcavie$details;
        mcavie$details = TombstoneDetails.read(nbt);
        mcavie$vaultTitle = nbt.contains(TITLE_KEY) ? nbt.getStringOr(TITLE_KEY, "") : null;
        mcavie$vaultOwner = Nbt.hasUUID(nbt, OWNER_KEY) ? Nbt.getUUID(nbt, OWNER_KEY) : null;
        computedName = null;
        BlockEntity self = (BlockEntity) (Object) this;
        Level world = self.getLevel();
        if (world != null && world.isClientSide() && !java.util.Objects.equals(previous, mcavie$details)) {
            world.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), 8);
        }
    }
}
