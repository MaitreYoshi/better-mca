package com.maitreyoshi.mcavie.life;

import java.util.Arrays;

public final class FamilyAgesCheck {
    private static int failures;

    public static void main(String[] args) {
        // Capture du 1er novembre : enfants et petits-enfants des joueurs tous à 18 ans, monde d'un an.
        // 0 Gerardette, 1 GuiGuillotin, 2 Domi-nique, 3 Ani (sans âge), 4 SaleFaible, 5 Radu, 6 Eftimie, 7 Beata
        FamilyAges.Family family = FamilyAges.Family.of(
                new int[]{18, 18, 18, -1, 18, 18, 18, 30},
                new boolean[]{true, true, true, false, true, true, true, false},
                new int[][]{{2, 5}, {3, 5}, {2, 6}, {3, 6}},
                new int[][]{{2, 3}, {6, 7}});
        check("capture détectée", family.inconsistent(1));
        int[] ages = family.solve(1);
        System.out.println("capture : " + Arrays.toString(ages));
        check("capture soluble", ages != null);
        if (ages != null) {
            check("Domi-nique au moins 18 ans de plus que Radu", ages[2] - ages[5] >= 18);
            check("Domi-nique au moins 18 ans de plus qu'Eftimie", ages[2] - ages[6] >= 18);
            check("frères et sœurs sans descendants restent à 18", ages[0] == 18 && ages[1] == 18 && ages[4] == 18);
            check("Eftimie et Beata proches", Math.abs(ages[6] - ages[7]) <= 8);
        }

        // Premier rapport : épouse 36 ans, fils 40, petit-fils 46.
        FamilyAges.Family first = FamilyAges.Family.of(
                new int[]{36, 40, 46},
                new boolean[]{false, true, true},
                new int[][]{{0, 1}, {1, 2}},
                new int[0][]);
        int[] fixed = first.solve(1);
        System.out.println("épouse/petit-fils : " + Arrays.toString(fixed));
        check("épouse/petit-fils soluble", fixed != null && fixed[0] - fixed[1] >= 18 && fixed[1] - fixed[2] >= 18);

        // Famille cohérente : rien ne bouge.
        FamilyAges.Family fine = FamilyAges.Family.of(
                new int[]{62, 60, 35, 33, 8},
                new boolean[]{false, false, true, false, true},
                new int[][]{{0, 2}, {1, 2}, {2, 4}, {3, 4}},
                new int[][]{{0, 1}, {2, 3}});
        check("famille cohérente non détectée", !fine.inconsistent(80));
        int[] same = fine.solve(80);
        check("famille cohérente inchangée", same != null && Arrays.equals(same, new int[]{62, 60, 35, 33, 8}));

        System.out.println(failures == 0 ? "OK" : failures + " échec(s)");
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "OK   " : "FAIL ") + name);
        if (!ok) {
            failures++;
        }
    }
}
