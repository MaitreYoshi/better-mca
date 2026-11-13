package com.maitreyoshi.mcavie.path;

import com.maitreyoshi.mcavie.compat.ServerTickEvents;
import com.maitreyoshi.mcavie.mixin.path.PathNodeMakerAccessor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Échelles et trappes pour les PNJ MCA. Le pathfinding terrestre (MCA comme vanilla) ne crée aucun nœud
 * vertical : un lit accessible seulement par une échelle est injoignable et le PNJ s'arrête au point le
 * plus proche, souvent à la verticale du lit. On ajoute des nœuds dans les puits d'échelle (entrée par le
 * haut ou par le côté, montée, descente, trappe en bois au-dessus de l'échelle), puis on aide la navigation
 * à les suivre : saut pour grimper, ouverture des trappes en bois, refermées une fois le passage libre.
 */
public final class LadderPaths {
    private static final int CLOSE_DELAY = 40;
    private static final Map<ServerLevel, Map<BlockPos, Opened>> OPENED = new WeakHashMap<>();

    /** Trappe ouverte par un PNJ : état d'origine à remettre, dernier passage. */
    private record Opened(BlockState original, long time) {
    }

    private LadderPaths() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(LadderPaths::closeTrapdoors);
    }

    private static boolean climbable(BlockGetter world, BlockPos pos) {
        return world.getBlockState(pos).is(BlockTags.CLIMBABLE);
    }

    /** Trappe franchissable : ouverte, ou en bois (le PNJ l'ouvre). */
    private static boolean trapdoor(BlockState state) {
        return state.is(BlockTags.TRAPDOORS) && (state.is(BlockTags.WOODEN_TRAPDOORS) || state.getValue(TrapDoorBlock.OPEN));
    }

    /** Case d'un puits d'échelle : échelle, ou trappe posée juste au-dessus ou au-dessous d'une échelle. */
    private static boolean shaft(BlockGetter world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.is(BlockTags.CLIMBABLE)) return true;
        return trapdoor(state) && (climbable(world, pos.below()) || climbable(world, pos.above()));
    }

    /** Le corps peut occuper ce bloc : vide de collision, grimpable ou trappe franchissable. */
    private static boolean passable(BlockGetter world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.is(BlockTags.CLIMBABLE) || trapdoor(state)
                || (state.getCollisionShape(world, pos).isEmpty() && state.getFluidState().isEmpty());
    }

    private static boolean bodyFits(BlockGetter world, BlockPos feet, int height) {
        for (int i = 0; i < height; i++) {
            if (!passable(world, feet.above(i))) return false;
        }
        return true;
    }

    public static int addSuccessors(PathNodeMakerAccessor maker, Node[] successors, Node node, int count) {
        BlockGetter world = maker.mcavie$context() == null ? null : maker.mcavie$context().level();
        Mob entity = maker.mcavie$entity();
        if (world == null || entity == null) return count;
        int height = Math.max(1, Mth.ceil(entity.getBbHeight()));
        BlockPos pos = node.asBlockPos();

        if (shaft(world, pos)) {
            // monter dans le puits, ou en sortir par le haut
            BlockPos up = pos.above();
            if (bodyFits(world, up, height)) count = add(maker, successors, count, up);
            // descendre dans le puits
            BlockPos down = pos.below();
            if (shaft(world, down)) count = add(maker, successors, count, down);
        }
        if (shaft(world, pos.below())) {
            // debout au-dessus d'un puits (trou ou trappe dans un plancher) : on peut y descendre
            count = add(maker, successors, count, pos.below());
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            // entrer de côté dans une échelle à la même hauteur
            if (shaft(world, side) && bodyFits(world, side, height)) {
                count = add(maker, successors, count, side);
            } else if (shaft(world, side.below()) && bodyFits(world, side, height)) {
                // puits qui commence dans le plancher juste à côté
                count = add(maker, successors, count, side.below());
            }
        }
        return count;
    }

    private static int add(PathNodeMakerAccessor maker, Node[] successors, int count, BlockPos pos) {
        if (count >= successors.length) return count;
        Node n = maker.mcavie$getNode(pos.getX(), pos.getY(), pos.getZ());
        if (n == null || n.closed) return count;
        for (int i = 0; i < count; i++) {
            if (successors[i] == n) return count;
        }
        n.type = PathType.WALKABLE;
        n.costMalus = Math.max(n.costMalus, 0.0f);
        successors[count] = n;
        return count + 1;
    }

    public static boolean inShaft(Mob entity) {
        return entity.onClimbable() || shaft(entity.level(), entity.blockPosition());
    }

    /** Appelé à chaque tick de navigation d'un PNJ MCA. */
    public static void followLadder(Mob entity, Path path) {
        if (entity.level() instanceof ServerLevel world) {
            openTrapdoors(world, entity, path);
        }
        int bx = Mth.floor(entity.getX());
        int bz = Mth.floor(entity.getZ());
        boolean onShaft = inShaft(entity) || shaft(entity.level(), entity.blockPosition().below());
        if (!onShaft) return;
        double y = entity.getY();
        Vec3i node = path.getNextNodePos();
        // nœuds de la colonne atteints : passer au suivant (hors du sol, la navigation vanilla ne le fait pas)
        for (int guard = 0; guard < 16 && path.getNextNodeIndex() + 1 < path.getNodeCount(); guard++) {
            if (node.getX() != bx || node.getZ() != bz) break;
            Vec3i next = path.getNodePos(path.getNextNodeIndex() + 1);
            boolean nextInColumn = next.getX() == bx && next.getZ() == bz;
            boolean passed;
            if (nextInColumn && next.getY() > node.getY()) passed = y >= node.getY() - 0.05;
            else if (nextInColumn && next.getY() < node.getY()) passed = y <= node.getY() + 0.05;
            else passed = Math.abs(y - node.getY()) <= 0.1;
            if (!passed) break;
            path.advance();
            node = path.getNextNodePos();
        }
        boolean inColumn = node.getX() == bx && node.getZ() == bz;
        Vec3 v = entity.getDeltaMovement();
        if (inColumn && node.getY() > y + 0.05) {
            // monter : vitesse d'échelle, aussi dans la case de trappe au sommet (non grimpable)
            entity.setDeltaMovement(v.x, Math.max(v.y, 0.2), v.z);
        } else if (inColumn && node.getY() < y - 0.05) {
            if (entity.onClimbable()) {
                descend(entity, v);
            } else {
                // posé sur le bord de l'échelle (PNJ large) : s'écarter du mur pour entrer dans le puits
                BlockState below = entity.level().getBlockState(entity.blockPosition().below());
                if (below.is(BlockTags.CLIMBABLE) && below.hasProperty(LadderBlock.FACING)) {
                    Direction f = below.getValue(LadderBlock.FACING);
                    entity.setPos(entity.getX() + f.getStepX() * 0.05, y, entity.getZ() + f.getStepZ() * 0.05);
                }
            }
        }
    }

    /**
     * Après le tick de navigation : dans un puits, le PNJ vise le centre de la colonne, décalé du mur de l'échelle
     * (un PNJ large frotte sinon contre l'échelle et la vanilla le fait remonter), et tant qu'il n'est pas à la
     * hauteur du nœud de sortie il reste dans la colonne (sinon sa tête heurte le plafond ou il retombe).
     */
    public static void holdColumn(Mob entity, Path path, double speed) {
        Level world = entity.level();
        Vec3i node = path.getNextNodePos();
        BlockPos nodePos = new BlockPos(node);
        int bx = Mth.floor(entity.getX());
        int bz = Mth.floor(entity.getZ());
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
            offset = ladderFacing(world, entity.blockPosition());
        } else if (inShaft && adjacent && !inColumn) {
            // sortie de plain-pied (bas de l'échelle) : la MoveControl vanilla sauterait (pieds dans le bloc
            // d'échelle) et le PNJ remonterait ; on le pousse nous-mêmes vers la case de sortie
            entity.getMoveControl().setWantedPosition(entity.getX(), y, entity.getZ(), speed);
            Vec3 v = entity.getDeltaMovement();
            double vx = Mth.clamp(node.getX() + 0.5 - entity.getX(), -0.15, 0.15);
            double vz = Mth.clamp(node.getZ() + 0.5 - entity.getZ(), -0.15, 0.15);
            entity.horizontalCollision = false;
            entity.setDeltaMovement(vx, v.y, vz);
            return;
        } else {
            return;
        }
        if (offset != null) {
            cx += offset.getStepX() * 0.08;
            cz += offset.getStepZ() * 0.08;
        }
        double dy = node.getY() - y;
        if (!inShaft || Math.abs(dy) <= 0.05) {
            entity.getMoveControl().setWantedPosition(cx, node.getY(), cz, speed);
            return;
        }
        // phase verticale : la MoveControl, cible presque à la verticale, partirait dans une direction quelconque ;
        // on la neutralise (cible = position actuelle) et on pilote la vitesse nous-mêmes
        entity.getMoveControl().setWantedPosition(entity.getX(), y, entity.getZ(), speed);
        Vec3 v = entity.getDeltaMovement();
        double vx = Mth.clamp((cx - entity.getX()) * 0.5, -0.1, 0.1);
        double vz = Mth.clamp((cz - entity.getZ()) * 0.5, -0.1, 0.1);
        if (dy > 0) {
            entity.setDeltaMovement(vx, Math.max(v.y, 0.2), vz);
        } else {
            entity.horizontalCollision = false;
            entity.setDeltaMovement(vx, entity.onClimbable() ? Math.min(v.y, -0.15) : v.y, vz);
        }
    }

    /** Orientation de l'échelle de cette case ou de celle du dessous (case de trappe). */
    private static Direction ladderFacing(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.is(BlockTags.CLIMBABLE)) state = world.getBlockState(pos.below());
        return state.hasProperty(LadderBlock.FACING) && state.is(BlockTags.CLIMBABLE) ? state.getValue(LadderBlock.FACING) : null;
    }

    /** Descente d'échelle : sans ça, un PNJ large frotte contre l'échelle et la vanilla le fait remonter. */
    private static void descend(Mob entity, Vec3 v) {
        entity.horizontalCollision = false;
        entity.setDeltaMovement(v.x, Math.min(v.y, -0.15), v.z);
    }

    private static void openTrapdoors(ServerLevel world, Mob entity, Path path) {
        int index = path.getNextNodeIndex();
        for (int i = index; i < Math.min(index + 2, path.getNodeCount()); i++) {
            // seulement une trappe traversée par le chemin (nœud dans sa case), pas une trappe qu'on enjambe
            BlockPos p = path.getNodePos(i);
            BlockState state = world.getBlockState(p);
            if (!state.is(BlockTags.WOODEN_TRAPDOORS) || !shaft(world, p) || !p.closerToCenterThan(entity.position(), 2.5)) continue;
            // ouverte contre le mur de l'échelle : panneau grimpable, sans gêner le passage (le côté opposé
            // laisserait moins de place qu'un PNJ large n'en occupe)
            Direction ladder = ladderFacing(world, p);
            BlockState wanted = state.setValue(TrapDoorBlock.OPEN, true);
            if (ladder != null) wanted = wanted.setValue(TrapDoorBlock.FACING, ladder);
            Map<BlockPos, Opened> opened = OPENED.computeIfAbsent(world, w -> new HashMap<>());
            Opened previous = opened.get(p);
            if (state != wanted) {
                world.setBlock(p, wanted, Block.UPDATE_CLIENTS);
                if (!state.getValue(TrapDoorBlock.OPEN)) {
                    world.playSound(null, p, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
                    world.gameEvent(entity, GameEvent.BLOCK_OPEN, p);
                }
            } else if (previous == null) {
                continue; // déjà ouverte comme il faut par quelqu'un d'autre : on n'y touche pas
            }
            opened.put(p.immutable(), new Opened(previous != null ? previous.original() : state, world.getGameTime()));
        }
    }

    /** Referme les trappes ouvertes par un PNJ dès que personne ne se trouve dans le passage. */
    private static void closeTrapdoors(ServerLevel world) {
        Map<BlockPos, Opened> opened = OPENED.get(world);
        if (opened == null || opened.isEmpty()) return;
        long now = world.getGameTime();
        Iterator<Map.Entry<BlockPos, Opened>> it = opened.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Opened> e = it.next();
            if (now - e.getValue().time() < CLOSE_DELAY) continue;
            BlockPos p = e.getKey();
            if (!world.hasChunkAt(p)) {
                it.remove();
                continue;
            }
            AABB passage = new AABB(p).inflate(0.5, 2.0, 0.5);
            if (!world.getEntitiesOfClass(LivingEntity.class, passage, LivingEntity::isAlive).isEmpty()) continue;
            BlockState state = world.getBlockState(p);
            BlockState original = e.getValue().original();
            // remettre la trappe comme elle était, sauf si quelqu'un l'a changée entre-temps
            if (state.is(BlockTags.WOODEN_TRAPDOORS) && state.getValue(TrapDoorBlock.OPEN) && state.getBlock() == original.getBlock()) {
                world.setBlock(p, original, Block.UPDATE_CLIENTS);
                if (!original.getValue(TrapDoorBlock.OPEN)) {
                    world.playSound(null, p, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
                    world.gameEvent(null, GameEvent.BLOCK_CLOSE, p);
                }
            }
            it.remove();
        }
    }
}
