package com.geotowny.domain.model;

/**
 * 国家意识形态。
 * <p>按决策 D-05 收敛：保留民主制、帝国制、联邦制、神权制的核心机制；
 * 君主制、共和制、集体制、部落制仅保留称呼与数值倍率。
 */
public enum Ideology {

    DEMOCRACY("民主制", "总统", "副总统",
            1.0, 0.8, 1.1,
            1.0, 1.0, 1.0, 1.0,
            true, true, true,
            false, false, false,
            false, false, false,
            0.0, 99),

    MONARCHY("君主制", "国王", "王储",
            0.9, 1.0, 1.0,
            1.0, 1.0, 1.0, 1.0,
            false, false, false,
            false, false, false,
            false, false, false,
            0.0, 99),

    REPUBLIC("共和制", "总理", "副总理",
            0.95, 1.0, 1.15,
            1.0, 1.0, 1.0, 1.0,
            false, false, false,
            false, false, false,
            false, false, false,
            0.0, 99),

    FEDERATION("联邦制", "联邦主席", "副主席",
            1.1, 0.9, 1.05,
            1.0, 1.0, 1.0, 2.0,
            false, false, false,
            false, true, true,
            false, false, false,
            0.0, 99),

    EMPIRE("帝国制", "皇帝", "皇储",
            0.7, 1.3, 1.0,
            1.0, 1.0, 1.0, 1.0,
            false, false, false,
            true, false, false,
            false, false, false,
            0.5, 99),

    COLLECTIVIST("集体制", "总书记", "副总书记",
            0.8, 0.9, 1.2,
            1.0, 1.0, 1.0, 1.0,
            false, false, false,
            false, false, false,
            false, false, false,
            0.0, 8),

    TRIBAL("部落制", "大酋长", "副酋长",
            0.85, 1.1, 0.95,
            0.7, 0.7, 0.8, 0.75,
            false, false, false,
            false, false, false,
            false, false, false,
            0.0, 99),

    THEOCRACY("神权制", "教皇", "枢机主教",
            1.05, 0.95, 1.0,
            1.0, 1.0, 1.0, 1.0,
            false, false, false,
            false, false, false,
            true, true, true,
            0.0, 99);

    private final String displayName;
    private final String leaderTitle;
    private final String deputyTitle;

    private final double stabilityMod;
    private final double warMod;
    private final double economyMod;

    private final double nationCreationCostMod;
    private final double townCreationCostMod;
    private final double claimCostMod;
    private final double nationMaxTownsMod;

    private final boolean warRequiresVote;
    private final boolean impeachment;
    private final boolean termLimit;

    private final boolean provinceSystem;
    private final boolean freeLeave;
    private final boolean townAutonomy;

    private final boolean clergySystem;
    private final boolean holyWar;
    private final boolean heresyPenalty;

    private final double provinceTaxBonus;
    private final int techLevelCap;

    Ideology(String displayName, String leaderTitle, String deputyTitle,
             double stabilityMod, double warMod, double economyMod,
             double nationCreationCostMod, double townCreationCostMod, double claimCostMod, double nationMaxTownsMod,
             boolean warRequiresVote, boolean impeachment, boolean termLimit,
             boolean provinceSystem, boolean freeLeave, boolean townAutonomy,
             boolean clergySystem, boolean holyWar, boolean heresyPenalty,
             double provinceTaxBonus, int techLevelCap) {
        this.displayName = displayName;
        this.leaderTitle = leaderTitle;
        this.deputyTitle = deputyTitle;
        this.stabilityMod = stabilityMod;
        this.warMod = warMod;
        this.economyMod = economyMod;
        this.nationCreationCostMod = nationCreationCostMod;
        this.townCreationCostMod = townCreationCostMod;
        this.claimCostMod = claimCostMod;
        this.nationMaxTownsMod = nationMaxTownsMod;
        this.warRequiresVote = warRequiresVote;
        this.impeachment = impeachment;
        this.termLimit = termLimit;
        this.provinceSystem = provinceSystem;
        this.freeLeave = freeLeave;
        this.townAutonomy = townAutonomy;
        this.clergySystem = clergySystem;
        this.holyWar = holyWar;
        this.heresyPenalty = heresyPenalty;
        this.provinceTaxBonus = provinceTaxBonus;
        this.techLevelCap = techLevelCap;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLeaderTitle() {
        return leaderTitle;
    }

    public String getDeputyTitle() {
        return deputyTitle;
    }

    public double getStabilityMod() {
        return stabilityMod;
    }

    public double getWarMod() {
        return warMod;
    }

    public double getEconomyMod() {
        return economyMod;
    }

    public double getNationCreationCostMod() {
        return nationCreationCostMod;
    }

    public double getTownCreationCostMod() {
        return townCreationCostMod;
    }

    public double getClaimCostMod() {
        return claimCostMod;
    }

    public double getNationMaxTownsMod() {
        return nationMaxTownsMod;
    }

    public boolean isWarRequiresVote() {
        return warRequiresVote;
    }

    public boolean isImpeachment() {
        return impeachment;
    }

    public boolean isTermLimit() {
        return termLimit;
    }

    public boolean isProvinceSystem() {
        return provinceSystem;
    }

    public boolean isFreeLeave() {
        return freeLeave;
    }

    public boolean isTownAutonomy() {
        return townAutonomy;
    }

    public boolean isClergySystem() {
        return clergySystem;
    }

    public boolean isHolyWar() {
        return holyWar;
    }

    public boolean isHeresyPenalty() {
        return heresyPenalty;
    }

    public double getProvinceTaxBonus() {
        return provinceTaxBonus;
    }

    public int getTechLevelCap() {
        return techLevelCap;
    }
}