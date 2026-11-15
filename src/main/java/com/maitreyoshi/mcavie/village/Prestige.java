package com.maitreyoshi.mcavie.village;

import net.mca.resources.data.BuildingType;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Prestige {
    private Prestige() {
    }

    public enum Status {
        COUNTED,
        NEXT_RANK,
        TOO_HIGH,
        TOO_SMALL,
        OVER_MAX,
        NOT_RATED,
        DECAYED
    }

    public record Score(VillageConfig.BuildingRule rule, double points, Status status, int surface, BuildingQuality quality,
                        boolean protectedKey) {
        public boolean counts() {
            return status == Status.COUNTED || status == Status.NEXT_RANK;
        }

        public boolean isActive() {
            return status == Status.COUNTED;
        }
    }

    private static VillageConfig keyTypesConfig;
    private static java.util.Set<String> keyTypes = java.util.Set.of();

    public static boolean isKeyType(String type) {
        VillageConfig config = VillageConfig.get();
        if (config != keyTypesConfig) {
            keyTypes = config.ranks.stream().map(r -> r.keyBuilding).filter(k -> k != null && !k.isEmpty())
                    .collect(java.util.stream.Collectors.toSet());
            keyTypesConfig = config;
        }
        return keyTypes.contains(type);
    }

    private static int magnificentKey(Village village, VillageRank rank) {
        VillageConfig config = VillageConfig.get();
        return village.getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(b -> config.isPublic(b.getType()))
                .filter(b -> !(b.isTypeForced() && !hasRequiredBlocks(b.getBuildingType(), b)))
                .filter(b -> {
                    VillageConfig.BuildingRule rule = config.building(b.getType());
                    int surface = surface(b);
                    return rule != null && rule.points > 0 && rule.rank <= rank.ordinal() + 1 && surface >= rule.minSurface
                            && quality(b, rule, surface) == BuildingQuality.MAGNIFICENT;
                })
                .mapToInt(Building::getId)
                .min()
                .orElse(-1);
    }

    public static BuildingQuality quality(Building building, VillageConfig.BuildingRule rule, int surface) {
        if (rule == null || building.getBuildingType().grouped()) {
            return BuildingQuality.CORRECT;
        }
        return BuildingQuality.of(BuildingQuality.measureOf(building), surface, rule.minSurface);
    }

    public static boolean hasActive(Result result, Village village, String type) {
        return village.getBuildingsOfType(type).anyMatch(b -> {
            Score score = result.score(b);
            return score != null && score.isActive();
        });
    }

    public record Result(VillageRank rank, double prestige, double residential, double residentialCap,
                         int population, Map<Integer, Score> scores) {
        public Score score(Building building) {
            return scores.get(building.getId());
        }
    }

    public static int surface(Building building) {
        BuildingQuality.Measure measure = BuildingQuality.measureOf(building);
        if (measure != null && measure.floor() > 0) {
            return measure.floor();
        }
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        return (Math.abs(p1.getX() - p0.getX()) + 1) * (Math.abs(p1.getZ() - p0.getZ()) + 1);
    }

    public static boolean hasRequiredBlocks(BuildingType type, Building building) {
        Map<Identifier, List<BlockPos>> available = type.getGroups(building.getBlocks());
        return type.getGroups().entrySet().stream()
                .allMatch(e -> available.containsKey(e.getKey()) && available.get(e.getKey()).size() >= e.getValue());
    }

    public static Result evaluate(Village village, VillageRank rank) {
        VillageConfig config = VillageConfig.get();
        Map<Integer, Score> scores = new HashMap<>();
        java.util.Set<Integer> decayed = VillageState.of(village).decayedIds(village);

        Map<String, List<Building>> byType = new HashMap<>();
        village.getBuildings().values().stream()
                .sorted(Comparator.comparing((Building b) -> decayed.contains(b.getId())).thenComparingInt(Building::getId))
                .forEach(b -> {
                    if (b.isComplete()) {
                        byType.computeIfAbsent(b.getType(), t -> new ArrayList<>()).add(b);
                    }
                });

        double residential = 0;
        double other = 0;
        int workshops = 0;
        int magnificentKey = magnificentKey(village, rank);
        for (Map.Entry<String, List<Building>> entry : byType.entrySet()) {
            VillageConfig.BuildingRule rule = config.building(entry.getKey());
            int counted = 0;
            for (Building b : entry.getValue()) {
                int surface = surface(b);
                BuildingQuality quality = quality(b, rule, surface);
                boolean protectedKey = isKeyType(b.getType()) || b.getId() == magnificentKey;
                Status status;
                if (rule == null || rule.points <= 0 || (b.isTypeForced() && !hasRequiredBlocks(b.getBuildingType(), b))) {
                    status = Status.NOT_RATED;
                } else if (rule.rank > rank.ordinal() + 1) {
                    status = Status.TOO_HIGH;
                } else if (!b.getBuildingType().grouped() && surface < rule.minSurface) {
                    status = Status.TOO_SMALL;
                } else if (counted >= rule.maxCount || (rule.workshop && workshops >= config.workshopMaxTotal)) {
                    status = Status.OVER_MAX;
                } else if (decayed.contains(b.getId()) && !protectedKey) {
                    status = Status.DECAYED;
                } else {
                    status = rule.rank > rank.ordinal() ? Status.NEXT_RANK : Status.COUNTED;
                }

                double points = 0;
                if (status == Status.COUNTED || status == Status.NEXT_RANK || status == Status.DECAYED) {
                    counted++;
                    if (rule.workshop) {
                        workshops++;
                    }
                    points = rule.points * quality.multiplier();
                    if (status == Status.DECAYED) {
                        points /= 2.0;
                    }
                    if (rule.residential) {
                        residential += points;
                    } else {
                        other += points;
                    }
                }
                scores.put(b.getId(), new Score(rule, points, status, surface, quality, protectedKey));
            }
        }

        double share = Math.max(0, Math.min(0.99, config.residentialShare));
        double cap = Math.floor(other * share / (1 - share) + 1e-9);
        double prestige = other + Math.min(residential, cap);

        return new Result(rank, prestige, residential, cap, village.getPopulation(), scores);
    }

    private static java.lang.ref.WeakReference<Village> cachedVillage = new java.lang.ref.WeakReference<>(null);
    private static VillageRank cachedRank;
    private static VillageConfig cachedConfig;
    private static Result cachedResult;

    public static Result evaluateCached(Village village, VillageRank rank) {
        VillageConfig config = VillageConfig.get();
        if (cachedVillage.get() != village || cachedRank != rank || cachedConfig != config || cachedResult == null) {
            cachedResult = evaluate(village, rank);
            cachedVillage = new java.lang.ref.WeakReference<>(village);
            cachedRank = rank;
            cachedConfig = config;
        }
        return cachedResult;
    }

    public static boolean meets(Village village, VillageRank target, Result result) {
        VillageConfig.RankRule rule = target.rule();
        return result.prestige() >= rule.prestige
                && result.population() >= rule.population
                && hasKeyBuilding(village, target, result);
    }

    public static boolean hasKeyBuilding(Village village, VillageRank target, Result result) {
        String key = target.rule().keyBuilding;
        if (key == null || key.isEmpty()) {
            return true;
        }
        if (key.equals(VillageConfig.KEY_MAGNIFICENT)) {
            VillageConfig config = VillageConfig.get();
            return village.getBuildings().values().stream().anyMatch(b -> {
                Score score = result.score(b);
                return score != null && config.isPublic(b.getType()) && score.quality() == BuildingQuality.MAGNIFICENT
                        && (score.counts() || score.status() == Status.OVER_MAX);
            });
        }
        return village.getBuildingsOfType(key).anyMatch(b -> {
            Score score = result.score(b);
            return score != null && (score.counts() || score.status() == Status.OVER_MAX);
        });
    }

    public static Result climb(Village village, VillageRank current) {
        VillageRank rank = current;
        Result result = evaluate(village, rank);
        while (rank.next() != null && meets(village, rank, result) && meets(village, rank.next(), result)) {
            rank = rank.next();
            result = evaluate(village, rank);
        }
        return result;
    }

    public static VillageRank fallback(Village village, VillageRank current, Result currentResult) {
        for (VillageRank rank = current; rank != null; rank = rank.previous()) {
            if (meets(village, rank, rank == currentResult.rank() ? currentResult : evaluate(village, rank))) {
                return rank;
            }
        }
        return VillageRank.HAMLET;
    }
}
