package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.RelationType;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.money.Money;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

public final class KillDropService {

    private final GeoTownyCore core;
    private final PluginConfig config;
    private final DoubleSupplier random;
    private final Map<UUID, Double> warPercent = new ConcurrentHashMap<>();
    private final Map<UUID, Double> normalChance = new ConcurrentHashMap<>();

    public KillDropService(GeoTownyCore core, PluginConfig config) {
        this(core, config, Math::random);
    }

    public KillDropService(GeoTownyCore core, PluginConfig config, DoubleSupplier random) {
        this.core = core;
        this.config = config;
        this.random = random;
    }

    public Money onPlayerKill(Player killer, Player victim) {
        if (killer == null || victim == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return Money.ZERO;
        }
        Resident killerResident = core.getResident(killer.getUniqueId());
        Resident victimResident = core.getResident(victim.getUniqueId());
        if (killerResident == null || victimResident == null) {
            return Money.ZERO;
        }
        if (!config.killDropEnabled()) {
            return Money.ZERO;
        }

        if (atWar(killerResident, victimResident)) {
            return warKill(killer, victim, killerResident, victimResident);
        }
        return normalKill(killer, killerResident);
    }

    private Money warKill(Player killer, Player victim, Resident killerResident, Resident victimResident) {
        double percent = warPercent.computeIfAbsent(killer.getUniqueId(), key -> config.warKillDropPercent());
        Money drop = victimResident.getBalance().times(percent);
        if (drop.cents() < 1L) {
            decayWar(killer.getUniqueId());
            return Money.ZERO;
        }
        if (!victimResident.withdraw(drop)) {
            drop = victimResident.getBalance();
            if (drop.cents() < 1L) {
                return Money.ZERO;
            }
        }
        killerResident.deposit(drop);
        core.saveResident(victimResident);
        core.saveResident(killerResident);
        decayWar(killer.getUniqueId());
        killer.sendMessage(ChatColor.GREEN + "[GeoTowny] 战争击杀掉落 +" + drop.format()
                + " " + config.currencyName() + "（" + String.format("%.1f%%", percent * 100) + "）");
        victim.sendMessage(ChatColor.RED + "[GeoTowny] 被击杀掉落 -" + drop.format()
                + " " + config.currencyName() + "（" + String.format("%.1f%%", percent * 100) + "）");
        return drop;
    }

    private Money normalKill(Player killer, Resident killerResident) {
        double chance = normalChance.computeIfAbsent(killer.getUniqueId(), key -> config.killDropBaseChance());
        Money result = Money.ZERO;
        if (random.getAsDouble() < chance) {
            result = Money.of(config.killDropBaseAmount());
            killerResident.deposit(result);
            core.saveResident(killerResident);
            killer.sendMessage(ChatColor.GREEN + "[GeoTowny] 击杀掉落 +" + result.format()
                    + " " + config.currencyName());
        }
        decayNormal(killer.getUniqueId());
        return result;
    }

    public double currentWarPercent(UUID uuid) {
        return warPercent.getOrDefault(uuid, config.warKillDropPercent());
    }

    public double currentNormalChance(UUID uuid) {
        return normalChance.getOrDefault(uuid, config.killDropBaseChance());
    }

    public void dailyReset() {
        warPercent.clear();
        normalChance.clear();
    }

    private boolean atWar(Resident killer, Resident victim) {
        Nation killerNation = nationOf(killer);
        Nation victimNation = nationOf(victim);
        return killerNation != null && victimNation != null
                && killerNation.getRelation(victimNation.getUuid()) == RelationType.AT_WAR;
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

    private void decayWar(UUID uuid) {
        double current = warPercent.getOrDefault(uuid, config.warKillDropPercent());
        warPercent.put(uuid, Math.max(0.001d, current * config.warKillDropDecayFactor()));
    }

    private void decayNormal(UUID uuid) {
        double current = normalChance.getOrDefault(uuid, config.killDropBaseChance());
        normalChance.put(uuid, current * config.killDropDecayFactor());
    }
}