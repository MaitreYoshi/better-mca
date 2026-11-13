package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.compat.Saved;
import com.maitreyoshi.mcavie.compat.Nbt;
import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import com.maitreyoshi.mcavie.phone.PortraitRegistry;
import com.maitreyoshi.mcavie.tree.server.mixin.FamilyTreeAccessor;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
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
public class Ghosts extends SavedData {
    public static final Identifier OPEN = MCAVie.id("ghost_altar_open");
    public static final Identifier CHOOSE = MCAVie.id("ghost_altar_choose");
    private static final String DATA_ID = "mca_vie_ghosts";
    private static final String[] STRIPPED = {"Brain", "Inventory", "Offers", "Gossips", "HandItems", "ArmorItems", "HandDropChances",
            "ArmorDropChances", "ActiveEffects", "Passengers", "Leash", "Pos", "Motion", "Rotation", "UUID", "Health", "DeathTime",
            "HurtTime", "HurtByTimestamp", "isPanicking", "Fire", "FallDistance", "Air", "Attributes", "SleepingX", "SleepingY", "SleepingZ"};

    private static final String HOME = "mcavie_home";

    private final Map<UUID, CompoundTag> looks = new HashMap<>();

    public static Ghosts get(MinecraftServer server) {
        return Saved.get(server.overworld(), Ghosts::fromNbt, Ghosts::new, Ghosts::save, DATA_ID);
    }

    private static Ghosts fromNbt(CompoundTag nbt) {
        Ghosts ghosts = new Ghosts();
        CompoundTag all = nbt.getCompoundOrEmpty("looks");
        for (String key : all.keySet()) {
            try {
                ghosts.looks.put(UUID.fromString(key), all.getCompoundOrEmpty(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return ghosts;
    }

        public CompoundTag save(CompoundTag nbt) {
        CompoundTag all = new CompoundTag();
        looks.forEach((id, look) -> all.put(id.toString(), look));
        nbt.put("looks", all);
        return nbt;
    }

    @Nullable
    public CompoundTag look(UUID person) {
        return looks.get(person);
    }

    private void put(UUID person, CompoundTag look) {
        looks.put(person, look);
        setDirty();
    }

    /** Appelé à la mort d'un PNJ : on garde son apparence de ce moment-là. */
    public static void died(VillagerEntityMCA villager, ServerLevel world) {
        CompoundTag nbt = Nbt.saveEntity(villager);
        if (nbt == null) {
            debug("apparence non enregistrée à la mort (sauvegarde du PNJ vide)");
        }
        if (nbt != null) {
            get(world.getServer()).put(villager.getUUID(), strip(nbt));
        }
    }

    private static CompoundTag strip(CompoundTag nbt) {
        CompoundTag copy = nbt.copy();
        // maison = position du lit (mémoire HOME du PNJ), gardée avant d'alléger le NBT
        int[] home = nbt.getCompoundOrEmpty("Brain").getCompoundOrEmpty("memories").getCompoundOrEmpty("minecraft:home").getCompoundOrEmpty("value").getIntArray("pos").orElse(new int[0]);
        if (home.length == 3) {
            copy.putIntArray(HOME, home);
        }
        for (String key : STRIPPED) {
            copy.remove(key);
        }
        return copy;
    }

    /** Morts avant cette version : leur tombe MCA garde leurs données. */
    private void searchTombs(ServerLevel world, BlockPos around, List<UUID> missing) {
        if (missing.isEmpty()) {
            return;
        }
        List<BlockPos> tombs = GraveyardManager.get(world).findAll(new AABB(around).inflate(1024, 384, 1024), false, true);
        int checked = 0;
        for (BlockPos pos : tombs) {
            if (missing.isEmpty() || checked++ > 256) {
                return;
            }
            BlockEntity be = world.getBlockEntity(pos);
            if (be == null) {
                continue;
            }
            CompoundTag data = be.saveWithoutMetadata(world.registryAccess()).getCompoundOrEmpty("entityData");
            if (Nbt.hasUUID(data, "UUID") && missing.remove(Nbt.getUUID(data, "UUID"))) {
                put(Nbt.getUUID(data, "UUID"), strip(data));
            }
        }
    }

    private static final java.util.Set<String> DEBUGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void debug(String reason) {
        if (DEBUGGED.add(reason)) {
            com.maitreyoshi.mcavie.MCAVie.LOGGER.info("Fantôme : {}", reason);
        }
    }

    public static boolean isNight(Level world) {
        long time = world.getOverworldClockTime() % 24000L;
        return time >= 13000L && time < 23000L;
    }

    public static boolean canReturn(MinecraftServer server, UUID person) {
        return get(server).look(person) != null && PortraitRegistry.get(server).get(person) != null
                && FamilyTree.get(server.overworld()).getOrEmpty(person).map(FamilyTreeNode::isDeceased).orElse(false);
    }

    private record Entry(UUID id, String name, boolean look) {
    }

    public static void open(ServerPlayer player, GhostAltarBlockEntity altar) {
        MinecraftServer server = player.level().getServer();
        Ghosts ghosts = get(server);
        PortraitRegistry portraits = PortraitRegistry.get(server);
        Map<UUID, FamilyTreeNode> nodes = ((FamilyTreeAccessor) FamilyTree.get(server.overworld())).mcavie$getEntries();
        List<FamilyTreeNode> dead = nodes.values().stream()
                .filter(n -> n.isDeceased() && !n.isPlayer() && portraits.get(n.id()) != null).toList();
        List<UUID> missing = new ArrayList<>(dead.stream().map(FamilyTreeNode::id).filter(id -> ghosts.look(id) == null).toList());
        ghosts.searchTombs(player.level(), altar.getBlockPos(), missing);
        // seulement les défunts dont le lit était dans la maison où l'autel est posé
        Building house = VillageManager.get(player.level()).findNearestVillage(altar.getBlockPos(), 0)
                .flatMap(v -> v.getBuildingAt(altar.getBlockPos())).orElse(null);
        List<Entry> entries = new ArrayList<>(dead.stream().filter(n -> livedIn(ghosts.look(n.id()), house))
                .map(n -> new Entry(n.id(), n.getName(), true)).toList());
        entries.sort(Comparator.comparing((Entry e) -> !e.look()).thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
        FriendlyByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(altar.getBlockPos());
        out.writeBoolean(house != null);
        UUID current = altar.person();
        out.writeBoolean(current != null);
        if (current != null) {
            out.writeUUID(current);
        }
        out.writeVarInt(entries.size());
        for (Entry entry : entries) {
            out.writeUUID(entry.id());
            out.writeUtf(entry.name());
            out.writeBoolean(entry.look());
        }
        ServerPlayNetworking.send(player, OPEN, out);
    }

    private static boolean livedIn(@Nullable CompoundTag look, @Nullable Building house) {
        if (look == null || house == null) {
            return false;
        }
        int[] home = look.getIntArray(HOME).orElse(new int[0]);
        return home.length == 3 && house.containsPos(new BlockPos(home[0], home[1], home[2]));
    }

    public static void registerNetwork() {
        ServerPlayNetworking.registerGlobalReceiver(CHOOSE, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            UUID person = buf.readBoolean() ? buf.readUUID() : null;
            server.execute(() -> {
                Level world = player.level();
                if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 64 || !(world.getBlockEntity(pos) instanceof GhostAltarBlockEntity altar)) {
                    return;
                }
                if (person != null && !canReturn(server, person)) {
                    return;
                }
                altar.setPerson(person);
                world.sendBlockUpdated(pos, altar.getBlockState(), altar.getBlockState(), Block.UPDATE_ALL);
            });
        });
    }
}
