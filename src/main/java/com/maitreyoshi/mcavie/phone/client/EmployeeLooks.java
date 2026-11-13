package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.phone.PhoneEmployees;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Appli Employés : entité à afficher pour un employé. Celle du monde client si elle est chargée,
// sinon un villageois MCA factice reconstruit depuis l'apparence envoyée par le serveur (cache par UUID).
public final class EmployeeLooks {
    private record Cached(CompoundTag look, VillagerEntityMCA entity) {
    }

    private static final Map<UUID, Cached> CACHE = new HashMap<>();
    @Nullable
    private static ClientLevel cacheWorld;

    private EmployeeLooks() {
    }

    public static void clear() {
        CACHE.clear();
        cacheWorld = null;
    }

    @Nullable
    public static LivingEntity get(PhoneEmployees.Employee employee) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            return null;
        }
        for (Entity entity : world.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && entity.getUUID().equals(employee.uuid()) && living.isAlive()) {
                return living;
            }
        }
        if (employee.look() == null) {
            return null;
        }
        if (cacheWorld != world) {
            CACHE.clear();
            cacheWorld = world;
        }
        Cached cached = CACHE.get(employee.uuid());
        if (cached != null && cached.look().equals(employee.look())) {
            return cached.entity();
        }
        VillagerEntityMCA villager = build(world, employee.look());
        if (villager != null) {
            CACHE.put(employee.uuid(), new Cached(employee.look(), villager));
        }
        return villager;
    }

    @Nullable
    private static VillagerEntityMCA build(ClientLevel world, CompoundTag look) {
        try {
            VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.create(world, net.minecraft.world.entity.EntitySpawnReason.LOAD);
            if (villager == null) {
                return null;
            }
            villager.getTypeDataManager().load(villager, look);
            if (villager.getGenetics().getGender() == Gender.FEMALE) {
                VillagerEntityMCA female = EntitiesMCA.FEMALE_VILLAGER.create(world, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                if (female != null) {
                    female.getTypeDataManager().load(female, look);
                    villager = female;
                }
            }
            villager.setId(com.maitreyoshi.mcavie.compat.client.GuiEntity.fakeId());
            villager.setAge(look.getIntOr("Age", 0));
            villager.refreshDimensions();
            return villager;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // corps entier, pieds en bas du cadre ; le regard suit la souris comme dans l'inventaire
    public static void drawBody(GuiGraphicsExtractor context, LivingEntity entity, int x, int y, int w, int h, int mouseX, int mouseY) {
        float height = Math.max(0.5f, entity.getBbHeight());
        int size = Math.max(1, Math.round((h - 8) / height));
        entity.tickCount = (int) (System.currentTimeMillis() / 50L);
        InventoryScreen.extractEntityInInventoryFollowsMouse(context, x, y, x + w, y + h, size, 0.0625f, mouseX, mouseY, entity);
    }

    // tête seule, de face, dans un carré (le rognage est à la charge de l'appelant)
    public static void drawHead(GuiGraphicsExtractor context, LivingEntity entity, int x, int y, int box) {
        float height = Math.max(0.5f, entity.getBbHeight());
        int scale = Math.max(1, Math.round(box / (height * 0.25f)));
        com.maitreyoshi.mcavie.compat.client.GuiEntity.head(context, entity, x, y, x + box, y + box, scale);
    }
}
