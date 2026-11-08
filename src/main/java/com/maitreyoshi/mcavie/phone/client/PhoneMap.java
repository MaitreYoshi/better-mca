package com.maitreyoshi.mcavie.phone.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class PhoneMap {
    public static final Identifier TEXTURE = new Identifier("mca_vie", "phone_map");
    private static final int MAX_RADIUS = 12;
    private static final int NEW_PER_FRAME = 24;
    private static final int REFRESH_PER_FRAME = 4;

    private static final Map<Long, int[]> CACHE = new HashMap<>();
    private static ClientWorld world;
    private static NativeImageBackedTexture texture;
    private static int radius = -1;
    private static int centerX = Integer.MIN_VALUE;
    private static int centerZ = Integer.MIN_VALUE;
    private static List<ChunkPos> order = List.of();
    private static int refreshIndex;
    private static boolean dirty;

    private PhoneMap() {
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
    public static boolean update(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return false;
        }
        if (client.world != world) {
            world = client.world;
            CACHE.clear();
            centerX = Integer.MIN_VALUE;
        }
        int wanted = Math.max(2, Math.min(MAX_RADIUS, client.options.getClampedViewDistance()));
        if (wanted != radius || texture == null) {
            radius = wanted;
            if (texture != null) {
                texture.close();
            }
            texture = new NativeImageBackedTexture(new NativeImage(size(), size(), true));
            client.getTextureManager().registerTexture(TEXTURE, texture);
            centerX = Integer.MIN_VALUE;
        }
        ChunkPos center = client.player.getChunkPos();
        if (center.x != centerX || center.z != centerZ) {
            recenter(center);
        }

        int computed = 0;
        for (ChunkPos pos : order) {
            if (computed >= NEW_PER_FRAME) {
                break;
            }
            if (!CACHE.containsKey(pos.toLong()) && compute(pos)) {
                computed++;
            }
        }
        for (int i = 0; i < REFRESH_PER_FRAME && !order.isEmpty(); i++) {
            refreshIndex = (refreshIndex + 1) % order.size();
            ChunkPos pos = order.get(refreshIndex);
            if (CACHE.containsKey(pos.toLong())) {
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
        centerX = center.x;
        centerZ = center.z;
        CACHE.keySet().removeIf(key -> Math.abs(ChunkPos.getPackedX(key) - centerX) > radius + 2
                || Math.abs(ChunkPos.getPackedZ(key) - centerZ) > radius + 2);
        List<ChunkPos> list = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                list.add(new ChunkPos(centerX + dx, centerZ + dz));
            }
        }
        list.sort(Comparator.comparingInt(p -> Math.max(Math.abs(p.x - centerX), Math.abs(p.z - centerZ))));
        order = list;
        refreshIndex = 0;
        NativeImage image = texture.getImage();
        if (image == null) {
            return;
        }
        image.fillRect(0, 0, size(), size(), 0);
        for (ChunkPos pos : order) {
            int[] colors = CACHE.get(pos.toLong());
            if (colors != null) {
                blit(pos, colors);
            }
        }
        dirty = true;
    }

    private static WorldChunk loaded(int cx, int cz) {
        Chunk chunk = world.getChunk(cx, cz, ChunkStatus.FULL, false);
        return chunk instanceof WorldChunk worldChunk && !worldChunk.isEmpty() ? worldChunk : null;
    }

    private static boolean compute(ChunkPos pos) {
        WorldChunk chunk = loaded(pos.x, pos.z);
        if (chunk == null) {
            return false;
        }
        WorldChunk north = loaded(pos.x, pos.z - 1);
        int[] colors = new int[256];
        int[] column = new int[3];
        BlockPos.Mutable mutable = new BlockPos.Mutable();
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
                MapColor color = MapColor.get(column[2]);
                if (previous == Integer.MIN_VALUE) {
                    previous = height;
                }
                MapColor.Brightness brightness;
                int worldX = pos.getStartX() + x;
                int worldZ = pos.getStartZ() + z;
                if (color == MapColor.WATER_BLUE) {
                    double d = depth * 0.1 + ((worldX + worldZ) & 1) * 0.2;
                    brightness = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                } else {
                    double d = (height - previous) * 0.8 + (((worldX + worldZ) & 1) - 0.5) * 0.4;
                    brightness = d > 0.6 ? MapColor.Brightness.HIGH : d < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                previous = height;
                colors[z * 16 + x] = color == MapColor.CLEAR ? 0 : color.getRenderColor(brightness);
            }
        }
        int[] old = CACHE.put(pos.toLong(), colors);
        if (old == null || !Arrays.equals(old, colors)) {
            blit(pos, colors);
            dirty = true;
        }
        return true;
    }

    /** Remplit column = {hauteur, profondeur d'eau, id couleur} comme une carte vanilla. */
    private static void surface(WorldChunk chunk, int x, int z, BlockPos.Mutable mutable, int[] column) {
        int bottom = world.getBottomY();
        int y = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z) + 1;
        int startX = chunk.getPos().getStartX() + x;
        int startZ = chunk.getPos().getStartZ() + z;
        BlockState state = null;
        MapColor color = MapColor.CLEAR;
        if (y > bottom + 1) {
            do {
                y--;
                mutable.set(startX, y, startZ);
                state = chunk.getBlockState(mutable);
                color = state.getMapColor(world, mutable);
            } while (color == MapColor.CLEAR && y > bottom);
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
        NativeImage image = texture.getImage();
        if (image == null) {
            return;
        }
        int baseX = (pos.x - centerX + radius) * 16;
        int baseZ = (pos.z - centerZ + radius) * 16;
        if (baseX < 0 || baseZ < 0 || baseX + 16 > size() || baseZ + 16 > size()) {
            return;
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                image.setColor(baseX + x, baseZ + z, colors[z * 16 + x]);
            }
        }
    }
}
