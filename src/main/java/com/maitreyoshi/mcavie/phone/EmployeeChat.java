package com.maitreyoshi.mcavie.phone;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import com.maitreyoshi.mcavie.work.WorkStatus;
import java.util.Locale;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Messagerie de l'appli Employés : réponses par mots-clés (EmployeeDialogues), ordres par texte, conséquences sur les cœurs.
public final class EmployeeChat {
    public static final Identifier REQUEST = MCAVie.id("employee_chat_request");
    public static final Identifier SEND = MCAVie.id("employee_chat_send");
    public static final Identifier LOG = MCAVie.id("employee_chat_log");
    public static final int MAX_TEXT = 200;
    private static final int MAX_LOG = 30;
    private static final long SPAM_TICKS = 30;
    private static final long BLOCK_TICKS = 6000;
    private static final long TENSION_DECAY_TICKS = 2400;
    private static final long COMPLIMENT_COOLDOWN = 1200;
    private static final int COMPLIMENTS_PER_DAY = 3;
    private static final int RESIGN_TENSION = 9;
    private static final int BLOCK_TENSION = 6;
    private static final Random RANDOM = new Random();
    private static final Pattern TOKEN = Pattern.compile("\\{t:([a-zA-Z0-9_.:-]+)}");

    private EmployeeChat() {
    }

