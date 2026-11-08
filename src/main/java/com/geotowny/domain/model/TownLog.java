package com.geotowny.domain.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 城镇行为日志。
 */
public class TownLog {

    public enum ActionType {
        ENTER,
        EXIT,
        BREAK,
        PLACE,
        OPEN_CHEST,
        OPEN_FURNACE,
        OPEN_DOOR,
        PRESS_BUTTON,
        USE_LEVER,
        KILL_PLAYER,
        KILL_MOB,
        IGNITE,
        EXPLOSION,
        FIRE_SPREAD,
        BUCKET_USE,
        PVP,
        MONSTER_SPAWN
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private long timestamp;
    private String townUuid;
    private String playerUuid;
    private String playerName;
    private String playerTown;
    private String playerNation;
    private ActionType action;
    private String worldKey;
    private int x;
    private int y;
    private int z;
    private String detail;

    public TownLog() {
    }

    public TownLog(long timestamp, String townUuid, String playerUuid, String playerName,
                   String playerTown, String playerNation, ActionType action,
                   String worldKey, int x, int y, int z, String detail) {
        this.timestamp = timestamp;
        this.townUuid = townUuid;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.playerTown = playerTown;
        this.playerNation = playerNation;
        this.action = action;
        this.worldKey = worldKey;
        this.x = x;
        this.y = y;
        this.z = z;
        this.detail = detail;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getTownUuid() {
        return townUuid;
    }

    public void setTownUuid(String townUuid) {
        this.townUuid = townUuid;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getPlayerTown() {
        return playerTown;
    }

    public void setPlayerTown(String playerTown) {
        this.playerTown = playerTown;
    }

    public String getPlayerNation() {
        return playerNation;
    }

    public void setPlayerNation(String playerNation) {
        this.playerNation = playerNation;
    }

    public ActionType getAction() {
        return action;
    }

    public void setAction(ActionType action) {
        this.action = action;
    }

    public String getWorldKey() {
        return worldKey;
    }

    public void setWorldKey(String worldKey) {
        this.worldKey = worldKey;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String format() {
        String time = Instant.ofEpochMilli(timestamp).atZone(ZONE).format(FORMATTER);
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(time).append("] ")
                .append(playerName == null ? "?" : playerName)
                .append('(').append(affiliation()).append(") ")
                .append(actionName(action))
                .append(" @ (").append(x).append(',').append(y).append(',').append(z).append(')');
        if (detail != null && !detail.isEmpty()) {
            sb.append(' ').append(detail);
        }
        return sb.toString();
    }

    private String affiliation() {
        String town = (playerTown == null || playerTown.isEmpty()) ? "无" : playerTown;
        String nation = (playerNation == null || playerNation.isEmpty()) ? "无" : playerNation;
        return town + "/" + nation;
    }

    private static String actionName(ActionType action) {
        if (action == null) {
            return "未知";
        }
        return switch (action) {
            case ENTER -> "进入领地";
            case EXIT -> "离开领地";
            case BREAK -> "破坏方块";
            case PLACE -> "放置方块";
            case OPEN_CHEST -> "打开箱子";
            case OPEN_FURNACE -> "打开熔炉";
            case OPEN_DOOR -> "打开门";
            case PRESS_BUTTON -> "按按钮";
            case USE_LEVER -> "使用拉杆";
            case KILL_PLAYER -> "击杀玩家";
            case KILL_MOB -> "击杀怪物";
            case IGNITE -> "点燃方块";
            case EXPLOSION -> "爆炸";
            case FIRE_SPREAD -> "火焰蔓延";
            case BUCKET_USE -> "使用桶";
            case PVP -> "PVP攻击";
            case MONSTER_SPAWN -> "怪物生成";
        };
    }
}