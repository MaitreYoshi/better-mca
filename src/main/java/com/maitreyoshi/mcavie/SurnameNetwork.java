package com.maitreyoshi.mcavie;

import com.maitreyoshi.mcavie.compat.Nbt;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import com.maitreyoshi.mcavie.life.Wellbeing;
import com.maitreyoshi.mcavie.compat.EntityTrackingEvents;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.PlayerLookup;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import com.maitreyoshi.mcavie.compat.ServerPlayConnectionEvents;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import java.util.UUID;

public final class SurnameNetwork {
    public static final Identifier SET_SURNAME = MCAVie.id("set_surname");
    public static final Identifier RANDOM_SURNAME = MCAVie.id("random_surname");
    public static final Identifier OWN_SURNAME = MCAVie.id("own_surname");
    public static final Identifier RANDOM_SURNAME_RESULT = MCAVie.id("random_surname_result");
    public static final Identifier BABY_SURNAME = MCAVie.id("baby_surname");
    public static final Identifier BIRTH_DAY_REQUEST = MCAVie.id("birth_day_request");
    public static final Identifier BIRTH_DAY_RESULT = MCAVie.id("birth_day_result");
    public static final Identifier HAPPINESS = MCAVie.id("happiness");

    private SurnameNetwork() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(SET_SURNAME, (server, player, handler, buf, responseSender) -> {
            String surname = SurnameText.sanitize(buf.readUtf(64));
            server.execute(() -> {
                if (!surname.isEmpty()) {
                    Surnames.setPlayerSurname(player, surname, false);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RANDOM_SURNAME, (server, player, handler, buf, responseSender) ->
                server.execute(() -> {
                    FriendlyByteBuf out = PacketByteBufs.create();
                    PlayerSaveData data = PlayerSaveData.get(player);
                    Gender gender = data.getEntityData() != null ? data.getGender() : Gender.MALE;
                    out.writeUtf(SurnameText.display(Surnames.randomSurnameFor(player), gender));
                    ServerPlayNetworking.send(player, RANDOM_SURNAME_RESULT, out);
                }));

        ServerPlayNetworking.registerGlobalReceiver(BABY_SURNAME, (server, player, handler, buf, responseSender) -> {
            int slot = buf.readVarInt();
            server.execute(() -> {
                ItemStack stack = slot >= 0 && slot < player.getInventory().getContainerSize() ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
                if (!(stack.getItem() instanceof BabyItem baby)) {
                    return;
                }
                CompoundTag nbt = BabyItem.getBabyNbt(stack);
                String surname = Surnames.babySurname(player.level(), player,
                        Nbt.hasUUID(nbt, "father") ? Nbt.getUUID(nbt, "father") : null,
                        Nbt.hasUUID(nbt, "mother") ? Nbt.getUUID(nbt, "mother") : null);
                if (surname != null) {
                    FriendlyByteBuf out = PacketByteBufs.create();
                    out.writeUtf(SurnameText.display(surname, baby.getGender()));
                    ServerPlayNetworking.send(player, RANDOM_SURNAME_RESULT, out);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(BIRTH_DAY_REQUEST, (server, player, handler, buf, responseSender) -> {
            UUID villager = buf.readUUID();
            server.execute(() -> {
                Long birth = LifeRegistry.get(server).getBirthDay(villager);
                if (birth != null) {
                    FriendlyByteBuf out = PacketByteBufs.create();
                    out.writeUUID(villager);
                    out.writeLong(birth);
                    ServerPlayNetworking.send(player, BIRTH_DAY_RESULT, out);
                }
            });
        });

        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof VillagerEntityMCA villager) {
                Wellbeing.sendTo(villager, player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String surname = SurnameRegistry.get(server).get(handler.player.getUUID());
            sendOwnSurname(handler.player, surname == null ? "" : surname);
            if (surname != null) {
                Surnames.uppercasePlayer(handler.player);
            }
            if (surname == null && net.conczin.mca.server.world.data.PlayerSaveData.get(handler.player).isEntityDataSet()) {
                handler.player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("command.mca_vie.none"));
            }
        });
    }

    public static void sendHappiness(Entity villager, int happiness, boolean sick) {
        for (ServerPlayer player : PlayerLookup.tracking(villager)) {
            sendHappinessTo(player, villager, happiness, sick);
        }
    }

    public static void sendHappinessTo(ServerPlayer player, Entity villager, int happiness, boolean sick) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(villager.getId());
        buf.writeByte(happiness);
        buf.writeBoolean(sick);
        ServerPlayNetworking.send(player, HAPPINESS, buf);
    }

    public static void sendOwnSurname(ServerPlayer player, String surname) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(surname);
        ServerPlayNetworking.send(player, OWN_SURNAME, buf);
    }
}
