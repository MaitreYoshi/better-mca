package com.maitreyoshi.mcavie.phone.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

public final class PhoneMap {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mca_vie", "phone_map");
    private static final int MAX_RADIUS = 12;
    private static final int NEW_PER_FRAME = 24;
    private static final int REFRESH_PER_FRAME = 4;

    private static final Map<Long, int[]> CACHE = new HashMap<>();
    private static ClientLevel world;
    private static DynamicTexture texture;
    private static int radius = -1;
    private static int centerX = Integer.MIN_VALUE;
    private static int centerZ = Integer.MIN_VALUE;
    private static List<ChunkPos> order = List.of();
    private static int refreshIndex;
    private static boolean dirty;

    private PhoneMap() {
    }

    /** Oublie le monde quitté ; la texture est réutilisée à la prochaine ouverture. */
    public static void clear() {
        world = null;
        CACHE.clear();
        order = List.of();
    }

    /** Taille de la texture en pixels (= blocs). */
    public static int size() {
        return (2 * radius + 1) * 16;
    }

    /** Coordonnée bloc du coin nord-ouest de la texture. */
    public static int originX() {
        return (centerX - radius) * 16;
    }

    public static int originZ() {
        return (centerZ - radius) * 16;
    }

    /** Met à jour la carte autour du joueur. Retourne false si rien n'est affichable. */
    public static boolean update(Minecraft client) {
        if (client.level == null || client.player == null) {
            return false;
        }
        if (client.level != world) {
            world = client.level;
            CACHE.clear();
            centerX = Integer.MIN_VALUE;
        }
        int wanted = Math.max(2, Math.min(MAX_RADIUS, client.options.getEffectiveRenderDistance()));
        if (wanted != radius || texture == null) {
            radius = wanted;
            if (texture != null) {
                texture.close();
            }
            texture = new DynamicTexture(new NativeImage(size(), size(), true));
            client.getTextureManager().register(TEXTURE, texture);
            centerX = Integer.MIN_VALUE;
        }
        ChunkPos center = client.player.chunkPosition();
        if (center.x() != centerX || center.z() != centerZ) {
            recenter(center);
        }

        int computed = 0;
        for (ChunkPos pos : order) {
            if (computed >= NEW_PER_FRAME) {
                break;
            }
            if (!CACHE.containsKey(pos.pack()) && compute(pos)) {
                computed++;
            }
        }
        for (int i = 0; i < REFRESH_PER_FRAME && !order.isEmpty(); i++) {
            refreshIndex = (refreshIndex + 1) % order.size();
            ChunkPos pos = order.get(refreshIndex);
            if (CACHE.containsKey(pos.pack())) {
                compute(pos);
            }
        }
        if (dirty) {
            texture.upload();
            dirty = false;
        }
        return true;
    }

    private static void recenter(ChunkPos center) {
        centerX = center.x();
        centerZ = center.z();
        CACHE.keySet().removeIf(key -> Math.abs(ChunkPos.getX(key) - centerX) > radius + 2
                || Math.abs(ChunkPos.getZ(key) - centerZ) > radius + 2);
        List<ChunkPos> list = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                list.add(new ChunkPos(centerX + dx, centerZ + dz));
            }
        }
        list.sort(Comparator.comparingInt(p -> Math.max(Math.abs(p.x() - centerX), Math.abs(p.z() - centerZ))));
        order = list;
        refreshIndex = 0;
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        image.fillRect(0, 0, size(), size(), 0);
        for (ChunkPos pos : order) {
            int[] colors = CACHE.get(pos.pack());
            if (colors != null) {
                blit(pos, colors);
            }
        }
        dirty = true;
    }

    private static LevelChunk loaded(int cx, int cz) {
        ChunkAccess chunk = world.getChunk(cx, cz, ChunkStatus.FULL, false);
        return chunk instanceof LevelChunk worldChunk && !worldChunk.isEmpty() ? worldChunk : null;
    }

    private static boolean compute(ChunkPos pos) {
        LevelChunk chunk = loaded(pos.x(), pos.z());
        if (chunk == null) {
            return false;
        }
        LevelChunk north = loaded(pos.x(), pos.z() - 1);
        int[] colors = new int[256];
        int[] column = new int[3];
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            int previous;
            if (north != null) {
                surface(north, x, 15, mutable, column);
                previous = column[0];
            } else {
                previous = Integer.MIN_VALUE;
            }
            for (int z = 0; z < 16; z++) {
                surface(chunk, x, z, mutable, column);
                int height = column[0];
                int depth = column[1];
                MapColor color = MapColor.byId(column[2]);
                if (previous == Integer.MIN_VALUE) {
                    previous = height;
                }
                MapColor.Brightness brightness;
                int worldX = pos.getMinBlockX() + x;
                int worldZ = pos.getMinBlockZ() + z;
                if (color == MapColor.WATER) {
                    double d = depth * 0.1 + ((worldX + worldZ) & 1) * 0.2;
                    brightness = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                } else {
                    double d = (height - previous) * 0.8 + (((worldX + worldZ) & 1) - 0.5) * 0.4;
                    brightness = d > 0.6 ? MapColor.Brightness.HIGH : d < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                previous = height;
                colors[z * 16 + x] = color == MapColor.NONE ? 0 : color.calculateRGBColor(brightness);
            }
        }
        int[] old = CACHE.put(pos.pack(), colors);
        if (old == null || !Arrays.equals(old, colors)) {
            blit(pos, colors);
            dirty = true;
        }
        return true;
    }

    /** Remplit column = {hauteur, profondeur d'eau, id couleur} comme une carte vanilla. */
    private static void surface(LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos mutable, int[] column) {
        int bottom = world.getMinY();
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
        int startX = chunk.getPos().getMinBlockX() + x;
        int startZ = chunk.getPos().getMinBlockZ() + z;
        BlockState state = null;
        MapColor color = MapColor.NONE;
        if (y > bottom + 1) {
            do {
                y--;
                mutable.set(startX, y, startZ);
                state = chunk.getBlockState(mutable);
                color = state.getMapColor(world, mutable);
            } while (color == MapColor.NONE && y > bottom);
        }
        int depth = 0;
        if (state != null && y > bottom && !state.getFluidState().isEmpty()) {
            int below = y - 1;
            BlockState fluid;
            do {
                mutable.set(startX, below--, startZ);
                fluid = chunk.getBlockState(mutable);
                depth++;
            } while (below > bottom && !fluid.getFluidState().isEmpty());
        }
        column[0] = y;
        column[1] = depth;
        column[2] = color.id;
    }

    private static void blit(ChunkPos pos, int[] colors) {
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        int baseX = (pos.x() - centerX + radius) * 16;
        int baseZ = (pos.z() - centerZ + radius) * 16;
        if (baseX < 0 || baseZ < 0 || baseX + 16 > size() || baseZ + 16 > size()) {
            return;
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                image.setPixelRGBA(baseX + x, baseZ + z, colors[z * 16 + x]);
            }
        }
    }
}
