package com.maitreyoshi.mcavie.life.client;

import net.minecraft.util.LightCoordsUtil;
import com.maitreyoshi.mcavie.life.GhostEntity;
import com.maitreyoshi.mcavie.mixin.client.LimbAnimatorAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
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
public class GhostRenderer extends EntityRenderer<GhostEntity, GhostRenderer.State> {
    /** Lus par le mixin de VillagerLayer : les couches MCA passent en translucide et pâlissent. */
    public static boolean active;
    public static float alpha = 0.45f;
    private static final float ALPHA = 0.45f;

    public static class State extends EntityRenderState {
        GhostEntity ghost;
        float tickDelta;
    }

    public GhostRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GhostEntity ghost, State state, float tickDelta) {
        super.extractRenderState(ghost, state, tickDelta);
        state.ghost = ghost;
        state.tickDelta = tickDelta;
    }

    private static LivingEntity body(GhostEntity ghost) {
        CompoundTag look = ghost.look();
        if (look.isEmpty()) {
            com.maitreyoshi.mcavie.life.Ghosts.debug("client : apparence vide (données synchronisées trop grosses ou illisibles)");
            return null;
        }
        if (ghost.clientBody instanceof LivingEntity body && look == ghost.clientLook) {
            return body;
        }
        ghost.clientLook = look;
        ghost.clientBody = null;
        CompoundTag nbt = look.copy();
        nbt.remove("isPanicking"); // mort en panique : le fantôme revient apaisé
        Entity entity = EntityType.create(com.maitreyoshi.mcavie.compat.Nbt.input(nbt, ghost.registryAccess()), ghost.level(), new net.minecraft.world.entity.EntitySpawnRequest(net.minecraft.world.entity.EntitySpawnReason.LOAD, false)).orElse(null);
        if (entity instanceof LivingEntity body) {
            body.setId(com.maitreyoshi.mcavie.compat.client.GuiEntity.fakeId());
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                body.setItemSlot(slot, ItemStack.EMPTY);
            }
            body.setCustomName(null);
            ghost.clientBody = body;
            return body;
        }
        com.maitreyoshi.mcavie.life.Ghosts.debug("client : corps non recréé depuis l'apparence (id " + look.getStringOr("id", "?") + ")");
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
    public void submit(State state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
        GhostEntity ghost = state.ghost;
        LivingEntity body = ghost == null ? null : body(ghost);
        if (body == null) {
            return;
        }
        sync(ghost, body);
        alpha = ALPHA * Math.min(1f, (ghost.tickCount + state.tickDelta) / 30f);
        active = true;
        try {
            submitBody(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body), body, state.tickDelta, matrices, collector, camera);
        } catch (RuntimeException e) {
            com.maitreyoshi.mcavie.MCAVie.LOGGER.warn("Fantôme : rendu du corps impossible", e);
            ghost.clientBody = null;
        } finally {
            active = false;
        }
    }

    private static <S extends EntityRenderState> void submitBody(EntityRenderer<? super LivingEntity, S> renderer, LivingEntity body, float tickDelta,
                                                                PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
        S bodyState = renderer.createRenderState(body, tickDelta);
        bodyState.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        renderer.submit(bodyState, matrices, collector, camera);
    }

    /** Couleur ARVB tirée vers un blanc bleuté, avec l'alpha du fantôme. */
    public static int pale(int color) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        int a = (int) (255 * alpha);
        return a << 24 | pale(r, 0) << 16 | pale(g, 5) << 8 | pale(b, 20);
    }

    private static int pale(int c, int extra) {
        return Math.min(255, 140 + c * 115 / 255 + extra);
    }
}