    public record Line(boolean outgoing, String text) {
    }

    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, responseSender) -> {
            UUID uuid = buf.readUUID();
            server.execute(() -> {
                if (PhoneEmployees.allowed(player)) {
                    sendLog(player, uuid);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(SEND, (server, player, handler, buf, responseSender) -> {
            UUID uuid = buf.readUUID();
            String text = buf.readUtf(MAX_TEXT);
            String lang = buf.readUtf(16);
            server.execute(() -> {
                if (!PhoneEmployees.allowed(player)) {
                    return;
                }
                receive(player, uuid, text, lang);
                sendLog(player, uuid);
                PhoneEmployees.send(player);
            });
        });
    }

    // instantané de l'humeur pour répondre quand le PNJ n'est pas chargé
    static void snapshot(VillagerEntityMCA villager, WorkRegistry.Record record) {
        chat(record).putInt("mood", villager.getVillagerBrain().getMoodValue());
        if (record.employer != null) {
            Memories memories = villager.getVillagerBrain().getMemories().get(record.employer);
            chat(record).putInt("hearts", memories != null ? memories.getHearts() : 0);
        }
    }

    private static CompoundTag chat(WorkRegistry.Record record) {
        if (record.chat == null) {
            record.chat = new CompoundTag();
        }
        return record.chat;
    }

    private static void sendLog(ServerPlayer player, UUID uuid) {
        WorkRegistry.Record record = WorkRegistry.get(player.level().getServer()).get(uuid);
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(uuid);
        ListTag log = record != null && player.getUUID().equals(record.employer) && record.chat != null
                ? record.chat.getListOrEmpty("log") : new ListTag();
        buf.writeVarInt(log.size());
        for (int i = 0; i < log.size(); i++) {
            CompoundTag line = log.getCompoundOrEmpty(i);
            buf.writeBoolean(line.getBooleanOr("o", false));
            buf.writeUtf(line.getStringOr("t", ""));
        }
        ServerPlayNetworking.send(player, LOG, buf);
    }

    public static List<Line> readLog(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Line> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            boolean outgoing = buf.readBoolean();
            String text = buf.readUtf();
            lines.add(new Line(outgoing, outgoing ? text : localize(text)));
        }
        return lines;
    }

    // le serveur dédié n'a pas les traductions : les noms traduisibles partent en jetons, résolus par le client
    private static String token(Component text) {
        return text.getContents() instanceof TranslatableContents content ? "{t:" + content.getKey() + "}" : text.getString();
    }

    private static String localize(String text) {
        return TOKEN.matcher(text).replaceAll(m -> Matcher.quoteReplacement(Component.translatable(m.group(1)).getString()));
    }

    private static void log(CompoundTag chat, boolean outgoing, String text) {
        ListTag log = chat.getListOrEmpty("log");
        CompoundTag line = new CompoundTag();
        line.putBoolean("o", outgoing);
        line.putString("t", text);
        log.add(line);
        while (log.size() > MAX_LOG) {
            log.remove(0);
        }
        chat.put("log", log);
    }

    private static void receive(ServerPlayer player, UUID uuid, String raw, String lang) {
        MinecraftServer server = player.level().getServer();
        WorkRegistry registry = WorkRegistry.get(server);
        WorkRegistry.Record record = registry.get(uuid);
        if (record == null || !player.getUUID().equals(record.employer) || record.job == Job.NONE
                || PhoneEmployees.isDead(server, uuid)) {
            return;
        }
        String text = raw.trim();
        CompoundTag chat = chat(record);
        EmployeeDialogues dialogues = EmployeeDialogues.get(lang);
        long now = server.overworld().getGameTime();
        log(chat, true, text);
        registry.setDirty();
        if (dialogues == null) {
            return;
        }
        // la tension retombe avec le temps
        long elapsed = now - chat.getLongOr("lastTime", 0L);
        if (chat.getIntOr("tension", 0) > 0 && elapsed > TENSION_DECAY_TICKS) {
            chat.putInt("tension", Math.max(0, chat.getIntOr("tension", 0) - (int) (elapsed / TENSION_DECAY_TICKS)));
        }
        String normalized = EmployeeDialogues.normalize(text);
        // bouderie : messages ignorés jusqu'à la fin du blocage
        long blocked = chat.getLongOr("blockedUntil", 0L);
        if (blocked > now) {
            chat.putLong("lastTime", now);
            if (!chat.getBooleanOr("blockNoticed", false)) {
                chat.putBoolean("blockNoticed", true);
                answer(chat, "...");
            }
            return;
        }
        VillagerEntityMCA villager = PhoneEmployees.find(server, uuid);
        Context context = new Context(player, uuid, record, villager, lang);
        if (blocked != 0) {
            chat.putLong("blockedUntil", 0);
            chat.putBoolean("blockNoticed", false);
            chat.putInt("streak", 0);
            chat.putLong("lastTime", now);
            say(chat, dialogues, "blocked_end", context);
            return;
        }
        // anti-spam : trop rapide ou même message répété
        boolean repeated = !normalized.isEmpty() && normalized.equals(chat.getStringOr("lastText", ""));
        if (elapsed < SPAM_TICKS || repeated) {
            chat.putInt("spam", chat.getIntOr("spam", 0) + 1);
        } else {
            chat.putInt("spam", 0);
        }
        chat.putLong("lastTime", now);
        chat.putString("lastText", normalized);
        if (chat.getIntOr("spam", 0) >= 3) {
            say(chat, dialogues, "spam", context);
            return;
        }
        if (normalized.isEmpty()) {
            say(chat, dialogues, text.isEmpty() ? "empty" : "emoji_only", context);
            return;
        }
        EmployeeDialogues.Match match = dialogues.classify(normalized);
        String effect = match != null ? match.intent().effect() : "none";
        // confirmation de licenciement en attente
        if (chat.getBooleanOr("fireAsk", false)) {
            chat.putBoolean("fireAsk", false);
            if ("yes".equals(effect)) {
                say(chat, dialogues, "fire_done", context);
                String name = PhoneEmployees.dismiss(server, uuid, record);
                player.sendOverlayMessage(Component.translatable("job.mca_vie.fired", name));
                return;
            }
            if ("no".equals(effect)) {
                say(chat, dialogues, "fire_cancel", context);
                return;
            }
        }
        EmployeeTopics.Topic topic = EmployeeTopics.analyze(normalized);
        if (topic != null && EmployeeTopics.ANAPHORA.equals(topic.subject())) {
            // « il a quel âge ? » : la personne dont on vient de parler
            String lastSubject = chat.getStringOr("lastSubject", "");
            topic = EmployeeTopics.PERSONS.contains(lastSubject)
                    ? new EmployeeTopics.Topic(lastSubject, topic.intent(), chat.getStringOr("lastDetail", ""), false, true) : null;
        }
        if (topic != null && topicWins(topic, effect, normalized, dialogues)) {
            sayTopic(chat, dialogues, topic, context);
            return;
        }
        if ("ask_you".equals(effect) && "topic".equals(chat.getStringOr("lastIntent", "")) && normalized.split(" ").length <= 4) {
            say(chat, dialogues, "how_are_you", context);
            return;
        }
        if (match == null) {
            say(chat, dialogues, EmployeeDialogues.gibberish(normalized) ? "gibberish" : "fallback", context);
            return;
        }
        String id = match.intent().id();
        // « pourquoi ? », « et toi ? » : suite du sujet précédent
        if (("ask_why".equals(effect) || "ask_you".equals(effect)) && normalized.split(" ").length <= 4) {
            String follow = dialogues.followup(chat.getStringOr("lastIntent", ""), "ask_why".equals(effect) ? "why" : "you",
                    chat.getStringOr("lastReply", ""), RANDOM);
            if (follow != null) {
                answer(chat, fill(follow, context));
                return;
            }
        }
        switch (effect) {
            case "insult", "offensive", "threat" -> {
                boolean strong = !"insult".equals(effect);
                int tension = chat.getIntOr("tension", 0) + (strong ? 2 : 1);
                int streak = chat.getIntOr("streak", 0) + 1;
                chat.putInt("tension", tension);
                chat.putInt("streak", streak);
                consequence(context, strong ? -6 : -3, strong ? -4 : -2);
                if (tension >= RESIGN_TENSION) {
                    say(chat, dialogues, "resign", context);
                    chat.putInt("tension", 0);
                    chat.putInt("streak", 0);
                    String name = PhoneEmployees.dismiss(server, uuid, record);
                    player.sendOverlayMessage(Component.translatable("msg.mca_vie.employee.resigned", name));
                    return;
                }
                if (streak >= 4 || tension >= BLOCK_TENSION) {
                    say(chat, dialogues, id, context);
                    chat.putLong("blockedUntil", now + BLOCK_TICKS);
                    chat.putBoolean("blockNoticed", false);
                    return;
                }
                say(chat, dialogues, streak == 3 ? "insult_warning" : id, context);
                return;
            }
            case "apology" -> {
                chat.putInt("streak", 0);
                int tension = chat.getIntOr("tension", 0);
                if (tension > 0) {
                    chat.putInt("tension", Math.max(0, tension - 2));
                    consequence(context, 0, 1);
                    say(chat, dialogues, "apology_accepted", context);
                    return;
                }
            }
            case "compliment", "thanks" -> {
                chat.putInt("streak", 0);
                chat.putInt("tension", Math.max(0, chat.getIntOr("tension", 0) - 1));
                long day = PhoneEmployees.today(server);
                if (chat.getLongOr("complDay", 0L) != day) {
                    chat.putLong("complDay", day);
                    chat.putInt("complCount", 0);
                }
                if (now >= chat.getLongOr("complNext", 0L) && chat.getIntOr("complCount", 0) < COMPLIMENTS_PER_DAY) {
                    chat.putInt("complCount", chat.getIntOr("complCount", 0) + 1);
                    chat.putLong("complNext", now + COMPLIMENT_COOLDOWN);
                    consequence(context, 1, 1);
                } else if ("compliment".equals(effect)) {
                    say(chat, dialogues, "compliment_capped", context);
                    return;
                }
            }
            case "order_follow", "order_stay", "order_home", "order_work" -> {
                chat.putInt("streak", 0);
                Boolean loaded = PhoneEmployees.order(player, uuid, effect.substring("order_".length()));
                say(chat, dialogues, Boolean.FALSE.equals(loaded) ? "order_offline" : id, context);
                return;
            }
            case "order_fire" -> {
                chat.putBoolean("fireAsk", true);
                say(chat, dialogues, "fire_ask", context);
                return;
            }
            default -> chat.putInt("streak", 0);
        }
        say(chat, dialogues, id, context);
    }

    private static void say(CompoundTag chat, EmployeeDialogues dialogues, String id, Context context) {
        EmployeeLife life = EmployeeLife.get(context.lang);
        EmployeeLife.State state = context.life(chat);
        useWarmth(chat);
        EmployeeDialogues.Intent intent = dialogues.intent(id);
        boolean casual = intent != null && EmployeeLife.casual(id, intent.effect());
        String last = chat.getStringOr("lastReply", "");
        String contextual = life != null ? life.replace(id, casual, state, recent(chat), RANDOM) : null;
        String raw = contextual != null ? contextual : dialogues.reply(id, state.mood(), last, RANDOM);
        if (life != null && contextual == null && casual) {
            String extra = life.addition(id, state, RANDOM);
            if (extra != null) {
                raw += " " + extra;
            }
        }
        String text = fill(raw, context);
        chat.putString("lastIntent", id);
        chat.putString("lastSubject", "");
        answer(chat, text);
    }

    // un sujet reconnu passe avant la réponse par mots-clés, sauf ordres et vraies insultes (« ta mère » seul en reste une)
    private static boolean topicWins(EmployeeTopics.Topic topic, String effect, String normalized, EmployeeDialogues dialogues) {
        if (Set.of("none", "ask_status", "ask_family", "ask_where", "ask_time", "ask_health", "ask_harvest", "ask_why", "ask_you", "yes", "no", "compliment", "thanks").contains(effect)) {
            return true;
        }
        if (!Set.of("insult", "offensive").contains(effect) || !topic.explicit()) {
            return false;
        }
        String cleaned = (" " + normalized + " ").replace(" ta mere ", " ").trim();
        EmployeeDialogues.Match other = dialogues.classify(cleaned);
        return other == null || !Set.of("insult", "offensive", "threat").contains(other.intent().effect());
    }

    private static void sayTopic(CompoundTag chat, EmployeeDialogues dialogues, EmployeeTopics.Topic topic, Context context) {
        EmployeeLife life = EmployeeLife.get(context.lang);
        EmployeeLife.State state = context.life(chat);
        useWarmth(chat);
        List<String> recent = recent(chat);
        String raw = life != null ? life.answer(topic, state, recent, RANDOM) : null;
        if (raw == null && life != null) {
            raw = life.relance(recent, RANDOM, state);
        }
        if (raw == null) {
            raw = dialogues.reply("fallback", state.mood(), chat.getStringOr("lastReply", ""), RANDOM);
        }
        boolean declaration = Set.of("love", "miss", "likeme", "proposal").contains(topic.subject());
        if (declaration) {
            // après une déclaration bien reçue, pas de réplique sèche juste derrière
            chat.putInt("warm", topic.negated() || state.hearts() < 0 ? 0 : 3);
        }
        chat.putString("lastIntent", "topic");
        chat.putString("lastSubject", topic.subject());
        chat.putString("lastDetail", topic.detail());
        answer(chat, fill(raw, context));
    }

    private static void useWarmth(CompoundTag chat) {
        chat.putInt("warm", Math.max(0, chat.getIntOr("warm", 0) - 1));
    }

    private static List<String> recent(CompoundTag chat) {
        ListTag list = chat.getListOrEmpty("recent");
        List<String> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getStringOr(i, ""));
        }
        return out;
    }

    private static void answer(CompoundTag chat, String text) {
        chat.putString("lastReply", text);
        ListTag recent = chat.getListOrEmpty("recent");
        recent.add(StringTag.valueOf(text));
        while (recent.size() > 3) {
            recent.remove(0);
        }
        chat.put("recent", recent);
        log(chat, false, text);
    }

    // cœurs envers l'employeur et humeur : tout de suite si le PNJ est chargé, sinon à son rechargement
    private static void consequence(Context context, int hearts, int mood) {
        if (context.villager != null) {
            if (hearts != 0) {
                context.villager.getVillagerBrain().getMemoriesForPlayer(context.player).modHearts(hearts);
            }
            if (mood != 0) {
                context.villager.getVillagerBrain().modifyMoodValue(mood);
            }
        } else {
            context.record.pendingHearts += hearts;
            context.record.pendingMood += mood;
        }
    }

    private record Context(ServerPlayer player, UUID uuid, WorkRegistry.Record record, @Nullable VillagerEntityMCA villager, String lang) {
        EmployeeLife.State life(CompoundTag chat) {
            MinecraftServer server = player.level().getServer();
            int hour = (int) ((server.overworld().getOverworldClockTime() + 6000) % 24000 / 1000);
            String part = EmployeeLife.partOfDay(hour);
            boolean working = villager != null ? WorkManager.isWorking(villager) : record.lastStatus >= 0;
            // PNJ non chargé : présumé endormi la nuit, sauf s'il travaillait à son dernier passage
            String activity = villager != null && villager.isSleeping() ? "sleeping" : working ? "working"
                    : villager == null && "night".equals(part) ? "sleeping" : "idle";
            int hearts = villager != null ? villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts()
                    : chat.contains("hearts") ? chat.getIntOr("hearts", 0) : 20;
            FamilyTree tree = FamilyTree.get(server.overworld());
            FamilyTreeNode node = tree.getOrEmpty(uuid).orElse(null);
            String kin = "";
            if (node != null) {
                UUID self = player.getUUID();
                boolean promised = node.getRelationshipState() == RelationshipState.ENGAGED || node.getRelationshipState() == RelationshipState.PROMISED;
                kin = node.partner().equals(self) ? (promised ? "fiance" : "spouse")
                        : node.father().equals(self) || node.mother().equals(self) ? "child"
                        : node.children().contains(self) ? "parent" : "";
            }
            boolean warm = chat.getIntOr("warm", 0) > 0 && chat.getIntOr("tension", 0) < 2;
            String mood = warm ? "good" : EmployeeLife.adjustMood(mood(chat), EmployeeLife.State.relation(hearts, kin));
            return new EmployeeLife.State(activity, kin, hearts, part, record.job.name().toLowerCase(Locale.ROOT), mood, warm, profile(server, tree, node));
        }

        private EmployeeLife.Profile profile(MinecraftServer server, FamilyTree tree, @Nullable FamilyTreeNode node) {
            ServerLevel world = server.overworld();
            String weather = world.isThundering() ? "thunder" : world.isRaining() ? "rain" : "clear";
            String village = villager != null
                    ? villager.getResidency().getHomeVillage().map(Village::getName).orElse(null) : null;
            if (node == null) {
                return new EmployeeLife.Profile(false, null, false, List.of(), null, null, List.of(), village, weather);
            }
            long today = WorldCalendar.now(world).dayIndex();
            return new EmployeeLife.Profile(node.gender() == Gender.FEMALE, person(server, tree, node.partner(), today),
                    node.getRelationshipState() == RelationshipState.WIDOW,
                    people(server, tree, node.children(), today), person(server, tree, node.father(), today),
                    person(server, tree, node.mother(), today), people(server, tree, node.siblings(), today), village, weather);
        }

        private List<EmployeeLife.Person> people(MinecraftServer server, FamilyTree tree, Set<UUID> ids, long today) {
            List<EmployeeLife.Person> list = new ArrayList<>();
            for (UUID id : ids) {
                EmployeeLife.Person person = person(server, tree, id, today);
                if (person != null) {
                    list.add(person);
                }
            }
            return list;
        }

        @Nullable
        private EmployeeLife.Person person(MinecraftServer server, FamilyTree tree, @Nullable UUID id, long today) {
            if (!FamilyTreeNode.isValid(id)) {
                return null;
            }
            FamilyTreeNode other = tree.getOrEmpty(id).orElse(null);
            if (other == null) {
                return null;
            }
            Long birth = LifeRegistry.get(server).getBirthDay(id);
            return new EmployeeLife.Person(other.getName(), other.gender() == Gender.FEMALE, !other.isDeceased(), other.isPlayer(),
                    birth == null ? null : LifeCycle.ageInYears(birth, today));
        }

        String mood(CompoundTag chat) {
            int value = villager != null ? villager.getVillagerBrain().getMoodValue() : chat.getIntOr("mood", 0);
            if (chat.getIntOr("tension", 0) >= 2) {
                return "bad";
            }
            return value >= 5 ? "good" : value <= -5 ? "bad" : "neutral";
        }
    }

    private static String fill(String source, Context c) {
        boolean female = FamilyTree.get(c.player.level().getServer().overworld()).getOrEmpty(c.uuid)
                .map(n -> n.gender() == Gender.FEMALE).orElse(false);
        String text = EmployeeLife.accord(source, female);
        if (!text.contains("{")) {
            return text;
        }
        MinecraftServer server = c.player.level().getServer();
        WorkRegistry.Record r = c.record;
        String name = c.villager != null ? c.villager.getName().getString() : PhoneEmployees.nameOf(server, c.uuid, r);
        BlockPos pos = c.villager != null ? c.villager.blockPosition() : r.lastPos != null ? r.lastPos : r.center;
        String dimension = c.villager != null ? c.villager.level().dimension().identifier().toString()
                : r.lastDimension != null ? r.lastDimension.toString() : "";
        String distance = "?";
        if (pos != null && c.player.level().dimension().identifier().toString().equals(dimension)) {
            distance = String.valueOf((int) Math.sqrt(c.player.blockPosition().distSqr(pos)));
        }
        float health = c.villager != null ? c.villager.getHealth() : r.lastHealth;
        float max = c.villager != null ? c.villager.getMaxHealth() : r.lastMaxHealth;
        int status = c.villager != null ? PhoneEmployees.statusOf(c.villager) : r.lastStatus;
        long time = (server.overworld().getOverworldClockTime() + 6000) % 24000;
        FamilyTree tree = FamilyTree.get(server.overworld());
        FamilyTreeNode node = tree.getOrEmpty(c.uuid).orElse(null);
        String partner = node != null && !node.partner().equals(Util.NIL_UUID)
                ? tree.getOrEmpty(node.partner()).map(FamilyTreeNode::getName).orElse("?")
                : token(Component.translatable("gui.mca_vie.phone.employees.nobody"));
        int children = node != null ? node.children().size() : 0;
        return text.replace("{joueur}", c.player.getName().getString())
                .replace("{nom}", name)
                .replace("{metier}", token(r.job.getName()))
                .replace("{etat}", token(status < 0 ? Component.translatable("gui.mca_vie.phone.employees.resting")
                        : WorkStatus.byId(status).getText()))
                .replace("{lieu}", pos != null ? pos.getX() + ", " + pos.getY() + ", " + pos.getZ() : "?")
                .replace("{distance}", distance)
                .replace("{recolte}", harvest(server, r))
                .replace("{sante}", Math.round(health) + "/" + Math.round(max))
                .replace("{heure}", String.format("%02dh%02d", time / 1000, time % 1000 * 60 / 1000))
                .replace("{conjoint}", partner)
                .replace("{enfants}", String.valueOf(children));
    }

    private static String harvest(MinecraftServer server, WorkRegistry.Record record) {
        CompoundTag report = PhoneEmployees.report(server, record);
        if (report.isEmpty()) {
            return token(Component.translatable("gui.mca_vie.phone.employees.report_none"));
        }
        List<String> keys = new ArrayList<>(report.keySet());
        keys.sort(Comparator.comparingInt(report::getInt).reversed());
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(4, keys.size()); i++) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(keys.get(i)));
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(report.getInt(keys.get(i))).append(' ').append(token(item.getDescription()));
        }
        return b.toString();
    }
}
