package com.maitreyoshi.mcavie.social;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SocialChat {
    public static final int PHRASES = 6;
    private static final double HOLD_DISTANCE = 6;

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<UUID, Long> LAST_CHAT = new HashMap<>();

    private record Hold(VillagerEntityMCA partner, long until) {
    }

    private SocialChat() {
    }

    public static void show(ServerWorld world, VillagerEntityMCA a, VillagerEntityMCA b, int delta, int score, boolean family) {
        SocialConfig config = SocialConfig.get();
        long time = world.getTime();
        HOLDS.values().removeIf(h -> h.until < time || h.until - time > 600);
        if (config.lookSeconds > 0) {
            long until = time + config.lookSeconds * 20L;
            HOLDS.put(a.getUuid(), new Hold(b, until));
            HOLDS.put(b.getUuid(), new Hold(a, until));
            hold(a);
            hold(b);
        }
        if (!config.showChats || a.isSpeechImpaired() || a.isToYoungToSpeak()) {
            return;
        }
        String mood = delta < 0 ? "quarrel" : family ? "family" : score >= Social.FRIEND ? "friend" : "neutral";
        String key = "chat.mca_vie.social." + mood + "." + world.getRandom().nextInt(PHRASES);
        MutableText text = Text.translatable("chat.mca_vie.social.format", a.getName(), b.getName(), Text.translatable(key))
                .formatted(Formatting.GRAY, Formatting.ITALIC);
        double max = (double) config.chatRadius * config.chatRadius;
        long cooldown = config.chatCooldownSeconds * 20L;
        LAST_CHAT.values().removeIf(last -> time - last >= cooldown || last > time);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if ((player.squaredDistanceTo(a) <= max || player.squaredDistanceTo(b) <= max) && !LAST_CHAT.containsKey(player.getUuid())) {
                LAST_CHAT.put(player.getUuid(), time);
                player.sendMessage(text);
            }
        }
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

    public static void forgetPlayer(UUID uuid) {
        LAST_CHAT.remove(uuid);
    }

    public static void clear() {
        HOLDS.clear();
        LAST_CHAT.clear();
    }

    public static void forget(UUID uuid) {
        HOLDS.remove(uuid);
    }
}
