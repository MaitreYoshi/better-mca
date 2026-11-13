package com.maitreyoshi.mcavie.life.client;

import com.maitreyoshi.mcavie.life.GhostEntity;
import com.maitreyoshi.mcavie.mixin.client.LimbAnimatorAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

/** Dessine le fantôme avec le rendu MCA du défunt, blanchâtre et translucide. */
public class GhostRenderer extends EntityRenderer<GhostEntity> {
    /** Lu par le mixin de VillagerLayer : les couches MCA passent en translucide. */
    public static boolean active;
    private static final float ALPHA = 0.45f;

    public GhostRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public Identifier getTextureLocation(GhostEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    private static LivingEntity body(GhostEntity ghost) {
        CompoundTag look = ghost.look();
        if (look.isEmpty()) {
            return null;
        }
        if (ghost.clientBody instanceof LivingEntity body && look == ghost.clientLook) {
            return body;
        }
        ghost.clientLook = look;
        ghost.clientBody = null;
        CompoundTag nbt = look.copy();
        nbt.remove("isPanicking"); // mort en panique : le fantôme revient apaisé
        Entity entity = EntityType.create(nbt, ghost.level()).orElse(null);
        if (entity instanceof LivingEntity body) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                body.setItemSlot(slot, ItemStack.EMPTY);
            }
            body.setCustomName(null);
            ghost.clientBody = body;
            return body;
        }
        return null;
    }

    private static void sync(GhostEntity ghost, LivingEntity body) {
        body.setPosRaw(ghost.getX(), ghost.getY(), ghost.getZ());
        body.xo = ghost.xo;
        body.yo = ghost.yo;
        body.zo = ghost.zo;
        body.xOld = ghost.xOld;
        body.yOld = ghost.yOld;
        body.zOld = ghost.zOld;
        body.setYRot(ghost.getYRot());
        body.yRotO = ghost.yRotO;
        body.setXRot(ghost.getXRot());
        body.xRotO = ghost.xRotO;
        body.yBodyRot = ghost.yBodyRot;
        body.yBodyRotO = ghost.yBodyRotO;
        body.yHeadRot = ghost.yHeadRot;
        body.yHeadRotO = ghost.yHeadRotO;
        body.tickCount = ghost.tickCount;
        body.setOnGround(ghost.onGround());
        LimbAnimatorAccessor from = (LimbAnimatorAccessor) ghost.walkAnimation;
        LimbAnimatorAccessor to = (LimbAnimatorAccessor) body.walkAnimation;
        to.mcavie$setPrevSpeed(from.mcavie$getPrevSpeed());
        to.mcavie$setSpeed(from.mcavie$getSpeed());
        to.mcavie$setPos(from.mcavie$getPos());
    }

    @Override
    public void render(GhostEntity ghost, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource consumers, int light) {
        LivingEntity body = body(ghost);
        if (body == null) {
            return;
        }
        sync(ghost, body);
        active = true;
        try {
            Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body)
                    .render(body, yaw, tickDelta, matrices, new Ghostly(consumers, ALPHA * Math.min(1f, (ghost.tickCount + tickDelta) / 30f)), LightTexture.FULL_BRIGHT);
        } catch (RuntimeException e) {
            ghost.clientBody = null;
        } finally {
            active = false;
        }
    }

    private record Ghostly(MultiBufferSource inner, float alpha) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType layer) {
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
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(pale(red, 0), pale(green, 5), pale(blue, 20), (int) (alpha * this.alpha));
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light,
                           float normalX, float normalY, float normalZ) {
            delegate.vertex(x, y, z, pale((int) (red * 255), 0) / 255f, pale((int) (green * 255), 5) / 255f, pale((int) (blue * 255), 20) / 255f,
                    alpha * this.alpha, u, v, overlay, light, normalX, normalY, normalZ);
        }
    }
}
