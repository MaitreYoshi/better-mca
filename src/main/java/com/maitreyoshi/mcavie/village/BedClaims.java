package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.compat.ServerTickEvents;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.InteractionEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lits attribués. Le bouton MCA « Attribuer un lit » (sethome) ne choisit plus de lit au hasard autour du joueur :
 * il demande au joueur de cliquer sur le lit voulu (comme le poste de travail). Le lit appartient alors au PNJ
 * jusqu'à ce qu'il soit cassé (ou que le PNJ meure) : sa réservation est maintenue, aucun autre PNJ ne le garde
 * en mémoire et aucun joueur ne peut y dormir. Un PNJ ne possède qu'un lit à la fois.
 */
public final class BedClaims {
    private static final int CHECK_INTERVAL = 100;
    private static final int PICK_TICKS = 600;
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(UUID villager, long expiresAt) {
    }

    private BedClaims() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(BedClaims::tick);
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (player.getWorld().isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return EventResult.pass();
            }
            return onBedClick(serverPlayer, pos);
        });
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof VillagerEntityMCA villager && entity.getServer() != null) {
                Registry.get(entity.getServer()).release(villager.getUuid());
            }
            return EventResult.pass();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> PENDING.remove(handler.player.getUuid())));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    /** Remplace Residency.setHome de MCA : attend le clic sur le lit. */
    public static void setHome(VillagerEntityMCA villager, ServerPlayerEntity player) {
        if (!villager.requiresHome()) {
            villager.sendChatMessage(player, "interaction.sethome.temporary");
            return;
        }
        PENDING.put(player.getUuid(), new Pending(villager.getUuid(), player.getWorld().getTime() + PICK_TICKS));
        player.sendMessage(Text.translatable("bed.mca_vie.pick", villager.getName()).formatted(Formatting.YELLOW), false);
    }

    private static EventResult onBedClick(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = player.getServerWorld();
        BlockState state = world.getBlockState(pos);
        boolean bed = state.getBlock() instanceof BedBlock;
        Pending pending = PENDING.remove(player.getUuid());
        if (pending != null && bed && world.getTime() <= pending.expiresAt()) {
            if (world.getEntity(pending.villager()) instanceof VillagerEntityMCA villager) {
                assign(world, villager, player, head(pos, state));
            }
            return EventResult.interruptTrue();
        }
        if (bed && !player.isSneaking()) {
            Claim claim = Registry.get(world.getServer()).at(GlobalPos.create(world.getRegistryKey(), head(pos, state)));
            if (claim != null) {
                player.sendMessage(Text.translatable("bed.mca_vie.owned", claim.name()).formatted(Formatting.GOLD), true);
                return EventResult.interruptTrue();
            }
        }
        return EventResult.pass();
    }

    private static void assign(ServerWorld world, VillagerEntityMCA villager, ServerPlayerEntity player, BlockPos head) {
        GlobalPos target = GlobalPos.create(world.getRegistryKey(), head);
        Registry registry = Registry.get(world.getServer());
        Claim claim = registry.at(target);
        if (claim != null && !claim.villager().equals(villager.getUuid())) {
            player.sendMessage(Text.translatable("bed.mca_vie.taken", claim.name()).formatted(Formatting.RED), false);
            return;
        }
        if (!isBed(world, head)) {
            villager.sendChatMessage(player, "interaction.sethome.bedfail");
            return;
        }
        // ancien lit rendu, lit visé repris aux PNJ qui l'avaient en mémoire (leur réservation passe au nouveau propriétaire)
        GlobalPos current = villager.getBrain().getOptionalMemory(MemoryModuleType.HOME).orElse(null);
        if (current != null && !current.equals(target)) {
            villager.releaseTicketFor(MemoryModuleType.HOME);
            villager.getBrain().forget(MemoryModuleType.HOME);
        }
        List<VillagerEntityMCA> others = rememberers(world, target, villager);
        others.forEach(BedClaims::evict);
        if (others.isEmpty() && !target.equals(current) && hasSpace(world.getPointOfInterestStorage(), head)) {
            reserve(world, head);
        }
        villager.getBrain().remember(MemoryModuleType.HOME, target);
        villager.getBrain().remember(MemoryModuleTypeMCA.FORCED_HOME.get(), true);
        registry.claim(target, villager.getUuid(), villager.getName().getString());
        villager.getResidency().seekHome();
        villager.sendChatMessage(player, "interaction.sethome.success");
    }

    /** Lits cassés oubliés, propriétaires rétablis, un lit = un PNJ, réservations maintenues. */
    private static void tick(ServerWorld world) {
        if (world.getTime() % CHECK_INTERVAL != 0) {
            return;
        }
        Registry registry = Registry.get(world.getServer());
        PointOfInterestStorage poi = world.getPointOfInterestStorage();
        Map<UUID, VillagerEntityMCA> loaded = new HashMap<>();
        Map<GlobalPos, List<VillagerEntityMCA>> byBed = new HashMap<>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof VillagerEntityMCA villager && villager.isAlive()) {
                loaded.put(villager.getUuid(), villager);
                villager.getBrain().getOptionalMemory(MemoryModuleType.HOME)
                        .filter(h -> h.getDimension() == world.getRegistryKey())
                        .ifPresent(h -> byBed.computeIfAbsent(h, k -> new ArrayList<>()).add(villager));
            }
        }

        for (Map.Entry<GlobalPos, Claim> entry : new ArrayList<>(registry.claims.entrySet())) {
            GlobalPos bed = entry.getKey();
            if (bed.getDimension() != world.getRegistryKey() || !world.isChunkLoaded(bed.getPos())) {
                continue;
            }
            VillagerEntityMCA owner = loaded.get(entry.getValue().villager());
            if (!isBed(world, bed.getPos())) {
                registry.release(entry.getValue().villager());
                if (owner != null && owner.getBrain().getOptionalMemory(MemoryModuleType.HOME).filter(bed::equals).isPresent()) {
                    evict(owner);
                }
                continue;
            }
            if (owner != null && owner.getBrain().getOptionalMemory(MemoryModuleType.HOME).filter(bed::equals).isEmpty()) {
                // MCA lui a fait oublier son lit : on le lui rend
                owner.getBrain().remember(MemoryModuleType.HOME, bed);
                owner.getBrain().remember(MemoryModuleTypeMCA.FORCED_HOME.get(), true);
                byBed.computeIfAbsent(bed, k -> new ArrayList<>()).add(owner);
            }
            List<VillagerEntityMCA> rememberers = byBed.getOrDefault(bed, List.of());
            rememberers.stream().filter(v -> !v.getUuid().equals(entry.getValue().villager())).forEach(BedClaims::evict);
            byBed.remove(bed);
            if (hasSpace(poi, bed.getPos())) {
                reserve(world, bed.getPos());
            }
        }

        byBed.forEach((bed, villagers) -> {
            if (villagers.size() > 1) {
                VillagerEntityMCA keeper = villagers.stream().filter(BedClaims::forced).findFirst().orElse(villagers.get(0));
                villagers.stream().filter(v -> v != keeper).forEach(BedClaims::evict);
            }
            BlockPos pos = bed.getPos();
            if (world.isChunkLoaded(pos) && hasSpace(poi, pos)) {
                reserve(world, pos);
            }
        });
    }

    private static boolean forced(VillagerEntityMCA villager) {
        Optional<Boolean> forced = villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.FORCED_HOME.get());
        //noinspection OptionalAssignedToNull
        return forced != null && forced.isPresent();
    }

    /** Oublie le lit sans rendre sa réservation (elle appartient au PNJ qui le garde). */
    private static void evict(VillagerEntityMCA villager) {
        villager.getBrain().forget(MemoryModuleType.HOME);
        villager.getBrain().forget(MemoryModuleTypeMCA.FORCED_HOME.get());
    }

    private static List<VillagerEntityMCA> rememberers(ServerWorld world, GlobalPos bed, VillagerEntityMCA except) {
        List<VillagerEntityMCA> list = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof VillagerEntityMCA other && other != except
                    && other.getBrain().getOptionalMemory(MemoryModuleType.HOME).filter(bed::equals).isPresent()) {
                list.add(other);
            }
        }
        return list;
    }

    private static BlockPos head(BlockPos pos, BlockState state) {
        return state.get(BedBlock.PART) == BedPart.HEAD ? pos.toImmutable() : pos.offset(state.get(BedBlock.FACING)).toImmutable();
    }

    private static boolean isBed(ServerWorld world, BlockPos head) {
        BlockState state = world.getBlockState(head);
        return state.getBlock() instanceof BedBlock && state.get(BedBlock.PART) == BedPart.HEAD;
    }

    private static boolean hasSpace(PointOfInterestStorage poi, BlockPos pos) {
        return poi.getPositions(e -> e.matchesKey(PointOfInterestTypes.HOME), pos::equals, pos, 1,
                PointOfInterestStorage.OccupationStatus.HAS_SPACE).findAny().isPresent();
    }

    private static void reserve(ServerWorld world, BlockPos pos) {
        world.getPointOfInterestStorage().getPosition(e -> e.matchesKey(PointOfInterestTypes.HOME), (e, p) -> p.equals(pos), pos, 1);
    }

    private record Claim(UUID villager, String name) {
    }

    /** Lits attribués, sauvegardés avec le monde. */
    private static final class Registry extends PersistentState {
        private static final String DATA_ID = "mca_vie_beds";
        private final Map<GlobalPos, Claim> claims = new HashMap<>();

        static Registry get(MinecraftServer server) {
            return server.getOverworld().getPersistentStateManager().getOrCreate(Registry::fromNbt, Registry::new, DATA_ID);
        }

        Claim at(GlobalPos bed) {
            return claims.get(bed);
        }

        void claim(GlobalPos bed, UUID villager, String name) {
            claims.values().removeIf(c -> c.villager().equals(villager));
            claims.put(bed, new Claim(villager, name));
            markDirty();
        }

        void release(UUID villager) {
            if (claims.values().removeIf(c -> c.villager().equals(villager))) {
                markDirty();
            }
        }

        private static Registry fromNbt(NbtCompound nbt) {
            Registry registry = new Registry();
            NbtList list = nbt.getList("beds", 10);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, new Identifier(c.getString("dim")));
                BlockPos pos = BlockPos.fromLong(c.getLong("pos"));
                registry.claims.put(GlobalPos.create(dimension, pos), new Claim(c.getUuid("villager"), c.getString("name")));
            }
            return registry;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();
            claims.forEach((bed, claim) -> {
                NbtCompound c = new NbtCompound();
                c.putString("dim", bed.getDimension().getValue().toString());
                c.putLong("pos", bed.getPos().asLong());
                c.putUuid("villager", claim.villager());
                c.putString("name", claim.name());
                list.add(c);
            });
            nbt.put("beds", list);
            return nbt;
        }
    }
}
