package com.geotowny.domain.redeem;

import com.geotowny.domain.money.Money;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RedeemCode {

    public enum RedeemType {
        GEO,
        ITEM,
        COMMAND
    }

    private String code;
    private RedeemType type = RedeemType.GEO;
    private Money value = Money.ZERO;
    private String command = "";
    private List<String> itemData = new ArrayList<>();
    private int maxUses = 1;
    private int usedCount;
    private long expiryDate;
    private Set<String> usedByPlayerUuids = new HashSet<>();

    public RedeemCode() {
    }

    public RedeemCode(String code, RedeemType type) {
        this.code = code;
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public RedeemType getType() {
        return type == null ? RedeemType.GEO : type;
    }

    public void setType(RedeemType type) {
        this.type = type == null ? RedeemType.GEO : type;
    }

    public Money getValue() {
        return value == null ? Money.ZERO : value;
    }

    public void setValue(Money value) {
        this.value = value == null ? Money.ZERO : value;
    }

    public String getCommand() {
        return command == null ? "" : command;
    }

    public void setCommand(String command) {
        this.command = command == null ? "" : command;
    }

    public List<String> getItemData() {
        if (itemData == null) {
            itemData = new ArrayList<>();
        }
        return itemData;
    }

    public void setItemData(List<String> itemData) {
        this.itemData = itemData == null ? new ArrayList<>() : new ArrayList<>(itemData);
    }

    public int getMaxUses() {
        return maxUses;
    }

    public void setMaxUses(int maxUses) {
        this.maxUses = Math.max(0, maxUses);
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = Math.max(0, usedCount);
    }

    public void incrementUsedCount() {
        this.usedCount++;
    }

    public long getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(long expiryDate) {
        this.expiryDate = Math.max(0, expiryDate);
    }

    public Set<String> getUsedByPlayerUuids() {
        if (usedByPlayerUuids == null) {
            usedByPlayerUuids = new HashSet<>();
        }
        return usedByPlayerUuids;
    }

    public void setUsedByPlayerUuids(Set<String> usedByPlayerUuids) {
        this.usedByPlayerUuids = usedByPlayerUuids == null ? new HashSet<>() : usedByPlayerUuids;
    }

    public boolean isExpired() {
        return expiryDate > 0 && System.currentTimeMillis() > expiryDate;
    }

    public boolean hasUsesRemaining() {
        return maxUses == 0 || usedCount < maxUses;
    }
}