package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.tree.server.mixin.FamilyTreeAccessor;
import net.mca.network.c2s.FamilyTreeUUIDLookup;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Recherche de l'arbre généalogique et du traqueur de villageois : MCA exige le nom complet exact, et depuis les
 * noms de famille « Jean » ne trouvait plus « Jean Dupont ». On accepte le début du nom ou d'un de ses mots,
 * sans tenir compte des majuscules ni des accents.
 */
@Mixin(value = FamilyTreeUUIDLookup.class, remap = false)
public abstract class FamilyTreeUUIDLookupMixin {
    @Redirect(method = "receive", at = @At(value = "INVOKE", target = "Lnet/mca/server/world/data/FamilyTree;getAllWithName(Ljava/lang/String;)Ljava/util/stream/Stream;"))
    private Stream<FamilyTreeNode> mcavie$looseSearch(FamilyTree tree, String search) {
        String query = mcavie$normalize(search);
        if (query.isEmpty()) {
            return Stream.empty();
        }
        return ((FamilyTreeAccessor) tree).mcavie$getEntries().values().stream()
                .filter(node -> mcavie$matches(mcavie$normalize(node.getName()), query))
                .sorted(Comparator.comparing((FamilyTreeNode node) -> !mcavie$normalize(node.getName()).equals(query))
                        .thenComparing(FamilyTreeNode::isDeceased)
                        .thenComparing(FamilyTreeNode::getName));
    }

    @Unique
    private static boolean mcavie$matches(String name, String query) {
        if (name.startsWith(query)) {
            return true;
        }
        for (String word : name.split(" ")) {
            if (word.startsWith(query)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static String mcavie$normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
