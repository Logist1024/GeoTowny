package com.geotowny.service;

import com.geotowny.Log;
import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.Town;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InfluenceService {

    private final GeoTownyCore core;
    private final PluginConfig config;
    private final FestivalService festival;

    public InfluenceService(GeoTownyCore core, PluginConfig config, FestivalService festival) {
        this.core = core;
        this.config = config;
        this.festival = festival;
    }

    public double applyDailyGrowth() {
        if (!config.influenceEnabled()) {
            return 0.0d;
        }
        double base = config.influenceDailyGrowth();
        double festivalMultiplier = festival == null ? 1.0d : festival.getInfluenceMultiplier();
        Map<String, Double> townGrowth = new HashMap<>();
        double total = 0.0d;

        for (Town town : new ArrayList<>(core.allTowns())) {
            double growth = (base * (1.0d + town.getResidentCount() / 10.0d)
                    + town.getClaimCount() * 0.1d) * festivalMultiplier;
            town.addInfluence(growth);
            if (town.getInfluence() > config.maxTownInfluence()) {
                town.setInfluence(config.maxTownInfluence());
            }
            townGrowth.put(town.getUuid(), growth);
            core.saveTown(town);
        }

        for (Nation nation : new ArrayList<>(core.allNations())) {
            double dailyTownGrowth = 0.0d;
            for (String townUuid : nation.getTownUuids()) {
                Double growth = townGrowth.get(townUuid);
                if (growth != null) {
                    dailyTownGrowth += growth;
                }
            }
            dailyTownGrowth *= nation.getIdeology().getStabilityMod();
            int techCap = nation.getIdeology().getTechLevelCap();
            int effectiveTech = Math.min(nation.getTechLevel(), techCap);
            double dailyGrowth = dailyTownGrowth + effectiveTech * 10.0d;
            nation.setInfluence(nation.getInfluence() + dailyGrowth);
            double regen = (50.0d - nation.getStability()) * 0.05d * nation.getIdeology().getStabilityMod();
            nation.setStability(nation.getStability() + regen);
            total += dailyGrowth;
            core.saveNation(nation);
        }

        Log.info("影响力日结算完成：国家总增量 {}。", String.format("%.2f", total));
        return total;
    }

    public List<Nation> rankNations() {
        List<Nation> nations = new ArrayList<>(core.allNations());
        nations.sort(Comparator.comparingDouble(Nation::getInfluence).reversed());
        return nations;
    }

    public boolean spendInfluence(Nation nation, double amount) {
        if (nation == null || amount < 0 || nation.getInfluence() < amount) {
            return false;
        }
        nation.setInfluence(nation.getInfluence() - amount);
        core.saveNation(nation);
        return true;
    }
}