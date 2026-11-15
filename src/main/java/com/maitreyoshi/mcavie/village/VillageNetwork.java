package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayConnectionEvents;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import net.mca.network.c2s.GetVillageRequest;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class VillageNetwork {
    public static final Identifier WITHDRAW = MCAVie.id("treasury_withdraw");
    public static final Identifier CONFIG = MCAVie.id("village_config");
    public static final Identifier DECREE = MCAVie.id("village_decree");
    public static final Identifier DEPOSIT = MCAVie.id("treasury_deposit");
    public static final Identifier NATURE = MCAVie.id("tax_nature");
    public static final Identifier ORDER = MCAVie.id("market_order");
    public static final Identifier COLLECT = MCAVie.id("market_collect");
    public static final Identifier JUDGE = MCAVie.id("justice_judge");

    private VillageNetwork() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(WITHDRAW, (server, player, handler, buf, responseSender) -> {
            int villageId = buf.readInt();
            int amount = buf.readInt();
            server.execute(() -> {
                VillageEconomy.withdraw(player, villageId, amount);
                new GetVillageRequest().receive(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(DEPOSIT, (server, player, handler, buf, responseSender) -> {
            int villageId = buf.readInt();
            int amount = buf.readInt();
            server.execute(() -> {
                VillageEconomy.deposit(player, villageId, amount);
                new GetVillageRequest().receive(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(NATURE, (server, player, handler, buf, responseSender) -> {
            int villageId = buf.readInt();
            int direction = buf.readInt();
            server.execute(() -> {
                VillageEconomy.changeNature(player, villageId, direction);
                new GetVillageRequest().receive(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ORDER, (server, player, handler, buf, responseSender) -> {
            int villageId = buf.readInt();
            String item = buf.readString(128);
            int lots = buf.readInt();
            server.execute(() -> {
                VillageMarket.order(player, villageId, item, lots);
                new GetVillageRequest().receive(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(COLLECT, (server, player, handler, buf, responseSender) -> {
            int villageId = buf.readInt();
            server.execute(() -> {
                VillageMarket.collect(player, villageId);
                new GetVillageRequest().receive(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(JUDGE, (server, player, handler, buf, responseSender) -> {
            int villageId = buf.readInt();
            java.util.UUID suspect = buf.readUuid();
            int verdict = buf.readInt();
            server.execute(() -> {
                VillageJustice.judge(player, villageId, suspect, verdict);
                new GetVillageRequest().receive(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(DECREE, (server, player, handler, buf, responseSender) -> {
            int villageId = buf.readInt();
            Decree decree = Decree.byIndex(buf.readInt());
            if (decree == null) {
                return;
            }
            server.execute(() -> {
                VillageDecrees.launch(player, villageId, decree);
                new GetVillageRequest().receive(player);
            });
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(VillageConfig.get().toJson(), 1 << 20);
            sender.sendPacket(CONFIG, buf);
        });
    }
}
