package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.compat.Nbt;
import com.maitreyoshi.mcavie.compat.Saved;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MemorialRegistry extends SavedData {
    private static final String DATA_ID = "mca_vie_war_memorials";

    public record Fallen(TombstoneDetails details, String killer) {
        CompoundTag write() {
            CompoundTag nbt = new CompoundTag();
            details.write(nbt);
            nbt.putString("killer", killer);
            return nbt;
        }

        @Nullable
        static Fallen read(CompoundTag nbt) {
            TombstoneDetails details = TombstoneDetails.read(nbt);
            return details == null ? null : new Fallen(details, nbt.getStringOr("killer", ""));
        }
    }

    private final Map<Integer, List<Fallen>> fallen = new HashMap<>();
    private final Map<Integer, Set<GlobalPos>> monuments = new HashMap<>();

    public static MemorialRegistry get(MinecraftServer server) {
        return Saved.get(server.overworld(), MemorialRegistry::fromNbt, MemorialRegistry::new, MemorialRegistry::save, DATA_ID);
    }

    private static List<Fallen> readList(CompoundTag nbt) {
        List<Fallen> list = new ArrayList<>();
        for (Tag element : nbt.getListOrEmpty("fallen")) {
            Fallen entry = Fallen.read((CompoundTag) element);
            if (entry != null) {
                list.add(entry);
            }
        }
        return list;
    }

    private static MemorialRegistry fromNbt(CompoundTag nbt) {
        MemorialRegistry registry = new MemorialRegistry();
        CompoundTag villages = nbt.getCompoundOrEmpty("villages");
        for (String key : villages.keySet()) {
            int id = Integer.parseInt(key);
            CompoundTag village = villages.getCompoundOrEmpty(key);
            List<Fallen> list = readList(village);
            Set<GlobalPos> set = new LinkedHashSet<>();
            for (Tag element : village.getListOrEmpty("monuments")) {
                CompoundTag pos = (CompoundTag) element;
                set.add(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, Identifier.parse(pos.getStringOr("dim", ""))), Nbt.readPos(pos)));
                list.addAll(readList(pos));
            }
            registry.fallen.put(id, list);
            registry.monuments.put(id, set);
        }
        return registry;
    }

        public CompoundTag save(CompoundTag nbt) {
        CompoundTag villages = new CompoundTag();
        Set<Integer> ids = new java.util.HashSet<>(fallen.keySet());
        ids.addAll(monuments.keySet());
        for (int id : ids) {
            CompoundTag village = new CompoundTag();
            ListTag list = new ListTag();
            fallen.getOrDefault(id, List.of()).forEach(entry -> list.add(entry.write()));
            village.put("fallen", list);
            ListTag positions = new ListTag();
            for (GlobalPos pos : monuments.getOrDefault(id, Set.of())) {
                CompoundTag tag = Nbt.pos(pos.pos());
                tag.putString("dim", pos.dimension().identifier().toString());
                positions.add(tag);
            }
            village.put("monuments", positions);
            villages.put(String.valueOf(id), village);
        }
        nbt.put("villages", villages);
        return nbt;
    }

    public List<Fallen> fallen(int village, Level world, BlockPos pos, int capacity) {
        List<Fallen> all = fallen.getOrDefault(village, List.of());
        Set<GlobalPos> set = monuments.getOrDefault(village, Set.of());
        GlobalPos target = GlobalPos.of(world.dimension(), pos);
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

    public void addMonument(int village, Level world, BlockPos pos) {
        if (monuments.computeIfAbsent(village, k -> new LinkedHashSet<>()).add(GlobalPos.of(world.dimension(), pos))) {
            setDirty();
        }
    }

    public void refresh(MinecraftServer server, int village) {
        for (GlobalPos pos : monuments.getOrDefault(village, Set.of())) {
            ServerLevel target = server.getLevel(pos.dimension());
            if (target != null && target.hasChunkAt(pos.pos())) {
                BlockState state = target.getBlockState(pos.pos());
                target.sendBlockUpdated(pos.pos(), state, state, Block.UPDATE_ALL);
            }
        }
    }

    public void removeMonument(int village, Level world, BlockPos pos) {
        Set<GlobalPos> set = monuments.get(village);
        if (set != null && set.remove(GlobalPos.of(world.dimension(), pos))) {
            setDirty();
        }
    }

    @Nullable
    public static Component killer(DamageSource cause) {
        Entity attacker = cause.getEntity();
        if (attacker instanceof Player player) {
            return Component.literal(player.getGameProfile().name());
        }
        if (attacker instanceof Enemy) {
            return attacker.getType().getDescription();
        }
        return null;
    }

    public static boolean died(VillagerEntityMCA villager, ServerLevel world, DamageSource cause) {
        Component killer = killer(cause);
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
        TombstoneDetails details = TombstoneDetails.of(villager, world, life.getBirthDay(villager.getUUID()),
                com.maitreyoshi.mcavie.calendar.WorldCalendar.now(world).dayIndex());
        int id = village.get().getId();
        MemorialRegistry registry = get(server);
        if (registry.monuments.getOrDefault(id, Set.of()).isEmpty()) {
            return false;
        }
        List<Fallen> list = registry.fallen.computeIfAbsent(id, k -> new ArrayList<>());
        list.removeIf(entry -> entry.details().person().equals(villager.getUUID()));
        list.add(new Fallen(details, Component.Serializer.toJson(killer)));
        registry.setDirty();
        Component message = Component.translatable("block.mca_vie.war_memorial.added", details.firstName()
                + (details.lastName().isEmpty() ? "" : " " + details.lastName()), village.get().getName());
        registry.refresh(server, id);
        Set<net.minecraft.server.level.ServerPlayer> told = new HashSet<>();
        for (GlobalPos pos : registry.monuments.getOrDefault(id, Set.of())) {
            ServerLevel target = server.getLevel(pos.dimension());
            if (target != null && target.hasChunkAt(pos.pos())) {
                target.playSound(null, pos.pos(), net.minecraft.sounds.SoundEvents.BELL_BLOCK, net.minecraft.sounds.SoundSource.BLOCKS, 1.5f, 0.6f);
                told.addAll(target.getPlayers(p -> p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos.pos())) < 64 * 64));
            }
        }
        told.forEach(p -> p.sendSystemMessage(message.copy().withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC)));
        return true;
    }
}
