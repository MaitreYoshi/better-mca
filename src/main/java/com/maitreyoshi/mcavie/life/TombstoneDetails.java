package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.SurnameRegistry;
import com.maitreyoshi.mcavie.SurnameText;
import com.maitreyoshi.mcavie.aging.AgingConfig;
import com.maitreyoshi.mcavie.village.VillageState;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.EntityRelationship;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import java.util.List;
import java.util.UUID;

public record TombstoneDetails(UUID person, String firstName, String lastName, @Nullable String job, @Nullable Long birth, long death, int tier,
                               List<Long> pastLives, @Nullable Long exactBirth, @Nullable Long exactDeath) {
    public static final String KEY = "mca_vie:tombstone";
    public static final int LEGACY = -1;
    public static final int POOR = 0;
    public static final int MIDDLE = 1;
    public static final int RICH = 2;

    public interface Holder {
        @Nullable
        TombstoneDetails mcavie$details();
    }

    public static TombstoneDetails of(Entity entity, ServerWorld world, @Nullable Long birth, long death) {
        String name = entity.getName().getString();
        String canonical = SurnameRegistry.get(world.getServer()).get(entity.getUuid());
        String surname = "";
        if (canonical != null) {
            Gender gender = EntityRelationship.of(entity).map(EntityRelationship::getGender).orElse(Gender.MALE);
            surname = SurnameText.display(canonical, gender);
        }
        String first = SurnameText.firstName(name, surname);
        if (first.isEmpty()) {
            first = name.trim();
            surname = "";
        }
        LifeRegistry life = LifeRegistry.get(world.getServer());
        Long exactDeath = life.getDeathDay(entity.getUuid()) != null ? life.exactDeath(entity.getUuid()) : null;
        return new TombstoneDetails(entity.getUuid(), first, surname, jobOf(entity, world), birth, death, tierOf(entity, world),
                life.pastLives(entity.getUuid()), birth == null ? null : life.exactBirth(entity.getUuid()),
                exactDeath != null ? exactDeath : WorldCalendar.exactCalendarDay(world));
    }

    public static int tierOf(Entity entity, ServerWorld world) {
        if (!(entity instanceof VillagerEntityMCA villager) || villager.isBaby()) {
            return MIDDLE;
        }
        WorkRegistry.Record record = WorkRegistry.get(world.getServer()).get(entity.getUuid());
        Job job = record == null ? Job.NONE : record.job;
        String key = job == Job.RETIRED ? record.formerJob : job == Job.NONE ? null : "job.mca_vie." + job.id();
        int score;
        if (key != null && key.startsWith("job.mca_vie.")) {
            score = record.level;
        } else {
            score = key != null || !villager.getProfessionName().equals("mca.none") ? 1 : 0;
        }
        if (("job.mca_vie." + Job.DOCTOR.id()).equals(key) || ("job.mca_vie." + Job.TEACHER.id()).equals(key)) {
            score++;
        }
        score += villager.getResidency().getHomeVillage()
                .filter(village -> village.hasResident(villager.getUuid()))
                .map(village -> VillageState.of(village).rank.ordinal())
                .orElse(0);
        AgingConfig config = AgingConfig.get();
        return score >= config.tombRichScore ? RICH : score >= config.tombMiddleScore ? MIDDLE : POOR;
    }

    @Nullable
    private static String jobOf(Entity entity, ServerWorld world) {
        if (!(entity instanceof VillagerLike<?> villager) || villager.asEntity().isBaby()) {
            return null;
        }
        Job job = WorkRegistry.get(world.getServer()).getJob(entity.getUuid());
        if (job == Job.RETIRED) {
            String former = WorkRegistry.get(world.getServer()).retiredKey(entity.getUuid(), true);
            return former.isEmpty() ? null : former;
        }
        if (job != Job.NONE) {
            return "job.mca_vie." + job.id();
        }
        String profession = villager.getProfessionName();
        return profession.equals("mca.none") ? null : "entity.minecraft.villager." + profession;
    }

    @Nullable
    public static TombstoneDetails read(@Nullable NbtCompound parent) {
        if (parent == null || !parent.contains(KEY)) {
            return null;
        }
        NbtCompound nbt = parent.getCompound(KEY);
        if (!nbt.containsUuid("person")) {
            return null;
        }
        return new TombstoneDetails(
                nbt.getUuid("person"),
                nbt.getString("first"),
                nbt.getString("last"),
                nbt.contains("job") ? nbt.getString("job") : null,
                nbt.contains("birth") ? nbt.getLong("birth") : null,
                nbt.getLong("death"),
                nbt.contains("tier") ? nbt.getInt("tier") : LEGACY,
                nbt.contains("lives_cal") ? java.util.Arrays.stream(nbt.getLongArray("lives_cal")).boxed().toList()
                        : java.util.Arrays.stream(nbt.getLongArray("lives")).map(WorldCalendar::calendarDay).boxed().toList(),
                nbt.contains("exact_birth") ? nbt.getLong("exact_birth") : null,
                nbt.contains("exact_death") ? nbt.getLong("exact_death") : null
        );
    }

    public void write(NbtCompound parent) {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("person", person);
        nbt.putString("first", firstName);
        nbt.putString("last", lastName);
        if (job != null) {
            nbt.putString("job", job);
        }
        if (birth != null) {
            nbt.putLong("birth", birth);
        }
        nbt.putLong("death", death);
        if (tier != LEGACY) {
            nbt.putInt("tier", tier);
        }
        if (!pastLives.isEmpty()) {
            nbt.putLongArray("lives_cal", pastLives);
        }
        if (exactBirth != null) {
            nbt.putLong("exact_birth", exactBirth);
        }
        if (exactDeath != null) {
            nbt.putLong("exact_death", exactDeath);
        }
        parent.put(KEY, nbt);
    }
}
