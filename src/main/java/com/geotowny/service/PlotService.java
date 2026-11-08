package com.geotowny.service;

import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.PlotType;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
import com.geotowny.domain.money.Money;
import com.geotowny.util.Result;

/**
 * 个人地块服务：认领、放弃、类型、出售、购买、信任列表。
 */
public final class PlotService {

    private final GeoTownyCore core;

    public PlotService(GeoTownyCore core) {
        this.core = core;
    }

    public Result claimPlot(Resident actor, String worldKey, int chunkX, int chunkZ) {
        TownBlock block = core.getTownBlockAt(worldKey, chunkX, chunkZ);
        if (block == null) {
            return Result.fail("当前区块未被任何城镇声明。");
        }
        if (!actor.isInTown(block.getTownUuid())) {
            return Result.fail("你不属于该区块的城镇。");
        }
        if (block.isOwned()) {
            return Result.fail("该地块已被认领。");
        }
        block.setOwnerUuid(actor.getUuidString());
        core.saveTownBlocks();
        return Result.ok("已认领该地块为个人领地。");
    }

    public Result unclaimPlot(Resident actor, String worldKey, int chunkX, int chunkZ) {
        TownBlock block = core.getTownBlockAt(worldKey, chunkX, chunkZ);
        if (block == null) {
            return Result.fail("当前区块无地块。");
        }
        if (!actor.getUuidString().equals(block.getOwnerUuid())) {
            return Result.fail("你不是该地块的主人。");
        }
        block.setOwnerUuid(null);
        block.setForSale(false);
        block.setPrice(Money.ZERO);
        core.saveTownBlocks();
        return Result.ok("已放弃该个人地块。");
    }

    public Result setPlotType(Resident actor, String worldKey, int chunkX, int chunkZ, PlotType type) {
        TownBlock block = core.getTownBlockAt(worldKey, chunkX, chunkZ);
        if (block == null) {
            return Result.fail("当前区块无地块。");
        }
        Town town = core.getTown(block.getTownUuid());
        if (!canManage(actor, town, block)) {
            return Result.fail("只有市长或地块主人可设置地块类型。");
        }
        if (type == null || type == PlotType.JAIL || type == PlotType.WILDS || type == PlotType.CAPITAL_CORE) {
            return Result.fail("该地块类型已停用（D-05）。");
        }
        block.setPlotType(type);
        core.saveTownBlocks();
        return Result.ok("地块类型已设为 " + type.name() + "。");
    }

    public Result setForSale(Resident actor, String worldKey, int chunkX, int chunkZ, Money price) {
        TownBlock block = core.getTownBlockAt(worldKey, chunkX, chunkZ);
        if (block == null) {
            return Result.fail("当前区块无地块。");
        }
        Town town = core.getTown(block.getTownUuid());
        if (!canManage(actor, town, block)) {
            return Result.fail("只有市长或地块主人可挂牌出售。");
        }
        if (!block.isOwned()) {
            return Result.fail("公共地块不能出售，请先认领。");
        }
        if (price == null || !price.isPositive()) {
            return Result.fail("售价必须为正数。");
        }
        block.setForSale(true);
        block.setPrice(price);
        core.saveTownBlocks();
        return Result.ok("地块已挂牌出售，价格 " + price.format() + "。");
    }

    public Result cancelSale(Resident actor, String worldKey, int chunkX, int chunkZ) {
        TownBlock block = core.getTownBlockAt(worldKey, chunkX, chunkZ);
        if (block == null) {
            return Result.fail("当前区块无地块。");
        }
        Town town = core.getTown(block.getTownUuid());
        if (!canManage(actor, town, block)) {
            return Result.fail("只有市长或地块主人可取消出售。");
        }
        block.setForSale(false);
        core.saveTownBlocks();
        return Result.ok("地块已取消出售。");
    }

    public Result buyPlot(Resident buyer, String worldKey, int chunkX, int chunkZ) {
        TownBlock block = core.getTownBlockAt(worldKey, chunkX, chunkZ);
        if (block == null || !block.isForSale()) {
            return Result.fail("该地块未挂牌出售。");
        }
        if (!buyer.isInTown(block.getTownUuid())) {
            return Result.fail("你不属于该区块的城镇。");
        }
        String ownerUuid = block.getOwnerUuid();
        if (ownerUuid != null && ownerUuid.equals(buyer.getUuidString())) {
            return Result.fail("你已经是该地块的主人。");
        }
        Money price = block.getPrice();
        if (!price.isPositive()) {
            return Result.fail("地块售价无效。");
        }
        if (!buyer.withdraw(price)) {
            return Result.fail("余额不足，需 " + price.format() + "。");
        }
        Resident owner = core.getResident(ownerUuid);
        if (owner != null) {
            owner.deposit(price);
            core.saveResident(owner);
        } else {
            Town town = core.getTown(block.getTownUuid());
            if (town != null) {
                town.deposit(price);
                core.saveTown(town);
            }
        }
        block.setOwnerUuid(buyer.getUuidString());
        block.setForSale(false);
        core.saveResident(buyer);
        core.saveTownBlocks();
        return Result.ok("已购买该地块，花费 " + price.format() + "。");
    }

    public Result trust(Resident actor, String worldKey, int chunkX, int chunkZ, Resident target) {
        TownBlock block = core.getTownBlockAt(worldKey, chunkX, chunkZ);
        if (block == null) {
            return Result.fail("当前区块无地块。");
        }
        if (!actor.getUuidString().equals(block.getOwnerUuid())) {
            return Result.fail("只有地块主人可以管理信任列表。");
        }
        if (target == null || !target.isInTown(block.getTownUuid())) {
            return Result.fail("目标玩家不是本镇成员。");
        }
        block.addTrusted(target.getUuidString());
        core.saveTownBlocks();
        return Result.ok("已信任 " + target.getName() + "。");
    }

    public Result untrust(Resident actor, String worldKey, int chunkX, int chunkZ, Resident target) {
        TownBlock block = core.getTownBlockAt(worldKey, chunkX, chunkZ);
        if (block == null) {
            return Result.fail("当前区块无地块。");
        }
        if (!actor.getUuidString().equals(block.getOwnerUuid())) {
            return Result.fail("只有地块主人可以管理信任列表。");
        }
        if (target == null) {
            return Result.fail("目标玩家不存在。");
        }
        block.removeTrusted(target.getUuidString());
        core.saveTownBlocks();
        return Result.ok("已取消对 " + target.getName() + " 的信任。");
    }

    private boolean canManage(Resident actor, Town town, TownBlock block) {
        if (actor == null) {
            return false;
        }
        if (actor.getUuidString().equals(block.getOwnerUuid())) {
            return true;
        }
        return town != null && actor.getUuidString().equals(town.getMayorUuid());
    }
}