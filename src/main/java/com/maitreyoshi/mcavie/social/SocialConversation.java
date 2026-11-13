package com.maitreyoshi.mcavie.social;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.village.CivilRegistry;
import com.maitreyoshi.mcavie.village.VillageState;
import com.maitreyoshi.mcavie.life.WellbeingRegistry;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import com.maitreyoshi.mcavie.compat.ServerTickEvents;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SocialConversation {
    public static final String HEAD_MARKER = "mca_vie:head:";
    public static final int GAP = 60;
    public static final int BUBBLE_TICKS = 90;
    private static final int OPEN_VARIANTS = 3;
    private static final int ANSWER_VARIANTS = 2;
    private static final int WITNESS_VARIANTS = 3;
    private static final double WITNESS_RADIUS = 10;
    private static final long NEWS_DAYS = 7;

    private static final List<Line> PENDING = new ArrayList<>();

    private record Line(ServerLevel world, VillagerEntityMCA speaker, Component text, boolean angry, long at) {
    }

    private record Topic(String id, Component subject) {
    }

    private SocialConversation() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SocialConversation::tick);
    }

    public static void clear() {
        PENDING.clear();
    }

    public static int start(ServerLevel world, VillagerEntityMCA a, VillagerEntityMCA b, boolean quarrel, int score, boolean family) {
        if (!canSpeak(a) || !canSpeak(b)) {
            return 0;
        }
        RandomSource random = world.getRandom();
        Topic topic = quarrel && score <= Social.ENEMY ? new Topic("rival", Component.empty()) : chooseTopic(world, a, b, score, family, random);
        String base = "chat.mca_vie.talk." + topic.id() + ".";
        String answer = quarrel ? "no" : "yes";
        long time = world.getGameTime();
        PENDING.add(new Line(world, a, Component.translatable(base + "open." + random.nextInt(OPEN_VARIANTS), b.getName(), topic.subject()), false, time));
        PENDING.add(new Line(world, b, Component.translatable(base + answer + "." + random.nextInt(ANSWER_VARIANTS), a.getName(), topic.subject()), quarrel, time + GAP));
        PENDING.add(new Line(world, a, Component.translatable(base + "end_" + answer + "." + random.nextInt(ANSWER_VARIANTS), b.getName(), topic.subject()), quarrel, time + 2L * GAP));
        return 2 * GAP + BUBBLE_TICKS;
    }

    private static boolean canSpeak(VillagerEntityMCA villager) {
        return villager.isAlive() && !villager.isSpeechImpaired() && !villager.isToYoungToSpeak();
    }

    private static Topic chooseTopic(ServerLevel world, VillagerEntityMCA a, VillagerEntityMCA b, int score, boolean family, RandomSource random) {
        List<Topic> topics = new ArrayList<>();
        Component empty = Component.empty();
        if (world.isRaining()) {
            topics.add(new Topic("rain", empty));
        } else if (world.isNight()) {
            topics.add(new Topic("night", empty));
        } else {
            topics.add(new Topic("sun", empty));
        }
        WorkRegistry registry = WorkRegistry.get(world.getServer());
        WorkRegistry.Record work = registry.get(a.getUUID());
        if (work != null && work.job != Job.NONE) {
            topics.add(new Topic("work", work.job.getName()));
            topics.add(new Topic("job." + work.job.id(), work.job.getName()));
            topics.add(new Topic("job." + work.job.id(), work.job.getName()));
        }
        WorkRegistry.Record other = registry.get(b.getUUID());
        if (other != null && other.job != Job.NONE) {
            topics.add(new Topic("ask." + other.job.id(), other.job.getName()));
            topics.add(new Topic("ask." + other.job.id(), other.job.getName()));
        }
        Optional<Village> village = VillageManager.get(world).findNearestVillage(a);
        if (village.isPresent()) {
            float taxes = village.get().getTaxes();
            if (taxes >= 0.3f) {
                topics.add(new Topic("taxes_high", Component.literal(Math.round(taxes * 100) + " %")));
            } else if (taxes <= 0.1f) {
                topics.add(new Topic("taxes_low", empty));
            }
            Topic news = news(world, village.get());
            if (news != null) {
                topics.add(news);
                topics.add(news);
            }
        }
        Player player = world.getNearestPlayer(a, 10);
        if (player != null && !player.isSpectator()) {
            topics.add(new Topic("player", player.getName()));
        }
        if (family) {
            topics.add(new Topic("family", empty));
        } else if (score >= Social.FRIEND) {
            topics.add(new Topic("friend", empty));
        }
        WellbeingRegistry.Record mood = WellbeingRegistry.get(world.getServer()).get(a.getUUID());
        if (mood != null && mood.happiness < Social.UNHAPPY_BELOW) {
            topics.add(new Topic("gloomy", empty));
        }
        return topics.get(random.nextInt(topics.size()));
    }

    private static Topic news(ServerLevel world, Village village) {
        long today = WorldCalendar.now(world).dayIndex();
        for (CivilRegistry.Entry e : VillageState.of(village).registry) {
            if (today - e.day() > NEWS_DAYS) {
                break;
            }
            switch (e.kind()) {
                case CivilRegistry.BIRTH -> {
                    return new Topic("birth", Component.literal(e.name()));
                }
                case CivilRegistry.MARRIAGE -> {
                    return new Topic("marriage", Component.translatable("chat.mca_vie.talk.couple", e.name(), e.other()));
                }
                case CivilRegistry.DEATH -> {
                    return new Topic("death", Component.literal(e.name()));
                }
                case CivilRegistry.JUSTICE -> {
                    return new Topic("justice", Component.literal(e.name()));
                }
                default -> {
                }
            }
        }
        return null;
    }

    public static void witness(ServerLevel world, Entity source, String event, Component subject, UUID... involved) {
        if (!SocialConfig.get().showChats) {
            return;
        }
        List<VillagerEntityMCA> witnesses = world.getEntitiesOfClass(VillagerEntityMCA.class, source.getBoundingBox().inflate(WITNESS_RADIUS),
                v -> v != source && canSpeak(v) && !v.isSleeping() && !List.of(involved).contains(v.getUUID()));
        if (witnesses.isEmpty()) {
            return;
        }
        RandomSource random = world.getRandom();
        long time = world.getGameTime();
        for (int i = 0; i < Math.min(2, witnesses.size()); i++) {
            VillagerEntityMCA witness = witnesses.remove(random.nextInt(witnesses.size()));
            Component text = Component.translatable("chat.mca_vie.witness." + event + "." + random.nextInt(WITNESS_VARIANTS), subject);
            PENDING.add(new Line(world, witness, text, event.equals("hit") || event.equals("theft"), time + 10L + i * (long) GAP));
        }
    }

    public static void remark(ServerLevel world, VillagerEntityMCA speaker, Component text) {
        if (SocialConfig.get().showChats && canSpeak(speaker) && !speaker.isSleeping()) {
            PENDING.add(new Line(world, speaker, text, false, world.getGameTime() + 10L));
        }
    }

    private static void tick(ServerLevel world) {
        if (PENDING.isEmpty()) {
            return;
        }
        long time = world.getGameTime();
        Iterator<Line> it = PENDING.iterator();
        while (it.hasNext()) {
            Line line = it.next();
            if (line.world != world) {
                if (line.world.getServer() != world.getServer()) {
                    it.remove();
                }
                continue;
            }
            if (line.at > time + 20L * 60) {
                it.remove();
                continue;
            }
            if (line.at > time) {
                continue;
            }
            it.remove();
            if (line.speaker.isAlive() && line.speaker.level() == world) {
                say(world, line.speaker, line.text, line.angry);
            }
        }
    }

    private static void say(ServerLevel world, VillagerEntityMCA speaker, Component text, boolean angry) {
        SocialConfig config = SocialConfig.get();
        double max = (double) config.chatRadius * config.chatRadius;
        MutableComponent head = Component.literal("   ").withStyle(s -> s.withInsertion(HEAD_MARKER + speaker.getId() + ":" + speaker.getUUID()));
        MutableComponent chat = Component.empty().append(head).append(Component.translatable("chat.mca_vie.social.said", speaker.getName(), text)
                .withStyle(angry ? ChatFormatting.RED : ChatFormatting.GRAY, ChatFormatting.ITALIC));
        for (ServerPlayer player : world.players()) {
            if (player.distanceToSqr(speaker) <= max) {
                player.sendSystemMessage(chat);
            }
        }
    }
}
