package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
import com.geotowny.domain.money.Money;
import com.geotowny.util.Result;

import java.util.ArrayList;

/**
 * 领地服务：区块声明、取消声明、上限与相邻规则。
 */
public final class ClaimService {

    private final GeoTownyCore core;
    private final PluginConfig config;

    public ClaimService(GeoTownyCore core, PluginConfig config) {
        this.core = core;
        this.config = config;
    }

    public Result claim(Resident resident, Town town, String worldKey, int chunkX, int chunkZ) {
        if (resident == null || town == null) {
            return Result.fail("城镇或居民不存在。");
        }
        if (!resident.isInTown(town.getUuid())) {
            return Result.fail("你不在该城镇中。");
        }
        if (resident.getTownRank() == null && !resident.getUuidString().equals(town.getMayorUuid())) {
            return Result.fail("只有市长或城镇成员可以圈地。");
        }

        String key = TownBlock.keyOf(worldKey, chunkX, chunkZ);
        if (core.getTownBlock(key) != null) {
            return Result.fail("该区块已被占有。");
        }
        int max = maxClaims(town);
        if (town.getClaimCount() >= max) {
            return Result.fail("已达领地上限 (" + max + ")。");
        }
        if (!town.getClaimBlockKeys().isEmpty() && !isAdjacentToTown(town, worldKey, chunkX, chunkZ)) {
            return Result.fail("新领地必须与城镇已有领地相邻。");
        }

        Money cost = Money.of(config.claimCost());
        if (!town.withdraw(cost)) {
            return Result.fail("城镇国库余额不足 (需 " + cost.format() + ")。");
        }

        TownBlock block = new TownBlock(worldKey, chunkX, chunkZ, town.getUuid());
        town.getClaimBlockKeys().add(key);
        if (town.getHomeBlockKey() == null) {
            town.setHomeBlockKey(key);
        }
        core.putTownBlock(block);
        core.saveTown(town);
        core.saveTownBlocks();
        return Result.ok("成功声明领地 " + worldKey + " [" + chunkX + "," + chunkZ + "]。");
    }

    public Result unclaim(Resident resident, Town town, String worldKey, int chunkX, int chunkZ) {
        if (resident == null || town == null) {
            return Result.fail("城镇或居民不存在。");
        }
        if (!resident.isInTown(town.getUuid())) {
            return Result.fail("你不在该城镇中。");
        }
        String key = TownBlock.keyOf(worldKey, chunkX, chunkZ);
        TownBlock block = core.getTownBlock(key);
        if (block == null || !town.getUuid().equals(block.getTownUuid())) {
            return Result.fail("该区块不属于你的城镇。");
        }
        if (key.equals(town.getHomeBlockKey())) {
            return Result.fail("不可取消城镇中心地块，请先更换中心。");
        }
        town.getClaimBlockKeys().remove(key);
        core.removeTownBlock(key);
        core.saveTown(town);
        core.saveTownBlocks();
        return Result.ok("已取消声明领地 [" + chunkX + "," + chunkZ + "]。");
    }

    public int maxClaims(Town town) {
        int max = config.baseClaimBlocks() + town.getResidentCount() * config.claimBlocksPerResident();
        return Math.min(max, config.maxTownBlocks());
    }

    public boolean isAdjacentToTown(Town town, String worldKey, int chunkX, int chunkZ) {
        return owns(town, core.getTownBlockAt(worldKey, chunkX, chunkZ - 1))
                || owns(town, core.getTownBlockAt(worldKey, chunkX, chunkZ + 1))
                || owns(town, core.getTownBlockAt(worldKey, chunkX + 1, chunkZ))
                || owns(town, core.getTownBlockAt(worldKey, chunkX - 1, chunkZ));
    }

    private boolean owns(Town town, TownBlock block) {
        return block != null && town.getUuid().equals(block.getTownUuid());
    }

    /**
     * 城镇解散时释放全部领地。
     */
    public void releaseAll(Town town) {
        for (String key : new ArrayList<>(town.getClaimBlockKeys())) {
            core.removeTownBlock(key);
        }
        town.getClaimBlockKeys().clear();
        core.saveTownBlocks();
    }
}