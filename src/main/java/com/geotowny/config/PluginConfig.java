package com.geotowny.config;

import com.geotowny.Log;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 类型化配置视图。
 * <p>只暴露决策后保留的配置项；非法值回退默认值并记录告警。
 */
public final class PluginConfig {

    public enum DayDefinition {
        REAL,
        MINECRAFT
    }

    private final int configVersion;
    private final DayDefinition dayDefinition;

    private final double townCreationCost;
    private final double claimCost;
    private final double upkeepPerBlock;
    private final int baseClaimBlocks;
    private final int claimBlocksPerResident;
    private final int maxTownBlocks;
    private final int bankruptcyDays;
    private final int abandonedDays;
    private final int abandonedDissolveDays;

    private final double nationCreationCost;
    private final int nationMaxTowns;

    private final double shopPlotDailyIncome;
    private final double farmPlotUpkeepDiscount;
    private final double bankPlotInterestRate;
    private final double innPlotDailyIncome;
    private final double arenaPlotDailyIncome;
    private final double fortPlotDefenseBonus;
    private final double fortPlotUpkeepExtra;
    private final double embassyPlotUpkeep;

    private final double startingBalance;
    private final double maxDailyTax;
    private final String currencyName;
    private final boolean physicalCurrencyEnabled;
    private final double physicalCurrencyFeeRate;
    private final int physicalCurrencyMaxIssued;
    private final int autoSaveIntervalSeconds;

    private final boolean warEnabled;
    private final double warDeclareCost;
    private final int campaignCapturePoints;
    private final int campaignPointsPerKill;
    private final int campaignPointsPerBreak;
    private final int campaignDurationMinutes;
    private final int exileDays;
    private final double campaignCost;
    private final int campaignCaptureCountdownSeconds;
    private final int campaignFailCountdownSeconds;
    private final int campaignBaseRespawnSeconds;
    private final double campaignRespawnIncreasePercent;
    private final int campaignMaxDefenderDeaths;
    private final double campaignDefenderRatio;
    private final double campaignCompensationPercent;
    private final double campaignMaxResidentContributionPercent;
    private final int campaignMaxPerNation;

    private final boolean killDropEnabled;
    private final double warKillDropPercent;
    private final double warKillDropDecayFactor;
    private final double killDropBaseChance;
    private final double killDropBaseAmount;
    private final double killDropDecayFactor;

    private final boolean festivalEnabled;
    private final double festivalTaxMultiplier;
    private final double festivalInfluenceMultiplier;
    private final double festivalShopPriceMultiplier;
    private final boolean festivalWeekend;
    private final Map<String, String> festivalCustom;

    private final boolean influenceEnabled;
    private final double influenceDailyGrowth;
    private final double maxTownInfluence;

    private final boolean shopEnabled;
    private final boolean freeMarketEnabled;
    private final int shopTradeStep;
    private final double shopPriceElasticity;
    private final double shopPriceCeilingMultiplier;
    private final double shopPriceFloorMultiplier;
    private final double shopVolumeDecay;
    private final double shopDailyRandomRange;
    private final double shopSellBuyRatio;
    private final int shopPlayerDailyBuyLimit;
    private final int shopPlayerDailySellLimit;
    private final int shopServerDailyBuyLimit;
    private final int shopServerDailySellLimit;

    private final double freeMarketTax;
    private final int freeMarketMaxListingsPerPlayer;
    private final double freeMarketMinPrice;
    private final double freeMarketMaxPrice;
    private final int freeMarketExpireDays;

    private final boolean checkInEnabled;
    private final double checkInBaseReward;
    private final int checkInCycleDays;
    private final double[] checkInStreakRewards;

    private final boolean redeemEnabled;

    private final boolean protectBlockBreak;
    private final boolean protectBlockPlace;
    private final boolean protectInteract;
    private final boolean protectPvp;
    private final boolean wildernessPvp;
    private final boolean allowForeignerActions;

    private final boolean webEnabled;
    private final int webPort;
    private final String webUrl;
    private final String webBind;
    private final int webThreadPoolSize;
    private final int webSessionExpiryHours;
    private final int webOneTimeExpiryMinutes;
    private final int webLoginMaxAttempts;
    private final int webLoginLockMinutes;
    private final String webMapUrl;
    private final List<String> webAllowedOrigins;

    private final Map<String, String> worldMapping;

