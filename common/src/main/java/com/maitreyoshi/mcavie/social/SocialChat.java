package com.maitreyoshi.mcavie.social;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SocialChat {
    private static final double HOLD_DISTANCE = 6;

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();

    private record Hold(VillagerEntityMCA partner, long until) {
    }

    private SocialChat() {
    }

    public static void show(ServerWorld world, VillagerEntityMCA a, VillagerEntityMCA b, int delta, int score, boolean family) {
        SocialConfig config = SocialConfig.get();
        long time = world.getTime();
        HOLDS.values().removeIf(h -> h.until < time || h.until - time > 600);
        int talk = 0;
        if (config.showChats && anyPlayerNear(world, a, b, config.chatRadius)) {
            talk = SocialConversation.start(world, a, b, delta < 0, score, family);
        }
        int hold = Math.max(config.lookSeconds * 20, talk);
        if (hold > 0) {
            long until = time + hold;
            HOLDS.put(a.getUuid(), new Hold(b, until));
            HOLDS.put(b.getUuid(), new Hold(a, until));
            hold(a);
            hold(b);
        }
    }

    private static boolean anyPlayerNear(ServerWorld world, VillagerEntityMCA a, VillagerEntityMCA b, int radius) {
        double max = (double) radius * radius;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(a) <= max || player.squaredDistanceTo(b) <= max) {
                return true;
            }
        }
        return false;
    }

    public static void hold(VillagerEntityMCA self) {
        Hold hold = HOLDS.get(self.getUuid());
        if (hold == null) {
            return;
        }
        long left = hold.until - self.getWorld().getTime();
        VillagerEntityMCA partner = hold.partner;
        if (left <= 0 || left > 600 || !self.isAlive() || !partner.isAlive() || partner.getWorld() != self.getWorld()
                || self.squaredDistanceTo(partner) > HOLD_DISTANCE * HOLD_DISTANCE) {
            HOLDS.remove(self.getUuid());
            return;
        }
        self.getNavigation().stop();
        self.getBrain().forget(MemoryModuleType.WALK_TARGET);
        self.getBrain().remember(MemoryModuleType.LOOK_TARGET, new EntityLookTarget(partner, true), left);
    }

    public static void clear() {
        HOLDS.clear();
        SocialConversation.clear();
    }

    public static void forget(UUID uuid) {
        HOLDS.remove(uuid);
    }
}
