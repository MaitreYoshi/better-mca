package com.maitreyoshi.mcavie.path;

import com.maitreyoshi.mcavie.compat.ServerTickEvents;
import com.maitreyoshi.mcavie.mixin.path.PathNodeMakerAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Échelles et trappes pour les PNJ MCA. Le pathfinding terrestre (MCA comme vanilla) ne crée aucun nœud
 * vertical : un lit accessible seulement par une échelle est injoignable et le PNJ s'arrête au point le
 * plus proche, souvent à la verticale du lit. On ajoute des nœuds dans les puits d'échelle (entrée par le
 * haut ou par le côté, montée, descente, trappe en bois au-dessus de l'échelle), puis on aide la navigation
 * à les suivre : saut pour grimper, ouverture des trappes en bois, refermées une fois le passage libre.
 */
public final class LadderPaths {
    private static final int CLOSE_DELAY = 40;
    private static final Map<ServerWorld, Map<BlockPos, Opened>> OPENED = new WeakHashMap<>();

    /** Trappe ouverte par un PNJ : état d'origine à remettre, dernier passage. */
    private record Opened(BlockState original, long time) {
    }

    private LadderPaths() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(LadderPaths::closeTrapdoors);
    }

    private static boolean climbable(BlockView world, BlockPos pos) {
        return world.getBlockState(pos).isIn(BlockTags.CLIMBABLE);
    }

    /** Trappe franchissable : ouverte, ou en bois (le PNJ l'ouvre). */
    private static boolean trapdoor(BlockState state) {
        return state.isIn(BlockTags.TRAPDOORS) && (state.isIn(BlockTags.WOODEN_TRAPDOORS) || state.get(TrapdoorBlock.OPEN));
    }

    /** Case d'un puits d'échelle : échelle, ou trappe posée juste au-dessus ou au-dessous d'une échelle. */
    private static boolean shaft(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isIn(BlockTags.CLIMBABLE)) return true;
        return trapdoor(state) && (climbable(world, pos.down()) || climbable(world, pos.up()));
    }

    /** Le corps peut occuper ce bloc : vide de collision, grimpable ou trappe franchissable. */
    private static boolean passable(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isIn(BlockTags.CLIMBABLE) || trapdoor(state)
                || (state.getCollisionShape(world, pos).isEmpty() && state.getFluidState().isEmpty());
    }

    private static boolean bodyFits(BlockView world, BlockPos feet, int height) {
        for (int i = 0; i < height; i++) {
            if (!passable(world, feet.up(i))) return false;
        }
        return true;
    }

    public static int addSuccessors(PathNodeMakerAccessor maker, PathNode[] successors, PathNode node, int count) {
        BlockView world = maker.mcavie$world();
        MobEntity entity = maker.mcavie$entity();
        if (world == null || entity == null) return count;
        int height = Math.max(1, MathHelper.ceil(entity.getHeight()));
        BlockPos pos = node.getBlockPos();

        if (shaft(world, pos)) {
            // monter dans le puits, ou en sortir par le haut
            BlockPos up = pos.up();
            if (bodyFits(world, up, height)) count = add(maker, successors, count, up);
            // descendre dans le puits
            BlockPos down = pos.down();
            if (shaft(world, down)) count = add(maker, successors, count, down);
        }
        if (shaft(world, pos.down())) {
            // debout au-dessus d'un puits (trou ou trappe dans un plancher) : on peut y descendre
            count = add(maker, successors, count, pos.down());
        }
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos side = pos.offset(dir);
            // entrer de côté dans une échelle à la même hauteur
            if (shaft(world, side) && bodyFits(world, side, height)) {
                count = add(maker, successors, count, side);
            } else if (shaft(world, side.down()) && bodyFits(world, side, height)) {
                // puits qui commence dans le plancher juste à côté
                count = add(maker, successors, count, side.down());
            }
        }
        return count;
    }

    private static int add(PathNodeMakerAccessor maker, PathNode[] successors, int count, BlockPos pos) {
        if (count >= successors.length) return count;
        PathNode n = maker.mcavie$getNode(pos.getX(), pos.getY(), pos.getZ());
        if (n == null || n.visited) return count;
        for (int i = 0; i < count; i++) {
            if (successors[i] == n) return count;
        }
        n.type = PathNodeType.WALKABLE;
        n.penalty = Math.max(n.penalty, 0.0f);
        successors[count] = n;
        return count + 1;
    }

    public static boolean inShaft(MobEntity entity) {
        return entity.isClimbing() || shaft(entity.getWorld(), entity.getBlockPos());
    }

    /** Appelé à chaque tick de navigation d'un PNJ MCA. */
    public static void followLadder(MobEntity entity, Path path) {
        if (entity.getWorld() instanceof ServerWorld world) {
            openTrapdoors(world, entity, path);
        }
        int bx = MathHelper.floor(entity.getX());
        int bz = MathHelper.floor(entity.getZ());
        boolean onShaft = inShaft(entity) || shaft(entity.getWorld(), entity.getBlockPos().down());
        if (!onShaft) return;
        double y = entity.getY();
        Vec3i node = path.getCurrentNodePos();
        // nœuds de la colonne atteints : passer au suivant (hors du sol, la navigation vanilla ne le fait pas)
        for (int guard = 0; guard < 16 && path.getCurrentNodeIndex() + 1 < path.getLength(); guard++) {
            if (node.getX() != bx || node.getZ() != bz) break;
            Vec3i next = path.getNodePos(path.getCurrentNodeIndex() + 1);
            boolean nextInColumn = next.getX() == bx && next.getZ() == bz;
            boolean passed;
            if (nextInColumn && next.getY() > node.getY()) passed = y >= node.getY() - 0.05;
            else if (nextInColumn && next.getY() < node.getY()) passed = y <= node.getY() + 0.05;
            else passed = Math.abs(y - node.getY()) <= 0.1;
            if (!passed) break;
            path.next();
            node = path.getCurrentNodePos();
        }
        boolean inColumn = node.getX() == bx && node.getZ() == bz;
        Vec3d v = entity.getVelocity();
        if (inColumn && node.getY() > y + 0.05) {
            // monter : vitesse d'échelle, aussi dans la case de trappe au sommet (non grimpable)
            entity.setVelocity(v.x, Math.max(v.y, 0.2), v.z);
        } else if (inColumn && node.getY() < y - 0.05) {
            if (entity.isClimbing()) {
                descend(entity, v);
            } else {
                // posé sur le bord de l'échelle (PNJ large) : s'écarter du mur pour entrer dans le puits
                BlockState below = entity.getWorld().getBlockState(entity.getBlockPos().down());
                if (below.isIn(BlockTags.CLIMBABLE) && below.contains(LadderBlock.FACING)) {
                    Direction f = below.get(LadderBlock.FACING);
                    entity.setPosition(entity.getX() + f.getOffsetX() * 0.05, y, entity.getZ() + f.getOffsetZ() * 0.05);
                }
            }
        }
    }

    /**
     * Après le tick de navigation : dans un puits, le PNJ vise le centre de la colonne, décalé du mur de l'échelle
     * (un PNJ large frotte sinon contre l'échelle et la vanilla le fait remonter), et tant qu'il n'est pas à la
     * hauteur du nœud de sortie il reste dans la colonne (sinon sa tête heurte le plafond ou il retombe).
     */
    public static void holdColumn(MobEntity entity, Path path, double speed) {
        World world = entity.getWorld();
        Vec3i node = path.getCurrentNodePos();
        BlockPos nodePos = new BlockPos(node);
        int bx = MathHelper.floor(entity.getX());
        int bz = MathHelper.floor(entity.getZ());
        boolean inColumn = node.getX() == bx && node.getZ() == bz;
        boolean adjacent = Math.abs(node.getX() - bx) <= 1 && Math.abs(node.getZ() - bz) <= 1;
        Direction ladderFacing = ladderFacing(world, nodePos);
        boolean shaftNode = shaft(world, nodePos) && ladderFacing != null;
        boolean inShaft = inShaft(entity);
        double y = entity.getY();
        double cx;
        double cz;
        Direction offset;
        if (shaftNode && (inColumn || adjacent)) {
            // viser une case d'échelle : centre de la colonne, décalé du mur (sinon un PNJ large bute sur l'échelle)
            cx = node.getX() + 0.5;
            cz = node.getZ() + 0.5;
            offset = ladderFacing;
        } else if (inShaft && adjacent && Math.abs(node.getY() - y) > 0.05) {
            // sortie du puits vers une case voisine : rester dans la colonne jusqu'à la bonne hauteur
            cx = bx + 0.5;
            cz = bz + 0.5;
            offset = ladderFacing(world, entity.getBlockPos());
        } else if (inShaft && adjacent && !inColumn) {
            // sortie de plain-pied (bas de l'échelle) : la MoveControl vanilla sauterait (pieds dans le bloc
            // d'échelle) et le PNJ remonterait ; on le pousse nous-mêmes vers la case de sortie
            entity.getMoveControl().moveTo(entity.getX(), y, entity.getZ(), speed);
            Vec3d v = entity.getVelocity();
            double vx = MathHelper.clamp(node.getX() + 0.5 - entity.getX(), -0.15, 0.15);
            double vz = MathHelper.clamp(node.getZ() + 0.5 - entity.getZ(), -0.15, 0.15);
            entity.horizontalCollision = false;
            entity.setVelocity(vx, v.y, vz);
            return;
        } else {
            return;
        }
        if (offset != null) {
            cx += offset.getOffsetX() * 0.08;
            cz += offset.getOffsetZ() * 0.08;
        }
        double dy = node.getY() - y;
        if (!inShaft || Math.abs(dy) <= 0.05) {
            entity.getMoveControl().moveTo(cx, node.getY(), cz, speed);
            return;
        }
        // phase verticale : la MoveControl, cible presque à la verticale, partirait dans une direction quelconque ;
        // on la neutralise (cible = position actuelle) et on pilote la vitesse nous-mêmes
        entity.getMoveControl().moveTo(entity.getX(), y, entity.getZ(), speed);
        Vec3d v = entity.getVelocity();
        double vx = MathHelper.clamp((cx - entity.getX()) * 0.5, -0.1, 0.1);
        double vz = MathHelper.clamp((cz - entity.getZ()) * 0.5, -0.1, 0.1);
        if (dy > 0) {
            entity.setVelocity(vx, Math.max(v.y, 0.2), vz);
        } else {
            entity.horizontalCollision = false;
            entity.setVelocity(vx, entity.isClimbing() ? Math.min(v.y, -0.15) : v.y, vz);
        }
    }

    /** Orientation de l'échelle de cette case ou de celle du dessous (case de trappe). */
    private static Direction ladderFacing(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.isIn(BlockTags.CLIMBABLE)) state = world.getBlockState(pos.down());
        return state.contains(LadderBlock.FACING) && state.isIn(BlockTags.CLIMBABLE) ? state.get(LadderBlock.FACING) : null;
    }

    /** Descente d'échelle : sans ça, un PNJ large frotte contre l'échelle et la vanilla le fait remonter. */
    private static void descend(MobEntity entity, Vec3d v) {
        entity.horizontalCollision = false;
        entity.setVelocity(v.x, Math.min(v.y, -0.15), v.z);
    }

    private static void openTrapdoors(ServerWorld world, MobEntity entity, Path path) {
        int index = path.getCurrentNodeIndex();
        for (int i = index; i < Math.min(index + 2, path.getLength()); i++) {
            // seulement une trappe traversée par le chemin (nœud dans sa case), pas une trappe qu'on enjambe
            BlockPos p = path.getNodePos(i);
            BlockState state = world.getBlockState(p);
            if (!state.isIn(BlockTags.WOODEN_TRAPDOORS) || !shaft(world, p) || !p.isWithinDistance(entity.getPos(), 2.5)) continue;
            // ouverte contre le mur de l'échelle : panneau grimpable, sans gêner le passage (le côté opposé
            // laisserait moins de place qu'un PNJ large n'en occupe)
            Direction ladder = ladderFacing(world, p);
            BlockState wanted = state.with(TrapdoorBlock.OPEN, true);
            if (ladder != null) wanted = wanted.with(TrapdoorBlock.FACING, ladder);
            Map<BlockPos, Opened> opened = OPENED.computeIfAbsent(world, w -> new HashMap<>());
            Opened previous = opened.get(p);
            if (state != wanted) {
                world.setBlockState(p, wanted, Block.NOTIFY_LISTENERS);
                if (!state.get(TrapdoorBlock.OPEN)) {
                    world.playSound(null, p, SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    world.emitGameEvent(entity, GameEvent.BLOCK_OPEN, p);
                }
            } else if (previous == null) {
                continue; // déjà ouverte comme il faut par quelqu'un d'autre : on n'y touche pas
            }
            opened.put(p.toImmutable(), new Opened(previous != null ? previous.original() : state, world.getTime()));
        }
    }

    /** Referme les trappes ouvertes par un PNJ dès que personne ne se trouve dans le passage. */
    private static void closeTrapdoors(ServerWorld world) {
        Map<BlockPos, Opened> opened = OPENED.get(world);
        if (opened == null || opened.isEmpty()) return;
        long now = world.getTime();
        Iterator<Map.Entry<BlockPos, Opened>> it = opened.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Opened> e = it.next();
            if (now - e.getValue().time() < CLOSE_DELAY) continue;
            BlockPos p = e.getKey();
            if (!world.isChunkLoaded(p)) {
                it.remove();
                continue;
            }
            Box passage = new Box(p).expand(0.5, 2.0, 0.5);
            if (!world.getEntitiesByClass(LivingEntity.class, passage, LivingEntity::isAlive).isEmpty()) continue;
            BlockState state = world.getBlockState(p);
            BlockState original = e.getValue().original();
            // remettre la trappe comme elle était, sauf si quelqu'un l'a changée entre-temps
            if (state.isIn(BlockTags.WOODEN_TRAPDOORS) && state.get(TrapdoorBlock.OPEN) && state.getBlock() == original.getBlock()) {
                world.setBlockState(p, original, Block.NOTIFY_LISTENERS);
                if (!original.get(TrapdoorBlock.OPEN)) {
                    world.playSound(null, p, SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    world.emitGameEvent(null, GameEvent.BLOCK_CLOSE, p);
                }
            }
            it.remove();
        }
    }
}
