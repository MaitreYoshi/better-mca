package com.maitreyoshi.mcavie.phone;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Vérifie le moteur de mots-clés de la messagerie Employés sur des messages types (hors jeu).
public final class EmployeeDialoguesCheck {
    private static int failures;

    public static void main(String[] args) throws Exception {
        String dir = "src/main/resources/data/mca_vie/employee_dialogues/";
        EmployeeDialogues fr = new EmployeeDialogues(new FileReader(dir + "fr_fr.json", StandardCharsets.UTF_8));
        EmployeeDialogues en = new EmployeeDialogues(new FileReader(dir + "en_us.json", StandardCharsets.UTF_8));
        System.out.println("fr: " + fr.size() + " intentions, en: " + en.size());
        norm("Connnnnard !!", "conard");
        norm("c o n n a r d", "conard");
        norm("C.O.N", "con");
        norm("T'es VIRÉ", "t es vire");
        norm("n00b", "nob");
        effect(fr, "salut", "none");
        effect(fr, "connnnard", "insult");
        effect(fr, "c o n n a r d", "insult");
        effect(fr, "t'es un gros abrutii", "insult");
        effect(fr, "viens me voir", "order_follow");
        effect(fr, "reste là", "order_stay");
        effect(fr, "rentre", "order_home");
        effect(fr, "au boulot !", "order_work");
        effect(fr, "t'es viré", "order_fire");
        effect(fr, "oui", "yes");
        effect(fr, "pourquoi ?", "ask_why");
        effect(fr, "et toi ?", "ask_you");
        effect(fr, "merci beaucoup", "thanks");
        effect(fr, "pardon, désolé", "apology");
        effect(fr, "je vais te tuer", "threat");
        effect(fr, "t'es trop fort", "compliment");
        effect(en, "hello", "none");
        effect(en, "you idiot", "insult");
        effect(en, "come here", "order_follow");
        phrases(fr, "src/test/resources/employee_phrases_fr.tsv");
        phrases(en, "src/test/resources/employee_phrases_en.tsv");
        check("gibberish", EmployeeDialogues.gibberish(EmployeeDialogues.normalize("qsdfghjklm xcvbn")));
        check("no gibberish", !EmployeeDialogues.gibberish(EmployeeDialogues.normalize("bonjour mon ami")));
        Random random = new Random(1);
        life(dir, random);
        topics("src/test/resources/employee_topics.tsv");
        accords(dir + "fr_fr.json");
        for (String id : new String[]{"greeting", "fallback", "insult", "fire_ask", "spam", "resign"}) {
            if (fr.intent(id) != null) {
                System.out.println(id + " -> " + fr.reply(id, "neutral", null, random));
            }
        }
        System.out.println(failures == 0 ? "OK" : failures + " ECHEC(S)");
        if (failures > 0) {
            System.exit(1);
        }
    }

    // phrase<TAB>id attendu, ou phrase<TAB>!effet1,effet2 (effets interdits : faux positifs)
    private static void phrases(EmployeeDialogues d, String file) throws Exception {
        java.nio.file.Path path = java.nio.file.Path.of(file);
        if (!java.nio.file.Files.exists(path)) {
            return;
        }
        int total = 0;
        int ok = 0;
        for (String row : java.nio.file.Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String[] parts = row.split("\t");
            if (parts.length < 2 || parts[0].isBlank()) {
                continue;
            }
            total++;
            EmployeeDialogues.Match m = d.classify(EmployeeDialogues.normalize(parts[0]));
            String expected = parts[1].trim();
            boolean good;
            if (expected.startsWith("!")) {
                good = m == null || !java.util.List.of(expected.substring(1).split(",")).contains(m.intent().effect());
            } else {
                good = m != null && m.intent().id().equals(expected);
            }
            if (good) {
                ok++;
            } else {
                check("'" + parts[0] + "' attendu " + expected + ", obtenu " + (m == null ? "null" : m.intent().id()), false);
            }
        }
        System.out.println(file + " : " + ok + "/" + total);
    }

    private static void norm(String in, String expected) {
        check("normalize '" + in + "' = '" + EmployeeDialogues.normalize(in) + "'", EmployeeDialogues.normalize(in).equals(expected));
    }

    private static void effect(EmployeeDialogues d, String text, String expected) {
        EmployeeDialogues.Match m = d.classify(EmployeeDialogues.normalize(text));
        String got = m == null ? "null" : m.intent().effect() + " (" + m.intent().id() + ")";
        check("'" + text + "' -> " + got, m != null && m.intent().effect().equals(expected));
    }

