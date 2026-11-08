package com.geotowny.service;

import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.util.Result;

/**
 * 居民服务：玩家间转账。
 */
public final class ResidentService {

    private final GeoTownyCore core;

    public ResidentService(GeoTownyCore core) {
        this.core = core;
    }

    public Result pay(Resident sender, Resident target, Money amount) {
        if (sender == null || target == null) {
            return Result.fail("玩家不存在。");
        }
        if (sender.getUuidString().equals(target.getUuidString())) {
            return Result.fail("不能给自己转账。");
        }
        if (amount == null || !amount.isPositive()) {
            return Result.fail("金额必须为正数。");
        }
        if (!sender.withdraw(amount)) {
            return Result.fail("余额不足，当前余额 " + sender.getBalance().format() + "。");
        }
        target.deposit(amount);
        core.saveResident(sender);
        core.saveResident(target);
        return Result.ok("已向 " + target.getName() + " 转账 " + amount.format()
                + "，剩余余额 " + sender.getBalance().format() + "。");
    }
}