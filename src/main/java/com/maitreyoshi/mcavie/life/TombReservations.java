package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.compat.Saved;
import com.maitreyoshi.mcavie.compat.Nbt;
import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.InteractionEvent;
import net.conczin.mca.registry.TagsMCA;
import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
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
    private static final int FALLBACK_RANGE = 256;

    private TombReservations() {
    }

    public static void register() {
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer) || hand != InteractionHand.MAIN_HAND) {
                return EventResult.pass();
            }
            ServerLevel world = serverPlayer.level();
            if (world.getBlockState(pos).getBlock() instanceof VaultBlock) {
                return EventResult.pass();
            }
            Optional<TombstoneBlock.Data> tomb = emptyTomb(world, pos);
            if (tomb.isEmpty()) {
                return EventResult.pass();
            }
            if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
                open(serverPlayer, world, pos.immutable());
                return EventResult.interruptTrue();
            }
            Claim claim = Registry.get(world.getServer()).at(GlobalPos.of(world.dimension(), pos));
            if (claim != null) {
                player.sendOverlayMessage(Component.translatable("tomb.mca_vie.reserved_for", claim.name()).withStyle(ChatFormatting.GOLD));
            }
            return EventResult.pass();
        });
        ServerPlayNetworking.registerGlobalReceiver(CHOOSE, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            UUID villager = buf.readBoolean() ? buf.readUUID() : null;
            server.execute(() -> choose(player, pos, villager));
        });
    }

    public static void openAt(ServerPlayer player, ServerLevel world, BlockPos pos) {
        if (emptyTomb(world, pos).isPresent()) {
            open(player, world, pos.immutable());
        }
    }

    public static void announce(net.minecraft.world.entity.player.Player player, ServerLevel world, BlockPos pos) {
        if (emptyTomb(world, pos).isEmpty()) {
            return;
        }
        Claim claim = Registry.get(world.getServer()).at(GlobalPos.of(world.dimension(), pos));
        if (claim != null) {
            player.sendOverlayMessage(Component.translatable("tomb.mca_vie.reserved_for", claim.name()).withStyle(ChatFormatting.GOLD));
        }
    }

    private static Optional<TombstoneBlock.Data> emptyTomb(ServerLevel world, BlockPos pos) {
        if (!world.getBlockState(pos).is(TagsMCA.Blocks.TOMBSTONES)) {
            return Optional.empty();
        }
        return TombstoneBlock.Data.of(world.getBlockEntity(pos)).filter(data -> !data.hasEntity());
    }

    private record Candidate(UUID id, String name) {
    }

    private static List<Candidate> candidates(ServerLevel world, Village village) {
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

    static Optional<Village> village(ServerLevel world, BlockPos pos, ServerPlayer player) {
        VillageManager manager = VillageManager.get(world);
        return manager.findNearestVillage(pos, VILLAGE_MARGIN).or(() -> manager.findNearestVillage(player))
                .or(() -> manager.findVillages(v -> v.getCenter().distSqr(pos) <= FALLBACK_RANGE * FALLBACK_RANGE)
                        .min(Comparator.comparingDouble(v -> v.getCenter().distSqr(pos))));
    }

    private static void open(ServerPlayer player, ServerLevel world, BlockPos pos) {
        Optional<Village> village = village(world, pos, player);
        if (village.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("tomb.mca_vie.no_village").withStyle(ChatFormatting.RED));
            return;
        }
        Registry registry = Registry.get(world.getServer());
        Claim current = registry.at(GlobalPos.of(world.dimension(), pos));
        List<Candidate> candidates = candidates(world, village.get());
        FriendlyByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(pos);
        out.writeUtf(village.get().getName());
        out.writeBoolean(current != null);
        if (current != null) {
            out.writeUUID(current.villager());
        }
        out.writeVarInt(candidates.size());
        for (Candidate candidate : candidates) {
            out.writeUUID(candidate.id());
            out.writeUtf(candidate.name());
            out.writeBoolean(registry.hasTomb(candidate.id()));
        }
        ServerPlayNetworking.send(player, OPEN, out);
    }

    private static void choose(ServerPlayer player, BlockPos pos, UUID villager) {
        ServerLevel world = player.level();
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > MAX_REACH * MAX_REACH || emptyTomb(world, pos).isEmpty()) {
            return;
        }
        GlobalPos target = GlobalPos.of(world.dimension(), pos);
        Registry registry = Registry.get(world.getServer());
        if (villager == null) {
            registry.releaseAt(target);
            player.sendOverlayMessage(Component.translatable("tomb.mca_vie.cleared").withStyle(ChatFormatting.YELLOW));
            return;
        }
        Optional<Candidate> candidate = village(world, pos, player)
                .flatMap(village -> candidates(world, village).stream().filter(c -> c.id().equals(villager)).findFirst());
        if (candidate.isEmpty()) {
            return;
        }
        registry.claim(target, villager, candidate.get().name());
        player.sendOverlayMessage(Component.translatable("tomb.mca_vie.reserved", candidate.get().name()).withStyle(ChatFormatting.GREEN));
    }

    public static Optional<BlockPos> findTomb(ServerLevel world, Entity dead, BlockPos deathPos, int chunkRange) {
        Registry registry = Registry.get(world.getServer());
        GlobalPos own = registry.of(dead.getUUID());
        if (own != null) {
            registry.release(dead.getUUID());
            if (own.dimension() == world.dimension() && emptyTomb(world, own.pos()).isPresent()) {
                return Optional.of(own.pos());
            }
        }
        int range = chunkRange * 16 + 16;
        AABB box = new AABB(deathPos).inflate(range, world.getHeight(), range);
        return GraveyardManager.get(world).findAll(box, true, false).stream()
                .filter(pos -> registry.at(GlobalPos.of(world.dimension(), pos)) == null)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(deathPos)));
    }

    public static void tombRemoved(ServerLevel world, BlockPos pos) {
        Registry.get(world.getServer()).releaseAt(GlobalPos.of(world.dimension(), pos));
    }

    private record Claim(UUID villager, String name) {
    }

    private static final class Registry extends SavedData {
        private static final String DATA_ID = "mca_vie_tombs";
        private final Map<GlobalPos, Claim> claims = new HashMap<>();

        static Registry get(MinecraftServer server) {
            return Saved.get(server.overworld(), Registry::fromNbt, Registry::new, Registry::save, DATA_ID);
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
            setDirty();
        }

        void release(UUID villager) {
            if (claims.values().removeIf(c -> c.villager().equals(villager))) {
                setDirty();
            }
        }

        void releaseAt(GlobalPos tomb) {
            if (claims.remove(tomb) != null) {
                setDirty();
            }
        }

        private static Registry fromNbt(CompoundTag nbt) {
            Registry registry = new Registry();
            ListTag list = nbt.getListOrEmpty("tombs");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompoundOrEmpty(i);
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(c.getStringOr("dim", "")));
                registry.claims.put(GlobalPos.of(dimension, BlockPos.of(c.getLongOr("pos", 0L))), new Claim(Nbt.getUUID(c, "villager"), c.getStringOr("name", "")));
            }
            return registry;
        }

                public CompoundTag save(CompoundTag nbt) {
            ListTag list = new ListTag();
            claims.forEach((tomb, claim) -> {
                CompoundTag c = new CompoundTag();
                c.putString("dim", tomb.dimension().identifier().toString());
                c.putLong("pos", tomb.pos().asLong());
                Nbt.putUUID(c, "villager", claim.villager());
                c.putString("name", claim.name());
                list.add(c);
            });
            nbt.put("tombs", list);
            return nbt;
        }
    }
}
