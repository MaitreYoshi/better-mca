package com.maitreyoshi.mcavie.aging;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.compat.EntityTrackingEvents;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.PlayerLookup;
import com.maitreyoshi.mcavie.compat.ServerPlayConnectionEvents;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.MoveState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import java.util.Optional;
import java.util.UUID;

public final class Aging {
    public static final ResourceLocation AGE = MCAVie.id("aging_age");
    public static final ResourceLocation CONFIG = MCAVie.id("aging_config");

    private static final UUID SPEED_ID = UUID.fromString("5d6f0b8e-3c1a-4f7e-9b2d-8a4c6e1f2a01");
    private static final UUID HEALTH_ID = UUID.fromString("5d6f0b8e-3c1a-4f7e-9b2d-8a4c6e1f2a02");

    private Aging() {
    }

    public static void register() {
        AgingConfig.load();
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof VillagerEntityMCA) {
                player.server.tell(new TickTask(player.server.getTickCount(), () -> {
                    int years = years(entity);
                    if (years >= 0 && !player.hasDisconnected()) {
                        sendTo(player, entity, years);
                    }
                }));
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeUtf(AgingConfig.get().toJson(), 1 << 16);
            sender.sendPacket(CONFIG, buf);
        });
    }

    public static int years(Entity entity) {
        return entity instanceof AgeHolder holder ? holder.mcavie$getAgeYears() : -1;
    }

    public static void update(VillagerEntityMCA villager, ServerLevel world, int years) {
        AgeHolder holder = (AgeHolder) villager;
        if (holder.mcavie$getAgeYears() != years) {
            holder.mcavie$setAgeYears(years);
            applyAttributes(villager, years);
            for (ServerPlayer player : PlayerLookup.tracking(villager)) {
                sendTo(player, villager, years);
            }
        }
        stayHome(villager, world, years);
    }

    public static void refresh(VillagerEntityMCA villager) {
        ((AgeHolder) villager).mcavie$setAgeYears(-1);
    }

    public static float voicePitchDrop(Entity entity) {
        AgingConfig config = AgingConfig.get();
        if (!config.deeperVoice) {
            return 0;
        }
        return config.voicePitchDrop * AgingConfig.progress(years(entity), config.voiceStartAge, config.voiceFullAge);
    }

    private static void sendTo(ServerPlayer player, Entity villager, int years) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(villager.getId());
        buf.writeVarInt(years);
        ServerPlayNetworking.send(player, AGE, buf);
    }

    private static void applyAttributes(VillagerEntityMCA villager, int years) {
        AgingConfig config = AgingConfig.get();
        float slow = 0;
        if (config.slowWalk && years >= config.slowStartAge) {
            slow = config.slowMin + (config.slowMax - config.slowMin) * AgingConfig.progress(years, config.slowStartAge, config.slowFullAge);
        }
        setModifier(villager, Attributes.MOVEMENT_SPEED, SPEED_ID, -slow);

        float loss = config.lowerHealth ? config.healthMaxLoss * AgingConfig.progress(years, config.healthStartAge, config.healthFullAge) : 0;
        setModifier(villager, Attributes.MAX_HEALTH, HEALTH_ID, -loss);
        if (villager.getHealth() > villager.getMaxHealth()) {
            villager.setHealth(villager.getMaxHealth());
        }
    }

    private static void setModifier(VillagerEntityMCA villager, Attribute attribute, UUID id, float amount) {
        AttributeInstance instance = villager.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(id, "mca_vie aging", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void stayHome(VillagerEntityMCA villager, ServerLevel world, int years) {
        AgingConfig config = AgingConfig.get();
        if (!config.stayHome || years < config.stayHomeAge || WorkManager.getJob(villager) != Job.RETIRED) {
            return;
        }
        WorldCalendar.CalendarDate date = WorldCalendar.now(world);
        if (date.hour() < 7 || date.hour() >= 20 || villager.isSleeping() || villager.isPassenger()
                || villager.getVillagerBrain().getMoveState() != MoveState.MOVE
                || villager.getVillagerBrain().isPanicking()
                || villager.getVillagerBrain().getCurrentJob() != Chore.NONE
                || villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return;
        }
        long hourIndex = date.dayIndex() * 24 + date.hour();
        long seed = villager.getUUID().getLeastSignificantBits() ^ (hourIndex * 0x9E3779B97F4A7C15L);
        float roll = (float) ((seed >>> 40) & 0xFFFF) / 0x10000;
        if (roll >= config.stayHomeChance) {
            return;
        }
        Optional<GlobalPos> home = villager.getResidency().getHome();
        if (home.isEmpty() || home.get().dimension() != world.dimension()) {
            return;
        }
        BlockPos pos = home.get().pos();
        int radius = config.stayHomeRadius;
        if (villager.blockPosition().distSqr(pos) > radius * radius) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, 0.5f, Math.max(1, radius / 2)));
        }
    }
}
