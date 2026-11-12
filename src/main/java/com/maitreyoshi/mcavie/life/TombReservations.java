package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.InteractionEvent;
import net.mca.TagsMCA;
import net.mca.block.TombstoneBlock;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.GraveyardManager;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TombReservations {
    public static final Identifier OPEN = MCAVie.id("tomb_reservation_open");
    public static final Identifier CHOOSE = MCAVie.id("tomb_reservation_choose");
    private static final int VILLAGE_MARGIN = 32;
    private static final double MAX_REACH = 64;

    private TombReservations() {
    }

    public static void register() {
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (player.getWorld().isClient || !(player instanceof ServerPlayerEntity serverPlayer) || hand != Hand.MAIN_HAND) {
                return EventResult.pass();
            }
            ServerWorld world = serverPlayer.getServerWorld();
            Optional<TombstoneBlock.Data> tomb = emptyTomb(world, pos);
            if (tomb.isEmpty()) {
                return EventResult.pass();
            }
            if (player.isSneaking() && player.getMainHandStack().isEmpty()) {
                open(serverPlayer, world, pos.toImmutable());
                return EventResult.interruptTrue();
            }
            Claim claim = Registry.get(world.getServer()).at(GlobalPos.create(world.getRegistryKey(), pos));
            if (claim != null) {
                player.sendMessage(Text.translatable("tomb.mca_vie.reserved_for", claim.name()).formatted(Formatting.GOLD), true);
            }
            return EventResult.pass();
        });
        ServerPlayNetworking.registerGlobalReceiver(CHOOSE, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            UUID villager = buf.readBoolean() ? buf.readUuid() : null;
            server.execute(() -> choose(player, pos, villager));
        });
    }

    private static Optional<TombstoneBlock.Data> emptyTomb(ServerWorld world, BlockPos pos) {
        if (!world.getBlockState(pos).isIn(TagsMCA.Blocks.TOMBSTONES)) {
            return Optional.empty();
        }
        return TombstoneBlock.Data.of(world.getBlockEntity(pos)).filter(data -> !data.hasEntity());
    }

    private record Candidate(UUID id, String name) {
    }

    private static List<Candidate> candidates(ServerWorld world, Village village) {
        FamilyTree tree = FamilyTree.get(world);
        List<Candidate> list = new ArrayList<>();
        village.getResidentsUUIDs().forEach(id -> tree.getOrEmpty(id)
                .filter(node -> !node.isDeceased())
                .map(FamilyTreeNode::getName)
                .filter(name -> !name.isBlank())
                .ifPresent(name -> list.add(new Candidate(id, name))));
        list.sort(Comparator.comparing(Candidate::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private static Optional<Village> village(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
        VillageManager manager = VillageManager.get(world);
        return manager.findNearestVillage(pos, VILLAGE_MARGIN).or(() -> manager.findNearestVillage(player));
    }

    private static void open(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        Optional<Village> village = village(world, pos, player);
        if (village.isEmpty()) {
            player.sendMessage(Text.translatable("tomb.mca_vie.no_village").formatted(Formatting.RED), true);
            return;
        }
        Registry registry = Registry.get(world.getServer());
        Claim current = registry.at(GlobalPos.create(world.getRegistryKey(), pos));
        List<Candidate> candidates = candidates(world, village.get());
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(pos);
        out.writeString(village.get().getName());
        out.writeBoolean(current != null);
        if (current != null) {
            out.writeUuid(current.villager());
        }
        out.writeVarInt(candidates.size());
        for (Candidate candidate : candidates) {
            out.writeUuid(candidate.id());
            out.writeString(candidate.name());
            out.writeBoolean(registry.hasTomb(candidate.id()));
        }
        ServerPlayNetworking.send(player, OPEN, out);
    }

    private static void choose(ServerPlayerEntity player, BlockPos pos, UUID villager) {
        ServerWorld world = player.getServerWorld();
        if (player.squaredDistanceTo(pos.toCenterPos()) > MAX_REACH * MAX_REACH || emptyTomb(world, pos).isEmpty()) {
            return;
        }
        GlobalPos target = GlobalPos.create(world.getRegistryKey(), pos);
        Registry registry = Registry.get(world.getServer());
        if (villager == null) {
            registry.releaseAt(target);
            player.sendMessage(Text.translatable("tomb.mca_vie.cleared").formatted(Formatting.YELLOW), true);
            return;
        }
        Optional<Candidate> candidate = village(world, pos, player)
                .flatMap(village -> candidates(world, village).stream().filter(c -> c.id().equals(villager)).findFirst());
        if (candidate.isEmpty()) {
            return;
        }
        registry.claim(target, villager, candidate.get().name());
        player.sendMessage(Text.translatable("tomb.mca_vie.reserved", candidate.get().name()).formatted(Formatting.GREEN), true);
    }

    public static Optional<BlockPos> findTomb(ServerWorld world, Entity dead, BlockPos deathPos, int chunkRange) {
        Registry registry = Registry.get(world.getServer());
        GlobalPos own = registry.of(dead.getUuid());
        if (own != null) {
            registry.release(dead.getUuid());
            if (own.getDimension() == world.getRegistryKey() && emptyTomb(world, own.getPos()).isPresent()) {
                return Optional.of(own.getPos());
            }
        }
        int range = chunkRange * 16 + 16;
        Box box = new Box(deathPos).expand(range, world.getHeight(), range);
        return GraveyardManager.get(world).findAll(box, true, false).stream()
                .filter(pos -> registry.at(GlobalPos.create(world.getRegistryKey(), pos)) == null)
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(deathPos)));
    }

    public static void tombRemoved(ServerWorld world, BlockPos pos) {
        Registry.get(world.getServer()).releaseAt(GlobalPos.create(world.getRegistryKey(), pos));
    }

    private record Claim(UUID villager, String name) {
    }

    private static final class Registry extends PersistentState {
        private static final String DATA_ID = "mca_vie_tombs";
        private final Map<GlobalPos, Claim> claims = new HashMap<>();

        static Registry get(MinecraftServer server) {
            return server.getOverworld().getPersistentStateManager().getOrCreate(Registry::fromNbt, Registry::new, DATA_ID);
        }

        Claim at(GlobalPos tomb) {
            return claims.get(tomb);
        }

        GlobalPos of(UUID villager) {
            return claims.entrySet().stream().filter(e -> e.getValue().villager().equals(villager)).map(Map.Entry::getKey).findFirst().orElse(null);
        }

        boolean hasTomb(UUID villager) {
            return of(villager) != null;
        }

        void claim(GlobalPos tomb, UUID villager, String name) {
            claims.values().removeIf(c -> c.villager().equals(villager));
            claims.put(tomb, new Claim(villager, name));
            markDirty();
        }

        void release(UUID villager) {
            if (claims.values().removeIf(c -> c.villager().equals(villager))) {
                markDirty();
            }
        }

        void releaseAt(GlobalPos tomb) {
            if (claims.remove(tomb) != null) {
                markDirty();
            }
        }

        private static Registry fromNbt(NbtCompound nbt) {
            Registry registry = new Registry();
            NbtList list = nbt.getList("tombs", 10);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, new Identifier(c.getString("dim")));
                registry.claims.put(GlobalPos.create(dimension, BlockPos.fromLong(c.getLong("pos"))), new Claim(c.getUuid("villager"), c.getString("name")));
            }
            return registry;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();
            claims.forEach((tomb, claim) -> {
                NbtCompound c = new NbtCompound();
                c.putString("dim", tomb.getDimension().getValue().toString());
                c.putLong("pos", tomb.getPos().asLong());
                c.putUuid("villager", claim.villager());
                c.putString("name", claim.name());
                list.add(c);
            });
            nbt.put("tombs", list);
            return nbt;
        }
    }
}
