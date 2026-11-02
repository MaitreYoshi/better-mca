package com.maitreyoshi.mcavie.aging;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkManager;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.MoveState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.ServerTask;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;

import java.util.Optional;
import java.util.UUID;

public final class Aging {
    public static final Identifier AGE = MCAVie.id("aging_age");
    public static final Identifier CONFIG = MCAVie.id("aging_config");

    private static final UUID SPEED_ID = UUID.fromString("5d6f0b8e-3c1a-4f7e-9b2d-8a4c6e1f2a01");
    private static final UUID HEALTH_ID = UUID.fromString("5d6f0b8e-3c1a-4f7e-9b2d-8a4c6e1f2a02");

    private Aging() {
    }

    public static void register() {
        AgingConfig.load();
        // START_TRACKING part avant le paquet d'apparition : envoyé tout de suite, l'âge arrive sur une entité
        // que le client ne connaît pas encore et se perd. On l'envoie au tour suivant, une fois le villageois apparu.
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof VillagerEntityMCA) {
                player.server.send(new ServerTask(player.server.getTicks(), () -> {
                    int years = years(entity);
                    if (years >= 0 && !player.isDisconnected()) {
                        sendTo(player, entity, years);
                    }
                }));
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(AgingConfig.get().toJson(), 1 << 16);
            sender.sendPacket(CONFIG, buf);
        });
    }

    public static int years(Entity entity) {
        return entity instanceof AgeHolder holder ? holder.mcavie$getAgeYears() : -1;
    }

    public static void update(VillagerEntityMCA villager, ServerWorld world, int years) {
        AgeHolder holder = (AgeHolder) villager;
        if (holder.mcavie$getAgeYears() != years) {
            holder.mcavie$setAgeYears(years);
            applyAttributes(villager, years);
            for (ServerPlayerEntity player : PlayerLookup.tracking(villager)) {
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

    private static void sendTo(ServerPlayerEntity player, Entity villager, int years) {
        PacketByteBuf buf = PacketByteBufs.create();
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
        setModifier(villager, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, -slow);

        float loss = config.lowerHealth ? config.healthMaxLoss * AgingConfig.progress(years, config.healthStartAge, config.healthFullAge) : 0;
        setModifier(villager, EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_ID, -loss);
        if (villager.getHealth() > villager.getMaxHealth()) {
            villager.setHealth(villager.getMaxHealth());
        }
    }

    private static void setModifier(VillagerEntityMCA villager, EntityAttribute attribute, UUID id, float amount) {
        EntityAttributeInstance instance = villager.getAttributeInstance(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount != 0) {
            instance.addTemporaryModifier(new EntityAttributeModifier(id, "mca_vie aging", amount, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void stayHome(VillagerEntityMCA villager, ServerWorld world, int years) {
        AgingConfig config = AgingConfig.get();
        if (!config.stayHome || years < config.stayHomeAge || WorkManager.getJob(villager) != Job.RETIRED) {
            return;
        }
        WorldCalendar.CalendarDate date = WorldCalendar.now(world);
        if (date.hour() < 7 || date.hour() >= 20 || villager.isSleeping() || villager.hasVehicle()
                || villager.getVillagerBrain().getMoveState() != MoveState.MOVE
                || villager.getVillagerBrain().isPanicking()
                || villager.getVillagerBrain().getCurrentJob() != Chore.NONE
                || villager.getBrain().hasMemoryModule(MemoryModuleType.WALK_TARGET)) {
            return;
        }
        long hourIndex = date.dayIndex() * 24 + date.hour();
        long seed = villager.getUuid().getLeastSignificantBits() ^ (hourIndex * 0x9E3779B97F4A7C15L);
        float roll = (float) ((seed >>> 40) & 0xFFFF) / 0x10000;
        if (roll >= config.stayHomeChance) {
            return;
        }
        Optional<GlobalPos> home = villager.getResidency().getHome();
        if (home.isEmpty() || home.get().getDimension() != world.getRegistryKey()) {
            return;
        }
        BlockPos pos = home.get().getPos();
        int radius = config.stayHomeRadius;
        if (villager.getBlockPos().getSquaredDistance(pos) > radius * radius) {
            villager.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, 0.5f, Math.max(1, radius / 2)));
        }
    }
}
