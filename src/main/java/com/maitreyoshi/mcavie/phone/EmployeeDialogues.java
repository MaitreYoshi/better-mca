package com.maitreyoshi.mcavie.phone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class EmployeeDialogues {
    public static final String PATH = "/data/mca_vie/employee_dialogues/";
    private static final Map<String, EmployeeDialogues> CACHE = new HashMap<>();
    private static final String[] PARTS = {"intro", "body", "end"};

    public record Intent(String id, int priority, String effect, List<String> words, List<String> roots, List<String> phrases,
                         Map<String, Map<String, List<String>>> replies, Map<String, List<String>> followup) {
    }

    public record Match(Intent intent, int score) {
    }

    private final Map<String, Intent> intents = new LinkedHashMap<>();

    public EmployeeDialogues(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        for (JsonElement element : root.getAsJsonArray("intents")) {
            JsonObject o = element.getAsJsonObject();
            List<String> words = new ArrayList<>();
            List<String> roots = new ArrayList<>();
            List<String> phrases = new ArrayList<>();
            if (o.has("keywords")) {
                for (JsonElement k : o.getAsJsonArray("keywords")) {
                    String raw = k.getAsString().trim();
                    boolean isRoot = raw.endsWith("*");
                    String norm = normalize(isRoot ? raw.substring(0, raw.length() - 1) : raw);
                    if (norm.isEmpty()) {
                        continue;
                    }
                    if (norm.contains(" ")) {
                        phrases.add(norm);
                    } else if (isRoot) {
                        roots.add(norm);
                    } else {
                        words.add(norm);
                    }
                }
            }
            Map<String, Map<String, List<String>>> replies = new HashMap<>();
            JsonObject r = o.has("replies") ? o.getAsJsonObject("replies") : new JsonObject();
            for (String part : PARTS) {
                Map<String, List<String>> byMood = new HashMap<>();
                if (r.has(part)) {
                    for (Map.Entry<String, JsonElement> e : r.getAsJsonObject(part).entrySet()) {
                        byMood.put(e.getKey(), strings(e.getValue().getAsJsonArray()));
                    }
                }
                replies.put(part, byMood);
            }
            Map<String, List<String>> followup = new HashMap<>();
            if (o.has("followup")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("followup").entrySet()) {
                    followup.put(e.getKey(), strings(e.getValue().getAsJsonArray()));
                }
            }
            String id = o.get("id").getAsString();
            intents.put(id, new Intent(id, o.has("priority") ? o.get("priority").getAsInt() : 1,
                    o.has("effect") ? o.get("effect").getAsString() : "none", words, roots, phrases, replies, followup));
        }
    }

    private static List<String> strings(JsonArray array) {
        List<String> list = new ArrayList<>(array.size());
        array.forEach(e -> list.add(e.getAsString()));
        return list;
    }

    // la langue vient du client : seuls les fichiers livrés sont acceptés, ce qui borne le cache
    static String supported(@Nullable String language) {
        return language == null || language.toLowerCase(Locale.ROOT).startsWith("fr") ? "fr_fr" : "en_us";
    }

    public static EmployeeDialogues get(String language) {
        return CACHE.computeIfAbsent(supported(language), EmployeeDialogues::load);
    }

    @Nullable
    private static EmployeeDialogues load(String lang) {
        try (InputStream in = EmployeeDialogues.class.getResourceAsStream(PATH + lang + ".json")) {
            if (in == null) {
                return null;
            }
            return new EmployeeDialogues(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            com.maitreyoshi.mcavie.MCAVie.LOGGER.error("Dialogues employés illisibles ({})", lang, e);
            return null;
        }
    }

    public int size() {
        return intents.size();
    }

    @Nullable
    public Intent intent(String id) {
        return intents.get(id);
    }

    // minuscules, sans accents, leetspeak de base, ponctuation -> espaces, lettres isolées recollées, lettres répétées réduites
    public static String normalize(String text) {
        String s = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.replace("œ", "oe").replace("æ", "ae").replace("ß", "ss");
        StringBuilder out = new StringBuilder();
        for (String token : s.split("[^a-z0-9@$]+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.chars().anyMatch(Character::isLetter)) {
                token = token.replace('0', 'o').replace('1', 'i').replace('3', 'e').replace('4', 'a')
                        .replace('5', 's').replace('7', 't').replace('@', 'a').replace('$', 's');
            } else {
                token = token.replace("@", "").replace("$", "");
            }
            if (!token.isEmpty()) {
                out.append(token).append(' ');
            }
        }
        // « c o n n a r d » ou « c.o.n.n.a.r.d » : suites d'au moins 3 lettres isolées recollées
        String[] tokens = out.toString().trim().split(" ");
        StringBuilder joined = new StringBuilder();
        int i = 0;
        while (i < tokens.length) {
            int j = i;
            while (j < tokens.length && tokens[j].length() == 1 && Character.isLetter(tokens[j].charAt(0))) {
                j++;
            }
            if (j - i >= 3) {
                for (int k = i; k < j; k++) {
                    joined.append(tokens[k]);
                }
                joined.append(' ');
                i = j;
            } else {
                joined.append(tokens[i]).append(' ');
                i++;
            }
        }
        return joined.toString().trim().replaceAll("(.)\\1+", "$1");
    }

    // terminaisons courantes (pluriels, conjugaisons) ignorées pour les mots d'au moins 5 lettres
    private static final String[] ENDINGS = {"ent", "es", "ez", "er", "ai", "s", "x", "e"};

    private static String stem(String word) {
        for (String ending : ENDINGS) {
            if (word.endsWith(ending) && word.length() - ending.length() >= 4) {
                return word.substring(0, word.length() - ending.length());
            }
        }
        return word;
    }

    private static boolean nearlyEquals(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        if (a.length() >= 5 && b.length() >= 5 && stem(a).equals(stem(b))) {
            return true;
        }
        if (a.length() < 5 || b.length() < 5 || Math.abs(a.length() - b.length()) > 1) {
            return false;
        }
        int i = 0;
        int j = 0;
        int edits = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (++edits > 1) {
                return false;
            }
            if (a.length() > b.length()) {
                i++;
            } else if (a.length() < b.length()) {
                j++;
            } else {
                i++;
                j++;
            }
        }
        return edits + (a.length() - i) + (b.length() - j) <= 1;
    }

    @Nullable
    public Match classify(String normalized) {
        if (normalized.isEmpty()) {
            return null;
        }
        String[] tokens = normalized.split(" ");
        String padded = " " + normalized + " ";
        Match best = null;
        for (Intent intent : intents.values()) {
            int score = 0;
            for (String phrase : intent.phrases()) {
                if (padded.contains(" " + phrase + " ")) {
                    score += 2;
                }
            }
            for (String word : intent.words()) {
                for (String token : tokens) {
                    if (nearlyEquals(token, word)) {
                        score++;
                        break;
                    }
                }
            }
            for (String root : intent.roots()) {
                for (String token : tokens) {
                    if (token.startsWith(root)) {
                        score++;
                        break;
                    }
                }
            }
            if (score > 0 && (best == null || score > best.score()
                    || score == best.score() && intent.priority() > best.intent().priority())) {
                best = new Match(intent, score);
            }
        }
        return best;
    }

    // charabia : mots longs sans voyelle ou avec une longue suite de consonnes
    public static boolean gibberish(String normalized) {
        int bad = 0;
        String[] tokens = normalized.split(" ");
        for (String token : tokens) {
            if (token.length() >= 4 && (!token.matches(".*[aeiouy].*") || token.matches(".*[^aeiouy0-9]{5,}.*"))) {
                bad++;
            }
        }
        return bad > 0 && bad * 2 >= tokens.length;
    }

    // humeur : "good", "neutral" ou "bad"
    public String reply(String id, String mood, @Nullable String last, Random random) {
        Intent intent = intents.get(id);
        if (intent == null) {
            intent = intents.get("fallback");
        }
        if (intent == null) {
            return "...";
        }
        String text = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            StringBuilder b = new StringBuilder();
            for (String part : PARTS) {
                String piece = pick(intent.replies().get(part), mood, random).trim();
                if (!piece.isEmpty()) {
                    boolean punctuation = piece.matches("^[.,!?…:;)].*");
                    if (b.length() > 0 && !punctuation) {
                        b.append(' ');
                        // « Euh, Sérieusement » -> « Euh, sérieusement »
                        if (b.charAt(b.length() - 2) == ',' && piece.length() > 1
                                && Character.isUpperCase(piece.charAt(0)) && (Character.isLowerCase(piece.charAt(1)) || piece.charAt(1) == '\'')) {
                            piece = Character.toLowerCase(piece.charAt(0)) + piece.substring(1);
                        }
                    }
                    b.append(piece);
                }
            }
            text = b.toString().trim();
            if (!text.equals(last)) {
                break;
            }
        }
        return text == null || text.isEmpty() ? "..." : text;
    }

    @Nullable
    public String followup(String id, String kind, @Nullable String last, Random random) {
        Intent intent = intents.get(id);
        if (intent == null) {
            return null;
        }
        List<String> list = intent.followup().get(kind);
        if (list == null || list.isEmpty()) {
            return null;
        }
        String text = list.get(random.nextInt(list.size()));
        if (text.equals(last) && list.size() > 1) {
            text = list.get((list.indexOf(text) + 1) % list.size());
        }
        return text;
    }

    private static String pick(@Nullable Map<String, List<String>> byMood, String mood, Random random) {
        if (byMood == null) {
            return "";
        }
        List<String> pool = new ArrayList<>(byMood.getOrDefault("any", List.of()));
        pool.addAll(byMood.getOrDefault(mood, List.of()));
        if (pool.isEmpty()) {
            pool.addAll(byMood.getOrDefault("neutral", List.of()));
        }
        return pool.isEmpty() ? "" : pool.get(random.nextInt(pool.size()));
    }
}
