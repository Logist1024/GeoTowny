package com.geotowny.service;

import com.geotowny.Log;
import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Ideology;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.NationRank;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.util.Result;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class IdeologyService {

    private final GeoTownyCore core;
    private final PluginConfig config;
    private final Map<String, Set<String>> impeachmentVoters = new ConcurrentHashMap<>();

    public IdeologyService(GeoTownyCore core, PluginConfig config) {
        this.core = core;
        this.config = config;
    }

    public boolean isCoreIdeology(Ideology ideology) {
        return ideology == Ideology.DEMOCRACY
                || ideology == Ideology.EMPIRE
                || ideology == Ideology.FEDERATION
                || ideology == Ideology.THEOCRACY;
    }

    public String cosmeticWarning(Ideology ideology) {
        return isCoreIdeology(ideology) ? null
                : ideology.getDisplayName() + " 仅保留称呼与数值倍率，专属机制不迁移。";
    }

    public boolean canFreeLeave(Town town) {
        if (town == null || !town.hasNation()) {
            return false;
        }
        Nation nation = core.getNation(town.getNationUuid());
        return nation != null && nation.getIdeology().isFreeLeave();
    }

    public int techCap(Nation nation) {
        return nation == null ? 99 : nation.getIdeology().getTechLevelCap();
    }

    public double provinceTaxBonus(Town town) {
        if (town == null || !town.hasNation()) {
            return 0.0d;
        }
        Nation nation = core.getNation(town.getNationUuid());
        if (nation == null || !nation.getIdeology().isProvinceSystem()) {
            return 0.0d;
        }
        return nation.isProvinceTown(town.getUuid()) ? nation.getIdeology().getProvinceTaxBonus() : 0.0d;
    }

    public double holyWarBonus(Nation attacker, Nation defender) {
        if (attacker == null || defender == null
                || attacker.getIdeology() != Ideology.THEOCRACY
                || defender.getIdeology() == Ideology.THEOCRACY) {
            return 0.0d;
        }
        return 0.2d;
    }

    public double warEfficiency(Nation nation, Nation enemy) {
        if (nation == null) {
            return 1.0d;
        }
        return nation.getIdeology().getWarMod() + holyWarBonus(nation, enemy);
    }

    public Result startImpeachment(Resident initiator, Nation nation) {
        if (nation == null || initiator == null) {
            return Result.fail("参数无效。");
        }
        if (!nation.getIdeology().isImpeachment()) {
            return Result.fail("当前国体不支持弹劾。");
        }
        long[] vote = nation.getImpeachmentVote();
        if (vote != null && System.currentTimeMillis() < vote[0] + 24L * 60L * 60L * 1000L) {
            return Result.fail("已有进行中的弹劾投票。");
        }
        nation.setImpeachmentVote(new long[]{System.currentTimeMillis(), 0L, 0L});
        impeachmentVoters.remove(nation.getUuid());
        core.saveNation(nation);
        return Result.ok("已发起弹劾投票，24 小时内需首都过半数成员赞成。");
    }

    public Result castImpeachmentVote(Resident voter, Nation nation, boolean approve) {
        if (nation == null || voter == null) {
            return Result.fail("参数无效。");
        }
        long[] vote = nation.getImpeachmentVote();
        if (vote == null) {
            return Result.fail("无进行中的弹劾投票。");
        }
        if (System.currentTimeMillis() > vote[0] + 24L * 60L * 60L * 1000L) {
            nation.setImpeachmentVote(null);
            impeachmentVoters.remove(nation.getUuid());
            core.saveNation(nation);
            return Result.fail("弹劾投票已截止。");
        }
        Set<String> voters = impeachmentVoters.computeIfAbsent(nation.getUuid(), key -> new HashSet<>());
        if (!voters.add(voter.getUuidString())) {
            return Result.fail("你已经投过票了。");
        }
        if (approve) {
            vote[1]++;
        } else {
            vote[2]++;
        }

        Town capital = core.getTown(nation.getCapitalTownUuid());
        int members = capital == null ? 0 : capital.getResidentCount();
        int majority = members / 2 + 1;
        if (vote[1] >= majority) {
            Resident king = nation.getKingUuid() == null ? null : core.getResident(nation.getKingUuid());
            if (king != null) {
                king.setNationRank(NationRank.CITIZEN);
                core.saveResident(king);
            }
            nation.setKingUuid(null);
            nation.setImpeachmentVote(null);
            impeachmentVoters.remove(nation.getUuid());
            core.saveNation(nation);
            return Result.ok("弹劾通过，领袖已退位。");
        }
        core.saveNation(nation);
        return Result.ok("投票已记录：赞成 " + vote[1] + " / 反对 " + vote[2]
                + "（需 " + majority + " 票赞成）。");
    }

    public void dailyTick() {
        for (Nation nation : core.allNations()) {
            if (nation.getIdeology().isTermLimit()
                    && nation.getTermStart() > 0
                    && System.currentTimeMillis() > nation.getTermStart() + nation.getTermDuration()) {
                Resident king = nation.getKingUuid() == null ? null : core.getResident(nation.getKingUuid());
                if (king != null) {
                    king.setNationRank(NationRank.CITIZEN);
                    core.saveResident(king);
                }
                nation.setKingUuid(null);
                nation.setTermStart(0L);
                core.saveNation(nation);
                Log.info("国家 {} 的领袖任期已满，自动退位。", nation.getName());
            }
        }
    }

    public int onlineNationMembers(Nation nation) {
        if (nation == null) {
            return 0;
        }
        int count = 0;
        for (var player : org.bukkit.Bukkit.getOnlinePlayers()) {
            Resident resident = core.getResident(player.getUniqueId());
            if (resident == null || !resident.hasTown()) {
                continue;
            }
            Town town = core.getTown(resident.getTownUuid());
            if (town != null && town.hasNation() && town.getNationUuid().equals(nation.getUuid())) {
                count++;
            }
        }
        return count;
    }

    public void cleanupVotesForNation(String nationUuid) {
        if (nationUuid != null) {
            impeachmentVoters.remove(nationUuid);
        }
    }
}