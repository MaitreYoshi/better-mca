package com.maitreyoshi.mcavie.life;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Fantôme d'un défunt : se promène la nuit autour de son autel, intouchable, et s'efface à l'aube. */
public class GhostEntity extends PathfinderMob {
    private static final EntityDataAccessor<CompoundTag> LOOK = SynchedEntityData.defineId(GhostEntity.class, EntityDataSerializers.COMPOUND_TAG);
    public static final int RANGE = 48;
    private static final int TALKS = 10;
    private static final int ALONE = 6;
    private static final SoundEvent[] WHISPERS = {SoundEvents.VILLAGER_AMBIENT, SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM,
            SoundEvents.WANDERING_TRADER_AMBIENT, SoundEvents.SOUL_ESCAPE};

    @Nullable
    private Vec3 drift;
    @Nullable
    private String reply;
    private int replyAt;
    private int talkCooldown = 400;

    @Nullable
    private UUID person;
    @Nullable
    private BlockPos altar;
    /** Corps MCA utilisé pour le rendu côté client. */
    @Nullable
    public Object clientBody;
    @Nullable
    public CompoundTag clientLook;

    public GhostEntity(EntityType<? extends GhostEntity> type, Level world) {
        super(type, world);
        setInvulnerable(true);
        setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.MAX_HEALTH, 20);
    }

    void setup(CompoundTag look, UUID person, BlockPos altar) {
        this.person = person;
        this.altar = altar;
        entityData.set(LOOK, look);
        restrictTo(altar, RANGE);
    }

    public CompoundTag look() {
        return entityData.get(LOOK);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(LOOK, new CompoundTag());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        Level world = level();
        if (world.isClientSide) {
            if (random.nextInt(12) == 0) {
                world.addParticle(ParticleTypes.WHITE_ASH, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.02, 0);
            }
            return;
        }
        ServerLevel server = (ServerLevel) world;
        if (tickCount == 1) {
            server.sendParticles(ParticleTypes.SOUL, getX(), getY(0.5), getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        }
        hover();
        if (random.nextInt(240) == 0) {
            server.playSound(null, getX(), getEyeY(), getZ(), WHISPERS[random.nextInt(WHISPERS.length)], SoundSource.NEUTRAL,
                    0.35f, 0.45f + random.nextFloat() * 0.2f);
        }
        if (reply != null && tickCount >= replyAt) {
            say(server, reply);
            reply = null;
        }
        if (talkCooldown > 0) {
            talkCooldown--;
        } else if (random.nextInt(200) == 0) {
            talk(server);
        }
        if (tickCount % 20 != 0) {
            return;
        }
        boolean altarGone = altar == null || person == null || world.hasChunkAt(altar)
                && !(world.getBlockEntity(altar) instanceof GhostAltarBlockEntity be && person.equals(be.person()));
        if (altarGone || !Ghosts.isNight(world)) {
            ((ServerLevel) world).sendParticles(ParticleTypes.SOUL, getX(), getY(0.5), getZ(), 12, 0.3, 0.6, 0.3, 0.02);
            discard();
        }
    }

    private void hover() {
        if (altar == null) {
            return;
        }
        if (drift == null || drift.distanceToSqr(getX(), drift.y, getZ()) < 1.5 || random.nextInt(600) == 0) {
            drift = random.nextInt(3) == 0 ? null : Vec3.atBottomCenterOf(altar).add(random.nextInt(RANGE / 2 + 1) - RANGE / 4, 0, random.nextInt(RANGE / 2 + 1) - RANGE / 4);
            if (drift == null) {
                getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
                return;
            }
        }
        if (tickCount % 20 == 0 || !getMoveControl().hasWanted()) {
            getMoveControl().setWantedPosition(drift.x, getY(), drift.z, 0.6);
        }
    }

    private Component name() {
        String json = look().getString("CustomName");
        Component name = json.isEmpty() ? null : Component.Serializer.fromJson(json);
        return name == null ? Component.translatable("entity.mca_vie.ghost") : name;
    }

    private void say(ServerLevel world, String key) {
        Component line = Component.empty().append(Component.literal("   ").withStyle(st -> st.withInsertion(com.maitreyoshi.mcavie.social.SocialConversation.HEAD_MARKER + getId() + ":" + getUUID())))
                .append(Component.translatable("mca_vie.ghost.line", name(), Component.translatable(key)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        world.getPlayers(p -> p.distanceToSqr(this) < 16 * 16).forEach(p -> p.sendSystemMessage(line));
        world.playSound(null, getX(), getEyeY(), getZ(), WHISPERS[random.nextInt(WHISPERS.length)], SoundSource.NEUTRAL, 0.3f, 0.5f);
    }

    private void talk(ServerLevel world) {
        talkCooldown = 600 + random.nextInt(900);
        if (world.getPlayers(p -> p.distanceToSqr(this) < 16 * 16).isEmpty()) {
            return;
        }
        GhostEntity other = world.getEntitiesOfClass(GhostEntity.class, getBoundingBox().inflate(10), g -> g != this && g.reply == null)
                .stream().findAny().orElse(null);
        if (other == null) {
            say(world, "mca_vie.ghost.alone." + random.nextInt(ALONE));
            return;
        }
        int n = random.nextInt(TALKS);
        say(world, "mca_vie.ghost.talk." + n + ".a");
        getLookControl().setLookAt(other);
        other.getLookControl().setLookAt(this);
        other.reply = "mca_vie.ghost.talk." + n + ".b";
        other.replyAt = other.tickCount + 50 + random.nextInt(30);
        other.talkCooldown = Math.max(other.talkCooldown, 400);
    }

    public static boolean natural(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || state.is(net.minecraft.tags.BlockTags.DIRT)
                || state.is(net.minecraft.tags.BlockTags.SAND) || state.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER)
                || state.is(net.minecraft.tags.BlockTags.NYLIUM) || state.is(net.minecraft.tags.BlockTags.SNOW)
                || state.is(net.minecraft.tags.BlockTags.ICE) || state.is(net.minecraft.tags.BlockTags.TERRACOTTA)
                || state.is(net.minecraft.world.level.block.Blocks.GRAVEL) || state.is(net.minecraft.world.level.block.Blocks.CLAY) || state.is(net.minecraft.world.level.block.Blocks.BEDROCK)
                || state.is(net.minecraft.world.level.block.Blocks.SANDSTONE) || state.is(net.minecraft.world.level.block.Blocks.RED_SANDSTONE)
                || state.is(net.minecraft.tags.BlockTags.COAL_ORES) || state.is(net.minecraft.tags.BlockTags.IRON_ORES)
                || state.is(net.minecraft.tags.BlockTags.COPPER_ORES);
    }

    /** Murs des bâtiments traversables ; le sol sous ses pieds et le décor naturel restent solides. */
    public boolean passesThrough(net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
        return pos.getY() + 0.5 > getY() && !natural(state);
    }

    @Override
    public boolean isInWall() {
        return false;
    }

    // Intouchable : on le traverse, on ne peut ni le frapper ni lui parler.
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD) && super.hurt(source, amount);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // Jamais sauvegardé : l'autel le fait revenir chaque nuit.
    @Override
    public boolean save(CompoundTag nbt) {
        return false;
    }

    @Override
    public boolean saveAsPassenger(CompoundTag nbt) {
        return false;
    }
}
