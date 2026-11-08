package com.maitreyoshi.mcavie.phone;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Répliques liées à la vie réelle du PNJ (activité, métier, famille, relation, moment de la journée).
// Complète EmployeeDialogues : une réplique manquante laisse simplement la réponse générique.
public final class EmployeeLife {
    private static final Map<String, EmployeeLife> CACHE = new HashMap<>();
    private static final Set<String> SYSTEM = Set.of("empty", "gibberish", "emoji_only", "spam", "blocked_end", "resign",
            "fire_ask", "fire_done", "fire_cancel", "order_offline", "insult_warning", "apology_accepted", "compliment_capped");
    private static final Set<String> CASUAL_EFFECTS = Set.of("none", "ask_status", "ask_family", "compliment", "thanks", "apology");
    private static final Set<String> DOING = Set.of("ask_status", "work_going");
    private static final Set<String> ROMANCE = Set.of("marriage", "flirt_heavy", "sex");
    // remplis ensuite par EmployeeChat.fill
    private static final Set<String> LATE_VARS = Set.of("joueur", "nom", "metier", "etat", "lieu", "distance", "recolte", "sante", "heure", "conjoint", "enfants");
    private static final Pattern VARIABLE = Pattern.compile("\\{([a-z_]*)}");
    private static final Pattern ALTERNATIVE = Pattern.compile("\\{([^{}|]*)\\|([^{}|]*)}");
    private static final double TONE_CHANCE = 0.35;
    private static final double TIME_CHANCE = 0.6;
    private static final int COLD_HEARTS = 0;
    private static final int DISTANT_HEARTS = 10;
    private static final int CLOSE_HEARTS = 50;

    public record Person(String name, boolean female, boolean alive, boolean player, @Nullable Integer age) {
    }

    public record Profile(boolean female, @Nullable Person spouse, boolean widowed, List<Person> children,
                          @Nullable Person father, @Nullable Person mother, List<Person> siblings,
                          @Nullable String village, String weather) {
    }

    // activity : sleeping, working, idle ; kin : spouse, fiance, child (le PNJ est l'enfant du joueur), parent ou "" ;
    // mood : humeur déjà ajustée ; warm : suite d'une déclaration acceptée
    public record State(String activity, String kin, int hearts, String part, String job, String mood, boolean warm, Profile profile) {
        public String relation() {
            return relation(hearts, kin);
        }

        public static String relation(int hearts, String kin) {
            if (hearts < COLD_HEARTS) {
                return "cold";
            }
            if (!kin.isEmpty()) {
                return kin;
            }
            return hearts < DISTANT_HEARTS ? "distant" : hearts >= CLOSE_HEARTS ? "close" : "";
        }

        boolean family() {
            return "child".equals(kin) || "parent".equals(kin);
        }

        boolean couple() {
            return "spouse".equals(kin) || "fiance".equals(kin);
        }

        // froid, neutre ou chaleureux : cœurs corrigés par l'humeur, lien de parenté, contexte de la conversation
        String tone() {
            if (warm) {
                return "warm";
            }
            int effective = hearts + ("good".equals(mood) ? 10 : "bad".equals(mood) ? -15 : 0);
            if (effective < COLD_HEARTS) {
                return "cold";
            }
            return !kin.isEmpty() || effective >= CLOSE_HEARTS ? "warm" : "neutral";
        }
    }

    private final Map<String, List<String>> pools = new HashMap<>();

