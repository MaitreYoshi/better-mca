package com.maitreyoshi.mcavie.work.client;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkNetwork;
import com.maitreyoshi.mcavie.work.WorkStatus;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WorkClient {
    private static final Map<Integer, Info> BY_ENTITY_ID = new HashMap<>();

    public record Info(Job job, boolean working, int level, boolean hasDepot, WorkStatus status, int experience) {
    }

    private WorkClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(WorkNetwork.JOB_INFO, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            Info info = new Info(Job.byId(buf.readVarInt()), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), WorkStatus.byId(buf.readVarInt()), buf.readVarInt());
            client.execute(() -> BY_ENTITY_ID.put(entityId, info));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BY_ENTITY_ID.clear());
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> BY_ENTITY_ID.remove(entity.getId()));
    }

    @Nullable
    public static Info get(int entityId) {
        return BY_ENTITY_ID.get(entityId);
    }

    public static Text menuLine(Info info) {
        return Text.translatable("job.mca_vie.menu_line", jobLine(info)).formatted(Formatting.WHITE);
    }

    public static MutableText jobLine(Info info) {
        MutableText text = info.job().getName().copy().formatted(Formatting.WHITE);
        if (info.job().isCraft()) {
            text.append(Text.literal(" (").formatted(Formatting.GRAY))
                    .append(Text.translatable("job.mca_vie.level." + Math.max(1, Math.min(5, info.level()))).formatted(Formatting.GOLD))
                    .append(Text.literal(")").formatted(Formatting.GRAY))
                    .append(Text.literal(" — ").formatted(Formatting.GRAY))
                    .append(statusText(info));
        }
        return text;
    }

    public static MutableText statusText(Info info) {
        if (info.status() == WorkStatus.INVENTORY_FULL || info.status() == WorkStatus.NO_GRIEFING || info.status() == WorkStatus.NO_WORKSTATION) {
            return info.status().getText().copy().formatted(Formatting.RED);
        }
        if (!info.working()) {
            return Text.translatable("job.mca_vie.resting").formatted(Formatting.GRAY);
        }
        return info.status().getText().copy().formatted(info.status() == WorkStatus.NOTHING_TO_DO ? Formatting.YELLOW : Formatting.GREEN);
    }

    public static void setJob(UUID villager, Job job) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(villager);
        buf.writeVarInt(job.ordinal());
        ClientPlayNetworking.send(WorkNetwork.SET_JOB, buf);
    }

    public static void pickDepot(UUID villager) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(villager);
        ClientPlayNetworking.send(WorkNetwork.PICK_DEPOT, buf);
    }

    public static void showWorkstation(UUID villager) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(villager);
        ClientPlayNetworking.send(WorkNetwork.SHOW_WORKSTATION, buf);
    }

    public static void zoneHere(UUID villager) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(villager);
        ClientPlayNetworking.send(WorkNetwork.ZONE_HERE, buf);
    }
}
