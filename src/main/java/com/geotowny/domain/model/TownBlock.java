package com.geotowny.domain.model;

import com.geotowny.domain.money.Money;

import java.util.ArrayList;
import java.util.List;

/**
 * 区块领地（TownBlock）：一个被城镇声明的 chunk。
 */
public class TownBlock {

    private String worldKey;
    private int chunkX;
    private int chunkZ;
    private String townUuid;
    private PlotType plotType;
    private String ownerUuid;
    private Money price;
    private String name;
    private boolean forSale;
    private int fortificationLevel;
    private List<String> trustedPlayers = new ArrayList<>();

    public TownBlock() {
    }

    public TownBlock(String worldKey, int chunkX, int chunkZ, String townUuid) {
        this.worldKey = worldKey;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.townUuid = townUuid;
        this.plotType = PlotType.DEFAULT;
        this.price = Money.ZERO;
    }

    public static String keyOf(String worldKey, int chunkX, int chunkZ) {
        return worldKey + "#" + chunkX + "#" + chunkZ;
    }

    public String key() {
        return keyOf(worldKey, chunkX, chunkZ);
    }

    public String getWorldKey() {
        return worldKey;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public String getTownUuid() {
        return townUuid;
    }

    public void setTownUuid(String townUuid) {
        this.townUuid = townUuid;
    }

    public PlotType getPlotType() {
        return plotType == null ? PlotType.DEFAULT : plotType;
    }

    public void setPlotType(PlotType plotType) {
        this.plotType = plotType;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(String ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public boolean isOwned() {
        return ownerUuid != null && !ownerUuid.isEmpty();
    }

    public Money getPrice() {
        return price == null ? Money.ZERO : price;
    }

    public void setPrice(Money price) {
        this.price = price == null ? Money.ZERO : price;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isForSale() {
        return forSale;
    }

    public void setForSale(boolean forSale) {
        this.forSale = forSale;
    }

    public int getFortificationLevel() {
        return fortificationLevel;
    }

    public void setFortificationLevel(int level) {
        this.fortificationLevel = level;
    }

    public List<String> getTrustedPlayers() {
        if (trustedPlayers == null) {
            trustedPlayers = new ArrayList<>();
        }
        return trustedPlayers;
    }

    public boolean isTrusted(String playerUuid) {
        return trustedPlayers != null && trustedPlayers.contains(playerUuid);
    }

    public void addTrusted(String playerUuid) {
        if (!getTrustedPlayers().contains(playerUuid)) {
            getTrustedPlayers().add(playerUuid);
        }
    }

    public void removeTrusted(String playerUuid) {
        getTrustedPlayers().remove(playerUuid);
    }
}