    public EmployeeLife(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            List<String> lines = new ArrayList<>();
            entry.getValue().getAsJsonArray().forEach(e -> lines.add(e.getAsString()));
            pools.put(entry.getKey(), lines);
        }
    }

    @Nullable
    public static EmployeeLife get(String language) {
        String lang = EmployeeDialogues.supported(language);
        if (!CACHE.containsKey(lang)) {
            CACHE.put(lang, load(lang));
        }
        return CACHE.get(lang);
    }

    @Nullable
    private static EmployeeLife load(String lang) {
        try (InputStream in = EmployeeLife.class.getResourceAsStream(EmployeeDialogues.PATH + "life_" + lang + ".json")) {
            return in == null ? null : new EmployeeLife(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            com.maitreyoshi.mcavie.MCAVie.LOGGER.error("Répliques de vie illisibles ({})", lang, e);
            return null;
        }
    }

    public boolean has(String key) {
        return pools.containsKey(key) && !pools.get(key).isEmpty();
    }

    public static String partOfDay(int hour) {
        return hour >= 5 && hour < 12 ? "morning" : hour >= 12 && hour < 18 ? "day" : hour >= 18 && hour < 22 ? "evening" : "night";
    }

    public static String adjustMood(String mood, String relation) {
        return switch (relation) {
            case "cold" -> "good".equals(mood) ? "neutral" : "bad";
            case "distant" -> "good".equals(mood) ? "neutral" : mood;
            case "spouse", "fiance", "child", "parent", "close" -> "neutral".equals(mood) ? "good" : mood;
            default -> mood;
        };
    }

    // accords des répliques : {e} et {masculin|féminin}, selon le genre du PNJ qui parle
    public static String accord(String text, boolean female) {
        if (!text.contains("{")) {
            return text;
        }
        return ALTERNATIVE.matcher(text.replace("{e}", female ? "e" : "")).replaceAll(female ? "$2" : "$1");
    }

    public static boolean casual(String id, String effect) {
        return !SYSTEM.contains(id) && CASUAL_EFFECTS.contains(effect);
    }

    // réplique d'un sujet reconnu par EmployeeTopics ; null si aucune tournure n'est utilisable
    @Nullable
    public String answer(EmployeeTopics.Topic t, State s, Collection<String> recent, Random random) {
        Map<String, String> v = vars(t, s);
        return switch (t.subject()) {
            case "love", "miss", "likeme", "proposal" -> relation(t, s, v, recent, random);
            case "family" -> first(v, recent, random, familyKey(s));
            default -> person(t, s, v, recent, random);
        };
    }

    @Nullable
    public String relance(Collection<String> recent, Random random, State s) {
        return first(vars(null, s), recent, random, "relance");
    }

    private String relation(EmployeeTopics.Topic t, State s, Map<String, String> v, Collection<String> recent, Random random) {
        String tone = s.tone();
        String subject = t.subject();
        if (t.negated()) {
            return first(v, recent, random, "love.no");
        }
        if ("proposal".equals(subject)) {
            String key = s.couple() ? "proposal.spouse" : s.family() ? "proposal.family" : s.profile.spouse() != null && s.profile.spouse().alive() ? "proposal.taken"
                    : "cold".equals(tone) ? "proposal.cold" : "warm".equals(tone) ? "proposal.close" : "proposal.neutral";
            return first(v, recent, random, key);
        }
        if ("love".equals(subject)) {
            String key = s.couple() ? "love.spouse" : s.family() ? "love.family"
                    : "cold".equals(tone) ? "love.cold" : "warm".equals(tone) ? "love.close" : "love.neutral";
            return first(v, recent, random, key, "love.neutral");
        }
        return first(v, recent, random, subject + "." + tone, subject + ".neutral");
    }

    private String familyKey(State s) {
        Profile p = s.profile;
        boolean dead = p.widowed() || p.spouse() != null && !p.spouse().alive();
        int kids = alive(p.children()).size();
        if (s.couple() && p.spouse() != null && p.spouse().alive()) {
            return kids > 0 ? "family.player_kids" : "family.player";
        }
        if (p.spouse() != null && p.spouse().alive()) {
            return kids > 0 ? "family.married_kids" : "family.married";
        }
        if (dead) {
            return "family.widow";
        }
        return kids > 0 ? "family.kids" : "family.alone";
    }

    private String person(EmployeeTopics.Topic t, State s, Map<String, String> v, Collection<String> recent, Random random) {
        String subject = t.subject();
        String prefix = subject;
        String fact;
        List<Person> people = List.of();
        Profile p = s.profile;
        switch (subject) {
            case "spouse" -> {
                Person spouse = p.spouse();
                fact = s.couple() && spouse != null && spouse.alive() ? "you" : spouse != null && spouse.alive() ? "yes" : p.widowed() || spouse != null ? "dead" : "no";
                people = spouse != null ? List.of(spouse) : List.of();
            }
            case "children" -> {
                List<Person> all = filter(p.children(), t.detail());
                people = alive(all);
                fact = people.size() > 1 ? "many" : people.size() == 1 ? "one" : all.isEmpty() ? "none" : "dead";
            }
            case "siblings" -> {
                List<Person> all = filter(p.siblings(), t.detail());
                people = alive(all);
                fact = people.size() > 1 ? "many" : people.size() == 1 ? "one" : all.isEmpty() ? "none" : "dead";
            }
            default -> {
                if (!"both".equals(t.detail())) {
                    Person parent = "father".equals(t.detail()) ? p.father() : p.mother();
                    prefix = "parent";
                    fact = parent == null ? "none" : parent.player() ? "you" : parent.alive() ? "yes" : "dead";
                    people = parent != null ? List.of(parent) : List.of();
                } else {
                    Person f = p.father();
                    Person m = p.mother();
                    int known = (f != null ? 1 : 0) + (m != null ? 1 : 0);
                    int living = (f != null && f.alive() ? 1 : 0) + (m != null && m.alive() ? 1 : 0);
                    fact = "child".equals(s.kin()) ? "you" : known == 0 ? "none" : known == 1 ? (living == 1 ? "one" : "dead")
                            : living == 2 ? "alive" : living == 0 ? "dead" : "mixed";
                    if (f != null && f.alive()) {
                        people = m != null && m.alive() ? List.of(f, m) : List.of(f);
                    } else if (m != null && m.alive()) {
                        people = List.of(m);
                    }
                }
            }
        }
        String intent = t.intent();
        boolean living = Set.of("yes", "one", "many", "alive", "mixed").contains(fact);
        String tone = s.tone();
        if ("age".equals(intent) && living) {
            boolean known = people.stream().allMatch(x -> x.age() != null);
            return first(v, recent, random, !known ? "age.unknown" : people.size() > 1 ? "age.many" : "age.one");
        }
        if ("where".equals(intent) && living) {
            return first(v, recent, random, people.size() > 1 ? "where.many" : "where.one");
        }
        List<String> keys = new ArrayList<>();
        keys.add(prefix + "." + intent + "." + fact + "." + tone);
        if ("cold".equals(tone) && living) {
            keys.add("private.cold");
        }
        keys.add(prefix + "." + intent + "." + fact);
        keys.add(prefix + ".has." + fact);
        keys.add(prefix + ".who." + fact);
        return first(v, recent, random, keys.toArray(String[]::new));
    }

    private static List<Person> alive(List<Person> people) {
        return people.stream().filter(Person::alive).toList();
    }

    private static List<Person> filter(List<Person> people, String detail) {
        return switch (detail) {
            case "son", "brother" -> people.stream().filter(p -> !p.female()).toList();
            case "daughter", "sister" -> people.stream().filter(Person::female).toList();
            default -> people;
        };
    }

    private String word(String key) {
        List<String> list = pools.get("word." + key);
        return list == null || list.isEmpty() ? "" : list.get(0);
    }

    private void put(Map<String, String> v, String key, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            v.put(key, value);
        }
    }

    private String join(List<Person> people) {
        List<String> names = people.stream().map(Person::name).filter(n -> !n.isBlank()).toList();
        if (names.size() < 2) {
            return names.isEmpty() ? "" : names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " " + word("and") + " " + names.get(names.size() - 1);
    }

    private String childrenPhrase(int count) {
        return count == 1 ? word("one_child") : count + " " + word("children");
    }

    // variables disponibles : une réplique qui en réclame une autre est écartée
    private Map<String, String> vars(@Nullable EmployeeTopics.Topic t, State s) {
        Profile p = s.profile;
        Map<String, String> v = new HashMap<>();
        v.put("e", p.female() ? "e" : "");
        v.put("widow", word(p.female() ? "widow" : "widower"));
        put(v, "village", p.village());
        Person spouse = p.spouse();
        if (spouse != null) {
            put(v, "spouse", spouse.name());
            v.put("spouse_word", word(spouse.female() ? "wife" : "husband"));
            v.put("he", word(spouse.female() ? "she" : "he"));
        }
        List<Person> kids = alive(p.children());
        v.put("children_phrase", childrenPhrase(kids.size()));
        if (t == null) {
            return v;
        }
        switch (t.subject()) {
            case "children" -> {
                List<Person> all = filter(p.children(), t.detail());
                List<Person> people = alive(all);
                people(v, people, "child", "children_count", "child_list");
                v.put("s", people.size() > 1 ? "s" : "");
                if (people.size() == 1) {
                    v.put("child_word", word(people.get(0).female() ? "daughter" : "son"));
                }
                v.put("no_child", word("son".equals(t.detail()) ? "no_son" : "daughter".equals(t.detail()) ? "no_daughter" : "no_child"));
                ages(v, people);
            }
            case "siblings" -> {
                List<Person> all = filter(p.siblings(), t.detail());
                List<Person> people = alive(all);
                people(v, people, "sibling", "sibling_count", "sibling_list");
                v.put("s", people.size() > 1 ? "s" : "");
                if (people.size() == 1) {
                    v.put("sibling_word", word(people.get(0).female() ? "sister" : "brother"));
                }
                v.put("no_sibling", word("brother".equals(t.detail()) ? "no_brother" : "sister".equals(t.detail()) ? "no_sister" : "no_sibling"));
                ages(v, people);
            }
            case "parents" -> {
                Person f = p.father();
                Person m = p.mother();
                if (f != null) {
                    put(v, "father", f.name());
                }
                if (m != null) {
                    put(v, "mother", m.name());
                }
                if (!"both".equals(t.detail())) {
                    Person parent = "father".equals(t.detail()) ? f : m;
                    v.put("parent_word", word("father".equals(t.detail()) ? "father" : "mother"));
                    v.put("he", word("father".equals(t.detail()) ? "he" : "she"));
                    if (parent != null) {
                        put(v, "parent", parent.name());
                        ages(v, List.of(parent));
                    }
                } else {
                    List<Person> known = new ArrayList<>();
                    if (f != null) {
                        known.add(f);
                    }
                    if (m != null) {
                        known.add(m);
                    }
                    List<Person> living = alive(known);
                    List<Person> dead = known.stream().filter(x -> !x.alive()).toList();
                    if (living.size() == 1) {
                        put(v, "alive_parent", living.get(0).name());
                    }
                    if (dead.size() == 1) {
                        put(v, "dead_parent", dead.get(0).name());
                    }
                    ages(v, living);
                }
            }
            case "spouse" -> {
                if (spouse != null) {
                    ages(v, List.of(spouse));
                }
            }
            default -> {
            }
        }
        return v;
    }

    private void people(Map<String, String> v, List<Person> people, String one, String count, String list) {
        v.put(count, String.valueOf(people.size()));
        if (!people.isEmpty()) {
            put(v, one, people.get(0).name());
            put(v, list, join(people));
            v.putIfAbsent("he", word(people.get(0).female() ? "she" : "he"));
            if (people.size() == 1) {
                v.put("he", word(people.get(0).female() ? "she" : "he"));
            }
        }
    }

    private void ages(Map<String, String> v, List<Person> people) {
        if (people.isEmpty() || people.stream().anyMatch(x -> x.age() == null)) {
            return;
        }
        if (people.size() == 1) {
            put(v, "name", people.get(0).name());
            v.put("age", String.valueOf(people.get(0).age()));
            v.put("he", word(people.get(0).female() ? "she" : "he"));
        } else {
            List<String> parts = people.stream().map(x -> x.name() + " (" + x.age() + ")").toList();
            v.put("ages", String.join(", ", parts));
        }
    }

    // réplique qui remplace la réponse générique, ou null pour la garder
    @Nullable
    public String replace(String id, boolean casual, State s, Collection<String> recent, Random random) {
        if (SYSTEM.contains(id)) {
            return null;
        }
        Map<String, String> v = vars(null, s);
        if (ROMANCE.contains(id) && s.family()) {
            return first(v, recent, random, "romance.family");
        }
        if ("marriage".equals(id) && s.couple()) {
            return first(v, recent, random, "proposal.spouse");
        }
        if (!casual) {
            return null;
        }
        if ("sleeping".equals(s.activity())) {
            return first(v, recent, random, "sleeping." + s.part(), "sleeping");
        }
        if (DOING.contains(id)) {
            return "working".equals(s.activity()) ? first(v, recent, random, "doing." + s.job(), "doing.working")
                    : first(v, recent, random, "doing.idle." + s.part(), "doing.idle");
        }
        return switch (id) {
            case "love_light" -> answer(new EmployeeTopics.Topic("love", "declare", "", false, true), s, recent, random);
            case "miss_you" -> answer(new EmployeeTopics.Topic("miss", "declare", "", false, true), s, recent, random);
            case "like_me" -> answer(new EmployeeTopics.Topic("likeme", "declare", "", false, true), s, recent, random);
            case "ask_family" -> first(v, recent, random, familyKey(s));
            case "goodmorning" -> "morning".equals(s.part()) ? null : first(v, recent, random, "time.not_morning");
            case "goodnight" -> "morning".equals(s.part()) || "day".equals(s.part()) ? first(v, recent, random, "time.not_night") : null;
            case "weather" -> first(v, recent, random, "weather." + s.profile.weather());
            case "village" -> s.profile.village() != null ? first(v, recent, random, "village.named") : null;
            case "fallback" -> first(v, recent, random, "relance");
            default -> null;
        };
    }

    // phrase ajoutée après une réponse générique : moment de la journée pour un bonjour, sinon ton de la relation
    @Nullable
    public String addition(String id, State s, Random random) {
        Map<String, String> v = vars(null, s);
        if ("greeting".equals(id) && random.nextDouble() < TIME_CHANCE) {
            return first(v, List.of(), random, "time." + s.part());
        }
        String relation = s.relation();
        if (relation.isEmpty() || random.nextDouble() >= TONE_CHANCE
                || "bad".equals(s.mood()) && !"cold".equals(relation) && !"distant".equals(relation)) {
            return null;
        }
        return first(v, List.of(), random, "tone." + relation);
    }

    @Nullable
    private String first(Map<String, String> vars, Collection<String> recent, Random random, String... keys) {
        for (String key : keys) {
            String text = line(key, vars, recent, random);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    @Nullable
    private String line(String key, Map<String, String> vars, Collection<String> recent, Random random) {
        List<String> list = pools.get(key);
        if (list == null) {
            return null;
        }
        List<String> usable = new ArrayList<>();
        for (String raw : list) {
            String text = render(raw, vars);
            if (text != null) {
                usable.add(text);
            }
        }
        List<String> fresh = usable.stream().filter(x -> !recent.contains(x)).toList();
        List<String> pool = fresh.isEmpty() ? usable : fresh;
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    @Nullable
    private static String render(String raw, Map<String, String> vars) {
        Matcher m = VARIABLE.matcher(raw);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String value = vars.get(name);
            if (value == null && !LATE_VARS.contains(name)) {
                return null;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(out);
        return out.isEmpty() ? null : Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }
}
