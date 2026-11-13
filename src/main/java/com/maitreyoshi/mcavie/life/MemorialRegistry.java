package com.maitreyoshi.mcavie.life;

import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MemorialRegistry extends PersistentState {
    private static final String DATA_ID = "mca_vie_war_memorials";

    public record Fallen(TombstoneDetails details, String killer) {
        NbtCompound write() {
            NbtCompound nbt = new NbtCompound();
            details.write(nbt);
            nbt.putString("killer", killer);
            return nbt;
        }

        @Nullable
        static Fallen read(NbtCompound nbt) {
            TombstoneDetails details = TombstoneDetails.read(nbt);
            return details == null ? null : new Fallen(details, nbt.getString("killer"));
        }
    }

    private final Map<Integer, List<Fallen>> fallen = new HashMap<>();
    private final Map<Integer, Set<GlobalPos>> monuments = new HashMap<>();

    public static MemorialRegistry get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(MemorialRegistry::fromNbt, MemorialRegistry::new, DATA_ID);
    }

    private static List<Fallen> readList(NbtCompound nbt) {
        List<Fallen> list = new ArrayList<>();
        for (NbtElement element : nbt.getList("fallen", NbtElement.COMPOUND_TYPE)) {
            Fallen entry = Fallen.read((NbtCompound) element);
            if (entry != null) {
                list.add(entry);
            }
        }
        return list;
    }

    private static MemorialRegistry fromNbt(NbtCompound nbt) {
        MemorialRegistry registry = new MemorialRegistry();
        NbtCompound villages = nbt.getCompound("villages");
        for (String key : villages.getKeys()) {
            int id = Integer.parseInt(key);
            NbtCompound village = villages.getCompound(key);
            List<Fallen> list = readList(village);
            Set<GlobalPos> set = new LinkedHashSet<>();
            for (NbtElement element : village.getList("monuments", NbtElement.COMPOUND_TYPE)) {
                NbtCompound pos = (NbtCompound) element;
                set.add(GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, new Identifier(pos.getString("dim"))), NbtHelper.toBlockPos(pos)));
                list.addAll(readList(pos));
            }
            registry.fallen.put(id, list);
            registry.monuments.put(id, set);
        }
        return registry;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound villages = new NbtCompound();
        Set<Integer> ids = new java.util.HashSet<>(fallen.keySet());
        ids.addAll(monuments.keySet());
        for (int id : ids) {
            NbtCompound village = new NbtCompound();
            NbtList list = new NbtList();
            fallen.getOrDefault(id, List.of()).forEach(entry -> list.add(entry.write()));
            village.put("fallen", list);
            NbtList positions = new NbtList();
            for (GlobalPos pos : monuments.getOrDefault(id, Set.of())) {
                NbtCompound tag = NbtHelper.fromBlockPos(pos.getPos());
                tag.putString("dim", pos.getDimension().getValue().toString());
                positions.add(tag);
            }
            village.put("monuments", positions);
            villages.put(String.valueOf(id), village);
        }
        nbt.put("villages", villages);
        return nbt;
    }

    public List<Fallen> fallen(int village, World world, BlockPos pos, int capacity) {
        List<Fallen> all = fallen.getOrDefault(village, List.of());
        Set<GlobalPos> set = monuments.getOrDefault(village, Set.of());
        GlobalPos target = GlobalPos.create(world.getRegistryKey(), pos);
        int i = 0;
        for (GlobalPos p : set) {
            if (p.equals(target)) {
                int from = Math.min(all.size(), i * capacity);
                return all.subList(from, i == set.size() - 1 ? all.size() : Math.min(all.size(), from + capacity));
            }
            i++;
        }
        return List.of();
    }

    public void addMonument(int village, World world, BlockPos pos) {
        if (monuments.computeIfAbsent(village, k -> new LinkedHashSet<>()).add(GlobalPos.create(world.getRegistryKey(), pos))) {
            markDirty();
        }
    }

    public void refresh(MinecraftServer server, int village) {
        for (GlobalPos pos : monuments.getOrDefault(village, Set.of())) {
            ServerWorld target = server.getWorld(pos.getDimension());
            if (target != null && target.isChunkLoaded(pos.getPos())) {
                BlockState state = target.getBlockState(pos.getPos());
                target.updateListeners(pos.getPos(), state, state, Block.NOTIFY_ALL);
            }
        }
    }

    public void removeMonument(int village, World world, BlockPos pos) {
        Set<GlobalPos> set = monuments.get(village);
        if (set != null && set.remove(GlobalPos.create(world.getRegistryKey(), pos))) {
            markDirty();
        }
    }

    @Nullable
    public static Text killer(DamageSource cause) {
        Entity attacker = cause.getAttacker();
        if (attacker instanceof PlayerEntity player) {
            return Text.literal(player.getGameProfile().getName());
        }
        if (attacker instanceof Monster) {
            return attacker.getType().getName();
        }
        return null;
    }

    public static boolean died(VillagerEntityMCA villager, ServerWorld world, DamageSource cause) {
        Text killer = killer(cause);
        if (killer == null) {
            return false;
        }
        Optional<Village> home = villager.getResidency().getHomeVillage();
        Optional<Village> village = home.isPresent() ? home : VillageManager.get(world).findNearestVillage(villager);
        if (village.isEmpty()) {
            return false;
        }
        MinecraftServer server = world.getServer();
        LifeRegistry life = LifeRegistry.get(server);
        TombstoneDetails details = TombstoneDetails.of(villager, world, life.getBirthDay(villager.getUuid()),
                com.maitreyoshi.mcavie.calendar.WorldCalendar.now(world).dayIndex());
        int id = village.get().getId();
        MemorialRegistry registry = get(server);
        if (registry.monuments.getOrDefault(id, Set.of()).isEmpty()) {
            return false;
        }
        List<Fallen> list = registry.fallen.computeIfAbsent(id, k -> new ArrayList<>());
        list.removeIf(entry -> entry.details().person().equals(villager.getUuid()));
        list.add(new Fallen(details, Text.Serializer.toJson(killer)));
        registry.markDirty();
        Text message = Text.translatable("block.mca_vie.war_memorial.added", details.firstName()
                + (details.lastName().isEmpty() ? "" : " " + details.lastName()), village.get().getName());
        registry.refresh(server, id);
        Set<net.minecraft.server.network.ServerPlayerEntity> told = new HashSet<>();
        for (GlobalPos pos : registry.monuments.getOrDefault(id, Set.of())) {
            ServerWorld target = server.getWorld(pos.getDimension());
            if (target != null && target.isChunkLoaded(pos.getPos())) {
                target.playSound(null, pos.getPos(), net.minecraft.sound.SoundEvents.BLOCK_BELL_USE, net.minecraft.sound.SoundCategory.BLOCKS, 1.5f, 0.6f);
                told.addAll(target.getPlayers(p -> p.squaredDistanceTo(pos.getPos().toCenterPos()) < 64 * 64));
            }
        }
        told.forEach(p -> p.sendMessage(message.copy().formatted(net.minecraft.util.Formatting.GRAY, net.minecraft.util.Formatting.ITALIC)));
        return true;
    }
}
