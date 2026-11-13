package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.compat.ServerTickEvents;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.InteractionEvent;
import com.maitreyoshi.mcavie.compat.ServerLifecycleEvents;
import com.maitreyoshi.mcavie.compat.ServerPlayConnectionEvents;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.saveddata.SavedData;
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
            if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
                return EventResult.pass();
            }
            return onBedClick(serverPlayer, pos);
        });
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof VillagerEntityMCA villager && entity.getServer() != null) {
                Registry.get(entity.getServer()).release(villager.getUUID());
            }
            return EventResult.pass();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> PENDING.remove(handler.player.getUUID())));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    /** Remplace Residency.setHome de MCA : attend le clic sur le lit. */
    public static void setHome(VillagerEntityMCA villager, ServerPlayer player) {
        if (!villager.requiresHome()) {
            villager.sendChatMessage(player, "interaction.sethome.temporary");
            return;
        }
        PENDING.put(player.getUUID(), new Pending(villager.getUUID(), player.level().getGameTime() + PICK_TICKS));
        player.displayClientMessage(Component.translatable("bed.mca_vie.pick", villager.getName()).withStyle(ChatFormatting.YELLOW), false);
    }

    private static EventResult onBedClick(ServerPlayer player, BlockPos pos) {
        ServerLevel world = player.serverLevel();
        BlockState state = world.getBlockState(pos);
        boolean bed = state.getBlock() instanceof BedBlock;
        Pending pending = PENDING.remove(player.getUUID());
        if (pending != null && bed && world.getGameTime() <= pending.expiresAt()) {
            if (world.getEntity(pending.villager()) instanceof VillagerEntityMCA villager) {
                assign(world, villager, player, head(pos, state));
            }
            return EventResult.interruptTrue();
        }
        if (bed && !player.isShiftKeyDown()) {
            Claim claim = Registry.get(world.getServer()).at(GlobalPos.of(world.dimension(), head(pos, state)));
            if (claim != null) {
                player.displayClientMessage(Component.translatable("bed.mca_vie.owned", claim.name()).withStyle(ChatFormatting.GOLD), true);
                return EventResult.interruptTrue();
            }
        }
        return EventResult.pass();
    }

    private static void assign(ServerLevel world, VillagerEntityMCA villager, ServerPlayer player, BlockPos head) {
        GlobalPos target = GlobalPos.of(world.dimension(), head);
        Registry registry = Registry.get(world.getServer());
        Claim claim = registry.at(target);
        if (claim != null && !claim.villager().equals(villager.getUUID())) {
            player.displayClientMessage(Component.translatable("bed.mca_vie.taken", claim.name()).withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!isBed(world, head)) {
            villager.sendChatMessage(player, "interaction.sethome.bedfail");
            return;
        }
        // ancien lit rendu, lit visé repris aux PNJ qui l'avaient en mémoire (leur réservation passe au nouveau propriétaire)
        GlobalPos current = villager.getBrain().getMemoryInternal(MemoryModuleType.HOME).orElse(null);
        if (current != null && !current.equals(target)) {
            villager.releasePoi(MemoryModuleType.HOME);
            villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        }
        List<VillagerEntityMCA> others = rememberers(world, target, villager);
        others.forEach(BedClaims::evict);
        if (others.isEmpty() && !target.equals(current) && hasSpace(world.getPoiManager(), head)) {
            reserve(world, head);
        }
        villager.getBrain().setMemory(MemoryModuleType.HOME, target);
        villager.getBrain().setMemory(MemoryModuleTypeMCA.FORCED_HOME.get(), true);
        registry.claim(target, villager.getUUID(), villager.getName().getString());
        villager.getResidency().seekHome();
        villager.sendChatMessage(player, "interaction.sethome.success");
    }

    /** Lits cassés oubliés, propriétaires rétablis, un lit = un PNJ, réservations maintenues. */
    private static void tick(ServerLevel world) {
        if (world.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }
        Registry registry = Registry.get(world.getServer());
        PoiManager poi = world.getPoiManager();
        Map<UUID, VillagerEntityMCA> loaded = new HashMap<>();
        Map<GlobalPos, List<VillagerEntityMCA>> byBed = new HashMap<>();
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof VillagerEntityMCA villager && villager.isAlive()) {
                loaded.put(villager.getUUID(), villager);
                villager.getBrain().getMemoryInternal(MemoryModuleType.HOME)
                        .filter(h -> h.dimension() == world.dimension())
                        .ifPresent(h -> byBed.computeIfAbsent(h, k -> new ArrayList<>()).add(villager));
            }
        }

        for (Map.Entry<GlobalPos, Claim> entry : new ArrayList<>(registry.claims.entrySet())) {
            GlobalPos bed = entry.getKey();
            if (bed.dimension() != world.dimension() || !world.hasChunkAt(bed.pos())) {
                continue;
            }
            VillagerEntityMCA owner = loaded.get(entry.getValue().villager());
            if (!isBed(world, bed.pos())) {
                registry.release(entry.getValue().villager());
                if (owner != null && owner.getBrain().getMemoryInternal(MemoryModuleType.HOME).filter(bed::equals).isPresent()) {
                    evict(owner);
                }
                continue;
            }
            if (owner != null && owner.getBrain().getMemoryInternal(MemoryModuleType.HOME).filter(bed::equals).isEmpty()) {
                // MCA lui a fait oublier son lit : on le lui rend
                owner.getBrain().setMemory(MemoryModuleType.HOME, bed);
                owner.getBrain().setMemory(MemoryModuleTypeMCA.FORCED_HOME.get(), true);
                byBed.computeIfAbsent(bed, k -> new ArrayList<>()).add(owner);
            }
            List<VillagerEntityMCA> rememberers = byBed.getOrDefault(bed, List.of());
            rememberers.stream().filter(v -> !v.getUUID().equals(entry.getValue().villager())).forEach(BedClaims::evict);
            byBed.remove(bed);
            if (hasSpace(poi, bed.pos())) {
                reserve(world, bed.pos());
            }
        }

        byBed.forEach((bed, villagers) -> {
            if (villagers.size() > 1) {
                VillagerEntityMCA keeper = villagers.stream().filter(BedClaims::forced).findFirst().orElse(villagers.get(0));
                villagers.stream().filter(v -> v != keeper).forEach(BedClaims::evict);
            }
            BlockPos pos = bed.pos();
            if (world.hasChunkAt(pos) && hasSpace(poi, pos)) {
                reserve(world, pos);
            }
        });
    }

    private static boolean forced(VillagerEntityMCA villager) {
        Optional<Boolean> forced = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME.get());
        //noinspection OptionalAssignedToNull
        return forced != null && forced.isPresent();
    }

    /** Oublie le lit sans rendre sa réservation (elle appartient au PNJ qui le garde). */
    private static void evict(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.FORCED_HOME.get());
    }

    private static List<VillagerEntityMCA> rememberers(ServerLevel world, GlobalPos bed, VillagerEntityMCA except) {
        List<VillagerEntityMCA> list = new ArrayList<>();
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof VillagerEntityMCA other && other != except
                    && other.getBrain().getMemoryInternal(MemoryModuleType.HOME).filter(bed::equals).isPresent()) {
                list.add(other);
            }
        }
        return list;
    }

    private static BlockPos head(BlockPos pos, BlockState state) {
        return state.getValue(BedBlock.PART) == BedPart.HEAD ? pos.immutable() : pos.relative(state.getValue(BedBlock.FACING)).immutable();
    }

    private static boolean isBed(ServerLevel world, BlockPos head) {
        BlockState state = world.getBlockState(head);
        return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    private static boolean hasSpace(PoiManager poi, BlockPos pos) {
        return poi.findAll(e -> e.is(PoiTypes.HOME), pos::equals, pos, 1,
                PoiManager.Occupancy.HAS_SPACE).findAny().isPresent();
    }

    private static void reserve(ServerLevel world, BlockPos pos) {
        world.getPoiManager().take(e -> e.is(PoiTypes.HOME), (e, p) -> p.equals(pos), pos, 1);
    }

    private record Claim(UUID villager, String name) {
    }

    /** Lits attribués, sauvegardés avec le monde. */
    private static final class Registry extends SavedData {
        private static final String DATA_ID = "mca_vie_beds";
        private final Map<GlobalPos, Claim> claims = new HashMap<>();

        static Registry get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Registry::fromNbt, Registry::new, DATA_ID);
        }

        Claim at(GlobalPos bed) {
            return claims.get(bed);
        }

        void claim(GlobalPos bed, UUID villager, String name) {
            claims.values().removeIf(c -> c.villager().equals(villager));
            claims.put(bed, new Claim(villager, name));
            setDirty();
        }

        void release(UUID villager) {
            if (claims.values().removeIf(c -> c.villager().equals(villager))) {
                setDirty();
            }
        }

        private static Registry fromNbt(CompoundTag nbt) {
            Registry registry = new Registry();
            ListTag list = nbt.getList("beds", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(c.getString("dim")));
                BlockPos pos = BlockPos.of(c.getLong("pos"));
                registry.claims.put(GlobalPos.of(dimension, pos), new Claim(c.getUUID("villager"), c.getString("name")));
            }
            return registry;
        }

        @Override
        public CompoundTag save(CompoundTag nbt) {
            ListTag list = new ListTag();
            claims.forEach((bed, claim) -> {
                CompoundTag c = new CompoundTag();
                c.putString("dim", bed.dimension().location().toString());
                c.putLong("pos", bed.pos().asLong());
                c.putUUID("villager", claim.villager());
                c.putString("name", claim.name());
                list.add(c);
            });
            nbt.put("beds", list);
            return nbt;
        }
    }
}