    private static void topics(String file) throws Exception {
        for (String row : java.nio.file.Files.readAllLines(java.nio.file.Path.of(file), StandardCharsets.UTF_8)) {
            String[] parts = row.split("\t");
            if (parts.length < 3) {
                continue;
            }
            EmployeeTopics.Topic t = EmployeeTopics.analyze(EmployeeDialogues.normalize(parts[0]));
            boolean negated = parts[2].endsWith("!");
            String intent = negated ? parts[2].substring(0, parts[2].length() - 1) : parts[2];
            boolean ok = "-".equals(parts[1]) ? t == null : t != null && parts[1].equals(t.subject()) && intent.equals(t.intent()) && negated == t.negated();
            check("topic '" + parts[0] + "' -> " + (t == null ? "-" : t.subject() + "/" + t.intent()), ok);
        }
    }

    private static EmployeeLife.Person p(String name, boolean female, boolean alive) {
        return new EmployeeLife.Person(name, female, alive, false, 30);
    }

    private static EmployeeLife.State state(String kin, int hearts, EmployeeLife.Profile profile) {
        return new EmployeeLife.State("idle", kin, hearts, "day", "baker", "neutral", false, profile);
    }

    private static void life(String dir, Random random) throws Exception {
        String[] jobs = {"farmer", "lumberjack", "fisher", "breeder", "doctor", "guard", "archer", "blacksmith", "baker", "teacher", "retired", "miner", "cook"};
        List<EmployeeLife.Person> four = List.of(p("Ana", true, true), p("Bo", false, true), p("Cy", false, true), p("Di", true, true));
        List<EmployeeLife.Person> one = List.of(p("Ana", true, true));
        EmployeeLife.Person gjin = p("Gjin Svoboda", false, true);
        EmployeeLife.Person father = p("Otto", false, true);
        EmployeeLife.Person mother = p("Eva", true, true);
        EmployeeLife.Person deadF = p("Otto", false, false);
        EmployeeLife.Person deadM = p("Eva", true, false);
        EmployeeLife.Person player = new EmployeeLife.Person("Thomas", false, true, true, null);
        Map<String, EmployeeLife.State> states = new LinkedHashMap<>();
        states.put("kathrin", state("", 20, new EmployeeLife.Profile(true, gjin, false, four, father, mother, List.of(p("Lea", true, true)), "Valbourg", "rain")));
        states.put("homme", state("", 20, new EmployeeLife.Profile(false, p("Mia", true, true), false, one, father, deadM, List.of(), null, "clear")));
        states.put("celibataire", state("", 5, new EmployeeLife.Profile(true, null, false, List.of(), deadF, deadM, four, null, "clear")));
        states.put("veuf", state("", 60, new EmployeeLife.Profile(false, null, true, one, null, null, List.of(), null, "thunder")));
        states.put("veuve_nom", state("", -5, new EmployeeLife.Profile(true, deadF, true, List.of(), null, mother, List.of(), null, "clear")));
        states.put("marie_joueur", state("spouse", 90, new EmployeeLife.Profile(false, player, false, four, father, mother, List.of(), null, "clear")));
        states.put("enfant_joueur", state("child", 80, new EmployeeLife.Profile(true, null, false, List.of(), player, null, List.of(), null, "clear")));
        String[][] asks = {{"spouse", ""}, {"children", ""}, {"children", "son"}, {"children", "daughter"}, {"parents", "both"}, {"parents", "father"}, {"parents", "mother"}, {"siblings", ""}, {"siblings", "sister"}, {"family", ""}};
        String[] intents = {"has", "who", "how", "count", "age", "where"};
        for (String lang : new String[]{"fr_fr", "en_us"}) {
            EmployeeLife life = new EmployeeLife(new FileReader(dir + "life_" + lang + ".json", StandardCharsets.UTF_8));
            for (String job : jobs) {
                check(lang + " doing." + job, life.has("doing." + job));
            }
            for (Map.Entry<String, EmployeeLife.State> e : states.entrySet()) {
                for (String[] ask : asks) {
                    for (String intent : intents) {
                        for (int i = 0; i < 12; i++) {
                            String out = life.answer(new EmployeeTopics.Topic(ask[0], intent, ask[1], false, true), e.getValue(), List.of(), random);
                            clean(lang + " " + e.getKey() + " " + ask[0] + "/" + ask[1] + "/" + intent, out);
                        }
                    }
                }
                for (String subject : new String[]{"love", "miss", "likeme", "proposal"}) {
                    clean(lang + " " + e.getKey() + " " + subject, life.answer(new EmployeeTopics.Topic(subject, "declare", "", false, true), e.getValue(), List.of(), random));
                }
            }
            EmployeeLife.State k = states.get("kathrin");
            String love = life.answer(new EmployeeTopics.Topic("love", "declare", "", false, true), k, List.of(), random);
            check(lang + " no job in love", love != null && !love.contains("blé") && !love.contains("{metier}"));
            EmployeeLife.State asleep = new EmployeeLife.State("sleeping", "", 20, "night", "blacksmith", "neutral", false, k.profile());
            EmployeeLife.State forge = new EmployeeLife.State("working", "", 20, "day", "blacksmith", "neutral", false, k.profile());
            check(lang + " working", life.replace("ask_status", true, forge, List.of(), random) != null);
            check(lang + " asleep", life.replace("greeting", true, asleep, List.of(), random) != null);
            check(lang + " system kept", life.replace("fire_ask", false, asleep, List.of(), random) == null);
            check(lang + " order kept", life.replace("order_home", false, asleep, List.of(), random) == null);
            check(lang + " kin romance", life.replace("flirt_heavy", false, states.get("enfant_joueur"), List.of(), random) != null);
            check(lang + " generic kept", life.replace("creepers", true, forge, List.of(), random) == null);
            String first = life.answer(new EmployeeTopics.Topic("spouse", "has", "", false, true), k, List.of(), random);
            check(lang + " no repeat", first != null && !first.equals(life.answer(new EmployeeTopics.Topic("spouse", "has", "", false, true), k, List.of(first), random)));
        }
        check("cold mood", "bad".equals(EmployeeLife.adjustMood("neutral", "cold")));
        check("spouse mood", "good".equals(EmployeeLife.adjustMood("neutral", "spouse")));
        check("night", "night".equals(EmployeeLife.partOfDay(23)) && "night".equals(EmployeeLife.partOfDay(3)));
        EmployeeLife fr = new EmployeeLife(new FileReader(dir + "life_fr_fr.json", StandardCharsets.UTF_8));
        EmployeeLife.State k = states.get("kathrin");
        String[][] demo = {{"children", "how", ""}, {"spouse", "has", ""}, {"parents", "who", "both"}, {"children", "count", ""}, {"love", "declare", ""}, {"spouse", "who", ""}};
        for (String[] d : demo) {
            System.out.println("KATHRIN " + d[0] + "/" + d[1] + " -> " + fr.answer(new EmployeeTopics.Topic(d[0], d[1], d[2], false, true), k, List.of(), random));
        }
    }

