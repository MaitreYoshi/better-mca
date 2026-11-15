package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import com.maitreyoshi.mcavie.life.TombstoneDetails;
import net.mca.block.TombstoneBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import net.mca.util.localization.FlowingText;
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
    public abstract Optional<Text> getEntityName();

    @Inject(method = "setEntity", at = @At("HEAD"), remap = false)
    private void mcavie$recordDates(@Nullable Entity entity, CallbackInfo ci) {
        mcavie$years = null;
        mcavie$details = null;
        if (entity == null || !(entity.getWorld() instanceof ServerWorld world)) {
            return;
        }
        LifeRegistry registry = LifeRegistry.get(world.getServer());
        Long birth = registry.getBirthDay(entity.getUuid());
        Long death = registry.getDeathDay(entity.getUuid());
        if (death == null) {
            death = WorldCalendar.now(world).dayIndex();
            if (birth != null) {
                registry.recordDeath(entity.getUuid(), death, WorldCalendar.exactCalendarDay(world));
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
    private void mcavie$appendDates(CallbackInfoReturnable<Optional<Text>> cir) {
        if (mcavie$years != null) {
            String years = mcavie$years;
            cir.setReturnValue(cir.getReturnValue().map(name -> Text.literal(name.getString() + " " + years)));
        }
    }

    @Inject(method = "getOrCreateEntityName", at = @At("HEAD"), cancellable = true, remap = false)
    private void mcavie$datesOnOwnLine(Function<Text, FlowingText> factory, CallbackInfoReturnable<FlowingText> cir) {
        if (computedName == null && mcavie$years != null) {
            String years = mcavie$years;
            String name = getEntityName().map(Text::getString).orElse("");
            if (name.endsWith(" " + years)) {
                name = name.substring(0, name.length() - years.length() - 1);
            }
            computedName = factory.apply(Text.literal(name + "\n" + years));
        }
        if (computedName != null) {
            cir.setReturnValue(computedName);
        }
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void mcavie$writeDates(NbtCompound nbt, CallbackInfo ci) {
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
            nbt.putUuid(OWNER_KEY, mcavie$vaultOwner);
        }
    }

    @Inject(method = "writeToStack", at = @At("TAIL"), remap = false)
    private void mcavie$writeDatesToStack(ItemStack stack, CallbackInfo ci) {
        NbtCompound data = stack.getSubNbt("entityData");
        if (mcavie$years != null && data != null) {
            data.putString(KEY, mcavie$years);
        }
        if (mcavie$details != null && data != null) {
            mcavie$details.write(data);
        }
    }

    @Inject(method = "readFromStack", at = @At("TAIL"), remap = false)
    private void mcavie$readDatesFromStack(ItemStack stack, CallbackInfo ci) {
        NbtCompound data = stack == null ? null : stack.getSubNbt("entityData");
        mcavie$years = data != null && data.contains(KEY) ? data.getString(KEY) : null;
        mcavie$details = TombstoneDetails.read(data);
        computedName = null;
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void mcavie$readDates(NbtCompound nbt, CallbackInfo ci) {
        mcavie$years = nbt.contains(KEY) ? nbt.getString(KEY) : null;
        TombstoneDetails previous = mcavie$details;
        mcavie$details = TombstoneDetails.read(nbt);
        mcavie$vaultTitle = nbt.contains(TITLE_KEY) ? nbt.getString(TITLE_KEY) : null;
        mcavie$vaultOwner = nbt.containsUuid(OWNER_KEY) ? nbt.getUuid(OWNER_KEY) : null;
        computedName = null;
        BlockEntity self = (BlockEntity) (Object) this;
        World world = self.getWorld();
        if (world != null && world.isClient && !java.util.Objects.equals(previous, mcavie$details)) {
            world.updateListeners(self.getPos(), self.getCachedState(), self.getCachedState(), 8);
        }
    }
}
