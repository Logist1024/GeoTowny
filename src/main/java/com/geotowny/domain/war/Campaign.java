package com.geotowny.domain.war;

import com.geotowny.domain.model.WorldLocation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Campaign {

    public enum Type {
        SIEGE,
        INVASION
    }

    public enum Status {
        ACTIVE,
        CAPTURE_COUNTDOWN,
        FAIL_COUNTDOWN,
        SUCCESS,
        FAILED,
        CANCELLED
    }

    private final String campaignId = UUID.randomUUID().toString();
    private final Type type;
    private final String attackerNationUuid;
    private final String defenderNationUuid;
    private final String targetTownUuid;
    private final long startTime;
    private final int attackerRespawnBaseSeconds;
    private final WorldLocation attackerRespawnPoint;

    private volatile Status status = Status.ACTIVE;
    private int siegePoints;
    private long captureCountdownStart;
    private long captureCountdownEnd;
    private final int captureCountdownSeconds;
    private long failCountdownStart;
    private long failCountdownEnd;
    private int failCountdownSeconds;

    private final Map<String, Integer> defenderDeaths = new ConcurrentHashMap<>();
    private final Map<String, Integer> attackerDeaths = new ConcurrentHashMap<>();

    public Campaign(Type type, String attackerNationUuid, String defenderNationUuid,
                    String targetTownUuid, int captureCountdownSeconds,
                    int attackerRespawnBaseSeconds, WorldLocation attackerRespawnPoint) {
        this.type = type;
        this.attackerNationUuid = attackerNationUuid;
        this.defenderNationUuid = defenderNationUuid;
        this.targetTownUuid = targetTownUuid;
        this.startTime = System.currentTimeMillis();
        this.captureCountdownSeconds = Math.max(1, captureCountdownSeconds);
        this.attackerRespawnBaseSeconds = Math.max(1, attackerRespawnBaseSeconds);
        this.attackerRespawnPoint = attackerRespawnPoint;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public Type getType() {
        return type;
    }

    public String getAttackerNationUuid() {
        return attackerNationUuid;
    }

    public String getDefenderNationUuid() {
        return defenderNationUuid;
    }

    public String getTargetTownUuid() {
        return targetTownUuid;
    }

    public long getStartTime() {
        return startTime;
    }

    public Status getStatus() {
        return status;
    }

    public int getSiegePoints() {
        return siegePoints;
    }

    public int addSiegePoints(int points, int threshold) {
        if (!isActive() || points <= 0) {
            return siegePoints;
        }
        siegePoints += points;
        return siegePoints;
    }

    public boolean startCaptureCountdown(long now) {
        if (status != Status.ACTIVE) {
            return false;
        }
        captureCountdownStart = now;
        captureCountdownEnd = now + captureCountdownSeconds * 1000L;
        status = Status.CAPTURE_COUNTDOWN;
        return true;
    }

    public boolean cancelCaptureCountdown() {
        if (status != Status.CAPTURE_COUNTDOWN) {
            return false;
        }
        captureCountdownStart = 0L;
        captureCountdownEnd = 0L;
        status = Status.ACTIVE;
        return true;
    }

    public boolean startFailCountdown(long now, int seconds) {
        if (status == Status.FAIL_COUNTDOWN || isTerminal()) {
            return false;
        }
        captureCountdownStart = 0L;
        captureCountdownEnd = 0L;
        failCountdownSeconds = Math.max(1, seconds);
        failCountdownStart = now;
        failCountdownEnd = now + failCountdownSeconds * 1000L;
        status = Status.FAIL_COUNTDOWN;
        return true;
    }

    public boolean cancelFailCountdown() {
        if (status != Status.FAIL_COUNTDOWN) {
            return false;
        }
        failCountdownStart = 0L;
        failCountdownEnd = 0L;
        status = Status.ACTIVE;
        return true;
    }

    public boolean finish(Status result) {
        if (isTerminal() || result == null || !isTerminal(result)) {
            return false;
        }
        status = result;
        return true;
    }

    public boolean isTerminal() {
        return isTerminal(status);
    }

    public boolean isActive() {
        return !isTerminal();
    }

    public static boolean isTerminal(Status status) {
        return status == Status.SUCCESS || status == Status.FAILED || status == Status.CANCELLED;
    }

    public int getCaptureCountdownSeconds() {
        return captureCountdownSeconds;
    }

    public long getCaptureCountdownEnd() {
        return captureCountdownEnd;
    }

    public int getFailCountdownSeconds() {
        return failCountdownSeconds;
    }

    public void setFailCountdownSeconds(int failCountdownSeconds) {
        this.failCountdownSeconds = Math.max(1, failCountdownSeconds);
    }

    public long getFailCountdownEnd() {
        return failCountdownEnd;
    }

    public double captureProgress(long now) {
        if (captureCountdownEnd <= captureCountdownStart) {
            return 0.0d;
        }
        long remaining = Math.max(0L, captureCountdownEnd - now);
        return 1.0d - (double) remaining / (captureCountdownEnd - captureCountdownStart);
    }

    public double failProgress(long now) {
        if (failCountdownEnd <= failCountdownStart) {
            return 0.0d;
        }
        long remaining = Math.max(0L, failCountdownEnd - now);
        return 1.0d - (double) remaining / (failCountdownEnd - failCountdownStart);
    }

    public long remainingCaptureMillis(long now) {
        return Math.max(0L, captureCountdownEnd - now);
    }

    public long remainingFailMillis(long now) {
        return Math.max(0L, failCountdownEnd - now);
    }

    public int getDefenderDeathCount(String playerUuid) {
        return defenderDeaths.getOrDefault(playerUuid, 0);
    }

    public int incrementDefenderDeath(String playerUuid) {
        int count = getDefenderDeathCount(playerUuid) + 1;
        defenderDeaths.put(playerUuid, count);
        return count;
    }

    public int getAttackerDeathCount(String playerUuid) {
        return attackerDeaths.getOrDefault(playerUuid, 0);
    }

    public int incrementAttackerDeath(String playerUuid) {
        int count = getAttackerDeathCount(playerUuid) + 1;
        attackerDeaths.put(playerUuid, count);
        return count;
    }

    public int calcDefenderRespawnSeconds(String playerUuid, int baseSeconds, double increasePercent) {
        int deaths = getDefenderDeathCount(playerUuid);
        return (int) Math.round(baseSeconds * (1.0d + deaths * increasePercent));
    }

    public int calcAttackerRespawnSeconds(String playerUuid, double increasePercent) {
        int deaths = getAttackerDeathCount(playerUuid);
        return (int) Math.round(attackerRespawnBaseSeconds * (1.0d + deaths * increasePercent));
    }

    public WorldLocation getAttackerRespawnPoint() {
        return attackerRespawnPoint;
    }
}