package com.maitreyoshi.mcavie.social;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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

    public static void show(ServerLevel world, VillagerEntityMCA a, VillagerEntityMCA b, int delta, int score, boolean family) {
        SocialConfig config = SocialConfig.get();
        long time = world.getGameTime();
        HOLDS.values().removeIf(h -> h.until < time || h.until - time > 600);
        int talk = 0;
        if (config.showChats && anyPlayerNear(world, a, b, config.chatRadius)) {
            talk = SocialConversation.start(world, a, b, delta < 0, score, family);
        }
        int hold = Math.max(config.lookSeconds * 20, talk);
        if (hold > 0) {
            long until = time + hold;
            HOLDS.put(a.getUUID(), new Hold(b, until));
            HOLDS.put(b.getUUID(), new Hold(a, until));
            hold(a);
            hold(b);
        }
    }

    private static boolean anyPlayerNear(ServerLevel world, VillagerEntityMCA a, VillagerEntityMCA b, int radius) {
        double max = (double) radius * radius;
        for (ServerPlayer player : world.players()) {
            if (player.distanceToSqr(a) <= max || player.distanceToSqr(b) <= max) {
                return true;
            }
        }
        return false;
    }

    public static void hold(VillagerEntityMCA self) {
        Hold hold = HOLDS.get(self.getUUID());
        if (hold == null) {
            return;
        }
        long left = hold.until - self.level().getGameTime();
        VillagerEntityMCA partner = hold.partner;
        if (left <= 0 || left > 600 || !self.isAlive() || !partner.isAlive() || partner.level() != self.level()
                || self.distanceToSqr(partner) > HOLD_DISTANCE * HOLD_DISTANCE) {
            HOLDS.remove(self.getUUID());
            return;
        }
        self.getNavigation().stop();
        self.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        self.getBrain().setMemoryWithExpiry(MemoryModuleType.LOOK_TARGET, new EntityTracker(partner, true), left);
    }

    public static void clear() {
        HOLDS.clear();
        SocialConversation.clear();
    }

    public static void forget(UUID uuid) {
        HOLDS.remove(uuid);
    }
}