    public PluginConfig(FileConfiguration raw) {
        this.configVersion = raw.getInt("config-version", 1);
        this.dayDefinition = parseDayDefinition(raw.getString("time.day-definition", "REAL"));

        this.townCreationCost = nonNegative(raw, "town.creation-cost", 1000.0);
        this.claimCost = nonNegative(raw, "town.claim-cost", 50.0);
        this.upkeepPerBlock = nonNegative(raw, "town.upkeep-per-block", 1.0);
        this.baseClaimBlocks = nonNegativeInt(raw, "town.base-claim-blocks", 16);
        this.claimBlocksPerResident = nonNegativeInt(raw, "town.claim-blocks-per-resident", 8);
        this.maxTownBlocks = nonNegativeInt(raw, "town.max-town-blocks", 1024);
        this.bankruptcyDays = nonNegativeInt(raw, "town.bankruptcy-days", 7);
        this.abandonedDays = nonNegativeInt(raw, "town.abandoned-days", 14);
        this.abandonedDissolveDays = nonNegativeInt(raw, "town.abandoned-dissolve-days", 7);

        this.nationCreationCost = nonNegative(raw, "nation.creation-cost", 100000.0);
        this.nationMaxTowns = nonNegativeInt(raw, "nation.max-towns", 50);

        this.shopPlotDailyIncome = nonNegative(raw, "plot.shop-daily-income", 5.0);
        this.farmPlotUpkeepDiscount = nonNegative(raw, "plot.farm-upkeep-discount", 0.5);
        this.bankPlotInterestRate = nonNegative(raw, "plot.bank-interest-rate", 0.001);
        this.innPlotDailyIncome = nonNegative(raw, "plot.inn-daily-income", 2.0);
        this.arenaPlotDailyIncome = nonNegative(raw, "plot.arena-daily-income", 1.0);
        this.fortPlotDefenseBonus = nonNegative(raw, "plot.fort-defense-bonus", 0.05);
        this.fortPlotUpkeepExtra = nonNegative(raw, "plot.fort-upkeep-extra", 2.0);
        this.embassyPlotUpkeep = nonNegative(raw, "plot.embassy-upkeep", 3.0);

        this.startingBalance = nonNegative(raw, "economy.starting-balance", 100.0);
        this.maxDailyTax = nonNegative(raw, "economy.max-daily-tax", 100.0);
        this.currencyName = nonBlank(raw.getString("economy.currency-name"), "Geo");
        this.physicalCurrencyEnabled = raw.getBoolean("physical-currency.enabled", true);
        this.physicalCurrencyFeeRate = bounded(raw, "physical-currency.fee-rate", 0.02, 0.0, 1.0);
        this.physicalCurrencyMaxIssued = Math.max(1,
                nonNegativeInt(raw, "physical-currency.max-issued-per-denomination", 1_000_000));
        this.autoSaveIntervalSeconds = Math.max(30, nonNegativeInt(raw, "economy.auto-save-interval-seconds", 300));

        this.warEnabled = raw.getBoolean("war.enabled", true);
        this.warDeclareCost = nonNegative(raw, "war.declare-cost", 2000.0);
        this.campaignCapturePoints = Math.max(1, nonNegativeInt(raw, "war.campaign.capture-points", 100));
        this.campaignPointsPerKill = nonNegativeInt(raw, "war.campaign.points-per-kill", 20);
        this.campaignPointsPerBreak = nonNegativeInt(raw, "war.campaign.points-per-break", 5);
        this.campaignDurationMinutes = Math.max(1, nonNegativeInt(raw, "war.campaign.duration-minutes", 120));
        this.exileDays = nonNegativeInt(raw, "war.campaign.exile-days", 7);
        this.campaignCost = nonNegative(raw, "war.campaign.cost", 5000.0);
        this.campaignCaptureCountdownSeconds = Math.max(1, nonNegativeInt(raw, "war.campaign.capture-countdown-seconds", 900));
        this.campaignFailCountdownSeconds = Math.max(1, nonNegativeInt(raw, "war.campaign.fail-countdown-seconds", 300));
        this.campaignBaseRespawnSeconds = Math.max(1, nonNegativeInt(raw, "war.campaign.base-respawn-seconds", 10));
        this.campaignRespawnIncreasePercent = bounded(raw, "war.campaign.respawn-increase-percent", 0.10, 0.0, 10.0);
        this.campaignMaxDefenderDeaths = Math.max(1, nonNegativeInt(raw, "war.campaign.max-defender-deaths", 15));
        this.campaignDefenderRatio = bounded(raw, "war.campaign.defender-ratio", 0.40, 0.0, 10.0);
        this.campaignCompensationPercent = bounded(raw, "war.campaign.compensation-percent", 0.70, 0.0, 1.0);
        this.campaignMaxResidentContributionPercent = bounded(
                raw, "war.campaign.max-resident-contribution-percent", 0.30, 0.0, 1.0);
        this.campaignMaxPerNation = Math.max(1, nonNegativeInt(raw, "war.campaign.max-per-nation", 1));

        this.killDropEnabled = raw.getBoolean("war.kill-drop.enabled", true);
        this.warKillDropPercent = bounded(raw, "war.kill-drop.war-percent", 0.10, 0.0, 1.0);
        this.warKillDropDecayFactor = bounded(raw, "war.kill-drop.war-decay-factor", 0.70, 0.0, 1.0);
        this.killDropBaseChance = bounded(raw, "war.kill-drop.base-chance", 0.20, 0.0, 1.0);
        this.killDropBaseAmount = nonNegative(raw, "war.kill-drop.base-amount", 10.0);
        this.killDropDecayFactor = bounded(raw, "war.kill-drop.decay-factor", 0.70, 0.0, 1.0);

        this.festivalEnabled = raw.getBoolean("festival.enabled", true);
        this.festivalTaxMultiplier = bounded(raw, "festival.tax-multiplier", 0.5, 0.0, 10.0);
        this.festivalInfluenceMultiplier = bounded(raw, "festival.influence-multiplier", 2.0, 0.0, 10.0);
        this.festivalShopPriceMultiplier = bounded(raw, "festival.shop-price-multiplier", 0.9, 0.0, 10.0);
        this.festivalWeekend = raw.getBoolean("festival.weekend", false);
        this.festivalCustom = parseStringMap(raw, "festival.custom");

        this.influenceEnabled = raw.getBoolean("influence.enabled", true);
        this.influenceDailyGrowth = nonNegative(raw, "influence.daily-growth", 1.0);
        this.maxTownInfluence = nonNegative(raw, "influence.max-town", 100_000.0);

        this.shopEnabled = raw.getBoolean("market.shop-enabled", true);
        this.freeMarketEnabled = raw.getBoolean("market.free-market-enabled", true);
        this.shopTradeStep = Math.max(1, nonNegativeInt(raw, "market.shop.trade-step", 1));
        this.shopPriceElasticity = bounded(raw, "market.shop.price-elasticity", 0.02, 0.0, 10.0);
        this.shopPriceCeilingMultiplier = bounded(raw, "market.shop.price-ceiling-multiplier", 5.0, 1.0, 100.0);
        this.shopPriceFloorMultiplier = bounded(raw, "market.shop.price-floor-multiplier", 0.1, 0.01, 1.0);
        this.shopVolumeDecay = bounded(raw, "market.shop.volume-decay", 0.98, 0.0, 1.0);
        this.shopDailyRandomRange = bounded(raw, "market.shop.daily-random-range", 0.08, 0.0, 0.5);
        this.shopSellBuyRatio = bounded(raw, "market.shop.sell-buy-ratio", 0.85, 0.01, 0.99);
        this.shopPlayerDailyBuyLimit = nonNegativeInt(raw, "market.shop.player-daily-buy-limit", 512);
        this.shopPlayerDailySellLimit = nonNegativeInt(raw, "market.shop.player-daily-sell-limit", 512);
        this.shopServerDailyBuyLimit = nonNegativeInt(raw, "market.shop.server-daily-buy-limit", 8192);
        this.shopServerDailySellLimit = nonNegativeInt(raw, "market.shop.server-daily-sell-limit", 8192);

        this.freeMarketTax = bounded(raw, "market.free-market.tax", 0.0, 0.0, 1.0);
        this.freeMarketMaxListingsPerPlayer = nonNegativeInt(raw, "market.free-market.max-listings-per-player", 20);
        this.freeMarketMinPrice = nonNegative(raw, "market.free-market.min-price", 0.01);
        this.freeMarketMaxPrice = nonNegative(raw, "market.free-market.max-price", 1_000_000.0);
        this.freeMarketExpireDays = nonNegativeInt(raw, "market.free-market.expire-days", 0);

        this.checkInEnabled = raw.getBoolean("checkin.enabled", true);
        this.checkInBaseReward = nonNegative(raw, "checkin.base-reward", 5.0);
        this.checkInCycleDays = Math.max(1, nonNegativeInt(raw, "checkin.cycle-days", 7));
        this.checkInStreakRewards = parseStreakRewards(raw);

        this.redeemEnabled = raw.getBoolean("redeem.enabled", true);

        this.protectBlockBreak = raw.getBoolean("protection.block-break", true);
        this.protectBlockPlace = raw.getBoolean("protection.block-place", true);
        this.protectInteract = raw.getBoolean("protection.interact", true);
        this.protectPvp = raw.getBoolean("protection.pvp", true);
        this.wildernessPvp = raw.getBoolean("protection.wilderness-pvp", true);
        this.allowForeignerActions = raw.getBoolean("protection.allow-foreigner-actions", true);

        this.webEnabled = raw.getBoolean("web.enabled", true);
        this.webPort = clampPort(raw.getInt("web.port", 8080));
        this.webUrl = raw.getString("web.url", "");
        this.webBind = nonBlank(raw.getString("web.bind"), "127.0.0.1");
        this.webThreadPoolSize = Math.max(1, nonNegativeInt(raw, "web.thread-pool-size", 4));
        this.webSessionExpiryHours = Math.max(1, nonNegativeInt(raw, "web.session-expiry-hours", 720));
        this.webOneTimeExpiryMinutes = Math.max(1, nonNegativeInt(raw, "web.one-time-expiry-minutes", 10));
        this.webLoginMaxAttempts = Math.max(1, nonNegativeInt(raw, "web.login-max-attempts", 5));
        this.webLoginLockMinutes = Math.max(1, nonNegativeInt(raw, "web.login-lock-minutes", 5));
        this.webMapUrl = raw.getString("web.map-url", "");
        this.webAllowedOrigins = List.copyOf(raw.getStringList("web.allowed-origins"));

        this.worldMapping = parseWorldMapping(raw);
    }

