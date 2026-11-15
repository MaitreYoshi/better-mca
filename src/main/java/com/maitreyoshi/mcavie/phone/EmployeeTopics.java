package com.maitreyoshi.mcavie.phone;

import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.regex.Pattern;

// Analyse sujet + intention d'un message déjà normalisé par EmployeeDialogues.normalize
// (sans accents ni ponctuation, lettres doublées réduites : femme -> feme, fille -> file, appelle -> apele).
public final class EmployeeTopics {
    public static final Set<String> PERSONS = Set.of("spouse", "children", "parents", "siblings");
    public static final String ANAPHORA = "anaphora";

    // explicit : l'intention figure dans le message ; sinon "how" par défaut
    public record Topic(String subject, String intent, String detail, boolean negated, boolean explicit) {
    }

    private static final String POSS_FR = "\\b(?:ton|ta|tes|votre|vos) ";
    private static final String HAS = "(?:\\bas tu\\b|\\bt as\\b|\\btu as\\b|\\btu a\\b|\\bavez vous\\b|\\bvous avez\\b|\\byou have\\b|\\bhave you got\\b|\\byou got\\b"
            + "|\\btas (?=un|une|des|d|de|pas)|\\bta (?=un |une |des |d |pas ))";

    private static final Pattern PROPOSAL = rx("\\b(on se marie|epouse moi|m epouser|mepouser|t epouser|marions nous|marie moi|marie toi avec moi|te marier avec moi|mary me|mary you)\\b");
    private static final Pattern NOT_LOVE = rx("\\b(je (ne )?t aime (pas|plus)|tu me manques? (pas|plus)|tu ne me manques? (pas|plus)|don t love you|dont love you|don t mis you|dont mis you)\\b");
    private static final Pattern LOVE = rx("\\b(je t aime|je taime|jtaime|jetaime|jtm|je t adore|i love you|love you)\\b");
    private static final Pattern MISS = rx("\\b(tu me manques?|mis you)\\b");
    private static final Pattern LIKE_ME = rx("\\b(tu m aimes?|tu maimes?|tu m aprecies?|do you love me|do you like me)\\b");

    private static final String FATHER = "pere|papa";
    private static final String MOTHER = "mere|maman";
    private static final Pattern PARENT_POSS = rx(POSS_FR + "(" + FATHER + "|" + MOTHER + "|parent|parents)\\b|\\byour (father|dad|mother|mum|mom|parents)\\b");
    private static final Pattern PARENT_HAS = rx(HAS + ".*\\b(un pere|une mere|des parents|a father|a mother|parents)\\b");
    private static final Pattern IS_FATHER = rx("\\b(" + FATHER + "|father|dad)\\b");
    private static final Pattern IS_MOTHER = rx("\\b(" + MOTHER + "|mother|mum|mom)\\b");

    private static final String CHILD_FR = "enfants?|fils|files?|goses?|gamin|gamins|gamines?|momes?|bebes?";
    private static final String CHILD_EN = "child|children|kids?|sons?|daughters?";
    private static final Pattern CHILD_POSS = rx(POSS_FR + "(" + CHILD_FR + "|petits?(?! ami)|petites?(?! ami))\\b|\\byour (" + CHILD_EN + ")\\b");
    private static final Pattern CHILD_HAS = rx("(" + HAS + ".*|\\bcombien (de |d )|\\bhow many )\\b(" + CHILD_FR + "|" + CHILD_EN + ")\\b");
    private static final Pattern IS_SON = rx("\\b(fils|sons?)\\b");
    private static final Pattern IS_DAUGHTER = rx("\\b(files?|daughters?)\\b");

    private static final Pattern SPOUSE_POSS = rx(POSS_FR + "(mari|feme|epoux|epouse|conjointe?|compagnon|compagne|moitie|petite amie|petit ami|copine|copain|fiancee?)\\b|\\byour (husband|wife|spouse|partner|girlfriend|boyfriend)\\b");
    private static final Pattern SPOUSE_HAS = rx(HAS + ".*\\b(un mari|une feme|un epoux|une epouse|une? conjointe?|une? copain|une copine|a husband|a wife|a spouse|a partner)\\b");
    private static final Pattern MARRIED = rx("\\b(es tu|tu es|t es|tes|etes vous|vous etes|are you|you re)\\s+(pas\\s+|encore\\s+|deja\\s+)?(maries?|maried|celibataire|single|veuf|veuve|widowed|fiancee?|engaged)\\b");

