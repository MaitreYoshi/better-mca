package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import com.maitreyoshi.mcavie.phone.PortraitRegistry;
import com.maitreyoshi.mcavie.tree.server.mixin.FamilyTreeAccessor;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.GraveyardManager;
import net.mca.server.world.data.VillageManager;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Apparence des défunts (NBT du PNJ au moment de sa mort) et choix du défunt sur l'autel du souvenir.
 */
public class Ghosts extends PersistentState {
    public static final Identifier OPEN = MCAVie.id("ghost_altar_open");
    public static final Identifier CHOOSE = MCAVie.id("ghost_altar_choose");
    private static final String DATA_ID = "mca_vie_ghosts";
    private static final String[] STRIPPED = {"Brain", "Inventory", "Offers", "Gossips", "HandItems", "ArmorItems", "HandDropChances",
            "ArmorDropChances", "ActiveEffects", "Passengers", "Leash", "Pos", "Motion", "Rotation", "UUID", "Health", "DeathTime",
            "HurtTime", "HurtByTimestamp", "isPanicking", "Fire", "FallDistance", "Air", "Attributes", "SleepingX", "SleepingY", "SleepingZ"};

    private static final String HOME = "mcavie_home";

    private final Map<UUID, NbtCompound> looks = new HashMap<>();

    public static Ghosts get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(Ghosts::fromNbt, Ghosts::new, DATA_ID);
    }

    private static Ghosts fromNbt(NbtCompound nbt) {
        Ghosts ghosts = new Ghosts();
        NbtCompound all = nbt.getCompound("looks");
        for (String key : all.getKeys()) {
            try {
                ghosts.looks.put(UUID.fromString(key), all.getCompound(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return ghosts;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound all = new NbtCompound();
        looks.forEach((id, look) -> all.put(id.toString(), look));
        nbt.put("looks", all);
        return nbt;
    }

    @Nullable
    public NbtCompound look(UUID person) {
        return looks.get(person);
    }

    private void put(UUID person, NbtCompound look) {
        looks.put(person, look);
        markDirty();
    }

    /** Appelé à la mort d'un PNJ : on garde son apparence de ce moment-là. */
    public static void died(VillagerEntityMCA villager, ServerWorld world) {
        NbtCompound nbt = new NbtCompound();
        if (villager.saveSelfNbt(nbt)) {
            get(world.getServer()).put(villager.getUuid(), strip(nbt));
        }
    }

    private static NbtCompound strip(NbtCompound nbt) {
        NbtCompound copy = nbt.copy();
        // maison = position du lit (mémoire HOME du PNJ), gardée avant d'alléger le NBT
        int[] home = nbt.getCompound("Brain").getCompound("memories").getCompound("minecraft:home").getCompound("value").getIntArray("pos");
        if (home.length == 3) {
            copy.putIntArray(HOME, home);
        }
        for (String key : STRIPPED) {
            copy.remove(key);
        }
        return copy;
    }

    /** Morts avant cette version : leur tombe MCA garde leurs données. */
    private void searchTombs(ServerWorld world, BlockPos around, List<UUID> missing) {
        if (missing.isEmpty()) {
            return;
        }
        List<BlockPos> tombs = GraveyardManager.get(world).findAll(new Box(around).expand(1024, 384, 1024), false, true);
        int checked = 0;
        for (BlockPos pos : tombs) {
            if (missing.isEmpty() || checked++ > 256) {
                return;
            }
            BlockEntity be = world.getBlockEntity(pos);
            if (be == null) {
                continue;
            }
            NbtCompound data = be.createNbt().getCompound("entityData");
            if (data.containsUuid("UUID") && missing.remove(data.getUuid("UUID"))) {
                put(data.getUuid("UUID"), strip(data));
            }
        }
    }

    public static boolean isNight(World world) {
        long time = world.getTimeOfDay() % 24000L;
        return time >= 13000L && time < 23000L;
    }

    public static boolean canReturn(MinecraftServer server, UUID person) {
        return get(server).look(person) != null && PortraitRegistry.get(server).get(person) != null
                && FamilyTree.get(server.getOverworld()).getOrEmpty(person).map(FamilyTreeNode::isDeceased).orElse(false);
    }

    private record Entry(UUID id, String name, boolean look) {
    }

    public static void open(ServerPlayerEntity player, GhostAltarBlockEntity altar) {
        MinecraftServer server = player.getServer();
        Ghosts ghosts = get(server);
        PortraitRegistry portraits = PortraitRegistry.get(server);
        Map<UUID, FamilyTreeNode> nodes = ((FamilyTreeAccessor) FamilyTree.get(server.getOverworld())).mcavie$getEntries();
        List<FamilyTreeNode> dead = nodes.values().stream()
                .filter(n -> n.isDeceased() && !n.isPlayer() && portraits.get(n.id()) != null).toList();
        List<UUID> missing = new ArrayList<>(dead.stream().map(FamilyTreeNode::id).filter(id -> ghosts.look(id) == null).toList());
        ghosts.searchTombs(player.getServerWorld(), altar.getPos(), missing);
        // seulement les défunts dont le lit était dans la maison où l'autel est posé
        Building house = VillageManager.get(player.getServerWorld()).findNearestVillage(altar.getPos(), 0)
                .flatMap(v -> v.getBuildingAt(altar.getPos())).orElse(null);
        List<Entry> entries = new ArrayList<>(dead.stream().filter(n -> livedIn(ghosts.look(n.id()), house))
                .map(n -> new Entry(n.id(), n.getName(), true)).toList());
        entries.sort(Comparator.comparing((Entry e) -> !e.look()).thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(altar.getPos());
        out.writeBoolean(house != null);
        UUID current = altar.person();
        out.writeBoolean(current != null);
        if (current != null) {
            out.writeUuid(current);
        }
        out.writeVarInt(entries.size());
        for (Entry entry : entries) {
            out.writeUuid(entry.id());
            out.writeString(entry.name());
            out.writeBoolean(entry.look());
        }
        ServerPlayNetworking.send(player, OPEN, out);
    }

    private static boolean livedIn(@Nullable NbtCompound look, @Nullable Building house) {
        if (look == null || house == null) {
            return false;
        }
        int[] home = look.getIntArray(HOME);
        return home.length == 3 && house.containsPos(new BlockPos(home[0], home[1], home[2]));
    }

    public static void registerNetwork() {
        ServerPlayNetworking.registerGlobalReceiver(CHOOSE, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            UUID person = buf.readBoolean() ? buf.readUuid() : null;
            server.execute(() -> {
                World world = player.getWorld();
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64 || !(world.getBlockEntity(pos) instanceof GhostAltarBlockEntity altar)) {
                    return;
                }
                if (person != null && !canReturn(server, person)) {
                    return;
                }
                altar.setPerson(person);
                world.updateListeners(pos, altar.getCachedState(), altar.getCachedState(), Block.NOTIFY_ALL);
            });
        });
    }
}
