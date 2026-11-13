package com.maitreyoshi.mcavie.work.client;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkNetwork;
import com.maitreyoshi.mcavie.work.WorkStatus;
import com.maitreyoshi.mcavie.work.WorkGesture;
import com.maitreyoshi.mcavie.compat.client.ClientEntityEvents;
import com.maitreyoshi.mcavie.compat.client.ClientPlayConnectionEvents;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class WorkClient {
    private static final Map<Integer, Info> BY_ENTITY_ID = new HashMap<>();

    public record Info(Job job, boolean working, int level, boolean hasDepot, WorkStatus status, int experience, WorkGesture gesture) {
    }

    private WorkClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(WorkNetwork.JOB_INFO, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            Info info = new Info(Job.byId(buf.readVarInt()), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), WorkStatus.byId(buf.readVarInt()), buf.readVarInt(), WorkGesture.byId(buf.readVarInt()));
            client.execute(() -> BY_ENTITY_ID.put(entityId, info));
        });
        ClientPlayNetworking.registerGlobalReceiver(WorkNetwork.JOB_REFUSED, (client, handler, buf, responseSender) -> {
            Component message = buf.readComponent();
            client.execute(() -> {
                if (client.screen instanceof JobScreen screen) {
                    screen.showRefusal(message);
                } else if (client.player != null) {
                    client.player.displayClientMessage(message, true);
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BY_ENTITY_ID.clear());
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> BY_ENTITY_ID.remove(entity.getId()));
    }

    @Nullable
    public static Info get(int entityId) {
        return BY_ENTITY_ID.get(entityId);
    }

    public static Component menuLine(Info info) {
        return Component.translatable("job.mca_vie.menu_line", jobLine(info)).withStyle(ChatFormatting.WHITE);
    }

    public static MutableComponent jobLine(Info info) {
        MutableComponent text = info.job().getName().copy().withStyle(ChatFormatting.WHITE);
        if (info.job().isCraft()) {
            text.append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("job.mca_vie.level." + Math.max(1, Math.min(5, info.level()))).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(")").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                    .append(statusText(info));
        }
        return text;
    }

    public static MutableComponent statusText(Info info) {
        if (info.status() == WorkStatus.INVENTORY_FULL || info.status() == WorkStatus.NO_GRIEFING || info.status() == WorkStatus.NO_WORKSTATION
                || info.status() == WorkStatus.NO_TOOL) {
            return info.status().getText().copy().withStyle(ChatFormatting.RED);
        }
        if (!info.working()) {
            return Component.translatable("job.mca_vie.resting").withStyle(ChatFormatting.GRAY);
        }
        return info.status().getText().copy().withStyle(info.status() == WorkStatus.NOTHING_TO_DO ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
    }

    public static void setJob(UUID villager, Job job) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(villager);
        buf.writeVarInt(job.ordinal());
        ClientPlayNetworking.send(WorkNetwork.SET_JOB, buf);
    }

    public static void pickDepot(UUID villager) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(villager);
        ClientPlayNetworking.send(WorkNetwork.PICK_DEPOT, buf);
    }

    public static void showWorkstation(UUID villager) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(villager);
        ClientPlayNetworking.send(WorkNetwork.SHOW_WORKSTATION, buf);
    }

    public static void zoneHere(UUID villager) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(villager);
        ClientPlayNetworking.send(WorkNetwork.ZONE_HERE, buf);
    }
}