    private static final Pattern SIBLING_POSS = rx(POSS_FR + "(freres?|soeurs?|fratrie)\\b|\\byour (brothers?|sisters?|siblings?)\\b");
    private static final Pattern SIBLING_HAS = rx("(" + HAS + ".*|\\bcombien (de |d )|\\bhow many )\\b(freres?|soeurs?|brothers?|sisters?|siblings?)\\b");
    private static final Pattern IS_BROTHER = rx("\\b(freres?|brothers?)\\b");
    private static final Pattern IS_SISTER = rx("\\b(soeurs?|sisters?)\\b");
    private static final Pattern FAMILY = rx(POSS_FR + "famile\\b|\\byour family\\b");

    private static final Pattern COUNT = rx("\\b(combien|how many)\\b");
    private static final Pattern AGE = rx("\\b(quel age|how old)\\b");
    private static final Pattern WHO = rx("\\b(qui|who|apele|apelent|prenoms?|name|names|caled)\\b");
    private static final Pattern HOW = rx("\\b(coment|how)\\b.*\\b(va|vont|ca va|portes?|portent|is|are|doing)\\b|\\b(va|vont|vas) (bien|mal)\\b|\\b(ca va|doing wel|doing ok)\\b");
    private static final Pattern WHERE = rx("\\b(ou|where)\\b|\\b(fait|font) quoi\\b|\\bwhat (is|are) \\w+ doing\\b");
    private static final Pattern PRONOUN = rx("\\b(il|ele|ils|eles|lui|he|she|they|him)\\b");
    private static final Pattern WEATHER = rx("\\b(temps|meteo|weather|pleut|pleuvoir|rain|heure|time)\\b");

    private EmployeeTopics() {
    }

    private static Pattern rx(String regex) {
        return Pattern.compile(regex);
    }

    @Nullable
    public static Topic analyze(String text) {
        if (text.isEmpty()) {
            return null;
        }
        if (PROPOSAL.matcher(text).find()) {
            return new Topic("proposal", "declare", "", false, true);
        }
        boolean notLove = NOT_LOVE.matcher(text).find();
        if (notLove) {
            return new Topic("love", "declare", "", true, true);
        }
        if (LOVE.matcher(text).find()) {
            return new Topic("love", "declare", "", false, true);
        }
        if (MISS.matcher(text).find()) {
            return new Topic("miss", "declare", "", false, true);
        }
        if (LIKE_ME.matcher(text).find()) {
            return new Topic("likeme", "declare", "", false, true);
        }
        boolean parentHas = PARENT_HAS.matcher(text).find();
        if (PARENT_POSS.matcher(text).find() || parentHas) {
            String detail = IS_FATHER.matcher(text).find() ? "father" : IS_MOTHER.matcher(text).find() ? "mother" : "both";
            return topic("parents", text, detail, parentHas);
        }
        boolean childHas = CHILD_HAS.matcher(text).find();
        if (CHILD_POSS.matcher(text).find() || childHas) {
            String detail = IS_SON.matcher(text).find() ? "son" : IS_DAUGHTER.matcher(text).find() ? "daughter" : "";
            return topic("children", text, detail, childHas);
        }
        boolean spouseHas = SPOUSE_HAS.matcher(text).find() || MARRIED.matcher(text).find();
        if (SPOUSE_POSS.matcher(text).find() || spouseHas) {
            return topic("spouse", text, "", spouseHas);
        }
        boolean siblingHas = SIBLING_HAS.matcher(text).find();
        if (SIBLING_POSS.matcher(text).find() || siblingHas) {
            String detail = IS_BROTHER.matcher(text).find() ? "brother" : IS_SISTER.matcher(text).find() ? "sister" : "";
            return topic("siblings", text, detail, siblingHas);
        }
        if (FAMILY.matcher(text).find()) {
            return topic("family", text, "", false);
        }
        if (PRONOUN.matcher(text).find() && !WEATHER.matcher(text).find()) {
            String intent = intent(text);
            if (intent != null && !"has".equals(intent)) {
                return new Topic(ANAPHORA, intent, "", false, true);
            }
        }
        return null;
    }

    private static Topic topic(String subject, String text, String detail, boolean has) {
        String intent = intent(text);
        boolean explicit = intent != null || has;
        return new Topic(subject, intent != null ? intent : has ? "has" : "how", detail, false, explicit);
    }

    @Nullable
    private static String intent(String text) {
        if (COUNT.matcher(text).find()) {
            return "count";
        }
        if (AGE.matcher(text).find()) {
            return "age";
        }
        if (WHO.matcher(text).find()) {
            return "who";
        }
        if (HOW.matcher(text).find()) {
            return "how";
        }
        if (Pattern.compile(HAS).matcher(text).find()) {
            return "has";
        }
        return WHERE.matcher(text).find() ? "where" : null;
    }
}