    private static DayDefinition parseDayDefinition(String value) {
        try {
            DayDefinition parsed = DayDefinition.valueOf(String.valueOf(value).trim().toUpperCase());
            if (parsed == DayDefinition.MINECRAFT) {
                Log.warn("time.day-definition=MINECRAFT 为实验选项，正式部署建议使用 REAL。");
            }
            return parsed;
        } catch (IllegalArgumentException e) {
            Log.warn("无效的 time.day-definition: {}，回退为 REAL。", value);
            return DayDefinition.REAL;
        }
    }

    private static Map<String, String> parseWorldMapping(FileConfiguration raw) {
        Map<String, String> mapping = new LinkedHashMap<>();
        var section = raw.getConfigurationSection("world-mapping");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null && !value.isBlank()) {
                    mapping.put(key, value.trim());
                }
            }
        }
        if (mapping.isEmpty()) {
            mapping.put("minecraft:overworld", "world");
            mapping.put("minecraft:the_nether", "world_nether");
            mapping.put("minecraft:the_end", "world_the_end");
            Log.warn("world-mapping 为空，使用默认世界映射。");
        }
        return Collections.unmodifiableMap(mapping);
    }

    private static Map<String, String> parseStringMap(FileConfiguration raw, String path) {
        Map<String, String> values = new LinkedHashMap<>();
        var section = raw.getConfigurationSection(path);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null && !value.isBlank()) {
                    values.put(key, value.trim());
                }
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private static double nonNegative(FileConfiguration raw, String path, double fallback) {
        double value = raw.getDouble(path, fallback);
        if (!Double.isFinite(value) || value < 0) {
            Log.warn("配置项 {} 非法（{}），回退为 {}。", path, value, fallback);
            return fallback;
        }
        return value;
    }

    private static int nonNegativeInt(FileConfiguration raw, String path, int fallback) {
        int value = raw.getInt(path, fallback);
        if (value < 0) {
            Log.warn("配置项 {} 非法（{}），回退为 {}。", path, value, fallback);
            return fallback;
        }
        return value;
    }

    private static double bounded(FileConfiguration raw, String path, double fallback, double min, double max) {
        double value = raw.getDouble(path, fallback);
        if (!Double.isFinite(value) || value < min || value > max) {
            Log.warn("配置项 {} 非法（{}），回退为 {}。", path, value, fallback);
            return fallback;
        }
        return value;
    }

    private static double[] parseStreakRewards(FileConfiguration raw) {
        java.util.List<Double> values = raw.getDoubleList("checkin.streak-rewards");
        if (values == null || values.isEmpty()) {
            return new double[]{50.0, 80.0, 120.0, 180.0, 250.0, 350.0, 500.0};
        }
        java.util.List<Double> valid = values.stream()
                .filter(value -> value != null && Double.isFinite(value) && value >= 0)
                .toList();
        if (valid.isEmpty()) {
            return new double[]{50.0, 80.0, 120.0, 180.0, 250.0, 350.0, 500.0};
        }
        double[] result = new double[valid.size()];
        for (int i = 0; i < valid.size(); i++) {
            result[i] = valid.get(i);
        }
        return result;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int clampPort(int port) {
        if (port < 1 || port > 65535) {
            Log.warn("web.port 非法（{}），回退为 8080。", port);
            return 8080;
        }
        return port;
    }

    public int configVersion() {
        return configVersion;
    }

    public DayDefinition dayDefinition() {
        return dayDefinition;
    }

    public double townCreationCost() {
        return townCreationCost;
    }

    public double claimCost() {
        return claimCost;
    }

    public double upkeepPerBlock() {
        return upkeepPerBlock;
    }

    public int baseClaimBlocks() {
        return baseClaimBlocks;
    }

    public int claimBlocksPerResident() {
        return claimBlocksPerResident;
    }

    public int maxTownBlocks() {
        return maxTownBlocks;
    }

    public int bankruptcyDays() {
        return bankruptcyDays;
    }

    public int abandonedDays() {
        return abandonedDays;
    }

    public int abandonedDissolveDays() {
        return abandonedDissolveDays;
    }

    public double nationCreationCost() {
        return nationCreationCost;
    }

    public int nationMaxTowns() {
        return nationMaxTowns;
    }

    public double shopPlotDailyIncome() {
        return shopPlotDailyIncome;
    }

    public double farmPlotUpkeepDiscount() {
        return farmPlotUpkeepDiscount;
    }

    public double bankPlotInterestRate() {
        return bankPlotInterestRate;
    }

    public double innPlotDailyIncome() {
        return innPlotDailyIncome;
    }

    public double arenaPlotDailyIncome() {
        return arenaPlotDailyIncome;
    }

    public double fortPlotDefenseBonus() {
        return fortPlotDefenseBonus;
    }

    public double fortPlotUpkeepExtra() {
        return fortPlotUpkeepExtra;
    }

    public double embassyPlotUpkeep() {
        return embassyPlotUpkeep;
    }

    public double startingBalance() {
        return startingBalance;
    }

    public double maxDailyTax() {
        return maxDailyTax;
    }

    public String currencyName() {
        return currencyName;
    }

    public boolean physicalCurrencyEnabled() {
        return physicalCurrencyEnabled;
    }

    public double physicalCurrencyFeeRate() {
        return physicalCurrencyFeeRate;
    }

    public int physicalCurrencyMaxIssued() {
        return physicalCurrencyMaxIssued;
    }

    public int autoSaveIntervalSeconds() {
        return autoSaveIntervalSeconds;
    }

    public boolean warEnabled() {
        return warEnabled;
    }

    public double warDeclareCost() {
        return warDeclareCost;
    }

    public int campaignCapturePoints() {
        return campaignCapturePoints;
    }

    public int campaignPointsPerKill() {
        return campaignPointsPerKill;
    }

    public int campaignPointsPerBreak() {
        return campaignPointsPerBreak;
    }

    public int campaignDurationMinutes() {
        return campaignDurationMinutes;
    }

    public int exileDays() {
        return exileDays;
    }

    public double campaignCost() {
        return campaignCost;
    }

    public int campaignCaptureCountdownSeconds() {
        return campaignCaptureCountdownSeconds;
    }

    public int campaignFailCountdownSeconds() {
        return campaignFailCountdownSeconds;
    }

    public int campaignBaseRespawnSeconds() {
        return campaignBaseRespawnSeconds;
    }

    public double campaignRespawnIncreasePercent() {
        return campaignRespawnIncreasePercent;
    }

    public int campaignMaxDefenderDeaths() {
        return campaignMaxDefenderDeaths;
    }

    public double campaignDefenderRatio() {
        return campaignDefenderRatio;
    }

    public double campaignCompensationPercent() {
        return campaignCompensationPercent;
    }

    public double campaignMaxResidentContributionPercent() {
        return campaignMaxResidentContributionPercent;
    }

    public int campaignMaxPerNation() {
        return campaignMaxPerNation;
    }

    public boolean killDropEnabled() {
        return killDropEnabled;
    }

    public double warKillDropPercent() {
        return warKillDropPercent;
    }

    public double warKillDropDecayFactor() {
        return warKillDropDecayFactor;
    }

    public double killDropBaseChance() {
        return killDropBaseChance;
    }

    public double killDropBaseAmount() {
        return killDropBaseAmount;
    }

    public double killDropDecayFactor() {
        return killDropDecayFactor;
    }

    public boolean festivalEnabled() {
        return festivalEnabled;
    }

    public double festivalTaxMultiplier() {
        return festivalTaxMultiplier;
    }

    public double festivalInfluenceMultiplier() {
        return festivalInfluenceMultiplier;
    }

    public double festivalShopPriceMultiplier() {
        return festivalShopPriceMultiplier;
    }

    public boolean festivalWeekend() {
        return festivalWeekend;
    }

    public Map<String, String> festivalCustom() {
        return festivalCustom;
    }

    public boolean influenceEnabled() {
        return influenceEnabled;
    }

    public double influenceDailyGrowth() {
        return influenceDailyGrowth;
    }

    public double maxTownInfluence() {
        return maxTownInfluence;
    }

    public boolean shopEnabled() {
        return shopEnabled;
    }

    public boolean freeMarketEnabled() {
        return freeMarketEnabled;
    }

    public int shopTradeStep() {
        return shopTradeStep;
    }

    public double shopPriceElasticity() {
        return shopPriceElasticity;
    }

    public double shopPriceCeilingMultiplier() {
        return shopPriceCeilingMultiplier;
    }

    public double shopPriceFloorMultiplier() {
        return shopPriceFloorMultiplier;
    }

    public double shopVolumeDecay() {
        return shopVolumeDecay;
    }

    public double shopDailyRandomRange() {
        return shopDailyRandomRange;
    }

    public double shopSellBuyRatio() {
        return shopSellBuyRatio;
    }

    public int shopPlayerDailyBuyLimit() {
        return shopPlayerDailyBuyLimit;
    }

    public int shopPlayerDailySellLimit() {
        return shopPlayerDailySellLimit;
    }

    public int shopServerDailyBuyLimit() {
        return shopServerDailyBuyLimit;
    }

    public int shopServerDailySellLimit() {
        return shopServerDailySellLimit;
    }

    public double freeMarketTax() {
        return freeMarketTax;
    }

    public int freeMarketMaxListingsPerPlayer() {
        return freeMarketMaxListingsPerPlayer;
    }

    public double freeMarketMinPrice() {
        return freeMarketMinPrice;
    }

    public double freeMarketMaxPrice() {
        return freeMarketMaxPrice;
    }

    public int freeMarketExpireDays() {
        return freeMarketExpireDays;
    }

    public boolean checkInEnabled() {
        return checkInEnabled;
    }

    public double checkInBaseReward() {
        return checkInBaseReward;
    }

    public int checkInCycleDays() {
        return checkInCycleDays;
    }

    public double[] checkInStreakRewards() {
        return checkInStreakRewards.clone();
    }

    public boolean redeemEnabled() {
        return redeemEnabled;
    }

    public boolean protectBlockBreak() {
        return protectBlockBreak;
    }

    public boolean protectBlockPlace() {
        return protectBlockPlace;
    }

    public boolean protectInteract() {
        return protectInteract;
    }

    public boolean protectPvp() {
        return protectPvp;
    }

    public boolean wildernessPvp() {
        return wildernessPvp;
    }

    public boolean allowForeignerActions() {
        return allowForeignerActions;
    }

    public boolean webEnabled() {
        return webEnabled;
    }

    public int webPort() {
        return webPort;
    }

    public String webUrl() {
        return webUrl;
    }

    public String webBind() {
        return webBind;
    }

    public int webThreadPoolSize() {
        return webThreadPoolSize;
    }

    public int webSessionExpiryHours() {
        return webSessionExpiryHours;
    }

    public int webOneTimeExpiryMinutes() {
        return webOneTimeExpiryMinutes;
    }

    public int webLoginMaxAttempts() {
        return webLoginMaxAttempts;
    }

    public int webLoginLockMinutes() {
        return webLoginLockMinutes;
    }

    public String webMapUrl() {
        return webMapUrl;
    }

    public List<String> webAllowedOrigins() {
        return webAllowedOrigins;
    }

    public Map<String, String> worldMapping() {
        return worldMapping;
    }
}