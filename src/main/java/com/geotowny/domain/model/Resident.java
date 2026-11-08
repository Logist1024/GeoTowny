package com.geotowny.domain.model;

import com.geotowny.domain.money.Money;

import java.util.UUID;

/**
 * 居民：服务器玩家在 GeoTowny 中的数据镜像。
 * <p>按决策 D-01 改为单城镇模型：一个居民只属于一个城镇。
 */
public class Resident {

    private String uuid;
    private String name;
    private String townUuid;
    private TownRank townRank;
    private NationRank nationRank;
    private Money balance = Money.ZERO;
    private long registered;
    private long lastOnline;
    private String webPassword;

    private transient UUID cachedUuid;

    public Resident() {
    }

    public Resident(UUID uuid, String name, Money startingBalance) {
        this.uuid = uuid.toString();
        this.name = name;
        this.balance = startingBalance == null ? Money.ZERO : startingBalance;
        long now = System.currentTimeMillis();
        this.registered = now;
        this.lastOnline = now;
    }

    public UUID getUuid() {
        if (cachedUuid == null && uuid != null) {
            cachedUuid = UUID.fromString(uuid);
        }
        return cachedUuid;
    }

    public String getUuidString() {
        return uuid;
    }

    public void setUuidString(String uuid) {
        this.uuid = uuid;
        this.cachedUuid = null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTownUuid() {
        return townUuid;
    }

    public void setTownUuid(String townUuid) {
        this.townUuid = townUuid;
    }

    public void joinTown(String townUuid) {
        this.townUuid = townUuid;
    }

    public void leaveTown() {
        this.townUuid = null;
        this.townRank = null;
        // 单城镇模型下，离开唯一城镇即离开其所属国家。
        this.nationRank = null;
    }

    public boolean hasTown() {
        return townUuid != null && !townUuid.isEmpty();
    }

    public boolean isInTown(String townUuid) {
        return this.townUuid != null && this.townUuid.equals(townUuid);
    }

    public TownRank getTownRank() {
        return townRank;
    }

    public void setTownRank(TownRank rank) {
        this.townRank = rank;
    }

    public boolean isMayorOf(String townUuid) {
        return isInTown(townUuid) && townRank == TownRank.MAYOR;
    }

    public boolean isMayor() {
        return townRank == TownRank.MAYOR;
    }

    public NationRank getNationRank() {
        return nationRank;
    }

    public void setNationRank(NationRank rank) {
        this.nationRank = rank;
    }

    public boolean isKing() {
        return nationRank == NationRank.KING;
    }

    public Money getBalance() {
        return balance == null ? Money.ZERO : balance;
    }

    public void setBalance(Money balance) {
        if (balance != null && !balance.isNegative()) {
            this.balance = balance;
        }
    }

    public void deposit(Money amount) {
        if (amount != null && amount.isPositive()) {
            this.balance = getBalance().plus(amount);
        }
    }

    public boolean withdraw(Money amount) {
        if (amount == null || !amount.isPositive() || getBalance().compareTo(amount) < 0) {
            return false;
        }
        this.balance = getBalance().minus(amount);
        return true;
    }

    public long getRegistered() {
        return registered;
    }

    public void setRegistered(long registered) {
        this.registered = registered;
    }

    public long getLastOnline() {
        return lastOnline;
    }

    public void setLastOnline(long lastOnline) {
        this.lastOnline = lastOnline;
    }

    public String getWebPassword() {
        return webPassword;
    }

    public void setWebPassword(String webPassword) {
        this.webPassword = webPassword;
    }
}