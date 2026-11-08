package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.RelationType;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;

/**
 * 保护规则：破坏、放置、交互、PVP。
 * <p>D-05 保留"领地日志 + 可选阻止"行为；规则与旧版 ProtectionManager 对齐。
 */
public final class ProtectionService {

    private final GeoTownyCore core;
    private final PluginConfig config;

    public ProtectionService(GeoTownyCore core, PluginConfig config) {
        this.core = core;
        this.config = config;
    }

    public boolean canBreak(Resident resident, String worldKey, int x, int z) {
        return check(resident, blockAt(worldKey, x, z));
    }

    public boolean canPlace(Resident resident, String worldKey, int x, int z) {
        return check(resident, blockAt(worldKey, x, z));
    }

    public boolean canInteract(Resident resident, String worldKey, int x, int z) {
        return check(resident, blockAt(worldKey, x, z));
    }

    public boolean canPvp(Resident attacker, String worldKey, int x, int z) {
        TownBlock block = blockAt(worldKey, x, z);
        if (block == null) {
            return config.wildernessPvp();
        }
        // 旧版规则：城镇内 PVP 始终允许，由城镇/战争规则另行约束。
        return true;
    }

    public TownBlock blockAt(String worldKey, int x, int z) {
        return core.getTownBlockAt(worldKey, x >> 4, z >> 4);
    }

    private boolean check(Resident resident, TownBlock block) {
        if (block == null) {
            return true;
        }
        Town town = core.getTown(block.getTownUuid());
        if (town == null) {
            return true;
        }
        if (resident != null) {
            if (resident.isInTown(town.getUuid())) {
                return true;
            }
            if (block.isOwned() && resident.getUuidString().equals(block.getOwnerUuid())) {
                return true;
            }
            if (block.isTrusted(resident.getUuidString())) {
                return true;
            }
            if (config.warEnabled() && isEnemy(resident, town)) {
                return true;
            }
        }
        return config.allowForeignerActions();
    }

    private boolean isEnemy(Resident resident, Town town) {
        if (!town.hasNation() || !resident.hasTown()) {
            return false;
        }
        Town residentTown = core.getTown(resident.getTownUuid());
        if (residentTown == null || !residentTown.hasNation()) {
            return false;
        }
        Nation attackerNation = core.getNation(residentTown.getNationUuid());
        Nation defenderNation = core.getNation(town.getNationUuid());
        if (attackerNation == null || defenderNation == null) {
            return false;
        }
        return attackerNation.getRelation(defenderNation.getUuid()) == RelationType.AT_WAR;
    }
}