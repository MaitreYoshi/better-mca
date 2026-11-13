package com.maitreyoshi.mcavie.life.client;

import com.maitreyoshi.mcavie.life.GhostEntity;
import com.maitreyoshi.mcavie.mixin.client.LimbAnimatorAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;

/** Dessine le fantôme avec le rendu MCA du défunt, blanchâtre et translucide. */
public class GhostRenderer extends EntityRenderer<GhostEntity> {
    /** Lu par le mixin de VillagerLayer : les couches MCA passent en translucide. */
    public static boolean active;
    private static final float ALPHA = 0.45f;

    public GhostRenderer(EntityRendererFactory.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public Identifier getTexture(GhostEntity entity) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }

    private static LivingEntity body(GhostEntity ghost) {
        NbtCompound look = ghost.look();
        if (look.isEmpty()) {
            return null;
        }
        if (ghost.clientBody instanceof LivingEntity body && look == ghost.clientLook) {
            return body;
        }
        ghost.clientLook = look;
        ghost.clientBody = null;
        NbtCompound nbt = look.copy();
        nbt.remove("isPanicking"); // mort en panique : le fantôme revient apaisé
        Entity entity = EntityType.getEntityFromNbt(nbt, ghost.getWorld()).orElse(null);
        if (entity instanceof LivingEntity body) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                body.equipStack(slot, ItemStack.EMPTY);
            }
            body.setCustomName(null);
            ghost.clientBody = body;
            return body;
        }
        return null;
    }

    private static void sync(GhostEntity ghost, LivingEntity body) {
        body.setPos(ghost.getX(), ghost.getY(), ghost.getZ());
        body.prevX = ghost.prevX;
        body.prevY = ghost.prevY;
        body.prevZ = ghost.prevZ;
        body.lastRenderX = ghost.lastRenderX;
        body.lastRenderY = ghost.lastRenderY;
        body.lastRenderZ = ghost.lastRenderZ;
        body.setYaw(ghost.getYaw());
        body.prevYaw = ghost.prevYaw;
        body.setPitch(ghost.getPitch());
        body.prevPitch = ghost.prevPitch;
        body.bodyYaw = ghost.bodyYaw;
        body.prevBodyYaw = ghost.prevBodyYaw;
        body.headYaw = ghost.headYaw;
        body.prevHeadYaw = ghost.prevHeadYaw;
        body.age = ghost.age;
        body.setOnGround(ghost.isOnGround());
        LimbAnimatorAccessor from = (LimbAnimatorAccessor) ghost.limbAnimator;
        LimbAnimatorAccessor to = (LimbAnimatorAccessor) body.limbAnimator;
        to.mcavie$setPrevSpeed(from.mcavie$getPrevSpeed());
        to.mcavie$setSpeed(from.mcavie$getSpeed());
        to.mcavie$setPos(from.mcavie$getPos());
    }

    @Override
    public void render(GhostEntity ghost, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        LivingEntity body = body(ghost);
        if (body == null) {
            return;
        }
        sync(ghost, body);
        active = true;
        try {
            MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(body)
                    .render(body, yaw, tickDelta, matrices, new Ghostly(consumers, ALPHA * Math.min(1f, (ghost.age + tickDelta) / 30f)), LightmapTextureManager.MAX_LIGHT_COORDINATE);
        } catch (RuntimeException e) {
            ghost.clientBody = null;
        } finally {
            active = false;
        }
    }

    private record Ghostly(VertexConsumerProvider inner, float alpha) implements VertexConsumerProvider {
        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return new Pale(inner.getBuffer(layer), alpha);
        }
    }

    /** Couleurs tirées vers un blanc bleuté, alpha réduit. */
    private record Pale(VertexConsumer delegate, float alpha) implements VertexConsumer {
        private static int pale(int c, int extra) {
            return Math.min(255, 140 + c * 115 / 255 + extra);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(pale(red, 0), pale(green, 5), pale(blue, 20), (int) (alpha * this.alpha));
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void next() {
            delegate.next();
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
            delegate.fixedColor(pale(red, 0), pale(green, 5), pale(blue, 20), (int) (alpha * this.alpha));
        }

        @Override
        public void unfixColor() {
            delegate.unfixColor();
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light,
                           float normalX, float normalY, float normalZ) {
            delegate.vertex(x, y, z, pale((int) (red * 255), 0) / 255f, pale((int) (green * 255), 5) / 255f, pale((int) (blue * 255), 20) / 255f,
                    alpha * this.alpha, u, v, overlay, light, normalX, normalY, normalZ);
        }
    }
}
