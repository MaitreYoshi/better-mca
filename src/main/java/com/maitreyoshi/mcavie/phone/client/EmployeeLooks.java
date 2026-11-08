package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.phone.PhoneEmployees;
import net.mca.entity.EntitiesMCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Appli Employés : entité à afficher pour un employé. Celle du monde client si elle est chargée,
// sinon un villageois MCA factice reconstruit depuis l'apparence envoyée par le serveur (cache par UUID).
public final class EmployeeLooks {
    private record Cached(NbtCompound look, VillagerEntityMCA entity) {
    }

    private static final Map<UUID, Cached> CACHE = new HashMap<>();
    @Nullable
    private static ClientWorld cacheWorld;

    private EmployeeLooks() {
    }

    @Nullable
    public static LivingEntity get(PhoneEmployees.Employee employee) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return null;
        }
        for (Entity entity : world.getEntities()) {
            if (entity instanceof LivingEntity living && entity.getUuid().equals(employee.uuid()) && living.isAlive()) {
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
    private static VillagerEntityMCA build(ClientWorld world, NbtCompound look) {
        try {
            VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.get().create(world);
            if (villager == null) {
                return null;
            }
            villager.getTypeDataManager().load(villager, look);
            if (villager.getGenetics().getGender() == Gender.FEMALE) {
                VillagerEntityMCA female = EntitiesMCA.FEMALE_VILLAGER.get().create(world);
                if (female != null) {
                    female.getTypeDataManager().load(female, look);
                    villager = female;
                }
            }
            villager.setBreedingAge(look.getInt("Age"));
            villager.calculateDimensions();
            return villager;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // corps entier, pieds en bas du cadre ; le regard suit la souris comme dans l'inventaire
    public static void drawBody(DrawContext context, LivingEntity entity, int x, int y, int w, int h, int mouseX, int mouseY) {
        float height = Math.max(0.5f, entity.getHeight());
        int size = Math.max(1, Math.round((h - 8) / height));
        int cx = x + w / 2;
        int feet = y + h - 4;
        entity.age = (int) (System.currentTimeMillis() / 50L);
        InventoryScreen.drawEntity(context, cx, feet, size, cx - mouseX, feet - Math.round(height * size * 0.85f) - mouseY, entity);
    }

    // tête seule, de face, dans un carré (le rognage est à la charge de l'appelant)
    public static void drawHead(DrawContext context, LivingEntity entity, int x, int y, int box) {
        float height = Math.max(0.5f, entity.getHeight());
        int scale = Math.max(1, Math.round(box / (height * 0.25f)));
        float bodyYaw = entity.bodyYaw;
        float yaw = entity.getYaw();
        float pitch = entity.getPitch();
        float headYaw = entity.headYaw;
        float prevHeadYaw = entity.prevHeadYaw;
        entity.bodyYaw = 180;
        entity.setYaw(180);
        entity.setPitch(0);
        entity.headYaw = 180;
        entity.prevHeadYaw = 180;
        try {
            InventoryScreen.drawEntity(context, x + box / 2, y + Math.round(height * scale) - Math.round(box * 0.05f), scale,
                    new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), entity);
        } finally {
            entity.bodyYaw = bodyYaw;
            entity.setYaw(yaw);
            entity.setPitch(pitch);
            entity.headYaw = headYaw;
            entity.prevHeadYaw = prevHeadYaw;
        }
    }
}
