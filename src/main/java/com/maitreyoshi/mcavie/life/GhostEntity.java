package com.maitreyoshi.mcavie.life;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Fantôme d'un défunt : se promène la nuit autour de son autel, intouchable, et s'efface à l'aube. */
public class GhostEntity extends PathAwareEntity {
    private static final TrackedData<NbtCompound> LOOK = DataTracker.registerData(GhostEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    public static final int RANGE = 48;
    private static final int TALKS = 10;
    private static final int ALONE = 6;
    private static final SoundEvent[] WHISPERS = {SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM,
            SoundEvents.ENTITY_WANDERING_TRADER_AMBIENT, SoundEvents.PARTICLE_SOUL_ESCAPE};

    @Nullable
    private Vec3d drift;
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
    public NbtCompound clientLook;

    public GhostEntity(EntityType<? extends GhostEntity> type, World world) {
        super(type, world);
        setInvulnerable(true);
        setSilent(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5).add(EntityAttributes.GENERIC_MAX_HEALTH, 20);
    }

    void setup(NbtCompound look, UUID person, BlockPos altar) {
        this.person = person;
        this.altar = altar;
        dataTracker.set(LOOK, look);
        setPositionTarget(altar, RANGE);
    }

    public NbtCompound look() {
        return dataTracker.get(LOOK);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(LOOK, new NbtCompound());
    }

    @Override
    protected void initGoals() {
        goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 6));
        goalSelector.add(4, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        World world = getWorld();
        if (world.isClient) {
            if (random.nextInt(12) == 0) {
                world.addParticle(ParticleTypes.WHITE_ASH, getParticleX(0.6), getRandomBodyY(), getParticleZ(0.6), 0, 0.02, 0);
            }
            return;
        }
        ServerWorld server = (ServerWorld) world;
        if (age == 1) {
            server.spawnParticles(ParticleTypes.SOUL, getX(), getBodyY(0.5), getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        }
        hover();
        if (random.nextInt(240) == 0) {
            server.playSound(null, getX(), getEyeY(), getZ(), WHISPERS[random.nextInt(WHISPERS.length)], SoundCategory.NEUTRAL,
                    0.35f, 0.45f + random.nextFloat() * 0.2f);
        }
        if (reply != null && age >= replyAt) {
            say(server, reply);
            reply = null;
        }
        if (talkCooldown > 0) {
            talkCooldown--;
        } else if (random.nextInt(200) == 0) {
            talk(server);
        }
        if (age % 20 != 0) {
            return;
        }
        boolean altarGone = altar == null || person == null || world.isChunkLoaded(altar)
                && !(world.getBlockEntity(altar) instanceof GhostAltarBlockEntity be && person.equals(be.person()));
        if (altarGone || !Ghosts.isNight(world)) {
            ((ServerWorld) world).spawnParticles(ParticleTypes.SOUL, getX(), getBodyY(0.5), getZ(), 12, 0.3, 0.6, 0.3, 0.02);
            discard();
        }
    }

    private void hover() {
        if (altar == null) {
            return;
        }
        if (drift == null || drift.squaredDistanceTo(getX(), drift.y, getZ()) < 1.5 || random.nextInt(600) == 0) {
            drift = random.nextInt(3) == 0 ? null : Vec3d.ofBottomCenter(altar).add(random.nextInt(RANGE / 2 + 1) - RANGE / 4, 0, random.nextInt(RANGE / 2 + 1) - RANGE / 4);
            if (drift == null) {
                getMoveControl().moveTo(getX(), getY(), getZ(), 0);
                return;
            }
        }
        if (age % 20 == 0 || !getMoveControl().isMoving()) {
            getMoveControl().moveTo(drift.x, getY(), drift.z, 0.6);
        }
    }

    private Text name() {
        String json = look().getString("CustomName");
        Text name = json.isEmpty() ? null : Text.Serializer.fromJson(json);
        return name == null ? Text.translatable("entity.mca_vie.ghost") : name;
    }

    private void say(ServerWorld world, String key) {
        Text line = Text.empty().append(Text.literal("   ").styled(st -> st.withInsertion(com.maitreyoshi.mcavie.social.SocialConversation.HEAD_MARKER + getId() + ":" + getUuid())))
                .append(Text.translatable("mca_vie.ghost.line", name(), Text.translatable(key)).formatted(Formatting.GRAY, Formatting.ITALIC));
        world.getPlayers(p -> p.squaredDistanceTo(this) < 16 * 16).forEach(p -> p.sendMessage(line));
        world.playSound(null, getX(), getEyeY(), getZ(), WHISPERS[random.nextInt(WHISPERS.length)], SoundCategory.NEUTRAL, 0.3f, 0.5f);
    }

    private void talk(ServerWorld world) {
        talkCooldown = 600 + random.nextInt(900);
        if (world.getPlayers(p -> p.squaredDistanceTo(this) < 16 * 16).isEmpty()) {
            return;
        }
        GhostEntity other = world.getEntitiesByClass(GhostEntity.class, getBoundingBox().expand(10), g -> g != this && g.reply == null)
                .stream().findAny().orElse(null);
        if (other == null) {
            say(world, "mca_vie.ghost.alone." + random.nextInt(ALONE));
            return;
        }
        int n = random.nextInt(TALKS);
        say(world, "mca_vie.ghost.talk." + n + ".a");
        getLookControl().lookAt(other);
        other.getLookControl().lookAt(this);
        other.reply = "mca_vie.ghost.talk." + n + ".b";
        other.replyAt = other.age + 50 + random.nextInt(30);
        other.talkCooldown = Math.max(other.talkCooldown, 400);
    }

    public static boolean natural(net.minecraft.block.BlockState state) {
        return state.isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_OVERWORLD) || state.isIn(net.minecraft.registry.tag.BlockTags.DIRT)
                || state.isIn(net.minecraft.registry.tag.BlockTags.SAND) || state.isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_NETHER)
                || state.isIn(net.minecraft.registry.tag.BlockTags.NYLIUM) || state.isIn(net.minecraft.registry.tag.BlockTags.SNOW)
                || state.isIn(net.minecraft.registry.tag.BlockTags.ICE) || state.isIn(net.minecraft.registry.tag.BlockTags.TERRACOTTA)
                || state.isOf(net.minecraft.block.Blocks.GRAVEL) || state.isOf(net.minecraft.block.Blocks.CLAY) || state.isOf(net.minecraft.block.Blocks.BEDROCK)
                || state.isOf(net.minecraft.block.Blocks.SANDSTONE) || state.isOf(net.minecraft.block.Blocks.RED_SANDSTONE)
                || state.isIn(net.minecraft.registry.tag.BlockTags.COAL_ORES) || state.isIn(net.minecraft.registry.tag.BlockTags.IRON_ORES)
                || state.isIn(net.minecraft.registry.tag.BlockTags.COPPER_ORES);
    }

    /** Murs des bâtiments traversables ; le sol sous ses pieds et le décor naturel restent solides. */
    public boolean passesThrough(net.minecraft.block.BlockState state, BlockPos pos) {
        return pos.getY() + 0.5 > getY() && !natural(state);
    }

    @Override
    public boolean isInsideWall() {
        return false;
    }

    // Intouchable : on le traverse, on ne peut ni le frapper ni lui parler.
    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    protected void tickCramming() {
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD) && super.damage(source, amount);
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        return ActionResult.PASS;
    }

    @Override
    public boolean canBeLeashedBy(PlayerEntity player) {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // Jamais sauvegardé : l'autel le fait revenir chaque nuit.
    @Override
    public boolean saveNbt(NbtCompound nbt) {
        return false;
    }

    @Override
    public boolean saveSelfNbt(NbtCompound nbt) {
        return false;
    }
}
