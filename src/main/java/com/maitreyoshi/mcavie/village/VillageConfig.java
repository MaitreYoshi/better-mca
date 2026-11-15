package com.maitreyoshi.mcavie.village;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.maitreyoshi.mcavie.MCAVie;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VillageConfig {
    private static final String FILE = "mca_vie_village.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String KEY_MAGNIFICENT = "*magnificent_public*";

    private static VillageConfig instance;

    public int descentDays = 14;
    public double residentialShare = 0.30;
    public int workshopMaxTotal = 8;

    public double taxBase = 10;
    public double taxLevelBonus = 0.125;
    public double libraryTaxBonus = 0.10;
    public int taxAdultAge = 18;
    public int taxCatchUpWeeks = 4;
    public int playerSharePercent = 20;
    public int overdraftGraceWeeks = 2;
    public List<EquilibriumStep> happinessEquilibrium = defaultEquilibrium();
    public int chapelHappinessBonus = 2;
    public int theaterHappinessBonus = 2;
    public int capitalHappinessBonus = 5;

    public double feastCostPerResident = 1;
    public int feastHappiness = 10;
    public int birthBonusCostPerBirth = 3;
    public double birthBonusChance = 0.5;
    public int renovationWeeks = 4;
    public double scholarshipCostPerApprentice = 1;
    public double scholarshipXpMultiplier = 2;
    public double settlersCallCostPerResident = 1;
    public double freeCareCostPerResident = 0.5;
    public double freeCareIllnessMultiplier = 0.5;
    public int guardCost = 10;
    public int guardsPerBarracks = 2;
    public double festivalCostPerResident = 3;
    public int festivalHappiness = 20;

    public double schoolXpMultiplier = 1.25;
    public double infirmaryIllnessMultiplier = 0.75;
    public int apprenticeMinAge = 14;

    public double marketDiscount = 0.10;

    public int natureStep = 25;
    public int marketDayOfWeek = 6;
    public int marketStartHour = 8;
    public int marketEndHour = 18;
    public double marketSpendPerResident = 1;
    public double marketSalePrice = 1;
    public int marketHappiness = 3;
    public int marketDiscountPercent = 20;
    public int marketMerchants = 2;
    public int marketMerchantTicks = 6000;
    public int orderLotPrice = 2;
    public int maxOrdersPerPlayer = 5;
    public int maxLotsPerOrder = 10;
    public int registrySize = 300;
    public int crimeHappiness = 20;
    public double crimeChance = 0.05;
    public double prisonCrimeMultiplier = 0.5;
    public int theftMax = 5;
    public double arrestChancePerGuard = 0.25;
    public double arrestMaxChance = 0.9;
    public int judgmentDays = 3;
    public int prisonDays = 7;
    public int prisonHappiness = 10;
    public int pardonHappiness = 10;
    public Map<String, Integer> natureUnitsPerEmerald = defaultNatureUnits();

    public List<Double> qualityMultipliers = new ArrayList<>(List.of(0.5, 1.0, 1.5, 2.0));
    public double beautifulSurface = 1.5;
    public int beautifulWindows = 2;
    public int beautifulDecor = 6;
    public double magnificentSurface = 2.0;
    public int magnificentWindows = 4;
    public int magnificentDecor = 12;
    public List<String> publicBuildings = defaultPublicBuildings();

    public List<Double> familiesPerWeek = new ArrayList<>(List.of(0.25, 1.0 / 3, 0.5, 1.0, 1.5));
    public int familyMinSize = 2;
    public int familyMaxSize = 4;

    public static final class EquilibriumStep {
        public int maxRate;
        public int value;

        EquilibriumStep(int maxRate, int value) {
            this.maxRate = maxRate;
            this.value = value;
        }
    }
    public List<RankRule> ranks = defaultRanks();
    public Map<String, BuildingRule> buildings = defaultBuildings();

    public static final class RankRule {
        public int prestige;
        public int population;
        public String keyBuilding;
        public double taxMultiplier;
        public int maxTaxRate;

        RankRule(int prestige, int population, String keyBuilding, double taxMultiplier, int maxTaxRate) {
            this.prestige = prestige;
            this.population = population;
            this.keyBuilding = keyBuilding;
            this.taxMultiplier = taxMultiplier;
            this.maxTaxRate = maxTaxRate;
        }
    }

    public static final class BuildingRule {
        public int rank;
        public int points;
        public int maxCount;
        public int minSurface;
        public int upkeep;
        public boolean residential;
        public boolean workshop;

        BuildingRule(int rank, int points, int maxCount, int minSurface, int upkeep) {
            this.rank = rank;
            this.points = points;
            this.maxCount = maxCount;
            this.minSurface = minSurface;
            this.upkeep = upkeep;
        }

        public VillageRank requiredRank() {
            return VillageRank.byIndex(rank);
        }
    }

    public static VillageConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public RankRule rank(VillageRank rank) {
        return ranks.get(rank.ordinal());
    }

    public BuildingRule building(String type) {
        return buildings.get(type);
    }

    public static void load() {
        Path path = Platform.getConfigFolder().resolve(FILE);
        VillageConfig config = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, VillageConfig.class);
            } catch (Exception e) {
                MCAVie.LOGGER.error("Impossible de lire {}, valeurs par défaut utilisées", FILE, e);
            }
        }
        if (config == null) {
            config = new VillageConfig();
        }
        if (config.ranks == null || config.ranks.size() != VillageRank.values().length) {
            config.ranks = defaultRanks();
        }
        if (config.buildings == null) {
            config.buildings = new LinkedHashMap<>();
        }
        defaultBuildings().forEach(config.buildings::putIfAbsent);
        if (config.familiesPerWeek == null || config.familiesPerWeek.size() != VillageRank.values().length) {
            config.familiesPerWeek = new ArrayList<>(List.of(0.25, 1.0 / 3, 0.5, 1.0, 1.5));
        }
        if (config.qualityMultipliers == null || config.qualityMultipliers.size() != BuildingQuality.values().length) {
            config.qualityMultipliers = new ArrayList<>(List.of(0.5, 1.0, 1.5, 2.0));
        }
        if (config.publicBuildings == null) {
            config.publicBuildings = defaultPublicBuildings();
        }
        if (config.natureUnitsPerEmerald == null) {
            config.natureUnitsPerEmerald = defaultNatureUnits();
        }
        if (config.buildings != null) {
            // Pas de surface minimale pour le Marché et l'Entrepôt, même avec un ancien fichier de config.
            for (String key : new String[]{"market", "storage"}) {
                BuildingRule rule = config.buildings.get(key);
                if (rule != null) {
                    rule.minSurface = 0;
                }
            }
        }
        config.natureStep = Math.max(1, Math.min(100, config.natureStep));
        config.marketDayOfWeek = Math.max(1, Math.min(7, config.marketDayOfWeek));
        config.orderLotPrice = Math.max(1, config.orderLotPrice);
        config.maxLotsPerOrder = Math.max(1, config.maxLotsPerOrder);
        config.marketSalePrice = Math.max(0.01, config.marketSalePrice);
        config.marketSpendPerResident = Math.max(0, config.marketSpendPerResident);
        config.marketStartHour = Math.max(0, Math.min(23, config.marketStartHour));
        config.marketEndHour = Math.max(config.marketStartHour + 1, Math.min(24, config.marketEndHour));
        if (config.happinessEquilibrium == null || config.happinessEquilibrium.isEmpty()) {
            config.happinessEquilibrium = defaultEquilibrium();
        }
        instance = config;

        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            MCAVie.LOGGER.warn("Impossible d'écrire {}", FILE, e);
        }
    }

    public int equilibriumFor(int ratePercent) {
        for (EquilibriumStep step : happinessEquilibrium) {
            if (ratePercent <= step.maxRate) {
                return step.value;
            }
        }
        return happinessEquilibrium.isEmpty() ? 50 : happinessEquilibrium.get(happinessEquilibrium.size() - 1).value;
    }

    private static List<EquilibriumStep> defaultEquilibrium() {
        List<EquilibriumStep> list = new ArrayList<>();
        list.add(new EquilibriumStep(0, 55));
        list.add(new EquilibriumStep(10, 50));
        list.add(new EquilibriumStep(15, 47));
        list.add(new EquilibriumStep(20, 44));
        list.add(new EquilibriumStep(25, 40));
        list.add(new EquilibriumStep(100, 35));
        return list;
    }

    public static void applyRemote(String json) {
        try {
            VillageConfig remote = GSON.fromJson(json, VillageConfig.class);
            if (remote != null && remote.ranks != null && remote.ranks.size() == VillageRank.values().length && remote.buildings != null) {
                instance = remote;
            }
        } catch (Exception e) {
            MCAVie.LOGGER.warn("Config du village reçue du serveur illisible", e);
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public boolean isPublic(String type) {
        return publicBuildings != null && publicBuildings.contains(type);
    }

    private static List<String> defaultPublicBuildings() {
        return new ArrayList<>(List.of("inn", "town_hall", "storage", "chapel", "market", "school", "infirmary", "armory", "library", "theater"));
    }

    public int unitsPerEmerald(String itemId) {
        Integer units = natureUnitsPerEmerald == null ? null : natureUnitsPerEmerald.get(itemId);
        return units == null || units <= 0 ? 0 : units;
    }

    private static Map<String, Integer> defaultNatureUnits() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("minecraft:wheat", 20);
        map.put("minecraft:potato", 26);
        map.put("minecraft:carrot", 22);
        map.put("minecraft:beetroot", 15);
        map.put("minecraft:pumpkin", 6);
        map.put("minecraft:melon_slice", 16);
        map.put("minecraft:bread", 6);
        map.put("minecraft:egg", 16);
        map.put("minecraft:cod", 15);
        map.put("minecraft:salmon", 13);
        map.put("minecraft:chicken", 14);
        map.put("minecraft:porkchop", 7);
        map.put("minecraft:beef", 10);
        map.put("minecraft:mutton", 7);
        map.put("minecraft:leather", 6);
        map.put("minecraft:white_wool", 18);
        map.put("minecraft:iron_ingot", 4);
        map.put("minecraft:coal", 15);
        map.put("minecraft:paper", 24);
        for (String wood : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"}) {
            map.put("minecraft:" + wood + "_log", 16);
        }
        return map;
    }

    private static List<RankRule> defaultRanks() {
        List<RankRule> list = new ArrayList<>();
        list.add(new RankRule(0, 1, "", 0.5, 10));
        list.add(new RankRule(15, 5, "town_hall", 1.0, 15));
        list.add(new RankRule(35, 12, "market", 1.3, 20));
        list.add(new RankRule(70, 22, "armory", 1.6, 25));
        list.add(new RankRule(120, 35, KEY_MAGNIFICENT, 2.0, 30));
        return list;
    }

    private static Map<String, BuildingRule> defaultBuildings() {
        Map<String, BuildingRule> map = new LinkedHashMap<>();
        BuildingRule house = new BuildingRule(0, 1, 20, 9, 0);
        house.residential = true;
        map.put("house", house);
        BuildingRule bigHouse = new BuildingRule(0, 2, 10, 25, 0);
        bigHouse.residential = true;
        map.put("big_house", bigHouse);
        for (String workshop : new String[]{"armorer", "bakery", "blacksmith", "bookkeeper", "butcher", "cartographer", "fishermans_hut",
                "fletcher", "leatherworker", "mason", "music_store", "toolsmith", "weaponsmith", "weaving_mill"}) {
            BuildingRule rule = new BuildingRule(0, 2, 1, 9, 0);
            rule.workshop = true;
            map.put(workshop, rule);
        }
        map.put("graveyard", new BuildingRule(0, 2, 1, 0, 0));
        map.put("inn", new BuildingRule(0, 3, 2, 30, 1));
        map.put("town_hall", new BuildingRule(1, 6, 1, 25, 2));
        map.put("storage", new BuildingRule(1, 3, 2, 0, 1));
        map.put("chapel", new BuildingRule(1, 4, 1, 25, 1));
        map.put("market", new BuildingRule(2, 6, 1, 0, 2));
        map.put("school", new BuildingRule(2, 5, 2, 25, 2));
        map.put("infirmary", new BuildingRule(2, 5, 2, 30, 2));
        map.put("armory", new BuildingRule(3, 6, 2, 36, 3));
        map.put("library", new BuildingRule(3, 5, 2, 25, 2));
        map.put("theater", new BuildingRule(4, 10, 1, 64, 4));
        map.put("prison", new BuildingRule(2, 4, 1, 9, 2));
        return map;
    }
}
