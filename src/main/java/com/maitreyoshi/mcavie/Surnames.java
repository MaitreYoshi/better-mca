package com.maitreyoshi.mcavie;

import com.maitreyoshi.mcavie.tree.server.mixin.FamilyTreeAccessor;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.network.s2c.PlayerDataMessage;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.resources.Names;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class Surnames {
    private static final int MAX_DEPTH = 16;

    private Surnames() {
    }

    public static void update(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }

        String current = villager.getTrackedValue(VillagerLike.VILLAGER_NAME);
        if (current == null || current.isBlank()) {
            return;
        }

        SurnameRegistry registry = SurnameRegistry.get(world.getServer());
        String surname = registry.get(villager.getUuid());
        if (surname == null && (surname = existingSurname(current)) != null) {
            registry.set(villager.getUuid(), surname);
        }
        if (surname == null) {
            FamilyTree tree = FamilyTree.get(world);
            FamilyTreeNode node = villager.getRelationships().getFamilyEntry();
            surname = resolve(tree, registry, node, regionOf(villager), 0);
            if (surname == null) {
                return;
            }
        }

        String updated = SurnameText.withSurname(current, SurnameText.display(surname, villager.getGenetics().getGender()));
        if (!updated.equals(current)) {
            villager.setName(updated);
        }
    }

    @Nullable
    private static String resolve(FamilyTree tree, SurnameRegistry registry, FamilyTreeNode node, String region, int depth) {
        String surname = registry.get(node.id());
        if (surname != null) {
            return surname;
        }
        if (node.isPlayer() || depth > MAX_DEPTH) {
            return null;
        }

        String existing = existingSurname(node.getName());
        if (existing != null) {
            registry.set(node.id(), existing);
            return existing;
        }

        String fatherSurname = tree.getOrEmpty(node.father())
                .map(father -> resolve(tree, registry, father, region, depth + 1))
                .orElse(null);
        String motherSurname = tree.getOrEmpty(node.mother())
                .map(mother -> resolve(tree, registry, mother, region, depth + 1))
                .orElse(null);

        if (fatherSurname != null) {
            surname = fatherSurname;
        } else if (motherSurname != null) {
            surname = motherSurname;
        } else {
            surname = SurnameLists.pick(region);
        }

        registry.set(node.id(), surname);
        node.setName(SurnameText.withSurname(node.getName(), SurnameText.display(surname, node.gender())));
        return surname;
    }

    @Nullable
    private static String existingSurname(String name) {
        String trimmed = name == null ? "" : name.trim();
        int space = trimmed.lastIndexOf(' ');
        if (space <= 0 || space == trimmed.length() - 1) {
            return null;
        }
        String surname = SurnameText.sanitize(trimmed.substring(space + 1));
        return surname.isEmpty() ? null : surname;
    }

    private static String regionOf(Entity entity) {
        try {
            return Names.getCitizenNation(entity);
        } catch (RuntimeException e) {
            return SurnameLists.DEFAULT_REGION;
        }
    }

    public static void onEditorRename(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }
        String surname = existingSurname(villager.getTrackedValue(VillagerLike.VILLAGER_NAME));
        if (surname == null) {
            return;
        }
        SurnameRegistry registry = SurnameRegistry.get(world.getServer());
        String current = registry.get(villager.getUuid());
        if (current == null || !surname.equals(SurnameText.display(current, villager.getGenetics().getGender()))) {
            registry.set(villager.getUuid(), surname);
        }
    }

    // Mondes créés avant l'addon : donne un nom de famille à toutes les personnes de l'arbre qui n'en ont pas.
    public static int fillMissing(ServerWorld world) {
        FamilyTree tree = FamilyTree.get(world);
        SurnameRegistry registry = SurnameRegistry.get(world.getServer());
        List<FamilyTreeNode> missing = ((FamilyTreeAccessor) tree).mcavie$getEntries().values().stream()
                .filter(node -> !node.isPlayer() && registry.get(node.id()) == null)
                .toList();
        int filled = 0;
        for (FamilyTreeNode node : missing) {
            if (registry.get(node.id()) != null || resolve(tree, registry, node, SurnameLists.DEFAULT_REGION, 0) != null) {
                filled++;
            }
        }
        return filled;
    }

    public static void assign(ServerWorld world, FamilyTreeNode node) {
        resolve(FamilyTree.get(world), SurnameRegistry.get(world.getServer()), node, SurnameLists.DEFAULT_REGION, 0);
    }

    public static String randomSurnameFor(Entity entity) {
        return SurnameLists.pick(regionOf(entity));
    }

    public static void setPlayerSurname(ServerPlayerEntity player, String surname, boolean renameNow) {
        ServerWorld world = player.getServerWorld();
        SurnameRegistry registry = SurnameRegistry.get(world.getServer());
        String previous = registry.get(player.getUuid());
        registry.set(player.getUuid(), surname);

        if (renameNow) {
            FamilyTreeNode node = FamilyTree.get(world).getOrCreate(player);
            PlayerSaveData data = PlayerSaveData.get(player);
            NbtCompound entityData = data.getEntityData();
            String oldName = entityData != null && entityData.contains("villagerName")
                    ? entityData.getString("villagerName")
                    : node.getName();
            String first = SurnameText.firstName(oldName, previous);
            if (first.isBlank()) {
                first = player.getGameProfile().getName();
            }
            String fullName = SurnameText.withSurname(first, surname);

            node.setName(fullName);
            if (data.isEntityDataSet() && entityData != null) {
                NbtCompound copy = entityData.copy();
                copy.putString("villagerName", fullName);
                data.setEntityData(copy);
                world.getPlayers().forEach(p -> NetworkHandler.sendToPlayer(new PlayerDataMessage(player.getUuid(), copy), p));
            }
        }

        SurnameNetwork.sendOwnSurname(player, surname);
    }
}