    private static final java.util.regex.Pattern MASCULINE = java.util.regex.Pattern.compile("(?iu)\\b(?:(?:je ne |je )?suis|j'étais|je serais?|je reste|me sens)\\s+(?:(?:pas|plus|très|trop|si|bien|un peu|assez|vraiment|encore|toujours|juste|plutôt)\\s+)*"
            + "(?:content|flatté|désolé|prudent|occupé|épuisé|comblé|fatigué|sûr|curieux|heureux|sérieux|fier|rancunier|inquiet|vexé|ému|bavard|têtu|fauché|un employé|un villageois)(?![\\p{L}])|(?iu)\\bdésolé(?![\\p{L}])");

    private static void accords(String file) throws Exception {
        JsonObject root = JsonParser.parseReader(new FileReader(file, StandardCharsets.UTF_8)).getAsJsonObject();
        int[] count = {0, 0};
        for (JsonElement e : root.getAsJsonArray("intents")) {
            JsonObject o = e.getAsJsonObject();
            for (String part : new String[]{"replies", "followup"}) {
                if (o.has(part)) {
                    walk(o.get(part), count);
                }
            }
        }
        check("accords : " + count[0] + " répliques, " + count[1] + " genrées", count[1] > 50);
    }

    private static void walk(JsonElement e, int[] count) {
        if (e.isJsonPrimitive()) {
            String raw = e.getAsString();
            count[0]++;
            count[1] += raw.contains("{e}") || raw.contains("|") ? 1 : 0;
            String female = EmployeeLife.accord(raw, true);
            String male = EmployeeLife.accord(raw, false);
            if (female.contains("{e}") || female.contains("|") || male.contains("{e}") || male.contains("|") || MASCULINE.matcher(female).find()) {
                check("accord féminin : " + raw + " => " + female, false);
            }
        } else if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(x -> walk(x, count));
        } else if (e.isJsonObject()) {
            e.getAsJsonObject().entrySet().forEach(x -> walk(x.getValue(), count));
        }
    }

    private static void clean(String label, String out) {
        boolean ok = out != null && !out.contains("(s)") && !out.contains("  ")
                && !out.replaceAll("\\{(joueur|heure|etat|nom|metier)}", "").contains("{");
        if (!ok) {
            check(label + " => " + out, false);
        }
    }

    private static void check(String label, boolean ok) {
        System.out.println((ok ? "OK   " : "FAIL ") + label);
        if (!ok) {
            failures++;
        }
    }
}
