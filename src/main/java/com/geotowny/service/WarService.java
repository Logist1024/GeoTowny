package com.geotowny.service;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.Log;
import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Ideology;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.NationRank;
import com.geotowny.domain.model.RelationType;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
import com.geotowny.domain.model.TownRank;
import com.geotowny.domain.model.WarState;
import com.geotowny.domain.model.WorldLocation;
import com.geotowny.domain.money.Money;
import com.geotowny.domain.war.Campaign;
import com.geotowny.util.Result;
import com.geotowny.world.WorldRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战争服务：宣战/公投、Campaign（攻城与侵略合并）、占领、赔偿、流亡、复国、吞并。
 */
public final class WarService {

    private final GeoTownyPlugin plugin;
    private final GeoTownyCore core;
    private final PluginConfig config;
    private final WorldRegistry worlds;
    private final TownService townService;
    private final KillDropService killDrop;

    private final Map<String, Campaign> campaigns = new ConcurrentHashMap<>();
    private final Map<String, BossBar> attackerBars = new ConcurrentHashMap<>();
    private final Map<String, BossBar> defenderBars = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRespawn> pendingRespawns = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> warVoters = new ConcurrentHashMap<>();

    private BukkitTask task;
    private int tickCounter;

    public WarService(GeoTownyPlugin plugin, GeoTownyCore core, PluginConfig config,
                      WorldRegistry worlds, TownService townService, KillDropService killDrop) {
        this.plugin = plugin;
        this.core = core;
        this.config = config;
        this.worlds = worlds;
        this.townService = townService;
        this.killDrop = killDrop;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Campaign campaign : new ArrayList<>(campaigns.values())) {
            restoreSpectators(campaign);
            removeBossBars(campaign);
        }
        campaigns.clear();
        pendingRespawns.clear();
        warVoters.clear();
    }

    public Result declareWar(Resident actor, Nation attacker, Nation defender) {
        if (!config.warEnabled()) {
            return Result.fail("战争系统已禁用。");
        }
        if (actor == null || attacker == null || defender == null || attacker.getUuid().equals(defender.getUuid())) {
            return Result.fail("国家不存在或不能对自己宣战。");
        }
        if (!isKing(actor, attacker)) {
            return Result.fail("只有国王可以宣战。");
        }
        if (attacker.getRelation(defender.getUuid()) == RelationType.AT_WAR) {
            return Result.fail("你们已处于战争状态。");
        }
        if (attacker.getIdeology() == Ideology.DEMOCRACY) {
            long deadline = System.currentTimeMillis() + 24L * 60L * 60L * 1000L;
            attacker.getWarVotes().put(defender.getUuid(), new long[]{0L, 0L, deadline});
            core.saveNation(attacker);
            return Result.ok("已发起对 " + defender.getName()
                    + " 的宣战公投，24 小时内需过半数在线成员赞成。使用 /nation warvote <国家> yes|no 投票。");
        }
        return beginWar(attacker, defender);
    }

    public Result castWarVote(Resident voter, Nation nation, Nation target, boolean approve) {
        if (voter == null || nation == null || target == null) {
            return Result.fail("参数无效。");
        }
        if (!isMember(voter, nation)) {
            return Result.fail("你不属于该国家。");
        }
        long[] vote = nation.getWarVotes().get(target.getUuid());
        if (vote == null) {
            return Result.fail("未发起到 " + target.getName() + " 的宣战公投。");
        }
        if (System.currentTimeMillis() > vote[2]) {
            nation.getWarVotes().remove(target.getUuid());
            warVoters.remove(voteKey(nation, target));
            core.saveNation(nation);
            return Result.fail("公投已截止。");
        }
        Set<String> voters = warVoters.computeIfAbsent(voteKey(nation, target), key -> ConcurrentHashMap.newKeySet());
        if (!voters.add(voter.getUuidString())) {
            return Result.fail("你已经投过票了。");
        }
        if (approve) {
            vote[0]++;
        } else {
            vote[1]++;
        }
        int online = Math.max(1, onlineNationMembers(nation));
        int majority = online / 2 + 1;
        if (vote[0] >= majority) {
            nation.getWarVotes().remove(target.getUuid());
            warVoters.remove(voteKey(nation, target));
            core.saveNation(nation);
            return beginWar(nation, target);
        }
        core.saveNation(nation);
        return Result.ok("投票已记录：赞成 " + vote[0] + " / 反对 " + vote[1]
                + "（需 " + majority + " 票赞成）。");
    }

    private Result beginWar(Nation attacker, Nation defender) {
        Money cost = Money.of(config.warDeclareCost());
        if (!attacker.withdraw(cost)) {
            return Result.fail("国家国库不足支付宣战费 " + cost.format() + "。");
        }
        attacker.setRelation(defender.getUuid(), RelationType.AT_WAR);
        defender.setRelation(attacker.getUuid(), RelationType.AT_WAR);
        attacker.setWarState(WarState.ACTIVE);
        defender.setWarState(WarState.ACTIVE);
        attacker.setStability(attacker.getStability() - 10.0d);
        defender.setStability(defender.getStability() - 5.0d);
        core.saveNation(attacker);
        core.saveNation(defender);
        broadcastNation(attacker, ChatColor.RED + "【战争】" + attacker.getName()
                + " 向 " + defender.getName() + " 宣战！");
        broadcastNation(defender, ChatColor.RED + "【战争】" + attacker.getName()
                + " 向 " + defender.getName() + " 宣战！");
        return Result.ok(attacker.getName() + " 向 " + defender.getName()
                + " 宣战，消耗 " + cost.format() + "。");
    }

    public Result makePeace(Resident actor, Nation nation, Nation target) {
        if (!isKing(actor, nation)) {
            return Result.fail("只有国王可以议和。");
        }
        return makePeaceInternal(nation, target);
    }

    private Result makePeaceInternal(Nation nation, Nation target) {
        if (nation == null || target == null
                || nation.getRelation(target.getUuid()) != RelationType.AT_WAR) {
            return Result.fail("双方未处于战争状态。");
        }
        cancelCampaignsBetween(nation, target);
        nation.setRelation(target.getUuid(), RelationType.NEUTRAL);
        target.setRelation(nation.getUuid(), RelationType.NEUTRAL);
        nation.setWarState(WarState.NONE);
        target.setWarState(WarState.NONE);
        nation.setStability(nation.getStability() + 5.0d);
        target.setStability(target.getStability() + 5.0d);
        core.saveNation(nation);
        core.saveNation(target);
        broadcastNation(nation, ChatColor.YELLOW + "【和平】" + nation.getName()
                + " 与 " + target.getName() + " 缔结和约。");
        broadcastNation(target, ChatColor.YELLOW + "【和平】" + nation.getName()
                + " 与 " + target.getName() + " 缔结和约。");
        return Result.ok(nation.getName() + " 与 " + target.getName() + " 已停战。");
    }

    public Result surrender(Resident actor, Nation loser, Nation winner, Town cedeTown, Money reparations) {
        if (!isKing(actor, loser)) {
            return Result.fail("只有国王可以投降。");
        }
        if (winner == null || loser.getRelation(winner.getUuid()) != RelationType.AT_WAR) {
            return Result.fail("双方未处于战争状态。");
        }
        String summary = "";
        if (cedeTown != null) {
            if (!loser.getUuid().equals(cedeTown.getNationUuid())) {
                return Result.fail("城镇 " + cedeTown.getName() + " 不属于你的国家。");
            }
            transferTown(cedeTown, loser, winner);
            summary += cedeTown.getName() + " 已割让给 " + winner.getName() + "。";
        }
        if (reparations != null && reparations.isPositive()) {
            if (!loser.withdraw(reparations)) {
                return Result.fail("国库不足以支付赔款 " + reparations.format() + "。");
            }
            winner.deposit(reparations);
            core.saveNation(loser);
            core.saveNation(winner);
            summary += " 赔款 " + reparations.format() + " " + config.currencyName() + "。";
        }
        makePeaceInternal(loser, winner);
        return Result.ok(loser.getName() + " 向 " + winner.getName() + " 投降。" + summary);
    }

    public Result startCampaign(Player player, Resident actor, Town target, Campaign.Type type) {
        if (!config.warEnabled()) {
            return Result.fail("战争系统已禁用。");
        }
        if (player == null || actor == null || target == null) {
            return Result.fail("参数无效。");
        }
        Nation attackerNation = nationOf(actor);
        if (attackerNation == null) {
            return Result.fail("你不在任何国家中。");
        }
        if (!isKing(actor, attackerNation) && actor.getNationRank() != NationRank.GENERAL) {
            return Result.fail("只有国王或将军可以发动战役。");
        }
        if (!target.hasNation()) {
            return Result.fail("目标城镇无所属国家。");
        }
        Nation defenderNation = core.getNation(target.getNationUuid());
        if (defenderNation == null) {
            return Result.fail("目标国家不存在。");
        }
        if (attackerNation.getRelation(defenderNation.getUuid()) != RelationType.AT_WAR) {
            return Result.fail("双方未处于战争状态，无法发动战役。");
        }
        if (findCampaignByTown(target.getUuid()) != null) {
            return Result.fail("该城镇已有进行中的战役。");
        }
        long active = campaigns.values().stream()
                .filter(campaign -> campaign.getAttackerNationUuid().equals(attackerNation.getUuid())
                        && campaign.isActive())
                .count();
        if (active >= config.campaignMaxPerNation()) {
            return Result.fail("你国已有进行中的战役。");
        }
        if (type == Campaign.Type.INVASION && !isInTerritory(player, target)) {
            return Result.fail("发动侵略必须位于 " + target.getName() + " 的领地内。");
        }
        if (type == Campaign.Type.INVASION) {
            int onlineAttackers = onlineNationMembers(attackerNation);
            int onlineDefenders = onlineTownMembers(target);
            int required = (int) Math.ceil(onlineAttackers * config.campaignDefenderRatio());
            if (onlineDefenders < required) {
                return Result.fail("防守方在线人数不足（" + onlineDefenders + "/" + onlineAttackers
                        + "），需要至少 " + required + " 人。");
            }
        }

        Money cost = Money.of(config.campaignCost());
        if (!attackerNation.withdraw(cost)) {
            return Result.fail("国库不足支付战役费用 " + cost.format() + "。");
        }
        WorldLocation attackerSpawn = findAttackerSpawn(target);
        if (attackerSpawn == null) {
            attackerNation.deposit(cost);
            core.saveNation(attackerNation);
            return Result.fail("无法找到合适的进攻方重生点，请确保城镇周围有空地。");
        }

        double areaFactor = target.getClaimCount() / 100.0d;
        double popFactor = target.getResidentCount() / 50.0d;
        int captureSeconds = (int) Math.round(
                config.campaignCaptureCountdownSeconds() * (1.0d + areaFactor + popFactor));
        int failSeconds = Math.max(60, (int) Math.round(
                config.campaignFailCountdownSeconds() / (1.0d + Math.max(1, onlineNationMembers(attackerNation)) * 0.1d)));

        Campaign campaign = new Campaign(type, attackerNation.getUuid(), defenderNation.getUuid(),
                target.getUuid(), captureSeconds, config.campaignBaseRespawnSeconds(), attackerSpawn);
        campaign.setFailCountdownSeconds(failSeconds);
        campaigns.put(campaign.getCampaignId(), campaign);
        attackerNation.setWarState(WarState.SIEGE);
        core.saveNation(attackerNation);
        createBossBars(campaign);
        String typeName = type == Campaign.Type.SIEGE ? "攻城" : "侵略";
        broadcastNation(attackerNation, ChatColor.RED + "【" + typeName + "】" + attackerNation.getName()
                + " 对 " + target.getName() + " 发动" + typeName + "！");
        broadcastNation(defenderNation, ChatColor.RED + "【" + typeName + "】" + attackerNation.getName()
                + " 对 " + target.getName() + " 发动" + typeName + "！");
        return Result.ok(typeName + "已发动：占领读条 " + captureSeconds + "s，失败读条 "
                + failSeconds + "s（攻城阈值 " + config.campaignCapturePoints() + " 点）。");
    }

    private void tick() {
        processPendingRespawns();
        if (campaigns.isEmpty()) {
            return;
        }
        tickCounter++;
        long now = System.currentTimeMillis();
        for (Campaign campaign : new ArrayList<>(campaigns.values())) {
            Nation attacker = core.getNation(campaign.getAttackerNationUuid());
            Nation defender = core.getNation(campaign.getDefenderNationUuid());
            Town target = core.getTown(campaign.getTargetTownUuid());
            if (attacker == null || defender == null || target == null
                    || attacker.getRelation(defender.getUuid()) != RelationType.AT_WAR) {
                endCampaign(campaign, Campaign.Status.CANCELLED);
                continue;
            }
            if (campaign.getType() == Campaign.Type.SIEGE) {
                if (now - campaign.getStartTime() > config.campaignDurationMinutes() * 60_000L) {
                    endCampaign(campaign, Campaign.Status.FAILED);
                }
            } else {
                processInvasionTick(campaign, attacker, defender, target, now);
            }
            if (tickCounter % 5 == 0) {
                updateBossBars(campaign, attacker, defender, target);
            }
        }
    }

    private void processInvasionTick(Campaign campaign, Nation attacker, Nation defender,
                                     Town target, long now) {
        int defenders = countInTerritory(campaign, target, false);
        int attackers = countInTerritory(campaign, target, true);

        if (defenders == 0) {
            if (campaign.getStatus() == Campaign.Status.ACTIVE) {
                if (campaign.startCaptureCountdown(now)) {
                    broadcastCampaign(campaign, "领地内无防守者，开始占领读条 "
                            + campaign.getCaptureCountdownSeconds() + "s！");
                }
            } else if (campaign.getStatus() == Campaign.Status.CAPTURE_COUNTDOWN
                    && now >= campaign.getCaptureCountdownEnd()) {
                endCampaign(campaign, Campaign.Status.SUCCESS);
                return;
            }
        } else if (campaign.getStatus() == Campaign.Status.CAPTURE_COUNTDOWN) {
            if (campaign.cancelCaptureCountdown()) {
                broadcastCampaign(campaign, "防守者回到领地，占领读条取消。");
            }
        }

        if (attackers == 0) {
            if (campaign.getStatus() == Campaign.Status.ACTIVE
                    || campaign.getStatus() == Campaign.Status.CAPTURE_COUNTDOWN) {
                int failSeconds = Math.max(60, (int) Math.round(
                        config.campaignFailCountdownSeconds()
                                / (1.0d + Math.max(1, onlineNationMembers(attacker)) * 0.1d)));
                if (campaign.startFailCountdown(now, failSeconds)) {
                    broadcastCampaign(campaign, "所有进攻方被赶出领地，开始失败读条 "
                            + failSeconds + "s！");
                }
            }
        } else if (campaign.getStatus() == Campaign.Status.FAIL_COUNTDOWN) {
            if (campaign.cancelFailCountdown()) {
                broadcastCampaign(campaign, "进攻方回到领地，失败读条取消。");
            }
        }
        if (campaign.getStatus() == Campaign.Status.FAIL_COUNTDOWN
                && now >= campaign.getFailCountdownEnd()) {
            endCampaign(campaign, Campaign.Status.FAILED);
            return;
        }

        if (defenders == 0 && !target.getResidentUuids().isEmpty()) {
            boolean allMaxDeaths = true;
            for (String residentUuid : target.getResidentUuids()) {
                if (campaign.getDefenderDeathCount(residentUuid) < config.campaignMaxDefenderDeaths()) {
                    allMaxDeaths = false;
                    break;
                }
            }
            if (allMaxDeaths) {
                endCampaign(campaign, Campaign.Status.SUCCESS);
            }
        }
    }

    public void endCampaign(Campaign campaign, Campaign.Status status) {
        if (campaign == null || !campaign.finish(status)) {
            return;
        }
        campaigns.remove(campaign.getCampaignId());
        removeBossBars(campaign);
        restoreSpectators(campaign);
        Nation attacker = core.getNation(campaign.getAttackerNationUuid());
        Nation defender = core.getNation(campaign.getDefenderNationUuid());
        Town target = core.getTown(campaign.getTargetTownUuid());
        switch (status) {
            case SUCCESS -> {
                occupyTown(campaign, attacker, defender, target);
                broadcastCampaign(campaign, ChatColor.RED + "【战役胜利】" + attacker.getName()
                        + " 占领了 " + target.getName() + "！");
            }
            case FAILED -> {
                processCompensation(campaign, attacker, defender, target);
                broadcastCampaign(campaign, ChatColor.GREEN + "【战役失败】" + attacker.getName()
                        + " 的进攻被击退！");
            }
            case CANCELLED -> broadcastCampaign(campaign, ChatColor.YELLOW + "【战役结束】战役已终止。");
            default -> {
            }
        }
        if (attacker != null && attacker.getTownCount() > 0
                && attacker.getRelation(defender == null ? "" : defender.getUuid()) == RelationType.AT_WAR) {
            attacker.setWarState(WarState.ACTIVE);
            core.saveNation(attacker);
        }
    }

    private void occupyTown(Campaign campaign, Nation attacker, Nation defender, Town target) {
        if (attacker == null || defender == null || target == null) {
            return;
        }
        transferTown(target, defender, attacker);
        boolean defenderEmpty = defender.getTownUuids().isEmpty();
        if (defenderEmpty) {
            triggerExile(attacker, defender);
            endWarRelations(attacker, defender);
        } else if (defender.getCapitalTownUuid() == null || core.getTown(defender.getCapitalTownUuid()) == null) {
            for (String townUuid : defender.getTownUuids()) {
                Town candidate = core.getTown(townUuid);
                if (candidate != null) {
                    candidate.setCapital(true);
                    defender.setCapitalTownUuid(candidate.getUuid());
                    core.saveTown(candidate);
                    break;
                }
            }
            core.saveNation(defender);
        }
        attacker.setStability(attacker.getStability() + 3.0d);
        core.saveNation(attacker);
        core.saveNation(defender);
        core.saveTown(target);
        Log.info("{} 通过战役占领了 {}", attacker.getName(), target.getName());
    }

    private void transferTown(Town town, Nation from, Nation to) {
        from.getTownUuids().remove(town.getUuid());
        from.getProvinceTowns().remove(town.getUuid());
        to.getTownUuids().add(town.getUuid());
        town.setNationUuid(to.getUuid());
        town.setCapital(false);
        if (to.getIdeology().isProvinceSystem()) {
            to.getProvinceTowns().add(town.getUuid());
        }
        Money treasury = town.getBalance();
        town.setBalance(Money.ZERO);
        to.deposit(treasury);

        Resident mayor = town.getMayorUuid() == null ? null : core.getResident(town.getMayorUuid());
        if (mayor != null) {
            mayor.setTownRank(TownRank.RESIDENT);
            core.saveResident(mayor);
        }
        if (town.getUuid().equals(from.getCapitalTownUuid())) {
            from.setCapitalTownUuid(null);
            for (String townUuid : from.getTownUuids()) {
                Town candidate = core.getTown(townUuid);
                if (candidate != null) {
                    candidate.setCapital(true);
                    from.setCapitalTownUuid(candidate.getUuid());
                    core.saveTown(candidate);
                    break;
                }
            }
        }
        core.saveTown(town);
        core.saveNation(from);
        core.saveNation(to);
    }

    private void processCompensation(Campaign campaign, Nation attacker, Nation defender, Town target) {
        if (attacker == null || defender == null || target == null) {
            return;
        }
        boolean capital = target.isCapital();
        Money assets = capital ? defender.getBalance() : target.getBalance();
        Money compensation = assets.times(config.campaignCompensationPercent());
        Money remaining = compensation;

        Money nationPay = min(attacker.getBalance(), remaining);
        if (nationPay.isPositive() && attacker.withdraw(nationPay)) {
            remaining = remaining.minus(nationPay);
            depositCompensation(capital, defender, target, nationPay);
        }
        core.saveNation(attacker);

        if (remaining.isPositive()) {
            List<Town> towns = new ArrayList<>();
            double totalBalance = 0.0d;
            for (String townUuid : attacker.getTownUuids()) {
                Town town = core.getTown(townUuid);
                if (town != null) {
                    towns.add(town);
                    totalBalance += town.getBalance().doubleValue();
                }
            }
            if (totalBalance > 0.0d) {
                for (Town town : towns) {
                    if (!remaining.isPositive()) {
                        break;
                    }
                    double shareUnits = remaining.doubleValue() * (town.getBalance().doubleValue() / totalBalance);
                    Money share = min(town.getBalance(), Money.of(shareUnits));
                    if (share.isPositive() && town.withdraw(share)) {
                        remaining = remaining.minus(share);
                        depositCompensation(capital, defender, target, share);
                        core.saveTown(town);
                    }
                }
            }
        }

        if (remaining.isPositive()) {
            double maxPercent = config.campaignMaxResidentContributionPercent();
            for (Resident resident : new ArrayList<>(core.allResidents())) {
                if (!remaining.isPositive()) {
                    break;
                }
                Town town = resident.hasTown() ? core.getTown(resident.getTownUuid()) : null;
                if (town == null || !attacker.getUuid().equals(town.getNationUuid())) {
                    continue;
                }
                Money maxPay = resident.getBalance().times(maxPercent);
                Money pay = min(maxPay, remaining);
                if (pay.isPositive() && resident.withdraw(pay)) {
                    remaining = remaining.minus(pay);
                    depositCompensation(capital, defender, target, pay);
                    core.saveResident(resident);
                }
            }
        }

        if (remaining.isPositive()) {
            String name = attacker.getName();
            for (String townUuid : new ArrayList<>(attacker.getTownUuids())) {
                Town town = core.getTown(townUuid);
                if (town != null) {
                    townService.dissolveTown(town);
                }
            }
            Resident king = attacker.getKingUuid() == null ? null : core.getResident(attacker.getKingUuid());
            if (king != null) {
                king.setNationRank(NationRank.CITIZEN);
                core.saveResident(king);
            }
            core.unregisterNation(attacker);
            Log.warn("国家 {} 因战役赔偿不足而解散。", name);
        }

        core.saveNation(defender);
        core.saveTown(target);
    }

    private void depositCompensation(boolean capital, Nation defender, Town target, Money amount) {
        if (!amount.isPositive()) {
            return;
        }
        if (capital) {
            defender.deposit(amount);
            core.saveNation(defender);
        } else {
            target.deposit(amount);
            core.saveTown(target);
        }
    }

    public Result restoreNation(Resident initiator, String nationUuid) {
        Nation fallen = core.getNation(nationUuid);
        if (fallen == null || initiator == null) {
            return Result.fail("流亡国家不存在。");
        }
        if (!fallen.isExiled()) {
            return Result.fail("该国家未处于流亡状态。");
        }
        if (System.currentTimeMillis() > fallen.getExileUntil()) {
            fallen.setExiled(false);
            core.saveNation(fallen);
            return Result.fail("流亡期已过，无法复国。");
        }
        if (!fallen.getExileMembers().contains(initiator.getUuidString())) {
            return Result.fail("只有流亡成员可以发起复国。");
        }
        Town capital = core.getTown(fallen.getCapitalTownUuid());
        if (capital == null) {
            return Result.fail("原首都城镇不存在，无法复国。");
        }
        Nation occupier = capital.hasNation() ? core.getNation(capital.getNationUuid()) : null;
        if (occupier != null && !occupier.getUuid().equals(fallen.getUuid())) {
            occupier.getTownUuids().remove(capital.getUuid());
            occupier.getProvinceTowns().remove(capital.getUuid());
            core.saveNation(occupier);
        }
        capital.setNationUuid(fallen.getUuid());
        capital.setCapital(true);
        fallen.getTownUuids().clear();
        fallen.getTownUuids().add(capital.getUuid());
        fallen.setExiled(false);
        fallen.setExileSince(0L);
        fallen.setExileUntil(0L);
        fallen.setIdeology(fallen.getExileIdeology() == null ? fallen.getIdeology() : fallen.getExileIdeology());
        fallen.getExileMembers().clear();
        fallen.setExiledName(null);
        fallen.setExileIdeology(null);
        fallen.setStability(30.0d);
        fallen.setWarState(WarState.NONE);
        Resident king = fallen.getKingUuid() == null ? null : core.getResident(fallen.getKingUuid());
        if (king != null) {
            king.setNationRank(NationRank.KING);
            core.saveResident(king);
        }
        core.saveTown(capital);
        core.saveNation(fallen);
        return Result.ok("国家 " + fallen.getName() + " 已复国，首都 " + capital.getName() + " 已夺回。");
    }

    public Result annexNation(Resident actor, Nation overlord, Nation defeated) {
        if (overlord == null || defeated == null || actor == null) {
            return Result.fail("参数无效。");
        }
        if (!isKing(actor, overlord)) {
            return Result.fail("只有国王可以吞并。");
        }
        if (!overlord.getIdeology().isProvinceSystem()) {
            return Result.fail("只有帝国制国家可以吞并。");
        }
        if (overlord.getRelation(defeated.getUuid()) != RelationType.AT_WAR) {
            return Result.fail("双方未处于战争状态。");
        }
        if (defeated.getStability() >= 10.0d && defeated.getBalance().isPositive()) {
            return Result.fail("对方尚未战败（需稳定度 < 10 或国库为空）。");
        }
        int maxTowns = (int) Math.max(1, config.nationMaxTowns() * overlord.getIdeology().getNationMaxTownsMod());
        if (overlord.getTownCount() + defeated.getTownCount() > maxTowns) {
            return Result.fail("吞并后城镇数超过上限 " + maxTowns + "。");
        }
        for (String townUuid : new ArrayList<>(defeated.getTownUuids())) {
            Town town = core.getTown(townUuid);
            if (town != null) {
                transferTown(town, defeated, overlord);
                if (!overlord.getProvinceTowns().contains(town.getUuid())) {
                    overlord.getProvinceTowns().add(town.getUuid());
                }
            }
        }
        Resident king = defeated.getKingUuid() == null ? null : core.getResident(defeated.getKingUuid());
        if (king != null) {
            king.setNationRank(NationRank.CITIZEN);
            core.saveResident(king);
        }
        overlord.setStability(overlord.getStability() - 15.0d);
        for (Nation other : core.allNations()) {
            if (!other.getUuid().equals(defeated.getUuid())
                    && other.getRelations().remove(defeated.getUuid()) != null) {
                core.saveNation(other);
            }
        }
        core.unregisterNation(defeated);
        core.saveNation(overlord);
        return Result.ok("帝国 " + overlord.getName() + " 已吞并 " + defeated.getName()
                + "，所有城镇转为行省（稳定度 -15）。");
    }

    private void triggerExile(Nation conqueror, Nation fallen) {
        long now = System.currentTimeMillis();
        fallen.setExiled(true);
        fallen.setExileSince(now);
        fallen.setExileUntil(now + config.exileDays() * 24L * 60L * 60L * 1000L);
        fallen.setExiledName(fallen.getName());
        fallen.setExileIdeology(fallen.getIdeology());
        fallen.getExileMembers().clear();
        if (fallen.getKingUuid() != null) {
            fallen.getExileMembers().add(fallen.getKingUuid());
        }
        Town capital = core.getTown(fallen.getCapitalTownUuid());
        if (capital != null) {
            for (String residentUuid : capital.getResidentUuids()) {
                if (!fallen.getExileMembers().contains(residentUuid)) {
                    fallen.getExileMembers().add(residentUuid);
                }
            }
        }
        Resident king = fallen.getKingUuid() == null ? null : core.getResident(fallen.getKingUuid());
        if (king != null) {
            king.setNationRank(NationRank.CITIZEN);
            core.saveResident(king);
        }
        core.saveNation(fallen);
        broadcastNation(fallen, ChatColor.DARK_RED + "【流亡】国家 " + fallen.getName()
                + " 已被吞并，进入 " + config.exileDays() + " 天流亡期。");
        Log.warn("国家 {} 被 {} 吞并，进入流亡状态。", fallen.getName(),
                conqueror == null ? "?" : conqueror.getName());
    }

    private void endWarRelations(Nation attacker, Nation defender) {
        attacker.setRelation(defender.getUuid(), RelationType.NEUTRAL);
        defender.setRelation(attacker.getUuid(), RelationType.NEUTRAL);
        attacker.setWarState(WarState.NONE);
        defender.setWarState(WarState.NONE);
        core.saveNation(attacker);
        core.saveNation(defender);
    }

    private void cancelCampaignsBetween(Nation a, Nation b) {
        for (Campaign campaign : new ArrayList<>(campaigns.values())) {
            boolean matches = (campaign.getAttackerNationUuid().equals(a.getUuid())
                    && campaign.getDefenderNationUuid().equals(b.getUuid()))
                    || (campaign.getAttackerNationUuid().equals(b.getUuid())
                    && campaign.getDefenderNationUuid().equals(a.getUuid()));
            if (matches) {
                endCampaign(campaign, Campaign.Status.CANCELLED);
            }
        }
    }

    public void onPlayerDeath(Player victim, Player killer) {
        if (killer != null) {
            killDrop.onPlayerKill(killer, victim);
        }
        Resident victimResident = core.getResident(victim.getUniqueId());
        if (victimResident == null) {
            return;
        }
        Campaign campaign = campaignForVictim(victimResident);
        if (campaign == null) {
            return;
        }
        Nation attacker = core.getNation(campaign.getAttackerNationUuid());
        Town target = core.getTown(campaign.getTargetTownUuid());
        boolean defender = victimResident.isInTown(campaign.getTargetTownUuid());
        int seconds = defender
                ? campaign.calcDefenderRespawnSeconds(victim.getUniqueId().toString(),
                config.campaignBaseRespawnSeconds(), config.campaignRespawnIncreasePercent())
                : campaign.calcAttackerRespawnSeconds(victim.getUniqueId().toString(),
                config.campaignRespawnIncreasePercent());
        if (defender) {
            campaign.incrementDefenderDeath(victim.getUniqueId().toString());
        } else {
            campaign.incrementAttackerDeath(victim.getUniqueId().toString());
        }
        Location spawn = defender ? toLocation(defenderSpawn(target)) : toLocation(campaign.getAttackerRespawnPoint());
        pendingRespawns.put(victim.getUniqueId(), new PendingRespawn(
                System.currentTimeMillis() + seconds * 1000L, campaign, defender, spawn));
        victim.sendMessage(ChatColor.YELLOW + "[GeoTowny] 你将在 " + seconds + " 秒后复活。");

        if (killer != null && campaign.getType() == Campaign.Type.SIEGE) {
            Resident killerResident = core.getResident(killer.getUniqueId());
            if (killerResident != null && attacker != null && isMember(killerResident, attacker)) {
                addSiegePoints(killer, target, config.campaignPointsPerKill());
            }
        }
    }

    public void onPlayerRespawn(PlayerRespawnEvent event) {
        PendingRespawn pending = pendingRespawns.get(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        if (pending.spawn != null) {
            event.setRespawnLocation(pending.spawn);
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.sendMessage(ChatColor.GRAY + "[GeoTowny] 你正在等待复活...");
        });
    }

    public void onBlockBreak(Player player, org.bukkit.block.Block block) {
        if (player == null || block == null) {
            return;
        }
        String worldKey = worlds.toInternalKey(block.getWorld());
        TownBlock townBlock = core.getTownBlockAt(worldKey, block.getX() >> 4, block.getZ() >> 4);
        if (townBlock == null) {
            return;
        }
        Town town = core.getTown(townBlock.getTownUuid());
        if (town == null) {
            return;
        }
        Resident resident = core.getResident(player.getUniqueId());
        if (resident == null) {
            return;
        }
        Nation attacker = nationOf(resident);
        Campaign campaign = findCampaignByTown(town.getUuid());
        if (campaign == null || campaign.getType() != Campaign.Type.SIEGE
                || attacker == null || !attacker.getUuid().equals(campaign.getAttackerNationUuid())) {
            return;
        }
        addSiegePoints(player, town, config.campaignPointsPerBreak());
    }

    private void addSiegePoints(Player player, Town town, int points) {
        Campaign campaign = findCampaignByTown(town.getUuid());
        if (campaign == null || campaign.getType() != Campaign.Type.SIEGE || !campaign.isActive()) {
            return;
        }
        int forts = 0;
        for (String key : town.getClaimBlockKeys()) {
            TownBlock block = core.getTownBlock(key);
            if (block != null && block.getPlotType() == com.geotowny.domain.model.PlotType.FORT) {
                forts++;
            }
        }
        double discount = Math.min(0.75d, forts * config.fortPlotDefenseBonus());
        int actual = (int) Math.round(points * (1.0d - discount));
        if (actual <= 0) {
            return;
        }
        int total = campaign.addSiegePoints(actual, config.campaignCapturePoints());
        if (total >= config.campaignCapturePoints()) {
            endCampaign(campaign, Campaign.Status.SUCCESS);
        } else {
            player.sendMessage(ChatColor.GOLD + "[GeoTowny] 攻城点数 +" + actual + "（"
                    + total + "/" + config.campaignCapturePoints() + "）");
        }
    }

    public void onPlayerQuit(Player player) {
        PendingRespawn pending = pendingRespawns.remove(player.getUniqueId());
        if (pending != null && player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        }
    }

    private void processPendingRespawns() {
        if (pendingRespawns.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PendingRespawn> entry : new ArrayList<>(pendingRespawns.entrySet())) {
            PendingRespawn pending = entry.getValue();
            if (now < pending.endTime) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                pendingRespawns.remove(entry.getKey());
                continue;
            }
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            if (pending.spawn != null) {
                player.teleport(pending.spawn);
            }
            player.sendMessage(ChatColor.GREEN + "[GeoTowny] 你已复活！");
            pendingRespawns.remove(entry.getKey());
        }
    }

    private void restoreSpectators(Campaign campaign) {
        for (Map.Entry<UUID, PendingRespawn> entry : new ArrayList<>(pendingRespawns.entrySet())) {
            if (entry.getValue().campaign.getCampaignId().equals(campaign.getCampaignId())) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                }
                pendingRespawns.remove(entry.getKey());
            }
        }
    }

    private void createBossBars(Campaign campaign) {
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        BossBar attackerBar = Bukkit.createBossBar("战役", BarColor.RED, BarStyle.SEGMENTED_10);
        BossBar defenderBar = Bukkit.createBossBar("防守", BarColor.BLUE, BarStyle.SEGMENTED_10);
        attackerBars.put(campaign.getCampaignId(), attackerBar);
        defenderBars.put(campaign.getCampaignId(), defenderBar);
    }

    private void updateBossBars(Campaign campaign, Nation attacker, Nation defender, Town target) {
        BossBar attackerBar = attackerBars.get(campaign.getCampaignId());
        BossBar defenderBar = defenderBars.get(campaign.getCampaignId());
        if (attackerBar == null || defenderBar == null) {
            return;
        }
        long now = System.currentTimeMillis();
        double progress = 1.0d;
        String attackerTitle = "战役: " + target.getName();
        String defenderTitle = "防守: " + target.getName();
        if (campaign.getStatus() == Campaign.Status.CAPTURE_COUNTDOWN) {
            progress = campaign.captureProgress(now);
            long seconds = campaign.remainingCaptureMillis(now) / 1000L;
            attackerTitle = "占领读条: " + target.getName() + " (" + seconds + "s)";
            defenderTitle = "城池告急: " + target.getName() + " (" + seconds + "s)";
        } else if (campaign.getStatus() == Campaign.Status.FAIL_COUNTDOWN) {
            progress = campaign.failProgress(now);
            long seconds = campaign.remainingFailMillis(now) / 1000L;
            attackerTitle = "即将失败: " + target.getName() + " (" + seconds + "s)";
            defenderTitle = "坚守中: " + target.getName() + " (" + seconds + "s)";
        }
        attackerBar.setTitle(attackerTitle);
        defenderBar.setTitle(defenderTitle);
        attackerBar.setProgress(Math.max(0.0d, Math.min(1.0d, progress)));
        defenderBar.setProgress(Math.max(0.0d, Math.min(1.0d, progress)));
        attackerBar.removeAll();
        defenderBar.removeAll();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Resident resident = core.getResident(player.getUniqueId());
            if (resident == null || !resident.hasTown()) {
                continue;
            }
            Town town = core.getTown(resident.getTownUuid());
            if (town == null || !town.hasNation()) {
                continue;
            }
            if (town.getNationUuid().equals(attacker.getUuid())) {
                attackerBar.addPlayer(player);
            } else if (town.getNationUuid().equals(defender.getUuid())) {
                defenderBar.addPlayer(player);
            }
        }
    }

    private void removeBossBars(Campaign campaign) {
        BossBar attackerBar = attackerBars.remove(campaign.getCampaignId());
        BossBar defenderBar = defenderBars.remove(campaign.getCampaignId());
        if (attackerBar != null) {
            attackerBar.removeAll();
            attackerBar.setVisible(false);
        }
        if (defenderBar != null) {
            defenderBar.removeAll();
            defenderBar.setVisible(false);
        }
    }

    private Campaign campaignForVictim(Resident resident) {
        for (Campaign campaign : campaigns.values()) {
            if (!campaign.isActive()) {
                continue;
            }
            if (resident.isInTown(campaign.getTargetTownUuid())) {
                return campaign;
            }
            Nation attacker = core.getNation(campaign.getAttackerNationUuid());
            if (attacker != null && isMember(resident, attacker)) {
                return campaign;
            }
        }
        return null;
    }

    private int countInTerritory(Campaign campaign, Town target, boolean attackerSide) {
        if (Bukkit.getServer() == null) {
            return 0;
        }
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInTerritory(player, target)) {
                continue;
            }
            Resident resident = core.getResident(player.getUniqueId());
            if (resident == null) {
                continue;
            }
            if (attackerSide) {
                Nation attacker = core.getNation(campaign.getAttackerNationUuid());
                if (attacker != null && isMember(resident, attacker)) {
                    count++;
                }
            } else if (resident.isInTown(campaign.getTargetTownUuid())) {
                count++;
            }
        }
        return count;
    }

    private boolean isInTerritory(Player player, Town town) {
        if (player == null || town == null) {
            return false;
        }
        String worldKey = worlds.toInternalKey(player.getWorld());
        TownBlock block = core.getTownBlockAt(worldKey,
                player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4);
        return block != null && town.getUuid().equals(block.getTownUuid());
    }

    private WorldLocation findAttackerSpawn(Town town) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (String key : town.getClaimBlockKeys()) {
            TownBlock block = core.getTownBlock(key);
            if (block == null) {
                continue;
            }
            for (int[] offset : offsets) {
                int chunkX = block.getChunkX() + offset[0];
                int chunkZ = block.getChunkZ() + offset[1];
                if (core.getTownBlockAt(block.getWorldKey(), chunkX, chunkZ) != null) {
                    continue;
                }
                World world = worlds.toWorld(block.getWorldKey());
                if (world == null) {
                    continue;
                }
                int x = chunkX * 16 + 8;
                int z = chunkZ * 16 + 8;
                int y = world.getHighestBlockYAt(x, z) + 1;
                return new WorldLocation(block.getWorldKey(), x + 0.5d, y, z + 0.5d, 0f, 0f);
            }
        }
        return null;
    }

    private WorldLocation defenderSpawn(Town town) {
        if (town == null) {
            return null;
        }
        if (town.getSpawn() != null) {
            return town.getSpawn();
        }
        TownBlock home = town.getHomeBlockKey() == null ? null : core.getTownBlock(town.getHomeBlockKey());
        if (home == null) {
            return null;
        }
        World world = worlds.toWorld(home.getWorldKey());
        if (world == null) {
            return null;
        }
        int x = home.getChunkX() * 16 + 8;
        int z = home.getChunkZ() * 16 + 8;
        return new WorldLocation(home.getWorldKey(), x + 0.5d,
                world.getHighestBlockYAt(x, z) + 1, z + 0.5d, 0f, 0f);
    }

    private Location toLocation(WorldLocation location) {
        if (location == null) {
            return null;
        }
        World world = worlds.toWorld(location.getWorldKey());
        return world == null ? null : new Location(world, location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch());
    }

    private void broadcastCampaign(Campaign campaign, String message) {
        Nation attacker = core.getNation(campaign.getAttackerNationUuid());
        Nation defender = core.getNation(campaign.getDefenderNationUuid());
        if (attacker != null) {
            broadcastNation(attacker, message);
        }
        if (defender != null) {
            broadcastNation(defender, message);
        }
    }

    private void broadcastNation(Nation nation, String message) {
        if (nation == null || Bukkit.getServer() == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Resident resident = core.getResident(player.getUniqueId());
            if (resident != null && isMember(resident, nation)) {
                player.sendMessage(message);
            }
        }
    }

    private int onlineNationMembers(Nation nation) {
        if (nation == null || Bukkit.getServer() == null) {
            return 0;
        }
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Resident resident = core.getResident(player.getUniqueId());
            if (resident != null && isMember(resident, nation)) {
                count++;
            }
        }
        return count;
    }

    private int onlineTownMembers(Town town) {
        if (town == null || Bukkit.getServer() == null) {
            return 0;
        }
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (town != null && town.getResidentUuids().contains(player.getUniqueId().toString())) {
                count++;
            }
        }
        return count;
    }

    private boolean isMember(Resident resident, Nation nation) {
        if (resident == null || nation == null || !resident.hasTown()) {
            return false;
        }
        Town town = core.getTown(resident.getTownUuid());
        return town != null && town.hasNation() && nation.getUuid().equals(town.getNationUuid());
    }

    private Nation nationOf(Resident resident) {
        if (resident == null || !resident.hasTown()) {
            return null;
        }
        Town town = core.getTown(resident.getTownUuid());
        if (town == null || !town.hasNation()) {
            return null;
        }
        return core.getNation(town.getNationUuid());
    }

    private static boolean isKing(Resident resident, Nation nation) {
        return resident != null && nation != null
                && nation.getKingUuid() != null
                && nation.getKingUuid().equals(resident.getUuidString());
    }

    public boolean isInWarAgainstTown(Resident resident, Town town) {
        if (resident == null || town == null || !town.hasNation()) {
            return false;
        }
        Nation residentNation = nationOf(resident);
        Nation townNation = core.getNation(town.getNationUuid());
        return residentNation != null && townNation != null
                && residentNation.getRelation(townNation.getUuid()) == RelationType.AT_WAR;
    }

    public Campaign findCampaignByTown(String townUuid) {
        for (Campaign campaign : campaigns.values()) {
            if (campaign.getTargetTownUuid().equals(townUuid) && campaign.isActive()) {
                return campaign;
            }
        }
        return null;
    }

    public Collection<Campaign> allCampaigns() {
        return java.util.Collections.unmodifiableCollection(campaigns.values());
    }

    public void cleanupDaily() {
        long now = System.currentTimeMillis();
        for (Nation nation : new ArrayList<>(core.allNations())) {
            if (nation.isExiled() && nation.getExileUntil() > 0 && now > nation.getExileUntil()) {
                nation.setExiled(false);
                core.saveNation(nation);
                Log.info("国家 {} 的流亡期已结束。", nation.getName());
            }
        }
    }

    private static Money min(Money a, Money b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static String voteKey(Nation nation, Nation target) {
        return nation.getUuid() + "|" + target.getUuid();
    }

    private record PendingRespawn(long endTime, Campaign campaign, boolean defender, Location spawn) {
    }
}